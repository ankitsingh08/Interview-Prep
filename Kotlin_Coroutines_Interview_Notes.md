# Kotlin Coroutines Interview Notes For Android Lead Roles

These notes summarize the coroutine concepts we covered, with extra focus on the questions that were not immediately clear:

- `launch` vs `async`
- Why `async { ... }.await()` can become sequential
- Why not expose `MutableStateFlow`
- Whether `GlobalScope` is ever needed
- `Flow` vs `StateFlow`

## 1. Core Coroutine Mental Model

A coroutine is a lightweight, cancellable unit of asynchronous work.

It is not the same as a thread. Many coroutines can run on a small number of threads.

A suspend function can pause without blocking the thread:

```kotlin
suspend fun fetchUser(): User {
    delay(1_000) // Suspends coroutine, does not block the thread
    return api.getUser()
}
```

Important interview line:

```text
A suspend function describes asynchronous work, but a CoroutineScope owns and launches that work.
```

Example:

```kotlin
fun loadUser() {
    viewModelScope.launch {
        val user = repository.getUser()
        _uiState.value = UiState.Success(user)
    }
}
```

Here:

- `viewModelScope` owns the coroutine lifecycle.
- `launch` starts the coroutine.
- `repository.getUser()` is a suspend function.
- If the ViewModel is cleared, the coroutine is cancelled.

## 2. `launch` vs `async`

This was one of the confusing areas, so the clean mental model is:

```text
launch = start work when you do not need a returned value
async = start work when you need a returned value later
```

### `launch`

`launch` returns a `Job`.

A `Job` represents work that can be cancelled or observed, but it does not produce a value.

Use `launch` for:

- Updating UI state
- Saving something
- Sending analytics
- Starting a coroutine from a ViewModel
- Fire-and-forget work that is still lifecycle-bound

Example:

```kotlin
fun onSaveClicked() {
    viewModelScope.launch {
        _uiState.value = UiState.Saving

        repository.saveProfile(profile)

        _uiState.value = UiState.Saved
    }
}
```

This function does not need to return `Profile` or `Boolean` to the caller. The goal is to perform work and update state.

Another example:

```kotlin
fun trackScreenOpened() {
    viewModelScope.launch {
        analytics.trackScreen("Profile")
    }
}
```

This is a side effect. We care that it happens, not that it gives us a value.

### `async`

`async` returns a `Deferred<T>`.

`Deferred<T>` is like a `Job` that will eventually produce a value.

Use `async` when:

- You need a result.
- You want to run independent tasks concurrently.
- You will combine multiple results.

Example:

```kotlin
suspend fun loadHomeScreen(): HomeData = coroutineScope {
    val profileDeferred: Deferred<Profile> = async {
        profileRepository.getProfile()
    }

    val postsDeferred: Deferred<List<Post>> = async {
        postRepository.getPosts()
    }

    val profile = profileDeferred.await()
    val posts = postsDeferred.await()

    HomeData(profile = profile, posts = posts)
}
```

Here, `async` is useful because:

- One coroutine returns `Profile`.
- Another coroutine returns `List<Post>`.
- We need both values to build `HomeData`.

Interview answer:

```text
launch returns Job and is used for work without a result.
async returns Deferred<T> and is used when I need a value, usually for concurrent composition.
```

## 3. Why Immediate `await()` Becomes Sequential

This line was confusing:

```kotlin
val profile = async { profileRepository.getProfile() }.await()
```

The important part is `.await()`.

`await()` means:

```text
Pause here until this async result is finished.
```

So this code:

```kotlin
val profile = async {
    profileRepository.getProfile()
}.await()

val posts = async {
    postRepository.getPosts()
}.await()
```

Runs like this:

```text
1. Start getProfile()
2. Immediately wait for getProfile() to finish
3. Only after profile finishes, start getPosts()
4. Immediately wait for getPosts() to finish
```

That means it behaves almost like:

```kotlin
val profile = profileRepository.getProfile()
val posts = postRepository.getPosts()
```

It is sequential because the second task does not start until the first `await()` finishes.

### Sequential Timing Example

Suppose:

```text
getProfile() takes 1 second
getPosts() takes 1 second
```

Sequential version:

```kotlin
val profile = async { getProfile() }.await()
val posts = async { getPosts() }.await()
```

Timeline:

```text
0s: start profile
1s: profile finishes
1s: start posts
2s: posts finishes
```

Total time:

```text
About 2 seconds
```

### Concurrent Timing Example

Better:

```kotlin
val profileDeferred = async {
    getProfile()
}

val postsDeferred = async {
    getPosts()
}

val profile = profileDeferred.await()
val posts = postsDeferred.await()
```

Timeline:

```text
0s: start profile
0s: start posts
1s: profile finishes
1s: posts finishes
```

Total time:

```text
About 1 second
```

Key rule:

```text
Start all independent async work first.
Then await the results.
```

Bad:

```kotlin
val a = async { loadA() }.await()
val b = async { loadB() }.await()
```

Good:

```kotlin
val aDeferred = async { loadA() }
val bDeferred = async { loadB() }

val a = aDeferred.await()
val b = bDeferred.await()
```

## 4. Structured Concurrency

Structured concurrency means coroutines have a parent-child relationship.

If the parent is cancelled, children are cancelled.

If a child fails, the parent usually fails and cancels siblings.

Example:

```kotlin
suspend fun loadScreen(): ScreenData = coroutineScope {
    val user = async { userRepository.getUser() }
    val posts = async { postRepository.getPosts() }

    ScreenData(
        user = user.await(),
        posts = posts.await()
    )
}
```

If `getUser()` fails, `getPosts()` is cancelled. This is correct when the screen needs both pieces of data.

Use `supervisorScope` when partial success is okay:

```kotlin
suspend fun loadDashboard(): Dashboard = supervisorScope {
    val profile = async {
        runCatching { userRepository.getProfile() }
    }

    val recommendations = async {
        runCatching { recommendationRepository.getRecommendations() }
    }

    Dashboard(
        profile = profile.await().getOrNull(),
        recommendations = recommendations.await().getOrDefault(emptyList())
    )
}
```

Interview line:

```text
coroutineScope is all-or-nothing. supervisorScope allows sibling coroutines to fail independently.
```

## 5. Why Not Expose `MutableStateFlow`

This was another important question.

Bad:

```kotlin
class ProfileViewModel : ViewModel() {
    val uiState = MutableStateFlow(ProfileUiState())
}
```

Problem:

```kotlin
viewModel.uiState.value = ProfileUiState(error = "Changed from outside")
```

If the Fragment, Activity, or Composable can mutate ViewModel state directly, state ownership is broken.

The ViewModel should be the single owner of UI state changes.

Better:

```kotlin
class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)

            val profile = repository.getProfile()

            _uiState.value = ProfileUiState(profile = profile)
        }
    }
}
```

Inside the ViewModel:

```text
Can read and write _uiState
```

Outside the ViewModel:

```text
Can only observe uiState
```

The UI can collect:

```kotlin
viewModel.uiState.collect { state ->
    render(state)
}
```

But the UI cannot do:

```kotlin
viewModel.uiState.value = ProfileUiState()
```

Because `StateFlow` is read-only from the consumer's perspective.

Interview answer:

```text
I expose StateFlow instead of MutableStateFlow to preserve state ownership and unidirectional data flow. The ViewModel mutates state internally, while the UI only observes it.
```

This gives:

- Predictable state changes
- Easier debugging
- Easier testing
- Clear ownership
- Better architecture for larger teams

## 6. `GlobalScope`

`GlobalScope` creates coroutines that are not tied to a lifecycle.

Example:

```kotlin
GlobalScope.launch {
    repository.uploadLogs()
}
```

Problem:

- It is not tied to a ViewModel.
- It is not tied to a screen.
- It is not tied to a parent operation.
- It can continue after the caller no longer cares.
- Cancellation and failure become harder to reason about.

This escapes structured concurrency.

### Is There Ever A Use Case?

Yes, but it should be extremely rare in Android app code.

For process-lifetime work, prefer an injected application-level scope.

Example:

```kotlin
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + ioDispatcher)
    }
}
```

Usage:

```kotlin
class LogUploader @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope
) {
    fun uploadLogs() {
        appScope.launch {
            sendPendingLogs()
        }
    }
}
```

This is better than `GlobalScope` because:

- It is explicit.
- It is injectable.
- It is testable.
- It has a clear owner.
- It can use a controlled dispatcher.

Examples where app-level scope may be okay:

- Analytics upload
- In-memory cache warmup
- Process-lifetime websocket
- Non-critical log upload
- App-wide sync coordination while the process is alive

But if the work must survive app process death, use `WorkManager`.

Example:

```kotlin
val request = OneTimeWorkRequestBuilder<UploadLogsWorker>().build()

WorkManager
    .getInstance(context)
    .enqueue(request)
```

Interview answer:

```text
I almost never use GlobalScope in app code. If I need process-lifetime coroutine work, I inject an application-level CoroutineScope with a SupervisorJob. If the work must be guaranteed, retryable, constrained, or survive process death, I use WorkManager.
```

## 7. Flow vs StateFlow

This was the biggest topic.

Simple mental model:

```text
Flow = a stream or recipe for producing values
StateFlow = observable state holder with a current value
```

Another way:

```text
Flow asks: what values are emitted over time?
StateFlow asks: what is the current state right now?
```

## 8. Flow

`Flow` represents values emitted over time.

Most flows are cold.

Cold means:

```text
The flow does not run until it is collected.
```

Example:

```kotlin
fun searchUsers(query: String): Flow<List<User>> = flow {
    emit(api.searchUsers(query))
}
```

This does not start the API call:

```kotlin
val usersFlow = repository.searchUsers("swati")
```

The API call starts here:

```kotlin
usersFlow.collect { users ->
    println(users)
}
```

If two collectors collect the same cold flow, the work runs twice:

```kotlin
val usersFlow = repository.searchUsers("swati")

usersFlow.collect { users ->
    println("Collector 1: $users")
}

usersFlow.collect { users ->
    println("Collector 2: $users")
}
```

Conceptually:

```text
Collector 1 starts -> API call runs
Collector 2 starts -> API call runs again
```

Good uses for `Flow`:

- Room database streams
- Search query streams
- Network polling streams
- Transformation pipelines
- Streams that should start when collected

Example Room-style repository:

```kotlin
class UserRepository(
    private val userDao: UserDao
) {
    fun observeUser(userId: String): Flow<User> {
        return userDao.observeUser(userId)
    }
}
```

## 9. Flow Operators

Common operators:

```kotlin
map              // Transform each emitted value
filter           // Keep matching values
debounce         // Wait until values stop changing briefly
distinctUntilChanged // Ignore repeated same values
flatMapLatest    // Cancel previous inner flow when a new value arrives
combine          // Combine latest values from multiple flows
catch            // Handle upstream exceptions
onEach           // Run side effects
stateIn          // Convert Flow to StateFlow
shareIn          // Convert Flow to SharedFlow
```

Search example:

```kotlin
val resultsFlow: Flow<List<User>> =
    queryFlow
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.searchUsers(query)
        }
        .catch {
            emit(emptyList())
        }
```

What `flatMapLatest` does:

```text
User types "s"    -> start search for "s"
User types "sw"   -> cancel search for "s", start search for "sw"
User types "swa"  -> cancel search for "sw", start search for "swa"
```

This is ideal for search because old results are no longer relevant.

## 10. StateFlow

`StateFlow` is hot and state-holding.

Hot means:

```text
It exists and holds state whether or not someone is collecting.
```

State-holding means:

```text
It always has a current value.
```

Example:

```kotlin
data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val error: String? = null
)
```

ViewModel:

```kotlin
class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)

            try {
                val profile = repository.getProfile()
                _uiState.value = ProfileUiState(profile = profile)
            } catch (e: IOException) {
                _uiState.value = ProfileUiState(error = "Failed to load profile")
            }
        }
    }
}
```

If a screen starts collecting later, it immediately receives the latest value.

Example:

```text
Current StateFlow value = Success(profile)
New collector starts
New collector immediately receives Success(profile)
```

That is why `StateFlow` is perfect for UI state.

## 11. Flow vs StateFlow In Android Architecture

Repositories often expose `Flow`.

ViewModels often expose `StateFlow`.

Example repository:

```kotlin
class UserRepository(
    private val userDao: UserDao
) {
    fun observeUser(): Flow<User> {
        return userDao.observeUser()
    }
}
```

Example ViewModel:

```kotlin
class UserViewModel(
    repository: UserRepository
) : ViewModel() {

    val uiState: StateFlow<UserUiState> =
        repository.observeUser()
            .map { user ->
                UserUiState(user = user)
            }
            .catch {
                emit(UserUiState(error = "Could not load user"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UserUiState(isLoading = true)
            )
}
```

Here:

- Repository exposes a stream of domain data.
- ViewModel converts it into UI state.
- `stateIn` converts `Flow<UserUiState>` into `StateFlow<UserUiState>`.
- UI gets an immediate initial value.
- UI always has the latest state.

## 12. `stateIn`

`stateIn` converts a cold `Flow` into a hot `StateFlow`.

Example:

```kotlin
val uiState: StateFlow<UserUiState> =
    repository.observeUser()
        .map { user -> UserUiState(user = user) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserUiState(isLoading = true)
        )
```

Arguments:

```kotlin
scope = viewModelScope
```

The StateFlow is active within the ViewModel lifecycle.

```kotlin
started = SharingStarted.WhileSubscribed(5_000)
```

The upstream flow stays active while there are subscribers. After the last subscriber disappears, it waits 5 seconds before stopping. This helps avoid restarting expensive flows during quick configuration changes.

```kotlin
initialValue = UserUiState(isLoading = true)
```

StateFlow must always have a value, so we provide the first state.

Interview line:

```text
stateIn is commonly used in ViewModels to convert repository Flow streams into lifecycle-owned StateFlow UI state.
```

## 13. Lifecycle-Aware Collection

In a Fragment or Activity:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            render(state)
        }
    }
}
```

Why:

- Collection starts when lifecycle is at least `STARTED`.
- Collection stops when lifecycle goes below `STARTED`.
- Prevents unnecessary work when UI is not visible.
- Avoids updating stopped UI.

In Compose:

```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileContent(state)
}
```

Interview line:

```text
In Android UI, I collect flows lifecycle-aware using repeatOnLifecycle in Views or collectAsStateWithLifecycle in Compose.
```

## 14. Flow vs StateFlow Quick Comparison

```text
Flow:
Cold by default
Does not require an initial value
Starts when collected
May run separately for each collector
Good for streams and pipelines
Often exposed by repositories
```

```text
StateFlow:
Hot
Requires an initial value
Always has a current value
Immediately emits latest value to new collectors
Good for UI state
Often exposed by ViewModels
```

## 15. Interview Questions And Answers

### What is the difference between `launch` and `async`?

`launch` starts a coroutine and returns a `Job`. It is used for work that does not produce a result.

`async` starts a coroutine and returns `Deferred<T>`. It is used when the coroutine produces a value that will be consumed with `await()`.

### Why can `async { ... }.await()` be sequential?

Because `await()` immediately pauses the current coroutine until that result is ready.

If you do:

```kotlin
val profile = async { getProfile() }.await()
val posts = async { getPosts() }.await()
```

Then `getPosts()` does not start until `getProfile()` has finished.

To run both concurrently:

```kotlin
val profileDeferred = async { getProfile() }
val postsDeferred = async { getPosts() }

val profile = profileDeferred.await()
val posts = postsDeferred.await()
```

### Why should we not expose `MutableStateFlow` publicly?

Because anything outside the ViewModel could mutate the state.

Instead:

```kotlin
private val _uiState = MutableStateFlow(ProfileUiState())
val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
```

This keeps state changes owned by the ViewModel and lets the UI only observe.

### Is there ever a reason to use `GlobalScope`?

Very rarely. Most app code should not use it.

If work should live as long as the app process, inject an application-level `CoroutineScope`.

If work must survive app process death, use `WorkManager`.

### What is the difference between `Flow` and `StateFlow`?

`Flow` is usually a cold stream. It starts when collected and emits values over time.

`StateFlow` is a hot state holder. It always has a current value and immediately gives the latest value to new collectors.

### Where should `Flow` and `StateFlow` be used?

Use `Flow` in repositories and data sources for streams of data.

Use `StateFlow` in ViewModels for UI state.

### Why use `stateIn`?

To convert a `Flow` into a `StateFlow`.

This is useful when turning repository streams into UI state:

```kotlin
val uiState = repository.observeUser()
    .map { UserUiState(user = it) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserUiState(isLoading = true)
    )
```

## 16. Final Lead-Level Summary

For an Android lead interview, a strong answer sounds like this:

```text
I use structured concurrency so coroutine lifetimes are explicit and cancellable.
ViewModels launch UI-related work in viewModelScope.
Repositories expose suspend functions for one-shot work and Flow for streams.
ViewModels expose immutable StateFlow for UI state.
I use async only when I need concurrent results, and I start all async work before awaiting.
I avoid GlobalScope because it escapes lifecycle ownership; for process-level work I inject an application scope, and for guaranteed background work I use WorkManager.
```

