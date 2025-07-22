/**
 * # Advanced Exception Handling in Coroutines
 * 
 * ## Problem Description
 * Exception handling in coroutines is more complex than traditional exception handling
 * due to structured concurrency, asynchronous execution, and parent-child relationships.
 * Proper exception handling is crucial for building resilient production systems.
 * 
 * ## Solution Approach
 * Advanced exception handling strategies include:
 * - Understanding exception propagation in coroutine hierarchies
 * - Using SupervisorJob vs regular Job appropriately
 * - Implementing CoroutineExceptionHandler for unhandled exceptions
 * - Exception recovery and fallback patterns
 * - Isolating failures to prevent cascade effects
 * 
 * ## Key Learning Points
 * - Exception propagation follows structured concurrency rules
 * - SupervisorJob allows independent child failure handling
 * - CoroutineExceptionHandler catches unhandled exceptions
 * - Exception handling differs between launch and async
 * - Recovery patterns for building resilient systems
 * 
 * ## Performance Considerations
 * - Exception creation overhead: ~1-5μs per exception
 * - Handler invocation: ~0.1-1μs per handler
 * - Recovery operations: ~10-100μs depending on complexity
 * - Proper exception handling prevents resource leaks
 * 
 * ## Common Pitfalls
 * - Catching and ignoring CancellationException
 * - Not understanding when exceptions propagate vs when they're stored
 * - Using wrong Job type for failure isolation requirements
 * - Missing exception handlers for background coroutines
 * - Not implementing proper recovery mechanisms
 * 
 * ## Real-World Applications
 * - Web service error handling and graceful degradation
 * - Background task failure isolation
 * - Data processing pipeline error recovery
 * - Microservice resilience patterns
 * - Client application error boundaries
 * 
 * ## Related Concepts
 * - StructuredConcurrency.kt - Job hierarchies and cancellation
 * - CustomScopes.kt - Scope-level exception handling
 * - ProductionPatterns.kt - Error recovery strategies
 */

package coroutines.advanced

import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Exception propagation in coroutine hierarchies
 * 
 * Exception Propagation Rules:
 * 
 * Regular Job:
 * Parent ──> Child1 ──[Exception]──> Cancels siblings ──> Propagates up
 *       ├── Child2 ──[Cancelled]
 *       └── Child3 ──[Cancelled]
 * 
 * SupervisorJob:
 * Parent ──> Child1 ──[Exception]──> Isolated failure
 *       ├── Child2 ──[Running]──> Continues normally  
 *       └── Child3 ──[Running]──> Continues normally
 */
class ExceptionPropagation {
    
    fun demonstrateRegularJobPropagation() = runBlocking {
        println("=== Regular Job Exception Propagation ===")
        
        try {
            coroutineScope {
                println("Starting coroutines in regular scope...")
                
                // Child 1 - will complete successfully
                launch {
                    repeat(3) { i ->
                        println("  Child 1: Step $i")
                        delay(200)
                    }
                    println("  Child 1: Completed")
                }
                
                // Child 2 - will fail and cause cancellation
                launch {
                    delay(400)
                    println("  Child 2: About to fail...")
                    throw RuntimeException("Child 2 failed!")
                }
                
                // Child 3 - will be cancelled due to sibling failure
                launch {
                    try {
                        repeat(5) { i ->
                            println("  Child 3: Step $i")
                            delay(300)
                        }
                        println("  Child 3: Completed")
                    } catch (e: CancellationException) {
                        println("  Child 3: Cancelled due to sibling failure")
                        throw e
                    }
                }
                
                println("All children started, waiting for completion...")
            }
        } catch (e: Exception) {
            println("Caught exception from regular scope: ${e.message}")
        }
        
        println("Regular job propagation demo completed\n")
    }
    
    fun demonstrateSupervisorJobPropagation() = runBlocking {
        println("=== SupervisorJob Exception Propagation ===")
        
        supervisorScope {
            println("Starting coroutines in supervisor scope...")
            
            // Child 1 - will complete successfully
            launch {
                repeat(3) { i ->
                    println("  Child 1: Step $i")
                    delay(200)
                }
                println("  Child 1: Completed")
            }
            
            // Child 2 - will fail but won't affect siblings
            launch {
                delay(400)
                println("  Child 2: About to fail...")
                throw RuntimeException("Child 2 failed!")
            }
            
            // Child 3 - will continue running despite sibling failure
            launch {
                repeat(5) { i ->
                    println("  Child 3: Step $i")
                    delay(300)
                }
                println("  Child 3: Completed successfully")
            }
            
            println("All children started, waiting for completion...")
            delay(1600) // Wait for all children to finish
        }
        
        println("SupervisorJob propagation demo completed\n")
    }
    
    fun demonstrateAsyncExceptionHandling() = runBlocking {
        println("=== Async Exception Handling ===")
        
        // async stores exceptions in Deferred, doesn't propagate immediately
        supervisorScope {
            val deferred1 = async {
                delay(100)
                "Success result"
            }
            
            val deferred2 = async {
                delay(200)
                throw RuntimeException("Async operation failed")
            }
            
            val deferred3 = async {
                delay(300)
                "Another success result"
            }
            
            // Handle each async result individually
            try {
                val result1 = deferred1.await()
                println("  Result 1: $result1")
            } catch (e: Exception) {
                println("  Exception in async 1: ${e.message}")
            }
            
            try {
                val result2 = deferred2.await()
                println("  Result 2: $result2")
            } catch (e: Exception) {
                println("  Exception in async 2: ${e.message}")
            }
            
            try {
                val result3 = deferred3.await()
                println("  Result 3: $result3")
            } catch (e: Exception) {
                println("  Exception in async 3: ${e.message}")
            }
        }
        
        println("Async exception handling demo completed\n")
    }
}

/**
 * CoroutineExceptionHandler for unhandled exceptions
 */
class CoroutineExceptionHandlerDemo {
    
    fun demonstrateExceptionHandler() = runBlocking {
        println("=== CoroutineExceptionHandler Demo ===")
        
        val exceptionHandler = CoroutineExceptionHandler { context, exception ->
            println("Caught unhandled exception in context $context:")
            println("  Exception: ${exception::class.simpleName}: ${exception.message}")
            println("  Stack trace preview: ${exception.stackTrace.take(3).joinToString()}")
        }
        
        // Handler works with launch in supervisor scope
        val scope = CoroutineScope(SupervisorJob() + exceptionHandler)
        
        scope.launch {
            println("  Task 1: Starting...")
            delay(100)
            throw RuntimeException("Task 1 failed with unhandled exception")
        }
        
        scope.launch {
            println("  Task 2: Starting...")
            delay(200)
            println("  Task 2: Completed successfully")
        }
        
        scope.launch {
            println("  Task 3: Starting...")
            delay(50)
            throw IllegalStateException("Task 3 failed with different exception")
        }
        
        delay(300) // Wait for tasks to complete
        scope.cancel()
        
        println("Exception handler demo completed\n")
    }
    
    fun demonstrateExceptionHandlerScenarios() = runBlocking {
        println("=== Exception Handler Scenarios ===")
        
        val handler = CoroutineExceptionHandler { _, exception ->
            println("Handler caught: ${exception.message}")
        }
        
        // Scenario 1: Handler works with launch
        println("1. Handler with launch:")
        supervisorScope {
            launch(handler) {
                throw RuntimeException("Launch exception")
            }
            delay(100)
        }
        
        // Scenario 2: Handler doesn't work with async (exception stored in Deferred)
        println("\n2. Handler with async (won't catch):")
        supervisorScope {
            val deferred = async(handler) {
                throw RuntimeException("Async exception")
            }
            
            try {
                deferred.await()
            } catch (e: Exception) {
                println("Caught from await(): ${e.message}")
            }
        }
        
        // Scenario 3: Handler in nested scopes
        println("\n3. Handler inheritance:")
        val parentScope = CoroutineScope(SupervisorJob() + handler)
        
        parentScope.launch {
            // Child inherits handler
            launch {
                throw RuntimeException("Nested exception")
            }
            delay(100)
        }
        
        delay(200)
        parentScope.cancel()
        
        println("Exception handler scenarios completed\n")
    }
    
    fun demonstrateCustomExceptionHandler() = runBlocking {
        println("=== Custom Exception Handler Implementation ===")
        
        class ServiceExceptionHandler(private val serviceName: String) : CoroutineExceptionHandler {
            override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler
            
            override fun handleException(context: CoroutineContext, exception: Throwable) {
                when (exception) {
                    is CancellationException -> {
                        // Don't log cancellation as errors
                        println("Service $serviceName: Operation cancelled")
                    }
                    is IllegalArgumentException -> {
                        println("Service $serviceName: Input validation error: ${exception.message}")
                        // Could trigger input validation alerts
                    }
                    is RuntimeException -> {
                        println("Service $serviceName: Runtime error: ${exception.message}")
                        // Could trigger error monitoring alerts
                    }
                    else -> {
                        println("Service $serviceName: Unexpected error: ${exception::class.simpleName}: ${exception.message}")
                        // Could trigger critical alerts
                    }
                }
            }
        }
        
        val userServiceHandler = ServiceExceptionHandler("UserService")
        val orderServiceHandler = ServiceExceptionHandler("OrderService")
        
        // Different services with different handlers
        supervisorScope {
            launch(userServiceHandler) {
                delay(100)
                throw IllegalArgumentException("Invalid user ID")
            }
            
            launch(orderServiceHandler) {
                delay(150)
                throw RuntimeException("Database connection failed")
            }
            
            launch(userServiceHandler) {
                delay(200)
                throw Exception("Unexpected error")
            }
            
            delay(300)
        }
        
        println("Custom exception handler demo completed\n")
    }
}

/**
 * Exception recovery and resilience patterns
 */
class ExceptionRecoveryPatterns {
    
    suspend fun demonstrateRetryPattern() {
        println("=== Retry Pattern ===")
        
        suspend fun unreliableOperation(): String {
            if (Random.nextDouble() < 0.7) {
                throw RuntimeException("Operation failed randomly")
            }
            return "Success!"
        }
        
        suspend fun <T> retryOperation(
            maxAttempts: Int = 3,
            delayMs: Long = 100,
            operation: suspend () -> T
        ): Result<T> {
            repeat(maxAttempts) { attempt ->
                try {
                    val result = operation()
                    if (attempt > 0) {
                        println("  Operation succeeded on attempt ${attempt + 1}")
                    }
                    return Result.success(result)
                } catch (e: Exception) {
                    println("  Attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < maxAttempts - 1) {
                        delay(delayMs * (attempt + 1)) // Exponential backoff
                    }
                }
            }
            return Result.failure(RuntimeException("Operation failed after $maxAttempts attempts"))
        }
        
        // Demonstrate retry pattern
        val result = retryOperation(maxAttempts = 3) {
            unreliableOperation()
        }
        
        result.fold(
            onSuccess = { value -> println("  Final result: $value") },
            onFailure = { exception -> println("  Final failure: ${exception.message}") }
        )
        
        println("Retry pattern demo completed\n")
    }
    
    suspend fun demonstrateFallbackPattern() {
        println("=== Fallback Pattern ===")
        
        suspend fun primaryService(): String {
            delay(100)
            if (Random.nextBoolean()) {
                throw RuntimeException("Primary service unavailable")
            }
            return "Primary service result"
        }
        
        suspend fun secondaryService(): String {
            delay(200)
            if (Random.nextBoolean()) {
                throw RuntimeException("Secondary service unavailable")
            }
            return "Secondary service result"
        }
        
        suspend fun cacheService(): String {
            delay(50)
            return "Cached result"
        }
        
        suspend fun <T> withFallback(vararg operations: suspend () -> T): T {
            var lastException: Exception? = null
            
            for ((index, operation) in operations.withIndex()) {
                try {
                    val result = operation()
                    if (index > 0) {
                        println("  Fallback level $index succeeded")
                    }
                    return result
                } catch (e: Exception) {
                    println("  Level $index failed: ${e.message}")
                    lastException = e
                }
            }
            
            throw lastException ?: RuntimeException("All fallback operations failed")
        }
        
        // Demonstrate fallback pattern
        try {
            val result = withFallback(
                { primaryService() },
                { secondaryService() },
                { cacheService() }
            )
            println("  Final result: $result")
        } catch (e: Exception) {
            println("  All fallbacks failed: ${e.message}")
        }
        
        println("Fallback pattern demo completed\n")
    }
    
    suspend fun demonstrateCircuitBreakerPattern() {
        println("=== Circuit Breaker Pattern ===")
        
        class CircuitBreaker(
            private val failureThreshold: Int = 3,
            private val recoveryTimeoutMs: Long = 5000
        ) {
            private var failureCount = 0
            private var lastFailureTime = 0L
            private var state = State.CLOSED
            
            enum class State { CLOSED, OPEN, HALF_OPEN }
            
            suspend fun <T> execute(operation: suspend () -> T): T {
                when (state) {
                    State.OPEN -> {
                        if (System.currentTimeMillis() - lastFailureTime > recoveryTimeoutMs) {
                            state = State.HALF_OPEN
                            println("    Circuit breaker: OPEN -> HALF_OPEN")
                        } else {
                            throw RuntimeException("Circuit breaker is OPEN")
                        }
                    }
                    State.CLOSED, State.HALF_OPEN -> {
                        // Proceed with operation
                    }
                }
                
                return try {
                    val result = operation()
                    onSuccess()
                    result
                } catch (e: Exception) {
                    onFailure()
                    throw e
                }
            }
            
            private fun onSuccess() {
                failureCount = 0
                if (state == State.HALF_OPEN) {
                    state = State.CLOSED
                    println("    Circuit breaker: HALF_OPEN -> CLOSED")
                }
            }
            
            private fun onFailure() {
                failureCount++
                lastFailureTime = System.currentTimeMillis()
                
                if (failureCount >= failureThreshold && state == State.CLOSED) {
                    state = State.OPEN
                    println("    Circuit breaker: CLOSED -> OPEN")
                } else if (state == State.HALF_OPEN) {
                    state = State.OPEN
                    println("    Circuit breaker: HALF_OPEN -> OPEN")
                }
            }
        }
        
        val circuitBreaker = CircuitBreaker(failureThreshold = 2, recoveryTimeoutMs = 1000)
        
        suspend fun flakyService(): String {
            if (Random.nextDouble() < 0.8) {
                throw RuntimeException("Service failure")
            }
            return "Service success"
        }
        
        // Demonstrate circuit breaker
        repeat(10) { attempt ->
            try {
                val result = circuitBreaker.execute { flakyService() }
                println("  Attempt ${attempt + 1}: $result")
            } catch (e: Exception) {
                println("  Attempt ${attempt + 1}: ${e.message}")
            }
            
            delay(200)
        }
        
        println("Circuit breaker pattern demo completed\n")
    }
}

/**
 * Error boundaries and isolation
 */
class ErrorBoundaries {
    
    fun demonstrateErrorBoundaries() = runBlocking {
        println("=== Error Boundaries ===")
        
        class ErrorBoundary(private val name: String) {
            private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                println("Error boundary '$name' caught: ${exception.message}")
            }
            
            fun createScope(): CoroutineScope {
                return CoroutineScope(SupervisorJob() + exceptionHandler)
            }
        }
        
        // Create isolated error boundaries
        val userServiceBoundary = ErrorBoundary("UserService")
        val orderServiceBoundary = ErrorBoundary("OrderService")
        val paymentServiceBoundary = ErrorBoundary("PaymentService")
        
        val userScope = userServiceBoundary.createScope()
        val orderScope = orderServiceBoundary.createScope()
        val paymentScope = paymentServiceBoundary.createScope()
        
        // Launch operations in isolated scopes
        userScope.launch {
            delay(100)
            println("  User service: Processing user data")
            delay(200)
            throw RuntimeException("User service failed")
        }
        
        orderScope.launch {
            delay(150)
            println("  Order service: Processing orders")
            delay(300)
            println("  Order service: Completed successfully")
        }
        
        paymentScope.launch {
            delay(200)
            println("  Payment service: Processing payments")
            delay(100)
            throw IllegalStateException("Payment service failed")
        }
        
        // Even with failures, other services continue
        delay(600)
        
        // Cleanup
        userScope.cancel()
        orderScope.cancel()
        paymentScope.cancel()
        
        println("Error boundaries demo completed\n")
    }
    
    fun demonstrateFailureIsolation() = runBlocking {
        println("=== Failure Isolation Patterns ===")
        
        suspend fun processDataBatch(batchId: Int, items: List<String>): List<String> {
            return supervisorScope {
                val results = mutableListOf<String>()
                
                // Process each item independently
                val jobs = items.mapIndexed { index, item ->
                    async {
                        try {
                            // Simulate processing with random failures
                            if (Random.nextDouble() < 0.3) {
                                throw RuntimeException("Processing failed for item $item")
                            }
                            delay(Random.nextLong(50, 150))
                            "Processed: $item"
                        } catch (e: Exception) {
                            println("    Batch $batchId, item $index failed: ${e.message}")
                            null // Return null for failed items
                        }
                    }
                }
                
                // Collect successful results
                jobs.awaitAll().filterNotNull()
            }
        }
        
        // Process multiple batches with isolation
        val batches = listOf(
            listOf("A1", "A2", "A3", "A4"),
            listOf("B1", "B2", "B3"),
            listOf("C1", "C2", "C3", "C4", "C5")
        )
        
        supervisorScope {
            val batchJobs = batches.mapIndexed { batchIndex, batch ->
                async {
                    try {
                        val results = processDataBatch(batchIndex, batch)
                        println("  Batch $batchIndex completed: ${results.size}/${batch.size} items successful")
                        results
                    } catch (e: Exception) {
                        println("  Batch $batchIndex failed completely: ${e.message}")
                        emptyList<String>()
                    }
                }
            }
            
            val allResults = batchJobs.awaitAll().flatten()
            println("Total successful items: ${allResults.size}")
            println("Results: $allResults")
        }
        
        println("Failure isolation demo completed\n")
    }
}

/**
 * Advanced exception handling strategies
 */
class AdvancedExceptionStrategies {
    
    fun demonstrateExceptionClassification() = runBlocking {
        println("=== Exception Classification ===")
        
        sealed class ServiceException(message: String) : Exception(message) {
            class ValidationException(message: String) : ServiceException(message)
            class BusinessLogicException(message: String) : ServiceException(message)
            class ExternalServiceException(message: String) : ServiceException(message)
            class InfrastructureException(message: String) : ServiceException(message)
        }
        
        class ExceptionClassifier {
            fun classify(exception: Throwable): ServiceException {
                return when (exception) {
                    is IllegalArgumentException -> ServiceException.ValidationException(exception.message ?: "Validation failed")
                    is RuntimeException -> ServiceException.BusinessLogicException(exception.message ?: "Business logic error")
                    is java.net.ConnectException -> ServiceException.ExternalServiceException("External service unavailable")
                    else -> ServiceException.InfrastructureException("Infrastructure error: ${exception.message}")
                }
            }
            
            fun shouldRetry(exception: ServiceException): Boolean {
                return when (exception) {
                    is ServiceException.ValidationException -> false // Don't retry validation errors
                    is ServiceException.BusinessLogicException -> false // Don't retry business logic errors
                    is ServiceException.ExternalServiceException -> true // Retry external service errors
                    is ServiceException.InfrastructureException -> true // Retry infrastructure errors
                }
            }
        }
        
        val classifier = ExceptionClassifier()
        
        suspend fun handleOperation(operation: suspend () -> String): String? {
            return try {
                operation()
            } catch (e: Exception) {
                val classified = classifier.classify(e)
                println("  Classified exception: ${classified::class.simpleName}: ${classified.message}")
                
                if (classifier.shouldRetry(classified)) {
                    println("  Exception is retryable, could implement retry logic")
                } else {
                    println("  Exception is not retryable, failing fast")
                }
                
                null
            }
        }
        
        // Test different exception types
        val operations = listOf(
            { throw IllegalArgumentException("Invalid input") },
            { throw RuntimeException("Business rule violation") },
            { throw java.net.ConnectException("Network timeout") },
            { throw Exception("Unknown error") }
        )
        
        operations.forEachIndexed { index, operation ->
            println("Operation ${index + 1}:")
            handleOperation { operation(); "Success" }
        }
        
        println("Exception classification demo completed\n")
    }
    
    fun demonstrateErrorAggregation() = runBlocking {
        println("=== Error Aggregation ===")
        
        data class ErrorReport(
            val operation: String,
            val exception: Exception,
            val timestamp: Long = System.currentTimeMillis()
        )
        
        class ErrorAggregator {
            private val errors = mutableListOf<ErrorReport>()
            
            fun recordError(operation: String, exception: Exception) {
                errors.add(ErrorReport(operation, exception))
            }
            
            fun getErrorSummary(): Map<String, List<ErrorReport>> {
                return errors.groupBy { it.exception::class.simpleName ?: "Unknown" }
            }
            
            fun clearErrors() {
                errors.clear()
            }
        }
        
        val errorAggregator = ErrorAggregator()
        
        suspend fun executeWithErrorTracking(
            operationName: String,
            operation: suspend () -> String
        ): String? {
            return try {
                operation()
            } catch (e: Exception) {
                errorAggregator.recordError(operationName, e)
                null
            }
        }
        
        // Execute multiple operations that may fail
        supervisorScope {
            val operations = listOf(
                "UserValidation" to { throw IllegalArgumentException("Invalid user") },
                "DatabaseQuery" to { throw RuntimeException("DB connection failed") },
                "ExternalAPI" to { throw java.net.SocketTimeoutException("API timeout") },
                "FileProcessing" to { throw java.io.IOException("File not found") },
                "UserValidation" to { throw IllegalArgumentException("Another validation error") },
                "DatabaseQuery" to { throw RuntimeException("Another DB error") }
            )
            
            val jobs = operations.map { (name, operation) ->
                async {
                    executeWithErrorTracking(name) { operation(); "Success" }
                }
            }
            
            jobs.awaitAll()
            
            // Analyze errors
            val errorSummary = errorAggregator.getErrorSummary()
            println("Error Summary:")
            errorSummary.forEach { (exceptionType, reports) ->
                println("  $exceptionType: ${reports.size} occurrences")
                reports.forEach { report ->
                    println("    - ${report.operation}: ${report.exception.message}")
                }
            }
        }
        
        println("Error aggregation demo completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Exception propagation
        ExceptionPropagation().demonstrateRegularJobPropagation()
        ExceptionPropagation().demonstrateSupervisorJobPropagation()
        ExceptionPropagation().demonstrateAsyncExceptionHandling()
        
        // Exception handlers
        CoroutineExceptionHandlerDemo().demonstrateExceptionHandler()
        CoroutineExceptionHandlerDemo().demonstrateExceptionHandlerScenarios()
        CoroutineExceptionHandlerDemo().demonstrateCustomExceptionHandler()
        
        // Recovery patterns
        ExceptionRecoveryPatterns().demonstrateRetryPattern()
        ExceptionRecoveryPatterns().demonstrateFallbackPattern()
        ExceptionRecoveryPatterns().demonstrateCircuitBreakerPattern()
        
        // Error boundaries
        ErrorBoundaries().demonstrateErrorBoundaries()
        ErrorBoundaries().demonstrateFailureIsolation()
        
        // Advanced strategies
        AdvancedExceptionStrategies().demonstrateExceptionClassification()
        AdvancedExceptionStrategies().demonstrateErrorAggregation()
        
        println("=== Exception Handling Summary ===")
        println("✅ Exception Propagation:")
        println("   - Regular Job: Failure cancels siblings and propagates up")
        println("   - SupervisorJob: Failures are isolated to individual children")
        println("   - async: Exceptions stored in Deferred, retrieved on await()")
        println()
        println("✅ Exception Handlers:")
        println("   - CoroutineExceptionHandler catches unhandled exceptions")
        println("   - Works with launch but not async (exceptions stored in Deferred)")
        println("   - Should be attached to scope or root coroutine")
        println()
        println("✅ Recovery Patterns:")
        println("   - Retry with exponential backoff for transient failures")
        println("   - Fallback chains for service degradation")
        println("   - Circuit breakers for preventing cascade failures")
        println()
        println("✅ Error Boundaries:")
        println("   - Isolate failures using SupervisorJob and separate scopes")
        println("   - Classify exceptions for appropriate handling strategies")
        println("   - Aggregate errors for monitoring and analysis")
        println()
        println("✅ Best Practices:")
        println("   - Never catch and ignore CancellationException")
        println("   - Use appropriate Job type for failure isolation needs")
        println("   - Implement recovery strategies for resilient systems")
        println("   - Monitor and analyze exception patterns")
    }
}