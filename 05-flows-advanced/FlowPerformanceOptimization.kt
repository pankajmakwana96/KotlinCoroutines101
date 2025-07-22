/**
 * # Advanced Flow Performance Optimization
 * 
 * ## Problem Description
 * Flow performance can degrade due to inefficient operator chains, inappropriate
 * dispatcher usage, excessive memory allocations, poor buffering strategies,
 * and suboptimal concurrency patterns. Understanding flow internals and applying
 * targeted optimizations is crucial for high-performance reactive applications.
 * 
 * ## Solution Approach
 * Performance optimization strategies include:
 * - Flow operator fusion and chain optimization
 * - Memory allocation reduction and object pooling
 * - Efficient dispatcher selection and context switching
 * - Parallelization patterns and concurrency control
 * - Hot vs cold flow performance characteristics
 * 
 * ## Key Learning Points
 * - Flow operators can be fused for better performance
 * - Memory allocations are a major performance factor
 * - Context switching overhead impacts throughput
 * - Parallel processing requires careful coordination
 * - Benchmarking is essential for optimization validation
 * 
 * ## Performance Considerations
 * - Operator fusion reduces intermediate allocations (~30-70% improvement)
 * - flatMapMerge concurrency limits affect CPU usage
 * - Buffer sizes impact memory usage (16-1024 typical range)
 * - Context switching overhead: ~1-10μs per switch
 * - Object allocation overhead: ~10-100ns per object
 * 
 * ## Common Pitfalls
 * - Over-optimization without proper benchmarking
 * - Ignoring memory allocation patterns
 * - Inappropriate parallelization strategies
 * - Poor buffer size selection
 * - Missing performance monitoring
 * 
 * ## Real-World Applications
 * - High-frequency trading data processing
 * - Real-time analytics and streaming
 * - IoT data ingestion pipelines
 * - Game engine update loops
 * - Mobile app performance optimization
 * 
 * ## Related Concepts
 * - JVM performance characteristics
 * - Memory management and GC impact
 * - CPU cache efficiency
 * - Thread pool optimization
 */

package flow.advanced.performance

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Flow operator optimization and fusion
 * 
 * Flow Optimization Hierarchy:
 * 
 * Performance Factors:
 * ├── Operator Chain ──────── Fusion opportunities
 * ├── Memory Allocation ───── Object pooling, reuse
 * ├── Context Switching ───── Dispatcher optimization
 * ├── Concurrency ────────── Parallelization patterns
 * └── Buffer Management ───── Size and strategy tuning
 * 
 * Optimization Pipeline:
 * Source -> [Fused Operators] -> [Buffer] -> [Parallel Processing] -> Sink
 */
class FlowOperatorOptimization {
    
    fun demonstrateOperatorFusion() = runBlocking {
        println("=== Flow Operator Fusion ===")
        
        val dataSize = 100_000
        val testData = (1..dataSize).asFlow()
        
        // Non-fused chain (multiple intermediate collections)
        println("1. Non-fused operator chain:")
        val nonFusedTime = measureTimeMillis {
            testData
                .map { it * 2 }
                .toList() // Force materialization
                .asFlow()
                .filter { it > 1000 }
                .toList() // Force materialization
                .asFlow()
                .map { it + 1 }
                .count()
        }
        println("  Non-fused time: ${nonFusedTime}ms")
        
        // Fused chain (single pass through data)
        println("2. Fused operator chain:")
        val fusedTime = measureTimeMillis {
            testData
                .map { it * 2 }
                .filter { it > 1000 }
                .map { it + 1 }
                .count()
        }
        println("  Fused time: ${fusedTime}ms")
        
        val improvement = ((nonFusedTime - fusedTime).toDouble() / nonFusedTime * 100)
        println("  Performance improvement: ${"%.1f".format(improvement)}%")
        
        println()
        
        // Transform vs separate map operations
        println("3. Transform vs separate operations:")
        
        val separateOpsTime = measureTimeMillis {
            testData
                .map { it.toString() }
                .map { "Value: $it" }
                .map { it.uppercase() }
                .count()
        }
        
        val transformTime = measureTimeMillis {
            testData
                .transform { value ->
                    // Single transformation step
                    val string = value.toString()
                    val prefixed = "Value: $string"
                    val uppercased = prefixed.uppercase()
                    emit(uppercased)
                }
                .count()
        }
        
        println("  Separate operations: ${separateOpsTime}ms")
        println("  Transform operation: ${transformTime}ms")
        println("  Transform improvement: ${"%.1f".format((separateOpsTime - transformTime).toDouble() / separateOpsTime * 100)}%")
        
        println("Operator fusion completed\n")
    }
    
    fun demonstrateMemoryOptimization() = runBlocking {
        println("=== Memory Allocation Optimization ===")
        
        // Object pooling for frequent allocations
        class StringPool {
            private val pool = mutableListOf<StringBuilder>()
            
            fun borrow(): StringBuilder {
                return if (pool.isNotEmpty()) {
                    pool.removeLastOrNull()?.clear() ?: StringBuilder()
                } else {
                    StringBuilder()
                }
            }
            
            fun return(sb: StringBuilder) {
                if (pool.size < 10) { // Limit pool size
                    pool.add(sb)
                }
            }
            
            inline fun <T> use(block: (StringBuilder) -> T): T {
                val sb = borrow()
                try {
                    return block(sb)
                } finally {
                    return(sb)
                }
            }
        }
        
        val stringPool = StringPool()
        
        println("1. Memory allocation patterns:")
        
        // High-allocation approach
        val highAllocTime = measureTimeMillis {
            flow {
                repeat(10_000) { i ->
                    emit(i)
                }
            }
                .map { value ->
                    // Creates new StringBuilder each time
                    StringBuilder()
                        .append("Value: ")
                        .append(value)
                        .append(" processed")
                        .toString()
                }
                .count()
        }
        
        // Pool-based approach
        val pooledTime = measureTimeMillis {
            flow {
                repeat(10_000) { i ->
                    emit(i)
                }
            }
                .map { value ->
                    // Reuse StringBuilder from pool
                    stringPool.use { sb ->
                        sb.append("Value: ")
                            .append(value)
                            .append(" processed")
                            .toString()
                    }
                }
                .count()
        }
        
        println("  High allocation: ${highAllocTime}ms")
        println("  Pooled allocation: ${pooledTime}ms")
        
        println()
        
        // Avoiding intermediate collections
        println("2. Intermediate collection optimization:")
        
        val intermediateTime = measureTimeMillis {
            flow {
                repeat(50_000) { i ->
                    emit(i)
                }
            }
                .map { it * 2 }
                .toList() // Unnecessary materialization
                .asFlow()
                .filter { it % 3 == 0 }
                .count()
        }
        
        val streamingTime = measureTimeMillis {
            flow {
                repeat(50_000) { i ->
                    emit(i)
                }
            }
                .map { it * 2 }
                .filter { it % 3 == 0 }
                .count()
        }
        
        println("  With intermediate collection: ${intermediateTime}ms")
        println("  Pure streaming: ${streamingTime}ms")
        
        println("Memory optimization completed\n")
    }
    
    fun demonstrateContextOptimization() = runBlocking {
        println("=== Context Switching Optimization ===")
        
        val dataSize = 1_000
        
        // Excessive context switching
        println("1. Excessive context switching:")
        val excessiveSwitchingTime = measureTimeMillis {
            flow {
                repeat(dataSize) { i ->
                    emit(i)
                }
            }
                .flowOn(Dispatchers.IO)
                .map { it * 2 }
                .flowOn(Dispatchers.Default)
                .filter { it > 100 }
                .flowOn(Dispatchers.IO)
                .map { it + 1 }
                .flowOn(Dispatchers.Default)
                .count()
        }
        
        // Optimized context usage
        println("2. Optimized context usage:")
        val optimizedTime = measureTimeMillis {
            flow {
                repeat(dataSize) { i ->
                    emit(i)
                }
            }
                .map { it * 2 }
                .filter { it > 100 }
                .map { it + 1 }
                .flowOn(Dispatchers.Default) // Single context switch
                .count()
        }
        
        println("  Excessive switching: ${excessiveSwitchingTime}ms")
        println("  Optimized switching: ${optimizedTime}ms")
        
        println()
        
        // Context-aware operations
        println("3. Context-aware operation placement:")
        
        suspend fun cpuIntensiveWork(value: Int): Int {
            // Simulate CPU work
            var result = value
            repeat(100) {
                result = (result * 1.1).toInt()
            }
            return result
        }
        
        suspend fun ioWork(value: Int): String {
            delay(1) // Simulate I/O
            return "Result: $value"
        }
        
        val contextAwareTime = measureTimeMillis {
            flow {
                repeat(100) { i ->
                    emit(i)
                }
            }
                .map { cpuIntensiveWork(it) } // CPU work
                .flowOn(Dispatchers.Default)
                .map { ioWork(it) } // I/O work
                .flowOn(Dispatchers.IO)
                .count()
        }
        
        println("  Context-aware operations: ${contextAwareTime}ms")
        
        println("Context optimization completed\n")
    }
}

/**
 * Parallel processing and concurrency optimization
 */
class ParallelFlowOptimization {
    
    fun demonstrateFlatMapConcurrency() = runBlocking {
        println("=== FlatMap Concurrency Optimization ===")
        
        suspend fun processItem(item: Int): String {
            delay(Random.nextLong(50, 150)) // Simulate work
            return "Processed-$item"
        }
        
        val items = (1..20).asFlow()
        
        // Sequential processing
        println("1. Sequential processing:")
        val sequentialTime = measureTimeMillis {
            items
                .map { processItem(it) }
                .count()
        }
        println("  Sequential time: ${sequentialTime}ms")
        
        // Concurrent processing with different concurrency levels
        val concurrencyLevels = listOf(2, 4, 8, 16)
        
        for (concurrency in concurrencyLevels) {
            val concurrentTime = measureTimeMillis {
                items
                    .flatMapMerge(concurrency = concurrency) { item ->
                        flow { emit(processItem(item)) }
                    }
                    .count()
            }
            
            val speedup = sequentialTime.toDouble() / concurrentTime
            println("  Concurrency $concurrency: ${concurrentTime}ms (${String.format("%.1f", speedup)}x speedup)")
        }
        
        println()
        
        // Optimal concurrency detection
        println("2. Adaptive concurrency:")
        
        suspend fun findOptimalConcurrency(): Int {
            val testSize = 10
            val testItems = (1..testSize).asFlow()
            var bestConcurrency = 1
            var bestTime = Long.MAX_VALUE
            
            for (concurrency in 1..8) {
                val time = measureTimeMillis {
                    testItems
                        .flatMapMerge(concurrency = concurrency) { item ->
                            flow { emit(processItem(item)) }
                        }
                        .count()
                }
                
                if (time < bestTime) {
                    bestTime = time
                    bestConcurrency = concurrency
                }
            }
            
            return bestConcurrency
        }
        
        val optimalConcurrency = findOptimalConcurrency()
        println("  Detected optimal concurrency: $optimalConcurrency")
        
        // Use optimal concurrency
        val optimalTime = measureTimeMillis {
            items
                .flatMapMerge(concurrency = optimalConcurrency) { item ->
                    flow { emit(processItem(item)) }
                }
                .count()
        }
        
        println("  Optimal concurrent time: ${optimalTime}ms")
        
        println("FlatMap concurrency completed\n")
    }
    
    fun demonstrateParallelCollectionProcessing() = runBlocking {
        println("=== Parallel Collection Processing ===")
        
        val largeDataset = (1..1000).toList()
        
        // Sequential processing
        println("1. Sequential processing:")
        val sequentialTime = measureTimeMillis {
            largeDataset
                .asFlow()
                .map { item ->
                    // CPU-intensive work
                    var result = item
                    repeat(1000) {
                        result = (result * 1.001).toInt()
                    }
                    result
                }
                .count()
        }
        println("  Sequential: ${sequentialTime}ms")
        
        // Chunked parallel processing
        println("2. Chunked parallel processing:")
        val chunkSize = 50
        val chunkedTime = measureTimeMillis {
            largeDataset
                .chunked(chunkSize)
                .asFlow()
                .flatMapMerge(concurrency = 4) { chunk ->
                    flow {
                        // Process chunk in parallel
                        val results = chunk.map { item ->
                            var result = item
                            repeat(1000) {
                                result = (result * 1.001).toInt()
                            }
                            result
                        }
                        results.forEach { emit(it) }
                    }
                }
                .flowOn(Dispatchers.Default)
                .count()
        }
        
        val chunkSpeedup = sequentialTime.toDouble() / chunkedTime
        println("  Chunked parallel: ${chunkedTime}ms (${String.format("%.1f", chunkSpeedup)}x speedup)")
        
        println()
        
        // Work-stealing pattern
        println("3. Work-stealing pattern:")
        
        class WorkQueue<T>(private val items: List<T>) {
            private var index = 0
            
            @Synchronized
            fun nextBatch(batchSize: Int): List<T> {
                val start = index
                val end = minOf(start + batchSize, items.size)
                index = end
                return items.subList(start, end)
            }
            
            val hasWork: Boolean
                @Synchronized get() = index < items.size
        }
        
        val workQueue = WorkQueue(largeDataset)
        
        val workStealingTime = measureTimeMillis {
            val workers = 4
            val results = (1..workers).map { workerId ->
                async(Dispatchers.Default) {
                    var count = 0
                    while (workQueue.hasWork) {
                        val batch = workQueue.nextBatch(25)
                        batch.forEach { item ->
                            // Process item
                            var result = item
                            repeat(1000) {
                                result = (result * 1.001).toInt()
                            }
                            count++
                        }
                    }
                    count
                }
            }
            
            results.awaitAll().sum()
        }
        
        val workStealingSpeedup = sequentialTime.toDouble() / workStealingTime
        println("  Work stealing: ${workStealingTime}ms (${String.format("%.1f", workStealingSpeedup)}x speedup)")
        
        println("Parallel processing completed\n")
    }
    
    fun demonstratePipelineParallelism() = runBlocking {
        println("=== Pipeline Parallelism ===")
        
        // Simulate multi-stage processing pipeline
        suspend fun stage1(item: Int): String {
            delay(50) // I/O operation
            return "Stage1-$item"
        }
        
        suspend fun stage2(item: String): String {
            // CPU operation
            var result = item
            repeat(1000) {
                result = result.hashCode().toString()
            }
            return "Stage2-$result"
        }
        
        suspend fun stage3(item: String): String {
            delay(30) // Another I/O operation
            return "Stage3-$item"
        }
        
        val items = (1..50).asFlow()
        
        // Sequential pipeline
        println("1. Sequential pipeline:")
        val sequentialTime = measureTimeMillis {
            items
                .map { stage1(it) }
                .map { stage2(it) }
                .map { stage3(it) }
                .count()
        }
        println("  Sequential pipeline: ${sequentialTime}ms")
        
        // Parallel stages with different dispatchers
        println("2. Parallel pipeline with context optimization:")
        val parallelTime = measureTimeMillis {
            items
                .map { stage1(it) }
                .flowOn(Dispatchers.IO) // I/O stage
                .buffer(10) // Buffer between stages
                .map { stage2(it) }
                .flowOn(Dispatchers.Default) // CPU stage
                .buffer(10)
                .map { stage3(it) }
                .flowOn(Dispatchers.IO) // I/O stage
                .count()
        }
        
        val pipelineSpeedup = sequentialTime.toDouble() / parallelTime
        println("  Parallel pipeline: ${parallelTime}ms (${String.format("%.1f", pipelineSpeedup)}x speedup)")
        
        println()
        
        // Pipeline with backpressure handling
        println("3. Pipeline with backpressure optimization:")
        val backpressureTime = measureTimeMillis {
            items
                .map { stage1(it) }
                .flowOn(Dispatchers.IO)
                .buffer(capacity = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .map { stage2(it) }
                .flowOn(Dispatchers.Default)
                .conflate() // Keep only latest for final stage
                .map { stage3(it) }
                .flowOn(Dispatchers.IO)
                .count()
        }
        
        println("  Pipeline with backpressure: ${backpressureTime}ms")
        
        println("Pipeline parallelism completed\n")
    }
}

/**
 * Buffer and caching optimizations
 */
class BufferCacheOptimization {
    
    fun demonstrateBufferSizeOptimization() = runBlocking {
        println("=== Buffer Size Optimization ===")
        
        // Test different buffer sizes
        suspend fun testBufferSize(bufferSize: Int): Pair<Long, Long> {
            var produced = 0L
            var consumed = 0L
            
            val time = measureTimeMillis {
                flow {
                    repeat(1000) { i ->
                        emit(i)
                        produced++
                        delay(1) // Fast producer
                    }
                }
                    .buffer(bufferSize)
                    .collect { value ->
                        consumed++
                        delay(5) // Slow consumer
                    }
            }
            
            return time to (produced - consumed)
        }
        
        println("Buffer size performance analysis:")
        val bufferSizes = listOf(1, 8, 32, 128, 512)
        
        for (size in bufferSizes) {
            val (time, queuedItems) = testBufferSize(size)
            val memoryEstimate = size * 8 // Rough estimate
            println("  Size $size: ${time}ms, ${queuedItems} queued, ~${memoryEstimate}B memory")
        }
        
        println()
        
        // Adaptive buffer sizing
        println("Adaptive buffer sizing:")
        
        class AdaptiveFlowBuffer<T> {
            private var currentSize = 16
            private var throughputHistory = mutableListOf<Double>()
            
            fun getOptimalSize(recentThroughput: Double): Int {
                throughputHistory.add(recentThroughput)
                if (throughputHistory.size > 5) {
                    throughputHistory.removeFirst()
                }
                
                val avgThroughput = throughputHistory.average()
                
                // Adjust buffer size based on throughput trends
                currentSize = when {
                    avgThroughput > 100 && currentSize < 256 -> (currentSize * 1.5).toInt()
                    avgThroughput < 50 && currentSize > 8 -> (currentSize * 0.8).toInt()
                    else -> currentSize
                }
                
                return currentSize
            }
        }
        
        val adaptiveBuffer = AdaptiveFlowBuffer<Int>()
        var totalProcessed = 0
        
        flow {
            repeat(500) { i ->
                emit(i)
                delay(Random.nextLong(1, 10))
            }
        }
            .transform { value ->
                val throughput = Random.nextDouble(30.0, 120.0) // Simulate varying throughput
                val bufferSize = adaptiveBuffer.getOptimalSize(throughput)
                emit(value to bufferSize)
            }
            .buffer(16) // Base buffer
            .collect { (value, bufferSize) ->
                totalProcessed++
                if (totalProcessed % 100 == 0) {
                    println("  Processed $totalProcessed items with buffer size $bufferSize")
                }
            }
        
        println("Buffer optimization completed\n")
    }
    
    fun demonstrateCachingStrategies() = runBlocking {
        println("=== Caching Strategies ===")
        
        // Simple cache implementation
        class FlowCache<K, V>(private val maxSize: Int = 100) {
            private val cache = mutableMapOf<K, V>()
            private val accessOrder = mutableListOf<K>()
            
            fun get(key: K): V? = cache[key]?.also { moveToEnd(key) }
            
            fun put(key: K, value: V) {
                cache[key] = value
                moveToEnd(key)
                evictIfNecessary()
            }
            
            private fun moveToEnd(key: K) {
                accessOrder.remove(key)
                accessOrder.add(key)
            }
            
            private fun evictIfNecessary() {
                if (cache.size > maxSize) {
                    val oldestKey = accessOrder.removeFirstOrNull()
                    oldestKey?.let { cache.remove(it) }
                }
            }
            
            fun stats(): String = "Cache size: ${cache.size}/$maxSize"
        }
        
        val expensiveOperationCache = FlowCache<Int, String>(50)
        
        suspend fun expensiveOperation(input: Int): String {
            delay(100) // Simulate expensive operation
            return "ExpensiveResult-$input"
        }
        
        fun <T, R> Flow<T>.cachedMap(
            cache: FlowCache<T, R>,
            transform: suspend (T) -> R
        ): Flow<R> = flow {
            collect { value ->
                val cached = cache.get(value)
                if (cached != null) {
                    emit(cached)
                } else {
                    val result = transform(value)
                    cache.put(value, result)
                    emit(result)
                }
            }
        }
        
        println("1. Without caching:")
        val noCacheTime = measureTimeMillis {
            flowOf(1, 2, 3, 1, 2, 4, 1, 3, 5)
                .map { expensiveOperation(it) }
                .count()
        }
        println("  No cache time: ${noCacheTime}ms")
        
        println("2. With caching:")
        val cachedTime = measureTimeMillis {
            flowOf(1, 2, 3, 1, 2, 4, 1, 3, 5)
                .cachedMap(expensiveOperationCache) { expensiveOperation(it) }
                .count()
        }
        println("  Cached time: ${cachedTime}ms")
        println("  ${expensiveOperationCache.stats()}")
        
        val cacheSpeedup = noCacheTime.toDouble() / cachedTime
        println("  Cache speedup: ${String.format("%.1f", cacheSpeedup)}x")
        
        println()
        
        // Time-based cache expiration
        println("3. Time-based cache expiration:")
        
        class TimedCache<K, V>(private val ttlMs: Long = 1000) {
            private val cache = mutableMapOf<K, Pair<V, Long>>()
            
            fun get(key: K): V? {
                val entry = cache[key]
                return if (entry != null && System.currentTimeMillis() - entry.second < ttlMs) {
                    entry.first
                } else {
                    cache.remove(key)
                    null
                }
            }
            
            fun put(key: K, value: V) {
                cache[key] = value to System.currentTimeMillis()
            }
            
            fun cleanup() {
                val now = System.currentTimeMillis()
                cache.entries.removeIf { (_, entry) -> now - entry.second >= ttlMs }
            }
        }
        
        val timedCache = TimedCache<String, String>(500)
        
        fun <T, R> Flow<T>.timedCachedMap(
            cache: TimedCache<T, R>,
            transform: suspend (T) -> R
        ): Flow<R> = flow {
            collect { value ->
                val cached = cache.get(value)
                if (cached != null) {
                    emit(cached)
                } else {
                    val result = transform(value)
                    cache.put(value, result)
                    emit(result)
                }
            }
        }
        
        // Test time-based expiration
        flowOf("A", "B", "A", "A")
            .onEach { delay(200) } // Spread out over time
            .timedCachedMap(timedCache) { input ->
                delay(100)
                "Result-$input"
            }
            .collect { result ->
                println("  Timed cache result: $result")
            }
        
        println("Caching strategies completed\n")
    }
    
    fun demonstrateMemoryPooling() = runBlocking {
        println("=== Memory Pooling Optimization ===")
        
        // Object pool for heavy objects
        class ObjectPool<T>(
            private val factory: () -> T,
            private val reset: (T) -> Unit,
            private val maxSize: Int = 10
        ) {
            private val pool = mutableListOf<T>()
            
            fun borrow(): T {
                return if (pool.isNotEmpty()) {
                    pool.removeLastOrNull() ?: factory()
                } else {
                    factory()
                }
            }
            
            fun return(item: T) {
                if (pool.size < maxSize) {
                    reset(item)
                    pool.add(item)
                }
            }
            
            inline fun <R> use(block: (T) -> R): R {
                val item = borrow()
                try {
                    return block(item)
                } finally {
                    return(item)
                }
            }
        }
        
        // Heavy object that we want to pool
        class ProcessingContext {
            private val buffer = StringBuilder(1000)
            private val tempList = mutableListOf<Int>()
            
            fun process(input: Int): String {
                // Use internal buffers for processing
                buffer.clear()
                tempList.clear()
                
                repeat(input % 10) { i ->
                    tempList.add(i * input)
                }
                
                tempList.forEach { 
                    buffer.append(it).append(", ")
                }
                
                return "Processed: ${buffer.toString().dropLast(2)}"
            }
            
            fun reset() {
                buffer.clear()
                tempList.clear()
            }
        }
        
        val contextPool = ObjectPool(
            factory = { ProcessingContext() },
            reset = { it.reset() },
            maxSize = 5
        )
        
        println("1. Without object pooling:")
        val noPoolTime = measureTimeMillis {
            flow {
                repeat(1000) { i ->
                    emit(i)
                }
            }
                .map { input ->
                    val context = ProcessingContext() // New object each time
                    context.process(input)
                }
                .count()
        }
        println("  No pooling time: ${noPoolTime}ms")
        
        println("2. With object pooling:")
        val pooledTime = measureTimeMillis {
            flow {
                repeat(1000) { i ->
                    emit(i)
                }
            }
                .map { input ->
                    contextPool.use { context ->
                        context.process(input)
                    }
                }
                .count()
        }
        println("  Pooled time: ${pooledTime}ms")
        
        val poolSpeedup = noPoolTime.toDouble() / pooledTime
        println("  Pool speedup: ${String.format("%.1f", poolSpeedup)}x")
        
        println("Memory pooling completed\n")
    }
}

/**
 * Advanced benchmarking and profiling
 */
class FlowBenchmarking {
    
    fun demonstrateBenchmarkingPatterns() = runBlocking {
        println("=== Flow Benchmarking Patterns ===")
        
        // Micro-benchmark framework
        class FlowBenchmark {
            private val results = mutableMapOf<String, List<Long>>()
            
            suspend fun <T> benchmark(
                name: String,
                iterations: Int = 5,
                warmupIterations: Int = 2,
                benchmark: suspend () -> T
            ): BenchmarkResult<T> {
                // Warmup
                repeat(warmupIterations) { benchmark() }
                
                // Actual measurements
                val times = mutableListOf<Long>()
                var lastResult: T? = null
                
                repeat(iterations) {
                    val time = measureTimeMillis {
                        lastResult = benchmark()
                    }
                    times.add(time)
                }
                
                results[name] = times
                
                return BenchmarkResult(
                    name = name,
                    times = times,
                    result = lastResult!!
                )
            }
            
            fun printResults() {
                println("Benchmark Results:")
                results.forEach { (name, times) ->
                    val avg = times.average()
                    val min = times.minOrNull() ?: 0
                    val max = times.maxOrNull() ?: 0
                    val stdDev = kotlin.math.sqrt(times.map { (it - avg).let { d -> d * d } }.average())
                    
                    println("  $name: avg=${String.format("%.1f", avg)}ms, " +
                           "min=${min}ms, max=${max}ms, " +
                           "stddev=${String.format("%.1f", stdDev)}ms")
                }
            }
        }
        
        data class BenchmarkResult<T>(
            val name: String,
            val times: List<Long>,
            val result: T
        )
        
        val benchmark = FlowBenchmark()
        val testSize = 10_000
        
        // Benchmark different approaches
        benchmark.benchmark("Sequential Processing") {
            (1..testSize).asFlow()
                .map { it * 2 }
                .filter { it > 1000 }
                .count()
        }
        
        benchmark.benchmark("Fused Operations") {
            (1..testSize).asFlow()
                .transform { value ->
                    val doubled = value * 2
                    if (doubled > 1000) {
                        emit(doubled)
                    }
                }
                .count()
        }
        
        benchmark.benchmark("Parallel Processing") {
            (1..testSize).asFlow()
                .flatMapMerge(concurrency = 4) { value ->
                    flow { 
                        val doubled = value * 2
                        if (doubled > 1000) {
                            emit(doubled)
                        }
                    }
                }
                .count()
        }
        
        benchmark.printResults()
        
        println()
        
        // Memory usage estimation
        println("Memory usage analysis:")
        
        fun estimateMemoryUsage(description: String, objectCount: Int, bytesPerObject: Int) {
            val totalBytes = objectCount * bytesPerObject
            val mb = totalBytes / (1024 * 1024.0)
            println("  $description: $objectCount objects × ${bytesPerObject}B = ${String.format("%.2f", mb)}MB")
        }
        
        estimateMemoryUsage("Small buffer (16 items)", 16, 64)
        estimateMemoryUsage("Medium buffer (256 items)", 256, 64)
        estimateMemoryUsage("Large buffer (1024 items)", 1024, 64)
        estimateMemoryUsage("Cached results (1000 items)", 1000, 128)
        
        println("Benchmarking completed\n")
    }
    
    fun demonstratePerformanceMonitoring() = runBlocking {
        println("=== Performance Monitoring ===")
        
        // Performance monitor for flows
        class FlowPerformanceMonitor {
            private var itemsProcessed = 0L
            private var totalProcessingTime = 0L
            private var startTime = System.currentTimeMillis()
            private val throughputHistory = mutableListOf<Double>()
            
            fun recordProcessing(processingTimeMs: Long) {
                itemsProcessed++
                totalProcessingTime += processingTimeMs
                
                // Calculate throughput every 100 items
                if (itemsProcessed % 100 == 0L) {
                    val currentTime = System.currentTimeMillis()
                    val elapsedSeconds = (currentTime - startTime) / 1000.0
                    val throughput = itemsProcessed / elapsedSeconds
                    throughputHistory.add(throughput)
                    
                    println("  Processed $itemsProcessed items - Throughput: ${String.format("%.1f", throughput)} items/sec")
                }
            }
            
            fun getStats(): String {
                val avgProcessingTime = if (itemsProcessed > 0) totalProcessingTime.toDouble() / itemsProcessed else 0.0
                val avgThroughput = if (throughputHistory.isNotEmpty()) throughputHistory.average() else 0.0
                
                return "Items: $itemsProcessed, Avg processing: ${String.format("%.2f", avgProcessingTime)}ms, " +
                       "Avg throughput: ${String.format("%.1f", avgThroughput)} items/sec"
            }
        }
        
        val monitor = FlowPerformanceMonitor()
        
        println("Performance monitoring during flow processing:")
        
        flow {
            repeat(1000) { i ->
                emit(i)
            }
        }
            .map { value ->
                val startTime = System.nanoTime()
                
                // Simulate variable processing time
                val processingTime = Random.nextLong(1, 20)
                delay(processingTime)
                
                val endTime = System.nanoTime()
                val actualTime = (endTime - startTime) / 1_000_000 // Convert to ms
                monitor.recordProcessing(actualTime)
                
                value * 2
            }
            .count()
        
        println("Final stats: ${monitor.getStats()}")
        
        println()
        
        // Resource usage monitoring
        println("Resource usage monitoring:")
        
        class ResourceMonitor {
            private val runtime = Runtime.getRuntime()
            private var maxMemoryUsed = 0L
            private var measurements = 0
            
            fun measure(): ResourceSnapshot {
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val usedMemory = totalMemory - freeMemory
                
                maxMemoryUsed = maxOf(maxMemoryUsed, usedMemory)
                measurements++
                
                return ResourceSnapshot(
                    usedMemoryMB = usedMemory / (1024 * 1024.0),
                    totalMemoryMB = totalMemory / (1024 * 1024.0),
                    maxUsedMemoryMB = maxMemoryUsed / (1024 * 1024.0)
                )
            }
        }
        
        data class ResourceSnapshot(
            val usedMemoryMB: Double,
            val totalMemoryMB: Double,
            val maxUsedMemoryMB: Double
        )
        
        val resourceMonitor = ResourceMonitor()
        
        // Memory-intensive flow processing
        flow {
            repeat(500) { i ->
                emit(i)
            }
        }
            .map { value ->
                // Create some objects to simulate memory usage
                val largeString = "x".repeat(1000 * value)
                largeString.hashCode()
            }
            .onEach { 
                if (Random.nextBoolean()) {
                    val snapshot = resourceMonitor.measure()
                    println("  Memory: ${String.format("%.1f", snapshot.usedMemoryMB)}MB / ${String.format("%.1f", snapshot.totalMemoryMB)}MB")
                }
            }
            .count()
        
        val finalSnapshot = resourceMonitor.measure()
        println("Max memory used: ${String.format("%.1f", finalSnapshot.maxUsedMemoryMB)}MB")
        
        println("Performance monitoring completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Operator optimization
        FlowOperatorOptimization().demonstrateOperatorFusion()
        FlowOperatorOptimization().demonstrateMemoryOptimization()
        FlowOperatorOptimization().demonstrateContextOptimization()
        
        // Parallel processing
        ParallelFlowOptimization().demonstrateFlatMapConcurrency()
        ParallelFlowOptimization().demonstrateParallelCollectionProcessing()
        ParallelFlowOptimization().demonstratePipelineParallelism()
        
        // Buffer and cache optimization
        BufferCacheOptimization().demonstrateBufferSizeOptimization()
        BufferCacheOptimization().demonstrateCachingStrategies()
        BufferCacheOptimization().demonstrateMemoryPooling()
        
        // Benchmarking
        FlowBenchmarking().demonstrateBenchmarkingPatterns()
        FlowBenchmarking().demonstratePerformanceMonitoring()
        
        println("=== Flow Performance Optimization Summary ===")
        println("✅ Operator Optimization:")
        println("   - Fuse operators to reduce intermediate allocations")
        println("   - Use transform instead of multiple maps")
        println("   - Minimize context switches with strategic flowOn")
        println("   - Avoid unnecessary materialization (toList/toSet)")
        println()
        println("✅ Parallel Processing:")
        println("   - Use flatMapMerge with optimal concurrency levels")
        println("   - Implement work-stealing for load balancing")
        println("   - Design pipeline parallelism with appropriate buffers")
        println("   - Match dispatcher types to operation characteristics")
        println()
        println("✅ Memory Management:")
        println("   - Pool heavy objects to reduce allocations")
        println("   - Use appropriate buffer sizes for memory/performance trade-off")
        println("   - Implement caching for expensive operations")
        println("   - Monitor memory usage during processing")
        println()
        println("✅ Performance Measurement:")
        println("   - Benchmark with proper warmup and iterations")
        println("   - Monitor throughput and latency continuously")
        println("   - Profile memory usage patterns")
        println("   - Validate optimizations with real-world scenarios")
        println()
        println("✅ Best Practices:")
        println("   - Measure before optimizing")
        println("   - Focus on bottlenecks, not micro-optimizations")
        println("   - Consider trade-offs between memory and CPU")
        println("   - Design for expected load patterns")
    }
}