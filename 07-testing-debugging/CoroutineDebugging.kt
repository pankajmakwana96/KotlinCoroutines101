/**
 * # Coroutine Debugging Techniques and Tools
 * 
 * ## Problem Description
 * Debugging coroutines presents unique challenges due to their asynchronous nature,
 * complex execution contexts, and potential issues like deadlocks, race conditions,
 * and memory leaks. Traditional debugging approaches often fall short when dealing
 * with suspended functions, structured concurrency, and context switching. Developers
 * need specialized tools and techniques to effectively identify and resolve coroutine-
 * related issues in production and development environments.
 * 
 * ## Solution Approach
 * Coroutine debugging strategies include:
 * - Using coroutine debugging libraries and IDE integration
 * - Implementing custom logging and monitoring for coroutines
 * - Detecting and resolving common concurrency issues
 * - Memory leak detection and resource management verification
 * - Performance profiling and bottleneck identification
 * 
 * ## Key Learning Points
 * - kotlinx-coroutines-debug provides coroutine-specific debugging capabilities
 * - Coroutine dump analysis helps identify stuck or problematic coroutines
 * - Structured concurrency principles aid in debugging hierarchical failures
 * - Context preservation is crucial for proper error propagation
 * - Thread-safe debugging techniques for concurrent operations
 * 
 * ## Performance Considerations
 * - Debug builds should include coroutine debugging overhead
 * - Production debugging requires minimal performance impact
 * - Memory profiling helps identify coroutine-related leaks
 * - CPU profiling reveals coroutine scheduling bottlenecks
 * - Network and I/O debugging in async contexts
 * 
 * ## Common Pitfalls
 * - Debugging suspended functions without proper context
 * - Missing exception handling in coroutine hierarchies
 * - Deadlocks in complex coroutine interactions
 * - Memory leaks from uncancelled coroutines
 * - Race conditions in shared state access
 * 
 * ## Real-World Applications
 * - Production system monitoring and alerting
 * - Performance optimization in high-load applications
 * - Memory leak detection in long-running services
 * - Deadlock resolution in complex concurrent systems
 * - Integration testing of async components
 * 
 * ## Related Concepts
 * - Async stack trace reconstruction
 * - Distributed tracing in microservices
 * - Observability and monitoring patterns
 * - Performance profiling methodologies
 */

package testing.debugging.techniques

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import kotlinx.coroutines.sync.*
import kotlin.coroutines.*
import kotlin.system.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Basic coroutine debugging patterns and techniques
 * 
 * Debugging Architecture:
 * 
 * Debug Infrastructure:
 * ├── Logging ────────── Structured logging with coroutine context
 * ├── Monitoring ─────── Coroutine state and performance metrics
 * ├── Stack Traces ───── Async stack trace reconstruction
 * ├── Dumps ─────────── Coroutine dump analysis
 * └── Profiling ─────── Performance bottleneck identification
 * 
 * Debugging Flow:
 * Identify Issue -> Collect Debug Info -> Analyze Context -> Fix Problem -> Verify Solution
 */
class BasicDebuggingPatterns {
    
    // Custom debug context for enhanced debugging
    class DebugContext(
        val operationName: String,
        val startTime: Long = System.currentTimeMillis(),
        val metadata: Map<String, String> = emptyMap()
    ) : AbstractCoroutineContextElement(DebugContext) {
        companion object Key : CoroutineContext.Key<DebugContext>
        
        override fun toString(): String {
            return "DebugContext(operation=$operationName, duration=${System.currentTimeMillis() - startTime}ms, metadata=$metadata)"
        }
    }
    
    // Service with built-in debugging support
    class DebuggableService {
        private val logger = DebugLogger()
        
        suspend fun performOperation(operationId: String, data: String): String {
            return withContext(DebugContext("performOperation", metadata = mapOf("id" to operationId))) {
                logger.logEntry("performOperation", mapOf("operationId" to operationId, "dataLength" to data.length.toString()))
                
                try {
                    delay(100) // Simulate work
                    
                    if (data.contains("error")) {
                        throw RuntimeException("Operation failed for: $operationId")
                    }
                    
                    val result = "Processed: $data"
                    logger.logExit("performOperation", mapOf("result" to result))
                    result
                    
                } catch (e: Exception) {
                    logger.logError("performOperation", e, mapOf("operationId" to operationId))
                    throw e
                }
            }
        }
        
        suspend fun concurrentOperations(operations: List<Pair<String, String>>): List<String> {
            return withContext(DebugContext("concurrentOperations")) {
                logger.logEntry("concurrentOperations", mapOf("count" to operations.size.toString()))
                
                try {
                    val results = operations.map { (id, data) ->
                        async {
                            performOperation(id, data)
                        }
                    }.awaitAll()
                    
                    logger.logExit("concurrentOperations", mapOf("successCount" to results.size.toString()))
                    results
                    
                } catch (e: Exception) {
                    logger.logError("concurrentOperations", e)
                    throw e
                }
            }
        }
        
        suspend fun longRunningOperation(duration: Long): String {
            return withContext(DebugContext("longRunningOperation", metadata = mapOf("duration" to duration.toString()))) {
                logger.logEntry("longRunningOperation", mapOf("expectedDuration" to duration.toString()))
                
                val startTime = System.currentTimeMillis()
                try {
                    // Simulate long-running work with periodic yielding
                    repeat((duration / 100).toInt()) { i ->
                        delay(100)
                        logger.logProgress("longRunningOperation", mapOf(
                            "progress" to "${(i + 1) * 100}/${duration}",
                            "percentage" to "${((i + 1) * 100.0 / (duration / 100)).toInt()}%"
                        ))
                        yield() // Allow cancellation
                    }
                    
                    val actualDuration = System.currentTimeMillis() - startTime
                    val result = "Completed long operation in ${actualDuration}ms"
                    logger.logExit("longRunningOperation", mapOf("actualDuration" to actualDuration.toString()))
                    result
                    
                } catch (e: CancellationException) {
                    val partialDuration = System.currentTimeMillis() - startTime
                    logger.logCancellation("longRunningOperation", mapOf("partialDuration" to partialDuration.toString()))
                    throw e
                } catch (e: Exception) {
                    logger.logError("longRunningOperation", e)
                    throw e
                }
            }
        }
    }
    
    // Enhanced logging for coroutines
    class DebugLogger {
        private val logs = mutableListOf<LogEntry>()
        
        data class LogEntry(
            val timestamp: Long,
            val level: Level,
            val operation: String,
            val message: String,
            val context: Map<String, String>,
            val coroutineInfo: String,
            val exception: Exception? = null
        ) {
            enum class Level { ENTRY, EXIT, PROGRESS, ERROR, CANCELLATION }
        }
        
        private fun log(level: LogEntry.Level, operation: String, message: String, context: Map<String, String> = emptyMap(), exception: Exception? = null) {
            val coroutineContext = kotlin.coroutines.coroutineContext
            val debugContext = coroutineContext[DebugContext]
            val coroutineInfo = buildString {
                append("Coroutine[")
                append("name=${coroutineContext[CoroutineName]?.name ?: "unnamed"}")
                if (debugContext != null) {
                    append(", debug=${debugContext}")
                }
                append("]")
            }
            
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                operation = operation,
                message = message,
                context = context,
                coroutineInfo = coroutineInfo,
                exception = exception
            )
            
            logs.add(entry)
            println("${entry.timestamp} [${entry.level}] ${entry.operation}: ${entry.message} | ${entry.coroutineInfo} | ${entry.context}")
            
            if (exception != null) {
                println("  Exception: ${exception.message}")
            }
        }
        
        suspend fun logEntry(operation: String, context: Map<String, String> = emptyMap()) {
            log(LogEntry.Level.ENTRY, operation, "Operation started", context)
        }
        
        suspend fun logExit(operation: String, context: Map<String, String> = emptyMap()) {
            log(LogEntry.Level.EXIT, operation, "Operation completed", context)
        }
        
        suspend fun logProgress(operation: String, context: Map<String, String> = emptyMap()) {
            log(LogEntry.Level.PROGRESS, operation, "Operation progress", context)
        }
        
        suspend fun logError(operation: String, exception: Exception, context: Map<String, String> = emptyMap()) {
            log(LogEntry.Level.ERROR, operation, "Operation failed", context, exception)
        }
        
        suspend fun logCancellation(operation: String, context: Map<String, String> = emptyMap()) {
            log(LogEntry.Level.CANCELLATION, operation, "Operation cancelled", context)
        }
        
        fun getLogs(): List<LogEntry> = logs.toList()
        
        fun getLogsByLevel(level: LogEntry.Level): List<LogEntry> = logs.filter { it.level == level }
        
        fun getLogsByOperation(operation: String): List<LogEntry> = logs.filter { it.operation == operation }
    }
    
    suspend fun demonstrateBasicDebugging() {
        println("=== Basic Coroutine Debugging Demo ===")
        
        val service = DebuggableService()
        
        try {
            // Successful operation
            withContext(CoroutineName("demo-operation")) {
                val result = service.performOperation("op-1", "test data")
                println("✅ Result: $result")
            }
            
            // Failed operation
            try {
                withContext(CoroutineName("error-operation")) {
                    service.performOperation("op-2", "error data")
                }
            } catch (e: Exception) {
                println("❌ Expected error: ${e.message}")
            }
            
        } catch (e: Exception) {
            println("❌ Unexpected error: ${e.message}")
        }
        
        println("✅ Basic debugging demo completed")
    }
    
    suspend fun demonstrateConcurrentDebugging() {
        println("=== Concurrent Operation Debugging Demo ===")
        
        val service = DebuggableService()
        
        val operations = listOf(
            "concurrent-1" to "data-1",
            "concurrent-2" to "data-2", 
            "concurrent-3" to "error", // This will fail
            "concurrent-4" to "data-4"
        )
        
        try {
            withContext(CoroutineName("concurrent-demo")) {
                service.concurrentOperations(operations)
            }
        } catch (e: Exception) {
            println("❌ Concurrent operations failed as expected: ${e.message}")
        }
        
        println("✅ Concurrent debugging demo completed")
    }
    
    suspend fun demonstrateCancellationDebugging() {
        println("=== Cancellation Debugging Demo ===")
        
        val service = DebuggableService()
        
        val job = CoroutineScope(Dispatchers.Default).launch(CoroutineName("cancellation-demo")) {
            try {
                service.longRunningOperation(1000)
            } catch (e: CancellationException) {
                println("  Operation was cancelled")
                throw e
            }
        }
        
        // Let it run for a bit
        delay(300)
        
        // Cancel the job
        job.cancel("Demonstration cancellation")
        
        try {
            job.join()
        } catch (e: CancellationException) {
            println("❌ Job cancelled as expected")
        }
        
        println("✅ Cancellation debugging demo completed")
    }
}

/**
 * Advanced debugging techniques and tools
 */
class AdvancedDebuggingPatterns {
    
    // Coroutine monitoring and analysis
    class CoroutineMonitor {
        data class CoroutineInfo(
            val name: String,
            val state: String,
            val startTime: Long,
            val duration: Long,
            val context: Map<String, String>
        )
        
        private val activeCoroutines = mutableMapOf<String, CoroutineInfo>()
        private val completedCoroutines = mutableListOf<CoroutineInfo>()
        
        fun registerCoroutine(name: String, context: Map<String, String> = emptyMap()) {
            activeCoroutines[name] = CoroutineInfo(
                name = name,
                state = "RUNNING",
                startTime = System.currentTimeMillis(),
                duration = 0,
                context = context
            )
        }
        
        fun completeCoroutine(name: String) {
            activeCoroutines[name]?.let { info ->
                val completedInfo = info.copy(
                    state = "COMPLETED",
                    duration = System.currentTimeMillis() - info.startTime
                )
                completedCoroutines.add(completedInfo)
                activeCoroutines.remove(name)
            }
        }
        
        fun cancelCoroutine(name: String) {
            activeCoroutines[name]?.let { info ->
                val cancelledInfo = info.copy(
                    state = "CANCELLED",
                    duration = System.currentTimeMillis() - info.startTime
                )
                completedCoroutines.add(cancelledInfo)
                activeCoroutines.remove(name)
            }
        }
        
        fun getActiveCoroutines(): List<CoroutineInfo> = activeCoroutines.values.toList()
        
        fun getCompletedCoroutines(): List<CoroutineInfo> = completedCoroutines.toList()
        
        fun generateReport(): String = buildString {
            appendLine("=== Coroutine Monitor Report ===")
            appendLine("Active Coroutines: ${activeCoroutines.size}")
            activeCoroutines.values.forEach { info ->
                val runningTime = System.currentTimeMillis() - info.startTime
                appendLine("  ${info.name}: ${info.state} for ${runningTime}ms")
            }
            
            appendLine("\nCompleted Coroutines: ${completedCoroutines.size}")
            completedCoroutines.takeLast(5).forEach { info ->
                appendLine("  ${info.name}: ${info.state} in ${info.duration}ms")
            }
            
            val avgDuration = completedCoroutines.map { it.duration }.average()
            if (avgDuration.isFinite()) {
                appendLine("\nAverage completion time: ${avgDuration.toInt()}ms")
            }
        }
    }
    
    // Deadlock detection utilities
    class DeadlockDetector {
        private val lockGraph = mutableMapOf<String, MutableSet<String>>()
        private val activeLocks = mutableMapOf<String, String>()
        
        fun acquireLock(coroutineName: String, lockName: String) {
            // Record the lock acquisition
            activeLocks[lockName] = coroutineName
            
            // Check for potential cycles
            detectCycle(coroutineName, lockName)
        }
        
        fun releaseLock(lockName: String) {
            activeLocks.remove(lockName)
        }
        
        fun addDependency(from: String, to: String) {
            lockGraph.computeIfAbsent(from) { mutableSetOf() }.add(to)
        }
        
        private fun detectCycle(startCoroutine: String, targetLock: String) {
            val visited = mutableSetOf<String>()
            val recursionStack = mutableSetOf<String>()
            
            if (hasCycle(startCoroutine, visited, recursionStack)) {
                println("⚠️  Potential deadlock detected involving $startCoroutine and $targetLock")
                println("   Lock graph: $lockGraph")
                println("   Active locks: $activeLocks")
            }
        }
        
        private fun hasCycle(node: String, visited: MutableSet<String>, recursionStack: MutableSet<String>): Boolean {
            if (recursionStack.contains(node)) {
                return true
            }
            
            if (visited.contains(node)) {
                return false
            }
            
            visited.add(node)
            recursionStack.add(node)
            
            lockGraph[node]?.forEach { neighbor ->
                if (hasCycle(neighbor, visited, recursionStack)) {
                    return true
                }
            }
            
            recursionStack.remove(node)
            return false
        }
        
        fun getActiveLocks(): Map<String, String> = activeLocks.toMap()
        
        fun generateReport(): String = buildString {
            appendLine("=== Deadlock Detection Report ===")
            appendLine("Active Locks: ${activeLocks.size}")
            activeLocks.forEach { (lock, coroutine) ->
                appendLine("  $lock held by $coroutine")
            }
            
            appendLine("\nLock Dependencies:")
            lockGraph.forEach { (from, toSet) ->
                toSet.forEach { to ->
                    appendLine("  $from -> $to")
                }
            }
        }
    }
    
    // Memory leak detection for coroutines
    class MemoryLeakDetector {
        private val jobRegistry = mutableMapOf<String, Job>()
        private val creationTimes = mutableMapOf<String, Long>()
        private val leakThresholdMs = 30000 // 30 seconds
        
        fun registerJob(name: String, job: Job) {
            jobRegistry[name] = job
            creationTimes[name] = System.currentTimeMillis()
        }
        
        fun unregisterJob(name: String) {
            jobRegistry.remove(name)
            creationTimes.remove(name)
        }
        
        fun detectLeaks(): List<String> {
            val currentTime = System.currentTimeMillis()
            val suspectedLeaks = mutableListOf<String>()
            
            jobRegistry.forEach { (name, job) ->
                val creationTime = creationTimes[name] ?: currentTime
                val age = currentTime - creationTime
                
                if (age > leakThresholdMs && job.isActive) {
                    suspectedLeaks.add("$name (age: ${age}ms, state: ${job})")
                }
            }
            
            return suspectedLeaks
        }
        
        fun forceCleanup() {
            val leaks = detectLeaks()
            println("🧹 Force cleanup: ${leaks.size} suspected leaks")
            
            jobRegistry.forEach { (name, job) ->
                if (job.isActive) {
                    println("  Cancelling: $name")
                    job.cancel("Force cleanup")
                }
            }
            
            jobRegistry.clear()
            creationTimes.clear()
        }
        
        fun generateReport(): String = buildString {
            appendLine("=== Memory Leak Detection Report ===")
            val leaks = detectLeaks()
            appendLine("Suspected Leaks: ${leaks.size}")
            leaks.forEach { leak ->
                appendLine("  ⚠️  $leak")
            }
            
            appendLine("\nActive Jobs: ${jobRegistry.size}")
            jobRegistry.forEach { (name, job) ->
                val age = System.currentTimeMillis() - (creationTimes[name] ?: 0)
                appendLine("  $name: ${job.javaClass.simpleName} (age: ${age}ms)")
            }
        }
    }
    
    // Comprehensive debugging service
    class DebuggingService {
        private val monitor = CoroutineMonitor()
        private val deadlockDetector = DeadlockDetector()
        private val memoryLeakDetector = MemoryLeakDetector()
        
        suspend fun monitoredOperation(name: String, operation: suspend () -> Unit) {
            monitor.registerCoroutine(name)
            try {
                operation()
                monitor.completeCoroutine(name)
            } catch (e: CancellationException) {
                monitor.cancelCoroutine(name)
                throw e
            } catch (e: Exception) {
                monitor.completeCoroutine(name)
                throw e
            }
        }
        
        suspend fun operationWithLocking(
            coroutineName: String,
            locks: List<String>,
            operation: suspend () -> Unit
        ) {
            val acquiredLocks = mutableListOf<String>()
            
            try {
                // Acquire locks in order
                locks.forEach { lockName ->
                    deadlockDetector.acquireLock(coroutineName, lockName)
                    acquiredLocks.add(lockName)
                }
                
                // Perform operation
                operation()
                
            } finally {
                // Release locks in reverse order
                acquiredLocks.reversed().forEach { lockName ->
                    deadlockDetector.releaseLock(lockName)
                }
            }
        }
        
        fun startLongRunningJob(name: String, job: suspend () -> Unit): Job {
            val coroutineJob = CoroutineScope(Dispatchers.Default).launch {
                job()
            }
            
            memoryLeakDetector.registerJob(name, coroutineJob)
            
            // Auto-cleanup when job completes
            coroutineJob.invokeOnCompletion { 
                memoryLeakDetector.unregisterJob(name)
            }
            
            return coroutineJob
        }
        
        fun generateDebugReport(): String = buildString {
            appendLine(monitor.generateReport())
            appendLine()
            appendLine(deadlockDetector.generateReport())
            appendLine()
            appendLine(memoryLeakDetector.generateReport())
        }
        
        fun performHealthCheck(): Map<String, Any> {
            val activeCoroutines = monitor.getActiveCoroutines()
            val suspectedLeaks = memoryLeakDetector.detectLeaks()
            val activeLocks = deadlockDetector.getActiveLocks()
            
            return mapOf(
                "activeCoroutines" to activeCoroutines.size,
                "suspectedLeaks" to suspectedLeaks.size,
                "activeLocks" to activeLocks.size,
                "healthScore" to calculateHealthScore(activeCoroutines, suspectedLeaks, activeLocks)
            )
        }
        
        private fun calculateHealthScore(
            activeCoroutines: List<CoroutineMonitor.CoroutineInfo>,
            suspectedLeaks: List<String>,
            activeLocks: Map<String, String>
        ): Int {
            var score = 100
            
            // Deduct points for potential issues
            score -= suspectedLeaks.size * 20 // Major issue
            score -= activeLocks.size * 5 // Minor concern
            score -= maxOf(0, activeCoroutines.size - 10) * 2 // Too many active coroutines
            
            return maxOf(0, score)
        }
    }
    
    suspend fun demonstrateAdvancedDebugging() {
        println("=== Advanced Debugging Demo ===")
        
        val debugService = DebuggingService()
        
        // Start some monitored operations
        coroutineScope {
            launch {
                debugService.monitoredOperation("task-1") {
                    delay(500)
                }
            }
            
            launch {
                debugService.monitoredOperation("task-2") {
                    delay(300)
                }
            }
            
            launch {
                try {
                    debugService.monitoredOperation("task-3") {
                        delay(200)
                        throw RuntimeException("Simulated error")
                    }
                } catch (e: Exception) {
                    println("  Handled error in task-3: ${e.message}")
                }
            }
        }
        
        // Generate debug report
        println(debugService.generateDebugReport())
        
        // Perform health check
        val healthInfo = debugService.performHealthCheck()
        println("System Health: ${healthInfo}")
        
        println("✅ Advanced debugging demo completed")
    }
    
    suspend fun demonstrateDeadlockDetection() {
        println("=== Deadlock Detection Demo ===")
        
        val debugService = DebuggingService()
        
        // Simulate potential deadlock scenario
        coroutineScope {
            launch {
                debugService.operationWithLocking("coroutine-A", listOf("lock-1", "lock-2")) {
                    delay(100)
                }
            }
            
            launch {
                debugService.operationWithLocking("coroutine-B", listOf("lock-2", "lock-1")) {
                    delay(100) 
                }
            }
        }
        
        println("✅ Deadlock detection demo completed")
    }
    
    suspend fun demonstrateMemoryLeakDetection() {
        println("=== Memory Leak Detection Demo ===")
        
        val debugService = DebuggingService()
        
        // Start some long-running jobs
        val job1 = debugService.startLongRunningJob("background-task-1") {
            delay(1000)
        }
        
        val job2 = debugService.startLongRunningJob("background-task-2") {
            repeat(1000) {
                delay(100)
                yield()
            }
        }
        
        // Let them run for a bit
        delay(200)
        
        // Cancel one job
        job1.cancel()
        
        // Generate report
        println(debugService.generateDebugReport())
        
        // Cleanup
        job2.cancel()
        
        println("✅ Memory leak detection demo completed")
    }
}

/**
 * Performance profiling and bottleneck identification
 */
class PerformanceProfilingPatterns {
    
    data class PerformanceMetrics(
        val operationName: String,
        val executionTime: Long,
        val memoryUsage: Long,
        val coroutineCount: Int,
        val contextSwitches: Int
    )
    
    class PerformanceProfiler {
        private val metrics = mutableListOf<PerformanceMetrics>()
        
        suspend fun <T> profile(operationName: String, operation: suspend () -> T): T {
            val startTime = System.currentTimeMillis()
            val startMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            
            // Count active coroutines (simplified)
            val coroutineCount = Thread.activeCount() // Approximation
            
            val result = operation()
            
            val endTime = System.currentTimeMillis()
            val endMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            
            val metric = PerformanceMetrics(
                operationName = operationName,
                executionTime = endTime - startTime,
                memoryUsage = endMemory - startMemory,
                coroutineCount = coroutineCount,
                contextSwitches = 0 // Would need OS-level profiling for accurate count
            )
            
            metrics.add(metric)
            println("📊 Performance: $operationName took ${metric.executionTime}ms, memory: ${metric.memoryUsage} bytes")
            
            return result
        }
        
        fun getMetrics(): List<PerformanceMetrics> = metrics.toList()
        
        fun generatePerformanceReport(): String = buildString {
            appendLine("=== Performance Report ===")
            
            if (metrics.isEmpty()) {
                appendLine("No performance data collected")
                return@buildString
            }
            
            val avgExecutionTime = metrics.map { it.executionTime }.average()
            val totalMemoryUsage = metrics.sumOf { it.memoryUsage }
            val slowestOperation = metrics.maxByOrNull { it.executionTime }
            val highestMemoryUsage = metrics.maxByOrNull { it.memoryUsage }
            
            appendLine("Total Operations: ${metrics.size}")
            appendLine("Average Execution Time: ${avgExecutionTime.toInt()}ms")
            appendLine("Total Memory Impact: ${totalMemoryUsage} bytes")
            
            slowestOperation?.let {
                appendLine("Slowest Operation: ${it.operationName} (${it.executionTime}ms)")
            }
            
            highestMemoryUsage?.let {
                appendLine("Highest Memory Usage: ${it.operationName} (${it.memoryUsage} bytes)")
            }
            
            appendLine("\nDetailed Metrics:")
            metrics.forEach { metric ->
                appendLine("  ${metric.operationName}: ${metric.executionTime}ms, ${metric.memoryUsage}B, ${metric.coroutineCount} coroutines")
            }
        }
        
        fun identifyBottlenecks(): List<String> {
            val bottlenecks = mutableListOf<String>()
            
            if (metrics.isEmpty()) return bottlenecks
            
            val avgTime = metrics.map { it.executionTime }.average()
            val avgMemory = metrics.map { it.memoryUsage }.average()
            
            metrics.forEach { metric ->
                if (metric.executionTime > avgTime * 2) {
                    bottlenecks.add("${metric.operationName}: Slow execution (${metric.executionTime}ms)")
                }
                
                if (metric.memoryUsage > avgMemory * 2) {
                    bottlenecks.add("${metric.operationName}: High memory usage (${metric.memoryUsage} bytes)")
                }
            }
            
            return bottlenecks
        }
    }
    
    class ConcurrentLoadTester {
        suspend fun runLoadTest(
            testName: String,
            concurrencyLevel: Int,
            operationsPerCoroutine: Int,
            operation: suspend (Int) -> Unit
        ): Map<String, Any> {
            
            println("🚀 Running load test: $testName")
            println("   Concurrency: $concurrencyLevel, Operations per coroutine: $operationsPerCoroutine")
            
            val startTime = System.currentTimeMillis()
            val results = mutableMapOf<String, Any>()
            val errors = mutableListOf<String>()
            
            try {
                coroutineScope {
                    val jobs = (1..concurrencyLevel).map { coroutineId ->
                        launch {
                            repeat(operationsPerCoroutine) { operationId ->
                                try {
                                    operation(operationId)
                                } catch (e: Exception) {
                                    synchronized(errors) {
                                        errors.add("Coroutine $coroutineId, Operation $operationId: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    
                    jobs.forEach { it.join() }
                }
                
                val endTime = System.currentTimeMillis()
                val totalOperations = concurrencyLevel * operationsPerCoroutine
                val duration = endTime - startTime
                
                results["testName"] = testName
                results["totalOperations"] = totalOperations
                results["duration"] = duration
                results["operationsPerSecond"] = (totalOperations * 1000.0 / duration).toInt()
                results["errors"] = errors.size
                results["success"] = true
                
                println("✅ Load test completed: ${totalOperations} operations in ${duration}ms")
                println("   Throughput: ${results["operationsPerSecond"]} ops/sec")
                println("   Errors: ${errors.size}")
                
            } catch (e: Exception) {
                results["success"] = false
                results["error"] = e.message
                println("❌ Load test failed: ${e.message}")
            }
            
            return results
        }
    }
    
    suspend fun demonstratePerformanceProfiling() {
        println("=== Performance Profiling Demo ===")
        
        val profiler = PerformanceProfiler()
        
        // Profile different types of operations
        profiler.profile("cpu-intensive") {
            repeat(100000) { i ->
                // Simulate CPU work
                if (i % 10000 == 0) yield()
            }
        }
        
        profiler.profile("io-intensive") {
            repeat(10) {
                delay(50) // Simulate I/O
            }
        }
        
        profiler.profile("memory-intensive") {
            val largeList = (1..10000).toList()
            largeList.map { it * 2 }.filter { it > 5000 }
        }
        
        profiler.profile("concurrent-operations") {
            coroutineScope {
                repeat(20) { i ->
                    launch {
                        delay(25)
                    }
                }
            }
        }
        
        // Generate reports
        println(profiler.generatePerformanceReport())
        
        val bottlenecks = profiler.identifyBottlenecks()
        if (bottlenecks.isNotEmpty()) {
            println("\n⚠️  Identified Bottlenecks:")
            bottlenecks.forEach { println("  - $it") }
        }
        
        println("✅ Performance profiling demo completed")
    }
    
    suspend fun demonstrateLoadTesting() {
        println("=== Load Testing Demo ===")
        
        val loadTester = ConcurrentLoadTester()
        
        // Test with different concurrency levels
        val testConfigs = listOf(
            Triple("Light Load", 5, 20),
            Triple("Medium Load", 10, 50),
            Triple("Heavy Load", 20, 100)
        )
        
        for ((testName, concurrency, operations) in testConfigs) {
            val results = loadTester.runLoadTest(testName, concurrency, operations) { operationId ->
                // Simulate work
                delay((1..50).random().toLong())
                
                // Occasional error
                if (operationId % 100 == 0) {
                    throw RuntimeException("Simulated error for operation $operationId")
                }
            }
            
            println("Test Results: $results")
            println()
        }
        
        println("✅ Load testing demo completed")
    }
}

/**
 * Debugging utilities and helper functions
 */
object DebuggingUtils {
    
    /**
     * Print current coroutine context information
     */
    suspend fun printCoroutineInfo(prefix: String = "") {
        val context = coroutineContext
        println("${prefix}Coroutine Info:")
        println("  Name: ${context[CoroutineName]?.name ?: "unnamed"}")
        println("  Job: ${context[Job]}")
        println("  Dispatcher: ${context[ContinuationInterceptor]}")
        
        // Custom debug context if present
        context[BasicDebuggingPatterns.DebugContext]?.let { debugContext ->
            println("  Debug: $debugContext")
        }
    }
    
    /**
     * Measure and report coroutine execution time
     */
    suspend fun <T> measureAndReport(name: String, operation: suspend () -> T): T {
        println("🕐 Starting: $name")
        val time = measureTime {
            operation()
        }
        println("⏱️  Completed: $name in $time")
        return operation()
    }
    
    /**
     * Safe coroutine execution with comprehensive error handling
     */
    suspend fun <T> safeExecute(
        operationName: String,
        operation: suspend () -> T,
        onError: (Exception) -> T? = { null },
        onCancel: () -> T? = { null }
    ): T? {
        return try {
            println("🚀 Executing: $operationName")
            val result = operation()
            println("✅ Completed: $operationName")
            result
        } catch (e: CancellationException) {
            println("🛑 Cancelled: $operationName")
            onCancel()
        } catch (e: Exception) {
            println("❌ Error in $operationName: ${e.message}")
            onError(e)
        }
    }
    
    /**
     * Monitor coroutine hierarchy and report structure
     */
    suspend fun analyzeCoroutineHierarchy(job: Job, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        println("${indent}Job: $job")
        println("${indent}  State: ${when {
            job.isActive -> "ACTIVE"
            job.isCompleted -> "COMPLETED"
            job.isCancelled -> "CANCELLED"
            else -> "UNKNOWN"
        }}")
        
        job.children.forEach { childJob ->
            analyzeCoroutineHierarchy(childJob, depth + 1)
        }
    }
    
    /**
     * Generate thread dump with coroutine information
     */
    fun generateThreadDump(): String = buildString {
        appendLine("=== Thread Dump with Coroutine Info ===")
        appendLine("Timestamp: ${System.currentTimeMillis()}")
        
        Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
            appendLine("\nThread: ${thread.name} (${thread.state})")
            appendLine("  Daemon: ${thread.isDaemon}")
            appendLine("  Priority: ${thread.priority}")
            
            stackTrace.take(10).forEach { frame ->
                appendLine("    at $frame")
            }
            
            if (stackTrace.size > 10) {
                appendLine("    ... ${stackTrace.size - 10} more frames")
            }
        }
    }
    
    /**
     * Create a debug-enabled coroutine scope
     */
    fun createDebugScope(name: String): CoroutineScope {
        return CoroutineScope(
            SupervisorJob() + 
            Dispatchers.Default + 
            CoroutineName(name) +
            CoroutineExceptionHandler { context, exception ->
                println("❌ Uncaught exception in $name: ${exception.message}")
                println("   Context: $context")
                exception.printStackTrace()
            }
        )
    }
    
    /**
     * Validate coroutine cleanup
     */
    suspend fun validateCleanup(scope: CoroutineScope, timeoutSeconds: Int = 5) {
        println("🧹 Validating cleanup for scope: $scope")
        
        scope.cancel()
        
        try {
            withTimeout(timeoutSeconds.seconds) {
                scope.coroutineContext[Job]?.join()
            }
            println("✅ Cleanup completed successfully")
        } catch (e: TimeoutCancellationException) {
            println("⚠️  Cleanup timed out after ${timeoutSeconds}s")
            println("   Some coroutines may still be running")
        }
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Coroutine Debugging Techniques Demo ===")
    
    try {
        // Basic debugging patterns
        val basicDebugging = BasicDebuggingPatterns()
        basicDebugging.demonstrateBasicDebugging()
        println()
        
        basicDebugging.demonstrateConcurrentDebugging()
        println()
        
        basicDebugging.demonstrateCancellationDebugging()
        println()
        
        // Advanced debugging patterns
        val advancedDebugging = AdvancedDebuggingPatterns()
        advancedDebugging.demonstrateAdvancedDebugging()
        println()
        
        advancedDebugging.demonstrateDeadlockDetection()
        println()
        
        advancedDebugging.demonstrateMemoryLeakDetection()
        println()
        
        // Performance profiling
        val performanceProfiling = PerformanceProfilingPatterns()
        performanceProfiling.demonstratePerformanceProfiling()
        println()
        
        performanceProfiling.demonstrateLoadTesting()
        println()
        
        println("=== All Debugging Demos Completed ===")
        println()
        
        println("Key Debugging Techniques Covered:")
        println("✅ Structured logging with coroutine context")
        println("✅ Coroutine monitoring and lifecycle tracking")
        println("✅ Deadlock detection and prevention")
        println("✅ Memory leak identification and cleanup")
        println("✅ Performance profiling and bottleneck analysis")
        println("✅ Load testing and concurrent stress testing")
        println("✅ Error handling and exception propagation")
        println("✅ Thread dump analysis and coroutine inspection")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}