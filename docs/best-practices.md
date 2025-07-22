# Kotlin Concurrency Best Practices

## 🎯 Golden Rules

### 1. Always Use Structured Concurrency
```kotlin
// ❌ Avoid: Unstructured concurrency
GlobalScope.launch { 
    // This can leak and is hard to control
}

// ✅ Prefer: Structured concurrency
class MyService : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job
    
    fun doWork() = launch {
        // Properly scoped and manageable
    }
    
    fun cleanup() = job.cancel()
}
```

### 2. Choose the Right Dispatcher
```kotlin
// ✅ CPU-intensive work
withContext(Dispatchers.Default) {
    heavyComputation()
}

// ✅ I/O operations
withContext(Dispatchers.IO) {
    fileRead() 
    networkCall()
}

// ✅ UI updates
withContext(Dispatchers.Main) {
    updateUI()
}
```

### 3. Handle Cancellation Cooperatively
```kotlin
// ✅ Check for cancellation in loops
suspend fun processLargeDataset(data: List<Item>) {
    for (item in data) {
        ensureActive() // Throws CancellationException if cancelled
        processItem(item)
    }
}

// ✅ Use cancellable suspending functions
suspend fun networkOperation() {
    withTimeout(5000) { // Automatically cancellable
        apiCall()
    }
}
```

## 🏗️ Architectural Patterns

### Repository Pattern with Coroutines
```kotlin
interface UserRepository {
    suspend fun getUser(id: String): User
    fun getUserUpdates(id: String): Flow<User>
}

class UserRepositoryImpl(
    private val api: UserApi,
    private val cache: UserCache
) : UserRepository {
    
    override suspend fun getUser(id: String): User = 
        withContext(Dispatchers.IO) {
            cache.getUser(id) ?: run {
                val user = api.fetchUser(id)
                cache.saveUser(user)
                user
            }
        }
    
    override fun getUserUpdates(id: String): Flow<User> = flow {
        emit(cache.getUser(id) ?: getUser(id))
        
        api.observeUserUpdates(id).collect { user ->
            cache.saveUser(user)
            emit(user)
        }
    }.flowOn(Dispatchers.IO)
}
```

### ViewModel Pattern (Android/UI)
```kotlin
class UserViewModel(
    private val repository: UserRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun loadUser(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading
                val user = repository.getUser(id)
                _uiState.value = UiState.Success(user)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun observeUserUpdates(id: String) {
        viewModelScope.launch {
            repository.getUserUpdates(id)
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Error") }
                .collect { user -> _uiState.value = UiState.Success(user) }
        }
    }
}
```

## 🔄 Flow Best Practices

### 1. Prefer Cold Flows for Data Streams
```kotlin
// ✅ Cold flow - each collector gets fresh data
fun getDataStream(): Flow<Data> = flow {
    val data = fetchFreshData()
    emit(data)
}

// ✅ Use flowOn to specify context
fun getDataStream(): Flow<Data> = flow {
    val data = heavyComputation() // Runs on IO dispatcher
    emit(data)
}.flowOn(Dispatchers.IO) // Context for the flow
```

### 2. Use StateFlow for UI State
```kotlin
class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    fun updateScore(points: Int) {
        _gameState.update { currentState ->
            currentState.copy(score = currentState.score + points)
        }
    }
}
```

### 3. Combine Multiple Flows Efficiently
```kotlin
// ✅ Use combine for latest values from multiple sources
val combinedData = combine(
    userFlow,
    settingsFlow,
    preferencesFlow
) { user, settings, preferences ->
    UiData(user, settings, preferences)
}

// ✅ Use zip for synchronized emission
val synchronizedData = userFlow.zip(profileFlow) { user, profile ->
    UserProfile(user, profile)
}
```

### 4. Handle Backpressure Appropriately
```kotlin
// ✅ Use buffer for faster producers
val bufferedFlow = dataFlow
    .buffer(capacity = 64)
    .collect { processData(it) }

// ✅ Use conflate to keep only latest
val latestFlow = fastProducerFlow
    .conflate() // Keeps only the latest emission
    .collect { processLatest(it) }
```

## ⚠️ Error Handling Patterns

### 1. Supervision Strategy
```kotlin
// ✅ Use SupervisorJob for independent failure handling
class ServiceManager : CoroutineScope {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + supervisorJob + 
        CoroutineExceptionHandler { _, exception ->
            logger.error("Unhandled coroutine exception", exception)
        }
    
    fun startServices() {
        // If service1 fails, service2 continues running
        launch { service1.start() }
        launch { service2.start() }
        launch { service3.start() }
    }
}
```

### 2. Flow Error Handling
```kotlin
// ✅ Handle errors at the right level
fun getDataWithErrorHandling(): Flow<Result<Data>> = flow {
    val data = apiService.getData()
    emit(Result.success(data))
}.catch { exception ->
    emit(Result.failure(exception))
}.retry(retries = 3) { exception ->
    exception is IOException // Only retry network errors
}

// ✅ Use onEach for side effects, catch for errors
fun processDataStream() {
    dataFlow
        .onEach { data -> 
            logger.debug("Processing: $data") 
        }
        .catch { exception ->
            logger.error("Processing failed", exception)
            emit(fallbackData)
        }
        .collect { processedData ->
            updateUI(processedData)
        }
}
```

### 3. Timeout Handling
```kotlin
// ✅ Set appropriate timeouts
suspend fun fetchWithTimeout(): Data = withTimeout(5.seconds) {
    apiService.fetchData()
}

// ✅ Use timeout operators in flows
val timeoutFlow = dataFlow
    .timeout(30.seconds)
    .catch { exception ->
        if (exception is TimeoutCancellationException) {
            emit(cachedData)
        } else {
            throw exception
        }
    }
```

## 🚀 Performance Optimizations

### 1. Minimize Context Switching
```kotlin
// ❌ Avoid: Excessive context switching
suspend fun badExample() {
    withContext(Dispatchers.IO) { operation1() }
    withContext(Dispatchers.Default) { operation2() }
    withContext(Dispatchers.IO) { operation3() }
}

// ✅ Prefer: Group operations by context
suspend fun goodExample() {
    val result1 = withContext(Dispatchers.IO) { 
        operation1()
        operation3() // Group I/O operations
    }
    val result2 = withContext(Dispatchers.Default) { 
        operation2()
    }
}
```

### 2. Use Appropriate Coroutine Builders
```kotlin
// ✅ Use async for concurrent independent operations
suspend fun fetchUserData(userId: String): UserData {
    return coroutineScope {
        val userDeferred = async { userService.getUser(userId) }
        val prefsDeferred = async { prefsService.getPreferences(userId) }
        val historyDeferred = async { historyService.getHistory(userId) }
        
        UserData(
            user = userDeferred.await(),
            preferences = prefsDeferred.await(),
            history = historyDeferred.await()
        )
    }
}

// ✅ Use launch for fire-and-forget operations
fun logUserAction(action: UserAction) {
    scope.launch { 
        analyticsService.log(action)
    }
}
```

### 3. Channel Sizing Strategy
```kotlin
// ✅ Choose appropriate channel capacity
val unlimitedChannel = Channel<Data>(Channel.UNLIMITED) // Use carefully
val bufferedChannel = Channel<Data>(capacity = 100)     // Good for bursty data
val rendezvousChannel = Channel<Data>()                 // Direct handoff
```

## 🧪 Testing Best Practices

### 1. Use Test Dispatchers
```kotlin
@Test
fun testCoroutineOperation() = runTest {
    val repository = UserRepository(testApi)
    
    // This will use the test scheduler
    val result = repository.getUser("123")
    
    assertEquals(expectedUser, result)
}
```

### 2. Test Flow Operations
```kotlin
@Test
fun testFlowTransformation() = runTest {
    val inputFlow = flowOf(1, 2, 3, 4, 5)
    
    val result = inputFlow
        .filter { it > 2 }
        .map { it * 2 }
        .toList()
    
    assertEquals(listOf(6, 8, 10), result)
}
```

## 🔒 Thread Safety

### 1. Immutable Data Structures
```kotlin
// ✅ Use immutable data classes
data class UserState(
    val user: User,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    fun withLoading(loading: Boolean) = copy(isLoading = loading)
    fun withError(error: String) = copy(error = error)
    fun withUser(user: User) = copy(user = user, error = null)
}
```

### 2. Thread-Safe Collections
```kotlin
// ✅ Use concurrent collections when needed
class ThreadSafeCache<K, V> {
    private val cache = ConcurrentHashMap<K, V>()
    
    fun get(key: K): V? = cache[key]
    fun put(key: K, value: V): V? = cache.put(key, value)
}
```

## 📱 Platform-Specific Guidelines

### Android
```kotlin
// ✅ Use lifecycle-aware scopes
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use lifecycle scope for UI operations
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
        
        // Use repeatOnLifecycle for flows
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }
}
```

### Server-Side (Ktor/Spring)
```kotlin
// ✅ Use appropriate scope for request handling
@RestController
class UserController(
    private val userService: UserService
) {
    @GetMapping("/users/{id}")
    suspend fun getUser(@PathVariable id: String): ResponseEntity<User> {
        return try {
            val user = userService.getUser(id)
            ResponseEntity.ok(user)
        } catch (e: UserNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
}
```

## 🚨 Common Anti-Patterns to Avoid

### 1. Blocking in Coroutines
```kotlin
// ❌ Never do this
launch {
    Thread.sleep(1000) // Blocks the thread!
    runBlocking { suspendingOperation() } // Blocks!
}

// ✅ Do this instead
launch {
    delay(1000) // Suspends without blocking
    suspendingOperation()
}
```

### 2. Resource Leaks
```kotlin
// ❌ This can leak resources
class LeakyService {
    private val scope = CoroutineScope(Dispatchers.Default)
    
    fun startBackgroundWork() {
        scope.launch {
            while (true) { // No cancellation check!
                doWork()
                delay(1000)
            }
        }
    }
    // No cleanup method!
}

// ✅ Proper resource management
class ProperService : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + job
    
    fun startBackgroundWork() = launch {
        while (isActive) { // Respects cancellation
            doWork()
            delay(1000)
        }
    }
    
    fun cleanup() {
        job.cancel()
    }
}
```

### 3. Exception Swallowing
```kotlin
// ❌ Silent failures are dangerous
launch {
    try {
        riskyOperation()
    } catch (e: Exception) {
        // Ignored - bad practice!
    }
}

// ✅ Always handle or propagate exceptions
launch {
    try {
        riskyOperation()
    } catch (e: Exception) {
        logger.error("Operation failed", e)
        // Re-throw or handle appropriately
        throw e
    }
}
```

These best practices form the foundation for writing robust, performant, and maintainable concurrent Kotlin code. Each pattern addresses common challenges and provides proven solutions used in production applications.