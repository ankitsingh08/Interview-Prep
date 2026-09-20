# Kotlin Language Interview Notes For Android Lead Roles

These notes focus on Kotlin language depth for Android lead engineer interviews.

Core areas covered:

- Null safety and Java platform types
- `val`, `var`, and immutability
- Data classes and limitations
- Sealed classes vs sealed interfaces vs enums
- Higher-order functions
- `inline`, `noinline`, `crossinline`, and `reified`
- Generics variance: `in` and `out`
- Extension function dispatch
- Collections, sequences, and performance
- Suspend function internals
- Kotlin/JVM interoperability
- KSP/KAPT and build implications
- `lateinit` vs `lazy`
- Value classes vs type aliases
- Delegation, contracts, nested/inner classes, and API design

## 1. `val` vs `var`

`val` means the reference cannot be reassigned.

```kotlin
val name = "Ankit"
// name = "Rahul" // Error
```

`var` means the reference can be reassigned.

```kotlin
var count = 0
count++
```

Important nuance:

```kotlin
val users = mutableListOf("A", "B")
users.add("C") // Allowed
```

`val` prevents reassignment of the variable, but it does not make the object deeply immutable.

Interview answer:

```text
I prefer val by default because it reduces accidental mutation. For UI state and domain models, I prefer immutable data structures and explicit state replacement using copy().
```

## 2. Null Safety

Kotlin makes nullability part of the type system.

```kotlin
val name: String = "Ankit"
val nickname: String? = null
```

You cannot directly call members on nullable values.

```kotlin
fun printLength(value: String?) {
    println(value?.length)
}
```

Common tools:

```kotlin
user?.name              // Safe call
user?.name ?: "Guest"   // Elvis operator
user!!.name             // Force unwrap, risky
```

Android example:

```kotlin
val userId = savedStateHandle.get<String>("userId")
    ?: throw IllegalArgumentException("Missing userId")
```

Better helper:

```kotlin
fun missingArgument(name: String): Nothing {
    throw IllegalArgumentException("Missing argument: $name")
}
```

```kotlin
val userId = savedStateHandle.get<String>("userId")
    ?: missingArgument("userId")
```

Interview answer:

```text
Kotlin reduces null pointer crashes by making nullability explicit at compile time. I avoid !! except when there is a very strong invariant, and I validate nullable values at boundaries.
```

## 3. Java Platform Types

When Kotlin calls Java code, nullability may be unknown.

Java:

```java
class JavaUser {
    String getName() {
        return null;
    }
}
```

Kotlin sees this roughly as:

```kotlin
String!
```

This is called a platform type.

Risky:

```kotlin
val name = javaUser.name
println(name.length) // Could crash if Java returned null
```

Safer:

```kotlin
val name = javaUser.name ?: "Unknown"
```

Or fail early:

```kotlin
val userId = javaUser.id
    ?: error("Java API returned null userId")
```

Lead-level approach:

```text
I contain platform types at Java/Kotlin boundaries. I validate nullability as soon as values enter Kotlin code instead of letting String! spread through the app.
```

## 4. Smart Casts

Kotlin automatically casts after type checks.

```kotlin
fun handle(value: Any) {
    if (value is String) {
        println(value.length)
    }
}
```

With nullable values:

```kotlin
fun printName(user: User?) {
    if (user != null) {
        println(user.name)
    }
}
```

Smart casts are especially useful with sealed types.

```kotlin
sealed interface UiState {
    data object Loading : UiState
    data class Success(val user: User) : UiState
    data class Error(val message: String) : UiState
}
```

```kotlin
fun render(state: UiState) {
    when (state) {
        UiState.Loading -> showLoading()
        is UiState.Success -> showUser(state.user)
        is UiState.Error -> showError(state.message)
    }
}
```

## 5. `Unit`, `Nothing`, And `Any`

`Unit` means no meaningful return value.

```kotlin
fun log(message: String): Unit {
    println(message)
}
```

`Any` is the root of non-null Kotlin types.

```kotlin
fun printAnything(value: Any) {
    println(value)
}
```

`Nothing` means a function never returns normally.

```kotlin
fun fail(message: String): Nothing {
    throw IllegalStateException(message)
}
```

Useful with Elvis:

```kotlin
val userId = savedStateHandle.get<String>("userId")
    ?: fail("Missing userId")
```

Interview answer:

```text
Nothing is useful for functions that throw or never complete. It helps Kotlin understand control flow and smart casts.
```

## 6. Data Classes

Data classes are useful for value-like models.

```kotlin
data class ProfileUiState(
    val isLoading: Boolean = false,
    val name: String = "",
    val error: String? = null
)
```

They generate:

- `equals()`
- `hashCode()`
- `toString()`
- `copy()`
- `componentN()`

State update:

```kotlin
_uiState.update { old ->
    old.copy(isLoading = true, error = null)
}
```

Interview answer:

```text
Data classes are excellent for immutable state and value objects. In Android, they pair well with Flow and Compose because copy() makes state updates explicit.
```

## 7. Data Class Limitations

Only primary constructor properties participate in generated `equals`, `hashCode`, `copy`, `toString`, and destructuring.

```kotlin
data class User(val id: String) {
    var isSelected: Boolean = false
}
```

Example:

```kotlin
val first = User("1").apply { isSelected = false }
val second = User("1").apply { isSelected = true }

println(first == second) // true
```

This can surprise teams in:

- Compose state comparison
- DiffUtil
- Unit tests
- Caching
- Set/Map keys

Better:

```kotlin
data class UserUiModel(
    val id: String,
    val name: String,
    val isSelected: Boolean
)
```

Also remember that `copy()` is shallow:

```kotlin
data class Team(
    val members: MutableList<String>
)
```

```kotlin
val a = Team(mutableListOf("A"))
val b = a.copy()

b.members.add("B")

println(a.members) // [A, B]
```

Interview answer:

```text
Data class copy is shallow, and only primary constructor properties participate in generated methods. For UI state, I keep all state-relevant values in the primary constructor and avoid mutable nested objects.
```

## 8. Sealed Classes Vs Sealed Interfaces

Use sealed types when the set of possible cases is closed.

Sealed interface:

```kotlin
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Content(val user: User) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
```

Sealed class:

```kotlin
sealed class ScreenState(
    open val canRefresh: Boolean
) {
    data object Loading : ScreenState(canRefresh = false)

    data class Content(
        val posts: List<Post>
    ) : ScreenState(canRefresh = true)

    data class Error(
        val message: String
    ) : ScreenState(canRefresh = true)
}
```

Rule:

```text
sealed interface = closed contract
sealed class = closed hierarchy with shared state or behavior
```

Use sealed interface when:

- Parent type has no constructor state
- You want flexible implementation
- A subtype may need to implement multiple interfaces

Use sealed class when:

- Parent needs constructor parameters
- Parent contains shared implementation
- Common state or behavior belongs in the base type

Interview answer:

```text
I usually start with sealed interface for UI states and events because it is lightweight and flexible. I use sealed class when the base type needs shared state, constructor parameters, or common behavior.
```

## 9. Enum Vs Sealed

Use enum for fixed constants with the same shape.

```kotlin
enum class ThemeMode {
    Light,
    Dark,
    System
}
```

Use sealed when cases carry different data.

```kotlin
sealed interface PaymentState {
    data object Loading : PaymentState
    data class Success(val receiptId: String) : PaymentState
    data class Error(val message: String) : PaymentState
}
```

Interview answer:

```text
Enums are great for fixed constant choices. Sealed types are better when each case can have different data or behavior.
```

## 10. Sealed Modeling For UI And Domain State

Bad UI state:

```kotlin
data class ProfileState(
    val isLoading: Boolean,
    val user: User?,
    val error: String?
)
```

This can represent impossible states:

```text
isLoading = true, user != null, error != null
```

Better:

```kotlin
sealed interface ProfileState {
    data object Loading : ProfileState
    data class Content(val user: User) : ProfileState
    data class Error(val message: String) : ProfileState
}
```

Domain example:

```kotlin
sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
    data object NetworkUnavailable : LoginResult
}
```

Usage:

```kotlin
when (val result = login()) {
    is LoginResult.Success -> navigateHome(result.user)
    LoginResult.InvalidCredentials -> showError("Invalid credentials")
    LoginResult.NetworkUnavailable -> showError("No internet connection")
}
```

Interview answer:

```text
I use sealed types to make invalid states unrepresentable and to let the compiler enforce exhaustive handling.
```

## 11. Immutability And State Updates

Immutable UI state:

```kotlin
data class FeedUiState(
    val isLoading: Boolean = false,
    val posts: List<PostUiModel> = emptyList(),
    val error: String? = null
)
```

Update with `copy()`:

```kotlin
_uiState.update { state ->
    state.copy(
        isLoading = false,
        posts = newPosts,
        error = null
    )
}
```

Why this matters:

- Easier reasoning
- Easier testing
- Better Flow/Compose compatibility
- Fewer accidental mutation bugs
- Cleaner state history in debugging

Important nuance:

```kotlin
val posts: List<PostUiModel>
```

This is read-only from this reference, but not guaranteed deeply immutable.

For stronger guarantees, consider immutable collections or defensive copies at module boundaries.

Interview answer:

```text
I prefer immutable UI state and explicit replacement using copy(). It makes state changes predictable and works well with reactive UI frameworks like Compose.
```

## 12. Higher-Order Functions

A higher-order function takes another function as a parameter or returns one.

```kotlin
fun execute(block: () -> Unit) {
    block()
}
```

Why they matter:

```text
They let callers pass behavior without defining a new class or interface.
```

Compose example:

```kotlin
@Composable
fun SaveButton(onSave: () -> Unit) {
    Button(onClick = onSave) {
        Text("Save")
    }
}
```

Retry example:

```kotlin
suspend fun <T> retry(
    times: Int,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null

    repeat(times) {
        try {
            return block()
        } catch (e: IOException) {
            lastError = e
        }
    }

    throw lastError ?: IllegalStateException("Retry failed")
}
```

Usage:

```kotlin
val user = retry(times = 3) {
    api.getUser()
}
```

Transaction-style example:

```kotlin
suspend fun <T> withTransaction(block: suspend () -> T): T {
    beginTransaction()

    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}
```

Interview answer:

```text
Higher-order functions let us pass behavior as data. In Android they are used for callbacks, Compose event handlers, retry wrappers, transactions, resource management, DSLs, and testable abstractions.
```

## 13. `inline`

`inline` tells the compiler to copy the function body and lambda body into the call site.

```kotlin
inline fun measure(block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    println(System.currentTimeMillis() - start)
}
```

Usage:

```kotlin
measure {
    loadData()
}
```

Conceptually compiled like:

```kotlin
val start = System.currentTimeMillis()
loadData()
println(System.currentTimeMillis() - start)
```

Benefits:

- Avoids some lambda allocation overhead
- Enables non-local returns
- Enables `reified` generics
- Useful for small higher-order utilities

Non-local return:

```kotlin
inline fun execute(block: () -> Unit) {
    block()
}
```

```kotlin
fun test() {
    execute {
        return // returns from test()
    }

    println("This will not run")
}
```

Interview answer:

```text
inline is useful for small higher-order functions, performance-sensitive wrappers, DSL-style APIs, and reified generics. I avoid inlining large functions because it can increase bytecode size.
```

## 14. `noinline`

Inside an inline function, lambda parameters are inlined by default.

Use `noinline` when a lambda must remain a real object.

```kotlin
inline fun registerCallback(
    id: String,
    noinline callback: () -> Unit
) {
    callbackRegistry[id] = callback
}
```

Use `noinline` when the lambda needs to be:

- Stored
- Returned
- Passed to another non-inline function
- Executed later as an object

Example:

```kotlin
inline fun createCallbacks(
    immediate: () -> Unit,
    noinline later: () -> Unit
): () -> Unit {
    immediate()
    return later
}
```

Interview answer:

```text
noinline opts a lambda out of inlining so it remains a normal function object that can be stored, returned, or passed around.
```

## 15. `crossinline`

`crossinline` keeps a lambda inline but forbids non-local return.

Example:

```kotlin
inline fun runOnMain(crossinline block: () -> Unit) {
    Handler(Looper.getMainLooper()).post {
        block()
    }
}
```

This is not allowed:

```kotlin
fun test() {
    runOnMain {
        // return // Error
    }
}
```

Why:

```text
The lambda runs later inside another Runnable. Returning from test() would not make sense because test() may already be finished.
```

Another example:

```kotlin
inline fun viewModelFactory(
    crossinline create: () -> ViewModel
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }
}
```

Interview answer:

```text
crossinline is used when a lambda is still inlined but is called from another execution context, such as a Runnable or anonymous object, so non-local returns must be forbidden.
```

## 16. `reified`

Normally generic type information is erased at runtime.

This does not work:

```kotlin
fun <T> isOfType(value: Any): Boolean {
    return value is T // Error
}
```

With `inline` and `reified`:

```kotlin
inline fun <reified T> isOfType(value: Any): Boolean {
    return value is T
}
```

Usage:

```kotlin
val isString = isOfType<String>("Hello")
```

Android Activity example:

```kotlin
inline fun <reified T : Activity> Context.openActivity() {
    startActivity(Intent(this, T::class.java))
}
```

Usage:

```kotlin
context.openActivity<ProfileActivity>()
```

Bundle example:

```kotlin
inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
}
```

Interview answer:

```text
reified lets an inline generic function access the actual type T at runtime. It requires inline because the compiler substitutes the real type at each call site.
```

## 17. `inline`, `noinline`, `crossinline`, `reified` Summary

```text
inline = copy function and lambda body at call site
noinline = keep this lambda as a real object
crossinline = inline this lambda, but forbid non-local return
reified = make generic type T available at runtime inside an inline function
```

## 18. Generics Variance: `out`

Variance answers this question:

```text
If Dog extends Animal, can Container<Dog> be used as Container<Animal>?
```

```kotlin
open class Animal
class Dog : Animal()
```

`out` means producer.

```kotlin
interface Producer<out T> {
    fun produce(): T
}
```

`Producer<Dog>` can be used as `Producer<Animal>`.

```kotlin
fun feedAnimal(producer: Producer<Animal>) {
    val animal = producer.produce()
}
```

```kotlin
val dogProducer: Producer<Dog> = object : Producer<Dog> {
    override fun produce(): Dog = Dog()
}

feedAnimal(dogProducer) // OK
```

Why this is safe:

```text
The producer only gives values out. A Dog is safely an Animal.
```

Kotlin `List` uses `out`:

```kotlin
interface List<out E>
```

So this works:

```kotlin
val dogs: List<Dog> = listOf(Dog())
val animals: List<Animal> = dogs
```

## 19. Generics Variance: `in`

`in` means consumer.

```kotlin
interface Consumer<in T> {
    fun consume(value: T)
}
```

Example:

```kotlin
val animalConsumer: Consumer<Animal> = object : Consumer<Animal> {
    override fun consume(value: Animal) {}
}
```

A `Consumer<Animal>` can be used where `Consumer<Dog>` is expected because it can consume any animal, including dogs.

```kotlin
fun trainDog(consumer: Consumer<Dog>) {
    consumer.consume(Dog())
}

trainDog(animalConsumer) // OK
```

Mnemonic:

```text
Producer out
Consumer in
```

Copy example:

```kotlin
fun copyNumbers(
    from: MutableList<out Number>,
    to: MutableList<in Number>
) {
    for (number in from) {
        to.add(number)
    }
}
```

Interview answer:

```text
out is for APIs that produce T. in is for APIs that consume T. If an API both consumes and produces T, it usually must remain invariant.
```

## 20. Extension Functions And Static Dispatch

Extension functions add callable behavior without inheritance.

```kotlin
fun String.isValidEmail(): Boolean {
    return contains("@") && contains(".")
}
```

Android example:

```kotlin
fun View.visible() {
    visibility = View.VISIBLE
}
```

Important: extensions are statically dispatched.

```kotlin
open class Animal
class Dog : Animal()

fun Animal.sound() = "animal"
fun Dog.sound() = "dog"

val animal: Animal = Dog()
println(animal.sound()) // animal
```

The compiler chooses the extension based on the declared type, not runtime type.

Member functions win over extensions:

```kotlin
class User {
    fun name() = "member"
}

fun User.name() = "extension"

println(User().name()) // member
```

Interview answer:

```text
Extensions are great for utility and local expressiveness, but they are not polymorphic and do not override members. If I need runtime dispatch, I use member functions or interfaces.
```

## 21. Scope Functions

Scope functions are mostly about readability.

```text
let   = nullable handling or transform result
apply = configure receiver and return receiver
also  = side effect and return original value
run   = compute result from receiver
with  = operate on object passed as argument
```

Examples:

```kotlin
user?.let { nonNullUser ->
    showUser(nonNullUser)
}
```

```kotlin
val intent = Intent(context, DetailActivity::class.java).apply {
    putExtra("userId", user.id)
}
```

```kotlin
val user = repository.getUser().also {
    analytics.trackUserLoaded(it.id)
}
```

Avoid deep nesting:

```kotlin
user?.let {
    it.address?.let {
        it.city?.let {
            // Hard to read
        }
    }
}
```

Interview answer:

```text
I use scope functions when they clarify intent, but I avoid deep chains because they hide control flow.
```

## 22. Collections, Sequences, And Performance

Collection operations are eager.

```kotlin
val names = users
    .filter { it.isActive }
    .map { it.name }
```

This can create intermediate lists.

Sequences are lazy.

```kotlin
val names = users
    .asSequence()
    .filter { it.isActive }
    .map { it.name }
    .take(10)
    .toList()
```

Use `Sequence` when:

- The collection is large
- There are many chained transformations
- There is early termination with `first`, `take`, or `any`
- Mapping/filtering is expensive

Do not assume `Sequence` is always faster. For small lists, normal collections may be clearer and faster.

`Sequence` vs `Flow`:

```text
Sequence = lazy synchronous values
Flow = asynchronous stream over time
```

Example:

```kotlin
users.asSequence()
    .filter { it.isActive }
    .map { it.name }
```

Versus:

```kotlin
repository.observeUsers()
    .map { users ->
        users.filter { it.isActive }
    }
```

Interview answer:

```text
I use collections for normal in-memory transformations, Sequence for lazy synchronous pipelines, and Flow for asynchronous streams.
```

## 23. Equality: `==` Vs `===`

Structural equality:

```kotlin
a == b
```

Calls `equals()`.

Referential equality:

```kotlin
a === b
```

Checks whether both references point to the same object.

Example:

```kotlin
data class User(val id: String)

val a = User("1")
val b = User("1")

println(a == b)  // true
println(a === b) // false
```

Lead relevance:

```text
Equality matters in state comparison, Compose skipping, DiffUtil, caching, and tests.
```

## 24. Suspend Function Internals

A `suspend` function does not automatically create a new thread.

```kotlin
suspend fun loadUser(): User {
    delay(1_000)
    return api.getUser()
}
```

The Kotlin compiler transforms suspend functions into continuation-based state machines.

Conceptually:

```kotlin
fun loadUser(continuation: Continuation<User>): Any?
```

At a suspend point:

```text
The coroutine saves its current state.
The underlying thread is free to do other work.
Later, the continuation resumes from where it paused.
```

Important distinction:

```kotlin
suspend fun wrong() {
    Thread.sleep(1_000) // Blocks thread
}
```

```kotlin
suspend fun right() {
    delay(1_000) // Suspends without blocking
}
```

Interview answer:

```text
Suspend means the function can pause and resume without blocking the thread. The compiler turns suspend functions into continuation-passing state-machine code, and dispatchers decide which threads execute the coroutine.
```

## 25. `lateinit` Vs `lazy`

`lateinit` is for a mutable property assigned later.

```kotlin
lateinit var repository: UserRepository
```

Common in:

- Tests
- Framework-initialized properties
- DI setup in older patterns

Accessing before initialization crashes:

```kotlin
lateinit property repository has not been initialized
```

Limitations:

- Only works with `var`
- Cannot be used with primitive types like `Int`
- Does not make initialization thread-safe by itself

`lazy` initializes a `val` on first access.

```kotlin
val database by lazy {
    createDatabase()
}
```

Thread-safety modes:

```kotlin
val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    createValue()
}
```

```kotlin
val value by lazy(LazyThreadSafetyMode.NONE) {
    createValue()
}
```

Interview answer:

```text
lateinit is a mutable var assigned after construction and crashes if read too early. lazy is a delegated val initialized once on first access. I prefer constructor injection when possible because it makes required dependencies explicit.
```

## 26. Delegated Properties

Delegation lets another object control get/set behavior.

`lazy`:

```kotlin
val config by lazy {
    loadConfig()
}
```

Observable:

```kotlin
var name: String by Delegates.observable("") { _, old, new ->
    println("Changed from $old to $new")
}
```

Map-backed:

```kotlin
class User(map: Map<String, Any?>) {
    val name: String by map
    val age: Int by map
}
```

Android examples:

```kotlin
val viewModel: ProfileViewModel by viewModels()
```

```kotlin
var query by remember { mutableStateOf("") }
```

Interview answer:

```text
Delegated properties let reusable logic handle property access. Android uses this pattern heavily for lazy values, ViewModels, Compose state, and DI-related helpers.
```

## 27. Class Delegation

Class delegation composes behavior without inheritance.

```kotlin
interface Logger {
    fun log(message: String)
}
```

```kotlin
class ConsoleLogger : Logger {
    override fun log(message: String) {
        println(message)
    }
}
```

```kotlin
class UserRepository(
    logger: Logger
) : Logger by logger
```

Usage:

```kotlin
val repository = UserRepository(ConsoleLogger())
repository.log("Loaded user")
```

Interview answer:

```text
Class delegation helps compose behavior without deep inheritance. It is useful when a class wants to expose or reuse another object's implementation of an interface.
```

## 28. Value Classes Vs Type Aliases

Type alias improves readability but does not create a new type.

```kotlin
typealias UserId = String
```

This is still allowed:

```kotlin
fun loadUser(id: UserId) {}

val orderId: String = "order-1"
loadUser(orderId) // Allowed
```

Value class creates a distinct type.

```kotlin
@JvmInline
value class UserId(val value: String)
```

Usage:

```kotlin
fun loadUser(id: UserId) {}

loadUser(UserId("user-1"))
```

Benefits:

- Better type safety
- Avoids mixing raw strings/ints
- Often avoids wrapper allocation at runtime

Example:

```kotlin
@JvmInline
value class OrderId(val value: String)

fun loadOrder(orderId: OrderId) {}
fun loadUser(userId: UserId) {}
```

Interview answer:

```text
typealias gives a more readable name but no type safety. value class creates a real domain type with better safety and usually low runtime overhead.
```

## 29. Object, Companion Object, And Singleton

Singleton:

```kotlin
object AnalyticsTracker {
    fun track(event: String) {}
}
```

Companion object:

```kotlin
class User {
    companion object {
        const val DEFAULT_NAME = "Guest"
    }
}
```

Factory:

```kotlin
class User private constructor(val name: String) {
    companion object {
        fun guest() = User("Guest")
    }
}
```

Lead warning:

```text
object is convenient, but global singletons can hurt testability and lifecycle control. For services with dependencies, I usually prefer dependency injection.
```

## 30. Nested Vs Inner Classes

Nested class does not hold a reference to the outer class.

```kotlin
class Outer {
    class Nested
}
```

Inner class holds a reference to the outer class.

```kotlin
class Outer {
    inner class Inner {
        fun accessOuter(): Outer = this@Outer
    }
}
```

Android concern:

```text
Inner classes can accidentally hold references to Activities, Fragments, or Views and contribute to memory leaks.
```

Interview answer:

```text
I avoid inner classes unless I truly need access to the outer instance. Nested classes are safer by default because they do not capture the outer object.
```

## 31. Visibility And Modules

Kotlin visibility:

```text
public    visible everywhere
internal  visible inside the same module
protected visible in subclasses
private   visible in file or class scope
```

`internal` is useful in multi-module Android projects.

```kotlin
internal class RealUserRepository : UserRepository
```

Expose interface:

```kotlin
interface UserRepository {
    suspend fun getUser(): User
}
```

Hide implementation:

```kotlin
internal class RealUserRepository(
    private val api: UserApi
) : UserRepository {
    override suspend fun getUser(): User = api.getUser()
}
```

Interview answer:

```text
internal helps enforce module boundaries. I expose stable APIs and hide implementation details inside the Gradle module.
```

## 32. Kotlin/JVM Interoperability

Kotlin and Java interoperate well, but API design matters.

### SAM Conversion

Java:

```java
interface ClickListener {
    void onClick();
}
```

Kotlin usage:

```kotlin
button.setClickListener {
    println("Clicked")
}
```

### `@JvmStatic`

```kotlin
class Logger {
    companion object {
        @JvmStatic
        fun log(message: String) {}
    }
}
```

Java can call:

```java
Logger.log("Hello");
```

### `@JvmOverloads`

```kotlin
@JvmOverloads
fun showMessage(
    message: String,
    duration: Int = Toast.LENGTH_SHORT
) {}
```

Generates overloads for Java callers.

### `@JvmField`

```kotlin
object Constants {
    @JvmField
    val version = "1.0"
}
```

Lead answer:

```text
When designing Kotlin APIs consumed by Java, I think about platform types, default arguments, @JvmStatic, @JvmOverloads, @JvmField, SAM conversion, and nullability annotations.
```

## 33. KAPT Vs KSP

Annotation processing affects Android build time.

KAPT:

```text
Kotlin Annotation Processing Tool.
Bridges Kotlin code to Java annotation processors.
Often slower because it relies on stub generation.
```

KSP:

```text
Kotlin Symbol Processing.
Kotlin-native symbol API.
Often faster and more incremental-friendly.
Preferred when supported.
```

Example:

```kotlin
plugins {
    id("com.google.devtools.ksp")
}
```

```kotlin
dependencies {
    ksp("androidx.room:room-compiler:<version>")
}
```

Instead of:

```kotlin
dependencies {
    kapt("androidx.room:room-compiler:<version>")
}
```

Lead answer:

```text
In large Android projects, annotation processing can significantly affect build time. When a library supports KSP, I prefer KSP over KAPT because it is Kotlin-native and often improves incremental build performance.
```

## 34. Contracts

Kotlin contracts let functions tell the compiler about control flow.

You do not write custom contracts often, but standard functions use them.

Example:

```kotlin
val user: User? = getUser()
requireNotNull(user)
println(user.name) // Smart-cast to non-null
```

`requireNotNull` can inform the compiler that after it returns normally, the value is non-null.

Custom contracts are advanced and require care.

Interview answer:

```text
Contracts let certain functions communicate control-flow guarantees to the compiler, enabling smarter nullability and initialization analysis. I mostly rely on standard library contracts rather than writing custom ones.
```

## 35. Operator Overloading

Operator overloading lets domain types support operators.

```kotlin
data class Money(val cents: Long) {
    operator fun plus(other: Money): Money {
        return Money(cents + other.cents)
    }
}
```

Usage:

```kotlin
val total = price + tax
```

Use carefully.

Good:

```text
Money + Money
Vector + Vector
DateRange contains Date
```

Risky:

```text
Operators with surprising side effects
Operators that do not match domain intuition
Operators that make code clever instead of clear
```

Interview answer:

```text
Operator overloading is useful when it matches domain intuition. I avoid it when it makes behavior ambiguous.
```

## 36. Destructuring

Data classes support destructuring.

```kotlin
data class User(
    val id: String,
    val name: String
)
```

```kotlin
val (id, name) = user
```

Useful in small cases:

```kotlin
map.forEach { (key, value) ->
    println("$key = $value")
}
```

Be careful with large models because destructuring depends on property order.

Interview answer:

```text
Destructuring is concise for pairs, maps, and small value objects, but I avoid it when named access is clearer.
```

## 37. Kotlin API Design

Good Kotlin APIs are explicit, safe, and readable.

Prefer domain types over primitives:

```kotlin
@JvmInline
value class UserId(val value: String)
```

Prefer default parameters over overload explosion:

```kotlin
fun showSnackbar(
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short
)
```

Prefer sealed results for known outcomes:

```kotlin
sealed interface SaveResult {
    data object Success : SaveResult
    data object Offline : SaveResult
    data class Failed(val reason: String) : SaveResult
}
```

Prefer explicit nullability:

```kotlin
fun findUser(id: UserId): User?
```

Lead-level API checklist:

- Keep nullability honest
- Avoid hidden global state
- Prefer immutable models
- Use `internal` to protect module boundaries
- Avoid deep scope-function chains
- Use extension functions for convenience, not polymorphism
- Avoid clever operators unless domain meaning is obvious

## 38. Kotlin Multiplatform Awareness

Kotlin Multiplatform uses `expect` and `actual` declarations to share common code while keeping platform-specific implementations separate.

Common code:

```kotlin
expect class PlatformLogger {
    fun log(message: String)
}
```

Android implementation:

```kotlin
actual class PlatformLogger {
    actual fun log(message: String) {
        Log.d("App", message)
    }
}
```

Lead answer:

```text
I would consider KMP for sharing domain logic, validation, networking clients, or data models, but I would be careful about over-sharing platform-specific UI or architecture.
```

## 39. Kotlin Performance Gotchas

Common areas to watch:

- Excessive collection chains can allocate intermediate lists
- `Sequence` has overhead and is not always faster
- Reflection can be expensive
- Large inline functions can increase bytecode size
- `data class copy()` is shallow
- Lambdas can allocate unless optimized or inlined
- Boxing can happen with nullable primitives and generics
- Overusing exceptions for expected control flow can be costly and unclear
- Annotation processing can slow builds

Example allocation issue:

```kotlin
val result = users
    .filter { it.isActive }
    .map { it.toUiModel() }
    .filter { it.isVisible }
```

For large lists:

```kotlin
val result = users
    .asSequence()
    .filter { it.isActive }
    .map { it.toUiModel() }
    .filter { it.isVisible }
    .toList()
```

Interview answer:

```text
I do not avoid Kotlin features by default, but I understand their costs. In performance-sensitive code, I watch allocations, reflection, collection chains, boxing, annotation processing, and bytecode size from excessive inlining.
```

## 40. Common Interview Questions

### What is the difference between `val` and `var`?

`val` means the variable reference cannot be reassigned. `var` can be reassigned. `val` does not guarantee deep immutability.

### How does Kotlin null safety work?

Kotlin separates nullable and non-null types at compile time. Nullable values must be handled with safe calls, Elvis, explicit checks, or other null-handling mechanisms.

### What is a platform type?

A platform type comes from Java when Kotlin does not know the nullability. It is risky because Kotlin cannot fully enforce null safety for it.

### Why use sealed types for UI state?

They model a closed set of cases and prevent invalid state combinations. They also allow exhaustive `when` handling.

### Sealed class or sealed interface?

Use sealed interface for a closed contract without shared state. Use sealed class when the base type needs constructor state or common behavior.

### Enum or sealed?

Use enum for fixed constants with the same shape. Use sealed when cases carry different data.

### What is `inline`?

`inline` copies the function and lambda body at the call site. It can reduce lambda overhead, allow non-local returns, and enable `reified`.

### What is `noinline`?

`noinline` keeps a lambda parameter as a normal function object inside an inline function.

### What is `crossinline`?

`crossinline` keeps a lambda inline but forbids non-local return, usually because the lambda is called from another context.

### What is `reified`?

`reified` lets an inline generic function access the actual type `T` at runtime.

### What are `in` and `out` in generics?

`out` is for producers of `T`. `in` is for consumers of `T`.

### Are extension functions polymorphic?

No. Extension functions are statically dispatched based on the declared type.

### What is the difference between `Sequence` and `Flow`?

`Sequence` is lazy and synchronous. `Flow` is asynchronous and emits values over time.

### What is the difference between `lateinit` and `lazy`?

`lateinit` is a mutable property initialized later. `lazy` is a delegated `val` initialized once on first access.

### What is the difference between typealias and value class?

`typealias` only gives another name to an existing type. A value class creates a distinct domain type with better type safety and usually low runtime overhead.

### How do suspend functions work internally?

The compiler transforms suspend functions into continuation-based state machines so they can pause and resume without blocking a thread.

### Why prefer KSP over KAPT?

KSP is Kotlin-native and often more incremental-friendly. KAPT uses Java annotation processing through generated stubs and can be slower.

## 41. Final Lead-Level Summary

A strong Android lead Kotlin answer:

```text
I use Kotlin to make invalid states harder to represent and APIs easier to reason about. I prefer immutable UI state, sealed modeling for finite state, explicit null handling, and type-safe domain wrappers. I understand inline/reified, variance, extension dispatch, collection performance, suspend internals, and Java interop because they directly affect architecture, correctness, performance, and build times in large Android apps.
```

