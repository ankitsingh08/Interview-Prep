# Coroutine Mastery Sandbox & Expert Interview Prep

This repository is a comprehensive, deep-dive training ground for Android Coroutines. It includes a functional Sandbox app and a high-level technical manual designed for senior-level preparation.

## 🚀 The Sandbox App
The entire implementation, including 12 interactive labs, is contained in:
👉 [**MainActivity.kt**](app/src/main/java/com/nobdore/coroutine/MainActivity.kt)

### Lab Directory
*   **Foundations**: Launch, Async, and Parallelism (Labs 1 & 2).
*   **Resilience**: Supervisor scopes and Exception Handling (Labs 3 & 4).
*   **Reactivity**: Flows, StateFlow, SharedFlow, and Channels (Labs 5 & 6).
*   **Advanced Logic**: Cancellation, Mutex, and Context Preservation (Labs 7, 8, & 11).
*   **Integration**: callbackFlow bridges and Terminal operations (Labs 9 & 10).

---

## 📘 Detailed Senior Developer Guide
For an in-depth analysis of "Why" and "How" these components work under the hood, including senior-level interview strategies, read the:
👉 [**COROUTINE_EXPERT_GUIDE.md**](COROUTINE_EXPERT_GUIDE.md)

---

## 🧠 Masterclass Summary
*   **Structured Concurrency**: Learn to manage the Job tree and prevent leaks.
*   **Cooperative Cancellation**: Understand that `cancel()` is a request, not a command.
*   **Thread Hygiene**: Master `flowOn` and `Mutex` to keep the UI thread pure.
*   **Reactive Lifecycle**: Bind flows to the UI safely using `collectAsStateWithLifecycle`.

---

## How to use this repo
1.  **Clone & Run**: Open in Android Studio and deploy to an emulator.
2.  **Experiment**: Tap buttons in the app and watch the **Console Logs**.
3.  **Read the Guide**: Match the app behavior to the technical explanations in the [Expert Guide](COROUTINE_EXPERT_GUIDE.md).
4.  **Refactor**: Try modifying the labs (e.g., removing a `Mutex` or changing a `Dispatcher`) to see exactly how the behavior breaks.
