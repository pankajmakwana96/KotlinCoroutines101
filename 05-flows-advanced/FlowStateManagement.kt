/**
 * # Advanced Flow State Management - StateFlow and SharedFlow
 * 
 * ## Problem Description
 * Regular Flows are cold and restart on each collection, which isn't suitable for
 * state management, broadcasting values to multiple collectors, or maintaining
 * shared state across the application. Hot flows like StateFlow and SharedFlow
 * solve these challenges by maintaining state and supporting multiple collectors.
 * 
 * ## Solution Approach
 * Advanced state management patterns include:
 * - StateFlow for single-value state management
 * - SharedFlow for event broadcasting and multi-cast scenarios
 * - Converting cold flows to hot flows
 * - State synchronization and consistency patterns
 * - Memory management and lifecycle handling
 * 
 * ## Key Learning Points
 * - StateFlow always has a current value and replays last value to new collectors
 * - SharedFlow can replay multiple values and handle different replay strategies
 * - Hot flows are active even without collectors
 * - Proper lifecycle management prevents memory leaks
 * - State flows support conflation and distinct value emission
 * 
 * ## Performance Considerations
 * - StateFlow creation overhead: ~500-1000ns per flow
 * - Value emission overhead: ~50-200ns per emission
 * - Memory usage: StateFlow holds one value, SharedFlow can hold replay cache
 * - Subscription overhead: ~100-300ns per new collector
 * - Hot flows consume memory even without active collectors
 * 
 * ## Common Pitfalls
 * - Not properly managing hot flow lifecycle
 * - Memory leaks from unclosed flows
 * - Race conditions in state updates
 * - Confusion between StateFlow and SharedFlow use cases
 * - Forgetting to handle flow completion and exceptions
 * 
 * ## Real-World Applications
 * - UI state management in Android/Compose
 * - Application-wide settings and configuration
 * - Real-time data feeds and notifications
 * - Event bus implementations
 * - Shared caching and data synchronization
 * 
 * ## Related Concepts
 * - LiveData - Android's observable data holder
 * - BehaviorSubject - RxJava equivalent
 * - ConflatedBroadcastChannel - Deprecated alternative
 */

package flow.advanced.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * StateFlow fundamentals and patterns
 * 
 * StateFlow Characteristics:
 * 
 * StateFlow
 * ├── Always has current value ────── Initial value required
 * ├── Conflated by design ──────────── Only latest value matters
 * ├── Distinct values only ─────────── No duplicate emissions
 * ├── Hot stream ───────────────────── Active without collectors
 * └── Thread-safe ──────────────────── Safe concurrent access
 * 
 * StateFlow vs Flow:
 * Cold Flow: Creation -> Collection -> Execution -> Values
 * StateFlow: Creation -> Always Active -> Values Available -> Collection
 */
class StateFlowManagement {
    
    fun demonstrateBasicStateFlow() = runBlocking {
        println("=== Basic StateFlow Operations ===")
        
        // Create MutableStateFlow with initial value
        val counterState = MutableStateFlow(0)
        
        println("1. StateFlow basics:")
        println("  Initial value: ${counterState.value}")
        
        // StateFlow always has a current value
        val readOnlyCounter: StateFlow<Int> = counterState.asStateFlow()
        println("  Read-only access: ${readOnlyCounter.value}")
        
        // Launch collector
        val job = launch {
            counterState.collect { value ->
                println("  Collected: $value")
            }
        }
        
        delay(100)
        
        // Update state - triggers collection
        println("Updating state...")
        counterState.value = 5
        counterState.value = 10
        counterState.value = 15
        
        delay(100)
        
        // New collector gets current value immediately
        println("New collector joins:")
        launch {
            counterState.take(1).collect { value ->
                println("  New collector received: $value")
            }
        }
        
        delay(100)
        job.cancel()
        
        println("StateFlow basics completed\n")
    }
    
    fun demonstrateStateFlowConflation() = runBlocking {
        println("=== StateFlow Conflation ===")
        
        val state = MutableStateFlow("Initial")
        
        // Slow collector
        val job = launch {
            state.collect { value ->
                println("  Collecting: $value")
                delay(200) // Slow processing
            }
        }
        
        delay(50)
        
        // Rapid updates - will be conflated
        println("Rapid state updates:")
        state.value = "Update-1"
        state.value = "Update-2" 
        state.value = "Update-3"
        state.value = "Update-4"
        state.value = "Final"
        
        delay(1000)
        job.cancel()
        
        println()
        
        // Demonstrate distinctUntilChanged behavior
        println("Distinct values behavior:")
        val distinctState = MutableStateFlow("A")
        
        launch {
            distinctState.collect { value ->
                println("  Distinct collected: $value")
            }
        }
        
        delay(50)
        
        // Duplicate values are filtered
        distinctState.value = "A" // Won't emit (same as current)
        distinctState.value = "B" // Will emit
        distinctState.value = "B" // Won't emit (duplicate)
        distinctState.value = "C" // Will emit
        
        delay(100)
        
        println("StateFlow conflation completed\n")
    }
    
    fun demonstrateStateFlowFromFlow() = runBlocking {
        println("=== Converting Flow to StateFlow ===")
        
        // Cold flow that we want to make hot
        val dataFlow = flow {
            println("  Flow started")
            repeat(5) { i ->
                emit("Data-$i")
                delay(300)
            }
            println("  Flow completed")
        }
        
        // Convert to StateFlow with initial value
        val stateFlow = dataFlow
            .stateIn(
                scope = this,
                started = SharingStarted.Eagerly, // Start immediately
                initialValue = "Loading..."
            )
        
        println("1. StateFlow created, current value: ${stateFlow.value}")
        
        // Multiple collectors
        val job1 = launch {
            stateFlow.collect { value ->
                println("  Collector-1: $value")
            }
        }
        
        delay(500)
        
        val job2 = launch {
            stateFlow.collect { value ->
                println("  Collector-2: $value")
            }
        }
        
        delay(2000)
        job1.cancel()
        job2.cancel()
        
        println()
        
        // Different sharing strategies
        println("2. Different sharing strategies:")
        
        val lazilySharedFlow = flow {
            println("  Lazy flow started")
            emit("Lazy value")
        }.stateIn(
            scope = this,
            started = SharingStarted.Lazily, // Start on first collector
            initialValue = "Not started"
        )
        
        println("Before collection: ${lazilySharedFlow.value}")
        
        launch {
            lazilySharedFlow.collect { value ->
                println("  Lazy collected: $value")
            }
        }
        
        delay(200)
        
        println("Flow to StateFlow conversion completed\n")
    }
    
    fun demonstrateStateFlowUpdate() = runBlocking {
        println("=== StateFlow Update Patterns ===")
        
        // State with complex data
        data class UserState(
            val name: String,
            val age: Int,
            val isLoggedIn: Boolean
        )
        
        val userState = MutableStateFlow(
            UserState("Unknown", 0, false)
        )
        
        // Collect state changes
        val job = launch {
            userState.collect { state ->
                println("  User state: $state")
            }
        }
        
        delay(100)
        
        // Update using value assignment
        println("1. Direct value assignment:")
        userState.value = UserState("Alice", 25, true)
        
        delay(100)
        
        // Atomic update using update function
        println("2. Atomic update:")
        userState.update { currentState ->
            currentState.copy(age = currentState.age + 1)
        }
        
        delay(100)
        
        // Conditional update using updateAndGet
        println("3. Conditional update:")
        val newState = userState.updateAndGet { currentState ->
            if (currentState.isLoggedIn) {
                currentState.copy(name = "Alice (Premium)")
            } else {
                currentState
            }
        }
        println("  Updated to: $newState")
        
        delay(100)
        
        // Concurrent updates demonstration
        println("4. Concurrent updates:")
        val counter = MutableStateFlow(0)
        
        // Launch multiple concurrent updaters
        val jobs = (1..5).map { id ->
            launch {
                repeat(10) {
                    counter.update { it + 1 }
                    delay(10)
                }
            }
        }
        
        // Monitor final result
        launch {
            delay(200)
            println("  Final counter value: ${counter.value}")
        }
        
        jobs.forEach { it.join() }
        job.cancel()
        
        println("StateFlow update patterns completed\n")
    }
}

/**
 * SharedFlow advanced patterns and use cases
 */
class SharedFlowManagement {
    
    fun demonstrateBasicSharedFlow() = runBlocking {
        println("=== Basic SharedFlow Operations ===")
        
        // Create MutableSharedFlow
        val eventFlow = MutableSharedFlow<String>()
        
        // SharedFlow doesn't have current value by default
        println("1. SharedFlow basics:")
        
        // Start collecting before emitting
        val job1 = launch {
            eventFlow.collect { event ->
                println("  Collector-1: $event")
            }
        }
        
        delay(100)
        
        // Emit events
        println("Emitting events:")
        eventFlow.emit("Event-1")
        eventFlow.emit("Event-2")
        eventFlow.emit("Event-3")
        
        // Add second collector (won't receive past events)
        val job2 = launch {
            eventFlow.collect { event ->
                println("  Collector-2: $event")
            }
        }
        
        delay(100)
        eventFlow.emit("Event-4")
        
        delay(100)
        job1.cancel()
        job2.cancel()
        
        println("Basic SharedFlow completed\n")
    }
    
    fun demonstrateSharedFlowReplay() = runBlocking {
        println("=== SharedFlow Replay Buffer ===")
        
        // SharedFlow with replay buffer
        val replayFlow = MutableSharedFlow<String>(
            replay = 2, // Keep last 2 values for new subscribers
            extraBufferCapacity = 1
        )
        
        println("1. SharedFlow with replay:")
        
        // Emit some events before any collector
        replayFlow.emit("Event-A")
        replayFlow.emit("Event-B")
        replayFlow.emit("Event-C")
        
        // New collector will receive last 2 events (replay)
        println("Collector joins after emission:")
        val job = launch {
            replayFlow.collect { event ->
                println("  Replayed: $event")
            }
        }
        
        delay(100)
        
        // Emit more events
        replayFlow.emit("Event-D")
        replayFlow.emit("Event-E")
        
        delay(100)
        job.cancel()
        
        println()
        
        // Reset replay cache
        println("2. Reset replay cache:")
        replayFlow.resetReplayCache()
        
        launch {
            replayFlow.collect { event ->
                println("  After reset: $event")
            }
        }
        
        delay(50)
        replayFlow.emit("New-Event")
        
        delay(100)
        
        println("SharedFlow replay completed\n")
    }
    
    fun demonstrateSharedFlowFromFlow() = runBlocking {
        println("=== Converting Flow to SharedFlow ===")
        
        // Cold flow to convert
        val tickerFlow = flow {
            repeat(10) { i ->
                emit("Tick-$i")
                delay(200)
            }
        }
        
        // Convert to SharedFlow
        val sharedTicker = tickerFlow.shareIn(
            scope = this,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 1000,
                replayExpirationMillis = 2000
            ),
            replay = 1
        )
        
        println("1. Shared ticker created")
        
        // First collector
        val job1 = launch {
            sharedTicker.take(5).collect { tick ->
                println("  Collector-1: $tick")
            }
        }
        
        delay(800)
        
        // Second collector joins
        val job2 = launch {
            sharedTicker.take(3).collect { tick ->
                println("  Collector-2: $tick")
            }
        }
        
        delay(1200)
        
        // Third collector after some delay
        val job3 = launch {
            sharedTicker.take(2).collect { tick ->
                println("  Collector-3: $tick")
            }
        }
        
        job1.join()
        job2.join()
        job3.join()
        
        delay(2000) // Wait for flow to stop due to no subscribers
        
        println("SharedFlow conversion completed\n")
    }
    
    fun demonstrateEventBusPattern() = runBlocking {
        println("=== Event Bus Pattern ===")
        
        // Event types
        sealed class AppEvent {
            object UserLoggedIn : AppEvent()
            object UserLoggedOut : AppEvent()
            data class DataUpdated(val data: String) : AppEvent()
            data class ErrorOccurred(val error: String) : AppEvent()
        }
        
        // Event bus implementation
        class EventBus {
            private val _events = MutableSharedFlow<AppEvent>()
            val events: SharedFlow<AppEvent> = _events.asSharedFlow()
            
            suspend fun emit(event: AppEvent) {
                _events.emit(event)
            }
            
            fun subscribe(): SharedFlow<AppEvent> = events
        }
        
        val eventBus = EventBus()
        
        // Different event subscribers
        val authJob = launch {
            eventBus.events
                .filterIsInstance<AppEvent.UserLoggedIn>()
                .collect { event ->
                    println("  Auth Service: User logged in")
                }
        }
        
        val dataJob = launch {
            eventBus.events
                .filterIsInstance<AppEvent.DataUpdated>()
                .collect { event ->
                    println("  Data Service: ${event.data}")
                }
        }
        
        val errorJob = launch {
            eventBus.events
                .filterIsInstance<AppEvent.ErrorOccurred>()
                .collect { event ->
                    println("  Error Handler: ${event.error}")
                }
        }
        
        val allEventsJob = launch {
            eventBus.events.collect { event ->
                println("  Event Logger: $event")
            }
        }
        
        delay(100)
        
        // Emit various events
        println("Emitting events:")
        eventBus.emit(AppEvent.UserLoggedIn)
        eventBus.emit(AppEvent.DataUpdated("User profile updated"))
        eventBus.emit(AppEvent.ErrorOccurred("Network timeout"))
        eventBus.emit(AppEvent.UserLoggedOut)
        
        delay(200)
        
        // Cleanup
        authJob.cancel()
        dataJob.cancel()
        errorJob.cancel()
        allEventsJob.cancel()
        
        println("Event bus pattern completed\n")
    }
}

/**
 * State synchronization and consistency patterns
 */
class FlowStateSynchronization {
    
    fun demonstrateStateSync() = runBlocking {
        println("=== State Synchronization Patterns ===")
        
        // Multiple state sources that need synchronization
        data class AppState(
            val userCount: Int,
            val serverStatus: String,
            val lastUpdate: Long
        )
        
        val userCountFlow = flow {
            repeat(5) { i ->
                emit(Random.nextInt(100, 1000))
                delay(800)
            }
        }
        
        val serverStatusFlow = flow {
            val statuses = listOf("Healthy", "Degraded", "Down", "Recovering")
            repeat(4) { i ->
                emit(statuses[i % statuses.size])
                delay(1200)
            }
        }
        
        // Combine multiple flows into single state
        println("1. Combining multiple state flows:")
        val combinedState = combine(
            userCountFlow,
            serverStatusFlow
        ) { userCount, serverStatus ->
            AppState(userCount, serverStatus, System.currentTimeMillis())
        }.stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = AppState(0, "Unknown", 0)
        )
        
        val job = launch {
            combinedState.collect { state ->
                println("  Combined state: $state")
            }
        }
        
        delay(5000)
        job.cancel()
        
        println()
        
        // State synchronization with external updates
        println("2. External state synchronization:")
        
        data class Counter(val value: Int, val lastModified: Long)
        
        val localCounter = MutableStateFlow(Counter(0, System.currentTimeMillis()))
        val remoteCounter = MutableStateFlow(Counter(0, System.currentTimeMillis()))
        
        // Sync logic: latest timestamp wins
        suspend fun syncCounters() {
            val local = localCounter.value
            val remote = remoteCounter.value
            
            when {
                local.lastModified > remote.lastModified -> {
                    println("  Syncing remote from local: ${local.value}")
                    remoteCounter.value = local
                }
                remote.lastModified > local.lastModified -> {
                    println("  Syncing local from remote: ${remote.value}")
                    localCounter.value = remote
                }
                else -> {
                    println("  Counters already in sync")
                }
            }
        }
        
        // Monitor both counters
        launch {
            localCounter.collect { counter ->
                println("  Local counter: ${counter.value} at ${counter.lastModified}")
            }
        }
        
        launch {
            remoteCounter.collect { counter ->
                println("  Remote counter: ${counter.value} at ${counter.lastModified}")
            }
        }
        
        delay(100)
        
        // Simulate concurrent updates
        localCounter.value = Counter(5, System.currentTimeMillis())
        delay(50)
        remoteCounter.value = Counter(10, System.currentTimeMillis())
        delay(50)
        
        syncCounters()
        delay(100)
        
        println("State synchronization completed\n")
    }
    
    fun demonstrateOptimisticUpdates() = runBlocking {
        println("=== Optimistic Updates Pattern ===")
        
        data class Task(
            val id: String,
            val title: String,
            val completed: Boolean,
            val syncing: Boolean = false
        )
        
        class TaskRepository {
            private val _tasks = MutableStateFlow<List<Task>>(emptyList())
            val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
            
            suspend fun addTask(title: String) {
                val newTask = Task(
                    id = "task-${Random.nextInt(1000)}",
                    title = title,
                    completed = false,
                    syncing = true
                )
                
                // Optimistic update
                _tasks.update { currentTasks ->
                    currentTasks + newTask
                }
                
                try {
                    // Simulate API call
                    delay(Random.nextLong(200, 800))
                    if (Random.nextBoolean()) {
                        throw Exception("API Error")
                    }
                    
                    // Success - mark as synced
                    _tasks.update { currentTasks ->
                        currentTasks.map { task ->
                            if (task.id == newTask.id) {
                                task.copy(syncing = false)
                            } else task
                        }
                    }
                    
                    println("  ✅ Task '${newTask.title}' synced successfully")
                    
                } catch (e: Exception) {
                    // Failed - revert optimistic update
                    _tasks.update { currentTasks ->
                        currentTasks.filter { it.id != newTask.id }
                    }
                    
                    println("  ❌ Failed to sync task '${newTask.title}': ${e.message}")
                }
            }
            
            suspend fun toggleTask(taskId: String) {
                // Find and optimistically update
                val taskToUpdate = _tasks.value.find { it.id == taskId } ?: return
                
                _tasks.update { currentTasks ->
                    currentTasks.map { task ->
                        if (task.id == taskId) {
                            task.copy(completed = !task.completed, syncing = true)
                        } else task
                    }
                }
                
                try {
                    // Simulate API call
                    delay(300)
                    
                    // Success
                    _tasks.update { currentTasks ->
                        currentTasks.map { task ->
                            if (task.id == taskId) {
                                task.copy(syncing = false)
                            } else task
                        }
                    }
                    
                    println("  ✅ Task toggle synced")
                    
                } catch (e: Exception) {
                    // Revert on failure
                    _tasks.update { currentTasks ->
                        currentTasks.map { task ->
                            if (task.id == taskId) {
                                taskToUpdate.copy(syncing = false)
                            } else task
                        }
                    }
                    
                    println("  ❌ Failed to sync task toggle")
                }
            }
        }
        
        val repository = TaskRepository()
        
        // Monitor tasks
        val job = launch {
            repository.tasks.collect { tasks ->
                println("  Tasks: ${tasks.map { "${it.title}(${if (it.syncing) "syncing" else "synced"})" }}")
            }
        }
        
        delay(100)
        
        // Add tasks with optimistic updates
        println("Adding tasks optimistically:")
        launch { repository.addTask("Learn Kotlin Flows") }
        launch { repository.addTask("Build awesome app") }
        launch { repository.addTask("Write documentation") }
        
        delay(1500)
        
        // Toggle first task
        val firstTask = repository.tasks.value.firstOrNull()
        if (firstTask != null) {
            println("Toggling first task:")
            repository.toggleTask(firstTask.id)
        }
        
        delay(500)
        job.cancel()
        
        println("Optimistic updates completed\n")
    }
}

/**
 * Hot flow lifecycle management
 */
class HotFlowLifecycle {
    
    fun demonstrateLifecycleManagement() = runBlocking {
        println("=== Hot Flow Lifecycle Management ===")
        
        // Lifecycle-aware flow manager
        class FlowManager(private val scope: CoroutineScope) {
            private val activeFlows = mutableMapOf<String, Job>()
            
            fun startDataStream(name: String): StateFlow<String> {
                val flow = flow {
                    var counter = 0
                    while (currentCoroutineContext().isActive) {
                        emit("$name-${counter++}")
                        delay(500)
                    }
                }.stateIn(
                    scope = scope,
                    started = SharingStarted.Lazily,
                    initialValue = "$name-initial"
                )
                
                println("  Started flow: $name")
                return flow
            }
            
            fun stopAllFlows() {
                activeFlows.values.forEach { it.cancel() }
                activeFlows.clear()
                println("  All flows stopped")
            }
            
            fun getActiveFlowCount(): Int = activeFlows.size
        }
        
        val flowScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val flowManager = FlowManager(flowScope)
        
        // Start multiple flows
        println("1. Starting lifecycle-managed flows:")
        val dataFlow1 = flowManager.startDataStream("DataStream1")
        val dataFlow2 = flowManager.startDataStream("DataStream2")
        
        // Collect from flows
        val job1 = launch {
            dataFlow1.take(3).collect { data ->
                println("  Stream1: $data")
            }
        }
        
        val job2 = launch {
            dataFlow2.take(3).collect { data ->
                println("  Stream2: $data")
            }
        }
        
        job1.join()
        job2.join()
        
        // Proper cleanup
        flowScope.cancel()
        println("Flow scope cancelled")
        
        println()
        
        // Memory leak prevention
        println("2. Memory leak prevention:")
        
        class LeakProneService {
            private val _events = MutableSharedFlow<String>()
            val events: SharedFlow<String> = _events.asSharedFlow()
            
            fun start() {
                println("  Service started - potential memory leak source")
            }
            
            fun stop() {
                // Important: Complete the flow to release resources
                _events.tryEmit("SERVICE_STOPPED")
                println("  Service stopped - resources cleaned up")
            }
            
            suspend fun emit(event: String) {
                _events.emit(event)
            }
        }
        
        val service = LeakProneService()
        service.start()
        
        val serviceJob = launch {
            service.events.collect { event ->
                println("  Service event: $event")
                if (event == "SERVICE_STOPPED") {
                    println("  Service shutdown detected")
                }
            }
        }
        
        delay(100)
        service.emit("WORKING")
        service.emit("PROCESSING")
        service.stop()
        
        delay(200)
        serviceJob.cancel()
        
        println("Hot flow lifecycle management completed\n")
    }
    
    fun demonstrateSharedFlowScoping() = runBlocking {
        println("=== SharedFlow Scoping Strategies ===")
        
        // Application-scoped vs component-scoped flows
        class ApplicationEventBus(private val scope: CoroutineScope) {
            private val _globalEvents = MutableSharedFlow<String>(replay = 1)
            val globalEvents: SharedFlow<String> = _globalEvents.asSharedFlow()
            
            suspend fun broadcast(event: String) {
                _globalEvents.emit("GLOBAL: $event")
            }
        }
        
        class ComponentEventBus {
            private val _componentEvents = MutableSharedFlow<String>()
            val componentEvents: SharedFlow<String> = _componentEvents.asSharedFlow()
            
            suspend fun emit(event: String) {
                _componentEvents.emit("COMPONENT: $event")
            }
        }
        
        // Application scope - long-lived
        val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val appEventBus = ApplicationEventBus(appScope)
        
        // Component scope - shorter-lived
        val componentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val componentEventBus = ComponentEventBus()
        
        println("1. Different scoping levels:")
        
        // Global listener (long-lived)
        val globalJob = launch {
            appEventBus.globalEvents.collect { event ->
                println("  Global listener: $event")
            }
        }
        
        // Component listener (component lifetime)
        val componentJob = componentScope.launch {
            componentEventBus.componentEvents.collect { event ->
                println("  Component listener: $event")
            }
        }
        
        delay(100)
        
        // Emit events
        appEventBus.broadcast("App started")
        componentEventBus.emit("Component initialized")
        componentEventBus.emit("Component working")
        appEventBus.broadcast("Background task completed")
        
        delay(200)
        
        // Simulate component destruction
        println("Destroying component...")
        componentScope.cancel()
        
        // Global events continue
        appEventBus.broadcast("Component destroyed")
        
        delay(200)
        
        // Cleanup
        globalJob.cancel()
        appScope.cancel()
        
        println("SharedFlow scoping completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // StateFlow management
        StateFlowManagement().demonstrateBasicStateFlow()
        StateFlowManagement().demonstrateStateFlowConflation()
        StateFlowManagement().demonstrateStateFlowFromFlow()
        StateFlowManagement().demonstrateStateFlowUpdate()
        
        // SharedFlow management
        SharedFlowManagement().demonstrateBasicSharedFlow()
        SharedFlowManagement().demonstrateSharedFlowReplay()
        SharedFlowManagement().demonstrateSharedFlowFromFlow()
        SharedFlowManagement().demonstrateEventBusPattern()
        
        // State synchronization
        FlowStateSynchronization().demonstrateStateSync()
        FlowStateSynchronization().demonstrateOptimisticUpdates()
        
        // Lifecycle management
        HotFlowLifecycle().demonstrateLifecycleManagement()
        HotFlowLifecycle().demonstrateSharedFlowScoping()
        
        println("=== Advanced State Management Summary ===")
        println("✅ StateFlow:")
        println("   - Single value holder with initial value")
        println("   - Conflated and distinct emissions")
        println("   - Perfect for UI state management")
        println("   - Thread-safe updates with atomic operations")
        println()
        println("✅ SharedFlow:")
        println("   - Multi-cast hot stream")
        println("   - Configurable replay buffer")
        println("   - Event broadcasting and notification patterns")
        println("   - No current value by default")
        println()
        println("✅ Hot Flow Conversion:")
        println("   - stateIn() for StateFlow creation")
        println("   - shareIn() for SharedFlow creation")
        println("   - Different sharing strategies (Eagerly, Lazily, WhileSubscribed)")
        println("   - Proper scope and lifecycle management")
        println()
        println("✅ Advanced Patterns:")
        println("   - Event bus implementation")
        println("   - State synchronization across sources")
        println("   - Optimistic updates with rollback")
        println("   - Memory leak prevention")
        println("   - Scoped flow management")
    }
}