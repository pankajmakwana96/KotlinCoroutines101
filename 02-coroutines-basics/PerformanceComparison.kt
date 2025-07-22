/**
 * # Performance Comparison: Coroutines vs Threads
 * 
 * ## Problem Description
 * Understanding the performance characteristics of coroutines vs traditional
 * threading is crucial for making informed architectural decisions. This
 * comparison covers memory usage, creation overhead, context switching,
 * and scalability limits.
 * 
 * ## Solution Approach
 * Comprehensive benchmarking across multiple dimensions:
 * - Memory consumption per concurrent unit
 * - Creation and startup overhead
 * - Context switching performance
 * - Scalability under load
 * - Resource utilization efficiency
 * 
 * ## Key Learning Points
 * - Coroutines use ~200 bytes vs threads ~1-2MB
 * - Context switching: coroutines ~10ns vs threads ~1-100μs
 * - Scalability: coroutines ~100K+ vs threads ~1K
 * - CPU utilization efficiency differences
 * - Memory pressure and GC impact
 * 
 * ## Performance Considerations
 * - Coroutines excel at I/O-bound workloads
 * - Threads may be better for CPU-bound work with true parallelism
 * - Context switching overhead dominates in high-concurrency scenarios
 * - Memory usage becomes critical at scale
 * 
 * ## Common Pitfalls
 * - Comparing apples to oranges (different workload types)
 * - Not accounting for dispatcher thread pool overhead
 * - Ignoring JVM warmup effects in benchmarks
 * - Missing the cooperative vs preemptive scheduling difference
 * 
 * ## Real-World Applications
 * - High-concurrency web servers
 * - Async I/O processing
 * - Reactive stream processing
 * - Microservice communication
 * - Event-driven architectures
 * 
 * ## Related Concepts
 * - ThreadPools.kt - Traditional thread management
 * - Dispatchers.kt - Coroutine thread management
 * - StructuredConcurrency.kt - Lifecycle management
 */

package coroutines

import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Memory usage comparison
 * 
 * Memory Footprint Visualization:
 * 
 * Thread:    [████████████████████████████████] ~1-2MB
 * Coroutine: [█] ~200 bytes
 * 
 * Ratio: ~5000:1 efficiency gain
 */
class MemoryComparison {
    
    fun compareMemoryUsage() {
        println("=== Memory Usage Comparison ===")
        
        val runtime = Runtime.getRuntime()
        
        // Baseline memory
        System.gc()
        Thread.sleep(100)
        val baselineMemory = runtime.totalMemory() - runtime.freeMemory()
        println("Baseline memory: ${baselineMemory / 1024 / 1024}MB")
        
        // Test thread memory usage
        val threadCount = 1000
        val threads = mutableListOf<Thread>()
        
        try {
            repeat(threadCount) { i ->
                val thread = thread(start = false) {
                    Thread.sleep(10000) // Keep thread alive
                }
                threads.add(thread)
                thread.start()
            }
            
            Thread.sleep(1000) // Let threads start
            System.gc()
            Thread.sleep(100)
            
            val threadMemory = runtime.totalMemory() - runtime.freeMemory()
            val threadOverhead = (threadMemory - baselineMemory) / threadCount
            println("$threadCount threads memory: ${(threadMemory - baselineMemory) / 1024 / 1024}MB")
            println("Memory per thread: ${threadOverhead / 1024}KB")
            
        } finally {
            threads.forEach { it.interrupt() }
        }
        
        // Wait for threads to die
        Thread.sleep(2000)
        System.gc()
        Thread.sleep(100)
        
        // Test coroutine memory usage
        runBlocking {
            val coroutineCount = 100_000
            val jobs = mutableListOf<Job>()
            
            repeat(coroutineCount) {
                val job = launch {
                    delay(10000) // Keep coroutine alive
                }
                jobs.add(job)
            }
            
            delay(1000) // Let coroutines start
            System.gc()
            delay(100)
            
            val coroutineMemory = runtime.totalMemory() - runtime.freeMemory()
            val coroutineOverhead = (coroutineMemory - baselineMemory) / coroutineCount
            println("$coroutineCount coroutines memory: ${(coroutineMemory - baselineMemory) / 1024 / 1024}MB")
            println("Memory per coroutine: ${coroutineOverhead}bytes")
            
            jobs.forEach { it.cancel() }
        }
        
        println("Memory efficiency ratio: ~${1024 * 1024 / 200}:1 (threads:coroutines)")
        println()
    }
    
    fun demonstrateMemoryPressure() = runBlocking {
        println("=== Memory Pressure Test ===")
        
        val runtime = Runtime.getRuntime()
        
        // Test with increasing numbers of concurrent units
        val testSizes = listOf(100, 1000, 10000, 50000)
        
        for (size in testSizes) {
            System.gc()
            delay(100)
            val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
            
            val jobs = (1..size).map {
                launch {
                    delay(1000) // Keep alive briefly
                }
            }
            
            delay(100) // Let them start
            val afterMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryPerCoroutine = (afterMemory - beforeMemory) / size
            
            println("$size coroutines: ${memoryPerCoroutine}bytes per coroutine")
            
            jobs.forEach { it.cancel() }
            delay(100)
        }
        
        println()
    }
}

/**
 * Creation overhead comparison
 */
class CreationOverheadComparison {
    
    fun compareCreationOverhead() {
        println("=== Creation Overhead Comparison ===")
        
        val iterations = 10000
        
        // Thread creation overhead
        val threadTime = measureTimeMillis {
            repeat(iterations) {
                val thread = thread {
                    // Minimal work
                    Thread.sleep(1)
                }
                thread.join()
            }
        }
        
        println("$iterations threads created in: ${threadTime}ms")
        println("Average thread creation: ${threadTime.toDouble() / iterations}ms")
        
        // Coroutine creation overhead
        val coroutineTime = measureTimeMillis {
            runBlocking {
                repeat(iterations) {
                    launch {
                        // Minimal work
                        delay(1)
                    }.join()
                }
            }
        }
        
        println("$iterations coroutines created in: ${coroutineTime}ms")
        println("Average coroutine creation: ${coroutineTime.toDouble() / iterations}ms")
        
        val speedup = threadTime.toDouble() / coroutineTime
        println("Coroutine creation speedup: ${String.format("%.1f", speedup)}x")
        
        println()
    }
    
    fun compareConcurrentCreation() = runBlocking {
        println("=== Concurrent Creation Comparison ===")
        
        val count = 10000
        
        // Concurrent thread creation (limited by thread pool)
        val executor = Executors.newCachedThreadPool()
        val threadTime = measureTimeMillis {
            val futures = (1..count).map {
                CompletableFuture.runAsync({
                    Thread.sleep(10)
                }, executor)
            }
            
            futures.forEach { it.join() }
        }
        executor.shutdown()
        
        println("$count concurrent threads: ${threadTime}ms")
        
        // Concurrent coroutine creation
        val coroutineTime = measureTimeMillis {
            val jobs = (1..count).map {
                async {
                    delay(10)
                }
            }
            
            jobs.awaitAll()
        }
        
        println("$count concurrent coroutines: ${coroutineTime}ms")
        
        val speedup = threadTime.toDouble() / coroutineTime
        println("Concurrent creation speedup: ${String.format("%.1f", speedup)}x")
        
        println()
    }
}

/**
 * Context switching performance
 */
class ContextSwitchingComparison {
    
    fun compareContextSwitching() = runBlocking {
        println("=== Context Switching Comparison ===")
        
        val switches = 100000
        
        // Thread context switching (via thread pools)
        val executor = Executors.newFixedThreadPool(4)
        val threadSwitchTime = measureTimeMillis {
            val futures = (1..switches).map {
                CompletableFuture.supplyAsync({
                    // Minimal work to force context switch
                    it * 2
                }, executor)
            }
            
            futures.forEach { it.join() }
        }
        executor.shutdown()
        
        println("$switches thread context switches: ${threadSwitchTime}ms")
        println("Average thread switch: ${(threadSwitchTime * 1000000.0) / switches}ns")
        
        // Coroutine context switching
        val coroutineSwitchTime = measureTimeMillis {
            val jobs = (1..switches).map {
                async(Dispatchers.Default) {
                    // Minimal work
                    it * 2
                }
            }
            
            jobs.awaitAll()
        }
        
        println("$switches coroutine context switches: ${coroutineSwitchTime}ms")
        println("Average coroutine switch: ${(coroutineSwitchTime * 1000000.0) / switches}ns")
        
        val speedup = threadSwitchTime.toDouble() / coroutineSwitchTime
        println("Context switching speedup: ${String.format("%.1f", speedup)}x")
        
        println()
    }
    
    fun demonstrateDispatcherSwitching() = runBlocking {
        println("=== Dispatcher Switching Performance ===")
        
        val switches = 10000
        
        val switchTime = measureTimeMillis {
            repeat(switches) {
                withContext(Dispatchers.IO) {
                    // Simulate I/O work
                    delay(1)
                }
                
                withContext(Dispatchers.Default) {
                    // Simulate CPU work
                    (1..100).sum()
                }
            }
        }
        
        println("$switches dispatcher switches: ${switchTime}ms")
        println("Average dispatcher switch: ${(switchTime * 1000.0) / switches}μs")
        
        println()
    }
}

/**
 * Scalability comparison
 */
class ScalabilityComparison {
    
    fun compareScalability() {
        println("=== Scalability Comparison ===")
        
        // Test maximum threads (carefully!)
        val maxThreadsToTest = 2000 // Conservative limit
        var maxThreads = 0
        
        try {
            val threads = mutableListOf<Thread>()
            repeat(maxThreadsToTest) { i ->
                try {
                    val thread = thread {
                        Thread.sleep(5000)
                    }
                    threads.add(thread)
                    maxThreads = i + 1
                } catch (e: OutOfMemoryError) {
                    println("Thread creation failed at $i threads")
                    break
                }
            }
            
            println("Successfully created $maxThreads threads")
            threads.forEach { it.interrupt() }
            
        } catch (e: Exception) {
            println("Thread test failed: ${e.message}")
        }
        
        // Test maximum coroutines
        runBlocking {
            val maxCoroutinesToTest = 100_000
            var maxCoroutines = 0
            
            try {
                val jobs = (1..maxCoroutinesToTest).map { i ->
                    try {
                        launch {
                            delay(5000)
                        }.also { maxCoroutines = i }
                    } catch (e: OutOfMemoryError) {
                        println("Coroutine creation failed at $i coroutines")
                        return@runBlocking
                    }
                }
                
                println("Successfully created $maxCoroutines coroutines")
                jobs.forEach { it.cancel() }
                
            } catch (e: Exception) {
                println("Coroutine test failed: ${e.message}")
            }
        }
        
        val scalabilityRatio = if (maxThreads > 0) maxCoroutines.toDouble() / maxThreads else Double.POSITIVE_INFINITY
        println("Scalability ratio: ${String.format("%.1f", scalabilityRatio)}:1 (coroutines:threads)")
        
        println()
    }
    
    fun demonstrateLoadHandling() = runBlocking {
        println("=== Load Handling Comparison ===")
        
        val loadSizes = listOf(100, 1000, 10000)
        
        for (loadSize in loadSizes) {
            println("Testing with $loadSize concurrent operations:")
            
            // Coroutine approach
            val coroutineTime = measureTimeMillis {
                val jobs = (1..loadSize).map {
                    async {
                        // Simulate mixed workload
                        delay(Random.nextLong(10, 50))
                        (1..1000).sum()
                    }
                }
                jobs.awaitAll()
            }
            
            println("  Coroutines: ${coroutineTime}ms")
            
            // Thread pool approach (limited size)
            val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
            val threadTime = measureTimeMillis {
                val futures = (1..loadSize).map {
                    CompletableFuture.supplyAsync({
                        Thread.sleep(Random.nextLong(10, 50))
                        (1..1000).sum()
                    }, executor)
                }
                futures.forEach { it.join() }
            }
            executor.shutdown()
            
            println("  Thread pool: ${threadTime}ms")
            
            val efficiency = threadTime.toDouble() / coroutineTime
            println("  Coroutine efficiency: ${String.format("%.1f", efficiency)}x")
            println()
        }
    }
}

/**
 * I/O bound workload comparison
 */
class IOBoundComparison {
    
    fun compareIOBoundWorkloads() = runBlocking {
        println("=== I/O Bound Workload Comparison ===")
        
        val concurrentOperations = 1000
        val ioDelayMs = 100L
        
        // Simulate I/O bound work with coroutines
        val coroutineTime = measureTimeMillis {
            val jobs = (1..concurrentOperations).map {
                async(Dispatchers.IO) {
                    simulateIOOperation(ioDelayMs)
                }
            }
            jobs.awaitAll()
        }
        
        println("$concurrentOperations I/O operations with coroutines: ${coroutineTime}ms")
        println("Theoretical minimum (if perfectly parallel): ${ioDelayMs}ms")
        println("Coroutine efficiency: ${String.format("%.1f", ioDelayMs.toDouble() / coroutineTime * 100)}%")
        
        // Compare with limited thread pool (realistic scenario)
        val threadPoolSize = 50 // Typical server thread pool size
        val executor = Executors.newFixedThreadPool(threadPoolSize)
        
        val threadTime = measureTimeMillis {
            val futures = (1..concurrentOperations).map {
                CompletableFuture.supplyAsync({
                    simulateIOOperationBlocking(ioDelayMs)
                }, executor)
            }
            futures.forEach { it.join() }
        }
        executor.shutdown()
        
        println("$concurrentOperations I/O operations with $threadPoolSize threads: ${threadTime}ms")
        
        val improvement = threadTime.toDouble() / coroutineTime
        println("Coroutine improvement: ${String.format("%.1f", improvement)}x")
        
        println()
    }
    
    private suspend fun simulateIOOperation(delayMs: Long): String {
        delay(delayMs) // Non-blocking I/O simulation
        return "IO result"
    }
    
    private fun simulateIOOperationBlocking(delayMs: Long): String {
        Thread.sleep(delayMs) // Blocking I/O simulation
        return "IO result"
    }
    
    fun demonstrateIOScaling() = runBlocking {
        println("=== I/O Scaling Demonstration ===")
        
        val operationCounts = listOf(100, 500, 1000, 5000)
        val ioDelay = 50L
        
        for (count in operationCounts) {
            // Coroutine scaling
            val coroutineTime = measureTimeMillis {
                val jobs = (1..count).map {
                    async(Dispatchers.IO) {
                        delay(ioDelay)
                        it
                    }
                }
                jobs.awaitAll()
            }
            
            val efficiency = ioDelay.toDouble() / coroutineTime * 100
            println("$count operations: ${coroutineTime}ms (${String.format("%.1f", efficiency)}% efficiency)")
        }
        
        println()
    }
}

/**
 * CPU bound workload comparison
 */
class CPUBoundComparison {
    
    fun compareCPUBoundWorkloads() = runBlocking {
        println("=== CPU Bound Workload Comparison ===")
        
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val taskCount = cpuCores * 4 // Oversubscribe to test scheduling
        
        println("Testing with $taskCount CPU-bound tasks on $cpuCores cores")
        
        // CPU-bound work with coroutines
        val coroutineTime = measureTimeMillis {
            val jobs = (1..taskCount).map {
                async(Dispatchers.Default) {
                    cpuIntensiveWork()
                }
            }
            jobs.awaitAll()
        }
        
        println("Coroutines (Default dispatcher): ${coroutineTime}ms")
        
        // CPU-bound work with fixed thread pool
        val executor = Executors.newFixedThreadPool(cpuCores)
        val threadTime = measureTimeMillis {
            val futures = (1..taskCount).map {
                CompletableFuture.supplyAsync({
                    cpuIntensiveWork()
                }, executor)
            }
            futures.forEach { it.join() }
        }
        executor.shutdown()
        
        println("Thread pool ($cpuCores threads): ${threadTime}ms")
        
        val comparison = threadTime.toDouble() / coroutineTime
        if (comparison > 1.1) {
            println("Coroutines faster by: ${String.format("%.1f", comparison)}x")
        } else if (comparison < 0.9) {
            println("Threads faster by: ${String.format("%.1f", 1/comparison)}x")
        } else {
            println("Performance is comparable")
        }
        
        println()
    }
    
    private fun cpuIntensiveWork(): Long {
        // Simulate CPU-intensive computation
        var result = 0L
        repeat(1_000_000) { i ->
            result += i * i
        }
        return result
    }
    
    fun demonstrateCPUVsIODispatcher() = runBlocking {
        println("=== CPU vs I/O Dispatcher Performance ===")
        
        val taskCount = 100
        
        // CPU work on Default dispatcher (optimal)
        val defaultTime = measureTimeMillis {
            val jobs = (1..taskCount).map {
                async(Dispatchers.Default) {
                    cpuIntensiveWork()
                }
            }
            jobs.awaitAll()
        }
        
        println("CPU work on Default dispatcher: ${defaultTime}ms")
        
        // CPU work on IO dispatcher (suboptimal)
        val ioTime = measureTimeMillis {
            val jobs = (1..taskCount).map {
                async(Dispatchers.IO) {
                    cpuIntensiveWork()
                }
            }
            jobs.awaitAll()
        }
        
        println("CPU work on IO dispatcher: ${ioTime}ms")
        
        val overhead = (ioTime.toDouble() / defaultTime - 1) * 100
        println("IO dispatcher overhead: ${String.format("%.1f", overhead)}%")
        
        println()
    }
}

/**
 * Resource utilization comparison
 */
class ResourceUtilizationComparison {
    
    fun compareResourceUtilization() = runBlocking {
        println("=== Resource Utilization Comparison ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Mixed workload test
        val ioOperations = 1000
        val cpuOperations = 100
        
        val startTime = System.currentTimeMillis()
        
        // Launch I/O operations
        val ioJobs = (1..ioOperations).map {
            async(Dispatchers.IO) {
                delay(Random.nextLong(50, 150))
                "IO-$it"
            }
        }
        
        // Launch CPU operations
        val cpuJobs = (1..cpuOperations).map {
            async(Dispatchers.Default) {
                val result = (1..100_000).fold(0L) { acc, n -> acc + n }
                "CPU-$it:$result"
            }
        }
        
        // Wait for all operations
        val ioResults = ioJobs.awaitAll()
        val cpuResults = cpuJobs.awaitAll()
        
        val totalTime = System.currentTimeMillis() - startTime
        val peakMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = peakMemory - initialMemory
        
        println("Mixed workload completed in: ${totalTime}ms")
        println("I/O operations: ${ioResults.size}")
        println("CPU operations: ${cpuResults.size}")
        println("Additional memory used: ${memoryUsed / 1024}KB")
        println("Memory per operation: ${memoryUsed / (ioOperations + cpuOperations)}bytes")
        
        println()
    }
    
    fun demonstrateGCPressure() = runBlocking {
        println("=== GC Pressure Comparison ===")
        
        val operations = 10000
        
        // Force GC and measure baseline
        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val beforeGC = runtime.totalMemory() - runtime.freeMemory()
        
        // Run coroutine operations
        val startTime = System.currentTimeMillis()
        
        val jobs = (1..operations).map { i ->
            async {
                // Create some temporary objects
                val data = (1..10).map { "Item-$i-$it" }
                delay(1)
                data.size
            }
        }
        
        val results = jobs.awaitAll()
        val operationTime = System.currentTimeMillis() - startTime
        
        // Measure memory after operations
        val afterOps = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = afterOps - beforeGC
        
        println("$operations coroutine operations: ${operationTime}ms")
        println("Memory increase: ${memoryIncrease / 1024}KB")
        println("Results processed: ${results.sum()}")
        
        // Force GC to see how much was temporary
        System.gc()
        Thread.sleep(100)
        val afterGC = runtime.totalMemory() - runtime.freeMemory()
        val gcReclaimed = afterOps - afterGC
        
        println("Memory reclaimed by GC: ${gcReclaimed / 1024}KB")
        println("GC efficiency: ${String.format("%.1f", gcReclaimed.toDouble() / memoryIncrease * 100)}%")
        
        println()
    }
}

/**
 * Comprehensive benchmark suite
 */
class ComprehensiveBenchmark {
    
    fun runComprehensiveBenchmark() = runBlocking {
        println("=== Comprehensive Performance Benchmark ===")
        
        val testCases = listOf(
            TestCase("Light I/O", 1000, 10),
            TestCase("Heavy I/O", 5000, 100),
            TestCase("Mixed Load", 2000, 50),
            TestCase("CPU Intensive", 500, 0)
        )
        
        for (testCase in testCases) {
            println("${testCase.name}:")
            runTestCase(testCase)
            println()
        }
        
        println("Benchmark completed")
        println()
    }
    
    private suspend fun runTestCase(testCase: TestCase) {
        val coroutineTime = measureTimeMillis {
            val jobs = (1..testCase.operations).map { i ->
                async(if (testCase.ioDelayMs > 0) Dispatchers.IO else Dispatchers.Default) {
                    if (testCase.ioDelayMs > 0) {
                        delay(testCase.ioDelayMs)
                    }
                    // CPU work
                    (1..10000).sum()
                }
            }
            jobs.awaitAll()
        }
        
        println("  Coroutines: ${coroutineTime}ms")
        println("  Throughput: ${testCase.operations * 1000L / coroutineTime} ops/sec")
        
        // Memory usage
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(50)
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val jobs = (1..testCase.operations).map {
            launch {
                if (testCase.ioDelayMs > 0) {
                    delay(testCase.ioDelayMs)
                }
                (1..10000).sum()
            }
        }
        
        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryPerOp = (afterMemory - beforeMemory) / testCase.operations
        
        jobs.forEach { it.cancel() }
        
        println("  Memory per operation: ${memoryPerOp}bytes")
    }
    
    data class TestCase(
        val name: String,
        val operations: Int,
        val ioDelayMs: Long
    )
}

/**
 * Main demonstration function
 */
fun main() {
    println("Starting comprehensive coroutine vs thread performance comparison...")
    println("Note: Results may vary based on hardware and JVM implementation\n")
    
    // Memory comparison
    MemoryComparison().compareMemoryUsage()
    MemoryComparison().demonstrateMemoryPressure()
    
    // Creation overhead
    CreationOverheadComparison().compareCreationOverhead()
    CreationOverheadComparison().compareConcurrentCreation()
    
    // Context switching
    ContextSwitchingComparison().compareContextSwitching()
    ContextSwitchingComparison().demonstrateDispatcherSwitching()
    
    // Scalability
    ScalabilityComparison().compareScalability()
    ScalabilityComparison().demonstrateLoadHandling()
    
    // I/O bound workloads
    IOBoundComparison().compareIOBoundWorkloads()
    IOBoundComparison().demonstrateIOScaling()
    
    // CPU bound workloads
    CPUBoundComparison().compareCPUBoundWorkloads()
    CPUBoundComparison().demonstrateCPUVsIODispatcher()
    
    // Resource utilization
    ResourceUtilizationComparison().compareResourceUtilization()
    ResourceUtilizationComparison().demonstrateGCPressure()
    
    // Comprehensive benchmark
    ComprehensiveBenchmark().runComprehensiveBenchmark()
    
    println("=== Performance Summary ===")
    println("✅ Coroutines excel at:")
    println("   - I/O bound workloads (10-100x better)")
    println("   - High concurrency scenarios (100K+ concurrent operations)")
    println("   - Memory efficiency (~5000x less memory per unit)")
    println("   - Creation speed (~10-100x faster)")
    println("   - Context switching (~1000x faster)")
    println()
    println("⚖️ Threads may be better for:")
    println("   - Pure CPU-bound work with true parallelism needs")
    println("   - Legacy code integration")
    println("   - When you need preemptive scheduling")
    println()
    println("📊 Key Metrics:")
    println("   - Memory: Coroutines ~200B vs Threads ~1-2MB")
    println("   - Context Switch: Coroutines ~10ns vs Threads ~1-100μs")
    println("   - Scalability: Coroutines ~100K vs Threads ~1K")
    println("   - Creation: Coroutines 10-100x faster")
}