/**
 * # Performance Profiling and Monitoring for Coroutines
 * 
 * ## Problem Description
 * Effective performance profiling and monitoring of coroutine applications requires
 * specialized tools and techniques that can handle the unique characteristics of
 * asynchronous, concurrent execution. Traditional profiling approaches often fail
 * to provide meaningful insights into coroutine performance due to context switching,
 * suspension points, and distributed execution across multiple threads. Comprehensive
 * monitoring must track metrics like coroutine lifecycle, suspension time, throughput,
 * latency, and resource utilization patterns.
 * 
 * ## Solution Approach
 * Performance profiling strategies include:
 * - Implementing custom metrics collection for coroutine-specific measurements
 * - Using statistical sampling and low-overhead profiling techniques
 * - Building real-time monitoring dashboards with actionable alerts
 * - Integrating with APM tools and distributed tracing systems
 * - Creating automated performance regression detection systems
 * 
 * ## Key Learning Points
 * - Coroutine-aware profiling requires tracking suspension and resumption
 * - Statistical sampling reduces profiling overhead while maintaining accuracy
 * - Real-time metrics enable proactive performance issue detection
 * - Integration with distributed tracing provides end-to-end visibility
 * - Automated alerts prevent performance degradation in production
 * 
 * ## Performance Considerations
 * - Profiling overhead should be <1% of application CPU usage
 * - Metric collection frequency vs accuracy trade-offs
 * - Memory overhead of metrics storage and aggregation
 * - Network overhead for distributed tracing and monitoring
 * - Storage requirements for long-term performance trend analysis
 * 
 * ## Common Pitfalls
 * - High-overhead profiling that affects application performance
 * - Insufficient metrics granularity for effective debugging
 * - Missing correlation between different performance metrics
 * - Inadequate alerting leading to undetected performance issues
 * - Profiling in development vs production environment differences
 * 
 * ## Real-World Applications
 * - Production monitoring of microservices architectures
 * - Performance regression testing in CI/CD pipelines
 * - Capacity planning and scaling decisions
 * - SLA monitoring and performance optimization
 * - Troubleshooting performance incidents and bottlenecks
 * 
 * ## Related Concepts
 * - Application Performance Monitoring (APM) integration
 * - Distributed tracing and observability patterns
 * - Statistical profiling and sampling techniques
 * - Time-series database optimization for metrics storage
 */

package performance.optimization.profiling

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.system.*
import kotlin.time.*
import kotlin.random.Random
import java.lang.management.ManagementFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

/**
 * Coroutine-specific metrics collection and analysis
 * 
 * Profiling Architecture:
 * 
 * Metrics Collection Layers:
 * ├── Application Metrics ─── Business logic performance measurements
 * ├── Coroutine Metrics ──── Suspension, resumption, lifecycle tracking
 * ├── System Metrics ─────── CPU, memory, GC, thread utilization
 * ├── Infrastructure ────── Network, disk, database performance
 * └── Real-time Alerting ── Threshold monitoring and notification
 * 
 * Collection Strategy:
 * Low Overhead -> Statistical Sampling -> Real-time Analysis -> Historical Storage
 */
class CoroutineMetricsCollector {
    
    // Comprehensive coroutine performance metrics
    data class CoroutineMetrics(
        val coroutineId: String,
        val creationTime: Long,
        val startTime: Long,
        val endTime: Long,
        val suspensionCount: Int,
        val suspensionDuration: Duration,
        val executionDuration: Duration,
        val dispatcherName: String,
        val errorCount: Int = 0,
        val cancellationReason: String? = null
    ) {
        val totalDuration: Duration get() = (endTime - creationTime).milliseconds
        val activeRatio: Double get() = executionDuration.inWholeNanoseconds.toDouble() / totalDuration.inWholeNanoseconds
    }
    
    // Real-time metrics aggregator
    class MetricsAggregator {
        private val metrics = ConcurrentHashMap<String, CoroutineMetrics>()
        private val completedMetrics = mutableListOf<CoroutineMetrics>()
        private val lock = Any()
        
        // Performance counters
        private val coroutinesCreated = AtomicLong(0)
        private val coroutinesCompleted = AtomicLong(0)
        private val coroutinesCancelled = AtomicLong(0)
        private val totalSuspensions = AtomicLong(0)
        private val totalExecutionTime = AtomicLong(0)
        
        fun recordCoroutineStart(
            coroutineId: String,
            dispatcherName: String
        ) {
            val currentTime = System.nanoTime()
            val metric = CoroutineMetrics(
                coroutineId = coroutineId,
                creationTime = currentTime,
                startTime = currentTime,
                endTime = 0,
                suspensionCount = 0,
                suspensionDuration = Duration.ZERO,
                executionDuration = Duration.ZERO,
                dispatcherName = dispatcherName
            )
            
            metrics[coroutineId] = metric
            coroutinesCreated.incrementAndGet()
        }
        
        fun recordSuspension(coroutineId: String, duration: Duration) {
            metrics[coroutineId]?.let { current ->
                val updated = current.copy(
                    suspensionCount = current.suspensionCount + 1,
                    suspensionDuration = current.suspensionDuration + duration
                )
                metrics[coroutineId] = updated
                totalSuspensions.incrementAndGet()
            }
        }
        
        fun recordCoroutineCompletion(
            coroutineId: String,
            executionDuration: Duration,
            cancelled: Boolean = false,
            cancellationReason: String? = null
        ) {
            metrics.remove(coroutineId)?.let { metric ->
                val completedMetric = metric.copy(
                    endTime = System.nanoTime(),
                    executionDuration = executionDuration,
                    cancellationReason = if (cancelled) cancellationReason else null
                )
                
                synchronized(lock) {
                    completedMetrics.add(completedMetric)
                }
                
                if (cancelled) {
                    coroutinesCancelled.incrementAndGet()
                } else {
                    coroutinesCompleted.incrementAndGet()
                    totalExecutionTime.addAndGet(executionDuration.inWholeNanoseconds)
                }
            }
        }
        
        fun getAggregatedMetrics(): AggregatedMetrics {
            synchronized(lock) {
                val completed = completedMetrics.toList()
                
                return AggregatedMetrics(
                    totalCoroutines = coroutinesCreated.get(),
                    activeCoroutines = metrics.size,
                    completedCoroutines = coroutinesCompleted.get(),
                    cancelledCoroutines = coroutinesCancelled.get(),
                    averageExecutionTime = if (completed.isNotEmpty()) {
                        completed.map { it.executionDuration.inWholeNanoseconds }.average().nanoseconds
                    } else Duration.ZERO,
                    averageSuspensionCount = if (completed.isNotEmpty()) {
                        completed.map { it.suspensionCount }.average()
                    } else 0.0,
                    totalSuspensions = totalSuspensions.get(),
                    completionRate = if (coroutinesCreated.get() > 0) {
                        coroutinesCompleted.get().toDouble() / coroutinesCreated.get()
                    } else 0.0
                )
            }
        }
        
        fun getDetailedMetrics(): List<CoroutineMetrics> {
            synchronized(lock) {
                return completedMetrics.toList()
            }
        }
        
        fun reset() {
            metrics.clear()
            synchronized(lock) {
                completedMetrics.clear()
            }
            coroutinesCreated.set(0)
            coroutinesCompleted.set(0)
            coroutinesCancelled.set(0)
            totalSuspensions.set(0)
            totalExecutionTime.set(0)
        }
    }
    
    data class AggregatedMetrics(
        val totalCoroutines: Long,
        val activeCoroutines: Int,
        val completedCoroutines: Long,
        val cancelledCoroutines: Long,
        val averageExecutionTime: Duration,
        val averageSuspensionCount: Double,
        val totalSuspensions: Long,
        val completionRate: Double
    ) {
        override fun toString(): String = buildString {
            appendLine("=== Coroutine Metrics Summary ===")
            appendLine("Total Coroutines: $totalCoroutines")
            appendLine("Active: $activeCoroutines, Completed: $completedCoroutines, Cancelled: $cancelledCoroutines")
            appendLine("Completion Rate: ${String.format("%.1f", completionRate * 100)}%")
            appendLine("Average Execution Time: $averageExecutionTime")
            appendLine("Average Suspensions: ${String.format("%.1f", averageSuspensionCount)}")
            appendLine("Total Suspensions: $totalSuspensions")
        }
    }
    
    // Instrumented coroutine wrapper
    suspend fun <T> measureCoroutine(
        id: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        aggregator: MetricsAggregator,
        operation: suspend () -> T
    ): T = withContext(dispatcher) {
        aggregator.recordCoroutineStart(id, dispatcher.toString())
        
        val executionStart = System.nanoTime()
        var suspensionTime = 0L
        
        try {
            val result = operation()
            val executionEnd = System.nanoTime()
            
            val executionDuration = (executionEnd - executionStart - suspensionTime).nanoseconds
            aggregator.recordCoroutineCompletion(id, executionDuration)
            
            result
        } catch (e: CancellationException) {
            val executionEnd = System.nanoTime()
            val executionDuration = (executionEnd - executionStart - suspensionTime).nanoseconds
            aggregator.recordCoroutineCompletion(id, executionDuration, cancelled = true, cancellationReason = e.message)
            throw e
        } catch (e: Exception) {
            val executionEnd = System.nanoTime()
            val executionDuration = (executionEnd - executionStart - suspensionTime).nanoseconds
            aggregator.recordCoroutineCompletion(id, executionDuration)
            throw e
        }
    }
    
    suspend fun demonstrateMetricsCollection() {
        println("=== Coroutine Metrics Collection Demo ===")
        
        val aggregator = MetricsAggregator()
        val workloads = 100
        
        // Simulate various coroutine workloads
        coroutineScope {
            repeat(workloads) { i ->
                launch {
                    measureCoroutine("coroutine-$i", Dispatchers.Default, aggregator) {
                        // Simulate different types of work
                        when (i % 4) {
                            0 -> {
                                // CPU-bound work
                                var sum = 0
                                repeat(10000) { j ->
                                    sum += j
                                }
                            }
                            1 -> {
                                // I/O-bound work with suspension
                                delay(Random.nextLong(10, 100))
                            }
                            2 -> {
                                // Mixed work
                                var sum = 0
                                repeat(1000) { j -> sum += j }
                                delay(Random.nextLong(5, 50))
                            }
                            3 -> {
                                // Quick work
                                delay(Random.nextLong(1, 10))
                            }
                        }
                        
                        // Occasionally cancel some coroutines
                        if (Random.nextDouble() < 0.1) {
                            throw CancellationException("Simulated cancellation")
                        }
                    }
                }
            }
        }
        
        val metrics = aggregator.getAggregatedMetrics()
        println(metrics)
        
        // Show detailed analysis
        val detailedMetrics = aggregator.getDetailedMetrics()
        val fastestExecution = detailedMetrics.minByOrNull { it.executionDuration }
        val slowestExecution = detailedMetrics.maxByOrNull { it.executionDuration }
        val mostSuspensions = detailedMetrics.maxByOrNull { it.suspensionCount }
        
        println("\nDetailed Analysis:")
        println("Fastest execution: ${fastestExecution?.executionDuration} (${fastestExecution?.coroutineId})")
        println("Slowest execution: ${slowestExecution?.executionDuration} (${slowestExecution?.coroutineId})")
        println("Most suspensions: ${mostSuspensions?.suspensionCount} (${mostSuspensions?.coroutineId})")
        
        println("✅ Metrics collection demo completed")
    }
}

/**
 * Statistical sampling profiler for low-overhead monitoring
 */
class StatisticalProfiler {
    
    data class ProfileSample(
        val timestamp: Long,
        val threadName: String,
        val stackTrace: List<String>,
        val coroutineId: String?,
        val state: String
    )
    
    // Low-overhead sampling profiler
    class SamplingProfiler(
        private val samplingInterval: Duration = 100.milliseconds,
        private val maxSamples: Int = 10000
    ) {
        private val samples = ConcurrentLinkedQueue<ProfileSample>()
        private val profilerJob = AtomicReference<Job?>(null)
        private val sampleCount = AtomicLong(0)
        
        fun startProfiling(scope: CoroutineScope) {
            val job = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    collectSample()
                    delay(samplingInterval)
                }
            }
            profilerJob.set(job)
        }
        
        fun stopProfiling() {
            profilerJob.get()?.cancel()
        }
        
        private fun collectSample() {
            val timestamp = System.nanoTime()
            
            // Sample all threads
            Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
                val sample = ProfileSample(
                    timestamp = timestamp,
                    threadName = thread.name,
                    stackTrace = stackTrace.take(10).map { it.toString() },
                    coroutineId = extractCoroutineId(stackTrace),
                    state = thread.state.name
                )
                
                // Maintain sample size limit
                if (samples.size >= maxSamples) {
                    samples.poll()
                }
                
                samples.offer(sample)
                sampleCount.incrementAndGet()
            }
        }
        
        private fun extractCoroutineId(stackTrace: Array<StackTraceElement>): String? {
            // Simple heuristic to identify coroutine-related stack frames
            return stackTrace.find { frame ->
                frame.className.contains("Coroutine") || 
                frame.methodName.contains("coroutine") ||
                frame.className.contains("kotlinx.coroutines")
            }?.let { "coroutine-${it.lineNumber}" }
        }
        
        fun getHotSpots(): List<HotSpot> {
            val methodCounts = mutableMapOf<String, Long>()
            val coroutineCounts = mutableMapOf<String, Long>()
            
            samples.forEach { sample ->
                sample.stackTrace.forEach { frame ->
                    methodCounts[frame] = methodCounts.getOrDefault(frame, 0) + 1
                }
                
                sample.coroutineId?.let { id ->
                    coroutineCounts[id] = coroutineCounts.getOrDefault(id, 0) + 1
                }
            }
            
            val topMethods = methodCounts.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { HotSpot(it.key, it.value, "method") }
            
            val topCoroutines = coroutineCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { HotSpot(it.key, it.value, "coroutine") }
            
            return topMethods + topCoroutines
        }
        
        fun getProfilingStats(): ProfilingStats {
            return ProfilingStats(
                totalSamples = sampleCount.get(),
                activeSamples = samples.size,
                samplingRate = samplingInterval,
                uniqueThreads = samples.distinctBy { it.threadName }.size,
                uniqueCoroutines = samples.mapNotNull { it.coroutineId }.distinct().size
            )
        }
    }
    
    data class HotSpot(
        val location: String,
        val sampleCount: Long,
        val type: String
    )
    
    data class ProfilingStats(
        val totalSamples: Long,
        val activeSamples: Int,
        val samplingRate: Duration,
        val uniqueThreads: Int,
        val uniqueCoroutines: Int
    )
    
    suspend fun demonstrateStatisticalProfiling() {
        println("=== Statistical Profiling Demo ===")
        
        val profiler = SamplingProfiler(samplingInterval = 50.milliseconds)
        val profilingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        profiler.startProfiling(profilingScope)
        
        // Generate load for profiling
        coroutineScope {
            // CPU-intensive workload
            repeat(5) { workerId ->
                launch(Dispatchers.Default) {
                    repeat(1000) { i ->
                        var result = 0.0
                        repeat(10000) { j ->
                            result += sqrt(j.toDouble()) * sin(i.toDouble())
                        }
                        if (i % 100 == 0) yield()
                    }
                }
            }
            
            // I/O-intensive workload
            repeat(10) { workerId ->
                launch(Dispatchers.IO) {
                    repeat(100) {
                        delay(Random.nextLong(10, 100))
                        // Simulate file I/O
                        val data = ByteArray(1024)
                        Random.nextBytes(data)
                    }
                }
            }
            
            // Mixed workload
            repeat(3) { workerId ->
                launch {
                    repeat(500) { i ->
                        // Some CPU work
                        var sum = 0
                        repeat(1000) { sum += it }
                        
                        // Some I/O
                        delay(5)
                    }
                }
            }
        }
        
        // Let profiling run for a bit more
        delay(1000)
        
        profiler.stopProfiling()
        profilingScope.cancel()
        
        val hotSpots = profiler.getHotSpots()
        val stats = profiler.getProfilingStats()
        
        println("Profiling Statistics:")
        println("Total samples: ${stats.totalSamples}")
        println("Active samples: ${stats.activeSamples}")
        println("Sampling rate: ${stats.samplingRate}")
        println("Unique threads: ${stats.uniqueThreads}")
        println("Unique coroutines: ${stats.uniqueCoroutines}")
        
        println("\nTop Hot Spots:")
        hotSpots.forEach { hotSpot ->
            println("${hotSpot.type}: ${hotSpot.location} (${hotSpot.sampleCount} samples)")
        }
        
        println("✅ Statistical profiling demo completed")
    }
}

/**
 * Real-time performance monitoring and alerting system
 */
class RealTimeMonitoring {
    
    // Performance threshold configuration
    data class PerformanceThresholds(
        val maxLatency: Duration = 1.seconds,
        val minThroughput: Double = 100.0, // ops/second
        val maxErrorRate: Double = 0.05, // 5%
        val maxMemoryUtilization: Double = 0.8, // 80%
        val maxCpuUtilization: Double = 0.9 // 90%
    )
    
    // Real-time metrics snapshot
    data class MetricsSnapshot(
        val timestamp: Long,
        val latency: Duration,
        val throughput: Double,
        val errorRate: Double,
        val memoryUtilization: Double,
        val cpuUtilization: Double,
        val activeCoroutines: Int,
        val queuedTasks: Int
    )
    
    // Alert definitions
    sealed class Alert {
        data class LatencyAlert(val current: Duration, val threshold: Duration) : Alert()
        data class ThroughputAlert(val current: Double, val threshold: Double) : Alert()
        data class ErrorRateAlert(val current: Double, val threshold: Double) : Alert()
        data class ResourceAlert(val resource: String, val current: Double, val threshold: Double) : Alert()
    }
    
    class PerformanceMonitor(
        private val thresholds: PerformanceThresholds = PerformanceThresholds(),
        private val monitoringInterval: Duration = 1.seconds
    ) {
        private val metrics = mutableListOf<MetricsSnapshot>()
        private val alerts = Channel<Alert>(100)
        private val operationCounts = AtomicLong(0)
        private val errorCounts = AtomicLong(0)
        private val latencyMeasurements = mutableListOf<Duration>()
        private val latencyLock = Any()
        
        private val runtime = Runtime.getRuntime()
        private val osBean = ManagementFactory.getOperatingSystemMXBean()
        
        suspend fun startMonitoring(scope: CoroutineScope) {
            scope.launch {
                while (isActive) {
                    collectMetrics()
                    delay(monitoringInterval)
                }
            }
            
            // Alert processor
            scope.launch {
                for (alert in alerts) {
                    processAlert(alert)
                }
            }
        }
        
        private suspend fun collectMetrics() {
            val currentTime = System.currentTimeMillis()
            
            // Calculate current throughput (ops/second)
            val totalOps = operationCounts.get()
            val timeWindow = 1.0 // 1 second window
            val throughput = totalOps / timeWindow
            
            // Calculate error rate
            val totalErrors = errorCounts.get()
            val errorRate = if (totalOps > 0) totalErrors.toDouble() / totalOps else 0.0
            
            // Calculate average latency
            val avgLatency = synchronized(latencyLock) {
                if (latencyMeasurements.isNotEmpty()) {
                    val avg = latencyMeasurements.map { it.inWholeNanoseconds }.average().nanoseconds
                    latencyMeasurements.clear()
                    avg
                } else {
                    Duration.ZERO
                }
            }
            
            // System resource utilization
            val memoryUtilization = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / runtime.totalMemory()
            val cpuUtilization = osBean.systemLoadAverage / runtime.availableProcessors()
            
            val snapshot = MetricsSnapshot(
                timestamp = currentTime,
                latency = avgLatency,
                throughput = throughput,
                errorRate = errorRate,
                memoryUtilization = memoryUtilization,
                cpuUtilization = maxOf(0.0, cpuUtilization), // Handle negative values
                activeCoroutines = estimateActiveCoroutines(),
                queuedTasks = 0 // Would need access to actual queue
            )
            
            metrics.add(snapshot)
            
            // Keep only recent metrics
            if (metrics.size > 100) {
                metrics.removeAt(0)
            }
            
            // Check thresholds and generate alerts
            checkThresholds(snapshot)
            
            // Reset counters for next window
            operationCounts.set(0)
            errorCounts.set(0)
        }
        
        private suspend fun checkThresholds(snapshot: MetricsSnapshot) {
            if (snapshot.latency > thresholds.maxLatency) {
                alerts.send(Alert.LatencyAlert(snapshot.latency, thresholds.maxLatency))
            }
            
            if (snapshot.throughput < thresholds.minThroughput) {
                alerts.send(Alert.ThroughputAlert(snapshot.throughput, thresholds.minThroughput))
            }
            
            if (snapshot.errorRate > thresholds.maxErrorRate) {
                alerts.send(Alert.ErrorRateAlert(snapshot.errorRate, thresholds.maxErrorRate))
            }
            
            if (snapshot.memoryUtilization > thresholds.maxMemoryUtilization) {
                alerts.send(Alert.ResourceAlert("memory", snapshot.memoryUtilization, thresholds.maxMemoryUtilization))
            }
            
            if (snapshot.cpuUtilization > thresholds.maxCpuUtilization) {
                alerts.send(Alert.ResourceAlert("cpu", snapshot.cpuUtilization, thresholds.maxCpuUtilization))
            }
        }
        
        private fun estimateActiveCoroutines(): Int {
            // Simplified estimation based on thread names
            return Thread.getAllStackTraces().keys
                .count { it.name.contains("DefaultDispatcher") || it.name.contains("CommonPool") }
        }
        
        private fun processAlert(alert: Alert) {
            when (alert) {
                is Alert.LatencyAlert -> {
                    println("🚨 LATENCY ALERT: Current ${alert.current} exceeds threshold ${alert.threshold}")
                }
                is Alert.ThroughputAlert -> {
                    println("🚨 THROUGHPUT ALERT: Current ${String.format("%.1f", alert.current)} below threshold ${String.format("%.1f", alert.threshold)}")
                }
                is Alert.ErrorRateAlert -> {
                    println("🚨 ERROR RATE ALERT: Current ${String.format("%.1f", alert.current * 100)}% exceeds threshold ${String.format("%.1f", alert.threshold * 100)}%")
                }
                is Alert.ResourceAlert -> {
                    println("🚨 RESOURCE ALERT: ${alert.resource.uppercase()} utilization ${String.format("%.1f", alert.current * 100)}% exceeds threshold ${String.format("%.1f", alert.threshold * 100)}%")
                }
            }
        }
        
        fun recordOperation(latency: Duration, isError: Boolean = false) {
            operationCounts.incrementAndGet()
            if (isError) {
                errorCounts.incrementAndGet()
            }
            
            synchronized(latencyLock) {
                latencyMeasurements.add(latency)
            }
        }
        
        fun getCurrentMetrics(): MetricsSnapshot? = metrics.lastOrNull()
        
        fun getMetricsHistory(): List<MetricsSnapshot> = metrics.toList()
        
        fun generateReport(): String = buildString {
            val currentMetrics = getCurrentMetrics()
            if (currentMetrics != null) {
                appendLine("=== Real-Time Performance Report ===")
                appendLine("Timestamp: ${currentMetrics.timestamp}")
                appendLine("Latency: ${currentMetrics.latency}")
                appendLine("Throughput: ${String.format("%.1f", currentMetrics.throughput)} ops/sec")
                appendLine("Error Rate: ${String.format("%.2f", currentMetrics.errorRate * 100)}%")
                appendLine("Memory Utilization: ${String.format("%.1f", currentMetrics.memoryUtilization * 100)}%")
                appendLine("CPU Utilization: ${String.format("%.1f", currentMetrics.cpuUtilization * 100)}%")
                appendLine("Active Coroutines: ${currentMetrics.activeCoroutines}")
            } else {
                appendLine("No metrics available")
            }
            
            if (metrics.size > 1) {
                val history = metrics.takeLast(10)
                val avgLatency = history.map { it.latency.inWholeNanoseconds }.average().nanoseconds
                val avgThroughput = history.map { it.throughput }.average()
                
                appendLine("\nRecent Averages (last ${history.size} samples):")
                appendLine("Average Latency: $avgLatency")
                appendLine("Average Throughput: ${String.format("%.1f", avgThroughput)} ops/sec")
            }
        }
    }
    
    suspend fun demonstrateRealTimeMonitoring() {
        println("=== Real-Time Monitoring Demo ===")
        
        val monitor = PerformanceMonitor(
            thresholds = PerformanceThresholds(
                maxLatency = 100.milliseconds,
                minThroughput = 50.0,
                maxErrorRate = 0.1
            ),
            monitoringInterval = 500.milliseconds
        )
        
        val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        monitor.startMonitoring(monitoringScope)
        
        // Simulate application workload with varying performance characteristics
        coroutineScope {
            launch {
                // Normal operations
                repeat(200) { i ->
                    delay(Random.nextLong(10, 50))
                    
                    val operationTime = measureTime {
                        // Simulate work
                        var sum = 0
                        repeat(1000) { sum += it }
                    }
                    
                    val isError = Random.nextDouble() < 0.02 // 2% error rate normally
                    monitor.recordOperation(operationTime, isError)
                }
            }
            
            launch {
                delay(2000) // Let normal operations run first
                
                // Simulate performance degradation
                repeat(50) { i ->
                    delay(Random.nextLong(100, 300)) // Slower operations
                    
                    val operationTime = measureTime {
                        // Simulate heavier work
                        var sum = 0.0
                        repeat(10000) { sum += sqrt(it.toDouble()) }
                    }
                    
                    val isError = Random.nextDouble() < 0.15 // Higher error rate
                    monitor.recordOperation(operationTime, isError)
                }
            }
            
            launch {
                delay(4000) // Recovery phase
                
                repeat(100) { i ->
                    delay(Random.nextLong(20, 80))
                    
                    val operationTime = measureTime {
                        var sum = 0
                        repeat(500) { sum += it }
                    }
                    
                    val isError = Random.nextDouble() < 0.01 // Low error rate
                    monitor.recordOperation(operationTime, isError)
                }
            }
        }
        
        delay(1000) // Let monitoring finish
        
        monitoringScope.cancel()
        
        val report = monitor.generateReport()
        println(report)
        
        val history = monitor.getMetricsHistory()
        println("\nPerformance Trend Analysis:")
        println("Total snapshots collected: ${history.size}")
        
        if (history.size > 5) {
            val early = history.take(5)
            val late = history.takeLast(5)
            
            val earlyAvgLatency = early.map { it.latency.inWholeNanoseconds }.average().nanoseconds
            val lateAvgLatency = late.map { it.latency.inWholeNanoseconds }.average().nanoseconds
            
            println("Early period avg latency: $earlyAvgLatency")
            println("Late period avg latency: $lateAvgLatency")
            
            val trend = if (lateAvgLatency > earlyAvgLatency) "degrading" else "improving"
            println("Performance trend: $trend")
        }
        
        println("✅ Real-time monitoring demo completed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Performance Profiling and Monitoring Demo ===")
    
    try {
        // Coroutine metrics collection
        val metricsCollector = CoroutineMetricsCollector()
        metricsCollector.demonstrateMetricsCollection()
        println()
        
        // Statistical profiling
        val statisticalProfiler = StatisticalProfiler()
        statisticalProfiler.demonstrateStatisticalProfiling()
        println()
        
        // Real-time monitoring
        val realTimeMonitoring = RealTimeMonitoring()
        realTimeMonitoring.demonstrateRealTimeMonitoring()
        println()
        
        println("=== All Profiling and Monitoring Demos Completed ===")
        println()
        
        println("Key Performance Profiling Concepts:")
        println("✅ Coroutine-specific metrics collection and lifecycle tracking")
        println("✅ Statistical sampling for low-overhead continuous profiling")
        println("✅ Real-time performance monitoring with threshold-based alerting")
        println("✅ Hot spot identification and performance bottleneck analysis")
        println("✅ Resource utilization monitoring (CPU, memory, GC)")
        println("✅ Automated alert generation for performance anomalies")
        println("✅ Historical trend analysis and performance regression detection")
        println()
        
        println("Profiling and Monitoring Best Practices:")
        println("✅ Keep profiling overhead below 1% of application performance")
        println("✅ Use statistical sampling for production environment profiling")
        println("✅ Implement comprehensive metrics collection for coroutine lifecycles")
        println("✅ Set up real-time alerting for critical performance thresholds")
        println("✅ Correlate different metrics to identify root causes")
        println("✅ Maintain historical data for trend analysis and capacity planning")
        println("✅ Integrate with APM tools and distributed tracing systems")
        println("✅ Automate performance regression testing in CI/CD pipelines")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}