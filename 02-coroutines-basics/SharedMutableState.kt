/**
 * # Managing Shared Mutable State in Coroutines
 * 
 * ## Problem Description
 * When multiple coroutines access and modify shared state concurrently, race conditions
 * and data corruption can occur. Traditional thread synchronization mechanisms like
 * synchronized blocks are not suitable for suspend functions as they can block threads.
 * Coroutines need special patterns for managing shared mutable state safely.
 * 
 * ## Solution Approach
 * Shared mutable state management includes:
 * - Atomic operations for lock-free programming
 * - Thread-safe collections and data structures
 * - Coroutine-specific synchronization primitives (Mutex, Semaphore)
 * - Actor pattern for state encapsulation
 * - Immutable data with copy-on-write patterns
 * - StateFlow for reactive state management
 * 
 * ## Key Learning Points
 * - Race conditions occur when multiple coroutines modify shared state
 * - Atomic operations provide lock-free thread safety
 * - Mutex provides coroutine-friendly locking
 * - Confinement strategies isolate state to specific contexts
 * - Immutable data structures eliminate race conditions
 * - Proper synchronization prevents data corruption
 * 
 * ## Performance Considerations
 * - Atomic operations: ~1-5ns per operation
 * - Mutex lock/unlock: ~100-500ns per operation
 * - Thread-safe collections: ~10-50ns overhead per operation
 * - Synchronization overhead scales with contention level
 * - Lock-free algorithms perform better under high contention
 * 
 * ## Common Pitfalls
 * - Using regular variables without synchronization
 * - Blocking synchronization primitives in suspend functions
 * - Race conditions in compound operations
 * - Deadlocks from nested locking
 * - Performance degradation from excessive synchronization
 * 
 * ## Real-World Applications
 * - Shared counters and statistics
 * - Cache implementations
 * - Connection pools
 * - Configuration management
 * - User session state
 * 
 * ## Related Concepts
 * - Actor model for state encapsulation
 * - Software Transactional Memory (STM)
 * - Lock-free data structures
 * - Compare-and-swap operations
 */

package coroutines.basics.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import java.util.concurrent.atomic.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Race conditions and the need for synchronization
 * 
 * Race Condition Example:
 * 
 * Coroutine 1 ──> Read counter (0) ──> Increment ──> Write counter (1)
 *                       │                                    │
 * Coroutine 2 ──> Read counter (0) ──> Increment ──> Write counter (1)
 * 
 * Result: Both read 0, both write 1, but expected result is 2
 * 
 * Thread-Safe Solutions:
 * ├── Atomic Operations ──── Lock-free, high performance
 * ├── Mutex ──────────────── Coroutine-safe locking
 * ├── Thread-Safe Collections ── Built-in synchronization
 * └── Confinement ────────── Single-threaded access
 */
class RaceConditionsAndSynchronization {
    
    fun demonstrateRaceCondition() = runBlocking {
        println("=== Race Condition Demonstration ===")
        
        // Unsafe shared counter
        var unsafeCounter = 0
        
        println("1. Race condition with unsafe counter:")
        val unsafeJobs = (1..100).map {
            launch {
                repeat(100) {
                    // Race condition: read, modify, write
                    unsafeCounter++
                }
            }
        }
        
        unsafeJobs.forEach { it.join() }
        println("  Unsafe counter result: $unsafeCounter (expected: 10000)")
        println("  Data lost due to race condition: ${10000 - unsafeCounter}")
        
        println()
        
        // Thread-safe atomic counter
        val atomicCounter = AtomicInteger(0)
        
        println("2. Thread-safe atomic counter:")
        val safeJobs = (1..100).map {
            launch {
                repeat(100) {
                    atomicCounter.incrementAndGet()
                }
            }
        }
        
        safeJobs.forEach { it.join() }
        println("  Atomic counter result: ${atomicCounter.get()} (expected: 10000)")
        println("  Data consistency: ${if (atomicCounter.get() == 10000) "✅ Perfect" else "❌ Still has issues"}")
        
        println()
        
        // Demonstrating compound operation race condition
        println("3. Compound operation race condition:")
        
        val atomicValue = AtomicInteger(0)
        var raceConditionCount = 0
        
        val compoundJobs = (1..50).map {
            launch {
                repeat(100) {
                    // This is still a race condition despite using atomic!
                    val current = atomicValue.get()
                    if (current % 2 == 0) {
                        val newValue = atomicValue.incrementAndGet()
                        if (newValue != current + 1) {
                            raceConditionCount++
                        }
                    }
                }
            }
        }
        
        compoundJobs.forEach { it.join() }
        println("  Compound operation race conditions detected: $raceConditionCount")
        println("  Final value: ${atomicValue.get()}")
        
        println("Race condition demonstration completed\n")
    }
    
    fun demonstrateAtomicOperations() = runBlocking {
        println("=== Atomic Operations ===")
        
        // Various atomic types
        val atomicInt = AtomicInteger(0)
        val atomicLong = AtomicLong(0L)
        val atomicBool = AtomicBoolean(false)
        val atomicRef = AtomicReference("initial")
        
        println("1. Basic atomic operations:")
        
        // Atomic integer operations
        val intJobs = (1..20).map { workerId ->
            launch {
                repeat(50) {
                    val oldValue = atomicInt.getAndIncrement()
                    val newValue = atomicInt.get()
                    
                    if (workerId == 1 && it % 10 == 0) {
                        println("  Worker-$workerId: $oldValue -> $newValue")
                    }
                }
            }
        }
        
        intJobs.forEach { it.join() }
        println("  Final atomic int: ${atomicInt.get()}")
        
        println()
        
        // Compare-and-swap operations
        println("2. Compare-and-swap operations:")
        
        val casCounter = AtomicInteger(0)
        val casSuccesses = AtomicInteger(0)
        val casFailures = AtomicInteger(0)
        
        val casJobs = (1..10).map { workerId ->
            launch {
                repeat(100) {
                    var success = false
                    while (!success) {
                        val current = casCounter.get()
                        val newValue = current + 1
                        success = casCounter.compareAndSet(current, newValue)
                        
                        if (success) {
                            casSuccesses.incrementAndGet()
                        } else {
                            casFailures.incrementAndGet()
                        }
                    }
                }
            }
        }
        
        casJobs.forEach { it.join() }
        println("  CAS final value: ${casCounter.get()}")
        println("  CAS successes: ${casSuccesses.get()}")
        println("  CAS failures (retries): ${casFailures.get()}")
        
        println()
        
        // Atomic reference with complex objects
        println("3. Atomic reference with complex objects:")
        
        data class Counter(val value: Int, val lastUpdater: String)
        
        val complexRef = AtomicReference(Counter(0, "init"))
        
        val refJobs = (1..5).map { workerId ->
            launch {
                repeat(20) {
                    var updated = false
                    while (!updated) {
                        val current = complexRef.get()
                        val newCounter = Counter(current.value + 1, "worker-$workerId")
                        updated = complexRef.compareAndSet(current, newCounter)
                        
                        if (updated && it % 5 == 0) {
                            println("  Worker-$workerId updated counter to ${newCounter.value}")
                        }
                    }
                    delay(Random.nextLong(1, 10))
                }
            }
        }
        
        refJobs.forEach { it.join() }
        val finalCounter = complexRef.get()
        println("  Final complex counter: value=${finalCounter.value}, lastUpdater=${finalCounter.lastUpdater}")
        
        println("Atomic operations demonstration completed\n")
    }
    
    fun demonstrateAtomicArrays() = runBlocking {
        println("=== Atomic Arrays and Collections ===")
        
        println("1. AtomicIntegerArray operations:")
        
        val atomicArray = AtomicIntegerArray(10)
        
        // Initialize array
        repeat(10) { i ->
            atomicArray.set(i, i * 10)
        }
        
        println("  Initial array: ${(0 until 10).map { atomicArray.get(it) }}")
        
        // Concurrent array updates
        val arrayJobs = (1..5).map { workerId ->
            launch {
                repeat(20) {
                    val index = Random.nextInt(10)
                    val incrementResult = atomicArray.getAndIncrement(index)
                    
                    if (it % 5 == 0) {
                        println("  Worker-$workerId: array[$index] was $incrementResult, now ${atomicArray.get(index)}")
                    }
                }
            }
        }
        
        arrayJobs.forEach { it.join() }
        println("  Final array: ${(0 until 10).map { atomicArray.get(it) }}")
        
        println()
        
        // Atomic field updaters
        println("2. Atomic field updaters:")
        
        class Statistics {
            @Volatile
            var totalRequests: Long = 0
            
            @Volatile
            var totalErrors: Long = 0
            
            companion object {
                private val requestsUpdater = AtomicLongFieldUpdater.newUpdater(Statistics::class.java, "totalRequests")
                private val errorsUpdater = AtomicLongFieldUpdater.newUpdater(Statistics::class.java, "totalErrors")
            }
            
            fun incrementRequests(): Long = requestsUpdater.incrementAndGet(this)
            fun incrementErrors(): Long = errorsUpdater.incrementAndGet(this)
            
            override fun toString(): String = "Statistics(requests=$totalRequests, errors=$totalErrors)"
        }
        
        val stats = Statistics()
        
        val statsJobs = (1..8).map { workerId ->
            launch {
                repeat(25) {
                    stats.incrementRequests()
                    
                    // Simulate some errors
                    if (Random.nextDouble() < 0.1) {
                        stats.incrementErrors()
                    }
                    
                    delay(1)
                }
            }
        }
        
        statsJobs.forEach { it.join() }
        println("  Final statistics: $stats")
        println("  Error rate: ${"%.2f".format((stats.totalErrors.toDouble() / stats.totalRequests) * 100)}%")
        
        println("Atomic arrays and collections completed\n")
    }
}

/**
 * Coroutine-specific synchronization primitives
 */
class CoroutineSynchronization {
    
    fun demonstrateMutex() = runBlocking {
        println("=== Mutex for Coroutine Synchronization ===")
        
        val mutex = Mutex()
        var sharedResource = 0
        val accessLog = mutableListOf<String>()
        
        println("1. Mutex protecting shared resource:")
        
        val mutexJobs = (1..5).map { workerId ->
            launch {
                repeat(10) { iteration ->
                    mutex.withLock {
                        // Critical section - only one coroutine at a time
                        val currentValue = sharedResource
                        delay(10) // Simulate some work
                        sharedResource = currentValue + 1
                        
                        val logEntry = "Worker-$workerId-$iteration: ${currentValue + 1}"
                        accessLog.add(logEntry)
                        
                        if (iteration % 3 == 0) {
                            println("  $logEntry")
                        }
                    }
                }
            }
        }
        
        mutexJobs.forEach { it.join() }
        println("  Final shared resource value: $sharedResource (expected: 50)")
        println("  Total access log entries: ${accessLog.size}")
        
        println()
        
        // Demonstrating mutex timeout
        println("2. Mutex with timeout:")
        
        val timeoutMutex = Mutex()
        
        // Long-running task holding the mutex
        val longTask = launch {
            timeoutMutex.withLock {
                println("  Long task: Acquired mutex, working for 2 seconds...")
                delay(2000)
                println("  Long task: Finished work")
            }
        }
        
        delay(100) // Let long task acquire mutex
        
        // Tasks trying to acquire with timeout
        val timeoutTasks = (1..3).map { taskId ->
            launch {
                try {
                    withTimeout(500) {
                        timeoutMutex.withLock {
                            println("  Task-$taskId: Got mutex!")
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    println("  Task-$taskId: Timed out waiting for mutex")
                }
            }
        }
        
        longTask.join()
        timeoutTasks.forEach { it.join() }
        
        println()
        
        // Reentrant mutex behavior
        println("3. Mutex reentrant behavior:")
        
        val reentrantMutex = Mutex()
        
        suspend fun recursiveFunction(depth: Int) {
            if (depth <= 0) return
            
            reentrantMutex.withLock {
                println("  Recursive function at depth $depth")
                delay(50)
                // Note: Kotlin's Mutex is NOT reentrant, this would deadlock
                // recursiveFunction(depth - 1) // Don't uncomment this!
            }
            
            // Call recursively outside the lock
            recursiveFunction(depth - 1)
        }
        
        launch {
            recursiveFunction(3)
        }.join()
        
        println("Mutex demonstration completed\n")
    }
    
    fun demonstrateSemaphore() = runBlocking {
        println("=== Semaphore for Resource Limiting ===")
        
        println("1. Semaphore for connection pool:")
        
        // Simulate a connection pool with 3 connections
        val connectionPool = Semaphore(permits = 3)
        val activeConnections = AtomicInteger(0)
        val maxConcurrentConnections = AtomicInteger(0)
        
        val connectionJobs = (1..10).map { clientId ->
            launch {
                connectionPool.withPermit {
                    val current = activeConnections.incrementAndGet()
                    val max = maxConcurrentConnections.updateAndGet { maxOf(it, current) }
                    
                    println("  Client-$clientId: Acquired connection ($current active, max seen: $max)")
                    
                    // Simulate work with connection
                    delay(Random.nextLong(100, 500))
                    
                    activeConnections.decrementAndGet()
                    println("  Client-$clientId: Released connection")
                }
            }
        }
        
        connectionJobs.forEach { it.join() }
        println("  Maximum concurrent connections used: ${maxConcurrentConnections.get()}")
        
        println()
        
        // Rate limiting with semaphore
        println("2. Rate limiting with semaphore:")
        
        class RateLimiter(private val requestsPerSecond: Int) {
            private val semaphore = Semaphore(requestsPerSecond)
            
            suspend fun execute(operation: suspend () -> Unit) {
                semaphore.withPermit {
                    operation()
                }
                
                // Release permit after delay to implement rate limiting
                delay(1000L / requestsPerSecond)
            }
        }
        
        val rateLimiter = RateLimiter(requestsPerSecond = 2) // Max 2 requests per second
        
        val rateLimitedJobs = (1..8).map { requestId ->
            launch {
                val startTime = System.currentTimeMillis()
                rateLimiter.execute {
                    val elapsed = System.currentTimeMillis() - startTime
                    println("  Request-$requestId executed after ${elapsed}ms")
                }
            }
        }
        
        rateLimitedJobs.forEach { it.join() }
        
        println()
        
        // Fair semaphore demonstration
        println("3. Fair vs unfair semaphore:")
        
        val fairSemaphore = Semaphore(permits = 1, acquiredPermits = 0)
        
        val fairnessJobs = (1..5).map { workerId ->
            launch {
                repeat(3) { iteration ->
                    fairSemaphore.withPermit {
                        println("  Fair worker-$workerId-$iteration got permit")
                        delay(100)
                    }
                }
            }
        }
        
        fairnessJobs.forEach { it.join() }
        
        println("Semaphore demonstration completed\n")
    }
    
    fun demonstrateChannelSynchronization() = runBlocking {
        println("=== Channel-Based Synchronization ===")
        
        println("1. Channel as a mutex alternative:")
        
        // Channel with capacity 1 acts like a mutex
        val mutexChannel = kotlinx.coroutines.channels.Channel<Unit>(capacity = 1)
        mutexChannel.trySend(Unit) // Initialize with one permit
        
        var channelProtectedResource = 0
        
        suspend fun withChannelLock(action: suspend () -> Unit) {
            mutexChannel.receive() // Acquire "lock"
            try {
                action()
            } finally {
                mutexChannel.send(Unit) // Release "lock"
            }
        }
        
        val channelJobs = (1..5).map { workerId ->
            launch {
                repeat(8) { iteration ->
                    withChannelLock {
                        val oldValue = channelProtectedResource
                        delay(20) // Simulate work
                        channelProtectedResource = oldValue + 1
                        
                        if (iteration % 2 == 0) {
                            println("  Worker-$workerId: ${oldValue + 1}")
                        }
                    }
                }
            }
        }
        
        channelJobs.forEach { it.join() }
        println("  Channel-protected resource final value: $channelProtectedResource")
        
        println()
        
        // Producer-consumer synchronization
        println("2. Producer-consumer synchronization:")
        
        data class WorkItem(val id: Int, val data: String)
        
        val workQueue = kotlinx.coroutines.channels.Channel<WorkItem>(capacity = 5)
        val completedWork = AtomicInteger(0)
        
        // Producer
        val producer = launch {
            repeat(20) { id ->
                val workItem = WorkItem(id, "data-$id")
                workQueue.send(workItem)
                println("  Producer: Created work item $id")
                delay(50)
            }
            workQueue.close()
        }
        
        // Multiple consumers
        val consumers = (1..3).map { consumerId ->
            launch {
                var itemsProcessed = 0
                for (workItem in workQueue) {
                    delay(Random.nextLong(30, 100)) // Processing time
                    completedWork.incrementAndGet()
                    itemsProcessed++
                    println("  Consumer-$consumerId: Processed work item ${workItem.id}")
                }
                println("  Consumer-$consumerId: Processed $itemsProcessed items")
            }
        }
        
        producer.join()
        consumers.forEach { it.join() }
        println("  Total work items completed: ${completedWork.get()}")
        
        println("Channel-based synchronization completed\n")
    }
}

/**
 * Thread-safe collections and data structures
 */
class ThreadSafeCollections {
    
    fun demonstrateConcurrentCollections() = runBlocking {
        println("=== Thread-Safe Collections ===")
        
        println("1. ConcurrentHashMap operations:")
        
        val concurrentMap = ConcurrentHashMap<String, AtomicInteger>()
        
        // Initialize some counters
        (1..5).forEach { i ->
            concurrentMap["counter$i"] = AtomicInteger(0)
        }
        
        val mapJobs = (1..10).map { workerId ->
            launch {
                repeat(20) {
                    val key = "counter${Random.nextInt(1, 6)}"
                    val counter = concurrentMap[key]
                    if (counter != null) {
                        val newValue = counter.incrementAndGet()
                        
                        if (it % 5 == 0) {
                            println("  Worker-$workerId: $key = $newValue")
                        }
                    }
                    
                    // Occasionally add new counters
                    if (Random.nextDouble() < 0.1) {
                        val newKey = "dynamic-${workerId}-${it}"
                        concurrentMap.putIfAbsent(newKey, AtomicInteger(1))
                    }
                }
            }
        }
        
        mapJobs.forEach { it.join() }
        
        println("  Final concurrent map state:")
        concurrentMap.forEach { (key, value) ->
            println("    $key: ${value.get()}")
        }
        
        println()
        
        // Thread-safe list operations
        println("2. Thread-safe list operations:")
        
        val synchronizedList = mutableListOf<String>().let {
            java.util.Collections.synchronizedList(it)
        }
        
        val listJobs = (1..8).map { workerId ->
            launch {
                repeat(15) { iteration ->
                    val item = "worker-$workerId-item-$iteration"
                    
                    synchronized(synchronizedList) {
                        synchronizedList.add(item)
                        
                        if (synchronizedList.size % 20 == 0) {
                            println("  List size reached: ${synchronizedList.size}")
                        }
                    }
                    
                    delay(Random.nextLong(1, 10))
                }
            }
        }
        
        listJobs.forEach { it.join() }
        
        synchronized(synchronizedList) {
            println("  Final list size: ${synchronizedList.size}")
            println("  Sample items: ${synchronizedList.take(5)}")
        }
        
        println()
        
        // Custom thread-safe data structure
        println("3. Custom thread-safe data structure:")
        
        class ThreadSafeCounter {
            private val count = AtomicInteger(0)
            private val history = ConcurrentHashMap<Long, Int>()
            
            fun increment(): Int {
                val newValue = count.incrementAndGet()
                history[System.currentTimeMillis()] = newValue
                return newValue
            }
            
            fun decrement(): Int {
                val newValue = count.decrementAndGet()
                history[System.currentTimeMillis()] = newValue
                return newValue
            }
            
            fun get(): Int = count.get()
            
            fun getHistory(): Map<Long, Int> = history.toMap()
        }
        
        val safeCounter = ThreadSafeCounter()
        
        val counterJobs = (1..6).map { workerId ->
            launch {
                repeat(25) {
                    if (Random.nextBoolean()) {
                        val newValue = safeCounter.increment()
                        if (it % 8 == 0) {
                            println("  Worker-$workerId: Incremented to $newValue")
                        }
                    } else {
                        val newValue = safeCounter.decrement()
                        if (it % 8 == 0) {
                            println("  Worker-$workerId: Decremented to $newValue")
                        }
                    }
                    delay(Random.nextLong(5, 20))
                }
            }
        }
        
        counterJobs.forEach { it.join() }
        
        println("  Final counter value: ${safeCounter.get()}")
        println("  History entries: ${safeCounter.getHistory().size}")
        
        println("Thread-safe collections completed\n")
    }
    
    fun demonstrateImmutableDataPatterns() = runBlocking {
        println("=== Immutable Data Patterns ===")
        
        println("1. Copy-on-write pattern:")
        
        data class ImmutableState(
            val counter: Int = 0,
            val items: List<String> = emptyList(),
            val metadata: Map<String, Any> = emptyMap()
        )
        
        val stateRef = AtomicReference(ImmutableState())
        
        suspend fun updateState(updater: (ImmutableState) -> ImmutableState) {
            var updated = false
            while (!updated) {
                val current = stateRef.get()
                val newState = updater(current)
                updated = stateRef.compareAndSet(current, newState)
            }
        }
        
        val immutableJobs = (1..5).map { workerId ->
            launch {
                repeat(10) { iteration ->
                    updateState { current ->
                        current.copy(
                            counter = current.counter + 1,
                            items = current.items + "worker-$workerId-$iteration",
                            metadata = current.metadata + ("lastUpdater" to "worker-$workerId")
                        )
                    }
                    
                    delay(Random.nextLong(10, 50))
                }
            }
        }
        
        immutableJobs.forEach { it.join() }
        
        val finalState = stateRef.get()
        println("  Final immutable state:")
        println("    Counter: ${finalState.counter}")
        println("    Items count: ${finalState.items.size}")
        println("    Last updater: ${finalState.metadata["lastUpdater"]}")
        
        println()
        
        // Persistent data structures simulation
        println("2. Persistent data structures pattern:")
        
        class PersistentList<T>(private val items: List<T> = emptyList()) {
            fun add(item: T): PersistentList<T> = PersistentList(items + item)
            fun remove(item: T): PersistentList<T> = PersistentList(items - item)
            fun get(index: Int): T = items[index]
            fun size(): Int = items.size
            fun toList(): List<T> = items.toList()
        }
        
        val persistentListRef = AtomicReference(PersistentList<String>())
        
        val persistentJobs = (1..4).map { workerId ->
            launch {
                repeat(15) { iteration ->
                    var updated = false
                    while (!updated) {
                        val current = persistentListRef.get()
                        val newList = current.add("worker-$workerId-item-$iteration")
                        updated = persistentListRef.compareAndSet(current, newList)
                    }
                    
                    // Occasionally remove items
                    if (iteration % 5 == 0 && persistentListRef.get().size() > 10) {
                        var removed = false
                        while (!removed) {
                            val current = persistentListRef.get()
                            if (current.size() > 0) {
                                val itemToRemove = current.get(0)
                                val newList = current.remove(itemToRemove)
                                removed = persistentListRef.compareAndSet(current, newList)
                            } else {
                                removed = true
                            }
                        }
                    }
                    
                    delay(Random.nextLong(20, 80))
                }
            }
        }
        
        persistentJobs.forEach { it.join() }
        
        val finalList = persistentListRef.get()
        println("  Final persistent list size: ${finalList.size()}")
        println("  Sample items: ${finalList.toList().take(5)}")
        
        println("Immutable data patterns completed\n")
    }
}

/**
 * Confinement and single-threaded access patterns
 */
class ConfinementPatterns {
    
    fun demonstrateThreadConfinement() = runBlocking {
        println("=== Thread Confinement Patterns ===")
        
        println("1. Single-threaded context confinement:")
        
        // Create a single-threaded context
        val singleThreadContext = newSingleThreadContext("ConfineThread")
        
        try {
            var confinedState = 0
            val accessLog = mutableListOf<String>()
            
            val confinedJobs = (1..5).map { workerId ->
                launch {
                    repeat(10) { iteration ->
                        withContext(singleThreadContext) {
                            // All access happens on the same thread - no synchronization needed
                            val threadName = Thread.currentThread().name
                            confinedState++
                            accessLog.add("Worker-$workerId-$iteration on $threadName: $confinedState")
                            
                            if (iteration % 3 == 0) {
                                println("  Worker-$workerId on $threadName: $confinedState")
                            }
                            
                            delay(10) // Simulate work
                        }
                    }
                }
            }
            
            confinedJobs.forEach { it.join() }
            
            withContext(singleThreadContext) {
                println("  Final confined state: $confinedState")
                println("  All accesses on same thread: ${accessLog.all { it.contains("ConfineThread") }}")
            }
            
        } finally {
            singleThreadContext.close()
        }
        
        println()
        
        // Actor-like confinement pattern
        println("2. Actor-like confinement pattern:")
        
        class ConfinedCounter {
            private val context = newSingleThreadContext("CounterActor")
            private var value = 0
            
            suspend fun increment(): Int = withContext(context) {
                value++
            }
            
            suspend fun decrement(): Int = withContext(context) {
                value--
            }
            
            suspend fun get(): Int = withContext(context) {
                value
            }
            
            fun close() {
                context.close()
            }
        }
        
        val confinedCounter = ConfinedCounter()
        
        try {
            val actorJobs = (1..8).map { workerId ->
                launch {
                    repeat(12) {
                        val operation = Random.nextInt(3)
                        val result = when (operation) {
                            0 -> {
                                val newValue = confinedCounter.increment()
                                "Worker-$workerId: Incremented to $newValue"
                            }
                            1 -> {
                                val newValue = confinedCounter.decrement()
                                "Worker-$workerId: Decremented to $newValue"
                            }
                            else -> {
                                val currentValue = confinedCounter.get()
                                "Worker-$workerId: Current value is $currentValue"
                            }
                        }
                        
                        if (it % 4 == 0) {
                            println("  $result")
                        }
                        
                        delay(Random.nextLong(10, 50))
                    }
                }
            }
            
            actorJobs.forEach { it.join() }
            
            val finalValue = confinedCounter.get()
            println("  Final confined counter value: $finalValue")
            
        } finally {
            confinedCounter.close()
        }
        
        println()
        
        // Channel-based actor pattern
        println("3. Channel-based actor pattern:")
        
        sealed class CounterMessage {
            object Increment : CounterMessage()
            object Decrement : CounterMessage()
            data class Get(val response: kotlinx.coroutines.channels.Channel<Int>) : CounterMessage()
        }
        
        fun CoroutineScope.counterActor() = kotlinx.coroutines.channels.actor<CounterMessage> {
            var counter = 0
            for (msg in channel) {
                when (msg) {
                    is CounterMessage.Increment -> counter++
                    is CounterMessage.Decrement -> counter--
                    is CounterMessage.Get -> msg.response.send(counter)
                }
            }
        }
        
        val actor = counterActor()
        
        val actorTestJobs = (1..6).map { workerId ->
            launch {
                repeat(8) { iteration ->
                    when (Random.nextInt(3)) {
                        0 -> {
                            actor.send(CounterMessage.Increment)
                            if (iteration % 2 == 0) {
                                println("  Worker-$workerId: Sent increment")
                            }
                        }
                        1 -> {
                            actor.send(CounterMessage.Decrement)
                            if (iteration % 2 == 0) {
                                println("  Worker-$workerId: Sent decrement")
                            }
                        }
                        else -> {
                            val responseChannel = kotlinx.coroutines.channels.Channel<Int>()
                            actor.send(CounterMessage.Get(responseChannel))
                            val value = responseChannel.receive()
                            if (iteration % 3 == 0) {
                                println("  Worker-$workerId: Got value $value")
                            }
                        }
                    }
                    delay(Random.nextLong(20, 80))
                }
            }
        }
        
        actorTestJobs.forEach { it.join() }
        
        // Get final value
        val finalResponseChannel = kotlinx.coroutines.channels.Channel<Int>()
        actor.send(CounterMessage.Get(finalResponseChannel))
        val finalActorValue = finalResponseChannel.receive()
        
        actor.close()
        println("  Final actor counter value: $finalActorValue")
        
        println("Thread confinement patterns completed\n")
    }
}

/**
 * StateFlow for reactive state management
 */
class ReactiveStateManagement {
    
    fun demonstrateStateFlowForSharedState() = runBlocking {
        println("=== StateFlow for Shared State Management ===")
        
        println("1. Centralized state with StateFlow:")
        
        data class AppState(
            val userCount: Int = 0,
            val activeConnections: Int = 0,
            val lastUpdate: Long = System.currentTimeMillis()
        )
        
        val appState = MutableStateFlow(AppState())
        
        // State observers
        val stateObserver1 = launch {
            appState.collect { state ->
                println("  Observer-1: Users=${state.userCount}, Connections=${state.activeConnections}")
            }
        }
        
        val stateObserver2 = launch {
            appState
                .map { it.userCount }
                .distinctUntilChanged()
                .collect { userCount ->
                    println("  Observer-2: User count changed to $userCount")
                }
        }
        
        delay(100)
        
        // State updaters
        val stateUpdaters = (1..5).map { updaterId ->
            launch {
                repeat(8) { iteration ->
                    appState.update { current ->
                        current.copy(
                            userCount = current.userCount + Random.nextInt(-2, 4),
                            activeConnections = current.activeConnections + Random.nextInt(-1, 3),
                            lastUpdate = System.currentTimeMillis()
                        )
                    }
                    
                    if (iteration % 2 == 0) {
                        println("  Updater-$updaterId: Updated state (iteration $iteration)")
                    }
                    
                    delay(Random.nextLong(100, 300))
                }
            }
        }
        
        stateUpdaters.forEach { it.join() }
        delay(200)
        
        stateObserver1.cancel()
        stateObserver2.cancel()
        
        val finalState = appState.value
        println("  Final app state: $finalState")
        
        println()
        
        // StateFlow with validation and constraints
        println("2. StateFlow with validation:")
        
        class ValidatedCounter {
            private val _value = MutableStateFlow(0)
            val value: StateFlow<Int> = _value.asStateFlow()
            
            private val _errors = MutableSharedFlow<String>()
            val errors: SharedFlow<String> = _errors.asSharedFlow()
            
            suspend fun increment(amount: Int = 1): Boolean {
                return updateValue { it + amount }
            }
            
            suspend fun decrement(amount: Int = 1): Boolean {
                return updateValue { it - amount }
            }
            
            suspend fun set(newValue: Int): Boolean {
                return updateValue { newValue }
            }
            
            private suspend fun updateValue(updater: (Int) -> Int): Boolean {
                val newValue = updater(_value.value)
                return if (isValid(newValue)) {
                    _value.value = newValue
                    true
                } else {
                    _errors.emit("Invalid value: $newValue (must be between 0 and 1000)")
                    false
                }
            }
            
            private fun isValid(value: Int): Boolean = value in 0..1000
        }
        
        val validatedCounter = ValidatedCounter()
        
        // Error observer
        launch {
            validatedCounter.errors.collect { error ->
                println("  ❌ Validation error: $error")
            }
        }
        
        // Value observer
        launch {
            validatedCounter.value.collect { value ->
                println("  ✅ Valid counter value: $value")
            }
        }
        
        delay(100)
        
        // Test validation
        validatedCounter.increment(10)
        delay(50)
        validatedCounter.set(500)
        delay(50)
        validatedCounter.increment(600) // This should fail
        delay(50)
        validatedCounter.decrement(100)
        delay(50)
        validatedCounter.set(-50) // This should fail
        
        delay(200)
        
        println("  Final validated counter: ${validatedCounter.value.value}")
        
        println("StateFlow state management completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Race conditions and basic synchronization
        RaceConditionsAndSynchronization().demonstrateRaceCondition()
        RaceConditionsAndSynchronization().demonstrateAtomicOperations()
        RaceConditionsAndSynchronization().demonstrateAtomicArrays()
        
        // Coroutine-specific synchronization
        CoroutineSynchronization().demonstrateMutex()
        CoroutineSynchronization().demonstrateSemaphore()
        CoroutineSynchronization().demonstrateChannelSynchronization()
        
        // Thread-safe collections
        ThreadSafeCollections().demonstrateConcurrentCollections()
        ThreadSafeCollections().demonstrateImmutableDataPatterns()
        
        // Confinement patterns
        ConfinementPatterns().demonstrateThreadConfinement()
        
        // Reactive state management
        ReactiveStateManagement().demonstrateStateFlowForSharedState()
        
        println("=== Shared Mutable State Management Summary ===")
        println("✅ Race Condition Prevention:")
        println("   - Atomic operations for lock-free programming")
        println("   - Compare-and-swap for non-blocking updates")
        println("   - Atomic arrays and field updaters")
        println()
        println("✅ Coroutine Synchronization:")
        println("   - Mutex for coroutine-safe critical sections")
        println("   - Semaphore for resource limiting and rate control")
        println("   - Channel-based synchronization patterns")
        println()
        println("✅ Thread-Safe Collections:")
        println("   - ConcurrentHashMap for concurrent key-value access")
        println("   - Synchronized collections for thread safety")
        println("   - Custom thread-safe data structures")
        println()
        println("✅ Immutable Data Patterns:")
        println("   - Copy-on-write for safe state updates")
        println("   - Persistent data structures")
        println("   - Atomic references with immutable objects")
        println()
        println("✅ Confinement Strategies:")
        println("   - Single-threaded context confinement")
        println("   - Actor pattern for state encapsulation")
        println("   - Channel-based actors for message passing")
        println()
        println("✅ Reactive State Management:")
        println("   - StateFlow for centralized state")
        println("   - Validation and constraint enforcement")
        println("   - Observable state changes with flow operators")
        println()
        println("✅ Best Practices:")
        println("   - Prefer immutable data structures when possible")
        println("   - Use atomic operations for simple counters")
        println("   - Choose appropriate synchronization level")
        println("   - Monitor performance impact of synchronization")
        println("   - Test concurrent code thoroughly")
    }
}