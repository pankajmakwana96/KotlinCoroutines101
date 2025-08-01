/**
 * # Coroutine Performance Fundamentals
 * 
 * ## Problem Description
 * Understanding and optimizing coroutine performance is critical for building
 * high-performance applications. Coroutines introduce unique performance
 * characteristics that differ significantly from traditional thread-based
 * approaches. Common performance issues include excessive context switching,
 * inefficient dispatcher usage, coroutine overhead, memory allocations, and
 * suboptimal concurrency patterns that can severely impact application throughput
 * and latency.
 * 
 * ## Solution Approach
 * Performance optimization strategies include:
 * - Understanding coroutine overhead and creation costs
 * - Optimizing dispatcher selection and thread pool sizing
 * - Minimizing context switching and suspension points
 * - Reducing memory allocations and garbage collection pressure
 * - Implementing efficient concurrency patterns and batching strategies
 * 
 * ## Key Learning Points
 * - Coroutine creation overhead vs thread creation (1000x lighter)
 * - Suspension point analysis and optimization techniques
 * - Dispatcher selection impact on performance characteristics
 * - Memory allocation patterns in coroutine operations
 * - Structured concurrency benefits for performance and resource management
 * 
 * ## Performance Considerations
 * - Coroutine creation: ~100 bytes vs ~8KB for threads
 * - Context switching: ~10-100ns vs ~1-10μs for threads
 * - Memory overhead: Minimal heap allocation for suspended coroutines
 * - CPU utilization: Better cache locality and reduced scheduling overhead
 * - Scalability: Support for millions of concurrent coroutines
 * 
 * ## Common Pitfalls
 * - Creating too many concurrent coroutines without proper limits
 * - Using inappropriate dispatchers for specific workload types
 * - Blocking operations in coroutine contexts
 * - Excessive suspension and resumption in tight loops
 * - Memory leaks from uncancelled or long-running coroutines
 * 
 * ## Real-World Applications
 * - High-throughput web servers and API gateways
 * - Real-time data processing and streaming applications
 * - Concurrent I/O operations and database access patterns
 * - Batch processing and ETL pipeline optimization
 * - Microservices communication and load balancing
 * 
 * ## Related Concepts
 * - Thread pool optimization and sizing strategies
 * - CPU cache optimization and memory locality
 * - Garbage collection tuning for concurrent applications
 * - Performance profiling and monitoring techniques
 */

package performance.optimization.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlin.system.*
import kotlin.time.*
import kotlin.random.Random
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Basic performance measurement and analysis patterns
 * 
 * Performance Analysis Framework:
 * 
 * Measurement Categories:
 * ├── Throughput ─────── Operations per second, requests per second
 * ├── Latency ───────── Response time, processing delay
 * ├── Resource Usage ─── Memory, CPU, thread utilization
 * ├── Scalability ───── Performance under increasing load
 * └── Efficiency ────── Resource utilization vs output
 * 
 * Analysis Process:
 * Baseline -> Load Testing -> Profiling -> Optimization -> Validation
 */
class PerformanceMeasurementPatterns {
    
    data class PerformanceMetrics(
        val operationName: String,
        val iterations: Int,
        val totalTime: Duration,
        val averageTime: Duration,
        val minTime: Duration,
        val maxTime: Duration,
        val throughput: Double, // ops/second
        val memoryUsed: Long = 0,
        val gcCount: Long = 0
    ) {
        override fun toString(): String = buildString {
            appendLine("Performance Metrics for $operationName:")
            appendLine("  Iterations: $iterations")
            appendLine("  Total Time: $totalTime")
            appendLine("  Average Time: $averageTime")
            appendLine("  Min/Max Time: $minTime / $maxTime")
            appendLine("  Throughput: ${String.format("%.1f", throughput)} ops/sec")
            if (memoryUsed > 0) {
                appendLine("  Memory Used: ${memoryUsed / 1024}KB")
            }
            if (gcCount > 0) {
                appendLine("  GC Collections: $gcCount")
            }
        }
    }
    
    class PerformanceBenchmark {
        private val runtime = Runtime.getRuntime()
        
        suspend fun <T> measurePerformance(
            operationName: String,
            iterations: Int,
            warmupIterations: Int = iterations / 10,
            operation: suspend (Int) -> T
        ): PerformanceMetrics {
            
            // Warmup phase
            println("🔥 Warming up $operationName ($warmupIterations iterations)...")
            repeat(warmupIterations) { i ->
                operation(i)
            }
            
            // Force GC before measurement
            System.gc()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            val initialGcCount = getGcCount()
            
            println("📊 Measuring $operationName ($iterations iterations)...")
            val times = mutableListOf<Duration>()
            
            val totalTime = measureTime {
                repeat(iterations) { i ->
                    val operationTime = measureTime {
                        operation(i)
                    }
                    times.add(operationTime)
                }
            }
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val finalGcCount = getGcCount()
            
            val averageTime = totalTime / iterations
            val minTime = times.minOrNull() ?: Duration.ZERO
            val maxTime = times.maxOrNull() ?: Duration.ZERO
            val throughput = iterations / totalTime.inWholeMilliseconds * 1000.0
            val memoryUsed = maxOf(0, finalMemory - initialMemory)
            val gcCount = finalGcCount - initialGcCount
            
            return PerformanceMetrics(
                operationName = operationName,
                iterations = iterations,
                totalTime = totalTime,
                averageTime = averageTime,
                minTime = minTime,
                maxTime = maxTime,
                throughput = throughput,
                memoryUsed = memoryUsed,
                gcCount = gcCount
            )
        }
        
        private fun getGcCount(): Long {
            return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
                .sumOf { it.collectionCount }
        }
        
        fun comparePerformance(metrics: List<PerformanceMetrics>): String = buildString {
            appendLine("=== Performance Comparison ===")
            
            if (metrics.isEmpty()) return "No metrics to compare"
            
            val fastest = metrics.maxByOrNull { it.throughput }
            val slowest = metrics.minByOrNull { it.throughput }
            
            appendLine("Fastest: ${fastest?.operationName} (${String.format("%.1f", fastest?.throughput ?: 0.0)} ops/sec)")
            appendLine("Slowest: ${slowest?.operationName} (${String.format("%.1f", slowest?.throughput ?: 0.0)} ops/sec)")
            
            if (fastest != null && slowest != null && slowest.throughput > 0) {
                val speedup = fastest.throughput / slowest.throughput
                appendLine("Performance Difference: ${String.format("%.1f", speedup)}x")
            }
            
            appendLine("\nDetailed Results:")
            metrics.sortedByDescending { it.throughput }.forEach { metric ->
                appendLine("  ${metric.operationName}: ${String.format("%.1f", metric.throughput)} ops/sec")
            }
        }
    }
    
    suspend fun demonstrateBasicMeasurement() {
        println("=== Basic Performance Measurement Demo ===")
        
        val benchmark = PerformanceBenchmark()
        
        // Test different coroutine creation patterns
        val metrics = mutableListOf<PerformanceMetrics>()
        
        // 1. Simple coroutine creation
        metrics.add(benchmark.measurePerformance("Simple Coroutine Creation", 10000) { i ->
            launch {
                // Minimal work
            }.join()
        })
        
        // 2. Coroutine with suspension
        metrics.add(benchmark.measurePerformance("Coroutine with Delay", 1000) { i ->
            launch {
                delay(1)
            }.join()
        })
        
        // 3. CPU-intensive work in coroutine
        metrics.add(benchmark.measurePerformance("CPU-Intensive Coroutine", 1000) { i ->
            launch {
                var sum = 0
                repeat(1000) { j ->
                    sum += j
                    if (j % 100 == 0) yield() // Cooperative cancellation
                }
            }.join()
        })
        
        // Compare and display results
        println(benchmark.comparePerformance(metrics))
        
        println("✅ Basic measurement demo completed")
    }
    
    suspend fun demonstrateCoroutineOverhead() {
        println("=== Coroutine Overhead Analysis Demo ===")
        
        val benchmark = PerformanceBenchmark()
        val metrics = mutableListOf<PerformanceMetrics>()
        
        // Compare different approaches to parallel execution
        val workSize = 10000
        val batchSize = 100
        
        // 1. Sequential execution
        metrics.add(benchmark.measurePerformance("Sequential Execution", batchSize) { i ->
            repeat(workSize / batchSize) { j ->
                // Simulate work
                val result = (i * batchSize + j) * 2
            }
        })
        
        // 2. Thread-based parallel execution
        metrics.add(benchmark.measurePerformance("Thread-Based Parallel", batchSize) { i ->
            val executor = Executors.newFixedThreadPool(4)
            val futures = (0 until workSize / batchSize).map { j ->
                executor.submit {
                    val result = (i * batchSize + j) * 2
                }
            }
            futures.forEach { it.get() }
            executor.shutdown()
        })
        
        // 3. Coroutine-based parallel execution
        metrics.add(benchmark.measurePerformance("Coroutine-Based Parallel", batchSize) { i ->
            coroutineScope {
                val jobs = (0 until workSize / batchSize).map { j ->
                    launch {
                        val result = (i * batchSize + j) * 2
                    }
                }
                jobs.forEach { it.join() }
            }
        })
        
        // 4. Coroutine with async/await
        metrics.add(benchmark.measurePerformance("Coroutine Async/Await", batchSize) { i ->
            coroutineScope {
                val deferreds = (0 until workSize / batchSize).map { j ->
                    async {
                        (i * batchSize + j) * 2
                    }
                }
                deferreds.awaitAll()
            }
        })
        
        println(benchmark.comparePerformance(metrics))
        
        println("✅ Coroutine overhead analysis completed")
    }
    
    suspend fun demonstrateScalabilityAnalysis() {
        println("=== Scalability Analysis Demo ===")
        
        val benchmark = PerformanceBenchmark()
        val concurrencyLevels = listOf(1, 10, 100, 1000, 10000)
        
        println("Testing scalability with different concurrency levels...")
        
        for (concurrency in concurrencyLevels) {
            val metrics = benchmark.measurePerformance(
                "Concurrency Level $concurrency", 
                10 // Fewer iterations for high concurrency
            ) { _ ->
                coroutineScope {
                    repeat(concurrency) {
                        launch {
                            delay(1) // Simulate async work
                        }
                    }
                }
            }
            
            println("Concurrency $concurrency: ${String.format("%.1f", metrics.throughput)} ops/sec, " +
                   "Avg: ${metrics.averageTime}, Memory: ${metrics.memoryUsed / 1024}KB")
        }
        
        println("✅ Scalability analysis completed")
    }
}

/**
 * Coroutine creation and lifecycle performance patterns
 */
class CoroutineLifecyclePerformance {
    
    class CoroutinePoolManager(
        private val poolSize: Int = 100
    ) {
        private val coroutinePool = Channel<CompletableDeferred<Unit>>(poolSize)
        private val activeCoroutines = AtomicInteger(0)
        
        init {
            // Pre-populate pool
            repeat(poolSize) {
                coroutinePool.trySend(CompletableDeferred())
            }
        }
        
        suspend fun executeWithPool(work: suspend () -> Unit) {
            val deferred = coroutinePool.receive()
            activeCoroutines.incrementAndGet()
            
            try {
                work()
                deferred.complete(Unit)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
                throw e
            } finally {
                activeCoroutines.decrementAndGet()
                coroutinePool.send(CompletableDeferred())
            }
        }
        
        fun getActiveCount(): Int = activeCoroutines.get()
        
        suspend fun shutdown() {
            coroutinePool.close()
        }
    }
    
    class CoroutineBatchProcessor<T, R>(
        private val batchSize: Int = 100,
        private val processor: suspend (List<T>) -> List<R>
    ) {
        private val buffer = mutableListOf<T>()
        private val results = mutableListOf<R>()
        
        suspend fun process(item: T): R? {
            buffer.add(item)
            
            if (buffer.size >= batchSize) {
                return processBatch()
            }
            
            return null
        }
        
        private suspend fun processBatch(): R? {
            if (buffer.isEmpty()) return null
            
            val batch = buffer.toList()
            buffer.clear()
            
            val batchResults = processor(batch)
            results.addAll(batchResults)
            
            return batchResults.lastOrNull()
        }
        
        suspend fun flush(): List<R> {
            processBatch()
            return results.toList()
        }
    }
    
    suspend fun demonstrateCreationOptimization() {
        println("=== Coroutine Creation Optimization Demo ===")
        
        val benchmark = PerformanceMeasurementPatterns.PerformanceBenchmark()
        val iterations = 10000
        
        // 1. Naive approach - create new coroutine for each task
        val naiveMetrics = benchmark.measurePerformance("Naive Creation", iterations) { i ->
            launch {
                delay(1)
                i * 2
            }.join()
        }
        
        // 2. Batch processing approach
        val batchMetrics = benchmark.measurePerformance("Batch Processing", iterations / 100) { batch ->
            coroutineScope {
                val batchSize = 100
                val jobs = (0 until batchSize).map { i ->
                    async {
                        delay(1)
                        (batch * batchSize + i) * 2
                    }
                }
                jobs.awaitAll()
            }
        }
        
        // 3. Coroutine pool approach
        val poolManager = CoroutinePoolManager(50)
        val poolMetrics = benchmark.measurePerformance("Pooled Execution", iterations / 10) { batch ->
            coroutineScope {
                repeat(10) { i ->
                    launch {
                        poolManager.executeWithPool {
                            delay(1)
                            (batch * 10 + i) * 2
                        }
                    }
                }
            }
        }
        
        val comparisonResults = benchmark.comparePerformance(listOf(naiveMetrics, batchMetrics, poolMetrics))
        println(comparisonResults)
        
        poolManager.shutdown()
        
        println("✅ Creation optimization demo completed")
    }
    
    suspend fun demonstrateBatchingEfficiency() {
        println("=== Batching Efficiency Demo ===")
        
        val benchmark = PerformanceMeasurementPatterns.PerformanceBenchmark()
        val dataSize = 10000
        
        // Create test data
        val testData = (1..dataSize).toList()
        
        // Simulate expensive processing function
        val expensiveProcessor: suspend (List<Int>) -> List<String> = { batch ->
            delay(10) // Simulate I/O or computation overhead
            batch.map { "processed-$it" }
        }
        
        // Test different batch sizes
        val batchSizes = listOf(1, 10, 100, 1000)
        
        for (batchSize in batchSizes) {
            val metrics = benchmark.measurePerformance("Batch Size $batchSize", 10) { _ ->
                val batchProcessor = CoroutineBatchProcessor(batchSize, expensiveProcessor)
                
                val results = mutableListOf<String>()
                testData.forEach { item ->
                    val result = batchProcessor.process(item)
                    result?.let { results.add(it) }
                }
                
                // Flush remaining items
                val finalResults = batchProcessor.flush()
                results.addAll(finalResults)
            }
            
            println("Batch size $batchSize: ${String.format("%.1f", metrics.throughput)} ops/sec, " +
                   "Avg: ${metrics.averageTime}")
        }
        
        println("✅ Batching efficiency demo completed")
    }
    
    suspend fun demonstrateLifecycleOptimization() {
        println("=== Lifecycle Optimization Demo ===")
        
        val benchmark = PerformanceMeasurementPatterns.PerformanceBenchmark()
        
        // 1. Short-lived coroutines (anti-pattern)
        val shortLivedMetrics = benchmark.measurePerformance("Short-lived Coroutines", 1000) { i ->
            launch {
                // Very short work
                val result = i * 2
            }.join()
        }
        
        // 2. Long-lived worker coroutines
        val longLivedMetrics = benchmark.measurePerformance("Long-lived Workers", 100) { batch ->
            val channel = Channel<Int>(100)
            val resultChannel = Channel<Int>(100)
            
            // Start worker coroutines
            val workers = List(4) {
                launch {
                    for (item in channel) {
                        val result = item * 2
                        resultChannel.send(result)
                    }
                }
            }
            
            // Send work
            repeat(100) { i ->
                channel.send(batch * 100 + i)
            }
            channel.close()
            
            // Collect results
            val results = mutableListOf<Int>()
            repeat(100) {
                results.add(resultChannel.receive())
            }
            
            workers.forEach { it.join() }
            resultChannel.close()
        }
        
        // 3. Coroutine reuse with channels
        val channelMetrics = benchmark.measurePerformance("Channel-based Reuse", 1000) { i ->
            val workChannel = Channel<Int>(10)
            val resultChannel = Channel<Int>(10)
            
            val worker = launch {
                for (work in workChannel) {
                    resultChannel.send(work * 2)
                }
                resultChannel.close()
            }
            
            workChannel.send(i)
            workChannel.close()
            
            val result = resultChannel.receive()
            worker.join()
        }
        
        val comparison = benchmark.comparePerformance(listOf(shortLivedMetrics, longLivedMetrics, channelMetrics))
        println(comparison)
        
        println("✅ Lifecycle optimization demo completed")
    }
}

/**
 * Memory allocation and garbage collection optimization patterns
 */
class MemoryOptimizationPatterns {
    
    data class MemoryProfile(
        val operationName: String,
        val allocatedBytes: Long,
        val gcCount: Long,
        val gcTime: Long,
        val objectsCreated: Long = 0
    ) {
        override fun toString(): String = buildString {
            appendLine("Memory Profile for $operationName:")
            appendLine("  Allocated: ${allocatedBytes / 1024}KB")
            appendLine("  GC Collections: $gcCount")
            appendLine("  GC Time: ${gcTime}ms")
            if (objectsCreated > 0) {
                appendLine("  Objects Created: $objectsCreated")
            }
        }
    }
    
    class MemoryProfiler {
        private val runtime = Runtime.getRuntime()
        
        fun <T> profileMemoryUsage(operationName: String, operation: () -> T): Pair<T, MemoryProfile> {
            // Force GC before measurement
            System.gc()
            Thread.sleep(100) // Allow GC to complete
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            val initialGc = getGcInfo()
            
            val result = operation()
            
            // Force GC after operation to see actual allocation
            System.gc()
            Thread.sleep(100)
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val finalGc = getGcInfo()
            
            val profile = MemoryProfile(
                operationName = operationName,
                allocatedBytes = maxOf(0, finalMemory - initialMemory),
                gcCount = finalGc.first - initialGc.first,
                gcTime = finalGc.second - initialGc.second
            )
            
            return result to profile
        }
        
        private fun getGcInfo(): Pair<Long, Long> {
            val gcBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
            val totalCollections = gcBeans.sumOf { it.collectionCount }
            val totalTime = gcBeans.sumOf { it.collectionTime }
            return totalCollections to totalTime
        }
    }
    
    // Object pooling for high-frequency allocations
    class ObjectPool<T>(
        private val factory: () -> T,
        private val reset: (T) -> Unit,
        initialSize: Int = 10
    ) {
        private val pool = Channel<T>(Channel.UNLIMITED)
        
        init {
            repeat(initialSize) {
                pool.trySend(factory())
            }
        }
        
        suspend fun borrow(): T {
            return pool.tryReceive().getOrNull() ?: factory()
        }
        
        suspend fun return(item: T) {
            reset(item)
            pool.trySend(item)
        }
        
        suspend fun <R> use(operation: suspend (T) -> R): R {
            val item = borrow()
            return try {
                operation(item)
            } finally {
                return(item)
            }
        }
    }
    
    suspend fun demonstrateAllocationOptimization() {
        println("=== Memory Allocation Optimization Demo ===")
        
        val profiler = MemoryProfiler()
        val iterations = 10000
        
        // 1. Naive approach with many allocations
        val (_, naiveProfile) = profiler.profileMemoryUsage("Naive Allocations") {
            runBlocking {
                repeat(iterations) { i ->
                    launch {
                        val data = ByteArray(1024) // 1KB allocation per coroutine
                        data[0] = i.toByte()
                    }.join()
                }
            }
        }
        
        // 2. Object pooling approach
        val byteArrayPool = ObjectPool(
            factory = { ByteArray(1024) },
            reset = { array -> array.fill(0) },
            initialSize = 100
        )
        
        val (_, pooledProfile) = profiler.profileMemoryUsage("Pooled Allocations") {
            runBlocking {
                repeat(iterations) { i ->
                    launch {
                        byteArrayPool.use { data ->
                            data[0] = i.toByte()
                        }
                    }.join()
                }
            }
        }
        
        // 3. Batch processing to reduce allocations
        val (_, batchProfile) = profiler.profileMemoryUsage("Batch Processing") {
            runBlocking {
                val batchSize = 100
                repeat(iterations / batchSize) { batch ->
                    launch {
                        val sharedData = ByteArray(1024 * batchSize)
                        repeat(batchSize) { i ->
                            sharedData[i * 1024] = (batch * batchSize + i).toByte()
                        }
                    }.join()
                }
            }
        }
        
        println(naiveProfile)
        println(pooledProfile)
        println(batchProfile)
        
        println("Memory Usage Comparison:")
        println("  Naive: ${naiveProfile.allocatedBytes / 1024}KB")
        println("  Pooled: ${pooledProfile.allocatedBytes / 1024}KB")
        println("  Batch: ${batchProfile.allocatedBytes / 1024}KB")
        
        println("✅ Allocation optimization demo completed")
    }
    
    suspend fun demonstrateGarbageCollectionImpact() {
        println("=== Garbage Collection Impact Demo ===")
        
        val profiler = MemoryProfiler()
        
        // Test different allocation patterns
        val scenarios = listOf(
            "Small Frequent Allocations" to {
                runBlocking {
                    repeat(100000) { i ->
                        launch {
                            val small = ByteArray(64)
                            small[0] = i.toByte()
                        }.join()
                    }
                }
            },
            
            "Large Infrequent Allocations" to {
                runBlocking {
                    repeat(100) { i ->
                        launch {
                            val large = ByteArray(64 * 1000)
                            large[0] = i.toByte()
                        }.join()
                    }
                }
            },
            
            "Mixed Allocation Sizes" to {
                runBlocking {
                    repeat(10000) { i ->
                        launch {
                            val size = when (i % 3) {
                                0 -> 64
                                1 -> 1024
                                else -> 4096
                            }
                            val data = ByteArray(size)
                            data[0] = i.toByte()
                        }.join()
                    }
                }
            }
        )
        
        scenarios.forEach { (name, scenario) ->
            val (_, profile) = profiler.profileMemoryUsage(name, scenario)
            println(profile)
            
            if (profile.gcTime > 0) {
                val gcOverhead = profile.gcTime.toDouble() / 1000.0 // Convert to seconds
                println("  GC Overhead: ${String.format("%.2f", gcOverhead)}s")
            }
            println()
        }
        
        println("✅ GC impact demo completed")
    }
    
    suspend fun demonstrateMemoryLeakPrevention() {
        println("=== Memory Leak Prevention Demo ===")
        
        val profiler = MemoryProfiler()
        
        // Simulate potential memory leak scenario
        val (_, leakyProfile) = profiler.profileMemoryUsage("Potentially Leaky Code") {
            runBlocking {
                val longLivedData = mutableListOf<ByteArray>()
                
                repeat(1000) { i ->
                    launch {
                        val data = ByteArray(1024)
                        longLivedData.add(data) // Potential memory leak
                        
                        // Simulate work
                        delay(1)
                        
                        // In real scenario, we might forget to remove from list
                    }.join()
                }
                
                // Clean up (in real code, this might be missed)
                longLivedData.clear()
            }
        }
        
        // Proper resource management
        val (_, cleanProfile) = profiler.profileMemoryUsage("Clean Resource Management") {
            runBlocking {
                repeat(1000) { i ->
                    launch {
                        val data = ByteArray(1024)
                        
                        try {
                            // Simulate work
                            delay(1)
                            
                            // Process data
                            data[0] = i.toByte()
                            
                        } finally {
                            // Explicit cleanup if needed
                            // data gets garbage collected automatically
                        }
                    }.join()
                }
            }
        }
        
        println(leakyProfile)
        println(cleanProfile)
        
        println("Memory Retention Comparison:")
        println("  Potentially Leaky: ${leakyProfile.allocatedBytes / 1024}KB retained")
        println("  Clean Management: ${cleanProfile.allocatedBytes / 1024}KB retained")
        
        println("✅ Memory leak prevention demo completed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Coroutine Performance Fundamentals Demo ===")
    
    try {
        // Basic performance measurement
        val measurements = PerformanceMeasurementPatterns()
        measurements.demonstrateBasicMeasurement()
        println()
        
        measurements.demonstrateCoroutineOverhead()
        println()
        
        measurements.demonstrateScalabilityAnalysis()
        println()
        
        // Lifecycle performance optimization
        val lifecycle = CoroutineLifecyclePerformance()
        lifecycle.demonstrateCreationOptimization()
        println()
        
        lifecycle.demonstrateBatchingEfficiency()
        println()
        
        lifecycle.demonstrateLifecycleOptimization()
        println()
        
        // Memory optimization
        val memory = MemoryOptimizationPatterns()
        memory.demonstrateAllocationOptimization()
        println()
        
        memory.demonstrateGarbageCollectionImpact()
        println()
        
        memory.demonstrateMemoryLeakPrevention()
        println()
        
        println("=== All Performance Fundamentals Demos Completed ===")
        println()
        
        println("Key Performance Concepts Covered:")
        println("✅ Performance measurement and benchmarking techniques")
        println("✅ Coroutine overhead analysis and optimization")
        println("✅ Scalability testing with varying concurrency levels")
        println("✅ Coroutine creation and lifecycle optimization")
        println("✅ Batching strategies for improved efficiency")
        println("✅ Memory allocation optimization and object pooling")
        println("✅ Garbage collection impact analysis and mitigation")
        println("✅ Memory leak prevention and resource management")
        println()
        
        println("Performance Optimization Guidelines:")
        println("✅ Measure before optimizing - establish baselines")
        println("✅ Choose appropriate concurrency levels for workload")
        println("✅ Prefer long-lived worker coroutines over short-lived ones")
        println("✅ Use batching to reduce overhead for bulk operations")
        println("✅ Implement object pooling for high-frequency allocations")
        println("✅ Monitor garbage collection impact and optimize allocation patterns")
        println("✅ Ensure proper resource cleanup to prevent memory leaks")
        println("✅ Profile regularly to detect performance regressions")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}