/**
 * # Coroutine Debugging and Monitoring
 * 
 * ## Problem Description
 * Debugging coroutines is more complex than traditional threading due to
 * asynchronous execution, suspension points, and cooperative scheduling.
 * Production systems need comprehensive monitoring, debugging tools, and
 * observability to track coroutine health and performance.
 * 
 * ## Solution Approach
 * Advanced debugging and monitoring techniques include:
 * - IDE debugging with coroutine-aware tools
 * - Custom logging and tracing for coroutine execution
 * - Performance monitoring and metrics collection
 * - Memory leak detection and prevention
 * - Production monitoring and alerting
 * 
 * ## Key Learning Points
 * - Coroutine debugging requires special tools and techniques
 * - Proper logging helps track asynchronous execution flows
 * - Performance monitoring is crucial for production systems
 * - Memory leaks in coroutines are different from thread leaks
 * - Observability patterns for distributed coroutine systems
 * 
 * ## Performance Considerations
 * - Debug information overhead: ~1-10% performance impact
 * - Logging overhead: ~10-100μs per log statement
 * - Monitoring overhead: ~0.1-1% CPU usage
 * - Memory tracking overhead: ~1-5% memory usage
 * 
 * ## Common Pitfalls
 * - Leaving debug mode enabled in production
 * - Excessive logging causing performance degradation
 * - Not monitoring coroutine lifecycle in production
 * - Missing memory leak detection
 * - Inadequate error tracking and alerting
 * 
 * ## Real-World Applications
 * - Production service monitoring
 * - Performance bottleneck identification
 * - Memory leak detection and prevention
 * - Distributed system tracing
 * - Development environment debugging
 * 
 * ## Related Concepts
 * - ExceptionHandling.kt - Error tracking and monitoring
 * - CustomScopes.kt - Scope-level monitoring
 * - PerformanceOptimization.kt - Performance monitoring
 */

package coroutines.advanced

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

/**
 * Coroutine debugging with IDE tools
 * 
 * Debugging Flow:
 * 
 * IDE Debugger ──> Coroutine Dump ──> Stack Traces
 *      │                 │                │
 *      ├─ Breakpoints    ├─ Active Jobs   ├─ Suspension Points
 *      ├─ Step Through   ├─ Job States    ├─ Context Info
 *      └─ Variables      └─ Hierarchies   └─ Thread Mapping
 */
class CoroutineDebugging {
    
    fun demonstrateBasicDebugging() = runBlocking {
        println("=== Basic Coroutine Debugging ===")
        
        // Enable coroutine debugging
        System.setProperty("kotlinx.coroutines.debug", "on")
        
        fun logCoroutineInfo(context: CoroutineContext, message: String) {
            val name = context[CoroutineName]?.name ?: "unnamed"
            val job = context[Job]
            val dispatcher = context[ContinuationInterceptor]
            
            println("[$name] $message")
            println("  Job: $job")
            println("  Dispatcher: $dispatcher")
            println("  Thread: ${Thread.currentThread().name}")
        }
        
        launch(CoroutineName("MainCoroutine")) {
            logCoroutineInfo(coroutineContext, "Main coroutine started")
            
            val child1 = launch(CoroutineName("Child1")) {
                logCoroutineInfo(coroutineContext, "Child 1 started")
                delay(200)
                logCoroutineInfo(coroutineContext, "Child 1 after delay")
            }
            
            val child2 = async(CoroutineName("Child2")) {
                logCoroutineInfo(coroutineContext, "Child 2 started")
                delay(150)
                logCoroutineInfo(coroutineContext, "Child 2 returning result")
                "Child 2 result"
            }
            
            child1.join()
            val result = child2.await()
            
            logCoroutineInfo(coroutineContext, "Main coroutine completed with: $result")
        }
        
        println("Basic debugging demo completed\n")
    }
    
    fun demonstrateCoroutineDump() = runBlocking {
        println("=== Coroutine Dump Analysis ===")
        
        // Create a complex coroutine hierarchy
        launch(CoroutineName("ParentCoroutine")) {
            println("Parent coroutine started")
            
            // CPU-bound child
            launch(CoroutineName("CPUWorker") + Dispatchers.Default) {
                repeat(5) { i ->
                    println("  CPU worker: iteration $i")
                    // Simulate CPU work
                    var sum = 0L
                    repeat(1_000_000) { sum += it }
                    delay(100) // Add suspension point for debugging
                }
            }
            
            // I/O-bound child
            launch(CoroutineName("IOWorker") + Dispatchers.IO) {
                repeat(3) { i ->
                    println("  I/O worker: operation $i")
                    delay(200) // Simulate I/O delay
                }
            }
            
            // Async computation
            val computation = async(CoroutineName("AsyncComputation")) {
                delay(300)
                "Computation result"
            }
            
            delay(150) // Let some coroutines start
            
            // Print coroutine dump information
            printCoroutineDumpInfo()
            
            computation.await()
        }
        
        println("Coroutine dump demo completed\n")
    }
    
    private fun printCoroutineDumpInfo() {
        try {
            // Note: In a real debugging scenario, you'd use IDE tools or
            // kotlinx.coroutines.debug.DebugProbes for detailed dumps
            println("  === Coroutine Dump Info ===")
            println("  Active coroutines can be inspected using:")
            println("  1. IntelliJ IDEA Coroutine Debugger")
            println("  2. kotlinx.coroutines.debug.DebugProbes")
            println("  3. Custom monitoring tools")
            println("  ============================")
        } catch (e: Exception) {
            println("  Could not generate coroutine dump: ${e.message}")
        }
    }
    
    fun demonstrateStackTraceDebugging() = runBlocking {
        println("=== Stack Trace Debugging ===")
        
        suspend fun level1Function() {
            delay(50)
            level2Function()
        }
        
        suspend fun level2Function() {
            delay(50)
            level3Function()
        }
        
        suspend fun level3Function() {
            delay(50)
            // Simulate an error with stack trace
            try {
                throw RuntimeException("Error in level 3")
            } catch (e: Exception) {
                println("Stack trace with coroutine info:")
                e.printStackTrace()
                
                // Custom stack trace analysis
                println("\nCoroutine stack trace analysis:")
                val stackTrace = Thread.currentThread().stackTrace
                stackTrace.take(10).forEach { frame ->
                    if (frame.className.contains("coroutines") || 
                        frame.className.contains("Continuation")) {
                        println("  Coroutine frame: ${frame.className}.${frame.methodName}")
                    } else {
                        println("  User frame: ${frame.className}.${frame.methodName}")
                    }
                }
            }
        }
        
        launch(CoroutineName("StackTraceDemo")) {
            try {
                level1Function()
            } catch (e: Exception) {
                println("Caught exception in coroutine: ${e.message}")
            }
        }
        
        println("Stack trace debugging completed\n")
    }
}

/**
 * Custom logging and tracing for coroutines
 */
class CoroutineLogging {
    
    enum class LogLevel { DEBUG, INFO, WARN, ERROR }
    
    class CoroutineLogger {
        fun log(level: LogLevel, context: CoroutineContext, message: String, exception: Throwable? = null) {
            val timestamp = System.currentTimeMillis()
            val coroutineName = context[CoroutineName]?.name ?: "unnamed"
            val jobId = context[Job]?.toString()?.substringAfter("@")?.take(8) ?: "unknown"
            val thread = Thread.currentThread().name
            
            val logMessage = "[$timestamp] $level [$coroutineName:$jobId] [$thread] $message"
            
            when (level) {
                LogLevel.DEBUG -> println("🐛 $logMessage")
                LogLevel.INFO -> println("ℹ️ $logMessage")
                LogLevel.WARN -> println("⚠️ $logMessage")
                LogLevel.ERROR -> println("❌ $logMessage")
            }
            
            exception?.let {
                println("   Exception: ${it.message}")
                it.stackTrace.take(3).forEach { frame ->
                    println("     at $frame")
                }
            }
        }
    }
    
    suspend fun CoroutineScope.withLogging(
        logger: CoroutineLogger,
        operation: suspend CoroutineScope.() -> Unit
    ) {
        logger.log(LogLevel.INFO, coroutineContext, "Coroutine started")
        val startTime = System.currentTimeMillis()
        
        try {
            operation()
            val duration = System.currentTimeMillis() - startTime
            logger.log(LogLevel.INFO, coroutineContext, "Coroutine completed in ${duration}ms")
        } catch (e: CancellationException) {
            val duration = System.currentTimeMillis() - startTime
            logger.log(LogLevel.WARN, coroutineContext, "Coroutine cancelled after ${duration}ms")
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.log(LogLevel.ERROR, coroutineContext, "Coroutine failed after ${duration}ms", e)
            throw e
        }
    }
    
    fun demonstrateCoroutineLogging() = runBlocking {
        println("=== Coroutine Logging ===")
        
        val logger = CoroutineLogger()
        
        supervisorScope {
            // Successful coroutine
            launch(CoroutineName("SuccessfulTask")) {
                withLogging(logger) {
                    logger.log(LogLevel.DEBUG, coroutineContext, "Starting data processing")
                    delay(100)
                    
                    logger.log(LogLevel.DEBUG, coroutineContext, "Processing step 1")
                    delay(50)
                    
                    logger.log(LogLevel.DEBUG, coroutineContext, "Processing step 2")
                    delay(75)
                    
                    logger.log(LogLevel.INFO, coroutineContext, "Data processing completed")
                }
            }
            
            // Failed coroutine
            launch(CoroutineName("FailingTask")) {
                withLogging(logger) {
                    logger.log(LogLevel.DEBUG, coroutineContext, "Starting risky operation")
                    delay(80)
                    
                    logger.log(LogLevel.WARN, coroutineContext, "Operation showing warning signs")
                    delay(40)
                    
                    throw RuntimeException("Operation failed unexpectedly")
                }
            }
            
            // Cancelled coroutine
            val cancellableJob = launch(CoroutineName("CancellableTask")) {
                withLogging(logger) {
                    repeat(10) { i ->
                        logger.log(LogLevel.DEBUG, coroutineContext, "Long operation step $i")
                        delay(50)
                    }
                }
            }
            
            delay(150)
            cancellableJob.cancel()
            
            delay(300) // Wait for all to complete
        }
        
        println("Coroutine logging completed\n")
    }
    
    fun demonstrateDistributedTracing() = runBlocking {
        println("=== Distributed Tracing ===")
        
        data class TraceContext(
            val traceId: String,
            val spanId: String,
            val parentSpanId: String? = null
        ) : AbstractCoroutineContextElement(TraceContext) {
            companion object Key : CoroutineContext.Key<TraceContext>
            
            override fun toString(): String = "TraceContext(traceId=$traceId, spanId=$spanId)"
        }
        
        class TracingLogger {
            fun logWithTrace(message: String, context: CoroutineContext) {
                val trace = context[TraceContext]
                val coroutineName = context[CoroutineName]?.name ?: "unnamed"
                
                if (trace != null) {
                    println("TRACE [${trace.traceId}:${trace.spanId}] [$coroutineName] $message")
                } else {
                    println("TRACE [no-trace] [$coroutineName] $message")
                }
            }
        }
        
        val tracingLogger = TracingLogger()
        
        suspend fun serviceA(input: String): String {
            val trace = coroutineContext[TraceContext]
            tracingLogger.logWithTrace("ServiceA processing: $input", coroutineContext)
            
            delay(100)
            
            // Call service B with new span
            val newTrace = TraceContext(
                traceId = trace?.traceId ?: "trace-001",
                spanId = "span-b",
                parentSpanId = trace?.spanId
            )
            
            val result = withContext(coroutineContext + newTrace) {
                serviceB("$input-processed")
            }
            
            tracingLogger.logWithTrace("ServiceA completed", coroutineContext)
            return result
        }
        
        suspend fun serviceB(input: String): String {
            tracingLogger.logWithTrace("ServiceB processing: $input", coroutineContext)
            delay(80)
            tracingLogger.logWithTrace("ServiceB completed", coroutineContext)
            return "$input-by-B"
        }
        
        // Start request with trace context
        val requestTrace = TraceContext(traceId = "trace-001", spanId = "span-a")
        
        launch(CoroutineName("RequestHandler") + requestTrace) {
            tracingLogger.logWithTrace("Request started", coroutineContext)
            
            val result = serviceA("user-data")
            
            tracingLogger.logWithTrace("Request completed with result: $result", coroutineContext)
        }
        
        delay(300)
        
        println("Distributed tracing completed\n")
    }
}

/**
 * Performance monitoring and metrics
 */
class CoroutinePerformanceMonitoring {
    
    class CoroutineMetrics {
        private val activeCoroutines = AtomicInteger(0)
        private val totalCoroutines = AtomicLong(0)
        private val completedCoroutines = AtomicLong(0)
        private val failedCoroutines = AtomicLong(0)
        private val cancelledCoroutines = AtomicLong(0)
        private val totalExecutionTime = AtomicLong(0)
        
        private val executionTimes = ConcurrentHashMap<String, MutableList<Long>>()
        
        fun coroutineStarted(name: String) {
            activeCoroutines.incrementAndGet()
            totalCoroutines.incrementAndGet()
            println("📊 Coroutine '$name' started (active: ${activeCoroutines.get()})")
        }
        
        fun coroutineCompleted(name: String, executionTimeMs: Long) {
            activeCoroutines.decrementAndGet()
            completedCoroutines.incrementAndGet()
            totalExecutionTime.addAndGet(executionTimeMs)
            
            executionTimes.computeIfAbsent(name) { mutableListOf() }.add(executionTimeMs)
            
            println("📊 Coroutine '$name' completed in ${executionTimeMs}ms (active: ${activeCoroutines.get()})")
        }
        
        fun coroutineFailed(name: String, executionTimeMs: Long, exception: Throwable) {
            activeCoroutines.decrementAndGet()
            failedCoroutines.incrementAndGet()
            totalExecutionTime.addAndGet(executionTimeMs)
            
            println("📊 Coroutine '$name' failed after ${executionTimeMs}ms: ${exception.message}")
        }
        
        fun coroutineCancelled(name: String, executionTimeMs: Long) {
            activeCoroutines.decrementAndGet()
            cancelledCoroutines.incrementAndGet()
            totalExecutionTime.addAndGet(executionTimeMs)
            
            println("📊 Coroutine '$name' cancelled after ${executionTimeMs}ms")
        }
        
        fun getMetrics(): Map<String, Any> {
            val avgExecutionTime = if (completedCoroutines.get() > 0) {
                totalExecutionTime.get() / completedCoroutines.get()
            } else 0
            
            return mapOf(
                "activeCoroutines" to activeCoroutines.get(),
                "totalCoroutines" to totalCoroutines.get(),
                "completedCoroutines" to completedCoroutines.get(),
                "failedCoroutines" to failedCoroutines.get(),
                "cancelledCoroutines" to cancelledCoroutines.get(),
                "averageExecutionTimeMs" to avgExecutionTime,
                "successRate" to if (totalCoroutines.get() > 0) {
                    (completedCoroutines.get().toDouble() / totalCoroutines.get() * 100).toInt()
                } else 0
            )
        }
        
        fun getDetailedStats(): String {
            val stats = StringBuilder()
            stats.appendLine("=== Coroutine Performance Statistics ===")
            
            getMetrics().forEach { (key, value) ->
                stats.appendLine("$key: $value")
            }
            
            stats.appendLine("\nExecution time percentiles:")
            executionTimes.forEach { (name, times) ->
                if (times.isNotEmpty()) {
                    val sorted = times.sorted()
                    val p50 = sorted[sorted.size * 50 / 100]
                    val p95 = sorted[sorted.size * 95 / 100]
                    val p99 = sorted[sorted.size * 99 / 100]
                    
                    stats.appendLine("$name: p50=${p50}ms, p95=${p95}ms, p99=${p99}ms")
                }
            }
            
            return stats.toString()
        }
    }
    
    suspend fun <T> CoroutineScope.withMetrics(
        metrics: CoroutineMetrics,
        name: String,
        operation: suspend CoroutineScope.() -> T
    ): T {
        metrics.coroutineStarted(name)
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = operation()
            val executionTime = System.currentTimeMillis() - startTime
            metrics.coroutineCompleted(name, executionTime)
            result
        } catch (e: CancellationException) {
            val executionTime = System.currentTimeMillis() - startTime
            metrics.coroutineCancelled(name, executionTime)
            throw e
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            metrics.coroutineFailed(name, executionTime, e)
            throw e
        }
    }
    
    fun demonstratePerformanceMonitoring() = runBlocking {
        println("=== Performance Monitoring ===")
        
        val metrics = CoroutineMetrics()
        
        // Start monitoring coroutine
        val monitoringJob = launch {
            repeat(10) {
                delay(500)
                println("\n📈 Current metrics: ${metrics.getMetrics()}")
            }
        }
        
        supervisorScope {
            // Fast tasks
            repeat(5) { i ->
                launch {
                    withMetrics(metrics, "FastTask-$i") {
                        delay(kotlin.random.Random.nextLong(50, 150))
                    }
                }
            }
            
            // Slow tasks
            repeat(3) { i ->
                launch {
                    withMetrics(metrics, "SlowTask-$i") {
                        delay(kotlin.random.Random.nextLong(300, 800))
                    }
                }
            }
            
            // Failing tasks
            repeat(2) { i ->
                launch {
                    withMetrics(metrics, "FailingTask-$i") {
                        delay(kotlin.random.Random.nextLong(100, 200))
                        if (kotlin.random.Random.nextBoolean()) {
                            throw RuntimeException("Task failed")
                        }
                    }
                }
            }
            
            // Cancelled task
            val cancellableJob = launch {
                withMetrics(metrics, "CancellableTask") {
                    delay(1000)
                }
            }
            
            delay(300)
            cancellableJob.cancel()
            
            delay(1500) // Wait for tasks to complete
        }
        
        monitoringJob.cancel()
        
        println("\n${metrics.getDetailedStats()}")
        println("Performance monitoring completed\n")
    }
}

/**
 * Memory leak detection and prevention
 */
class CoroutineMemoryLeakDetection {
    
    class CoroutineLeakDetector {
        private val trackedCoroutines = ConcurrentHashMap<String, CoroutineInfo>()
        private val leakDetectionThresholdMs = 30000 // 30 seconds
        
        data class CoroutineInfo(
            val name: String,
            val startTime: Long,
            val context: String,
            val stackTrace: List<String>
        )
        
        fun trackCoroutine(context: CoroutineContext) {
            val name = context[CoroutineName]?.name ?: "unnamed"
            val jobId = context[Job]?.toString() ?: "unknown"
            val key = "$name-$jobId"
            
            val info = CoroutineInfo(
                name = name,
                startTime = System.currentTimeMillis(),
                context = context.toString(),
                stackTrace = Thread.currentThread().stackTrace.take(5).map { it.toString() }
            )
            
            trackedCoroutines[key] = info
        }
        
        fun untrackCoroutine(context: CoroutineContext) {
            val name = context[CoroutineName]?.name ?: "unnamed"
            val jobId = context[Job]?.toString() ?: "unknown"
            val key = "$name-$jobId"
            
            trackedCoroutines.remove(key)
        }
        
        fun detectLeaks(): List<CoroutineInfo> {
            val now = System.currentTimeMillis()
            return trackedCoroutines.values.filter { info ->
                now - info.startTime > leakDetectionThresholdMs
            }
        }
        
        fun startLeakDetection(scope: CoroutineScope) {
            scope.launch {
                while (isActive) {
                    delay(10000) // Check every 10 seconds
                    
                    val leaks = detectLeaks()
                    if (leaks.isNotEmpty()) {
                        println("⚠️ MEMORY LEAK DETECTED: ${leaks.size} long-running coroutines")
                        leaks.forEach { leak ->
                            val ageMs = System.currentTimeMillis() - leak.startTime
                            println("  Leak: ${leak.name} (age: ${ageMs}ms)")
                            println("    Context: ${leak.context}")
                            println("    Stack: ${leak.stackTrace.first()}")
                        }
                    }
                }
            }
        }
    }
    
    suspend fun <T> CoroutineScope.withLeakDetection(
        detector: CoroutineLeakDetector,
        operation: suspend CoroutineScope.() -> T
    ): T {
        detector.trackCoroutine(coroutineContext)
        return try {
            operation()
        } finally {
            detector.untrackCoroutine(coroutineContext)
        }
    }
    
    fun demonstrateMemoryLeakDetection() = runBlocking {
        println("=== Memory Leak Detection ===")
        
        val leakDetector = CoroutineLeakDetector()
        
        // Start leak detection
        leakDetector.startLeakDetection(this)
        
        supervisorScope {
            // Normal coroutines (should not leak)
            repeat(3) { i ->
                launch(CoroutineName("NormalTask-$i")) {
                    withLeakDetection(leakDetector) {
                        delay(kotlin.random.Random.nextLong(100, 500))
                        println("  Normal task $i completed")
                    }
                }
            }
            
            // Potential leak: long-running coroutine
            launch(CoroutineName("LongRunningTask")) {
                withLeakDetection(leakDetector) {
                    repeat(1000) { i ->
                        delay(100)
                        if (i % 10 == 0) {
                            println("  Long running task: iteration $i")
                        }
                    }
                }
            }
            
            // Actual leak: infinite loop without cancellation check
            launch(CoroutineName("LeakyTask")) {
                withLeakDetection(leakDetector) {
                    var counter = 0
                    while (true) { // This will leak!
                        Thread.sleep(50) // Blocking call - can't be cancelled easily
                        counter++
                        if (counter % 100 == 0) {
                            println("  Leaky task: counter $counter")
                        }
                    }
                }
            }
            
            // Wait and observe leak detection
            delay(5000)
        }
        
        println("Memory leak detection completed\n")
    }
    
    fun demonstrateProperResourceCleanup() = runBlocking {
        println("=== Proper Resource Cleanup ===")
        
        class ManagedResource(private val name: String) {
            private var isOpen = false
            
            fun open() {
                isOpen = true
                println("    Resource '$name' opened")
            }
            
            fun close() {
                if (isOpen) {
                    isOpen = false
                    println("    Resource '$name' closed")
                }
            }
            
            fun isOpen() = isOpen
        }
        
        class ResourceManager {
            private val openResources = ConcurrentHashMap<String, ManagedResource>()
            
            fun openResource(name: String): ManagedResource {
                val resource = ManagedResource(name)
                resource.open()
                openResources[name] = resource
                return resource
            }
            
            fun closeResource(name: String) {
                openResources.remove(name)?.close()
            }
            
            fun closeAllResources() {
                println("  Closing ${openResources.size} remaining resources")
                openResources.values.forEach { it.close() }
                openResources.clear()
            }
            
            fun getOpenResourceCount() = openResources.size
        }
        
        val resourceManager = ResourceManager()
        
        try {
            supervisorScope {
                // Tasks that properly clean up resources
                repeat(3) { i ->
                    launch(CoroutineName("ProperCleanupTask-$i")) {
                        val resource = resourceManager.openResource("Resource-$i")
                        try {
                            delay(kotlin.random.Random.nextLong(200, 600))
                            println("  Task $i: Work completed")
                        } finally {
                            resourceManager.closeResource("Resource-$i")
                        }
                    }
                }
                
                // Task that doesn't clean up properly (simulating leak)
                launch(CoroutineName("ImproperCleanupTask")) {
                    resourceManager.openResource("LeakedResource")
                    delay(300)
                    // Resource not closed - this would be a leak
                    throw RuntimeException("Task failed without cleanup")
                }
                
                delay(1000)
                
                println("  Open resources before cleanup: ${resourceManager.getOpenResourceCount()}")
            }
        } finally {
            // Emergency cleanup
            resourceManager.closeAllResources()
            println("  Final open resources: ${resourceManager.getOpenResourceCount()}")
        }
        
        println("Resource cleanup demo completed\n")
    }
}

/**
 * Production monitoring and alerting
 */
class ProductionMonitoring {
    
    class CoroutineHealthMonitor {
        private val healthMetrics = ConcurrentHashMap<String, HealthMetric>()
        
        data class HealthMetric(
            var totalRequests: AtomicLong = AtomicLong(0),
            var successfulRequests: AtomicLong = AtomicLong(0),
            var failedRequests: AtomicLong = AtomicLong(0),
            var averageResponseTime: AtomicLong = AtomicLong(0),
            var lastUpdateTime: AtomicLong = AtomicLong(System.currentTimeMillis())
        )
        
        fun recordRequest(serviceName: String, responseTimeMs: Long, success: Boolean) {
            val metric = healthMetrics.computeIfAbsent(serviceName) { HealthMetric() }
            
            metric.totalRequests.incrementAndGet()
            if (success) {
                metric.successfulRequests.incrementAndGet()
            } else {
                metric.failedRequests.incrementAndGet()
            }
            
            // Update average response time
            val total = metric.totalRequests.get()
            val currentAvg = metric.averageResponseTime.get()
            val newAvg = ((currentAvg * (total - 1)) + responseTimeMs) / total
            metric.averageResponseTime.set(newAvg)
            
            metric.lastUpdateTime.set(System.currentTimeMillis())
        }
        
        fun getHealthStatus(): Map<String, Map<String, Any>> {
            return healthMetrics.mapValues { (_, metric) ->
                val total = metric.totalRequests.get()
                val successful = metric.successfulRequests.get()
                val failed = metric.failedRequests.get()
                val successRate = if (total > 0) (successful.toDouble() / total * 100).toInt() else 0
                
                mapOf(
                    "totalRequests" to total,
                    "successfulRequests" to successful,
                    "failedRequests" to failed,
                    "successRate" to "$successRate%",
                    "averageResponseTimeMs" to metric.averageResponseTime.get(),
                    "lastUpdateTime" to metric.lastUpdateTime.get()
                )
            }
        }
        
        fun checkHealth(): List<String> {
            val alerts = mutableListOf<String>()
            val now = System.currentTimeMillis()
            
            healthMetrics.forEach { (serviceName, metric) ->
                val total = metric.totalRequests.get()
                val successful = metric.successfulRequests.get()
                val successRate = if (total > 0) (successful.toDouble() / total * 100) else 100.0
                val avgResponseTime = metric.averageResponseTime.get()
                val lastUpdate = metric.lastUpdateTime.get()
                
                // Check for alerts
                if (successRate < 95.0 && total > 10) {
                    alerts.add("🚨 $serviceName: Low success rate (${successRate.toInt()}%)")
                }
                
                if (avgResponseTime > 1000) {
                    alerts.add("🚨 $serviceName: High response time (${avgResponseTime}ms)")
                }
                
                if (now - lastUpdate > 60000) { // No updates in last minute
                    alerts.add("🚨 $serviceName: Service appears inactive")
                }
            }
            
            return alerts
        }
    }
    
    suspend fun <T> CoroutineScope.withHealthMonitoring(
        monitor: CoroutineHealthMonitor,
        serviceName: String,
        operation: suspend CoroutineScope.() -> T
    ): T {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = operation()
            val responseTime = System.currentTimeMillis() - startTime
            monitor.recordRequest(serviceName, responseTime, success = true)
            result
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            monitor.recordRequest(serviceName, responseTime, success = false)
            throw e
        }
    }
    
    fun demonstrateProductionMonitoring() = runBlocking {
        println("=== Production Monitoring ===")
        
        val healthMonitor = CoroutineHealthMonitor()
        
        // Start health monitoring
        launch {
            repeat(20) {
                delay(1000)
                
                val alerts = healthMonitor.checkHealth()
                if (alerts.isNotEmpty()) {
                    println("\n🚨 HEALTH ALERTS:")
                    alerts.forEach { alert -> println("  $alert") }
                } else {
                    println("✅ All services healthy")
                }
                
                if (it % 5 == 0) {
                    println("\n📊 Health Status:")
                    healthMonitor.getHealthStatus().forEach { (service, metrics) ->
                        println("  $service: $metrics")
                    }
                }
            }
        }
        
        supervisorScope {
            // Simulate various services with different characteristics
            
            // Healthy service
            repeat(20) { i ->
                launch {
                    withHealthMonitoring(healthMonitor, "UserService") {
                        delay(kotlin.random.Random.nextLong(50, 200))
                        // 95% success rate
                        if (kotlin.random.Random.nextDouble() < 0.05) {
                            throw RuntimeException("Rare failure")
                        }
                    }
                }
            }
            
            // Slow service
            repeat(10) { i ->
                launch {
                    withHealthMonitoring(healthMonitor, "ReportsService") {
                        delay(kotlin.random.Random.nextLong(800, 1500)) // Slow responses
                    }
                }
            }
            
            // Unreliable service
            repeat(15) { i ->
                launch {
                    withHealthMonitoring(healthMonitor, "ExternalAPI") {
                        delay(kotlin.random.Random.nextLong(100, 400))
                        // 70% success rate
                        if (kotlin.random.Random.nextDouble() < 0.3) {
                            throw RuntimeException("External API failure")
                        }
                    }
                }
            }
            
            delay(15000) // Let monitoring run
        }
        
        println("Production monitoring completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic debugging
        CoroutineDebugging().demonstrateBasicDebugging()
        CoroutineDebugging().demonstrateCoroutineDump()
        CoroutineDebugging().demonstrateStackTraceDebugging()
        
        // Logging and tracing
        CoroutineLogging().demonstrateCoroutineLogging()
        CoroutineLogging().demonstrateDistributedTracing()
        
        // Performance monitoring
        CoroutinePerformanceMonitoring().demonstratePerformanceMonitoring()
        
        // Memory leak detection
        CoroutineMemoryLeakDetection().demonstrateMemoryLeakDetection()
        CoroutineMemoryLeakDetection().demonstrateProperResourceCleanup()
        
        // Production monitoring
        ProductionMonitoring().demonstrateProductionMonitoring()
        
        println("=== Coroutine Debugging Summary ===")
        println("✅ IDE Debugging:")
        println("   - Enable kotlinx.coroutines.debug for enhanced debugging")
        println("   - Use IntelliJ IDEA's coroutine debugger")
        println("   - Analyze coroutine dumps and stack traces")
        println("   - Use CoroutineName for easier identification")
        println()
        println("✅ Logging and Tracing:")
        println("   - Implement structured logging with coroutine context")
        println("   - Use distributed tracing for request flows")
        println("   - Log coroutine lifecycle events")
        println("   - Include performance metrics in logs")
        println()
        println("✅ Performance Monitoring:")
        println("   - Track coroutine metrics (active, completed, failed)")
        println("   - Monitor execution times and success rates")
        println("   - Set up alerts for performance degradation")
        println("   - Use percentiles for response time analysis")
        println()
        println("✅ Memory Leak Detection:")
        println("   - Track long-running coroutines")
        println("   - Implement proper resource cleanup")
        println("   - Use finally blocks for guaranteed cleanup")
        println("   - Monitor resource allocation/deallocation")
        println()
        println("✅ Production Monitoring:")
        println("   - Implement health checks and alerting")
        println("   - Monitor service success rates")
        println("   - Track response times and SLA compliance")
        println("   - Set up automated monitoring dashboards")
    }
}