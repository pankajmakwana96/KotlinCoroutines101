/**
 * # Custom Flow Operators and Extensions
 * 
 * ## Problem Description
 * While Kotlin Flows provide extensive built-in operators, real-world applications
 * often require specialized transformations, filtering, and processing patterns
 * that aren't available out-of-the-box. Building custom operators allows for
 * code reuse, improved readability, and domain-specific flow processing patterns.
 * 
 * ## Solution Approach
 * Custom operator patterns include:
 * - Extension functions on Flow for domain-specific operations
 * - FlowCollector-based operators for emission control
 * - Stateful operators with internal state management
 * - Composite operators combining multiple operations
 * - Conditional and adaptive operators based on runtime conditions
 * 
 * ## Key Learning Points
 * - Custom operators follow the same principles as built-in ones
 * - State management in operators requires careful consideration
 * - Flow context preservation is crucial for proper behavior
 * - Exception handling must be considered in custom operators
 * - Performance characteristics should match usage patterns
 * 
 * ## Performance Considerations
 * - Custom operator overhead: ~10-50ns per item (similar to built-ins)
 * - State management memory usage varies by operator complexity
 * - Suspension points affect overall flow performance
 * - Inline functions reduce call overhead for simple operators
 * - Complex operators may require optimization for high-throughput scenarios
 * 
 * ## Common Pitfalls
 * - Not handling exceptions properly in custom operators
 * - Breaking flow context or cancellation behavior
 * - Creating operators with memory leaks in stateful logic
 * - Poor performance due to unnecessary state or allocations
 * - Not following Flow contract regarding emission patterns
 * 
 * ## Real-World Applications
 * - Domain-specific data transformations
 * - Business rule processing in flows
 * - Custom retry and error handling patterns
 * - Specialized filtering and validation logic
 * - Integration adapters for legacy systems
 * 
 * ## Related Concepts
 * - Flow operators composition patterns
 * - Reactive programming operator design
 * - Functional programming transformations
 * - Stream processing patterns
 */

package flow.advanced.custom

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Basic custom operator patterns
 * 
 * Custom Operator Categories:
 * 
 * Operator Types:
 * ├── Transform ──────── Modify emitted values
 * ├── Filter ────────── Conditionally emit values  
 * ├── Combine ───────── Merge multiple flows
 * ├── Window ────────── Group values by criteria
 * └── Terminal ──────── Consume flow with side effects
 * 
 * Implementation Patterns:
 * Simple: Extension + transform/collect
 * Stateful: Internal state management
 * Complex: Multiple flows coordination
 */
class BasicCustomOperators {
    
    fun demonstrateSimpleTransformOperators() = runBlocking {
        println("=== Simple Transform Operators ===")
        
        // Custom mapIndexed operator
        fun <T, R> Flow<T>.mapIndexed(transform: suspend (index: Int, value: T) -> R): Flow<R> = flow {
            var index = 0
            collect { value ->
                emit(transform(index++, value))
            }
        }
        
        println("1. Custom mapIndexed operator:")
        flowOf("A", "B", "C", "D")
            .mapIndexed { index, value -> "$index: $value" }
            .collect { println("  $it") }
        
        println()
        
        // Custom scan operator (like fold but emits intermediate results)
        fun <T, R> Flow<T>.scan(initial: R, operation: suspend (accumulator: R, value: T) -> R): Flow<R> = flow {
            var accumulator = initial
            emit(accumulator)
            collect { value ->
                accumulator = operation(accumulator, value)
                emit(accumulator)
            }
        }
        
        println("2. Custom scan operator:")
        flowOf(1, 2, 3, 4, 5)
            .scan(0) { acc, value -> acc + value }
            .collect { println("  Running sum: $it") }
        
        println()
        
        // Custom pairwise operator (emit pairs of consecutive values)
        fun <T> Flow<T>.pairwise(): Flow<Pair<T, T>> = flow {
            var previous: T? = null
            collect { current ->
                previous?.let { prev ->
                    emit(prev to current)
                }
                previous = current
            }
        }
        
        println("3. Custom pairwise operator:")
        flowOf(1, 2, 3, 4, 5)
            .pairwise()
            .collect { (prev, current) -> println("  $prev -> $current") }
        
        println()
        
        // Custom mapNotNull with type transformation
        inline fun <T, reified R> Flow<T>.mapNotNull(crossinline transform: suspend (T) -> R?): Flow<R> = flow {
            collect { value ->
                transform(value)?.let { emit(it) }
            }
        }
        
        println("4. Custom mapNotNull operator:")
        flowOf("1", "abc", "2", "def", "3")
            .mapNotNull { it.toIntOrNull() }
            .collect { println("  Parsed number: $it") }
        
        println("Simple transform operators completed\n")
    }
    
    fun demonstrateFilteringOperators() = runBlocking {
        println("=== Custom Filtering Operators ===")
        
        // Custom filterIndexed operator
        fun <T> Flow<T>.filterIndexed(predicate: suspend (index: Int, value: T) -> Boolean): Flow<T> = flow {
            var index = 0
            collect { value ->
                if (predicate(index++, value)) {
                    emit(value)
                }
            }
        }
        
        println("1. Custom filterIndexed operator:")
        flowOf("A", "B", "C", "D", "E", "F")
            .filterIndexed { index, _ -> index % 2 == 0 } // Even indices only
            .collect { println("  Even index: $it") }
        
        println()
        
        // Custom skipUntil operator
        fun <T> Flow<T>.skipUntil(predicate: suspend (T) -> Boolean): Flow<T> = flow {
            var shouldEmit = false
            collect { value ->
                if (!shouldEmit && predicate(value)) {
                    shouldEmit = true
                }
                if (shouldEmit) {
                    emit(value)
                }
            }
        }
        
        println("2. Custom skipUntil operator:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8)
            .skipUntil { it > 4 } // Skip until value > 4
            .collect { println("  After condition: $it") }
        
        println()
        
        // Custom takeWhileInclusive operator (includes the first failing element)
        fun <T> Flow<T>.takeWhileInclusive(predicate: suspend (T) -> Boolean): Flow<T> = flow {
            collect { value ->
                emit(value)
                if (!predicate(value)) {
                    return@flow // Stop after emitting the failing element
                }
            }
        }
        
        println("3. Custom takeWhileInclusive operator:")
        flowOf(2, 4, 6, 7, 8, 10)
            .takeWhileInclusive { it % 2 == 0 } // Take while even (including first odd)
            .collect { println("  Inclusive take: $it") }
        
        println()
        
        // Custom distinctBy with custom key selector
        fun <T, K> Flow<T>.distinctBy(keySelector: suspend (T) -> K): Flow<T> = flow {
            val seen = mutableSetOf<K>()
            collect { value ->
                val key = keySelector(value)
                if (seen.add(key)) {
                    emit(value)
                }
            }
        }
        
        data class Person(val name: String, val age: Int)
        
        println("4. Custom distinctBy operator:")
        flowOf(
            Person("Alice", 25),
            Person("Bob", 30),
            Person("Alice", 26), // Same name, different age
            Person("Charlie", 25)  // Same age, different name
        )
            .distinctBy { it.name } // Distinct by name only
            .collect { println("  Distinct person: $it") }
        
        println("Custom filtering operators completed\n")
    }
    
    fun demonstrateWindowingOperators() = runBlocking {
        println("=== Custom Windowing Operators ===")
        
        // Custom slidingWindow operator
        fun <T> Flow<T>.slidingWindow(size: Int): Flow<List<T>> = flow {
            val window = mutableListOf<T>()
            collect { value ->
                window.add(value)
                if (window.size > size) {
                    window.removeAt(0)
                }
                if (window.size == size) {
                    emit(window.toList())
                }
            }
        }
        
        println("1. Custom slidingWindow operator:")
        flowOf(1, 2, 3, 4, 5, 6, 7)
            .slidingWindow(3)
            .collect { window -> println("  Window: $window") }
        
        println()
        
        // Custom groupBy operator (groups consecutive elements)
        fun <T, K> Flow<T>.groupBy(keySelector: suspend (T) -> K): Flow<Pair<K, List<T>>> = flow {
            var currentKey: K? = null
            val currentGroup = mutableListOf<T>()
            
            collect { value ->
                val key = keySelector(value)
                
                if (currentKey != null && currentKey != key) {
                    // Emit the completed group
                    emit(currentKey!! to currentGroup.toList())
                    currentGroup.clear()
                }
                
                currentKey = key
                currentGroup.add(value)
            }
            
            // Emit the final group
            if (currentGroup.isNotEmpty() && currentKey != null) {
                emit(currentKey!! to currentGroup.toList())
            }
        }
        
        println("2. Custom groupBy operator:")
        flowOf(1, 1, 2, 2, 2, 3, 1, 1)
            .groupBy { it } // Group consecutive identical values
            .collect { (key, group) -> println("  Group $key: $group") }
        
        println()
        
        // Custom buffer with time and size limits
        fun <T> Flow<T>.bufferUntil(
            maxSize: Int,
            maxTimeMs: Long
        ): Flow<List<T>> = channelFlow {
            val buffer = mutableListOf<T>()
            var lastEmissionTime = System.currentTimeMillis()
            
            launch {
                while (!isClosedForSend) {
                    delay(maxTimeMs)
                    if (buffer.isNotEmpty()) {
                        send(buffer.toList())
                        buffer.clear()
                        lastEmissionTime = System.currentTimeMillis()
                    }
                }
            }
            
            collect { value ->
                buffer.add(value)
                
                if (buffer.size >= maxSize) {
                    send(buffer.toList())
                    buffer.clear()
                    lastEmissionTime = System.currentTimeMillis()
                }
            }
            
            // Emit remaining buffer
            if (buffer.isNotEmpty()) {
                send(buffer.toList())
            }
        }
        
        println("3. Custom bufferUntil operator:")
        flow {
            repeat(15) { i ->
                emit(i)
                delay(Random.nextLong(50, 200))
            }
        }
            .bufferUntil(maxSize = 5, maxTimeMs = 300)
            .collect { buffer -> println("  Buffer: $buffer (size: ${buffer.size})") }
        
        println("Custom windowing operators completed\n")
    }
}

/**
 * Stateful custom operators with internal state management
 */
class StatefulCustomOperators {
    
    fun demonstrateAccumulatingOperators() = runBlocking {
        println("=== Stateful Accumulating Operators ===")
        
        // Custom movingAverage operator
        fun Flow<Number>.movingAverage(windowSize: Int): Flow<Double> = flow {
            val window = mutableListOf<Double>()
            collect { value ->
                window.add(value.toDouble())
                if (window.size > windowSize) {
                    window.removeAt(0)
                }
                val average = window.average()
                emit(average)
            }
        }
        
        println("1. Custom movingAverage operator:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .movingAverage(3)
            .collect { avg -> println("  Moving average: ${"%.2f".format(avg)}") }
        
        println()
        
        // Custom exponentialMovingAverage operator
        fun Flow<Number>.exponentialMovingAverage(alpha: Double): Flow<Double> = flow {
            var ema: Double? = null
            collect { value ->
                val current = value.toDouble()
                ema = if (ema == null) {
                    current
                } else {
                    alpha * current + (1 - alpha) * ema!!
                }
                emit(ema!!)
            }
        }
        
        println("2. Custom exponentialMovingAverage operator:")
        flowOf(10, 12, 13, 12, 15, 16, 14, 13, 17, 18)
            .exponentialMovingAverage(0.3)
            .collect { ema -> println("  EMA: ${"%.2f".format(ema)}") }
        
        println()
        
        // Custom rateLimiter operator with token bucket
        fun <T> Flow<T>.rateLimiter(
            tokensPerSecond: Double,
            bucketSize: Int = tokensPerSecond.toInt()
        ): Flow<T> = flow {
            var tokens = bucketSize.toDouble()
            var lastRefillTime = System.currentTimeMillis()
            
            collect { value ->
                val now = System.currentTimeMillis()
                val elapsed = (now - lastRefillTime) / 1000.0
                tokens = minOf(bucketSize.toDouble(), tokens + elapsed * tokensPerSecond)
                lastRefillTime = now
                
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    emit(value)
                } else {
                    val waitTime = ((1.0 - tokens) / tokensPerSecond * 1000).toLong()
                    delay(waitTime)
                    tokens = 0.0
                    emit(value)
                }
            }
        }
        
        println("3. Custom rateLimiter operator (2 items/second):")
        val startTime = System.currentTimeMillis()
        flowOf("A", "B", "C", "D", "E")
            .onEach { delay(100) } // Fast emission
            .rateLimiter(tokensPerSecond = 2.0)
            .collect { value -> 
                val elapsed = System.currentTimeMillis() - startTime
                println("  $value at ${elapsed}ms") 
            }
        
        println()
        
        // Custom debounceLatest operator (debounce but always emit the latest)
        fun <T> Flow<T>.debounceLatest(timeoutMs: Long): Flow<T> = channelFlow {
            var latestValue: T? = null
            var debounceJob: Job? = null
            
            collect { value ->
                latestValue = value
                debounceJob?.cancel()
                
                debounceJob = launch {
                    delay(timeoutMs)
                    latestValue?.let { send(it) }
                }
            }
            
            debounceJob?.join()
        }
        
        println("4. Custom debounceLatest operator:")
        flow {
            emit("A")
            delay(50)
            emit("B")
            delay(50)
            emit("C")
            delay(300) // Long pause
            emit("D")
            delay(50)
            emit("E")
            delay(300) // Long pause
        }
            .debounceLatest(200)
            .collect { value -> println("  Debounced: $value") }
        
        println("Stateful operators completed\n")
    }
    
    fun demonstrateConditionalOperators() = runBlocking {
        println("=== Conditional Stateful Operators ===")
        
        // Custom retryWithBackoff operator
        fun <T> Flow<T>.retryWithBackoff(
            maxRetries: Int,
            initialDelayMs: Long = 100,
            backoffMultiplier: Double = 2.0,
            maxDelayMs: Long = 5000
        ): Flow<T> = flow {
            var currentDelay = initialDelayMs
            var attempt = 0
            
            while (attempt <= maxRetries) {
                try {
                    collect { emit(it) }
                    break // Success, exit retry loop
                } catch (e: Exception) {
                    if (attempt == maxRetries) {
                        throw e // Exhausted retries
                    }
                    
                    println("    Retry attempt ${attempt + 1} after ${currentDelay}ms delay")
                    delay(currentDelay)
                    
                    currentDelay = minOf(
                        (currentDelay * backoffMultiplier).toLong(),
                        maxDelayMs
                    )
                    attempt++
                }
            }
        }
        
        println("1. Custom retryWithBackoff operator:")
        var attempts = 0
        flow {
            attempts++
            println("    Attempt $attempts")
            if (attempts < 3) {
                throw RuntimeException("Simulated failure")
            }
            emit("Success!")
        }
            .retryWithBackoff(maxRetries = 3, initialDelayMs = 100)
            .catch { e -> emit("Failed: ${e.message}") }
            .collect { result -> println("  Result: $result") }
        
        println()
        
        // Custom circuit breaker operator
        fun <T> Flow<T>.circuitBreaker(
            failureThreshold: Int = 5,
            timeoutMs: Long = 10000,
            resetTimeoutMs: Long = 30000
        ): Flow<T> = flow {
            var failures = 0
            var lastFailureTime = 0L
            var circuitState = CircuitState.CLOSED
            
            collect { value ->
                val currentTime = System.currentTimeMillis()
                
                when (circuitState) {
                    CircuitState.CLOSED -> {
                        try {
                            // Simulate operation that might fail
                            if (Random.nextDouble() < 0.3) { // 30% failure rate
                                throw RuntimeException("Operation failed")
                            }
                            emit(value)
                            failures = 0 // Reset on success
                        } catch (e: Exception) {
                            failures++
                            lastFailureTime = currentTime
                            
                            if (failures >= failureThreshold) {
                                circuitState = CircuitState.OPEN
                                println("    Circuit breaker OPENED")
                            }
                            throw e
                        }
                    }
                    
                    CircuitState.OPEN -> {
                        if (currentTime - lastFailureTime >= resetTimeoutMs) {
                            circuitState = CircuitState.HALF_OPEN
                            println("    Circuit breaker HALF-OPEN")
                        } else {
                            throw RuntimeException("Circuit breaker is OPEN")
                        }
                    }
                    
                    CircuitState.HALF_OPEN -> {
                        try {
                            emit(value)
                            circuitState = CircuitState.CLOSED
                            failures = 0
                            println("    Circuit breaker CLOSED")
                        } catch (e: Exception) {
                            circuitState = CircuitState.OPEN
                            lastFailureTime = currentTime
                            println("    Circuit breaker OPENED again")
                            throw e
                        }
                    }
                }
            }
        }
        
        enum class CircuitState { CLOSED, OPEN, HALF_OPEN }
        
        println("2. Custom circuit breaker operator:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .circuitBreaker(failureThreshold = 2, resetTimeoutMs = 500)
            .catch { e -> 
                println("    Caught: ${e.message}")
                emit(-1) // Fallback value
            }
            .collect { result -> 
                if (result != -1) {
                    println("  Success: $result")
                } else {
                    println("  Fallback value")
                }
            }
        
        println()
        
        // Custom conditionalTransform operator
        fun <T, R> Flow<T>.conditionalTransform(
            condition: suspend (T) -> Boolean,
            ifTrue: suspend (T) -> R,
            ifFalse: suspend (T) -> R
        ): Flow<R> = flow {
            collect { value ->
                val result = if (condition(value)) {
                    ifTrue(value)
                } else {
                    ifFalse(value)
                }
                emit(result)
            }
        }
        
        println("3. Custom conditionalTransform operator:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .conditionalTransform(
                condition = { it % 2 == 0 },
                ifTrue = { "Even: $it" },
                ifFalse = { "Odd: $it" }
            )
            .collect { result -> println("  $result") }
        
        println("Conditional operators completed\n")
    }
    
    fun demonstrateAdaptiveOperators() = runBlocking {
        println("=== Adaptive Stateful Operators ===")
        
        // Custom adaptive buffer operator
        fun <T> Flow<T>.adaptiveBuffer(): Flow<T> = channelFlow {
            var bufferSize = 16
            var throughputHistory = mutableListOf<Double>()
            var lastMeasureTime = System.currentTimeMillis()
            var itemCount = 0
            
            collect { value ->
                itemCount++
                
                // Measure throughput every 100 items
                if (itemCount % 100 == 0) {
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastMeasureTime) / 1000.0
                    val throughput = 100 / elapsed
                    
                    throughputHistory.add(throughput)
                    if (throughputHistory.size > 5) {
                        throughputHistory.removeAt(0)
                    }
                    
                    val avgThroughput = throughputHistory.average()
                    
                    // Adjust buffer size based on throughput
                    bufferSize = when {
                        avgThroughput > 200 -> minOf(bufferSize * 2, 256)
                        avgThroughput < 50 -> maxOf(bufferSize / 2, 4)
                        else -> bufferSize
                    }
                    
                    println("    Adaptive buffer adjusted to $bufferSize (throughput: ${"%.1f".format(avgThroughput)})")
                    lastMeasureTime = now
                }
                
                send(value)
            }
        }.buffer(16) // Start with base buffer
        
        println("1. Custom adaptive buffer operator:")
        flow {
            repeat(500) { i ->
                emit(i)
                delay(Random.nextLong(1, 20)) // Variable emission rate
            }
        }
            .adaptiveBuffer()
            .collect { value ->
                delay(Random.nextLong(5, 15)) // Variable processing time
                if (value % 100 == 0) {
                    println("  Processed: $value")
                }
            }
        
        println()
        
        // Custom smart retry operator that adapts based on error types
        fun <T> Flow<T>.smartRetry(
            maxRetries: Int = 3
        ): Flow<T> = flow {
            var networkRetries = 0
            var processingRetries = 0
            
            while (true) {
                try {
                    collect { emit(it) }
                    break
                } catch (e: Exception) {
                    when {
                        e.message?.contains("network") == true -> {
                            networkRetries++
                            if (networkRetries > maxRetries) throw e
                            delay(networkRetries * 1000L) // Longer delay for network issues
                            println("    Network retry $networkRetries")
                        }
                        
                        e.message?.contains("processing") == true -> {
                            processingRetries++
                            if (processingRetries > maxRetries) throw e
                            delay(processingRetries * 200L) // Shorter delay for processing issues
                            println("    Processing retry $processingRetries")
                        }
                        
                        else -> throw e // Don't retry other errors
                    }
                }
            }
        }
        
        println("2. Custom smart retry operator:")
        var attempt = 0
        flow {
            attempt++
            when {
                attempt <= 2 -> throw RuntimeException("network error")
                attempt <= 4 -> throw RuntimeException("processing error") 
                else -> emit("Success after smart retries")
            }
        }
            .smartRetry(maxRetries = 3)
            .catch { e -> emit("Final failure: ${e.message}") }
            .collect { result -> println("  $result") }
        
        println("Adaptive operators completed\n")
    }
}

/**
 * Advanced composite operators
 */
class CompositeCustomOperators {
    
    fun demonstrateFlowCombinationOperators() = runBlocking {
        println("=== Composite Flow Combination Operators ===")
        
        // Custom zipWithTimeout operator
        fun <T1, T2, R> Flow<T1>.zipWithTimeout(
            other: Flow<T2>,
            timeoutMs: Long,
            transform: suspend (T1, T2) -> R
        ): Flow<R> = channelFlow {
            val deferred1 = async { other.toList() }
            val deferred2 = async { this@zipWithTimeout.toList() }
            
            try {
                val list1 = withTimeout(timeoutMs) { deferred1.await() }
                val list2 = withTimeout(timeoutMs) { deferred2.await() }
                
                list1.zip(list2).forEach { (a, b) ->
                    send(transform(a, b))
                }
            } catch (e: TimeoutCancellationException) {
                println("    Zip operation timed out")
                deferred1.cancel()
                deferred2.cancel()
                throw e
            }
        }
        
        println("1. Custom zipWithTimeout operator:")
        val flow1 = flow {
            repeat(5) { i ->
                emit("A$i")
                delay(100)
            }
        }
        
        val flow2 = flow {
            repeat(5) { i ->
                emit("B$i")
                delay(150)
            }
        }
        
        flow1.zipWithTimeout(flow2, 1000) { a, b -> "$a+$b" }
            .collect { result -> println("  Zipped: $result") }
        
        println()
        
        // Custom mergeWithPriority operator
        fun <T> mergeWithPriority(vararg flows: Pair<Flow<T>, Int>): Flow<T> = channelFlow {
            val priorityQueues = mutableMapOf<Int, MutableList<T>>()
            
            flows.forEach { (flow, priority) ->
                launch {
                    flow.collect { value ->
                        synchronized(priorityQueues) {
                            priorityQueues.getOrPut(priority) { mutableListOf() }.add(value)
                        }
                        
                        // Emit from highest priority queue
                        synchronized(priorityQueues) {
                            val highestPriority = priorityQueues.keys.maxOrNull()
                            if (highestPriority != null) {
                                val queue = priorityQueues[highestPriority]!!
                                if (queue.isNotEmpty()) {
                                    val item = queue.removeAt(0)
                                    if (queue.isEmpty()) {
                                        priorityQueues.remove(highestPriority)
                                    }
                                    send(item)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        println("2. Custom mergeWithPriority operator:")
        val highPriorityFlow = flow {
            delay(200)
            emit("HIGH-1")
            delay(400)
            emit("HIGH-2")
        }
        
        val mediumPriorityFlow = flow {
            delay(100)
            emit("MEDIUM-1")
            delay(300)
            emit("MEDIUM-2")
            delay(200)
            emit("MEDIUM-3")
        }
        
        val lowPriorityFlow = flow {
            repeat(5) { i ->
                emit("LOW-$i")
                delay(150)
            }
        }
        
        mergeWithPriority(
            highPriorityFlow to 3,
            mediumPriorityFlow to 2,
            lowPriorityFlow to 1
        )
            .take(8)
            .collect { result -> println("  Priority merged: $result") }
        
        println()
        
        // Custom switchMapWithFallback operator
        fun <T, R> Flow<T>.switchMapWithFallback(
            fallback: suspend (T, Exception) -> Flow<R>,
            transform: suspend (T) -> Flow<R>
        ): Flow<R> = flow {
            collect { value ->
                try {
                    transform(value).collect { emit(it) }
                } catch (e: Exception) {
                    println("    Switching to fallback for $value due to: ${e.message}")
                    fallback(value, e).collect { emit(it) }
                }
            }
        }
        
        println("3. Custom switchMapWithFallback operator:")
        flowOf("valid1", "invalid", "valid2")
            .switchMapWithFallback(
                fallback = { value, _ -> flowOf("FALLBACK-$value") },
                transform = { value ->
                    if (value.contains("invalid")) {
                        throw RuntimeException("Invalid input")
                    }
                    flowOf("SUCCESS-$value")
                }
            )
            .collect { result -> println("  Fallback result: $result") }
        
        println("Composite operators completed\n")
    }
    
    fun demonstrateComplexProcessingOperators() = runBlocking {
        println("=== Complex Processing Operators ===")
        
        // Custom batchProcessor operator
        fun <T, R> Flow<T>.batchProcessor(
            batchSize: Int,
            timeoutMs: Long,
            processor: suspend (List<T>) -> List<R>
        ): Flow<R> = channelFlow {
            val batch = mutableListOf<T>()
            var batchJob: Job? = null
            
            suspend fun processBatch() {
                if (batch.isNotEmpty()) {
                    val currentBatch = batch.toList()
                    batch.clear()
                    
                    try {
                        val results = processor(currentBatch)
                        results.forEach { send(it) }
                    } catch (e: Exception) {
                        println("    Batch processing failed: ${e.message}")
                    }
                }
            }
            
            collect { value ->
                batch.add(value)
                
                // Cancel previous timeout job
                batchJob?.cancel()
                
                if (batch.size >= batchSize) {
                    processBatch()
                } else {
                    // Set timeout for partial batch
                    batchJob = launch {
                        delay(timeoutMs)
                        processBatch()
                    }
                }
            }
            
            // Process final batch
            processBatch()
        }
        
        println("1. Custom batchProcessor operator:")
        flow {
            repeat(25) { i ->
                emit(i)
                delay(Random.nextLong(50, 200))
            }
        }
            .batchProcessor(
                batchSize = 5,
                timeoutMs = 500,
                processor = { batch ->
                    delay(100) // Simulate batch processing
                    batch.map { "Batch-processed: $it (batch size: ${batch.size})" }
                }
            )
            .collect { result -> println("  $result") }
        
        println()
        
        // Custom pipeline operator
        fun <T> Flow<T>.pipeline(
            stages: List<suspend (T) -> T>
        ): Flow<T> = flow {
            collect { input ->
                var result = input
                for ((index, stage) in stages.withIndex()) {
                    try {
                        result = stage(result)
                    } catch (e: Exception) {
                        println("    Pipeline stage $index failed: ${e.message}")
                        return@collect // Skip this item
                    }
                }
                emit(result)
            }
        }
        
        println("2. Custom pipeline operator:")
        
        val stages = listOf<suspend (Int) -> Int>(
            { value -> 
                delay(50)
                value * 2 
            },
            { value ->
                if (value > 10) throw RuntimeException("Value too large")
                delay(30)
                value + 1
            },
            { value ->
                delay(20)
                value * value
            }
        )
        
        flowOf(1, 2, 3, 4, 5, 6)
            .pipeline(stages)
            .collect { result -> println("  Pipeline result: $result") }
        
        println()
        
        // Custom validator operator with error collection
        fun <T> Flow<T>.validateWithErrors(
            validators: List<suspend (T) -> String?>
        ): Flow<ValidationResult<T>> = flow {
            collect { value ->
                val errors = mutableListOf<String>()
                
                for (validator in validators) {
                    validator(value)?.let { error ->
                        errors.add(error)
                    }
                }
                
                val result = if (errors.isEmpty()) {
                    ValidationResult.Success(value)
                } else {
                    ValidationResult.Failure(value, errors)
                }
                
                emit(result)
            }
        }
        
        sealed class ValidationResult<T> {
            data class Success<T>(val value: T) : ValidationResult<T>()
            data class Failure<T>(val value: T, val errors: List<String>) : ValidationResult<T>()
        }
        
        println("3. Custom validation operator:")
        
        data class User(val name: String, val age: Int, val email: String)
        
        val userValidators = listOf<suspend (User) -> String?>(
            { user -> if (user.name.isBlank()) "Name cannot be empty" else null },
            { user -> if (user.age < 0) "Age cannot be negative" else null },
            { user -> if (!user.email.contains("@")) "Invalid email format" else null }
        )
        
        flowOf(
            User("Alice", 25, "alice@example.com"),
            User("", 30, "bob@example.com"),
            User("Charlie", -5, "invalid-email"),
            User("Diana", 28, "diana@example.com")
        )
            .validateWithErrors(userValidators)
            .collect { result ->
                when (result) {
                    is ValidationResult.Success -> 
                        println("  Valid user: ${result.value}")
                    is ValidationResult.Failure -> 
                        println("  Invalid user: ${result.value} - Errors: ${result.errors}")
                }
            }
        
        println("Complex processing operators completed\n")
    }
}

/**
 * Domain-specific operators
 */
class DomainSpecificOperators {
    
    fun demonstrateFinancialOperators() = runBlocking {
        println("=== Financial Domain Operators ===")
        
        data class StockPrice(val symbol: String, val price: Double, val timestamp: Long)
        data class TechnicalIndicator(val symbol: String, val value: Double, val type: String, val timestamp: Long)
        
        // Custom simple moving average operator for stock prices
        fun Flow<StockPrice>.simpleMovingAverage(period: Int): Flow<TechnicalIndicator> = flow {
            val priceWindow = mutableListOf<Double>()
            
            collect { stockPrice ->
                priceWindow.add(stockPrice.price)
                
                if (priceWindow.size > period) {
                    priceWindow.removeAt(0)
                }
                
                if (priceWindow.size == period) {
                    val sma = priceWindow.average()
                    emit(TechnicalIndicator(
                        symbol = stockPrice.symbol,
                        value = sma,
                        type = "SMA_$period",
                        timestamp = stockPrice.timestamp
                    ))
                }
            }
        }
        
        // Custom RSI (Relative Strength Index) operator
        fun Flow<StockPrice>.rsi(period: Int = 14): Flow<TechnicalIndicator> = flow {
            val priceChanges = mutableListOf<Double>()
            var previousPrice: Double? = null
            
            collect { stockPrice ->
                previousPrice?.let { prev ->
                    val change = stockPrice.price - prev
                    priceChanges.add(change)
                    
                    if (priceChanges.size > period) {
                        priceChanges.removeAt(0)
                    }
                    
                    if (priceChanges.size == period) {
                        val gains = priceChanges.filter { it > 0 }.average()
                        val losses = -priceChanges.filter { it < 0 }.average()
                        
                        val rsi = if (losses != 0.0) {
                            100 - (100 / (1 + gains / losses))
                        } else {
                            100.0
                        }
                        
                        emit(TechnicalIndicator(
                            symbol = stockPrice.symbol,
                            value = rsi,
                            type = "RSI",
                            timestamp = stockPrice.timestamp
                        ))
                    }
                }
                previousPrice = stockPrice.price
            }
        }
        
        println("1. Financial indicators:")
        
        // Simulate stock price feed
        flow {
            var price = 100.0
            repeat(20) { i ->
                price += Random.nextDouble(-5.0, 5.0)
                emit(StockPrice("AAPL", price, System.currentTimeMillis()))
                delay(100)
            }
        }
            .simpleMovingAverage(5)
            .collect { indicator -> 
                println("  SMA: ${indicator.symbol} = ${"%.2f".format(indicator.value)}")
            }
        
        println()
        
        // Custom risk assessment operator
        fun Flow<StockPrice>.riskAssessment(): Flow<String> = flow {
            val volatilityWindow = mutableListOf<Double>()
            var previousPrice: Double? = null
            
            collect { stockPrice ->
                previousPrice?.let { prev ->
                    val percentChange = ((stockPrice.price - prev) / prev) * 100
                    volatilityWindow.add(kotlin.math.abs(percentChange))
                    
                    if (volatilityWindow.size > 10) {
                        volatilityWindow.removeAt(0)
                    }
                    
                    if (volatilityWindow.size >= 5) {
                        val avgVolatility = volatilityWindow.average()
                        val riskLevel = when {
                            avgVolatility > 5.0 -> "HIGH RISK"
                            avgVolatility > 2.0 -> "MEDIUM RISK" 
                            else -> "LOW RISK"
                        }
                        emit("${stockPrice.symbol}: $riskLevel (volatility: ${"%.2f".format(avgVolatility)}%)")
                    }
                }
                previousPrice = stockPrice.price
            }
        }
        
        println("2. Risk assessment:")
        flow {
            var price = 50.0
            repeat(15) { i ->
                price += Random.nextDouble(-8.0, 8.0) // Higher volatility
                emit(StockPrice("VOLATILE", price, System.currentTimeMillis()))
                delay(50)
            }
        }
            .riskAssessment()
            .collect { assessment -> println("  $assessment") }
        
        println("Financial operators completed\n")
    }
    
    fun demonstrateIoTOperators() = runBlocking {
        println("=== IoT Domain Operators ===")
        
        data class SensorReading(val sensorId: String, val value: Double, val timestamp: Long, val unit: String)
        data class Alert(val message: String, val severity: String, val timestamp: Long)
        
        // Custom anomaly detection operator
        fun Flow<SensorReading>.anomalyDetection(
            windowSize: Int = 10,
            threshold: Double = 2.0
        ): Flow<Alert> = flow {
            val readings = mutableMapOf<String, MutableList<Double>>()
            
            collect { reading ->
                val sensorReadings = readings.getOrPut(reading.sensorId) { mutableListOf() }
                sensorReadings.add(reading.value)
                
                if (sensorReadings.size > windowSize) {
                    sensorReadings.removeAt(0)
                }
                
                if (sensorReadings.size >= 5) { // Minimum samples for anomaly detection
                    val mean = sensorReadings.average()
                    val variance = sensorReadings.map { (it - mean) * (it - mean) }.average()
                    val stdDev = kotlin.math.sqrt(variance)
                    
                    if (kotlin.math.abs(reading.value - mean) > threshold * stdDev) {
                        emit(Alert(
                            message = "Anomaly detected in ${reading.sensorId}: ${reading.value} ${reading.unit} " +
                                     "(expected: ${"%.2f".format(mean)} ± ${"%.2f".format(stdDev)})",
                            severity = if (kotlin.math.abs(reading.value - mean) > 3 * stdDev) "CRITICAL" else "WARNING",
                            timestamp = reading.timestamp
                        ))
                    }
                }
            }
        }
        
        // Custom sensor calibration operator
        fun Flow<SensorReading>.calibrate(
            calibrationMap: Map<String, (Double) -> Double>
        ): Flow<SensorReading> = flow {
            collect { reading ->
                val calibrationFunc = calibrationMap[reading.sensorId]
                val calibratedValue = calibrationFunc?.invoke(reading.value) ?: reading.value
                emit(reading.copy(value = calibratedValue))
            }
        }
        
        println("1. Sensor data processing with anomaly detection:")
        
        // Simulate multiple sensors
        merge(
            flow {
                repeat(20) { i ->
                    val value = 20.0 + Random.nextGaussian() * 2.0 + // Normal readings
                               if (i == 10) 15.0 else 0.0 // Inject anomaly
                    emit(SensorReading("TEMP_01", value, System.currentTimeMillis(), "°C"))
                    delay(200)
                }
            },
            flow {
                repeat(15) { i ->
                    val value = 50.0 + Random.nextGaussian() * 5.0 +
                               if (i == 7) 25.0 else 0.0 // Inject anomaly
                    emit(SensorReading("HUMIDITY_01", value, System.currentTimeMillis(), "%"))
                    delay(300)
                }
            }
        )
            .calibrate(mapOf(
                "TEMP_01" to { temp -> temp * 0.95 + 1.2 }, // Temperature calibration
                "HUMIDITY_01" to { humidity -> humidity * 1.02 - 0.8 } // Humidity calibration
            ))
            .anomalyDetection(windowSize = 8, threshold = 2.0)
            .collect { alert ->
                println("  🚨 ${alert.severity}: ${alert.message}")
            }
        
        println()
        
        // Custom energy efficiency operator
        data class EnergyReading(val deviceId: String, val powerConsumption: Double, val timestamp: Long)
        
        fun Flow<EnergyReading>.energyEfficiencyAnalysis(): Flow<String> = flow {
            val consumptionHistory = mutableMapOf<String, MutableList<Double>>()
            
            collect { reading ->
                val history = consumptionHistory.getOrPut(reading.deviceId) { mutableListOf() }
                history.add(reading.powerConsumption)
                
                if (history.size > 24) { // Keep last 24 readings (simulate 24 hours)
                    history.removeAt(0)
                }
                
                if (history.size >= 12) { // Minimum data for analysis
                    val avgConsumption = history.average()
                    val maxConsumption = history.maxOrNull() ?: 0.0
                    val efficiency = (avgConsumption / maxConsumption) * 100
                    
                    val recommendation = when {
                        efficiency < 60 -> "OPTIMIZE: Device running inefficiently"
                        efficiency > 85 -> "EFFICIENT: Device operating optimally"
                        else -> "MONITOR: Device efficiency is acceptable"
                    }
                    
                    emit("${reading.deviceId}: $recommendation (efficiency: ${"%.1f".format(efficiency)}%)")
                }
            }
        }
        
        println("2. Energy efficiency analysis:")
        flow {
            repeat(30) { i ->
                val baseConsumption = 100.0
                val variation = Random.nextGaussian() * 20.0
                val timeOfDayFactor = if (i % 24 in 8..18) 1.3 else 0.8 // Higher during day
                val consumption = baseConsumption * timeOfDayFactor + variation
                
                emit(EnergyReading("HVAC_SYSTEM", consumption, System.currentTimeMillis()))
                delay(50)
            }
        }
            .energyEfficiencyAnalysis()
            .distinctUntilChanged() // Only emit when recommendation changes
            .collect { recommendation -> println("  💡 $recommendation") }
        
        println("IoT operators completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic custom operators
        BasicCustomOperators().demonstrateSimpleTransformOperators()
        BasicCustomOperators().demonstrateFilteringOperators()
        BasicCustomOperators().demonstrateWindowingOperators()
        
        // Stateful operators
        StatefulCustomOperators().demonstrateAccumulatingOperators()
        StatefulCustomOperators().demonstrateConditionalOperators()
        StatefulCustomOperators().demonstrateAdaptiveOperators()
        
        // Composite operators
        CompositeCustomOperators().demonstrateFlowCombinationOperators()
        CompositeCustomOperators().demonstrateComplexProcessingOperators()
        
        // Domain-specific operators
        DomainSpecificOperators().demonstrateFinancialOperators()
        DomainSpecificOperators().demonstrateIoTOperators()
        
        println("=== Custom Flow Operators Summary ===")
        println("✅ Basic Custom Operators:")
        println("   - Simple transforms: mapIndexed, scan, pairwise")
        println("   - Advanced filters: filterIndexed, skipUntil, takeWhileInclusive")
        println("   - Windowing: slidingWindow, groupBy, bufferUntil")
        println()
        println("✅ Stateful Operators:")
        println("   - Accumulators: movingAverage, exponentialMovingAverage")
        println("   - Rate control: rateLimiter, debounceLatest") 
        println("   - Resilience: retryWithBackoff, circuitBreaker")
        println("   - Adaptive: adaptiveBuffer, smartRetry")
        println()
        println("✅ Composite Operators:")
        println("   - Flow combination: zipWithTimeout, mergeWithPriority")
        println("   - Processing: batchProcessor, pipeline, validator")
        println("   - Error handling: switchMapWithFallback")
        println()
        println("✅ Domain-Specific:")
        println("   - Financial: simpleMovingAverage, RSI, riskAssessment")
        println("   - IoT: anomalyDetection, calibrate, energyEfficiency")
        println("   - Custom business logic operators")
        println()
        println("✅ Design Principles:")
        println("   - Follow Flow contracts and conventions")
        println("   - Handle exceptions and cancellation properly")
        println("   - Manage state carefully to avoid memory leaks")
        println("   - Consider performance characteristics")
        println("   - Provide clear, composable APIs")
    }
}