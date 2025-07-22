# Kotlin Concurrency Troubleshooting Guide

## 🚨 Common Issues and Solutions

### 1. Memory Leaks

#### Problem: Coroutines Continue Running After Scope Ends
```kotlin
// ❌ Problematic code
class MyActivity : Activity() {
    fun startBackgroundTask() {
        GlobalScope.launch {
            while (true) {
                // This keeps running even after activity is destroyed!
                doWork()
                delay(1000)
            }
        }
    }
}
```

#### Solution: Use Proper Scoping
```kotlin
// ✅ Fixed code
class MyActivity : Activity(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job
    
    fun startBackgroundTask() {
        launch {
            while (isActive) { // Respects scope cancellation
                doWork()
                delay(1000)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Cancels all coroutines
    }
}
```

#### Detection: Use Memory Profiler
```kotlin
// Add logging to detect leaks
class CoroutineTracker {
    companion object {
        private val activeCoroutines = AtomicInteger(0)
        
        fun trackCoroutine(name: String) {
            val count = activeCoroutines.incrementAndGet()
            println("Coroutine started: $name, active: $count")
        }
        
        fun untrackCoroutine(name: String) {
            val count = activeCoroutines.decrementAndGet()
            println("Coroutine ended: $name, active: $count")
        }
    }
}
```

### 2. Deadlocks

#### Problem: Blocking on Main Thread
```kotlin
// ❌ This will deadlock
fun loadDataBlocking(): String {
    return runBlocking(Dispatchers.Main) {
        withContext(Dispatchers.Main) {
            // Trying to switch to Main from Main - deadlock!
            networkCall()
        }
    }
}
```

#### Solution: Use Appropriate Dispatchers
```kotlin
// ✅ Fixed code
suspend fun loadData(): String {
    return withContext(Dispatchers.IO) {
        networkCall() // Run on IO dispatcher
    }
}

// Call from appropriate context
fun updateUI() {
    lifecycleScope.launch(Dispatchers.Main) {
        val data = loadData() // Suspends, doesn't block
        displayData(data)
    }
}
```

#### Detection: Enable Coroutine Debugging
```kotlin
// Add to VM options: -Dkotlinx.coroutines.debug
// This will add coroutine names to stack traces

// In code, name your coroutines
launch(CoroutineName("DataLoader")) {
    loadData()
}
```

### 3. Race Conditions

#### Problem: Unsafe Shared State
```kotlin
// ❌ Race condition
class UnsafeCounter {
    private var count = 0
    
    fun increment() {
        GlobalScope.launch {
            repeat(1000) {
                count++ // Not thread-safe!
            }
        }
    }
}
```

#### Solution: Use Thread-Safe Operations
```kotlin
// ✅ Thread-safe approaches

// Option 1: Atomic operations
class AtomicCounter {
    private val count = AtomicInteger(0)
    
    suspend fun increment() {
        repeat(1000) {
            count.incrementAndGet()
        }
    }
}

// Option 2: Single dispatcher
class ChannelCounter {
    private var count = 0
    private val incrementChannel = Channel<Unit>()
    
    init {
        GlobalScope.launch(Dispatchers.Default) {
            for (unit in incrementChannel) {
                count++ // All operations on single thread
            }
        }
    }
    
    suspend fun increment() {
        repeat(1000) {
            incrementChannel.send(Unit)
        }
    }
}

// Option 3: Mutex for critical sections
class MutexCounter {
    private var count = 0
    private val mutex = Mutex()
    
    suspend fun increment() {
        repeat(1000) {
            mutex.withLock {
                count++
            }
        }
    }
}
```

### 4. Exception Handling Issues

#### Problem: Exceptions Disappearing
```kotlin
// ❌ Exception gets lost
fun problematicExceptionHandling() {
    GlobalScope.launch {
        launch {
            throw RuntimeException("This might get lost!")
        }
        
        launch {
            // This continues running even if sibling fails
            doOtherWork()
        }
    }
}
```

#### Solution: Proper Exception Strategy
```kotlin
// ✅ Comprehensive exception handling
class ServiceManager : CoroutineScope {
    private val supervisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logger.error("Unhandled coroutine exception", exception)
        // Report to crash analytics
        crashReporter.recordException(exception)
    }
    
    override val coroutineContext = 
        Dispatchers.Default + supervisorJob + exceptionHandler
    
    fun startServices() {
        // Use SupervisorJob so one failure doesn't kill others
        launch(CoroutineName("Service1")) {
            try {
                service1.start()
            } catch (e: Exception) {
                logger.error("Service1 failed", e)
                // Handle gracefully
            }
        }
        
        launch(CoroutineName("Service2")) {
            try {
                service2.start()
            } catch (e: Exception) {
                logger.error("Service2 failed", e)
            }
        }
    }
}
```

### 5. Flow Backpressure Issues

#### Problem: Fast Producer, Slow Consumer
```kotlin
// ❌ This can cause memory issues
fun fastProducerSlowConsumer() {
    flow {
        repeat(1000000) { i ->
            emit(i) // Producing very fast
        }
    }.collect { value ->
        delay(100) // Very slow consumer
        process(value)
    }
}
```

#### Solution: Backpressure Strategies
```kotlin
// ✅ Handle backpressure appropriately

// Strategy 1: Buffer with limit
flow {
    repeat(1000000) { i -> emit(i) }
}.buffer(capacity = 1000) // Limited buffer
.collect { value ->
    delay(100)
    process(value)
}

// Strategy 2: Drop old values
flow {
    repeat(1000000) { i -> emit(i) }
}.conflate() // Keep only latest
.collect { value ->
    delay(100)
    process(value)
}

// Strategy 3: Sample periodically
flow {
    repeat(1000000) { i -> emit(i) }
}.sample(1000) // Sample every 1 second
.collect { value ->
    process(value)
}

// Strategy 4: Batch processing
flow {
    repeat(1000000) { i -> emit(i) }
}.chunked(100) // Process in batches
.collect { batch ->
    processBatch(batch)
}
```

## 🔧 Debugging Techniques

### 1. Coroutine Dump Analysis
```kotlin
// Enable coroutine debugging
System.setProperty("kotlinx.coroutines.debug", "on")

// Get coroutine dump
fun printCoroutineDump() {
    val dump = DebugProbes.dumpCoroutines()
    dump.forEach { info ->
        println("Coroutine: ${info.context}")
        println("State: ${info.state}")
        println("Stack trace:")
        info.lastObservedStackTrace().forEach { frame ->
            println("  $frame")
        }
    }
}
```

### 2. Performance Profiling
```kotlin
// Add timing to coroutines
suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = block()
    val time = System.currentTimeMillis() - start
    return result to time
}

// Usage
val (result, time) = measureTime {
    performExpensiveOperation()
}
println("Operation took ${time}ms")
```

### 3. Flow Debugging
```kotlin
// Debug flow emissions
fun debugFlow(): Flow<String> = flow {
    emit("value1")
    emit("value2") 
    emit("value3")
}.onEach { value ->
    println("Emitting: $value on ${Thread.currentThread().name}")
}.onStart {
    println("Flow started")
}.onCompletion { exception ->
    if (exception != null) {
        println("Flow completed with exception: $exception")
    } else {
        println("Flow completed successfully")
    }
}
```

## 🔍 Testing Problematic Scenarios

### 1. Test Cancellation Behavior
```kotlin
@Test
fun testCancellationBehavior() = runTest {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("Working: $i")
                delay(100)
            }
        } catch (e: CancellationException) {
            println("Cancelled gracefully")
            throw e // Must re-throw
        } finally {
            println("Cleanup performed")
        }
    }
    
    delay(250) // Let it run a bit
    job.cancel() // Cancel the job
    job.join() // Wait for cleanup
    
    assertTrue(job.isCancelled)
}
```

### 2. Test Exception Propagation
```kotlin
@Test
fun testExceptionPropagation() = runTest {
    val parentJob = SupervisorJob()
    val scope = CoroutineScope(parentJob)
    
    val child1 = scope.launch {
        delay(100)
        throw RuntimeException("Child1 failed")
    }
    
    val child2 = scope.launch {
        delay(200)
        println("Child2 completed")
    }
    
    // Child1 failure shouldn't affect child2
    assertThrows<RuntimeException> {
        child1.join()
    }
    
    child2.join() // Should complete normally
    assertFalse(child2.isCancelled)
}
```

### 3. Test Resource Cleanup
```kotlin
@Test
fun testResourceCleanup() = runTest {
    val resources = mutableListOf<String>()
    
    val job = launch {
        try {
            resources.add("resource1")
            delay(1000)
            resources.add("resource2")
        } finally {
            // Cleanup
            resources.clear()
        }
    }
    
    delay(100) // Let it acquire resource1
    job.cancel()
    job.join()
    
    assertTrue(resources.isEmpty(), "Resources should be cleaned up")
}
```

## ⚡ Performance Issues

### 1. Too Many Context Switches
```kotlin
// ❌ Inefficient context switching
suspend fun inefficientContextSwitching() {
    withContext(Dispatchers.IO) { operation1() }
    withContext(Dispatchers.Default) { operation2() }
    withContext(Dispatchers.IO) { operation3() }
    withContext(Dispatchers.Default) { operation4() }
}

// ✅ Minimize context switches
suspend fun efficientContextSwitching() {
    val ioResults = withContext(Dispatchers.IO) {
        listOf(operation1(), operation3())
    }
    val cpuResults = withContext(Dispatchers.Default) {
        listOf(operation2(), operation4())
    }
}
```

### 2. Excessive Coroutine Creation
```kotlin
// ❌ Creating too many coroutines
fun processItems(items: List<Item>) {
    items.forEach { item ->
        GlobalScope.launch { // Creates one coroutine per item!
            process(item)
        }
    }
}

// ✅ Batch processing
suspend fun processItemsEfficiently(items: List<Item>) {
    items.chunked(10).forEach { batch ->
        coroutineScope {
            batch.map { item ->
                async { process(item) }
            }.awaitAll()
        }
    }
}
```

### 3. Memory Usage with Flows
```kotlin
// ❌ Memory intensive
fun memoryIntensiveFlow(): Flow<LargeObject> = flow {
    val largeList = mutableListOf<LargeObject>()
    repeat(1000000) { i ->
        largeList.add(createLargeObject(i)) // Accumulating in memory
    }
    largeList.forEach { emit(it) }
}

// ✅ Memory efficient
fun memoryEfficientFlow(): Flow<LargeObject> = flow {
    repeat(1000000) { i ->
        emit(createLargeObject(i)) // One at a time
    }
}
```

## 🛠️ Diagnostic Tools

### 1. Custom Coroutine Profiler
```kotlin
class CoroutineProfiler {
    private val metrics = ConcurrentHashMap<String, ProfileData>()
    
    data class ProfileData(
        var count: AtomicInteger = AtomicInteger(0),
        var totalTime: AtomicLong = AtomicLong(0),
        var maxTime: AtomicLong = AtomicLong(0)
    )
    
    suspend fun <T> profile(name: String, block: suspend () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val duration = System.nanoTime() - start
            val data = metrics.computeIfAbsent(name) { ProfileData() }
            data.count.incrementAndGet()
            data.totalTime.addAndGet(duration)
            data.maxTime.updateAndGet { max(it, duration) }
        }
    }
    
    fun printReport() {
        metrics.forEach { (name, data) ->
            val count = data.count.get()
            val avgTime = data.totalTime.get() / count
            val maxTime = data.maxTime.get()
            println("$name: count=$count, avg=${avgTime/1_000_000}ms, max=${maxTime/1_000_000}ms")
        }
    }
}
```

### 2. Flow Inspector
```kotlin
fun <T> Flow<T>.inspect(name: String): Flow<T> = flow {
    var count = 0
    var errors = 0
    val startTime = System.currentTimeMillis()
    
    try {
        collect { value ->
            count++
            println("[$name] Emitted #$count: $value")
            emit(value)
        }
    } catch (e: Exception) {
        errors++
        println("[$name] Error after $count emissions: $e")
        throw e
    } finally {
        val duration = System.currentTimeMillis() - startTime
        println("[$name] Summary: $count emissions, $errors errors, ${duration}ms total")
    }
}
```

### 3. Deadlock Detector
```kotlin
class DeadlockDetector {
    private val threadStates = ConcurrentHashMap<Long, ThreadState>()
    
    data class ThreadState(
        val name: String,
        val state: Thread.State,
        val stackTrace: Array<StackTraceElement>,
        val timestamp: Long
    )
    
    fun startMonitoring() {
        GlobalScope.launch {
            while (isActive) {
                checkForDeadlocks()
                delay(5000) // Check every 5 seconds
            }
        }
    }
    
    private fun checkForDeadlocks() {
        val threads = Thread.getAllStackTraces()
        threads.forEach { (thread, stackTrace) ->
            if (thread.state == Thread.State.BLOCKED || 
                thread.state == Thread.State.WAITING) {
                
                val state = ThreadState(
                    thread.name,
                    thread.state,
                    stackTrace,
                    System.currentTimeMillis()
                )
                
                val previous = threadStates.put(thread.id, state)
                if (previous != null && 
                    System.currentTimeMillis() - previous.timestamp > 30000) {
                    println("Potential deadlock detected in thread: ${thread.name}")
                    stackTrace.forEach { println("  $it") }
                }
            } else {
                threadStates.remove(thread.id)
            }
        }
    }
}
```

## 📋 Troubleshooting Checklist

### Before Deploying
- [ ] All coroutines have proper cancellation handling
- [ ] No blocking calls in suspending functions  
- [ ] Exception handling strategy is comprehensive
- [ ] Resource cleanup is guaranteed (use `finally` blocks)
- [ ] No GlobalScope usage (except for application-wide services)
- [ ] Flow backpressure is handled appropriately
- [ ] Performance has been profiled with realistic data volumes
- [ ] Memory usage has been tested under load
- [ ] All tests include cancellation scenarios

### When Issues Occur
- [ ] Enable coroutine debugging (`kotlinx.coroutines.debug=on`)
- [ ] Check thread dump for blocking operations
- [ ] Monitor memory usage for leaks
- [ ] Profile coroutine creation/destruction rates
- [ ] Verify exception handling covers all scenarios
- [ ] Test with realistic concurrent user loads
- [ ] Validate flow emission rates vs consumption rates

This guide covers the most common issues encountered when working with Kotlin coroutines and provides practical solutions that can be immediately applied to resolve problems.