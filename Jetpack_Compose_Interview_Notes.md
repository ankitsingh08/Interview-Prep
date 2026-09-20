# Jetpack Compose Interview Notes For Android Lead Roles

These notes focus on Jetpack Compose from an Android lead engineer interview perspective.

Core topics covered:

- Declarative UI mental model
- Composition, recomposition, layout, and draw
- Slot table and `remember`
- Positional identity and keys
- Stable vs unstable types
- Strong skipping
- State hoisting
- Side effects
- Debugging recomposition issues
- Performance and architecture patterns

## 1. Compose Mental Model

Jetpack Compose is a declarative UI toolkit.

Instead of manually mutating views, you describe UI as a function of state.

```kotlin
@Composable
fun ProfileScreen(state: ProfileUiState) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Text(text = state.name)
    }
}
```

The important idea:

```text
State changes -> Compose schedules recomposition -> affected UI is updated
```

Compose does not simply redraw the entire screen every time. It tracks state reads and recomposes the parts of the composition that depend on changed state.

Interview answer:

```text
Compose is declarative. A composable describes what the UI should look like for a given state. When state changes, Compose invalidates the composable scopes that read that state, recomposes them, skips eligible unchanged parts, and applies the resulting changes to the UI tree.
```

## 2. The Three Main Phases

Compose work can be thought of in three major phases:

```text
Composition: What UI should exist?
Layout: Where should it be placed and how big should it be?
Draw: How should it be painted?
```

### Composition

This is where composable functions run.

Example:

```kotlin
@Composable
fun Greeting(name: String) {
    Text("Hello $name")
}
```

During composition, Compose builds or updates its internal representation of the UI.

### Layout

Compose measures and places UI nodes.

Example:

```kotlin
Column {
    Text("Title")
    Button(onClick = {}) {
        Text("Continue")
    }
}
```

The `Column` decides how to place its children vertically.

### Draw

Compose paints pixels.

Example:

```kotlin
Box(
    modifier = Modifier
        .background(Color.Red)
        .size(100.dp)
)
```

The background and size affect layout/draw behavior.

Interview detail:

```text
Recomposition is only one phase. Some changes can skip composition and only trigger layout or draw work, depending on where state is read.
```

## 3. Recomposition

Recomposition means Compose re-executes composable functions whose inputs or observed state changed.

Example:

```kotlin
@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }

    Column {
        Text("Count: $count")
        Button(onClick = { count++ }) {
            Text("Increment")
        }
    }
}
```

When `count` changes:

```text
1. Compose sees that mutable state changed.
2. Compose invalidates the scope that read count.
3. Compose schedules recomposition.
4. The affected composable code runs again.
5. Compose applies only necessary UI changes.
```

Recomposition is normal. It is not automatically a performance problem.

The real problems are:

- Recomposition happening much more often than needed
- Recomposition doing expensive work
- Large parts of the UI recomposing because state is read too high
- Composables failing to skip because of unstable parameters

## 4. State Reads Drive Recomposition

Compose tracks state reads.

Example:

```kotlin
@Composable
fun CounterText(count: Int) {
    Text("Count: $count")
}
```

If `count` changes, `CounterText` may need to recompose.

Better structure:

```kotlin
@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }

    Column {
        CounterText(count = count)
        IncrementButton(onClick = { count++ })
    }
}

@Composable
fun CounterText(count: Int) {
    Text("Count: $count")
}

@Composable
fun IncrementButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("Increment")
    }
}
```

Why this is better:

- `CounterText` depends on `count`.
- `IncrementButton` does not need `count`.
- Compose has a better chance to recompose only the part that needs the changing state.

Lead-level principle:

```text
Read state as low as reasonably possible in the UI tree.
```

## 5. Slot Table

The slot table is Compose runtime's internal memory of the composition.

It stores information such as:

- Which composables were called
- Where they appeared
- Groups and keys
- `remember` values
- Data needed to restart or skip parts of the composition

This is why `remember` works:

```kotlin
@Composable
fun SearchBox() {
    var query by remember { mutableStateOf("") }

    TextField(
        value = query,
        onValueChange = { query = it }
    )
}
```

`query` is not stored in a normal local variable. The value is remembered by Compose in the slot table at that call position.

Interview answer:

```text
The slot table is the runtime data structure Compose uses to remember the composition. It stores groups, remembered values, and identity information so Compose can match composable calls across recompositions.
```

## 6. Positional Identity

Compose often identifies composables by where they are called.

Example:

```kotlin
@Composable
fun UserList(users: List<User>) {
    Column {
        users.forEach { user ->
            UserRow(user)
        }
    }
}
```

If the list order changes, Compose may have trouble preserving identity because the same call position now points to a different user.

In lazy lists, use keys:

```kotlin
LazyColumn {
    items(
        items = users,
        key = { user -> user.id }
    ) { user ->
        UserRow(user)
    }
}
```

Why keys matter:

- Preserve item identity
- Help Compose reuse composition correctly
- Prevent unnecessary recomposition
- Preserve item state when items move

Example with item state:

```kotlin
@Composable
fun UserRow(user: User) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(user.name)
        if (expanded) {
            Text(user.bio)
        }
    }
}
```

Without stable keys, item state like `expanded` can appear to jump to the wrong row after inserts, deletes, or moves.

Interview line:

```text
Compose uses positional identity by default, but keys allow me to provide stable identity for dynamic content, especially lazy lists.
```

## 7. `remember` vs `rememberSaveable`

Use `remember` for state that should survive recomposition.

```kotlin
var selectedTab by remember { mutableStateOf(0) }
```

This survives recomposition but not Activity recreation.

Use `rememberSaveable` for state that should survive configuration change or process recreation when possible.

```kotlin
var query by rememberSaveable { mutableStateOf("") }
```

Good use cases:

- Search text
- Selected tab
- Expanded/collapsed flags
- Simple UI state

Do not store large business state directly in `rememberSaveable`. Put business state in the ViewModel.

Interview line:

```text
remember stores state in the composition. rememberSaveable also saves state through the saved instance state mechanism for supported types.
```

## 8. Stability

Stability helps Compose decide whether it can skip a composable.

A type is stable when Compose can trust that either:

- The value cannot change unexpectedly
- Or Compose will be notified when it changes

Stable examples:

- `String`
- `Int`
- `Boolean`
- Lambdas
- `MutableState<T>`
- Immutable data classes with stable properties

Potentially unstable example:

```kotlin
data class User(
    var name: String
)
```

This is risky because `name` can change without Compose necessarily being notified.

Better:

```kotlin
@Immutable
data class UserUiModel(
    val id: String,
    val name: String,
    val avatarUrl: String
)
```

Important warning:

```text
Do not add @Stable or @Immutable just to silence Compose. These annotations are contracts. If the type lies, the UI can become incorrect.
```

## 9. Stable vs Unstable Parameters

Example:

```kotlin
@Composable
fun UserRow(user: UserUiModel) {
    Text(user.name)
}
```

If `UserUiModel` is stable and the same value is passed again, Compose can often skip `UserRow`.

Unstable collection example:

```kotlin
data class FeedUiState(
    val posts: List<Post>
)
```

Even though `val` looks immutable, `List` is only read-only from Kotlin's type perspective. Compose may not be able to prove the underlying collection cannot mutate.

Possible options:

```kotlin
data class FeedUiState(
    val posts: ImmutableList<Post>
)
```

Or map your data into stable UI models and avoid creating new list instances unnecessarily.

Interview answer:

```text
Stable types help Compose skip recomposition safely. I prefer immutable UI models with val properties, avoid mutable public state, and use compiler reports to verify stability instead of guessing.
```

## 10. Strong Skipping

Modern Compose compiler behavior includes strong skipping by default with Kotlin 2.0.20 and newer.

The important interview-level idea:

```text
Strong skipping allows more restartable composables to be skipped, including composables with unstable parameters.
```

Comparison:

```text
Stable parameters: compared using equals()
Unstable parameters: compared using instance equality ===
```

Example:

```kotlin
@Composable
fun FeedScreen(uiState: FeedUiState) {
    FeedList(posts = uiState.posts)
}
```

If `uiState.posts` is an unstable list but the same list instance is passed again, strong skipping may allow skipping. But if a new list instance is created every time, Compose may still recompose.

Bad:

```kotlin
@Composable
fun FeedScreen(posts: List<Post>) {
    FeedList(posts = posts.map { it.toUiModel() })
}
```

This creates a new list during composition on every recomposition.

Better:

```kotlin
@Composable
fun FeedScreen(posts: List<Post>) {
    val uiPosts = remember(posts) {
        posts.map { it.toUiModel() }
    }

    FeedList(posts = uiPosts)
}
```

Often even better: do this mapping in the ViewModel.

```kotlin
val uiState: StateFlow<FeedUiState> =
    repository.observePosts()
        .map { posts ->
            FeedUiState(posts = posts.map { it.toUiModel() })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState()
        )
```

Lead-level caution:

```text
Strong skipping improves performance characteristics, but it does not replace good state modeling. I still design stable UI state and avoid unnecessary object creation in composition.
```

## 11. State Hoisting

State hoisting means moving state up to the caller and passing state plus events down.

Stateful composable:

```kotlin
@Composable
fun SearchBox() {
    var query by remember { mutableStateOf("") }

    TextField(
        value = query,
        onValueChange = { query = it }
    )
}
```

Hoisted version:

```kotlin
@Composable
fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange
    )
}
```

ViewModel usage:

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SearchBox(
        query = state.query,
        onQueryChange = viewModel::onQueryChange
    )
}
```

Benefits:

- More reusable composables
- Easier testing
- Clear unidirectional data flow
- Better separation between UI and business logic

Interview line:

```text
I hoist state when the caller needs to control it, when the state affects siblings, or when the state belongs to business logic rather than local UI behavior.
```

## 12. ViewModel And Compose State

Common Android pattern:

```kotlin
class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
}
```

Compose:

```kotlin
@Composable
fun ProfileRoute(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        onRetry = viewModel::retry
    )
}
```

Split route and screen:

```kotlin
@Composable
fun ProfileRoute(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit
) {
    // Pure UI
}
```

Why this is good:

- Route knows ViewModel.
- Screen is stateless and previewable.
- UI is easier to test.
- State flow is lifecycle-aware.

## 13. Side Effects

Composable functions should ideally be side-effect free.

Bad:

```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, userId: String) {
    viewModel.loadUser(userId)
}
```

This can run many times because composables can recompose.

Better:

```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, userId: String) {
    LaunchedEffect(userId) {
        viewModel.loadUser(userId)
    }
}
```

`LaunchedEffect` runs when it enters composition and restarts when its key changes.

## 14. Common Effect APIs

### `LaunchedEffect`

Use for coroutine work tied to composition.

```kotlin
LaunchedEffect(userId) {
    viewModel.loadUser(userId)
}
```

Restarts when `userId` changes.

### `DisposableEffect`

Use when setup needs cleanup.

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            analytics.trackVisible()
        }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

### `rememberUpdatedState`

Use when an effect should not restart, but should use the latest value.

```kotlin
@Composable
fun Timeout(
    onTimeout: () -> Unit
) {
    val latestOnTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(Unit) {
        delay(3_000)
        latestOnTimeout()
    }
}
```

Without `rememberUpdatedState`, the effect may capture an old lambda.

### `SideEffect`

Runs after every successful recomposition.

```kotlin
SideEffect {
    analytics.setUserProperty("screen", "Profile")
}
```

Use carefully because it runs often.

### `produceState`

Converts external async data into Compose `State`.

```kotlin
@Composable
fun rememberProfile(userId: String): State<Profile?> {
    return produceState<Profile?>(initialValue = null, userId) {
        value = repository.loadProfile(userId)
    }
}
```

### `snapshotFlow`

Converts Compose state reads into a Flow.

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index ->
            analytics.trackFirstVisibleItem(index)
        }
}
```

Useful for observing Compose state with Flow operators.

## 15. `derivedStateOf`

Use `derivedStateOf` when:

- Input state changes frequently
- Derived output changes less frequently
- Recomposition should happen only when the derived result changes

Example:

```kotlin
@Composable
fun FeedScreen(listState: LazyListState) {
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    Box {
        FeedList(listState)

        if (showScrollToTop) {
            ScrollToTopButton()
        }
    }
}
```

Why this helps:

```text
firstVisibleItemIndex can change many times during scroll.
showScrollToTop only changes when index crosses 0.
```

Do not use it for every simple calculation.

Bad:

```kotlin
val fullName by remember {
    derivedStateOf { "$firstName $lastName" }
}
```

That is unnecessary overhead for a cheap calculation.

Interview line:

```text
derivedStateOf is useful when noisy input state changes more frequently than the UI result needs to update.
```

## 16. Avoid Expensive Work In Composition

Bad:

```kotlin
@Composable
fun UserList(users: List<User>) {
    val sortedUsers = users.sortedBy { it.name }

    LazyColumn {
        items(sortedUsers) { user ->
            UserRow(user)
        }
    }
}
```

This sorting may happen on every recomposition.

Better:

```kotlin
@Composable
fun UserList(users: List<User>) {
    val sortedUsers = remember(users) {
        users.sortedBy { it.name }
    }

    LazyColumn {
        items(
            items = sortedUsers,
            key = { user -> user.id }
        ) { user ->
            UserRow(user)
        }
    }
}
```

Often best:

```kotlin
class UserViewModel : ViewModel() {
    val uiState: StateFlow<UserUiState> =
        repository.observeUsers()
            .map { users ->
                UserUiState(
                    users = users.sortedBy { it.name }
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UserUiState()
            )
}
```

Lead-level thought:

```text
Composition should stay cheap. Expensive transformations usually belong in the ViewModel or should be memoized with remember when appropriate.
```

## 17. Debugging Recomposition Issues

Use this order:

```text
1. Confirm there is a real problem.
2. Use Layout Inspector to check recomposition and skip counts.
3. Use Compose compiler reports to inspect stability and skippability.
4. Check state reads and object creation.
5. Use traces or benchmarks for performance validation.
```

Do not optimize based only on intuition.

### Common Causes

- State read too high in the tree
- Passing unstable models
- Creating new lists or objects every recomposition
- Missing lazy list keys
- Expensive work directly inside composables
- Effects with changing keys
- Overusing `derivedStateOf` or `remember`
- Mutating objects without observable state

## 18. Layout Inspector

Android Studio Layout Inspector can show recomposition-related information.

Use it to ask:

- Which composables are recomposing often?
- Which composables are being skipped?
- Is recomposition happening where I expect?
- Is a large parent recomposing when only a small child needs to?

Interview line:

```text
I use Layout Inspector first to see whether recomposition is actually excessive and where it happens.
```

## 19. Compose Compiler Reports

Compiler reports help inspect:

- Which composables are restartable
- Which composables are skippable
- Which parameter types are stable or unstable
- Why Compose may not skip something

Example Gradle configuration:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

What to look for:

```text
restartable
skippable
stable
unstable
readonly
```

Lead-level use:

```text
When debugging Compose performance, I use compiler reports to verify stability instead of guessing whether a type is stable.
```

## 20. Recomposition Debug Example

Problem:

```kotlin
@Composable
fun FeedScreen(
    posts: List<Post>,
    scrollState: LazyListState
) {
    val showButton = scrollState.firstVisibleItemIndex > 0

    Column {
        Header(showButton = showButton)
        FeedList(posts = posts)
    }
}
```

Potential issue:

```text
firstVisibleItemIndex changes frequently during scroll.
FeedScreen may recompose frequently.
Header and FeedList sit under that recomposition scope.
```

Improved:

```kotlin
@Composable
fun FeedScreen(
    posts: List<Post>,
    scrollState: LazyListState
) {
    Column {
        Header(scrollState = scrollState)
        FeedList(posts = posts, scrollState = scrollState)
    }
}

@Composable
fun Header(scrollState: LazyListState) {
    val showButton by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0
        }
    }

    AnimatedVisibility(visible = showButton) {
        ScrollToTopButton()
    }
}
```

Why:

- Fast-changing state is read closer to where it is needed.
- Derived state reduces updates to meaningful changes.
- The whole screen is less likely to be affected.

## 21. Lazy Lists

Use stable keys:

```kotlin
LazyColumn {
    items(
        items = posts,
        key = { post -> post.id }
    ) { post ->
        PostRow(post)
    }
}
```

Avoid creating item data inside `items` when possible.

Bad:

```kotlin
LazyColumn {
    items(posts) { post ->
        PostRow(post.toUiModel())
    }
}
```

Better:

```kotlin
LazyColumn {
    items(
        items = postUiModels,
        key = { post -> post.id }
    ) { post ->
        PostRow(post)
    }
}
```

Important:

```text
LazyColumn composes only visible items plus some buffer, but poor keys or unstable item models can still create performance and state bugs.
```

## 22. Compose And Unidirectional Data Flow

Common flow:

```text
User action -> ViewModel event -> StateFlow update -> Compose observes state -> UI recomposes
```

Example:

```kotlin
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
```

```kotlin
sealed interface LoginAction {
    data class EmailChanged(val email: String) : LoginAction
    data class PasswordChanged(val password: String) : LoginAction
    data object Submit : LoginAction
}
```

```kotlin
@Composable
fun LoginScreen(
    state: LoginUiState,
    onAction: (LoginAction) -> Unit
) {
    TextField(
        value = state.email,
        onValueChange = { onAction(LoginAction.EmailChanged(it)) }
    )

    TextField(
        value = state.password,
        onValueChange = { onAction(LoginAction.PasswordChanged(it)) }
    )

    Button(
        onClick = { onAction(LoginAction.Submit) },
        enabled = !state.isLoading
    ) {
        Text("Login")
    }
}
```

Lead-level answer:

```text
I keep Compose UI mostly stateless, pass state down, send events up, and let ViewModels own business state.
```

## 23. Navigation Compose

Navigation in Compose should usually stay in the UI layer because `NavController` is UI state.

A common pattern:

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "profile/{userId}"
    ) {
        composable("profile/{userId}") { backStackEntry ->
            val userId = requireNotNull(backStackEntry.arguments?.getString("userId"))

            ProfileRoute(
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

Lead-level guidance:

- Keep `NavController` out of deeply nested UI when possible.
- Pass simple lambdas like `onBack` and `onOpenSettings`.
- Pass IDs through navigation, not large domain objects.
- Scope ViewModels carefully: destination-scoped by default, graph-scoped when screens share state.
- Treat deep links and back stack behavior as part of the feature design.

Good interview answer:

```text
I keep navigation execution in the UI layer because NavController is UI state. The ViewModel can expose navigation intents when business logic decides navigation should happen, but the composable usually performs the actual navigate call.
```

## 24. One-Off Events

One-off events include:

- Navigation
- Snackbars
- Toasts
- Dialog triggers
- Permission prompts

Be careful putting one-off events directly in persistent UI state.

Potentially problematic:

```kotlin
data class ProfileUiState(
    val user: User? = null,
    val snackbarMessage: String? = null
)
```

Why this can be risky:

```text
State is replayed to new collectors.
After rotation, the snackbar message may be shown again.
```

One common solution is `SharedFlow`:

```kotlin
sealed interface ProfileEvent {
    data class ShowSnackbar(val message: String) : ProfileEvent
    data object NavigateBack : ProfileEvent
}
```

```kotlin
class ProfileViewModel : ViewModel() {
    private val _events = MutableSharedFlow<ProfileEvent>()
    val events = _events.asSharedFlow()

    fun onSaveClicked() {
        viewModelScope.launch {
            repository.save()
            _events.emit(ProfileEvent.ShowSnackbar("Saved"))
        }
    }
}
```

Compose collection:

```kotlin
@Composable
fun ProfileRoute(viewModel: ProfileViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                ProfileEvent.NavigateBack -> {
                    // Call onBack or navController.popBackStack()
                }
            }
        }
    }
}
```

Lead-level nuance:

```text
For durable UI facts, I use StateFlow. For transient one-off effects, I usually use SharedFlow or an explicit event mechanism. I avoid replaying one-off events accidentally after configuration changes.
```

## 25. CompositionLocal

`CompositionLocal` provides values implicitly through the composition tree.

Common built-in examples:

- `LocalContext`
- `LocalLifecycleOwner`
- `LocalDensity`
- `LocalLayoutDirection`
- `MaterialTheme`

Custom example:

```kotlin
val LocalAnalytics = staticCompositionLocalOf<Analytics> {
    error("No Analytics provided")
}
```

Provide:

```kotlin
CompositionLocalProvider(LocalAnalytics provides analytics) {
    App()
}
```

Consume:

```kotlin
@Composable
fun ProfileScreen() {
    val analytics = LocalAnalytics.current

    LaunchedEffect(Unit) {
        analytics.trackScreen("Profile")
    }
}
```

When to use it:

- Theme values
- Locale/density/layout direction
- Cross-cutting dependencies used widely
- Design system values

When not to use it:

- Ordinary screen state
- Business data that should be explicit
- Dependencies that make composables harder to test

Interview answer:

```text
CompositionLocal is useful for ambient dependencies, but I avoid using it as hidden global state. If a value is important to a composable's behavior, I prefer passing it explicitly unless it is truly cross-cutting.
```

## 26. Modifiers

Modifiers decorate or add behavior to composables.

Modifier order matters.

Example:

```kotlin
Modifier
    .padding(16.dp)
    .background(Color.Red)
```

This applies padding before the background, so the red background is inside the padded area.

Different:

```kotlin
Modifier
    .background(Color.Red)
    .padding(16.dp)
```

This draws the red background first, then applies padding inside it.

Common modifier categories:

- Layout: `padding`, `size`, `fillMaxWidth`, `weight`
- Drawing: `background`, `clip`, `border`, `alpha`
- Input: `clickable`, `pointerInput`
- Semantics: `semantics`, `testTag`
- Parent data: `weight`, `align`

Custom modifier example:

```kotlin
fun Modifier.cardSurface(): Modifier =
    this
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(8.dp)
        )
```

Lead-level answer:

```text
Modifiers are ordered chains of behavior. I pay close attention to order because layout, drawing, input, and semantics modifiers can produce different results depending on where they appear.
```

## 27. Custom Layout

Compose layout follows a constraint-based model:

```text
Parents pass constraints down.
Children choose a size within those constraints.
Parents place children.
```

Simple custom row:

```kotlin
@Composable
fun CustomRow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        val width = placeables.sumOf { it.width }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(width, height) {
            var x = 0

            placeables.forEach { placeable ->
                placeable.placeRelative(x = x, y = 0)
                x += placeable.width
            }
        }
    }
}
```

Important details:

- A child should generally be measured once per layout pass.
- Constraints define min/max width and height.
- Placement happens after measurement.
- Intrinsics are advanced and can be expensive.

Interview line:

```text
In Compose layout, measurement and placement are explicit. Parent layouts pass constraints to children, children report a size, and then the parent places them.
```

## 28. Compose And View Interop

Migration from XML/View-based UI can be incremental.

Use `ComposeView` to host Compose in a View screen.

```kotlin
class ProfileFragment : Fragment(R.layout.profile_fragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.compose_view).setContent {
            ProfileRoute()
        }
    }
}
```

Use `AndroidView` to host a traditional View inside Compose.

```kotlin
@Composable
fun MapContent(location: Location) {
    AndroidView(
        factory = { context ->
            MapView(context)
        },
        update = { mapView ->
            // Update existing view from Compose state
        }
    )
}
```

Use cases:

- Maps
- Camera previews
- Legacy custom views
- Video players
- Incremental migration

Lead-level answer:

```text
Compose migration does not need to be all-or-nothing. I can host Compose inside Views with ComposeView and host Views inside Compose with AndroidView, which lets teams migrate feature by feature.
```

## 29. Compose Testing

Compose tests use the semantics tree.

Example:

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun loginButtonIsDisabledWhenLoading() {
    composeTestRule.setContent {
        LoginScreen(
            state = LoginUiState(isLoading = true),
            onAction = {}
        )
    }

    composeTestRule
        .onNodeWithText("Login")
        .assertIsNotEnabled()
}
```

Testing with tags:

```kotlin
Button(
    modifier = Modifier.testTag("loginButton"),
    onClick = onLogin
) {
    Text("Login")
}
```

```kotlin
composeTestRule
    .onNodeWithTag("loginButton")
    .assertExists()
```

Lead-level testing strategy:

- Prefer testing user-visible behavior through semantics.
- Use test tags when text is dynamic, duplicated, or not user-visible.
- Keep ViewModel logic tested separately with coroutine tests.
- Use screenshot tests when visual regressions matter.
- Make stateless screen composables easy to preview and test.

Interview answer:

```text
I test Compose screens through the semantics tree, assert user-visible behavior, and keep business logic in ViewModels where it can be unit tested separately.
```

## 30. Accessibility

Accessibility is a lead-level topic because Compose makes it easy to build custom UI that looks fine but behaves poorly for assistive tech.

Important areas:

- Content descriptions
- Touch target size
- Text scaling
- Contrast
- Semantics
- Focus order
- Screen reader behavior

Icon button example:

```kotlin
IconButton(onClick = onBack) {
    Icon(
        imageVector = Icons.Default.ArrowBack,
        contentDescription = "Back"
    )
}
```

Decorative icon:

```kotlin
Icon(
    imageVector = Icons.Default.Check,
    contentDescription = null
)
```

Custom semantics:

```kotlin
Box(
    modifier = Modifier.semantics {
        contentDescription = "Playback progress"
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = progress,
            range = 0f..1f
        )
    }
)
```

Interview line:

```text
I treat accessibility as part of the component contract. For custom UI, I verify semantics, content descriptions, touch targets, font scale, and screen reader behavior.
```

## 31. Animation

Common animation APIs:

- `animate*AsState`
- `AnimatedVisibility`
- `AnimatedContent`
- `updateTransition`
- `Animatable`

Simple state-driven animation:

```kotlin
val alpha by animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    label = "profile-alpha"
)

Box(
    modifier = Modifier.alpha(alpha)
)
```

Visibility:

```kotlin
AnimatedVisibility(visible = showActions) {
    ActionRow()
}
```

More controlled animation:

```kotlin
val offset = remember { Animatable(0f) }

LaunchedEffect(isExpanded) {
    offset.animateTo(
        targetValue = if (isExpanded) 1f else 0f
    )
}
```

Interview distinction:

```text
animate*AsState is good for simple state-driven animations. Animatable gives more control for coroutine-driven, interruptible, or gesture-connected animations.
```

## 32. Paging With Compose

Paging is common in real production Compose apps.

ViewModel:

```kotlin
val posts: Flow<PagingData<PostUiModel>> =
    repository.observePagedPosts()
        .cachedIn(viewModelScope)
```

Compose:

```kotlin
@Composable
fun FeedRoute(viewModel: FeedViewModel) {
    val posts = viewModel.posts.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = posts.itemCount,
            key = posts.itemKey { post -> post.id }
        ) { index ->
            val post = posts[index]

            if (post != null) {
                PostRow(post)
            } else {
                PlaceholderPostRow()
            }
        }
    }
}
```

Handle load states:

```kotlin
when (val refresh = posts.loadState.refresh) {
    is LoadState.Loading -> FullScreenLoading()
    is LoadState.Error -> ErrorContent(onRetry = posts::retry)
    is LoadState.NotLoading -> Unit
}
```

Lead-level points:

- Use `cachedIn(viewModelScope)`.
- Handle refresh, append, and error states.
- Provide stable keys.
- Support retry.
- Consider placeholders where useful.

## 33. Adaptive UI And Large Screens

Lead interviews increasingly care about foldables, tablets, and adaptive layouts.

Topics to know:

- Window size classes
- Navigation rail vs bottom navigation
- List-detail layouts
- Foldable posture when relevant
- Avoiding phone-only assumptions

Simple idea:

```kotlin
@Composable
fun HomeScreen(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactHome()
        WindowWidthSizeClass.Medium -> MediumHome()
        WindowWidthSizeClass.Expanded -> ExpandedHome()
    }
}
```

Interview answer:

```text
For lead-level Compose architecture, I design screens around adaptive layout decisions, not just fixed phone layouts. Navigation structure, content density, and pane count can change based on available width.
```

## 34. Preview Strategy

Previews are useful when composables are stateless and take explicit state.

```kotlin
@Preview
@Composable
fun ProfileScreenLoadingPreview() {
    AppTheme {
        ProfileScreen(
            state = ProfileUiState(isLoading = true),
            onAction = {}
        )
    }
}
```

Preview multiple states:

- Loading
- Empty
- Error
- Success
- Long text
- Dark theme
- Large font scale

Lead-level answer:

```text
Good previewability is a design signal. If a screen is hard to preview, it often means it is too coupled to ViewModel, navigation, or runtime dependencies.
```

## 35. Common Interview Questions

### How does recomposition work?

Compose tracks state reads during composition. When state changes, Compose invalidates the scopes that read that state. On the next frame, it recomposes those scopes, skips eligible unchanged composables, and applies changes to the UI tree.

### What is the slot table?

The slot table is Compose runtime's internal structure for storing the composition. It keeps groups, call positions, remembered values, and identity information. `remember` stores values in the slot table.

### Why do keys matter?

Compose uses positional identity by default. In dynamic lists, item positions can change. Keys give Compose stable identity, preserving item state and reducing unnecessary recomposition.

### What is stability?

Stability tells Compose whether it can trust a value to change predictably. Stable parameters allow Compose to skip recomposition more safely.

### What is strong skipping?

Strong skipping allows more restartable composables to be skipped, including composables with unstable parameters. Stable parameters are usually compared with `equals()`, while unstable parameters are compared with instance equality.

### What causes excessive recomposition?

Common causes are reading state too high in the tree, unstable parameters, missing lazy keys, creating new objects every recomposition, and doing expensive work inside composables.

### How do you debug recomposition?

Use Layout Inspector to see recomposition and skip counts. Use Compose compiler reports to inspect stability and skippability. Use traces and benchmarks to validate performance. Then fix state placement, model stability, keys, and expensive composition work.

### When should you use `remember`?

Use `remember` to keep a value across recompositions for the same call position.

### When should you use `rememberSaveable`?

Use `rememberSaveable` when simple UI state should survive configuration changes or process recreation through saved instance state.

### When should you use `derivedStateOf`?

Use it when input state changes frequently but the derived result changes less often, such as showing a button only after a list scrolls past the first item.

### Why should composables be side-effect free?

Composable functions can run many times. If side effects happen directly inside composables, they may run repeatedly and unpredictably. Use effect APIs like `LaunchedEffect`, `DisposableEffect`, and `SideEffect`.

### How do you structure a Compose screen in production?

I usually split it into a route composable and a screen composable. The route talks to the ViewModel, collects `StateFlow` lifecycle-aware, and handles navigation/event collection. The screen is mostly stateless and receives state plus callbacks.

### How do you handle one-off events?

I keep durable UI facts in `StateFlow` and use `SharedFlow`, channels, or an explicit event mechanism for transient effects like snackbars and navigation. I avoid accidentally replaying one-off events after configuration changes.

### When should you use CompositionLocal?

I use it for ambient, cross-cutting values like theme, density, layout direction, or carefully chosen app services. I avoid it for ordinary screen state because hidden dependencies make composables harder to reason about and test.

### Why does modifier order matter?

Modifiers are ordered. Layout, draw, input, and semantics modifiers can produce different behavior depending on their position in the chain.

### How does custom layout work?

Parents pass constraints to children. Children choose sizes within those constraints. Parents then place children. Custom layouts make measurement and placement explicit.

### How do you migrate from XML to Compose?

Incrementally. Use `ComposeView` to put Compose inside View-based screens and `AndroidView` to put legacy Views inside Compose. This lets teams migrate feature by feature.

### How do you test Compose UI?

I test through the semantics tree, prefer user-visible assertions, use test tags where needed, keep ViewModel logic in unit tests, and add screenshot tests when visual regressions are important.

### What accessibility issues do you check in Compose?

I check content descriptions, semantics, touch target sizes, font scaling, contrast, focus behavior, and screen reader behavior, especially for custom components.

## 36. Final Lead-Level Summary

A strong Android lead answer:

```text
Compose is a declarative UI runtime. It tracks state reads during composition and uses that information to invalidate and recompose only affected scopes. The slot table stores the composition, remembered values, and identity information. Recomposition is normal; performance issues usually come from poor state placement, unstable models, missing keys, object churn, or expensive work during composition.

For architecture, I expose StateFlow from ViewModels, collect it lifecycle-aware in Compose, keep screen composables mostly stateless, hoist state when needed, and use stable immutable UI models. I keep navigation execution in the UI layer, handle one-off events separately from durable state, and design screens to be testable, previewable, accessible, and adaptive.

For debugging, I verify with Layout Inspector, compiler reports, traces, and benchmarks before optimizing. I look for poor state placement, missing lazy keys, unstable models, object churn, expensive composition work, effect key mistakes, and accessibility gaps.
```

## 37. Official References

- Android Compose lifecycle and recomposition: https://developer.android.com/develop/ui/compose/lifecycle
- Compose stability: https://developer.android.com/develop/ui/compose/performance/stability
- Strong skipping: https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Compose side effects: https://developer.android.com/develop/ui/compose/side-effects
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Compose debugging tools: https://developer.android.com/develop/ui/compose/tooling/debug
- Navigation Compose: https://developer.android.com/develop/ui/compose/navigation
- Compose accessibility: https://developer.android.com/develop/ui/compose/accessibility
- Compose testing: https://developer.android.com/develop/ui/compose/testing
- Compose interoperability: https://developer.android.com/develop/ui/compose/migrate/interoperability-apis
- Compose adaptive layouts: https://developer.android.com/develop/ui/compose/layouts/adaptive
