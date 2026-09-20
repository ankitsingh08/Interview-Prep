# Coroutine Mastery Sandbox & Expert Interview Prep

This repository is a deep-dive into Android Coroutines, moving from basic foundations to senior-level architectural patterns. It was built through an iterative 12-lab exercise simulating real-world Android challenges.

## 🚀 The Sandbox App
The entire implementation, including all 12 labs, is contained in a single, interactive sandbox:
👉 [**MainActivity.kt**](app/src/main/java/com/nobdore/coroutine/MainActivity.kt)

### What's inside the Sandbox?
*   **Parallelism Labs**: `async/await` optimization.
*   **Resilience Labs**: Supervisor jobs and complex error handling.
*   **Reactivity Labs**: Flow, StateFlow, SharedFlow, and Channels.
*   **Bridge Labs**: `callbackFlow` for legacy API integration.
*   **Thread Safety Labs**: `Mutex` locks and cooperative cancellation.

---

## 📘 Detailed Study Guide
For a deep technical breakdown of every lab, including the "Why", "Senior Insights", and "Interview Questions", read the:
👉 [**COROUTINE_EXPERT_GUIDE.md**](COROUTINE_EXPERT_GUIDE.md)

---

## 🧠 Key Senior Interview Takeaways
*   **Structured Concurrency**: Know how to manage lifecycles to prevent leaks.
*   **Exception Propagation**: Understand how `supervisorScope` differs from standard scopes.
*   **Flow Lifecycle**: Master the use of `collectAsStateWithLifecycle` to prevent resource waste.
*   **Thread Hygiene**: Understand when to use `flowOn` vs `withContext`.

## How to use this repo
1.  Open the project in Android Studio.
2.  Deploy the app to an emulator.
3.  Tap each Lab button and observe the **Console Logs** in the UI.
4.  Read the corresponding section in the Expert Guide to understand the magic behind the logs.
