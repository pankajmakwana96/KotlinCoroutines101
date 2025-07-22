/**
 * # Coroutine Performance Optimization
 * 
 * ## Problem Description
 * While coroutines are lightweight, production applications need optimization
 * to handle high loads efficiently. This includes minimizing overhead,
 * optimizing context switching, managing memory efficiently, and choosing
 * the right patterns for maximum throughput and minimal latency.
 * 
 * ## Solution Approach
 * Performance optimization strategies include:
 * - Minimizing context switching overhead
 * - Efficient dispatcher selection and configuration
 * - Memory optimization and GC pressure reduction
 * - Batching and buffering strategies
 * - CPU cache-friendly patterns
 * 
 * ## Key Learning Points
 * - Context switching has measurable overhead
 * - Dispatcher choice significantly impacts performance
 * - Memory allocation patterns affect GC performance
 * - Batching operations improves throughput
 * - CPU cache locality matters for coroutines too
 * 
 * ## Performance Considerations
 * - Context switch overhead: ~10-50ns per switch
 * - Coroutine creation: ~100-500ns per coroutine
 * - Memory per coroutine: ~200-1000 bytes
 * - GC pressure from allocations
 * - CPU cache misses from poor locality
 * 
 * ## Common Pitfalls
 * - Excessive context switching
 * - Wrong dispatcher for workload type
 * - Too many small coroutines
 * - Memory leaks and excessive allocations
 * - Not measuring performance impact
 * 
 * ## Real-World Applications
 * - High-throughput web servers
 * - Real-time data processing
 * - Gaming and simulation systems
 * - Financial trading systems
 * - IoT data collection
 * 
 * ## Related Concepts
 * - Dispatchers.kt - Dispatcher selection
 * - CoroutineDebugging.kt - Performance monitoring
 * - CustomScopes.kt - Efficient scope management
 */

package coroutines.advanced

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Context switching optimization
 * 
 * Context Switching Overhead:
 * 
 * Excessive Switching:
 * Thread A ──switch──> Thread B ──switch──> Thread C ──switch──> Thread A
 *    10ns      50ns       10ns      50ns       10ns      50ns
 *    Total: 180ns overhead for simple operation
 * 
 * Optimized:
 * Thread A ──────── batch operations ────────> Result
 *    10ns           actual work              10ns
 *    Total: 20ns overhead
 */
class ContextSwitchingOptimization {
    
    fun demonstrateContextSwitchingOverhead() = runBlocking {
        println("=== Context Switching Overhead ===")
        
        val operations = 10000
        
        // Inefficient: Excessive context switching
        val inefficientTime = measureTimeMillis {
            repeat(operations) {
                // Each operation switches context
                withContext(Dispatchers.Default) {
                    it * 2
                }
            }
        }
        
        println("Inefficient (frequent switching): ${inefficientTime}ms")
        
        // Efficient: Batch operations in single context
        val efficientTime = measureTimeMillis {
            withContext(Dispatchers.Default) {
                repeat(operations) {
                    it * 2
                }
            }
        }
        
        println("Efficient (batched operations): ${efficientTime}ms")
        
        val improvement = inefficientTime.toDouble() / efficientTime
        println("Performance improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateBatchedContextSwitching() = runBlocking {
        println("=== Batched Context Switching ===")
        
        data class WorkItem(val id: Int, val data: String)
        
        val workItems = (1..1000).map { WorkItem(it, "data-$it") }
        
        // Inefficient: Process each item with context switch
        val inefficientTime = measureTimeMillis {
            workItems.map { item ->
                async {
                    withContext(Dispatchers.Default) {
                        // Simulate CPU work
                        item.data.hashCode()
                    }
                }
            }.awaitAll()
        }
        
        println("Individual processing: ${inefficientTime}ms")
        
        // Efficient: Batch process items
        val efficientTime = measureTimeMillis {
            workItems.chunked(100).map { batch ->
                async(Dispatchers.Default) {
                    batch.map { item ->
                        // Process entire batch in same context
                        item.data.hashCode()
                    }
                }
            }.awaitAll()
        }
        
        println("Batched processing: ${efficientTime}ms")
        
        val improvement = inefficientTime.toDouble() / efficientTime
        println("Batching improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateStickToDispatcher() = runBlocking {
        println("=== Stick to Dispatcher Optimization ===")
        
        suspend fun cpuIntensiveWork(input: Int): Int {
            // Simulate CPU work
            var result = input
            repeat(1000) {
                result = result.hashCode()
            }
            return result
        }
        
        suspend fun ioWork(input: Int): Int {
            delay(1) // Simulate I/O
            return input * 2
        }
        
        val workCount = 100
        
        // Inefficient: Constant dispatcher switching
        val switchingTime = measureTimeMillis {
            repeat(workCount) { i ->
                // Switch to Default for CPU work
                val cpuResult = withContext(Dispatchers.Default) {
                    cpuIntensiveWork(i)
                }
                
                // Switch to IO for I/O work
                val ioResult = withContext(Dispatchers.IO) {
                    ioWork(cpuResult)
                }
                
                // Switch back to Default
                withContext(Dispatchers.Default) {
                    ioResult + 1
                }
            }
        }
        
        println("Constant switching: ${switchingTime}ms")
        
        // Efficient: Group by dispatcher type
        val groupedTime = measureTimeMillis {
            // Do all CPU work in one context
            val cpuResults = withContext(Dispatchers.Default) {
                (0 until workCount).map { cpuIntensiveWork(it) }
            }
            
            // Do all I/O work in another context
            val ioResults = withContext(Dispatchers.IO) {
                cpuResults.map { ioWork(it) }
            }
            
            // Final processing
            withContext(Dispatchers.Default) {
                ioResults.map { it + 1 }
            }
        }
        
        println("Grouped by dispatcher: ${groupedTime}ms")
        
        val improvement = switchingTime.toDouble() / groupedTime
        println("Grouping improvement: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * Memory optimization strategies
 */
class MemoryOptimization {
    
    fun demonstrateObjectPooling() = runBlocking {
        println("=== Object Pooling Optimization ===")
        
        // Heavy object that's expensive to create
        data class ExpensiveObject(
            val data: ByteArray = ByteArray(1024),
            var value: Int = 0
        ) {
            fun reset() {
                value = 0
                data.fill(0)
            }
        }
        
        class ObjectPool<T>(
            private val factory: () -> T,
            private val reset: (T) -> Unit,
            maxSize: Int = 10
        ) {
            private val available = ArrayDeque<T>(maxSize)
            
            fun borrow(): T {
                return available.removeFirstOrNull() ?: factory()
            }
            
            fun return_(obj: T) {
                reset(obj)
                if (available.size < 10) {
                    available.addLast(obj)
                }
            }
        }
        
        val pool = ObjectPool<ExpensiveObject>(
            factory = { ExpensiveObject() },
            reset = { it.reset() }
        )
        
        val operations = 1000
        
        // Without pooling: Create new objects each time
        val withoutPoolingTime = measureTimeMillis {
            repeat(operations) {
                val obj = ExpensiveObject()
                obj.value = it
                // Object becomes garbage
            }
        }
        
        println("Without pooling: ${withoutPoolingTime}ms")
        
        // With pooling: Reuse objects
        val withPoolingTime = measureTimeMillis {
            repeat(operations) {
                val obj = pool.borrow()
                obj.value = it
                pool.return_(obj)
            }
        }
        
        println("With pooling: ${withPoolingTime}ms")
        
        val improvement = withoutPoolingTime.toDouble() / withPoolingTime
        println("Pooling improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateMemoryEfficientCoroutines() = runBlocking {
        println("=== Memory-Efficient Coroutines ===")
        
        val iterations = 10000
        
        // Memory inefficient: Many small coroutines
        val inefficientMemory = measureMemoryUsage {
            val jobs = (1..iterations).map { i ->
                async {
                    delay(1)
                    i * 2
                }
            }
            jobs.awaitAll()
        }
        
        println("Many small coroutines memory: ${inefficientMemory}MB")
        
        // Memory efficient: Batched processing
        val efficientMemory = measureMemoryUsage {
            val batchSize = 100
            val batches = (1..iterations).chunked(batchSize)
            
            batches.map { batch ->
                async {
                    batch.map { i ->
                        delay(1)
                        i * 2
                    }
                }
            }.awaitAll().flatten()
        }
        
        println("Batched coroutines memory: ${efficientMemory}MB")
        
        val memoryReduction = (inefficientMemory - efficientMemory) / inefficientMemory * 100
        println("Memory reduction: ${String.format("%.1f", memoryReduction)}%\n")
    }
    
    private suspend fun measureMemoryUsage(operation: suspend () -> Unit): Long {
        val runtime = Runtime.getRuntime()
        System.gc()
        delay(100)
        
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        operation()
        System.gc()
        delay(100)
        
        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        return (afterMemory - beforeMemory) / (1024 * 1024) // Convert to MB
    }
    
    fun demonstrateStringOptimization() = runBlocking {
        println("=== String Building Optimization ===")
        
        val operations = 1000
        
        // Inefficient: String concatenation
        val inefficientTime = measureTimeMillis {
            repeat(operations) { i ->
                async {
                    var result = ""
                    repeat(100) { j ->
                        result += "item-$i-$j," // Creates new string each time
                    }
                    result
                }
            }.awaitAll()
        }
        
        println("String concatenation: ${inefficientTime}ms")
        
        // Efficient: StringBuilder
        val efficientTime = measureTimeMillis {
            repeat(operations) { i ->
                async {
                    val builder = StringBuilder(2000) // Pre-size
                    repeat(100) { j ->
                        builder.append("item-$i-$j,")
                    }
                    builder.toString()
                }
            }.awaitAll()
        }
        
        println("StringBuilder: ${efficientTime}ms")
        
        val improvement = inefficientTime.toDouble() / efficientTime
        println("String building improvement: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * Batching and buffering optimization
 */
class BatchingOptimization {
    
    fun demonstrateBatchedProcessing() = runBlocking {
        println("=== Batched Processing ===")
        
        data class DatabaseRecord(val id: Int, val data: String)
        
        suspend fun saveToDatabase(records: List<DatabaseRecord>) {
            // Simulate database batch insert
            delay(50 + records.size * 2) // Base overhead + per-record cost
        }
        
        val records = (1..1000).map { DatabaseRecord(it, "data-$it") }
        
        // Inefficient: One by one
        val oneByOneTime = measureTimeMillis {
            records.forEach { record ->
                saveToDatabase(listOf(record))
            }
        }
        
        println("One-by-one processing: ${oneByOneTime}ms")
        
        // Efficient: Batched
        val batchedTime = measureTimeMillis {
            records.chunked(50).forEach { batch ->
                saveToDatabase(batch)
            }
        }
        
        println("Batched processing: ${batchedTime}ms")
        
        val improvement = oneByOneTime.toDouble() / batchedTime
        println("Batching improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateBufferedChannel() = runBlocking {
        println("=== Buffered Channel Optimization ===")
        
        // Unbuffered channel (rendezvous)
        val unbufferedTime = measureTimeMillis {
            val channel = Channel<Int>()
            
            val producer = launch {
                repeat(1000) { i ->
                    channel.send(i) // Blocks until consumed
                }
                channel.close()
            }
            
            val consumer = launch {
                for (value in channel) {
                    delay(1) // Simulate processing time
                }
            }
            
            producer.join()
            consumer.join()
        }
        
        println("Unbuffered channel: ${unbufferedTime}ms")
        
        // Buffered channel
        val bufferedTime = measureTimeMillis {
            val channel = Channel<Int>(capacity = 100)
            
            val producer = launch {
                repeat(1000) { i ->
                    channel.send(i) // Can send ahead
                }
                channel.close()
            }
            
            val consumer = launch {
                for (value in channel) {
                    delay(1) // Same processing time
                }
            }
            
            producer.join()
            consumer.join()
        }
        
        println("Buffered channel: ${bufferedTime}ms")
        
        val improvement = unbufferedTime.toDouble() / bufferedTime
        println("Buffering improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun demonstrateProducerOptimization() = runBlocking {
        println("=== Producer Pattern Optimization ===")
        
        // Regular channel producer
        val regularTime = measureTimeMillis {
            val channel = Channel<Int>(capacity = 10)
            
            launch {
                repeat(1000) { i ->
                    channel.send(i)
                    if (Random.nextBoolean()) {
                        delay(1) // Irregular production
                    }
                }
                channel.close()
            }
            
            launch {
                for (value in channel) {
                    delay(1) // Processing
                }
            }.join()
        }
        
        println("Regular producer: ${regularTime}ms")
        
        // Optimized producer with buffering
        val optimizedTime = measureTimeMillis {
            val producer = produce(capacity = 100) {
                repeat(1000) { i ->
                    send(i)
                    if (Random.nextBoolean()) {
                        delay(1) // Same irregular production
                    }
                }
            }
            
            for (value in producer) {
                delay(1) // Same processing
            }
        }
        
        println("Optimized producer: ${optimizedTime}ms")
        
        val improvement = regularTime.toDouble() / optimizedTime
        println("Producer optimization: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * CPU cache optimization
 */
class CPUCacheOptimization {
    
    fun demonstrateDataLocalityOptimization() = runBlocking {
        println("=== Data Locality Optimization ===")
        
        data class DataPoint(
            val id: Int,
            val value: Double,
            val timestamp: Long,
            val metadata: String
        )
        
        val dataPoints = (1..100000).map { 
            DataPoint(it, Random.nextDouble(), System.currentTimeMillis(), "meta-$it")
        }
        
        // Poor locality: Random access pattern
        val randomAccessTime = measureTimeMillis {
            val indices = dataPoints.indices.shuffled()
            indices.chunked(1000).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { i ->
                        dataPoints[i].value * 2
                    }.sum()
                }
            }.awaitAll().sum()
        }
        
        println("Random access pattern: ${randomAccessTime}ms")
        
        // Good locality: Sequential access pattern
        val sequentialAccessTime = measureTimeMillis {
            dataPoints.chunked(1000).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { dataPoint ->
                        dataPoint.value * 2
                    }.sum()
                }
            }.awaitAll().sum()
        }
        
        println("Sequential access pattern: ${sequentialAccessTime}ms")
        
        val improvement = randomAccessTime.toDouble() / sequentialAccessTime
        println("Locality improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateStructOfArrays() = runBlocking {
        println("=== Struct of Arrays Optimization ===")
        
        val count = 1000000
        
        // Array of Structs (AoS) - poor cache locality
        data class Point3D(val x: Double, val y: Double, val z: Double)
        val pointsAoS = Array(count) { 
            Point3D(Random.nextDouble(), Random.nextDouble(), Random.nextDouble())
        }
        
        // Struct of Arrays (SoA) - better cache locality
        class Points3DSoA(size: Int) {
            val x = DoubleArray(size) { Random.nextDouble() }
            val y = DoubleArray(size) { Random.nextDouble() }
            val z = DoubleArray(size) { Random.nextDouble() }
        }
        val pointsSoA = Points3DSoA(count)
        
        // Process only X coordinates (common pattern)
        
        // AoS: Poor cache usage (loads unnecessary y, z data)
        val aosTime = measureTimeMillis {
            (0 until count step 10000).map { start ->
                async(Dispatchers.Default) {
                    var sum = 0.0
                    for (i in start until minOf(start + 10000, count)) {
                        sum += pointsAoS[i].x // Loads entire Point3D
                    }
                    sum
                }
            }.awaitAll().sum()
        }
        
        println("Array of Structs: ${aosTime}ms")
        
        // SoA: Good cache usage (only loads x data)
        val soaTime = measureTimeMillis {
            (0 until count step 10000).map { start ->
                async(Dispatchers.Default) {
                    var sum = 0.0
                    for (i in start until minOf(start + 10000, count)) {
                        sum += pointsSoA.x[i] // Only loads x array
                    }
                    sum
                }
            }.awaitAll().sum()
        }
        
        println("Struct of Arrays: ${soaTime}ms")
        
        val improvement = aosTime.toDouble() / soaTime
        println("SoA improvement: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * Dispatcher optimization
 */
class DispatcherOptimization {
    
    fun demonstrateCustomDispatcherSizing() = runBlocking {
        println("=== Custom Dispatcher Sizing ===")
        
        suspend fun cpuBoundTask(): Int {
            var result = 0
            repeat(100000) {
                result += it.hashCode()
            }
            return result
        }
        
        val taskCount = 100
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        // Default dispatcher (optimal for CPU-bound)
        val defaultTime = measureTimeMillis {
            (1..taskCount).map {
                async(Dispatchers.Default) {
                    cpuBoundTask()
                }
            }.awaitAll()
        }
        
        println("Default dispatcher ($cpuCores threads): ${defaultTime}ms")
        
        // Over-sized dispatcher (too many threads)
        val oversizedDispatcher = Dispatchers.IO // Has many threads
        val oversizedTime = measureTimeMillis {
            (1..taskCount).map {
                async(oversizedDispatcher) {
                    cpuBoundTask()
                }
            }.awaitAll()
        }
        
        println("Oversized dispatcher (64+ threads): ${oversizedTime}ms")
        
        // Under-sized dispatcher
        val undersizedDispatcher = newFixedThreadPoolContext(1, "Undersized")
        val undersizedTime = measureTimeMillis {
            (1..taskCount).map {
                async(undersizedDispatcher) {
                    cpuBoundTask()
                }
            }.awaitAll()
        }
        undersizedDispatcher.close()
        
        println("Undersized dispatcher (1 thread): ${undersizedTime}ms")
        
        println("Default is optimal for CPU-bound tasks\n")
    }
    
    fun demonstrateDispatcherSpecialization() = runBlocking {
        println("=== Dispatcher Specialization ===")
        
        suspend fun ioTask(): String {
            delay(10) // Simulate I/O
            return "I/O result"
        }
        
        suspend fun cpuTask(): Int {
            var result = 0
            repeat(50000) {
                result += it
            }
            return result
        }
        
        val taskCount = 50
        
        // Mixed workload on single dispatcher (suboptimal)
        val mixedTime = measureTimeMillis {
            withContext(Dispatchers.Default) {
                (1..taskCount).map { i ->
                    async {
                        if (i % 2 == 0) {
                            ioTask()
                            "I/O"
                        } else {
                            cpuTask().toString()
                        }
                    }
                }.awaitAll()
            }
        }
        
        println("Mixed workload on Default: ${mixedTime}ms")
        
        // Specialized dispatchers (optimal)
        val specializedTime = measureTimeMillis {
            val ioTasks = (2..taskCount step 2).map {
                async(Dispatchers.IO) { ioTask() }
            }
            
            val cpuTasks = (1..taskCount step 2).map {
                async(Dispatchers.Default) { cpuTask() }
            }
            
            ioTasks.awaitAll()
            cpuTasks.awaitAll()
        }
        
        println("Specialized dispatchers: ${specializedTime}ms")
        
        val improvement = mixedTime.toDouble() / specializedTime
        println("Specialization improvement: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * Throughput optimization patterns
 */
class ThroughputOptimization {
    
    fun demonstratePipelineOptimization() = runBlocking {
        println("=== Pipeline Optimization ===")
        
        data class WorkItem(val id: Int, var data: String = "", var processed: Boolean = false)
        
        suspend fun stage1(item: WorkItem): WorkItem {
            delay(5)
            item.data = "stage1-${item.id}"
            return item
        }
        
        suspend fun stage2(item: WorkItem): WorkItem {
            delay(3)
            item.data += "-stage2"
            return item
        }
        
        suspend fun stage3(item: WorkItem): WorkItem {
            delay(4)
            item.processed = true
            return item
        }
        
        val items = (1..100).map { WorkItem(it) }
        
        // Sequential pipeline (slow)
        val sequentialTime = measureTimeMillis {
            items.map { item ->
                val after1 = stage1(item)
                val after2 = stage2(after1)
                stage3(after2)
            }
        }
        
        println("Sequential pipeline: ${sequentialTime}ms")
        
        // Parallel pipeline stages (fast)
        val parallelTime = measureTimeMillis {
            val stage1Channel = Channel<WorkItem>(capacity = 10)
            val stage2Channel = Channel<WorkItem>(capacity = 10)
            val stage3Channel = Channel<WorkItem>(capacity = 10)
            
            // Stage 1 processor
            launch {
                for (item in stage1Channel) {
                    val processed = stage1(item)
                    stage2Channel.send(processed)
                }
                stage2Channel.close()
            }
            
            // Stage 2 processor
            launch {
                for (item in stage2Channel) {
                    val processed = stage2(item)
                    stage3Channel.send(processed)
                }
                stage3Channel.close()
            }
            
            // Stage 3 processor
            val results = mutableListOf<WorkItem>()
            launch {
                for (item in stage3Channel) {
                    val processed = stage3(item)
                    results.add(processed)
                }
            }
            
            // Feed items into pipeline
            launch {
                items.forEach { item ->
                    stage1Channel.send(item)
                }
                stage1Channel.close()
            }
            
            // Wait for all stages to complete
            delay(2000) // Simplified waiting
        }
        
        println("Parallel pipeline: ${parallelTime}ms")
        
        val improvement = sequentialTime.toDouble() / parallelTime
        println("Pipeline improvement: ${String.format("%.1f", improvement)}x\n")
    }
    
    fun demonstrateLoadBalancing() = runBlocking {
        println("=== Load Balancing Optimization ===")
        
        suspend fun variableWorkload(workId: Int): String {
            // Simulate variable processing time
            val processingTime = when (workId % 4) {
                0 -> 50L   // Fast task
                1 -> 100L  // Medium task
                2 -> 200L  // Slow task
                else -> 300L // Very slow task
            }
            delay(processingTime)
            return "Result-$workId"
        }
        
        val workItems = (1..20).toList()
        
        // Unbalanced: Sequential distribution
        val unbalancedTime = measureTimeMillis {
            val workers = 4
            val workPerWorker = workItems.chunked(workItems.size / workers)
            
            workPerWorker.map { batch ->
                async {
                    batch.map { workId -> variableWorkload(workId) }
                }
            }.awaitAll()
        }
        
        println("Unbalanced distribution: ${unbalancedTime}ms")
        
        // Balanced: Dynamic work stealing
        val balancedTime = measureTimeMillis {
            val workQueue = Channel<Int>(capacity = Channel.UNLIMITED)
            val results = Channel<String>(capacity = Channel.UNLIMITED)
            
            // Add all work to queue
            workItems.forEach { workQueue.trySend(it) }
            workQueue.close()
            
            // Start workers that steal work dynamically
            val workers = (1..4).map { workerId ->
                async {
                    var processed = 0
                    for (workId in workQueue) {
                        val result = variableWorkload(workId)
                        results.send(result)
                        processed++
                    }
                    println("  Worker $workerId processed $processed items")
                }
            }
            
            workers.awaitAll()
            results.close()
            
            // Collect results
            val allResults = mutableListOf<String>()
            for (result in results) {
                allResults.add(result)
            }
        }
        
        println("Balanced distribution: ${balancedTime}ms")
        
        val improvement = unbalancedTime.toDouble() / balancedTime
        println("Load balancing improvement: ${String.format("%.1f", improvement)}x\n")
    }
}

/**
 * Performance measurement and profiling
 */
class PerformanceMeasurement {
    
    class PerformanceProfiler {
        private val measurements = mutableMapOf<String, MutableList<Long>>()
        
        suspend fun <T> profile(name: String, operation: suspend () -> T): T {
            val startTime = System.nanoTime()
            return try {
                operation()
            } finally {
                val duration = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
                measurements.computeIfAbsent(name) { mutableListOf() }.add(duration)
            }
        }
        
        fun getStats(name: String): String {
            val times = measurements[name] ?: return "No measurements for $name"
            if (times.isEmpty()) return "No measurements for $name"
            
            val sorted = times.sorted()
            val avg = times.average()
            val min = times.minOrNull() ?: 0
            val max = times.maxOrNull() ?: 0
            val p50 = sorted[sorted.size * 50 / 100]
            val p95 = sorted[sorted.size * 95 / 100]
            val p99 = sorted[sorted.size * 99 / 100]
            
            return "$name: avg=${String.format("%.1f", avg)}ms, min=${min}ms, max=${max}ms, " +
                   "p50=${p50}ms, p95=${p95}ms, p99=${p99}ms (${times.size} samples)"
        }
        
        fun getAllStats(): String {
            return measurements.keys.sorted().joinToString("\n") { getStats(it) }
        }
    }
    
    fun demonstratePerformanceProfiling() = runBlocking {
        println("=== Performance Profiling ===")
        
        val profiler = PerformanceProfiler()
        
        suspend fun fastOperation(): String {
            delay(Random.nextLong(10, 50))
            return "fast"
        }
        
        suspend fun slowOperation(): String {
            delay(Random.nextLong(100, 300))
            return "slow"
        }
        
        suspend fun variableOperation(): String {
            delay(Random.nextLong(20, 200))
            return "variable"
        }
        
        // Run operations multiple times to gather statistics
        repeat(50) {
            launch {
                profiler.profile("FastOp") { fastOperation() }
            }
        }
        
        repeat(30) {
            launch {
                profiler.profile("SlowOp") { slowOperation() }
            }
        }
        
        repeat(40) {
            launch {
                profiler.profile("VariableOp") { variableOperation() }
            }
        }
        
        delay(5000) // Wait for all operations to complete
        
        println("Performance Statistics:")
        println(profiler.getAllStats())
        println()
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Context switching optimization
        ContextSwitchingOptimization().demonstrateContextSwitchingOverhead()
        ContextSwitchingOptimization().demonstrateBatchedContextSwitching()
        ContextSwitchingOptimization().demonstrateStickToDispatcher()
        
        // Memory optimization
        MemoryOptimization().demonstrateObjectPooling()
        MemoryOptimization().demonstrateMemoryEfficientCoroutines()
        MemoryOptimization().demonstrateStringOptimization()
        
        // Batching optimization
        BatchingOptimization().demonstrateBatchedProcessing()
        BatchingOptimization().demonstrateBufferedChannel()
        BatchingOptimization().demonstrateProducerOptimization()
        
        // CPU cache optimization
        CPUCacheOptimization().demonstrateDataLocalityOptimization()
        CPUCacheOptimization().demonstrateStructOfArrays()
        
        // Dispatcher optimization
        DispatcherOptimization().demonstrateCustomDispatcherSizing()
        DispatcherOptimization().demonstrateDispatcherSpecialization()
        
        // Throughput optimization
        ThroughputOptimization().demonstratePipelineOptimization()
        ThroughputOptimization().demonstrateLoadBalancing()
        
        // Performance measurement
        PerformanceMeasurement().demonstratePerformanceProfiling()
        
        println("=== Performance Optimization Summary ===")
        println("✅ Context Switching:")
        println("   - Minimize context switches by batching operations")
        println("   - Group operations by dispatcher type")
        println("   - Use appropriate dispatcher for workload characteristics")
        println()
        println("✅ Memory Optimization:")
        println("   - Use object pooling for expensive-to-create objects")
        println("   - Batch coroutines to reduce memory overhead")
        println("   - Use StringBuilder for string operations")
        println("   - Pre-size collections when possible")
        println()
        println("✅ Batching and Buffering:")
        println("   - Batch database operations and API calls")
        println("   - Use buffered channels for producer-consumer patterns")
        println("   - Implement pipeline patterns for throughput")
        println()
        println("✅ CPU Cache Optimization:")
        println("   - Prefer sequential over random access patterns")
        println("   - Use Struct of Arrays for better cache locality")
        println("   - Process data in cache-friendly chunks")
        println()
        println("✅ Dispatcher Optimization:")
        println("   - Size thread pools appropriately for workload")
        println("   - Use specialized dispatchers for different work types")
        println("   - Avoid over-subscription and under-subscription")
        println()
        println("✅ Throughput Patterns:")
        println("   - Implement parallel pipeline stages")
        println("   - Use dynamic load balancing for variable workloads")
        println("   - Monitor and profile performance continuously")
    }
}