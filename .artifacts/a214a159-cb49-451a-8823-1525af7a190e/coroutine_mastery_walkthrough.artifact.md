# Coroutine Mastery Cheat Sheet

You have successfully implemented a 12-lab sandbox in [MainActivity.kt](file:///Users/swati/AndroidStudioProjects/Coroutine/app/src/main/java/com/nobdore/coroutine/MainActivity.kt).

## Key Interview Concepts

### 1. The Core Three
- **`launch`**: Fire-and-forget. Returns a `Job`.
- **`async`**: Parallel execution. Returns `Deferred<T>`. Use `.await()` to get result.
- **`withContext`**: Idiomatic thread switching. Safe and non-blocking.

### 2. Error Handling & Supervision
- **`CoroutineExceptionHandler`**: Only catches exceptions at the **root** of the coroutine tree.
- **`supervisorScope`**: Ensures that a child's failure does not cancel its siblings or parent. Critical for independent UI tasks.

### 3. Cancellation & Thread Safety
- **Cooperative Cancellation**: Coroutines must check `isActive` or call `ensureActive()` / `yield()` to be cancellable during heavy CPU work.
- **`Mutex`**: The coroutine-native way to handle thread safety without blocking threads.

### 4. Flows (Reactive Streams)
- **Flow**: Cold. Starts on collection.
- **StateFlow**: Hot. Always has a state. Conflated (ignores duplicate values). Best for UI state.
- **SharedFlow**: Hot. One-to-many broadcast. No state. Best for events (Toasts/Navigation).
- **`callbackFlow`**: The bridge between legacy callback APIs and coroutines.

### 5. Advanced Tips
- **`flowOn`**: Changes context for **upstream** emission, not downstream collection.
- **`runBlocking`**: Never use in UI. Only for tests and main entry points.
- **`awaitClose`**: Essential cleanup in `callbackFlow` to prevent memory leaks.

## Senior Interview Phrases to Use
> "I prefer **Structured Concurrency** to manage lifecycles and avoid zombie tasks."
> "I use **`collectAsStateWithLifecycle`** to ensure my UI stops consuming resources when the app is in the background."
> "Cancellation is **cooperative**, so I always include suspension points in long loops."
