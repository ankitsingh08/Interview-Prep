# Android Coroutines: The Definitive Senior Developer Guide

This manual provides a deep-dive analysis of the concepts implemented in our [Coroutine Sandbox App](app/src/main/java/com/nobdore/coroutine/MainActivity.kt).

---

## I. Structured Concurrency & Job Hierarchy

### 1.1 The "Why" of Structured Concurrency
In early versions of Android concurrency (Threads, AsyncTask), leaks were common because there was no parent-child relationship. Coroutines enforce a **Scope-Job relationship**.
*   **The Job Tree**: Every coroutine started in a scope becomes a child of that scope's Job.
*   **Cancellation Propagation**: If a parent Job is cancelled, all its children are cancelled recursively. If a child fails (and isn't supervised), the parent is cancelled.

### 1.2 SupervisorJob vs. Job (Lab 4)
*   **Standard Job**: A failure of any child cancels the entire hierarchy. This is "All-for-One."
*   **SupervisorJob**: A failure of a child only affects itself. The parent and siblings remain active.
*   **Interview Insight**: `viewModelScope` uses a `SupervisorJob` internally. This is why one failing network request in your ViewModel doesn't crash the entire screen.

---

## II. Thread Safety & Cooperative Cancellation

### 2.1 The Cancellation Contract (Lab 7)
Cancellation in coroutines is **cooperative**. A coroutine that is performing heavy CPU work in a loop without suspension points **will not stop** just because you call `job.cancel()`.
*   **`isActive`**: A simple boolean check.
*   **`ensureActive()`**: Throws `CancellationException` immediately if the job is no longer active.
*   **`yield()`**: The "polite" way. It checks for cancellation AND yields the dispatcher to other waiting coroutines. Use this in tight loops to prevent UI jank.

### 2.2 Mutex: The Suspendable Lock (Lab 11)
Standard `synchronized` or `ReentrantLock` blocks the entire thread. In Coroutines, this is a sin.
*   **`Mutex.withLock`**: Suspends the coroutine until the lock is available. The thread is free to do other work in the meantime.
*   **Atomic Operations**: For simple counters, `AtomicInteger` is faster. For complex state updates, `Mutex` is mandatory.

---

## III. Reactive Streams: The Flow Ecosystem

### 3.1 Cold vs. Hot Streams (Lab 5)
*   **Flow (Cold)**: Executed on-demand. If you call `collect` three times, the flow block runs three times. Best for Database/Network fetches.
*   **StateFlow (Hot)**: A state-holder. It always has a `value`. It is **conflated**, meaning if it emits "A" and then "A" again, collectors won't see the duplicate.
*   **SharedFlow (Hot)**: A broadcast event stream. Good for "One-time events" like Navigation or SnackBar. Use `replay = 0` to ensure only new subscribers see new events.

### 3.2 Context Preservation & `flowOn` (Lab 8)
*   **The Contract**: A flow must collect in the same context it was started. You **cannot** use `withContext` inside a `flow { ... }` builder to change threads.
*   **`flowOn`**: This is the only way to change the context of the **upstream** (everything above the `flowOn` call). It uses a channel internally to bridge the contexts safely.

### 3.3 `callbackFlow`: The Bridge (Lab 9)
Many Android APIs are still listener-based (Location, Sensor, Firebase).
*   **`trySend`**: Non-suspending way to offer a value to the flow.
*   **`awaitClose`**: **MANDATORY**. This block is kept suspended until the flow is cancelled. It is the only place to remove your listeners and prevent memory leaks.

---

## IV. Advanced Error Handling (Labs 3 & 12)

### 4.1 `CoroutineExceptionHandler` (CEH)
*   **Limitation**: It only catches uncaught exceptions that reach the "Root" of the scope.
*   **Interview Trap**: Putting a CEH on a child `launch` inside a ViewModel will do **nothing** if that child is not a root coroutine.

### 4.2 Exception Transparency
*   **Rule**: A Flow should be "exception transparent"—it should not swallow exceptions from the collector.
*   **`.catch { ... }`**: This operator only catches exceptions from the **upstream**. If your `collect { ... }` block throws an error, `.catch` will not see it. This is a design feature to ensure separation of concerns.

---

## V. Senior Interview "Killer" Topics

### 5.1 `Dispatchers.Main.immediate`
Standard `Main` dispatcher always posts a message to the Main Looper queue. `Main.immediate` checks if you are already on the Main thread; if so, it executes the code **instantly** without a frame delay.

### 5.2 Testing with `runTest`
Never use `runBlocking` in unit tests for code that has `delay()`. `runTest` provides a `TestDispatcher` that uses **Virtual Time**. A 1-hour delay in your code will execute in milliseconds during a test.

### 5.3 `select` Expression (Experimental but Senior)
Used to race multiple suspending functions. For example: "Fetch data from Cache and Network simultaneously; use whichever returns first."
