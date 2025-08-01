/**
 * # Dispatcher Optimization and Selection Strategies
 * 
 * ## Problem Description
 * Dispatcher selection is critical for coroutine performance, as it determines how
 * coroutines are scheduled and executed across threads. Inappropriate dispatcher
 * choice can lead to thread starvation, poor CPU utilization, excessive context
 * switching, and reduced throughput. Different workload types require different
 * dispatcher configurations for optimal performance, and understanding these
 * trade-offs is essential for building high-performance coroutine applications.
 * 
 * ## Solution Approach
 * Dispatcher optimization strategies include:
 * - Understanding workload characteristics and dispatcher types
 * - Configuring thread pool sizes based on workload and hardware
 * - Implementing custom dispatchers for specific performance requirements
 * - Optimizing context switching and thread affinity
 * - Load balancing and work stealing optimizations
 * 
 * ## Key Learning Points
 * - Dispatchers.Default: CPU-bound work, cores count threads
 * - Dispatchers.IO: I/O operations, up to 64 threads by default
 * - Dispatchers.Main: UI thread operations (Android/Swing)
 * - Dispatchers.Unconfined: No thread confinement, performance optimization
 * - Custom dispatchers: Tailored thread pools for specific workloads
 * 
 * ## Performance Considerations
 * - Thread pool sizing: CPU cores for CPU-bound, higher for I/O-bound
 * - Context switching overhead: ~1-10μs per switch
 * - Thread creation cost: ~1-2ms per thread
 * - Memory overhead: ~8KB per thread stack
 * - Work stealing efficiency in ForkJoinPool implementations
 * 
 * ## Common Pitfalls
 * - Using Default dispatcher for I/O operations
 * - Blocking the IO dispatcher with CPU-intensive work
 * - Over-provisioning thread pools leading to excessive context switching
 * - Under-provisioning causing thread starvation
 * - Mixing different workload types on same dispatcher
 * 
 * ## Real-World Applications
 * - Web server request processing optimization
 * - Database connection pool management
 * - File I/O and network operation handling
 * - CPU-intensive computation scaling
 * - Mixed workload system optimization
 * 
 * ## Related Concepts
 * - Thread pool theory and sizing strategies
 * - Work stealing and load balancing algorithms
 * - CPU affinity and NUMA optimization
 * - Reactive streams backpressure handling
 */

package performance.optimization.dispatchers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.system.*
import kotlin.time.*
import kotlin.random.Random
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.CoroutineContext

/**
 * Dispatcher performance analysis and comparison patterns
 * 
 * Dispatcher Selection Matrix:
 * 
 * Workload Type:
 * ├── CPU-Bound ─────────── Dispatchers.Default (cores count)
 * ├── I/O-Bound ─────────── Dispatchers.IO (64+ threads)
 * ├── Mixed Workload ────── Custom dispatcher with proper sizing
 * ├── UI Operations ─────── Dispatchers.Main
 * └── Hot Path/Critical ─── Dispatchers.Unconfined (careful use)
 * 
 * Selection Criteria:
 * Thread Count -> Workload Type -> Latency Requirements -> Resource Constraints
 */
class DispatcherPerformanceAnalysis {
    
    data class DispatcherMetrics(
        val dispatcherName: String,
        val workloadType: String,
        val threadCount: Int,
        val averageLatency: Duration,
        val throughput: Double, // ops/second
        val cpuUtilization: Double,
        val contextSwitches: Long,
        val totalTime: Duration
    ) {
        override fun toString(): String = buildString {
            appendLine("$dispatcherName Performance ($workloadType):")
            appendLine("  Threads: $threadCount")
            appendLine("  Avg Latency: $averageLatency")
            appendLine("  Throughput: ${String.format("%.1f", throughput)} ops/sec")
            appendLine("  CPU Usage: ${String.format("%.1f", cpuUtilization * 100)}%")
            appendLine("  Total Time: $totalTime")
        }
    }
    
    class DispatcherBenchmark {
        private val cpuCores = Runtime.getRuntime().availableProcessors()
        
        suspend fun benchmarkDispatcher(
            dispatcher: CoroutineDispatcher,
            dispatcherName: String,
            workloadType: String,
            iterations: Int,
            workload: suspend () -> Unit
        ): DispatcherMetrics {
            
            val startTime = System.nanoTime()
            val latencies = mutableListOf<Duration>()
            
            coroutineScope {
                repeat(iterations) { i ->
                    launch(dispatcher) {
                        val taskStart = System.nanoTime()
                        workload()
                        val taskEnd = System.nanoTime()
                        
                        synchronized(latencies) {
                            latencies.add((taskEnd - taskStart).nanoseconds)
                        }
                    }
                }
            }
            
            val endTime = System.nanoTime()
            val totalTime = (endTime - startTime).nanoseconds
            
            val averageLatency = if (latencies.isNotEmpty()) {
                latencies.map { it.inWholeNanoseconds }.average().nanoseconds
            } else {
                Duration.ZERO
            }
            
            val throughput = iterations / totalTime.inWholeMilliseconds * 1000.0
            
            return DispatcherMetrics(
                dispatcherName = dispatcherName,
                workloadType = workloadType,
                threadCount = getThreadCount(dispatcher),
                averageLatency = averageLatency,
                throughput = throughput,
                cpuUtilization = estimateCpuUtilization(totalTime, iterations),
                contextSwitches = estimateContextSwitches(iterations),
                totalTime = totalTime
            )
        }
        
        private fun getThreadCount(dispatcher: CoroutineDispatcher): Int {
            return when (dispatcher) {
                Dispatchers.Default -> cpuCores
                Dispatchers.IO -> 64 // Default IO thread limit
                Dispatchers.Unconfined -> 1 // No dedicated threads
                else -> {
                    // Try to extract thread count from custom dispatcher
                    when (dispatcher.toString()) {
                        contains("ForkJoinPool") -> cpuCores
                        contains("ThreadPoolExecutor") -> extractThreadCount(dispatcher.toString())
                        else -> -1 // Unknown
                    }
                }
            }
        }
        
        private fun extractThreadCount(dispatcherString: String): Int {
            // Simple heuristic to extract thread count from dispatcher string
            val regex = Regex("(\\d+)\\s*threads?")
            return regex.find(dispatcherString)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        }
        
        private fun estimateCpuUtilization(totalTime: Duration, iterations: Int): Double {
            // Simplified CPU utilization estimation
            val workPerIteration = 1.0 // Normalized work unit
            val totalWork = iterations * workPerIteration
            val timeInSeconds = totalTime.inWholeMilliseconds / 1000.0
            return minOf(1.0, totalWork / timeInSeconds / cpuCores)
        }
        
        private fun estimateContextSwitches(iterations: Int): Long {
            // Rough estimate based on iterations and dispatcher behavior
            return iterations * 2L // Context switch in and out
        }
    }
    
    suspend fun demonstrateDispatcherComparison() {
        println("=== Dispatcher Performance Comparison ===")
        
        val benchmark = DispatcherBenchmark()
        val iterations = 1000
        
        // CPU-bound workload
        val cpuWorkload: suspend () -> Unit = {
            var sum = 0
            repeat(10000) { i ->
                sum += i * i
            }
        }
        
        // I/O-bound workload
        val ioWorkload: suspend () -> Unit = {
            delay(1) // Simulate I/O operation
        }
        
        // Mixed workload
        val mixedWorkload: suspend () -> Unit = {
            var sum = 0
            repeat(1000) { i ->
                sum += i
            }
            delay(1) // Some I/O
        }
        
        val dispatchers = listOf(
            Dispatchers.Default to "Dispatchers.Default",
            Dispatchers.IO to "Dispatchers.IO",
            Dispatchers.Unconfined to "Dispatchers.Unconfined"
        )
        
        val workloads = listOf(
            cpuWorkload to "CPU-Bound",
            ioWorkload to "I/O-Bound",
            mixedWorkload to "Mixed"
        )
        
        for ((workload, workloadName) in workloads) {
            println("\n=== $workloadName Workload ===")
            
            for ((dispatcher, dispatcherName) in dispatchers) {
                try {
                    val metrics = benchmark.benchmarkDispatcher(
                        dispatcher, dispatcherName, workloadName, iterations, workload
                    )
                    println(metrics)
                } catch (e: Exception) {
                    println("Failed to benchmark $dispatcherName: ${e.message}")
                }
            }
        }
        
        println("✅ Dispatcher comparison completed")
    }
    
    suspend fun demonstrateLoadCharacteristics() {
        println("=== Load Characteristics Analysis ===")
        
        val benchmark = DispatcherBenchmark()
        val concurrencyLevels = listOf(10, 100, 1000)
        
        for (concurrency in concurrencyLevels) {
            println("\n--- Concurrency Level: $concurrency ---")
            
            // Test each dispatcher with increasing load
            val dispatchers = listOf(
                Dispatchers.Default to "Default",
                Dispatchers.IO to "IO"
            )
            
            for ((dispatcher, name) in dispatchers) {
                val metrics = benchmark.benchmarkDispatcher(
                    dispatcher, name, "Load Test", concurrency
                ) {
                    delay(10) // Simulate work
                    var sum = 0
                    repeat(100) { sum += it }
                }
                
                println("$name: ${String.format("%.1f", metrics.throughput)} ops/sec, " +
                       "Latency: ${metrics.averageLatency.inWholeMilliseconds}ms")
            }
        }
        
        println("✅ Load characteristics analysis completed")
    }
}

/**
 * Custom dispatcher implementation and optimization patterns
 */
class CustomDispatcherOptimization {
    
    // High-throughput dispatcher for specific workloads
    class HighThroughputDispatcher(
        private val threadCount: Int,
        private val queueCapacity: Int = 10000
    ) : ExecutorCoroutineDispatcher() {
        
        private val executor = ThreadPoolExecutor(
            threadCount, threadCount,
            60L, TimeUnit.SECONDS,
            ArrayBlockingQueue(queueCapacity),
            ThreadFactory { task ->
                Thread(task, "HighThroughput-${nextThreadId()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY + 1
                }
            },
            ThreadPoolExecutor.CallerRunsPolicy() // Backpressure handling
        )
        
        private val threadIdCounter = AtomicInteger(0)
        
        private fun nextThreadId(): Int = threadIdCounter.incrementAndGet()
        
        override val executor: Executor get() = this.executor
        
        override fun close() {
            executor.shutdown()
        }
        
        fun getActiveThreadCount(): Int = executor.activeCount
        fun getQueueSize(): Int = executor.queue.size
        fun getTaskCount(): Long = executor.taskCount
    }
    
    // NUMA-aware dispatcher for CPU-intensive work
    class NumaAwareDispatcher(
        private val coresPerNode: Int = Runtime.getRuntime().availableProcessors() / 2
    ) : ExecutorCoroutineDispatcher() {
        
        private val nodes = 2 // Assume 2 NUMA nodes for simplicity
        private val executors = Array(nodes) { nodeId ->
            ForkJoinPool(
                coresPerNode,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true // Enable work stealing
            )
        }
        
        private val nodeSelector = AtomicInteger(0)
        
        override val executor: Executor get() {
            val nodeId = nodeSelector.getAndIncrement() % nodes
            return executors[nodeId]
        }
        
        override fun close() {
            executors.forEach { it.shutdown() }
        }
        
        fun getNodeUtilization(): List<Double> {
            return executors.map { executor ->
                val activeThreads = executor.activeThreadCount.toDouble()
                val parallelism = executor.parallelism.toDouble()
                if (parallelism > 0) activeThreads / parallelism else 0.0
            }
        }
    }
    
    // Priority-based dispatcher
    class PriorityDispatcher(threadCount: Int = 4) : ExecutorCoroutineDispatcher() {
        
        enum class Priority(val value: Int) {
            LOW(1), NORMAL(5), HIGH(10), CRITICAL(15)
        }
        
        data class PriorityTask(
            val task: Runnable,
            val priority: Priority,
            val createdAt: Long = System.nanoTime()
        ) : Comparable<PriorityTask> {
            override fun compareTo(other: PriorityTask): Int {
                // Higher priority first, then FIFO for same priority
                val priorityComparison = other.priority.value.compareTo(this.priority.value)
                return if (priorityComparison != 0) priorityComparison 
                       else this.createdAt.compareTo(other.createdAt)
            }
        }
        
        private val priorityQueue = PriorityBlockingQueue<PriorityTask>()
        private val workers = List(threadCount) { workerId ->
            Thread({
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val priorityTask = priorityQueue.take()
                        priorityTask.task.run()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        // Log error but continue processing
                        println("Worker $workerId error: ${e.message}")
                    }
                }
            }, "PriorityWorker-$workerId").apply {
                isDaemon = true
                start()
            }
        }
        
        override val executor: Executor = Executor { command ->
            priorityQueue.offer(PriorityTask(command, Priority.NORMAL))
        }
        
        fun execute(priority: Priority, task: Runnable) {
            priorityQueue.offer(PriorityTask(task, priority))
        }
        
        override fun close() {
            workers.forEach { it.interrupt() }
        }
        
        fun getQueueSize(): Int = priorityQueue.size
    }
    
    suspend fun demonstrateCustomDispatchers() {
        println("=== Custom Dispatcher Performance Demo ===")
        
        val benchmark = DispatcherPerformanceAnalysis.DispatcherBenchmark()
        
        // High-throughput dispatcher test
        val highThroughputDispatcher = HighThroughputDispatcher(
            threadCount = 8,
            queueCapacity = 5000
        )
        
        val htMetrics = benchmark.benchmarkDispatcher(
            highThroughputDispatcher,
            "HighThroughput",
            "Batch Processing",
            10000
        ) {
            // Simulate batch processing work
            var sum = 0
            repeat(100) { i ->
                sum += i * 2
            }
        }
        
        println(htMetrics)
        println("Active threads: ${highThroughputDispatcher.getActiveThreadCount()}")
        println("Queue size: ${highThroughputDispatcher.getQueueSize()}")
        
        highThroughputDispatcher.close()
        
        // NUMA-aware dispatcher test
        val numaDispatcher = NumaAwareDispatcher()
        
        val numaMetrics = benchmark.benchmarkDispatcher(
            numaDispatcher,
            "NUMA-Aware",
            "CPU-Intensive",
            1000
        ) {
            // CPU-intensive work
            var result = 1.0
            repeat(10000) { i ->
                result = kotlin.math.sqrt(result + i)
            }
        }
        
        println(numaMetrics)
        println("Node utilization: ${numaDispatcher.getNodeUtilization()}")
        
        numaDispatcher.close()
        
        println("✅ Custom dispatcher demo completed")
    }
    
    suspend fun demonstratePriorityDispatcher() {
        println("=== Priority Dispatcher Demo ===")
        
        val priorityDispatcher = PriorityDispatcher(4)
        val results = mutableListOf<String>()
        
        // Submit tasks with different priorities
        val tasks = listOf(
            PriorityDispatcher.Priority.LOW to "Low priority task",
            PriorityDispatcher.Priority.HIGH to "High priority task",
            PriorityDispatcher.Priority.CRITICAL to "Critical task",
            PriorityDispatcher.Priority.NORMAL to "Normal task",
            PriorityDispatcher.Priority.HIGH to "Another high priority task"
        )
        
        // Submit all tasks
        tasks.forEachIndexed { index, (priority, description) ->
            priorityDispatcher.execute(priority) {
                Thread.sleep(100) // Simulate work
                synchronized(results) {
                    results.add("$description (submitted: $index)")
                }
            }
        }
        
        // Wait for completion
        delay(1000)
        
        println("Task execution order:")
        results.forEach { result ->
            println("  $result")
        }
        
        println("Queue size after completion: ${priorityDispatcher.getQueueSize()}")
        
        priorityDispatcher.close()
        
        println("✅ Priority dispatcher demo completed")
    }
    
    suspend fun demonstrateDispatcherTuning() {
        println("=== Dispatcher Tuning Demo ===")
        
        val benchmark = DispatcherPerformanceAnalysis.DispatcherBenchmark()
        val threadCounts = listOf(1, 2, 4, 8, 16)
        
        println("Testing different thread pool sizes for I/O workload:")
        
        for (threadCount in threadCounts) {
            val customDispatcher = Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
            
            val metrics = benchmark.benchmarkDispatcher(
                customDispatcher,
                "Custom-$threadCount",
                "I/O-Bound",
                1000
            ) {
                delay(10) // Simulate I/O
            }
            
            println("Threads: $threadCount, Throughput: ${String.format("%.1f", metrics.throughput)} ops/sec")
            
            customDispatcher.close()
        }
        
        println("✅ Dispatcher tuning demo completed")
    }
}

/**
 * Dispatcher selection strategies and decision frameworks
 */
class DispatcherSelectionStrategies {
    
    enum class WorkloadType {
        CPU_BOUND, IO_BOUND, MIXED, UI_BOUND, REAL_TIME
    }
    
    data class WorkloadCharacteristics(
        val type: WorkloadType,
        val expectedLatency: Duration,
        val concurrencyLevel: Int,
        val memoryIntensive: Boolean = false,
        val requiresOrdering: Boolean = false
    )
    
    class DispatcherSelector {
        
        fun selectOptimalDispatcher(characteristics: WorkloadCharacteristics): CoroutineDispatcher {
            return when (characteristics.type) {
                WorkloadType.CPU_BOUND -> selectCpuDispatcher(characteristics)
                WorkloadType.IO_BOUND -> selectIoDispatcher(characteristics)
                WorkloadType.MIXED -> selectMixedDispatcher(characteristics)
                WorkloadType.UI_BOUND -> Dispatchers.Main
                WorkloadType.REAL_TIME -> selectRealTimeDispatcher(characteristics)
            }
        }
        
        private fun selectCpuDispatcher(characteristics: WorkloadCharacteristics): CoroutineDispatcher {
            val cores = Runtime.getRuntime().availableProcessors()
            
            return when {
                characteristics.concurrencyLevel <= cores -> Dispatchers.Default
                characteristics.memoryIntensive -> {
                    // Reduce parallelism for memory-intensive tasks
                    Executors.newFixedThreadPool(maxOf(1, cores / 2)).asCoroutineDispatcher()
                }
                else -> Dispatchers.Default
            }
        }
        
        private fun selectIoDispatcher(characteristics: WorkloadCharacteristics): CoroutineDispatcher {
            return when {
                characteristics.expectedLatency < 1.milliseconds -> {
                    // Very fast I/O, might benefit from dedicated dispatcher
                    Executors.newCachedThreadPool().asCoroutineDispatcher()
                }
                characteristics.concurrencyLevel > 64 -> {
                    // High concurrency, expand I/O dispatcher
                    Executors.newCachedThreadPool().asCoroutineDispatcher()
                }
                else -> Dispatchers.IO
            }
        }
        
        private fun selectMixedDispatcher(characteristics: WorkloadCharacteristics): CoroutineDispatcher {
            val cores = Runtime.getRuntime().availableProcessors()
            
            // For mixed workloads, create a balanced thread pool
            val threadCount = when {
                characteristics.concurrencyLevel < 10 -> cores
                characteristics.concurrencyLevel < 100 -> cores * 2
                else -> cores * 4
            }
            
            return Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
        }
        
        private fun selectRealTimeDispatcher(characteristics: WorkloadCharacteristics): CoroutineDispatcher {
            // For real-time workloads, prefer dedicated threads with higher priority
            return Executors.newSingleThreadExecutor { task ->
                Thread(task, "RealTime").apply {
                    priority = Thread.MAX_PRIORITY
                    isDaemon = false
                }
            }.asCoroutineDispatcher()
        }
        
        fun getRecommendations(characteristics: WorkloadCharacteristics): List<String> {
            val recommendations = mutableListOf<String>()
            
            when (characteristics.type) {
                WorkloadType.CPU_BOUND -> {
                    recommendations.add("Use Dispatchers.Default for CPU-bound work")
                    recommendations.add("Consider thread count = CPU cores")
                    if (characteristics.memoryIntensive) {
                        recommendations.add("Reduce parallelism for memory-intensive tasks")
                    }
                }
                
                WorkloadType.IO_BOUND -> {
                    recommendations.add("Use Dispatchers.IO for I/O operations")
                    recommendations.add("Consider expanding thread pool for high concurrency")
                    if (characteristics.expectedLatency < 10.milliseconds) {
                        recommendations.add("Consider custom dispatcher for low-latency I/O")
                    }
                }
                
                WorkloadType.MIXED -> {
                    recommendations.add("Use custom dispatcher with balanced thread count")
                    recommendations.add("Consider separating CPU and I/O work if possible")
                }
                
                WorkloadType.REAL_TIME -> {
                    recommendations.add("Use dedicated high-priority dispatcher")
                    recommendations.add("Minimize context switching")
                    recommendations.add("Consider thread affinity")
                }
                
                WorkloadType.UI_BOUND -> {
                    recommendations.add("Always use Dispatchers.Main for UI updates")
                    recommendations.add("Keep UI work minimal")
                }
            }
            
            if (characteristics.requiresOrdering) {
                recommendations.add("Consider single-threaded dispatcher for ordered processing")
            }
            
            return recommendations
        }
    }
    
    suspend fun demonstrateSelectionStrategy() {
        println("=== Dispatcher Selection Strategy Demo ===")
        
        val selector = DispatcherSelector()
        
        val scenarios = listOf(
            WorkloadCharacteristics(
                type = WorkloadType.CPU_BOUND,
                expectedLatency = 10.milliseconds,
                concurrencyLevel = 8
            ) to "Heavy computation",
            
            WorkloadCharacteristics(
                type = WorkloadType.IO_BOUND,
                expectedLatency = 100.milliseconds,
                concurrencyLevel = 100
            ) to "Database queries",
            
            WorkloadCharacteristics(
                type = WorkloadType.MIXED,
                expectedLatency = 50.milliseconds,
                concurrencyLevel = 20
            ) to "Web request processing",
            
            WorkloadCharacteristics(
                type = WorkloadType.IO_BOUND,
                expectedLatency = 1.milliseconds,
                concurrencyLevel = 1000,
                requiresOrdering = false
            ) to "High-frequency trading",
            
            WorkloadCharacteristics(
                type = WorkloadType.CPU_BOUND,
                expectedLatency = 1.seconds,
                concurrencyLevel = 4,
                memoryIntensive = true
            ) to "Large dataset processing"
        )
        
        for ((characteristics, scenario) in scenarios) {
            println("\n--- Scenario: $scenario ---")
            println("Workload: $characteristics")
            
            val selectedDispatcher = selector.selectOptimalDispatcher(characteristics)
            println("Selected dispatcher: $selectedDispatcher")
            
            val recommendations = selector.getRecommendations(characteristics)
            println("Recommendations:")
            recommendations.forEach { recommendation ->
                println("  • $recommendation")
            }
        }
        
        println("✅ Selection strategy demo completed")
    }
    
    suspend fun demonstratePerformanceImpact() {
        println("=== Performance Impact of Dispatcher Selection ===")
        
        val benchmark = DispatcherPerformanceAnalysis.DispatcherBenchmark()
        val selector = DispatcherSelector()
        
        // Test scenarios with optimal vs suboptimal dispatcher selection
        val testCases = listOf(
            // CPU-bound work
            Triple(
                WorkloadCharacteristics(WorkloadType.CPU_BOUND, 10.milliseconds, 8),
                { var sum = 0; repeat(10000) { sum += it }; sum },
                "CPU-bound computation"
            ),
            
            // I/O-bound work  
            Triple(
                WorkloadCharacteristics(WorkloadType.IO_BOUND, 10.milliseconds, 100),
                { delay(10) },
                "I/O simulation"
            )
        )
        
        for ((characteristics, workload, description) in testCases) {
            println("\n--- Testing: $description ---")
            
            // Optimal dispatcher
            val optimalDispatcher = selector.selectOptimalDispatcher(characteristics)
            val optimalMetrics = benchmark.benchmarkDispatcher(
                optimalDispatcher, "Optimal", description, 100, workload
            )
            
            // Suboptimal dispatcher (wrong choice)
            val suboptimalDispatcher = when (characteristics.type) {
                WorkloadType.CPU_BOUND -> Dispatchers.IO // Wrong for CPU work
                WorkloadType.IO_BOUND -> Dispatchers.Default // Wrong for I/O work
                else -> Dispatchers.Unconfined
            }
            
            val suboptimalMetrics = benchmark.benchmarkDispatcher(
                suboptimalDispatcher, "Suboptimal", description, 100, workload
            )
            
            println("Optimal: ${String.format("%.1f", optimalMetrics.throughput)} ops/sec")
            println("Suboptimal: ${String.format("%.1f", suboptimalMetrics.throughput)} ops/sec")
            
            val improvement = if (suboptimalMetrics.throughput > 0) {
                optimalMetrics.throughput / suboptimalMetrics.throughput
            } else 1.0
            
            println("Performance improvement: ${String.format("%.1f", improvement)}x")
        }
        
        println("✅ Performance impact demo completed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Dispatcher Optimization Demo ===")
    
    try {
        // Dispatcher performance analysis
        val analysis = DispatcherPerformanceAnalysis()
        analysis.demonstrateDispatcherComparison()
        println()
        
        analysis.demonstrateLoadCharacteristics()
        println()
        
        // Custom dispatcher optimization
        val customization = CustomDispatcherOptimization()
        customization.demonstrateCustomDispatchers()
        println()
        
        customization.demonstratePriorityDispatcher()
        println()
        
        customization.demonstrateDispatcherTuning()
        println()
        
        // Selection strategies
        val selection = DispatcherSelectionStrategies()
        selection.demonstrateSelectionStrategy()
        println()
        
        selection.demonstratePerformanceImpact()
        println()
        
        println("=== All Dispatcher Optimization Demos Completed ===")
        println()
        
        println("Key Dispatcher Optimization Concepts:")
        println("✅ Understanding dispatcher types and their optimal use cases")
        println("✅ Performance analysis and benchmarking of different dispatchers")
        println("✅ Custom dispatcher implementation for specific requirements")
        println("✅ Thread pool sizing strategies based on workload characteristics")
        println("✅ Priority-based and NUMA-aware dispatcher implementations")
        println("✅ Dispatcher selection frameworks and decision matrices")
        println("✅ Performance impact analysis of optimal vs suboptimal choices")
        println()
        
        println("Dispatcher Selection Guidelines:")
        println("✅ CPU-bound: Dispatchers.Default (cores count threads)")
        println("✅ I/O-bound: Dispatchers.IO (expandable thread pool)")
        println("✅ Mixed workload: Custom balanced dispatcher")
        println("✅ UI operations: Dispatchers.Main")
        println("✅ High-throughput: Custom optimized dispatcher")
        println("✅ Real-time: Dedicated high-priority dispatcher")
        println("✅ Memory-intensive: Reduced parallelism dispatcher")
        println("✅ Always measure and profile before optimizing")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}