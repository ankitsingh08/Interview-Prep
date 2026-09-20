# Android Coroutines: Senior Developer Masterclass

This guide document summarizes the 12-lab practical exercise implemented in the [Coroutine Sandbox App](app/src/main/java/com/nobdore/coroutine/MainActivity.kt).

---

## 1. Foundations & Parallelism (Labs 1 & 2)

### The `launch` vs `async` Distinction
*   **`launch`**: Fire-and-forget. Used for tasks where the result isn't needed (e.g., logging).
*   **`async`**: Returns `Deferred<T>`. Essential for **parallelism**. 
*   **Senior Insight**: Running two 2-second tasks with `async` takes **2 seconds** total. Running them sequentially takes **4 seconds**.
*   **Interview Question**: "When do you use `awaitAll()`?" 
    *   **Answer**: When you have a dynamic list of `Deferred` objects and need all results before proceeding.

### Dispatcher Mastery
*   **`Main.immediate`**: Executes immediately if already on the Main thread. Standard `Main` always posts to a handler queue, causing a small delay.
*   **`IO` vs `Default`**: `IO` is for blocking tasks (Network/DB) and has a flexible thread pool. `Default` is for CPU-intensive work (Math/Sorting) and is limited to the number of CPU cores.

---

## 2. Resilience & Error Handling (Labs 3 & 4)

### The "Supervisor" Trap
*   **Scenario**: You have a `launch` block with two child coroutines inside. One fails.
*   **Standard Job**: The failure propagates up, cancels the sibling, and cancels the parent.
*   **`supervisorScope`**: The failure of one child **does not** affect the other. 
*   **Interview Trap**: Placing a `CoroutineExceptionHandler` (CEH) on a child coroutine inside a standard `Job` will **fail**. CEH only works when placed on a root coroutine or a `SupervisorJob`.

### Exception Transparency
*   **The Rule**: A Flow should never catch exceptions thrown in the `collect` block.
*   **The Solution**: Use the `.catch { }` operator. It only catches exceptions from "upstream" (the code above it).

---

## 3. Reactive Streams (Labs 5, 6, 9 & 12)

### Hot vs Cold Streams
*   **Flow (Cold)**: Only starts when `collect` is called. Every collector starts from the beginning (like a movie).
*   **StateFlow (Hot)**: Always holds the latest value. It is **conflated** (ignores duplicate emissions). Best for UI State.
*   **SharedFlow (Hot)**: Broadcasts events. If no one is listening, the event is lost. Best for Toasts or Navigation.

### Subscription Leaks (The Critical Bug)
*   **The Bug**: Launching a `collect` inside a button click creates a new subscriber every time the button is pressed.
*   **The Fix**: Use `LaunchedEffect(Unit)` or `collectAsStateWithLifecycle` to bind the collection to the UI lifecycle.

### `callbackFlow` & Legacy Bridges
*   **Concept**: Used to turn old-school listeners (like GPS or Firebase) into modern Flows.
*   **Senior Requirement**: Always use `awaitClose { ... }` to remove the listener. Forgetting this causes massive memory leaks.

---

## 4. Advanced Thread Safety (Labs 7, 8, 10 & 11)

### Cooperative Cancellation
*   **The Myth**: `job.cancel()` stops a coroutine instantly.
*   **The Truth**: It only sets `isActive = false`. If your code is a tight `while` loop with no suspending functions, it will **ignore** cancellation.
*   **The Fix**: Call `yield()` or `ensureActive()` inside heavy loops.

### `Mutex` vs `Synchronized`
*   **Concept**: `Mutex` (Mutual Exclusion) is the coroutine-native way to handle race conditions.
*   **Advantage**: It **suspends** the coroutine instead of blocking the thread. `synchronized` blocks the entire thread, which can lead to UI jank or deadlocks.

### `flowOn` Context Preservation
*   **The Rule**: You cannot use `withContext` inside a `flow { ... }` builder.
*   **The Fix**: Use `.flowOn(Dispatchers.IO)`. This changes the context of the **upstream** emission only.

---

## 5. Testing & Best Practices

*   **`runBlocking`**: NEVER use in Android UI code. It will cause ANRs. Only use in Unit Tests or simple CLI tools.
*   **Structured Concurrency**: The principle that every coroutine must belong to a scope. When the scope is destroyed, all children are cancelled.
*   **`runTest`**: The only way to test coroutines. It provides a "virtual clock" that skips `delay()` calls so tests run instantly.
