package dev.thomas7520.clipchat.windows;

import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;
import dev.thomas7520.clipchat.clipboard.model.TextNormalizer;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
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
	private static final Linker LINKER = Linker.nativeLinker();
	private static final AddressLayout PTR = ValueLayout.ADDRESS;
	private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;

	private static final FunctionDescriptor OUT_ONLY = FunctionDescriptor.of(INT, PTR, PTR);
	private static final FunctionDescriptor ARG_AND_OUT = FunctionDescriptor.of(INT, PTR, PTR, PTR);
	private static final FunctionDescriptor INDEX_AND_OUT = FunctionDescriptor.of(INT, PTR, INT, PTR);
	private static final FunctionDescriptor NO_ARGS = FunctionDescriptor.of(INT, PTR);

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

	private final Map<Long, MethodHandle> handles = new HashMap<>();

	private MethodHandle createString;
	private MethodHandle deleteString;
	private MethodHandle getStringRawBuffer;
	private MemorySegment factory;
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
			try (Arena arena = Arena.ofConfined()) {
				if (!connect()) {
					return false;
				}

				MemorySegment out = arena.allocate(INT);
				int hr = (int) handle(slot(factory, CLEAR_HISTORY), OUT_ONLY).invokeExact(factory, out);
				return hr >= 0 && out.get(ValueLayout.JAVA_BYTE, 0) != 0;
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
					int ignored = (int) handle(slot(factory, RELEASE), NO_ARGS).invokeExact(factory);
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
			try (Arena arena = Arena.ofConfined()) {
				if (!connect()) {
					return false;
				}

				MemorySegment result = awaitHistory(arena);

				if (result == null) {
					return false;
				}

				MemorySegment items = read(arena, result, 7);
				int size = readInt(arena, items, 7);
				boolean done = false;

				for (int index = 0; index < size && !done; index++) {
					MemorySegment item = elementAt(arena, items, index);

					if (nativeId.equals(text(arena, read(arena, item, 6)))) {
						MemorySegment out = arena.allocate(INT);
						int hr = (int) handle(slot(factory, slot), ARG_AND_OUT)
								.invokeExact(factory, item, out);
						// Success is a zero status enum, or a non-zero boolean, depending on the method.
						done = hr >= 0 && (statusResult
								? out.get(INT, 0) == 0
								: out.get(ValueLayout.JAVA_BYTE, 0) != 0);
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
		try (Arena arena = Arena.ofConfined()) {
			if (!connect()) {
				return Snapshot.failed(ProviderState.UNSUPPORTED_OS);
			}

			MemorySegment enabled = arena.allocate(ValueLayout.JAVA_BYTE);
			int hr = (int) handle(slot(factory, IS_HISTORY_ENABLED), OUT_ONLY).invokeExact(factory, enabled);

			if (hr < 0 || enabled.get(ValueLayout.JAVA_BYTE, 0) == 0) {
				return Snapshot.failed(ProviderState.DISABLED_BY_OS);
			}

			MemorySegment result = awaitHistory(arena);

			if (result == null) {
				return Snapshot.failed(ProviderState.ERROR);
			}

			int status = readInt(arena, result, 6);

			if (status != 0) {
				release(result);
				// 1 == AccessDenied, 2 == ClipboardHistoryDisabled.
				return Snapshot.failed(status == 1 ? ProviderState.ACCESS_DENIED : ProviderState.DISABLED_BY_OS);
			}

			MemorySegment items = read(arena, result, 7);
			int size = readInt(arena, items, 7);
			List<ClipboardEntry> entries = new ArrayList<>(size);

			for (int index = 0; index < size; index++) {
				readEntry(arena, items, index, maxCodePoints).ifPresent(entries::add);
			}

			release(items);
			release(result);
			return new Snapshot(entries.isEmpty() ? ProviderState.EMPTY : ProviderState.READY, entries);
		}
	}

	private Optional<ClipboardEntry> readEntry(Arena arena, MemorySegment items, int index, int maxCodePoints)
			throws Throwable {
		MemorySegment item = elementAt(arena, items, index);

		try {
			String id = text(arena, read(arena, item, 6));

			MemorySegment timestamp = arena.allocate(LONG);
			int hr = (int) handle(slot(item, 7), OUT_ONLY).invokeExact(item, timestamp);
			Instant createdAt = hr < 0 ? null : instant(timestamp.get(LONG, 0));

			MemorySegment view = read(arena, item, 8);
			MemorySegment operation = arena.allocate(PTR);
			hr = (int) handle(slot(view, GET_TEXT_ASYNC), OUT_ONLY).invokeExact(view, operation);

			// A non-text item fails here with DV_E_FORMATETC and is skipped.
			MemorySegment raw = hr < 0 ? null : await(arena, operation.get(PTR, 0));
			release(view);

			if (raw == null) {
				return Optional.empty();
			}

			return TextNormalizer.normalize(text(arena, raw), maxCodePoints, true)
					.map(normalized -> new ClipboardEntry(EntryId.windows(id), normalized.text(), createdAt,
							false, null, ClipboardSource.WINDOWS_HISTORY, normalized.originalLength()));
		} finally {
			release(item);
		}
	}

	private MemorySegment awaitHistory(Arena arena) throws Throwable {
		MemorySegment operation = arena.allocate(PTR);
		int hr = (int) handle(slot(factory, GET_HISTORY_ITEMS_ASYNC), OUT_ONLY).invokeExact(factory, operation);

		return hr < 0 ? null : await(arena, operation.get(PTR, 0));
	}

	/**
	 * Blocks until the operation completes, polling {@code IAsyncInfo::get_Status} until it leaves
	 * the started state or {@link #TIMEOUT_NANOS} elapses. Returns the result, or null on failure.
	 */
	private MemorySegment await(Arena arena, MemorySegment operation) throws Throwable {
		MemorySegment info = arena.allocate(PTR);
		int hr = (int) handle(slot(operation, 0), ARG_AND_OUT)
				.invokeExact(operation, guid(arena, IID_ASYNC_INFO), info);

		if (hr < 0) {
			release(operation);
			return null;
		}

		MemorySegment asyncInfo = info.get(PTR, 0);
		MemorySegment out = arena.allocate(INT);
		long deadline = System.nanoTime() + TIMEOUT_NANOS;
		int state = 0;

		while (System.nanoTime() < deadline) {
			hr = (int) handle(slot(asyncInfo, ASYNC_STATUS), OUT_ONLY).invokeExact(asyncInfo, out);
			state = hr < 0 ? 3 : out.get(INT, 0);

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

		MemorySegment results = arena.allocate(PTR);
		hr = (int) handle(slot(operation, GET_RESULTS), OUT_ONLY).invokeExact(operation, results);
		release(operation);

		return hr < 0 ? null : results.get(PTR, 0);
	}

	private boolean connect() throws Throwable {
		if (started) {
			return !unavailable;
		}

		started = true;

		try {
			SymbolLookup combase = SymbolLookup.libraryLookup("combase.dll", Arena.global());
			MethodHandle initialize = LINKER.downcallHandle(combase.find("RoInitialize").orElseThrow(),
					FunctionDescriptor.of(INT, INT));
			createString = LINKER.downcallHandle(combase.find("WindowsCreateString").orElseThrow(),
					FunctionDescriptor.of(INT, PTR, INT, PTR));
			deleteString = LINKER.downcallHandle(combase.find("WindowsDeleteString").orElseThrow(),
					FunctionDescriptor.of(INT, PTR));
			getStringRawBuffer = LINKER.downcallHandle(combase.find("WindowsGetStringRawBuffer").orElseThrow(),
					FunctionDescriptor.of(PTR, PTR, PTR));

			// 0 == RO_INIT_SINGLETHREADED. A positive status means the thread was already initialised.
			int hr = (int) initialize.invokeExact(0);

			if (hr < 0) {
				unavailable = true;
				return false;
			}

			try (Arena arena = Arena.ofConfined()) {
				MemorySegment name = arena.allocateFrom(CLIPBOARD_CLASS, StandardCharsets.UTF_16LE);
				MemorySegment nameOut = arena.allocate(PTR);
				hr = (int) createString.invokeExact(name, CLIPBOARD_CLASS.length(), nameOut);

				if (hr < 0) {
					unavailable = true;
					return false;
				}

				MemorySegment classId = nameOut.get(PTR, 0);
				MemorySegment factoryOut = arena.allocate(PTR);
				MethodHandle activation = LINKER.downcallHandle(
						combase.find("RoGetActivationFactory").orElseThrow(),
						FunctionDescriptor.of(INT, PTR, PTR, PTR));
				hr = (int) activation.invokeExact(classId, guid(arena, IID_CLIPBOARD_STATICS2), factoryOut);

				int ignored = (int) deleteString.invokeExact(classId);

				if (hr < 0) {
					unavailable = true;
					return false;
				}

				factory = factoryOut.get(PTR, 0);
			}

			return true;
		} catch (Throwable failure) {
			unavailable = true;
			return false;
		}
	}

	private MemorySegment elementAt(Arena arena, MemorySegment vector, int index) throws Throwable {
		MemorySegment out = arena.allocate(PTR);
		int hr = (int) handle(slot(vector, 6), INDEX_AND_OUT).invokeExact(vector, index, out);

		return hr < 0 ? null : out.get(PTR, 0);
	}

	private MemorySegment read(Arena arena, MemorySegment object, int slot) throws Throwable {
		MemorySegment out = arena.allocate(PTR);
		int hr = (int) handle(slot(object, slot), OUT_ONLY).invokeExact(object, out);

		return hr < 0 ? null : out.get(PTR, 0);
	}

	private int readInt(Arena arena, MemorySegment object, int slot) throws Throwable {
		MemorySegment out = arena.allocate(INT);
		int hr = (int) handle(slot(object, slot), OUT_ONLY).invokeExact(object, out);

		return hr < 0 ? 0 : out.get(INT, 0);
	}

	private String text(Arena arena, MemorySegment hstring) throws Throwable {
		if (hstring == null || hstring.address() == 0) {
			return "";
		}

		MemorySegment lengthOut = arena.allocate(INT);
		MemorySegment buffer = (MemorySegment) getStringRawBuffer.invokeExact(hstring, lengthOut);
		int length = lengthOut.get(INT, 0);
		String value = length == 0 ? ""
				: new String(buffer.reinterpret(length * 2L).toArray(ValueLayout.JAVA_BYTE),
						StandardCharsets.UTF_16LE);

		int ignored = (int) deleteString.invokeExact(hstring);
		return value;
	}

	private void release(MemorySegment object) throws Throwable {
		if (object != null && object.address() != 0) {
			int ignored = (int) handle(slot(object, RELEASE), NO_ARGS).invokeExact(object);
		}
	}

	/** Returns the downcall handle for a vtable function address, building it on first use. */
	private MethodHandle handle(MemorySegment function, FunctionDescriptor descriptor) {
		return handles.computeIfAbsent(function.address(), ignored -> LINKER.downcallHandle(function, descriptor));
	}

	private static MemorySegment slot(MemorySegment object, int index) {
		MemorySegment vtable = object.reinterpret(PTR.byteSize()).get(PTR, 0);
		return vtable.reinterpret((index + 1) * PTR.byteSize()).get(PTR, index * PTR.byteSize());
	}

	/**
	 * Writes a {@link UUID} as a Windows GUID: the first three fields in native byte order, then the
	 * remaining eight as bytes.
	 */
	private static MemorySegment guid(Arena arena, String uuid) {
		UUID parsed = UUID.fromString(uuid);
		long high = parsed.getMostSignificantBits();
		long low = parsed.getLeastSignificantBits();
		MemorySegment segment = arena.allocate(16, 4);

		segment.set(INT, 0, (int) (high >>> 32));
		segment.set(ValueLayout.JAVA_SHORT, 4, (short) (high >>> 16));
		segment.set(ValueLayout.JAVA_SHORT, 6, (short) high);

		for (int index = 0; index < 8; index++) {
			segment.set(ValueLayout.JAVA_BYTE, 8 + index, (byte) (low >>> (56 - 8 * index)));
		}

		return segment;
	}

	private static Instant instant(long ticks) {
		return Instant.ofEpochSecond(ticks / 10_000_000L - EPOCH_OFFSET_SECONDS, ticks % 10_000_000L * 100);
	}
}
