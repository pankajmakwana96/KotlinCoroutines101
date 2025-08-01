/**
 * # Main-Safety Conventions in Coroutines
 * 
 * ## Problem Description
 * In UI applications, especially Android, most UI operations must happen on the main/UI thread.
 * However, coroutines can execute on any thread, leading to potential violations of main-thread
 * requirements. Main-safety conventions ensure that suspend functions are safe to call from
 * the main thread without blocking it, and that UI updates happen on the correct thread.
 * 
 * ## Solution Approach
 * Main-safety conventions include:
 * - Main-safe suspend functions that never block the main thread
 * - Proper use of Dispatchers.Main for UI operations
 * - Context switching for background work with withContext()
 * - Understanding Dispatchers.Main.immediate for optimization
 * - Safe patterns for UI state updates from background threads
 * 
 * ## Key Learning Points
 * - Main-safe functions can be called from main thread without blocking
 * - Use withContext(Dispatchers.IO) for blocking operations
 * - Dispatchers.Main.immediate avoids unnecessary dispatching
 * - UI operations must always happen on the main thread
 * - Background processing should never directly touch UI
 * - Proper error handling preserves main-safety
 * 
 * ## Performance Considerations
 * - Main thread dispatch: ~50-200μs overhead
 * - Immediate dispatch: ~1-10μs when already on main thread
 * - Context switching: ~100-500μs per switch
 * - UI blocking operations: >16ms causes frame drops
 * - Background thread pool: Minimal overhead for CPU/IO work
 * 
 * ## Common Pitfalls
 * - Blocking operations on main thread
 * - Accessing UI from background threads
 * - Unnecessary main thread dispatching
 * - Missing context switches for blocking work
 * - Race conditions in UI state updates
 * 
 * ## Real-World Applications
 * - Android UI applications
 * - Desktop applications with Swing/JavaFX
 * - Web frontend with Kotlin/JS
 * - Data loading with UI updates
 * - Background processing with progress updates
 * 
 * ## Related Concepts
 * - Android Architecture Components (ViewModel, LiveData)
 * - Compose UI and coroutines integration
 * - Event-driven programming patterns
 * - Reactive UI programming
 */

package coroutines.basics.mainsafety

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Main-safe function patterns and conventions
 * 
 * Main-Safety Rules:
 * 
 * ✅ Main-Safe Function:
 * suspend fun loadData(): Data {
 *     return withContext(Dispatchers.IO) {
 *         // Blocking operations here
 *         database.query()
 *     }
 * }
 * 
 * ❌ Non Main-Safe Function:
 * suspend fun loadData(): Data {
 *     Thread.sleep(1000) // Blocks main thread!
 *     return database.query()
 * }
 * 
 * Main Thread Usage:
 * ├── UI Operations ────── Always on Dispatchers.Main
 * ├── Quick CPU Work ───── Safe on main thread
 * ├── I/O Operations ───── Switch to Dispatchers.IO
 * └── CPU-Intensive ────── Switch to Dispatchers.Default
 */
class MainSafeFunctions {
    
    fun demonstrateMainSafePatterns() = runBlocking {
        println("=== Main-Safe Function Patterns ===")
        
        // Simulate main thread context
        val mainDispatcher = Dispatchers.Main.limitedParallelism(1)
        
        println("1. Main-safe vs non-main-safe functions:")
        
        // ❌ Non main-safe function (DON'T DO THIS)
        suspend fun nonMainSafeFunction(): String {
            // This would block the main thread in a real app
            println("  ❌ Non-main-safe: Simulating blocking operation")
            delay(500) // Using delay instead of Thread.sleep for demo
            return "Blocking result"
        }
        
        // ✅ Main-safe function
        suspend fun mainSafeFunction(): String {
            return withContext(Dispatchers.IO) {
                println("  ✅ Main-safe: Blocking work on background thread")
                delay(500) // Simulate blocking I/O
                "Non-blocking result"
            }
        }
        
        // Call from main thread context
        withContext(mainDispatcher) {
            println("  Calling from main thread context...")
            
            val time1 = measureTimeMillis {
                val result1 = nonMainSafeFunction()
                println("  Non-main-safe result: $result1")
            }
            
            val time2 = measureTimeMillis {
                val result2 = mainSafeFunction()
                println("  Main-safe result: $result2")
            }
            
            println("  Non-main-safe time: ${time1}ms")
            println("  Main-safe time: ${time2}ms")
        }
        
        println()
        
        // Database/Network simulation
        println("2. Real-world main-safe patterns:")
        
        class UserRepository {
            // ✅ Main-safe: I/O operations properly dispatched
            suspend fun loadUser(id: String): User {
                return withContext(Dispatchers.IO) {
                    println("    Loading user $id from database...")
                    delay(300) // Simulate database query
                    User(id, "User Name $id", "user$id@example.com")
                }
            }
            
            // ✅ Main-safe: CPU-intensive work properly dispatched
            suspend fun processUserData(users: List<User>): List<ProcessedUser> {
                return withContext(Dispatchers.Default) {
                    println("    Processing ${users.size} users on background thread...")
                    delay(200) // Simulate CPU-intensive processing
                    users.map { user ->
                        ProcessedUser(
                            id = user.id,
                            displayName = user.name.uppercase(),
                            domain = user.email.substringAfter("@"),
                            score = user.name.length * 10
                        )
                    }
                }
            }
            
            // ✅ Main-safe: Network operations properly dispatched
            suspend fun syncUserToServer(user: User): Boolean {
                return withContext(Dispatchers.IO) {
                    println("    Syncing user ${user.id} to server...")
                    delay(400) // Simulate network call
                    Random.nextBoolean() // Random success/failure
                }
            }
        }
        
        data class User(val id: String, val name: String, val email: String)
        data class ProcessedUser(val id: String, val displayName: String, val domain: String, val score: Int)
        
        val repository = UserRepository()
        
        withContext(mainDispatcher) {
            println("  Main thread: Starting user operations...")
            
            // Load users (main-safe)
            val users = listOf("1", "2", "3").map { id ->
                repository.loadUser(id)
            }
            
            // Process users (main-safe)  
            val processedUsers = repository.processUserData(users)
            
            // Update UI (on main thread)
            println("  Main thread: Updating UI with processed users:")
            processedUsers.forEach { user ->
                println("    UI Update: ${user.displayName} (${user.domain}) - Score: ${user.score}")
            }
            
            // Sync to server (main-safe)
            val syncResults = users.map { user ->
                val success = repository.syncUserToServer(user)
                user.id to success
            }
            
            // Update UI with sync results (on main thread)
            println("  Main thread: Sync results:")
            syncResults.forEach { (id, success) ->
                println("    UI Update: User $id sync ${if (success) "✅ successful" else "❌ failed"}")
            }
        }
        
        println("Main-safe patterns completed\n")
    }
    
    fun demonstrateDispatcherSelection() = runBlocking {
        println("=== Dispatcher Selection for Main-Safety ===")
        
        println("1. Choosing the right dispatcher:")
        
        class DataProcessor {
            // Quick CPU work - can stay on main thread
            suspend fun validateInput(input: String): Boolean {
                // Fast validation logic - no context switch needed
                return input.isNotEmpty() && input.length <= 100
            }
            
            // I/O operations - switch to IO dispatcher  
            suspend fun loadFromFile(filename: String): String {
                return withContext(Dispatchers.IO) {
                    println("    Loading file $filename on ${Thread.currentThread().name}")
                    delay(200) // Simulate file I/O
                    "File content from $filename"
                }
            }
            
            // CPU-intensive work - switch to Default dispatcher
            suspend fun heavyComputation(data: List<Int>): List<Int> {
                return withContext(Dispatchers.Default) {
                    println("    Heavy computation on ${Thread.currentThread().name}")
                    delay(300) // Simulate CPU-intensive work
                    data.map { it * it * it } // Cube each number
                }
            }
            
            // UI updates - ensure main dispatcher
            suspend fun updateProgress(progress: Int) {
                withContext(Dispatchers.Main) {
                    println("    UI Update on ${Thread.currentThread().name}: Progress $progress%")
                }
            }
        }
        
        val processor = DataProcessor()
        
        // Simulate UI application flow
        launch(Dispatchers.Main) {
            println("  Starting data processing from main thread...")
            
            // Quick validation - no context switch
            val isValid = processor.validateInput("sample data")
            println("  Input valid: $isValid")
            
            if (isValid) {
                // Update progress
                processor.updateProgress(10)
                
                // Load data (I/O operation)
                val fileData = processor.loadFromFile("data.txt")
                println("  Loaded: $fileData")
                
                processor.updateProgress(50)
                
                // Process data (CPU-intensive)
                val numbers = listOf(1, 2, 3, 4, 5)
                val processed = processor.heavyComputation(numbers)
                println("  Processed: $processed")
                
                processor.updateProgress(100)
                println("  Processing completed on main thread")
            }
        }.join()
        
        println()
        
        // Demonstrating context inheritance
        println("2. Context inheritance and switching:")
        
        suspend fun demonstrateContextInheritance() {
            println("  Starting in context: ${coroutineContext[CoroutineDispatcher]}")
            
            withContext(Dispatchers.IO) {
                println("    Switched to: ${coroutineContext[CoroutineDispatcher]}")
                
                // Nested context - inherits IO
                launch {
                    println("      Child coroutine: ${coroutineContext[CoroutineDispatcher]}")
                    delay(100)
                }.join()
                
                // Explicit context switch
                withContext(Dispatchers.Default) {
                    println("      Nested switch to: ${coroutineContext[CoroutineDispatcher]}")
                }
                
                println("    Back to: ${coroutineContext[CoroutineDispatcher]}")
            }
            
            println("  Back to original: ${coroutineContext[CoroutineDispatcher]}")
        }
        
        launch(Dispatchers.Main) {
            demonstrateContextInheritance()
        }.join()
        
        println("Dispatcher selection completed\n")
    }
}

/**
 * Dispatchers.Main.immediate optimization patterns
 */
class MainImmediatePatterns {
    
    fun demonstrateMainImmediateOptimization() = runBlocking {
        println("=== Dispatchers.Main.immediate Optimization ===")
        
        println("1. Main vs Main.immediate performance:")
        
        suspend fun measureDispatchOverhead(dispatcher: CoroutineDispatcher, name: String) {
            val times = mutableListOf<Long>()
            
            repeat(5) { iteration ->
                val time = measureTimeMillis {
                    withContext(dispatcher) {
                        // Quick operation
                        val result = "Operation $iteration"
                        println("    $name: $result on ${Thread.currentThread().name.take(20)}")
                    }
                }
                times.add(time)
                delay(10)
            }
            
            val averageTime = times.average()
            println("  $name average time: ${"%.2f".format(averageTime)}ms")
        }
        
        // Test from main thread context
        withContext(Dispatchers.Main) {
            println("  Testing from main thread context:")
            measureDispatchOverhead(Dispatchers.Main, "Main")
            measureDispatchOverhead(Dispatchers.Main.immediate, "Main.immediate")
        }
        
        println()
        
        // Test from background thread context
        withContext(Dispatchers.Default) {
            println("  Testing from background thread context:")
            measureDispatchOverhead(Dispatchers.Main, "Main (from bg)")
            measureDispatchOverhead(Dispatchers.Main.immediate, "Main.immediate (from bg)")
        }
        
        println()
        
        println("2. Practical main.immediate usage:")
        
        class UIController {
            private val _uiState = MutableStateFlow("Initial")
            val uiState: StateFlow<String> = _uiState.asStateFlow()
            
            // Optimized UI update function
            suspend fun updateUI(newState: String) {
                withContext(Dispatchers.Main.immediate) {
                    println("    UI Update: $newState on ${Thread.currentThread().name.take(20)}")
                    _uiState.value = newState
                }
            }
            
            // Background work with UI updates
            suspend fun performWorkWithUpdates() {
                updateUI("Starting work...")
                
                withContext(Dispatchers.IO) {
                    println("    Background: Loading data...")
                    delay(300)
                    
                    // Update UI from background - will dispatch to main
                    updateUI("Data loaded")
                    
                    println("    Background: Processing data...")
                    delay(200)
                    
                    updateUI("Processing complete")
                }
                
                // This might not dispatch if already on main
                updateUI("All work finished")
            }
        }
        
        val controller = UIController()
        
        // Monitor UI state
        val stateJob = launch {
            controller.uiState.collect { state ->
                println("  State Observer: $state")
            }
        }
        
        delay(100)
        
        // Perform work from main thread
        withContext(Dispatchers.Main) {
            controller.performWorkWithUpdates()
        }
        
        delay(100)
        stateJob.cancel()
        
        println()
        
        println("3. Conditional dispatching pattern:")
        
        suspend fun conditionalMainDispatch(action: suspend () -> Unit) {
            if (Dispatchers.Main.immediate.isDispatchNeeded(coroutineContext)) {
                println("    Dispatching to main thread required")
                withContext(Dispatchers.Main.immediate) {
                    action()
                }
            } else {
                println("    Already on main thread, no dispatching needed")
                action()
            }
        }
        
        // Test from different contexts
        withContext(Dispatchers.Main) {
            println("  From main thread:")
            conditionalMainDispatch {
                println("    Action executed on ${Thread.currentThread().name.take(20)}")
            }
        }
        
        withContext(Dispatchers.Default) {
            println("  From background thread:")
            conditionalMainDispatch {
                println("    Action executed on ${Thread.currentThread().name.take(20)}")
            }
        }
        
        println("Main.immediate optimization completed\n")
    }
}

/**
 * UI state management patterns
 */
class UIStateManagement {
    
    fun demonstrateUIStatePatterns() = runBlocking {
        println("=== UI State Management Patterns ===")
        
        println("1. Safe UI state updates:")
        
        // Simulate UI state
        data class UIState(
            val loading: Boolean = false,
            val data: List<String> = emptyList(),
            val error: String? = null
        )
        
        class ScreenController {
            private val _uiState = MutableStateFlow(UIState())
            val uiState: StateFlow<UIState> = _uiState.asStateFlow()
            
            // Main-safe state update function
            private suspend fun updateState(update: (UIState) -> UIState) {
                withContext(Dispatchers.Main.immediate) {
                    _uiState.update(update)
                }
            }
            
            suspend fun loadData() {
                // Set loading state
                updateState { it.copy(loading = true, error = null) }
                
                try {
                    // Background data loading
                    val data = withContext(Dispatchers.IO) {
                        println("    Loading data from network...")
                        delay(500)
                        
                        // Simulate potential failure
                        if (Random.nextDouble() < 0.3) {
                            throw Exception("Network error")
                        }
                        
                        listOf("Item 1", "Item 2", "Item 3", "Item 4")
                    }
                    
                    // Update with success state
                    updateState { it.copy(loading = false, data = data) }
                    
                } catch (e: Exception) {
                    // Update with error state
                    updateState { it.copy(loading = false, error = e.message) }
                }
            }
            
            suspend fun refreshData() {
                println("  Refreshing data...")
                loadData()
            }
            
            suspend fun clearError() {
                updateState { it.copy(error = null) }
            }
        }
        
        val controller = ScreenController()
        
        // UI state observer (simulates UI layer)
        val uiObserver = launch {
            controller.uiState.collect { state ->
                withContext(Dispatchers.Main.immediate) {
                    println("  📱 UI Update: loading=${state.loading}, data=${state.data.size} items, error=${state.error}")
                    
                    // Simulate UI updates
                    when {
                        state.loading -> println("    🔄 Showing loading spinner")
                        state.error != null -> println("    ❌ Showing error: ${state.error}")
                        state.data.isNotEmpty() -> println("    ✅ Showing ${state.data.size} items")
                        else -> println("    📭 Showing empty state")
                    }
                }
            }
        }
        
        delay(100)
        
        // Simulate user actions
        withContext(Dispatchers.Main) {
            println("  User action: Load data")
            controller.loadData()
            
            delay(600)
            
            println("  User action: Refresh data")
            controller.refreshData()
            
            delay(600)
            
            if (controller.uiState.value.error != null) {
                println("  User action: Clear error")
                controller.clearError()
            }
        }
        
        delay(200)
        uiObserver.cancel()
        
        println()
        
        // Progress reporting pattern
        println("2. Progress reporting pattern:")
        
        class ProgressReporter {
            private val _progress = MutableStateFlow(0)
            val progress: StateFlow<Int> = _progress.asStateFlow()
            
            suspend fun updateProgress(value: Int) {
                withContext(Dispatchers.Main.immediate) {
                    _progress.value = value.coerceIn(0, 100)
                }
            }
            
            suspend fun performLongRunningTask() {
                updateProgress(0)
                
                withContext(Dispatchers.Default) {
                    val steps = 10
                    repeat(steps) { step ->
                        // Simulate work
                        delay(200)
                        
                        // Update progress
                        val progressValue = ((step + 1) * 100) / steps
                        updateProgress(progressValue)
                        
                        println("    Background: Completed step ${step + 1}/$steps")
                    }
                }
            }
        }
        
        val reporter = ProgressReporter()
        
        // Progress observer
        launch {
            reporter.progress.collect { progress ->
                withContext(Dispatchers.Main.immediate) {
                    println("  📊 Progress Update: $progress%")
                    if (progress == 100) {
                        println("    🎉 Task completed!")
                    }
                }
            }
        }
        
        delay(100)
        
        // Start long-running task
        withContext(Dispatchers.Main) {
            println("  Starting long-running task...")
            reporter.performLongRunningTask()
        }
        
        delay(100)
        
        println("UI state management completed\n")
    }
    
    fun demonstrateErrorHandlingInUI() = runBlocking {
        println("=== Error Handling in UI Context ===")
        
        println("1. Main-safe error handling:")
        
        sealed class Result<T> {
            data class Success<T>(val data: T) : Result<T>()
            data class Error<T>(val exception: Throwable) : Result<T>()
            data class Loading<T>(val message: String = "Loading...") : Result<T>()
        }
        
        class SafeDataLoader {
            suspend fun <T> safeLoad(
                loader: suspend () -> T
            ): Result<T> {
                return try {
                    val data = loader()
                    Result.Success(data)
                } catch (e: Exception) {
                    Result.Error(e)
                }
            }
            
            suspend fun loadUserProfile(userId: String): Result<String> {
                return withContext(Dispatchers.IO) {
                    delay(300)
                    
                    // Simulate various outcomes
                    when (Random.nextInt(3)) {
                        0 -> "User Profile: John Doe ($userId)"
                        1 -> throw Exception("User not found")
                        else -> throw Exception("Network timeout")
                    }
                }.let { Result.Success(it) }
            }
        }
        
        val loader = SafeDataLoader()
        
        // Safe loading with UI updates
        withContext(Dispatchers.Main) {
            println("  Loading user profiles...")
            
            val userIds = listOf("user1", "user2", "user3")
            
            userIds.forEach { userId ->
                val result = loader.safeLoad {
                    loader.loadUserProfile(userId).let { result ->
                        when (result) {
                            is Result.Success -> result.data
                            is Result.Error -> throw result.exception
                            is Result.Loading -> throw Exception("Still loading")
                        }
                    }
                }
                
                // Handle result on main thread
                when (result) {
                    is Result.Success -> {
                        println("    ✅ UI: Loaded ${result.data}")
                    }
                    is Result.Error -> {
                        println("    ❌ UI: Error loading $userId: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        println("    🔄 UI: ${result.message}")
                    }
                }
            }
        }
        
        println()
        
        println("2. Global error handling:")
        
        val globalErrorHandler = CoroutineExceptionHandler { _, exception ->
            // This runs on the thread where the exception occurred
            println("  🚨 Global error handler: ${exception.message}")
            
            // For UI errors, we need to dispatch to main thread
            CoroutineScope(Dispatchers.Main).launch {
                println("    📱 UI: Showing global error dialog")
            }
        }
        
        // Simulate global error handling
        launch(Dispatchers.Main + globalErrorHandler) {
            println("  Simulating global error scenario...")
            
            launch {
                delay(100)
                throw RuntimeException("Critical application error")
            }
            
            delay(200) // Let the error handler run
        }.join()
        
        delay(100)
        
        println("Error handling in UI completed\n")
    }
}

/**
 * Lifecycle-aware coroutines
 */
class LifecycleAwareCoroutines {
    
    fun demonstrateLifecyclePatterns() = runBlocking {
        println("=== Lifecycle-Aware Coroutine Patterns ===")
        
        println("1. Component lifecycle integration:")
        
        // Simulate Android-like component lifecycle
        enum class LifecycleState { CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED }
        
        class LifecycleAwareComponent {
            private var currentState = LifecycleState.CREATED
            private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            private var backgroundJobs = mutableListOf<Job>()
            
            suspend fun onCreate() {
                currentState = LifecycleState.CREATED
                println("    Component: onCreate() - State: $currentState")
                
                // Start lifecycle-aware coroutines
                startBackgroundTasks()
            }
            
            suspend fun onStart() {
                currentState = LifecycleState.STARTED
                println("    Component: onStart() - State: $currentState")
                
                // Resume any paused operations
                resumeOperations()
            }
            
            suspend fun onResume() {
                currentState = LifecycleState.RESUMED
                println("    Component: onResume() - State: $currentState")
                
                // Start UI updates
                startUIUpdates()
            }
            
            suspend fun onPause() {
                currentState = LifecycleState.PAUSED
                println("    Component: onPause() - State: $currentState")
                
                // Pause UI updates but keep background work
                pauseUIUpdates()
            }
            
            suspend fun onStop() {
                currentState = LifecycleState.STOPPED
                println("    Component: onStop() - State: $currentState")
                
                // Stop non-essential background work
                pauseOperations()
            }
            
            suspend fun onDestroy() {
                currentState = LifecycleState.DESTROYED
                println("    Component: onDestroy() - State: $currentState")
                
                // Cancel all coroutines
                componentScope.cancel()
                backgroundJobs.forEach { it.cancel() }
                backgroundJobs.clear()
            }
            
            private fun startBackgroundTasks() {
                val job = componentScope.launch {
                    while (isActive && currentState != LifecycleState.DESTROYED) {
                        // Background sync
                        withContext(Dispatchers.IO) {
                            println("      Background: Syncing data...")
                            delay(1000)
                        }
                        
                        // Update UI if component is visible
                        if (currentState in listOf(LifecycleState.RESUMED, LifecycleState.STARTED)) {
                            withContext(Dispatchers.Main.immediate) {
                                println("      UI: Updated with background data")
                            }
                        }
                    }
                }
                backgroundJobs.add(job)
            }
            
            private fun startUIUpdates() {
                val job = componentScope.launch {
                    while (isActive && currentState == LifecycleState.RESUMED) {
                        withContext(Dispatchers.Main.immediate) {
                            println("      UI: Refreshing visible content")
                        }
                        delay(500)
                    }
                }
                backgroundJobs.add(job)
            }
            
            private fun resumeOperations() {
                println("      Resuming paused operations")
            }
            
            private fun pauseUIUpdates() {
                println("      Pausing UI updates")
                // Cancel UI-specific jobs
                backgroundJobs.removeAll { job ->
                    if (job.isCompleted) {
                        true
                    } else {
                        false
                    }
                }
            }
            
            private fun pauseOperations() {
                println("      Pausing non-essential operations")
            }
        }
        
        val component = LifecycleAwareComponent()
        
        // Simulate lifecycle
        withContext(Dispatchers.Main) {
            component.onCreate()
            delay(500)
            
            component.onStart()
            delay(500)
            
            component.onResume()
            delay(1200) // Let it run for a bit
            
            component.onPause()
            delay(300)
            
            component.onStop()
            delay(300)
            
            component.onDestroy()
        }
        
        delay(200)
        
        println()
        
        println("2. Automatic lifecycle cancellation:")
        
        class AutoCancellingScope {
            private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            
            fun launchWithLifecycle(block: suspend CoroutineScope.() -> Unit): Job {
                return scope.launch(block = block)
            }
            
            suspend fun performLifecycleBoundWork() {
                println("    Starting lifecycle-bound work...")
                
                val jobs = mutableListOf<Job>()
                
                // Launch multiple lifecycle-bound operations
                jobs.add(launchWithLifecycle {
                    repeat(10) { i ->
                        println("      Operation 1 - Step $i")
                        delay(200)
                    }
                })
                
                jobs.add(launchWithLifecycle {
                    repeat(8) { i ->
                        withContext(Dispatchers.IO) {
                            println("      Operation 2 (IO) - Step $i")
                            delay(300)
                        }
                    }
                })
                
                jobs.add(launchWithLifecycle {
                    repeat(5) { i ->
                        withContext(Dispatchers.Default) {
                            println("      Operation 3 (CPU) - Step $i")
                            delay(400)
                        }
                    }
                })
                
                // Simulate lifecycle end after some time
                delay(1000)
                println("    Lifecycle ending - cancelling all operations...")
                destroy()
                
                // Wait a bit to see cancellation effect
                delay(500)
                jobs.forEach { job ->
                    println("      Job cancelled: ${job.isCancelled}")
                }
            }
            
            fun destroy() {
                scope.cancel()
            }
        }
        
        val autoCancellingScope = AutoCancellingScope()
        withContext(Dispatchers.Main) {
            autoCancellingScope.performLifecycleBoundWork()
        }
        
        println("Lifecycle-aware coroutines completed\n")
    }
}

/**
 * Best practices and common patterns
 */
class MainSafetyBestPractices {
    
    fun demonstrateBestPractices() = runBlocking {
        println("=== Main-Safety Best Practices ===")
        
        println("1. Function design best practices:")
        
        // ✅ GOOD: Clearly main-safe function
        suspend fun goodLoadUserData(userId: String): User {
            // All blocking operations properly dispatched
            val userData = withContext(Dispatchers.IO) {
                // Simulate database query
                delay(200)
                mapOf("id" to userId, "name" to "User $userId", "email" to "$userId@example.com")
            }
            
            val processedData = withContext(Dispatchers.Default) {
                // CPU-intensive data processing
                delay(100)
                userData.mapValues { (_, value) -> value.toString().uppercase() }
            }
            
            return User(
                id = processedData["id"] ?: userId,
                name = processedData["name"] ?: "Unknown",
                email = processedData["email"] ?: "unknown@example.com"
            )
        }
        
        // ❌ BAD: Not clearly main-safe
        suspend fun badLoadUserData(userId: String): User {
            // Mixing dispatchers and operations without clear separation
            val userData = withContext(Dispatchers.IO) {
                delay(200)
                // Some processing mixed with I/O
                val data = mapOf("id" to userId, "name" to "User $userId")
                data.mapValues { it.value.uppercase() } // CPU work on IO dispatcher
            }
            
            // More CPU work not properly dispatched
            val email = "$userId@example.com".reversed() // Should be on Default
            Thread.sleep(10) // NEVER do this in suspend function!
            
            return User(
                id = userData["id"] ?: userId,
                name = userData["name"] ?: "Unknown", 
                email = email
            )
        }
        
        data class User(val id: String, val name: String, val email: String)
        
        withContext(Dispatchers.Main) {
            println("  Testing good vs bad patterns:")
            
            val time1 = measureTimeMillis {
                val user1 = goodLoadUserData("123")
                println("    Good pattern result: $user1")
            }
            
            val time2 = measureTimeMillis {
                // Note: Bad pattern used for demo only - don't do this!
                try {
                    val user2 = badLoadUserData("456") 
                    println("    Bad pattern result: $user2")
                } catch (e: Exception) {
                    println("    Bad pattern failed: ${e.message}")
                }
            }
            
            println("    Good pattern time: ${time1}ms")
            println("    Bad pattern time: ${time2}ms")
        }
        
        println()
        
        println("2. Error boundary patterns:")
        
        class MainSafeErrorBoundary {
            suspend fun <T> safeExecute(
                operation: suspend () -> T,
                onError: suspend (Throwable) -> T
            ): T {
                return try {
                    operation()
                } catch (e: Exception) {
                    // Ensure error handling happens on main thread for UI updates
                    withContext(Dispatchers.Main.immediate) {
                        println("      Error boundary: Handling ${e.message}")
                        onError(e)
                    }
                }
            }
            
            suspend fun safeUIUpdate(update: suspend () -> Unit) {
                try {
                    withContext(Dispatchers.Main.immediate) {
                        update()
                    }
                } catch (e: Exception) {
                    // Log error but don't crash UI
                    println("      UI update failed safely: ${e.message}")
                }
            }
        }
        
        val errorBoundary = MainSafeErrorBoundary()
        
        withContext(Dispatchers.Main) {
            println("  Testing error boundaries:")
            
            // Safe operation with error handling
            val result = errorBoundary.safeExecute(
                operation = {
                    withContext(Dispatchers.IO) {
                        delay(100)
                        if (Random.nextBoolean()) {
                            throw RuntimeException("Simulated failure")
                        }
                        "Success data"
                    }
                },
                onError = { exception ->
                    "Error fallback: ${exception.message}"
                }
            )
            
            println("    Safe operation result: $result")
            
            // Safe UI update
            errorBoundary.safeUIUpdate {
                if (Random.nextBoolean()) {
                    throw RuntimeException("UI update failure")
                }
                println("    UI updated successfully")
            }
        }
        
        println()
        
        println("3. Performance optimization patterns:")
        
        class OptimizedMainSafeOperations {
            
            // Batch operations to reduce context switching
            suspend fun batchedOperations(items: List<String>): List<String> {
                // Single context switch for all I/O operations
                val ioResults = withContext(Dispatchers.IO) {
                    items.map { item ->
                        delay(50) // Simulate I/O for each item
                        "loaded-$item"
                    }
                }
                
                // Single context switch for all CPU operations
                val processedResults = withContext(Dispatchers.Default) {
                    ioResults.map { result ->
                        // Simulate CPU-intensive processing
                        result.uppercase().reversed()
                    }
                }
                
                return processedResults
            }
            
            // Avoid unnecessary dispatching
            suspend fun smartDispatch(needsMainThread: Boolean, operation: suspend () -> String): String {
                return if (needsMainThread) {
                    withContext(Dispatchers.Main.immediate) {
                        operation()
                    }
                } else {
                    // Execute in current context
                    operation()
                }
            }
            
            // Parallel operations when possible
            suspend fun parallelOperations(): List<String> = coroutineScope {
                val operations = (1..5).map { id ->
                    async(Dispatchers.IO) {
                        delay(Random.nextLong(100, 300))
                        "result-$id"
                    }
                }
                
                operations.awaitAll()
            }
        }
        
        val optimized = OptimizedMainSafeOperations()
        
        withContext(Dispatchers.Main) {
            println("  Testing optimization patterns:")
            
            // Batched operations
            val batchTime = measureTimeMillis {
                val results = optimized.batchedOperations(listOf("a", "b", "c"))
                println("    Batched results: $results")
            }
            
            // Smart dispatching
            val smartTime = measureTimeMillis {
                val result1 = optimized.smartDispatch(needsMainThread = true) {
                    "main-thread-result"
                }
                val result2 = optimized.smartDispatch(needsMainThread = false) {  
                    "current-context-result"
                }
                println("    Smart dispatch: $result1, $result2")
            }
            
            // Parallel operations
            val parallelTime = measureTimeMillis {
                val results = optimized.parallelOperations()
                println("    Parallel results: $results")
            }
            
            println("    Batched time: ${batchTime}ms")
            println("    Smart dispatch time: ${smartTime}ms")
            println("    Parallel time: ${parallelTime}ms")
        }
        
        println("Main-safety best practices completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Main-safe function patterns
        MainSafeFunctions().demonstrateMainSafePatterns()
        MainSafeFunctions().demonstrateDispatcherSelection()
        
        // Dispatchers.Main.immediate optimization
        MainImmediatePatterns().demonstrateMainImmediateOptimization()
        
        // UI state management
        UIStateManagement().demonstrateUIStatePatterns()
        UIStateManagement().demonstrateErrorHandlingInUI()
        
        // Lifecycle-aware coroutines
        LifecycleAwareCoroutines().demonstrateLifecyclePatterns()
        
        // Best practices
        MainSafetyBestPractices().demonstrateBestPractices()
        
        println("=== Main-Safety Conventions Summary ===")
        println("✅ Main-Safe Function Design:")
        println("   - Never block the main thread in suspend functions")
        println("   - Use withContext() to switch to appropriate dispatchers")
        println("   - Keep quick operations on main thread")
        println("   - Move I/O operations to Dispatchers.IO")
        println("   - Move CPU-intensive work to Dispatchers.Default")
        println()
        println("✅ Dispatcher Selection:")
        println("   - Dispatchers.Main for UI operations")
        println("   - Dispatchers.Main.immediate for optimization")
        println("   - Dispatchers.IO for blocking I/O operations")
        println("   - Dispatchers.Default for CPU-intensive work")
        println("   - Custom dispatchers for special requirements")
        println()
        println("✅ UI State Management:")
        println("   - Use StateFlow for reactive UI state")
        println("   - Ensure state updates happen on main thread")
        println("   - Handle errors gracefully in UI context")
        println("   - Implement proper progress reporting")
        println()
        println("✅ Lifecycle Integration:")
        println("   - Cancel coroutines when component is destroyed")
        println("   - Pause non-essential work when component is not visible")
        println("   - Use proper scoping for lifecycle-aware coroutines")
        println("   - Handle configuration changes appropriately")
        println()
        println("✅ Performance Optimization:")
        println("   - Batch operations to reduce context switching")
        println("   - Use Main.immediate to avoid unnecessary dispatching")
        println("   - Parallelize independent operations")
        println("   - Monitor and optimize dispatcher usage")
        println()
        println("✅ Best Practices:")
        println("   - Design all suspend functions to be main-safe")
        println("   - Test functions on main thread during development")
        println("   - Use error boundaries for robust error handling")
        println("   - Document dispatcher requirements in API contracts")
        println("   - Consider using architecture components for UI state")
    }
}