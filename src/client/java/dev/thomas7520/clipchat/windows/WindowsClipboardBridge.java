package dev.thomas7520.clipchat.windows;

import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;
import dev.thomas7520.clipchat.clipboard.model.TextNormalizer;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Every native call into the Windows clipboard history.
 *
 * <p>All work runs on one dedicated thread, initialised once as a single-threaded apartment, which
 * owns every pointer this class holds. The WinRT clipboard statics fail to activate from a
 * multi-threaded apartment with {@code RO_E_UNSUPPORTED_FROM_MTA}.
 */
final class WindowsClipboardBridge implements AutoCloseable {
	private static final String CLIPBOARD_CLASS = "Windows.ApplicationModel.DataTransfer.Clipboard";
	private static final String IID_CLIPBOARD_STATICS2 = "d2ac1b6a-d29f-554b-b303-f0452345fe02";
	private static final String IID_ASYNC_INFO = "00000036-0000-0000-C000-000000000046";

	private static final int RELEASE = 2;
	private static final int GET_HISTORY_ITEMS_ASYNC = 6;
	private static final int CLEAR_HISTORY = 7;
	private static final int DELETE_ITEM = 8;
	private static final int SET_AS_CONTENT = 9;
	private static final int IS_HISTORY_ENABLED = 10;
	private static final int GET_TEXT_ASYNC = 12;
	private static final int GET_RESULTS = 8;
	private static final int ASYNC_STATUS = 7;

	// Upper bound on how long a native call may block before it is abandoned.
	private static final long TIMEOUT_NANOS = 5_000_000_000L;

	/** The number of seconds between the FILETIME epoch of 1601 and the Unix epoch. */
	private static final long EPOCH_OFFSET_SECONDS = 11644473600L;

	private final ExecutorService apartment = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "clipchat-winrt");
		thread.setDaemon(true);
		return thread;
	});

	private final Map<Long, Function> handles = new HashMap<>();

	private Function createString;
	private Function deleteString;
	private Function getStringRawBuffer;
	private Pointer factory;
	private boolean started;
	private boolean unavailable;

	record Snapshot(ProviderState state, List<ClipboardEntry> entries) {
		static Snapshot failed(ProviderState state) {
			return new Snapshot(state, List.of());
		}
	}

	CompletableFuture<Snapshot> readHistory(int maxCodePoints) {
		return on(() -> {
			try {
				return readHistoryOnApartment(maxCodePoints);
			} catch (Throwable failure) {
				return Snapshot.failed(ProviderState.ERROR);
			}
		});
	}

	/** Re-selects a history entry as the current clipboard content. */
	CompletableFuture<Boolean> setAsContent(String nativeId) {
		return act(nativeId, SET_AS_CONTENT, true);
	}

	CompletableFuture<Boolean> delete(String nativeId) {
		return act(nativeId, DELETE_ITEM, false);
	}

	CompletableFuture<Boolean> clearHistory() {
		return on(() -> {
			try {
				if (!connect()) {
					return false;
				}

				Memory out = new Memory(4);
				int hr = handle(slot(factory, CLEAR_HISTORY)).invokeInt(new Object[] { factory, out });
				return hr >= 0 && out.getByte(0) != 0;
			} catch (Throwable failure) {
				return false;
			}
		});
	}

	@Override
	public void close() {
		apartment.execute(() -> {
			if (factory != null) {
				try {
					handle(slot(factory, RELEASE)).invokeInt(new Object[] { factory });
				} catch (Throwable ignored) {
					// Best effort; the apartment thread is shutting down either way.
				}

				factory = null;
			}
		});
		apartment.shutdown();
	}

	private <T> CompletableFuture<T> on(Supplier<T> work) {
		return CompletableFuture.supplyAsync(work, apartment);
	}

	/**
	 * Invokes a per-item method. The history is re-read on every call and matched on {@code Id},
	 * because the API takes an item pointer and no pointer is retained between calls.
	 */
	private CompletableFuture<Boolean> act(String nativeId, int slot, boolean statusResult) {
		return on(() -> {
			try {
				if (!connect()) {
					return false;
				}

				Pointer result = awaitHistory();

				if (result == null) {
					return false;
				}

				Pointer items = read(result, 7);
				int size = readInt(items, 7);
				boolean done = false;

				for (int index = 0; index < size && !done; index++) {
					Pointer item = elementAt(items, index);

					if (nativeId.equals(text(read(item, 6)))) {
						Memory out = new Memory(4);
						int hr = handle(slot(factory, slot)).invokeInt(new Object[] { factory, item, out });
						// Success is a zero status enum, or a non-zero boolean, depending on the method.
						done = hr >= 0 && (statusResult ? out.getInt(0) == 0 : out.getByte(0) != 0);
					}

					release(item);
				}

				release(items);
				release(result);
				return done;
			} catch (Throwable failure) {
				return false;
			}
		});
	}

	private Snapshot readHistoryOnApartment(int maxCodePoints) throws Throwable {
		if (!connect()) {
			return Snapshot.failed(ProviderState.UNSUPPORTED_OS);
		}

		Memory enabled = new Memory(1);
		int hr = handle(slot(factory, IS_HISTORY_ENABLED)).invokeInt(new Object[] { factory, enabled });

		if (hr < 0 || enabled.getByte(0) == 0) {
			return Snapshot.failed(ProviderState.DISABLED_BY_OS);
		}

		Pointer result = awaitHistory();

		if (result == null) {
			return Snapshot.failed(ProviderState.ERROR);
		}

		int status = readInt(result, 6);

		if (status != 0) {
			release(result);
			// 1 == AccessDenied, 2 == ClipboardHistoryDisabled.
			return Snapshot.failed(status == 1 ? ProviderState.ACCESS_DENIED : ProviderState.DISABLED_BY_OS);
		}

		Pointer items = read(result, 7);
		int size = readInt(items, 7);
		List<ClipboardEntry> entries = new ArrayList<>(size);

		for (int index = 0; index < size; index++) {
			readEntry(items, index, maxCodePoints).ifPresent(entries::add);
		}

		release(items);
		release(result);
		return new Snapshot(entries.isEmpty() ? ProviderState.EMPTY : ProviderState.READY, entries);
	}

	private Optional<ClipboardEntry> readEntry(Pointer items, int index, int maxCodePoints)
			throws Throwable {
		Pointer item = elementAt(items, index);

		try {
			String id = text(read(item, 6));

			Memory timestamp = new Memory(8);
			int hr = handle(slot(item, 7)).invokeInt(new Object[] { item, timestamp });
			Instant createdAt = hr < 0 ? null : instant(timestamp.getLong(0));

			Pointer view = read(item, 8);
			PointerByReference operation = new PointerByReference();
			hr = handle(slot(view, GET_TEXT_ASYNC)).invokeInt(new Object[] { view, operation });

			// A non-text item fails here with DV_E_FORMATETC and is skipped.
			Pointer raw = hr < 0 ? null : await(operation.getValue());
			release(view);

			if (raw == null) {
				return Optional.empty();
			}

			return TextNormalizer.normalize(text(raw), maxCodePoints, true)
					.map(normalized -> new ClipboardEntry(EntryId.windows(id), normalized.text(), createdAt,
							false, null, ClipboardSource.WINDOWS_HISTORY, normalized.originalLength()));
		} finally {
			release(item);
		}
	}

	private Pointer awaitHistory() throws Throwable {
		PointerByReference operation = new PointerByReference();
		int hr = handle(slot(factory, GET_HISTORY_ITEMS_ASYNC)).invokeInt(new Object[] { factory, operation });

		return hr < 0 ? null : await(operation.getValue());
	}

	/**
	 * Blocks until the operation completes, polling {@code IAsyncInfo::get_Status} until it leaves
	 * the started state or {@link #TIMEOUT_NANOS} elapses. Returns the result, or null on failure.
	 */
	private Pointer await(Pointer operation) throws Throwable {
		PointerByReference info = new PointerByReference();
		int hr = handle(slot(operation, 0)).invokeInt(new Object[] { operation, guid(IID_ASYNC_INFO), info });

		if (hr < 0) {
			release(operation);
			return null;
		}

		Pointer asyncInfo = info.getValue();
		Memory out = new Memory(4);
		long deadline = System.nanoTime() + TIMEOUT_NANOS;
		int state = 0;

		while (System.nanoTime() < deadline) {
			hr = handle(slot(asyncInfo, ASYNC_STATUS)).invokeInt(new Object[] { asyncInfo, out });
			state = hr < 0 ? 3 : out.getInt(0);

			if (state != 0) {
				break;
			}

			Thread.sleep(2);
		}

		release(asyncInfo);

		if (state != 1) {
			release(operation);
			return null;
		}

		PointerByReference results = new PointerByReference();
		hr = handle(slot(operation, GET_RESULTS)).invokeInt(new Object[] { operation, results });
		release(operation);

		return hr < 0 ? null : results.getValue();
	}

	private boolean connect() {
		if (started) {
			return !unavailable;
		}

		started = true;

		try {
			NativeLibrary combase = NativeLibrary.getInstance("combase");
			Function initialize = combase.getFunction("RoInitialize");
			createString = combase.getFunction("WindowsCreateString");
			deleteString = combase.getFunction("WindowsDeleteString");
			getStringRawBuffer = combase.getFunction("WindowsGetStringRawBuffer");

			// 0 == RO_INIT_SINGLETHREADED. A positive status means the thread was already initialised.
			int hr = initialize.invokeInt(new Object[] { 0 });

			if (hr < 0) {
				unavailable = true;
				return false;
			}

			Memory name = wideString(CLIPBOARD_CLASS);
			PointerByReference nameOut = new PointerByReference();
			hr = createString.invokeInt(new Object[] { name, CLIPBOARD_CLASS.length(), nameOut });

			if (hr < 0) {
				unavailable = true;
				return false;
			}

			Pointer classId = nameOut.getValue();
			PointerByReference factoryOut = new PointerByReference();
			Function activation = combase.getFunction("RoGetActivationFactory");
			hr = activation.invokeInt(new Object[] { classId, guid(IID_CLIPBOARD_STATICS2), factoryOut });

			deleteString.invokeInt(new Object[] { classId });

			if (hr < 0) {
				unavailable = true;
				return false;
			}

			factory = factoryOut.getValue();
			return true;
		} catch (Throwable failure) {
			unavailable = true;
			return false;
		}
	}

	private Pointer elementAt(Pointer vector, int index) {
		if (vector == null) {
			return null;
		}

		PointerByReference out = new PointerByReference();
		int hr = handle(slot(vector, 6)).invokeInt(new Object[] { vector, index, out });

		return hr < 0 ? null : out.getValue();
	}

	private Pointer read(Pointer object, int slot) {
		if (object == null) {
			return null;
		}

		PointerByReference out = new PointerByReference();
		int hr = handle(slot(object, slot)).invokeInt(new Object[] { object, out });

		return hr < 0 ? null : out.getValue();
	}

	private int readInt(Pointer object, int slot) {
		if (object == null) {
			return 0;
		}

		Memory out = new Memory(4);
		int hr = handle(slot(object, slot)).invokeInt(new Object[] { object, out });

		return hr < 0 ? 0 : out.getInt(0);
	}

	private String text(Pointer hstring) {
		if (hstring == null || Pointer.nativeValue(hstring) == 0) {
			return "";
		}

		IntByReference lengthOut = new IntByReference();
		Pointer buffer = getStringRawBuffer.invokePointer(new Object[] { hstring, lengthOut });
		int length = lengthOut.getValue();
		String value = length == 0 ? ""
				: new String(buffer.getByteArray(0, length * 2), StandardCharsets.UTF_16LE);

		deleteString.invokeInt(new Object[] { hstring });
		return value;
	}

	private void release(Pointer object) {
		if (object != null && Pointer.nativeValue(object) != 0) {
			handle(slot(object, RELEASE)).invokeInt(new Object[] { object });
		}
	}

	/** Returns the JNA function wrapper for a vtable function address, building it on first use. */
	private Function handle(Pointer function) {
		return handles.computeIfAbsent(Pointer.nativeValue(function), ignored -> Function.getFunction(function));
	}

	private static Pointer slot(Pointer object, int index) {
		Pointer vtable = object.getPointer(0);
		return vtable.getPointer((long) index * Native.POINTER_SIZE);
	}

	private static Memory wideString(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
		Memory memory = new Memory(bytes.length + 2L);
		memory.write(0, bytes, 0, bytes.length);
		memory.setShort(bytes.length, (short) 0);
		return memory;
	}

	/**
	 * Writes a {@link UUID} as a Windows GUID: the first three fields in native byte order, then the
	 * remaining eight as bytes.
	 */
	private static Memory guid(String uuid) {
		UUID parsed = UUID.fromString(uuid);
		long high = parsed.getMostSignificantBits();
		long low = parsed.getLeastSignificantBits();
		Memory segment = new Memory(16);

		segment.setInt(0, (int) (high >>> 32));
		segment.setShort(4, (short) (high >>> 16));
		segment.setShort(6, (short) high);

		for (int index = 0; index < 8; index++) {
			segment.setByte(8 + index, (byte) (low >>> (56 - 8 * index)));
		}

		return segment;
	}

	private static Instant instant(long ticks) {
		return Instant.ofEpochSecond(ticks / 10_000_000L - EPOCH_OFFSET_SECONDS, ticks % 10_000_000L * 100);
	}
}
