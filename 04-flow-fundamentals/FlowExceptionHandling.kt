/**
 * # Flow Exception Handling and Error Recovery
 * 
 * ## Problem Description
 * Asynchronous data streams are prone to various types of failures: network
 * timeouts, data corruption, processing errors, and resource exhaustion.
 * Traditional try-catch blocks are insufficient for handling exceptions in
 * flows due to their asynchronous nature and complex operator chains.
 * 
 * ## Solution Approach
 * Comprehensive flow exception handling includes:
 * - Understanding exception propagation in flow chains
 * - Using catch operator for declarative error handling
 * - Implementing retry mechanisms with exponential backoff
 * - Graceful degradation and fallback strategies
 * - Error boundary patterns for isolation
 * 
 * ## Key Learning Points
 * - Exceptions propagate downstream in flow chains
 * - catch operator handles upstream exceptions only
 * - retry and retryWhen provide automatic recovery
 * - onCompletion allows cleanup regardless of success/failure
 * - Error boundaries prevent cascade failures
 * 
 * ## Performance Considerations
 * - Exception handling overhead: ~1-10μs per exception
 * - Retry overhead: depends on retry strategy and delays
 * - Error logging overhead: ~10-100μs per log entry
 * - Recovery operations may have significant latency
 * 
 * ## Common Pitfalls
 * - catch operator placement (only handles upstream exceptions)
 * - Infinite retry loops without proper backoff
 * - Not handling different exception types appropriately
 * - Missing cleanup in error scenarios
 * - Over-complicated error recovery logic
 * 
 * ## Real-World Applications
 * - Network request error handling
 * - Database connection failure recovery
 * - File processing error management
 * - Real-time data stream resilience
 * - API rate limiting and throttling
 * 
 * ## Related Concepts
 * - CoroutineExceptionHandler - Coroutine-level error handling
 * - SupervisorJob - Error isolation in coroutines
 * - Circuit breaker pattern - Fault tolerance
 */

package flow.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic flow exception handling patterns
 * 
 * Exception Flow in Flows:
 * 
 * Upstream ──> Operator1 ──> Operator2 ──> catch ──> Downstream
 *    │            │            │            │           │
 *    └─ Exception ┴─ Exception ┴─ Exception ┘           └─ Safe
 * 
 * Exception Transparency:
 * catch operator only handles exceptions from upstream operations,
 * not from downstream operations after the catch.
 */
class BasicFlowExceptionHandling {
    
    fun demonstrateExceptionPropagation() = runBlocking {
        println("=== Exception Propagation in Flows ===")
        
        // Exception in flow builder
        println("1. Exception in flow builder:")
        try {
            flow {
                emit(1)
                emit(2)
                throw RuntimeException("Error in flow builder")
                emit(3) // This won't be reached
            }.collect { value ->
                println("  Collected: $value")
            }
        } catch (e: Exception) {
            println("  Caught exception: ${e.message}")
        }
        
        println()
        
        // Exception in operator
        println("2. Exception in map operator:")
        try {
            flowOf(1, 2, 3, 4, 5)
                .map { value ->
                    if (value == 3) {
                        throw IllegalStateException("Cannot process value 3")
                    }
                    value * 2
                }
                .collect { result ->
                    println("  Mapped result: $result")
                }
        } catch (e: Exception) {
            println("  Caught exception: ${e.message}")
        }
        
        println()
        
        // Exception in collect
        println("3. Exception in collect block:")
        try {
            flowOf(1, 2, 3, 4, 5)
                .collect { value ->
                    if (value == 4) {
                        throw RuntimeException("Processing failed for value 4")
                    }
                    println("  Processing: $value")
                }
        } catch (e: Exception) {
            println("  Caught exception: ${e.message}")
        }
        
        println("Exception propagation completed\n")
    }
    
    fun demonstrateCatchOperator() = runBlocking {
        println("=== Catch Operator Usage ===")
        
        // Basic catch usage
        println("1. Basic catch operator:")
        flow {
            emit("Item 1")
            emit("Item 2")
            throw RuntimeException("Something went wrong")
            emit("Item 3") // Won't be emitted
        }
            .catch { exception ->
                println("  Caught in catch: ${exception.message}")
                emit("Fallback item")
            }
            .collect { item ->
                println("  Received: $item")
            }
        
        println()
        
        // Catch with different exception types
        println("2. Catch with different exception types:")
        flow {
            val scenarios = listOf("network_error", "data_corruption", "timeout", "success")
            scenarios.forEach { scenario ->
                when (scenario) {
                    "network_error" -> throw java.net.ConnectException("Network unreachable")
                    "data_corruption" -> throw IllegalArgumentException("Invalid data format")
                    "timeout" -> throw java.util.concurrent.TimeoutException("Operation timed out")
                    "success" -> emit("Success case")
                }
            }
        }
            .catch { exception ->
                when (exception) {
                    is java.net.ConnectException -> {
                        println("  Network error caught, using cached data")
                        emit("Cached data")
                    }
                    is IllegalArgumentException -> {
                        println("  Data corruption caught, skipping invalid data")
                        // Don't emit anything, just skip
                    }
                    is java.util.concurrent.TimeoutException -> {
                        println("  Timeout caught, using partial results")
                        emit("Partial results")
                    }
                    else -> {
                        println("  Unexpected error: ${exception.message}")
                        emit("Error placeholder")
                    }
                }
            }
            .collect { result ->
                println("  Final result: $result")
            }
        
        println()
        
        // Exception transparency demonstration
        println("3. Exception transparency (catch placement matters):")
        
        // Correct placement - catch handles upstream exceptions
        println("   Correct placement:")
        flowOf(1, 2, 3)
            .map { value ->
                if (value == 2) throw RuntimeException("Map error")
                value * 10
            }
            .catch { exception ->
                println("    Caught upstream exception: ${exception.message}")
                emit(-1)
            }
            .map { it + 100 }
            .collect { println("    Result: $it") }
        
        println()
        
        // Incorrect placement - catch doesn't handle downstream exceptions
        println("   Placement limitation:")
        try {
            flowOf(1, 2, 3)
                .catch { exception ->
                    println("    This won't catch downstream exceptions")
                    emit(-1)
                }
                .map { value ->
                    if (value == 2) throw RuntimeException("Downstream error")
                    value * 10
                }
                .collect { println("    Result: $it") }
        } catch (e: Exception) {
            println("    Caught in outer try-catch: ${e.message}")
        }
        
        println("Catch operator completed\n")
    }
    
    fun demonstrateOnCompletionOperator() = runBlocking {
        println("=== onCompletion Operator ===")
        
        // onCompletion for successful flow
        println("1. onCompletion with successful flow:")
        flowOf("A", "B", "C")
            .onCompletion { exception ->
                if (exception == null) {
                    println("  Flow completed successfully")
                } else {
                    println("  Flow completed with exception: ${exception.message}")
                }
            }
            .collect { value ->
                println("  Processing: $value")
            }
        
        println()
        
        // onCompletion with exception
        println("2. onCompletion with exception:")
        flow {
            emit("Start")
            emit("Middle")
            throw RuntimeException("Flow failed")
        }
            .onCompletion { exception ->
                if (exception != null) {
                    println("  Cleanup after exception: ${exception.message}")
                    // Perform cleanup operations here
                }
            }
            .catch { exception ->
                println("  Exception handled: ${exception.message}")
                emit("Recovery value")
            }
            .collect { value ->
                println("  Value: $value")
            }
        
        println()
        
        // onCompletion for resource cleanup
        println("3. onCompletion for resource cleanup:")
        
        class Resource(val name: String) {
            fun open() = println("    Resource '$name' opened")
            fun close() = println("    Resource '$name' closed")
            fun process(data: String) = "Processed '$data' with $name"
        }
        
        suspend fun processWithResource(items: List<String>): Flow<String> = flow {
            val resource = Resource("DatabaseConnection")
            
            try {
                resource.open()
                
                items.forEach { item ->
                    if (item == "CORRUPT") {
                        throw IllegalArgumentException("Corrupt data encountered")
                    }
                    delay(50) // Simulate processing time
                    emit(resource.process(item))
                }
            } finally {
                // This finally block might not execute if flow is cancelled
                println("    Finally block in flow builder")
            }
        }.onCompletion { exception ->
            // This will always execute regardless of success, failure, or cancellation
            val resource = Resource("DatabaseConnection")
            resource.close()
            
            if (exception != null) {
                println("    Flow completed with exception, cleanup performed")
            } else {
                println("    Flow completed successfully, cleanup performed")
            }
        }
        
        try {
            processWithResource(listOf("Data1", "Data2", "CORRUPT", "Data3"))
                .collect { result ->
                    println("  Result: $result")
                }
        } catch (e: Exception) {
            println("  Caught final exception: ${e.message}")
        }
        
        println("onCompletion completed\n")
    }
}

/**
 * Retry mechanisms and recovery strategies
 */
class RetryMechanisms {
    
    fun demonstrateBasicRetry() = runBlocking {
        println("=== Basic Retry Mechanisms ===")
        
        // Simple retry with fixed attempts
        println("1. Simple retry (3 attempts):")
        var attempt = 0
        
        flow {
            attempt++
            println("  Attempt $attempt")
            
            if (attempt < 3) {
                throw RuntimeException("Attempt $attempt failed")
            }
            
            emit("Success on attempt $attempt")
        }
            .retry(retries = 3)
            .catch { exception ->
                println("  All retries exhausted: ${exception.message}")
                emit("Fallback value")
            }
            .collect { result ->
                println("  Final result: $result")
            }
        
        println()
        
        // retry with condition
        println("2. Conditional retry:")
        var networkAttempt = 0
        
        flow {
            networkAttempt++
            println("  Network attempt $networkAttempt")
            
            when (networkAttempt) {
                1 -> throw java.net.ConnectException("Connection refused")
                2 -> throw java.net.SocketTimeoutException("Connection timeout")
                3 -> throw IllegalArgumentException("Invalid response format")
                else -> emit("Network success")
            }
        }
            .retry(retries = 5) { exception ->
                when (exception) {
                    is java.net.ConnectException,
                    is java.net.SocketTimeoutException -> {
                        println("    Retrying network error: ${exception.message}")
                        true // Retry network errors
                    }
                    else -> {
                        println("    Not retrying: ${exception.message}")
                        false // Don't retry other errors
                    }
                }
            }
            .catch { exception ->
                println("  Final failure: ${exception.message}")
                emit("Network unavailable")
            }
            .collect { result ->
                println("  Network result: $result")
            }
        
        println("Basic retry completed\n")
    }
    
    fun demonstrateRetryWithBackoff() = runBlocking {
        println("=== Retry with Exponential Backoff ===")
        
        // Custom retry with exponential backoff
        suspend fun <T> Flow<T>.retryWithBackoff(
            maxRetries: Int = 3,
            initialDelayMs: Long = 100,
            backoffMultiplier: Double = 2.0,
            maxDelayMs: Long = 5000,
            shouldRetry: (Throwable) -> Boolean = { true }
        ): Flow<T> = flow {
            var retryCount = 0
            var currentDelay = initialDelayMs
            
            while (retryCount <= maxRetries) {
                try {
                    collect { value -> emit(value) }
                    break // Success, exit retry loop
                } catch (exception: Exception) {
                    if (retryCount == maxRetries || !shouldRetry(exception)) {
                        throw exception // Final failure or non-retryable error
                    }
                    
                    println("    Retry ${retryCount + 1}/$maxRetries after ${currentDelay}ms delay: ${exception.message}")
                    delay(currentDelay)
                    
                    retryCount++
                    currentDelay = minOf((currentDelay * backoffMultiplier).toLong(), maxDelayMs)
                }
            }
        }
        
        println("1. Exponential backoff retry:")
        var serviceAttempt = 0
        val startTime = System.currentTimeMillis()
        
        flow {
            serviceAttempt++
            val elapsed = System.currentTimeMillis() - startTime
            println("  Service call attempt $serviceAttempt at ${elapsed}ms")
            
            if (serviceAttempt < 4) {
                throw RuntimeException("Service temporarily unavailable")
            }
            
            emit("Service response: Success")
        }
            .retryWithBackoff(
                maxRetries = 5,
                initialDelayMs = 100,
                backoffMultiplier = 2.0,
                shouldRetry = { it is RuntimeException }
            )
            .collect { result ->
                val totalTime = System.currentTimeMillis() - startTime
                println("  Success after ${totalTime}ms: $result")
            }
        
        println()
        
        // Jittered retry to avoid thundering herd
        suspend fun <T> Flow<T>.retryWithJitter(
            maxRetries: Int = 3,
            baseDelayMs: Long = 100,
            jitterRatio: Double = 0.1
        ): Flow<T> = flow {
            var retryCount = 0
            
            while (retryCount <= maxRetries) {
                try {
                    collect { value -> emit(value) }
                    break
                } catch (exception: Exception) {
                    if (retryCount == maxRetries) {
                        throw exception
                    }
                    
                    // Add jitter to prevent thundering herd
                    val jitter = (Random.nextDouble() - 0.5) * 2 * jitterRatio
                    val delayWithJitter = (baseDelayMs * (1 + jitter)).toLong()
                    
                    println("    Jittered retry ${retryCount + 1} after ${delayWithJitter}ms")
                    delay(delayWithJitter)
                    retryCount++
                }
            }
        }
        
        println("2. Jittered retry (simulating multiple clients):")
        
        // Simulate multiple clients retrying simultaneously
        supervisorScope {
            repeat(3) { clientId ->
                launch {
                    var clientAttempt = 0
                    
                    flow {
                        clientAttempt++
                        println("  Client $clientId attempt $clientAttempt")
                        
                        if (clientAttempt < 3) {
                            throw RuntimeException("Client $clientId failed")
                        }
                        
                        emit("Client $clientId success")
                    }
                        .retryWithJitter(maxRetries = 3, baseDelayMs = 200)
                        .collect { result ->
                            println("  $result")
                        }
                }
            }
        }
        
        println("Retry with backoff completed\n")
    }
    
    fun demonstrateRetryWhen() = runBlocking {
        println("=== RetryWhen Advanced Patterns ===")
        
        // retryWhen for complex retry logic
        suspend fun <T> Flow<T>.retryWhen(
            predicate: suspend FlowCollector<T>.(attempt: Int, cause: Throwable) -> Boolean
        ): Flow<T> = flow {
            var attempt = 0
            
            while (true) {
                try {
                    collect { value -> emit(value) }
                    break
                } catch (exception: Exception) {
                    attempt++
                    if (!predicate(attempt, exception)) {
                        throw exception
                    }
                }
            }
        }
        
        println("1. Complex retry logic with retryWhen:")
        var complexAttempt = 0
        
        flow {
            complexAttempt++
            println("  Complex operation attempt $complexAttempt")
            
            when (complexAttempt) {
                1, 2 -> throw java.net.ConnectException("Network error")
                3 -> throw java.util.concurrent.TimeoutException("Timeout")
                4 -> throw RuntimeException("Server error")
                else -> emit("Complex operation succeeded")
            }
        }
            .retryWhen { attempt, cause ->
                when {
                    attempt > 5 -> {
                        println("    Max attempts reached, giving up")
                        false
                    }
                    cause is java.net.ConnectException -> {
                        println("    Network error, retrying immediately (attempt $attempt)")
                        true
                    }
                    cause is java.util.concurrent.TimeoutException -> {
                        println("    Timeout, retrying with delay (attempt $attempt)")
                        delay(500)
                        true
                    }
                    cause is RuntimeException && attempt <= 3 -> {
                        println("    Server error, limited retries (attempt $attempt)")
                        delay(1000)
                        true
                    }
                    else -> {
                        println("    Non-retryable error: ${cause.message}")
                        false
                    }
                }
            }
            .catch { exception ->
                println("  Final failure after retries: ${exception.message}")
                emit("Fallback response")
            }
            .collect { result ->
                println("  Final result: $result")
            }
        
        println("RetryWhen completed\n")
    }
}

/**
 * Error boundaries and fallback patterns
 */
class ErrorBoundariesAndFallbacks {
    
    fun demonstrateErrorBoundaries() = runBlocking {
        println("=== Error Boundaries ===")
        
        // Error boundary pattern for flow chains
        suspend fun <T> Flow<T>.errorBoundary(
            boundaryName: String,
            onError: suspend (Throwable) -> T? = { null }
        ): Flow<T> = catch { exception ->
            println("  Error boundary '$boundaryName' caught: ${exception.message}")
            val fallbackValue = onError(exception)
            if (fallbackValue != null) {
                emit(fallbackValue)
            }
        }
        
        // Complex processing pipeline with error boundaries
        println("1. Processing pipeline with error boundaries:")
        
        data class DataItem(val id: Int, val data: String, val processed: Boolean = false)
        
        val inputData = flowOf(
            DataItem(1, "valid_data"),
            DataItem(2, "corrupt_data"),
            DataItem(3, "valid_data"),
            DataItem(4, "timeout_data"),
            DataItem(5, "valid_data")
        )
        
        // Stage 1: Validation with error boundary
        val validatedData = inputData
            .map { item ->
                if (item.data == "corrupt_data") {
                    throw IllegalArgumentException("Data corruption detected for item ${item.id}")
                }
                item.copy(data = "validated_${item.data}")
            }
            .errorBoundary("Validation") { exception ->
                when (exception) {
                    is IllegalArgumentException -> DataItem(-1, "validation_failed")
                    else -> null
                }
            }
        
        // Stage 2: Processing with error boundary
        val processedData = validatedData
            .map { item ->
                if (item.data.contains("timeout")) {
                    throw java.util.concurrent.TimeoutException("Processing timeout for item ${item.id}")
                }
                item.copy(data = "processed_${item.data}", processed = true)
            }
            .errorBoundary("Processing") { exception ->
                when (exception) {
                    is java.util.concurrent.TimeoutException -> DataItem(-2, "processing_timeout")
                    else -> null
                }
            }
        
        // Stage 3: Final output with error boundary
        processedData
            .map { item ->
                if (item.id < 0) {
                    // Handle error items differently
                    "Error item: ${item.data}"
                } else {
                    "Success: ${item.data}"
                }
            }
            .errorBoundary("Output") { 
                "Unexpected error in output stage"
            }
            .collect { result ->
                println("  Pipeline result: $result")
            }
        
        println("Error boundaries completed\n")
    }
    
    fun demonstrateFallbackStrategies() = runBlocking {
        println("=== Fallback Strategies ===")
        
        // Fallback chain pattern
        suspend fun <T> Flow<T>.fallbackTo(fallback: Flow<T>): Flow<T> = 
            catch { emit ->
                fallback.collect { emit(it) }
            }
        
        // Multiple fallback levels
        suspend fun <T> Flow<T>.withFallbacks(vararg fallbacks: Flow<T>): Flow<T> = 
            fallbacks.fold(this) { current, fallback ->
                current.fallbackTo(fallback)
            }
        
        println("1. Cascading fallbacks:")
        
        // Primary data source (fails)
        val primarySource = flow {
            delay(100)
            throw RuntimeException("Primary source unavailable")
        }
        
        // Secondary data source (also fails)
        val secondarySource = flow {
            delay(50)
            throw RuntimeException("Secondary source unavailable")
        }
        
        // Cache source (succeeds)
        val cacheSource = flowOf("Cached data 1", "Cached data 2")
        
        // Default source (always works)
        val defaultSource = flowOf("Default data")
        
        primarySource
            .withFallbacks(secondarySource, cacheSource, defaultSource)
            .collect { data ->
                println("  Retrieved: $data")
            }
        
        println()
        
        // Partial fallback strategy
        println("2. Partial fallback with recovery:")
        
        data class APIResponse(val data: List<String>, val source: String)
        
        suspend fun fetchFromAPI(source: String, shouldFail: Boolean): Flow<APIResponse> = flow {
            if (shouldFail) {
                throw RuntimeException("$source API failed")
            }
            
            delay(Random.nextLong(50, 200))
            emit(APIResponse(
                data = listOf("${source}_item1", "${source}_item2"), 
                source = source
            ))
        }
        
        // Combine multiple sources with individual fallbacks
        merge(
            fetchFromAPI("UserAPI", shouldFail = true)
                .catch { emit(APIResponse(listOf("cached_user_data"), "UserCache")) },
            
            fetchFromAPI("ProductAPI", shouldFail = false)
                .catch { emit(APIResponse(listOf("cached_product_data"), "ProductCache")) },
            
            fetchFromAPI("OrderAPI", shouldFail = true)
                .catch { emit(APIResponse(listOf("default_order_data"), "DefaultOrders")) }
        )
            .collect { response ->
                println("  API Response from ${response.source}: ${response.data}")
            }
        
        println("Fallback strategies completed\n")
    }
    
    fun demonstrateGracefulDegradation() = runBlocking {
        println("=== Graceful Degradation ===")
        
        enum class ServiceTier { PREMIUM, STANDARD, BASIC }
        
        data class ServiceCapability(
            val name: String,
            val availableForTiers: Set<ServiceTier>,
            val fallbackValue: String? = null
        )
        
        class DegradationManager {
            private val capabilities = listOf(
                ServiceCapability("HighRes", setOf(ServiceTier.PREMIUM)),
                ServiceCapability("MediumRes", setOf(ServiceTier.PREMIUM, ServiceTier.STANDARD)),
                ServiceCapability("BasicRes", setOf(ServiceTier.PREMIUM, ServiceTier.STANDARD, ServiceTier.BASIC))
            )
            
            suspend fun <T> executeWithDegradation(
                userTier: ServiceTier,
                operations: List<Pair<ServiceCapability, suspend () -> T>>
            ): Flow<T> = flow {
                for ((capability, operation) in operations) {
                    if (userTier in capability.availableForTiers) {
                        try {
                            val result = operation()
                            emit(result)
                            return@flow // Success, no need to try other operations
                        } catch (e: Exception) {
                            println("  ${capability.name} failed: ${e.message}")
                            // Continue to next capability level
                        }
                    } else {
                        println("  ${capability.name} not available for tier $userTier")
                    }
                }
                
                // If all operations failed, emit fallback if available
                val fallback = operations.lastOrNull()?.first?.fallbackValue
                if (fallback != null) {
                    @Suppress("UNCHECKED_CAST")
                    emit(fallback as T)
                } else {
                    throw RuntimeException("All service capabilities exhausted")
                }
            }
        }
        
        val degradationManager = DegradationManager()
        
        // Simulate service requests for different user tiers
        val userTiers = listOf(ServiceTier.PREMIUM, ServiceTier.STANDARD, ServiceTier.BASIC)
        
        userTiers.forEach { userTier ->
            println("Processing request for $userTier user:")
            
            try {
                degradationManager.executeWithDegradation(
                    userTier = userTier,
                    operations = listOf(
                        ServiceCapability("HighRes", setOf(ServiceTier.PREMIUM)) to {
                            if (Random.nextBoolean()) throw RuntimeException("HighRes service down")
                            "4K Ultra HD Content"
                        },
                        ServiceCapability("MediumRes", setOf(ServiceTier.PREMIUM, ServiceTier.STANDARD)) to {
                            if (Random.nextBoolean()) throw RuntimeException("MediumRes service overloaded")
                            "1080p HD Content"
                        },
                        ServiceCapability("BasicRes", setOf(ServiceTier.PREMIUM, ServiceTier.STANDARD, ServiceTier.BASIC), "480p SD Content") to {
                            "720p Content"
                        }
                    )
                ).collect { result ->
                    println("  Delivered: $result")
                }
            } catch (e: Exception) {
                println("  Service completely unavailable: ${e.message}")
            }
            
            println()
        }
        
        println("Graceful degradation completed\n")
    }
}

/**
 * Advanced error recovery patterns
 */
class AdvancedErrorRecovery {
    
    fun demonstrateCircuitBreakerForFlows() = runBlocking {
        println("=== Circuit Breaker for Flows ===")
        
        enum class CircuitState { CLOSED, OPEN, HALF_OPEN }
        
        class FlowCircuitBreaker(
            private val failureThreshold: Int = 3,
            private val recoveryTimeoutMs: Long = 5000
        ) {
            private var state = CircuitState.CLOSED
            private var failureCount = 0
            private var lastFailureTime = 0L
            
            suspend fun <T> execute(flow: Flow<T>): Flow<T> = flow {
                when (state) {
                    CircuitState.OPEN -> {
                        if (System.currentTimeMillis() - lastFailureTime > recoveryTimeoutMs) {
                            state = CircuitState.HALF_OPEN
                            println("  Circuit breaker: OPEN -> HALF_OPEN")
                        } else {
                            throw RuntimeException("Circuit breaker is OPEN")
                        }
                    }
                    CircuitState.CLOSED, CircuitState.HALF_OPEN -> {
                        // Proceed with flow execution
                    }
                }
                
                try {
                    flow.collect { value -> emit(value) }
                    onSuccess()
                } catch (e: Exception) {
                    onFailure()
                    throw e
                }
            }
            
            private fun onSuccess() {
                failureCount = 0
                if (state == CircuitState.HALF_OPEN) {
                    state = CircuitState.CLOSED
                    println("  Circuit breaker: HALF_OPEN -> CLOSED")
                }
            }
            
            private fun onFailure() {
                failureCount++
                lastFailureTime = System.currentTimeMillis()
                
                if (failureCount >= failureThreshold && state == CircuitState.CLOSED) {
                    state = CircuitState.OPEN
                    println("  Circuit breaker: CLOSED -> OPEN")
                } else if (state == CircuitState.HALF_OPEN) {
                    state = CircuitState.OPEN
                    println("  Circuit breaker: HALF_OPEN -> OPEN")
                }
            }
        }
        
        val circuitBreaker = FlowCircuitBreaker(failureThreshold = 2, recoveryTimeoutMs = 2000)
        
        suspend fun unreliableService(): Flow<String> = flow {
            if (Random.nextDouble() < 0.7) { // 70% failure rate
                throw RuntimeException("Service failure")
            }
            emit("Service success")
        }
        
        // Test circuit breaker
        repeat(10) { attempt ->
            try {
                val result = circuitBreaker.execute(unreliableService())
                    .single()
                println("Attempt ${attempt + 1}: $result")
            } catch (e: Exception) {
                println("Attempt ${attempt + 1}: ${e.message}")
            }
            
            delay(300)
        }
        
        println("Circuit breaker completed\n")
    }
    
    fun demonstrateAdaptiveRetry() = runBlocking {
        println("=== Adaptive Retry Strategy ===")
        
        class AdaptiveRetryStrategy {
            private var consecutiveFailures = 0
            private var lastSuccessTime = System.currentTimeMillis()
            
            suspend fun <T> Flow<T>.adaptiveRetry(): Flow<T> = flow {
                var retryCount = 0
                val maxRetries = calculateMaxRetries()
                
                while (retryCount <= maxRetries) {
                    try {
                        collect { value -> emit(value) }
                        onSuccess()
                        break
                    } catch (exception: Exception) {
                        onFailure()
                        
                        if (retryCount == maxRetries) {
                            throw exception
                        }
                        
                        val backoffDelay = calculateBackoffDelay(retryCount)
                        println("    Adaptive retry ${retryCount + 1}/$maxRetries after ${backoffDelay}ms (consecutive failures: $consecutiveFailures)")
                        
                        delay(backoffDelay)
                        retryCount++
                    }
                }
            }
            
            private fun calculateMaxRetries(): Int {
                return when {
                    consecutiveFailures < 3 -> 3
                    consecutiveFailures < 10 -> 2
                    else -> 1
                }
            }
            
            private fun calculateBackoffDelay(retryCount: Int): Long {
                val baseDelay = when {
                    consecutiveFailures < 5 -> 100L
                    consecutiveFailures < 15 -> 500L
                    else -> 2000L
                }
                
                return baseDelay * (1 shl retryCount) // Exponential backoff
            }
            
            private fun onSuccess() {
                consecutiveFailures = 0
                lastSuccessTime = System.currentTimeMillis()
            }
            
            private fun onFailure() {
                consecutiveFailures++
            }
        }
        
        val adaptiveRetry = AdaptiveRetryStrategy()
        
        with(adaptiveRetry) {
            // Simulate varying failure rates
            repeat(5) { iteration ->
                println("Iteration ${iteration + 1}:")
                
                try {
                    flow {
                        // Simulate increasing instability
                        val failureRate = minOf(0.9, 0.3 + iteration * 0.15)
                        if (Random.nextDouble() < failureRate) {
                            throw RuntimeException("Service unstable (failure rate: ${(failureRate * 100).toInt()}%)")
                        }
                        emit("Success in iteration ${iteration + 1}")
                    }
                        .adaptiveRetry()
                        .collect { result ->
                            println("  Result: $result")
                        }
                } catch (e: Exception) {
                    println("  Final failure: ${e.message}")
                }
                
                delay(500)
                println()
            }
        }
        
        println("Adaptive retry completed\n")
    }
    
    fun demonstrateErrorRecoveryPipeline() = runBlocking {
        println("=== Error Recovery Pipeline ===")
        
        data class ProcessingResult<T>(
            val value: T? = null,
            val error: Throwable? = null,
            val recoveryApplied: String? = null,
            val metadata: Map<String, Any> = emptyMap()
        ) {
            val isSuccess: Boolean get() = error == null && value != null
            val isRecovered: Boolean get() = recoveryApplied != null
        }
        
        // Multi-stage error recovery pipeline
        suspend fun <T> Flow<T>.withRecoveryPipeline(
            recoveryStages: List<suspend (Throwable, T?) -> ProcessingResult<T>?> = emptyList()
        ): Flow<ProcessingResult<T>> = flow {
            collect { value ->
                try {
                    // Simulate processing that might fail
                    if (value.toString().contains("error")) {
                        throw RuntimeException("Processing failed for: $value")
                    }
                    
                    emit(ProcessingResult(value = value))
                } catch (exception: Throwable) {
                    println("  Primary processing failed: ${exception.message}")
                    
                    // Try recovery stages
                    var recovered = false
                    for ((index, recoveryStage) in recoveryStages.withIndex()) {
                        try {
                            val recoveryResult = recoveryStage(exception, value)
                            if (recoveryResult != null) {
                                emit(recoveryResult.copy(
                                    recoveryApplied = "Stage-${index + 1}",
                                    metadata = recoveryResult.metadata + ("originalError" to exception.message)
                                ))
                                recovered = true
                                break
                            }
                        } catch (recoveryException: Exception) {
                            println("    Recovery stage ${index + 1} failed: ${recoveryException.message}")
                        }
                    }
                    
                    if (!recovered) {
                        emit(ProcessingResult(error = exception))
                    }
                }
            }
        }
        
        val recoveryStages = listOf<suspend (Throwable, String?) -> ProcessingResult<String>?>(
            // Stage 1: Try alternative processing
            { exception, original ->
                if (exception.message?.contains("Processing failed") == true) {
                    println("    Recovery Stage 1: Alternative processing")
                    ProcessingResult(
                        value = "ALTERNATIVE_${original?.replace("error", "fixed")}",
                        metadata = mapOf("stage" to "alternative_processing")
                    )
                } else null
            },
            
            // Stage 2: Use cached value
            { exception, original ->
                println("    Recovery Stage 2: Using cached value")
                ProcessingResult(
                    value = "CACHED_VALUE_FOR_${original}",
                    metadata = mapOf("stage" to "cache_lookup")
                )
            },
            
            // Stage 3: Generate fallback
            { exception, original ->
                println("    Recovery Stage 3: Generating fallback")
                ProcessingResult(
                    value = "FALLBACK_${System.currentTimeMillis()}",
                    metadata = mapOf("stage" to "fallback_generation")
                )
            }
        )
        
        val testData = listOf(
            "normal_data_1",
            "error_data_2",
            "normal_data_3", 
            "critical_error_4",
            "normal_data_5"
        )
        
        flowOf(*testData.toTypedArray())
            .withRecoveryPipeline(recoveryStages)
            .collect { result ->
                when {
                    result.isSuccess && !result.isRecovered -> 
                        println("  ✅ Success: ${result.value}")
                    result.isSuccess && result.isRecovered -> 
                        println("  🔧 Recovered: ${result.value} (via ${result.recoveryApplied})")
                    else -> 
                        println("  ❌ Failed: ${result.error?.message}")
                }
            }
        
        println("Error recovery pipeline completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic exception handling
        BasicFlowExceptionHandling().demonstrateExceptionPropagation()
        BasicFlowExceptionHandling().demonstrateCatchOperator()
        BasicFlowExceptionHandling().demonstrateOnCompletionOperator()
        
        // Retry mechanisms
        RetryMechanisms().demonstrateBasicRetry()
        RetryMechanisms().demonstrateRetryWithBackoff()
        RetryMechanisms().demonstrateRetryWhen()
        
        // Error boundaries and fallbacks
        ErrorBoundariesAndFallbacks().demonstrateErrorBoundaries()
        ErrorBoundariesAndFallbacks().demonstrateFallbackStrategies()
        ErrorBoundariesAndFallbacks().demonstrateGracefulDegradation()
        
        // Advanced error recovery
        AdvancedErrorRecovery().demonstrateCircuitBreakerForFlows()
        AdvancedErrorRecovery().demonstrateAdaptiveRetry()
        AdvancedErrorRecovery().demonstrateErrorRecoveryPipeline()
        
        println("=== Flow Exception Handling Summary ===")
        println("✅ Exception Propagation:")
        println("   - Exceptions flow downstream through operators")
        println("   - catch operator handles upstream exceptions only")
        println("   - onCompletion executes regardless of success/failure")
        println("   - Exception transparency principle")
        println()
        println("✅ Retry Strategies:")
        println("   - Simple retry with fixed attempts")
        println("   - Conditional retry based on exception type")
        println("   - Exponential backoff with jitter")
        println("   - Advanced retryWhen for complex logic")
        println()
        println("✅ Error Boundaries:")
        println("   - Isolate failures in processing pipelines")
        println("   - Cascading fallback strategies")
        println("   - Graceful degradation for different service tiers")
        println("   - Partial failure handling")
        println()
        println("✅ Advanced Recovery:")
        println("   - Circuit breaker pattern for flows")
        println("   - Adaptive retry based on failure patterns")
        println("   - Multi-stage error recovery pipelines")
        println("   - Resilience patterns for production systems")
        println()
        println("✅ Best Practices:")
        println("   - Handle different exception types appropriately")
        println("   - Implement proper backoff strategies")
        println("   - Use error boundaries to prevent cascade failures")
        println("   - Monitor and adapt retry strategies based on patterns")
    }
}