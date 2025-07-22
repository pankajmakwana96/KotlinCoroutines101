/**
 * # Flow Transformations and Data Processing
 * 
 * ## Problem Description
 * Real-world data streams require complex transformations: filtering, mapping,
 * grouping, windowing, and aggregation. Simple map/filter operations are not
 * sufficient for advanced data processing scenarios like real-time analytics,
 * event processing, and reactive programming patterns.
 * 
 * ## Solution Approach
 * Advanced flow transformations include:
 * - Complex data mapping and filtering patterns
 * - Windowing and chunking operations
 * - Stateful transformations with accumulators
 * - Custom transformation operators
 * - Performance-optimized transformation chains
 * 
 * ## Key Learning Points
 * - Stateful vs stateless transformations
 * - Memory implications of windowing operations
 * - Custom operator creation patterns
 * - Performance characteristics of different operators
 * - Backpressure considerations in transformations
 * 
 * ## Performance Considerations
 * - Map operation overhead: ~20-50ns per element
 * - Filter operation overhead: ~10-30ns per element
 * - Windowing memory usage: O(window_size) per window
 * - Stateful operations require careful memory management
 * 
 * ## Common Pitfalls
 * - Memory leaks in stateful transformations
 * - Incorrect windowing logic
 * - Performance degradation with large datasets
 * - Complex transformation chains reducing readability
 * - Missing error handling in custom operators
 * 
 * ## Real-World Applications
 * - Data stream processing and ETL pipelines
 * - Real-time analytics and monitoring
 * - Event sourcing and CQRS patterns
 * - Reactive UI state management
 * - IoT data processing
 * 
 * ## Related Concepts
 * - FlowBasics.kt - Basic flow operations
 * - Channels - Hot stream alternatives
 * - Sequences - Synchronous transformations
 */

package flow.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Advanced mapping and filtering transformations
 * 
 * Transformation Pipeline:
 * 
 * Input Stream ──> Filter ──> Map ──> Transform ──> Output Stream
 *      │             │         │         │             │
 *      └─ Raw Data   └─ Valid  └─ Typed  └─ Enriched   └─ Results
 * 
 * Conditional Transformations:
 * 
 * Input ──┬── Condition A ──> Transform A ──┬──> Output
 *         ├── Condition B ──> Transform B ──┤
 *         └── Default ────────> Transform C ──┘
 */
class AdvancedMappingFiltering {
    
    data class UserEvent(
        val userId: String,
        val eventType: String,
        val timestamp: Long,
        val data: Map<String, Any> = emptyMap()
    )
    
    data class EnrichedEvent(
        val userId: String,
        val eventType: String,
        val timestamp: Long,
        val data: Map<String, Any>,
        val userProfile: UserProfile?,
        val processingTime: Long
    )
    
    data class UserProfile(
        val userId: String,
        val name: String,
        val tier: String,
        val preferences: Map<String, String>
    )
    
    fun demonstrateConditionalMapping() = runBlocking {
        println("=== Conditional Mapping ===")
        
        val userProfiles = mapOf(
            "user1" to UserProfile("user1", "Alice", "premium", mapOf("theme" to "dark")),
            "user2" to UserProfile("user2", "Bob", "standard", mapOf("theme" to "light")),
            "user3" to UserProfile("user3", "Carol", "premium", mapOf("theme" to "dark"))
        )
        
        suspend fun enrichEvent(event: UserEvent): EnrichedEvent {
            delay(Random.nextLong(10, 50)) // Simulate database lookup
            val profile = userProfiles[event.userId]
            return EnrichedEvent(
                userId = event.userId,
                eventType = event.eventType,
                timestamp = event.timestamp,
                data = event.data,
                userProfile = profile,
                processingTime = System.currentTimeMillis()
            )
        }
        
        val eventStream = flow {
            val events = listOf(
                UserEvent("user1", "login", System.currentTimeMillis()),
                UserEvent("user2", "purchase", System.currentTimeMillis(), mapOf("amount" to 99.99)),
                UserEvent("unknown", "view", System.currentTimeMillis()),
                UserEvent("user3", "logout", System.currentTimeMillis()),
                UserEvent("user1", "purchase", System.currentTimeMillis(), mapOf("amount" to 149.99))
            )
            
            events.forEach { event ->
                emit(event)
                delay(100)
            }
        }
        
        println("Processing events with conditional enrichment:")
        
        eventStream
            .filter { event ->
                // Filter out events from unknown users for performance
                event.userId != "unknown"
            }
            .map { event ->
                when (event.eventType) {
                    "purchase" -> {
                        // Enrich purchase events with full profile
                        enrichEvent(event)
                    }
                    "login", "logout" -> {
                        // Basic enrichment for auth events
                        EnrichedEvent(
                            userId = event.userId,
                            eventType = event.eventType,
                            timestamp = event.timestamp,
                            data = event.data,
                            userProfile = userProfiles[event.userId],
                            processingTime = System.currentTimeMillis()
                        )
                    }
                    else -> {
                        // Minimal processing for other events
                        EnrichedEvent(
                            userId = event.userId,
                            eventType = event.eventType,
                            timestamp = event.timestamp,
                            data = event.data,
                            userProfile = null,
                            processingTime = System.currentTimeMillis()
                        )
                    }
                }
            }
            .collect { enrichedEvent ->
                val profile = enrichedEvent.userProfile
                val profileInfo = if (profile != null) {
                    "${profile.name} (${profile.tier})"
                } else {
                    "No profile"
                }
                println("  ${enrichedEvent.eventType} from ${enrichedEvent.userId}: $profileInfo")
            }
        
        println("Conditional mapping completed\n")
    }
    
    fun demonstrateFilteringPatterns() = runBlocking {
        println("=== Advanced Filtering Patterns ===")
        
        data class SensorReading(
            val sensorId: String,
            val value: Double,
            val timestamp: Long,
            val quality: String
        )
        
        val sensorData = flow {
            repeat(20) { i ->
                val reading = SensorReading(
                    sensorId = "sensor-${Random.nextInt(1, 4)}",
                    value = Random.nextDouble(-10.0, 50.0),
                    timestamp = System.currentTimeMillis(),
                    quality = listOf("good", "fair", "poor").random()
                )
                emit(reading)
                delay(50)
            }
        }
        
        // 1. Multi-condition filtering
        println("1. Multi-condition filtering (good quality, reasonable values):")
        sensorData
            .filter { reading ->
                reading.quality == "good" && 
                reading.value in 0.0..40.0
            }
            .collect { reading ->
                println("  Valid reading: ${reading.sensorId} = ${String.format("%.1f", reading.value)}")
            }
        
        println()
        
        // 2. Filtering with side effects
        println("2. Filtering with anomaly detection:")
        var anomalyCount = 0
        
        sensorData
            .filter { reading ->
                if (reading.value < -5.0 || reading.value > 45.0) {
                    anomalyCount++
                    println("  ⚠️ Anomaly detected: ${reading.sensorId} = ${reading.value}")
                    false // Filter out anomalies
                } else {
                    true
                }
            }
            .collect { reading ->
                println("  Normal reading: ${reading.sensorId} = ${String.format("%.1f", reading.value)}")
            }
        
        println("  Total anomalies detected: $anomalyCount")
        
        println("Advanced filtering completed\n")
    }
    
    fun demonstrateTransformOperator() = runBlocking {
        println("=== Transform Operator Patterns ===")
        
        // Transform allows multiple emissions per input
        println("1. Transform with multiple emissions:")
        flowOf("hello", "world", "kotlin")
            .transform { word ->
                emit("START: $word")
                emit("LENGTH: ${word.length}")
                emit("UPPER: ${word.uppercase()}")
                emit("END: $word")
            }
            .collect { println("  $it") }
        
        println()
        
        // Transform with conditional emissions
        println("2. Conditional transform:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .transform { number ->
                when {
                    number % 3 == 0 -> {
                        emit("FIZZ: $number")
                        emit("DIVISIBLE_BY_3: $number")
                    }
                    number % 2 == 0 -> {
                        emit("EVEN: $number")
                    }
                    else -> {
                        emit("ODD: $number")
                    }
                }
            }
            .collect { println("  $it") }
        
        println()
        
        // Transform with async operations
        println("3. Transform with async operations:")
        flowOf("apple", "banana", "cherry")
            .transform { fruit ->
                // Simulate async API call
                delay(Random.nextLong(50, 150))
                
                // Emit original
                emit("Original: $fruit")
                
                // Emit translated (simulated)
                val translations = mapOf(
                    "apple" to "manzana",
                    "banana" to "plátano", 
                    "cherry" to "cereza"
                )
                emit("Spanish: ${translations[fruit] ?: "unknown"}")
                
                // Emit metadata
                emit("Length: ${fruit.length}")
            }
            .collect { println("  $it") }
        
        println("Transform operator patterns completed\n")
    }
}

/**
 * Stateful transformations and accumulators
 */
class StatefulTransformations {
    
    fun demonstrateRunningAggregations() = runBlocking {
        println("=== Running Aggregations ===")
        
        // scan - running accumulation
        println("1. Running sum with scan:")
        flowOf(1, 2, 3, 4, 5)
            .scan(0) { accumulator, value -> accumulator + value }
            .collect { println("  Running sum: $it") }
        
        println()
        
        // Custom running statistics
        println("2. Running statistics:")
        data class Stats(
            val count: Int = 0,
            val sum: Double = 0.0,
            val min: Double = Double.MAX_VALUE,
            val max: Double = Double.MIN_VALUE
        ) {
            val average: Double get() = if (count > 0) sum / count else 0.0
        }
        
        flowOf(2.5, 1.8, 4.2, 3.1, 5.7, 2.9)
            .scan(Stats()) { stats, value ->
                Stats(
                    count = stats.count + 1,
                    sum = stats.sum + value,
                    min = minOf(stats.min, value),
                    max = maxOf(stats.max, value)
                )
            }
            .drop(1) // Skip initial empty stats
            .collect { stats ->
                println("  Count: ${stats.count}, Avg: ${String.format("%.2f", stats.average)}, " +
                        "Min: ${stats.min}, Max: ${stats.max}")
            }
        
        println()
        
        // Moving average
        println("3. Moving average (window size 3):")
        val windowSize = 3
        val window = mutableListOf<Double>()
        
        flowOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
            .transform { value ->
                window.add(value)
                if (window.size > windowSize) {
                    window.removeAt(0)
                }
                
                if (window.size == windowSize) {
                    val average = window.average()
                    emit("Value: $value, Moving Avg: ${String.format("%.2f", average)}")
                }
            }
            .collect { println("  $it") }
        
        println("Running aggregations completed\n")
    }
    
    fun demonstrateStateManagement() = runBlocking {
        println("=== Flow State Management ===")
        
        data class ShoppingCart(
            val items: Map<String, Int> = emptyMap(),
            val total: Double = 0.0
        )
        
        data class CartEvent(
            val type: String, // "add", "remove", "clear"
            val item: String = "",
            val quantity: Int = 0,
            val price: Double = 0.0
        )
        
        val cartEvents = flowOf(
            CartEvent("add", "apple", 3, 1.50),
            CartEvent("add", "banana", 2, 0.80),
            CartEvent("add", "apple", 2, 1.50), // Add more apples
            CartEvent("remove", "banana", 1, 0.80),
            CartEvent("add", "orange", 4, 2.00),
            CartEvent("clear")
        )
        
        println("Shopping cart state management:")
        cartEvents
            .scan(ShoppingCart()) { cart, event ->
                when (event.type) {
                    "add" -> {
                        val currentQuantity = cart.items[event.item] ?: 0
                        val newQuantity = currentQuantity + event.quantity
                        val newItems = cart.items + (event.item to newQuantity)
                        val newTotal = cart.total + (event.quantity * event.price)
                        
                        ShoppingCart(newItems, newTotal)
                    }
                    "remove" -> {
                        val currentQuantity = cart.items[event.item] ?: 0
                        val newQuantity = maxOf(0, currentQuantity - event.quantity)
                        val newItems = if (newQuantity > 0) {
                            cart.items + (event.item to newQuantity)
                        } else {
                            cart.items - event.item
                        }
                        val newTotal = cart.total - (event.quantity * event.price)
                        
                        ShoppingCart(newItems, maxOf(0.0, newTotal))
                    }
                    "clear" -> ShoppingCart()
                    else -> cart
                }
            }
            .collect { cart ->
                println("  Cart: ${cart.items}, Total: $${String.format("%.2f", cart.total)}")
            }
        
        println("State management completed\n")
    }
    
    fun demonstrateStatefulFiltering() = runBlocking {
        println("=== Stateful Filtering ===")
        
        data class RateLimitState(
            val count: Int = 0,
            val windowStart: Long = System.currentTimeMillis()
        )
        
        // Rate limiting filter
        suspend fun <T> Flow<T>.rateLimit(
            maxRequests: Int,
            windowMs: Long
        ): Flow<T> = flow {
            var state = RateLimitState()
            
            collect { value ->
                val now = System.currentTimeMillis()
                
                // Reset window if expired
                if (now - state.windowStart >= windowMs) {
                    state = RateLimitState(windowStart = now)
                }
                
                // Check rate limit
                if (state.count < maxRequests) {
                    state = state.copy(count = state.count + 1)
                    emit(value)
                } else {
                    println("  Rate limit exceeded, dropping: $value")
                }
            }
        }
        
        // Duplicate detection filter
        suspend fun <T> Flow<T>.distinctWithHistory(historySize: Int = 10): Flow<T> = flow {
            val history = mutableSetOf<T>()
            val order = mutableListOf<T>()
            
            collect { value ->
                if (value !in history) {
                    history.add(value)
                    order.add(value)
                    
                    // Maintain history size
                    if (order.size > historySize) {
                        val removed = order.removeAt(0)
                        history.remove(removed)
                    }
                    
                    emit(value)
                } else {
                    println("  Duplicate detected, skipping: $value")
                }
            }
        }
        
        println("1. Rate limiting (max 3 per 500ms):")
        flow {
            repeat(8) { i ->
                emit("Request-$i")
                delay(100)
            }
        }
            .rateLimit(maxRequests = 3, windowMs = 500)
            .collect { println("  Processed: $it") }
        
        println()
        
        println("2. Duplicate detection with history:")
        flowOf("A", "B", "A", "C", "B", "D", "A", "E", "C")
            .distinctWithHistory(historySize = 3)
            .collect { println("  Unique: $it") }
        
        println("Stateful filtering completed\n")
    }
}

/**
 * Windowing and chunking operations
 */
class WindowingOperations {
    
    fun demonstrateTimeWindowing() = runBlocking {
        println("=== Time-Based Windowing ===")
        
        data class Event(val id: Int, val timestamp: Long, val value: Double)
        
        // Create time-based events
        val eventStream = flow {
            repeat(15) { i ->
                val event = Event(i, System.currentTimeMillis(), Random.nextDouble(1.0, 100.0))
                emit(event)
                delay(150) // Events every 150ms
            }
        }
        
        // Time window aggregation
        suspend fun <T> Flow<T>.timeWindow(
            windowDuration: Long,
            slideInterval: Long = windowDuration
        ): Flow<List<T>> = flow {
            val buffer = mutableListOf<Pair<T, Long>>()
            
            collect { value ->
                val now = System.currentTimeMillis()
                buffer.add(value to now)
                
                // Remove expired items
                buffer.removeAll { (_, timestamp) -> 
                    now - timestamp > windowDuration 
                }
                
                // Emit window at slide intervals
                if (buffer.isNotEmpty() && 
                    (buffer.size == 1 || (now - buffer.first().second) >= slideInterval)) {
                    emit(buffer.map { it.first })
                }
            }
        }
        
        println("1. Tumbling windows (500ms windows):")
        eventStream
            .timeWindow(windowDuration = 500)
            .collect { window ->
                val avgValue = window.map { it.value }.average()
                println("  Window: ${window.size} events, Avg: ${String.format("%.2f", avgValue)}")
            }
        
        println("Time windowing completed\n")
    }
    
    fun demonstrateCountBasedWindowing() = runBlocking {
        println("=== Count-Based Windowing ===")
        
        // chunked - group by count
        println("1. Fixed-size chunks:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            .chunked(4)
            .collect { chunk ->
                println("  Chunk: $chunk, Sum: ${chunk.sum()}")
            }
        
        println()
        
        // Sliding window
        println("2. Sliding windows (size 3, step 2):")
        
        suspend fun <T> Flow<T>.slidingWindow(size: Int, step: Int = 1): Flow<List<T>> = flow {
            val buffer = mutableListOf<T>()
            var emitted = 0
            
            collect { value ->
                buffer.add(value)
                
                if (buffer.size >= size) {
                    if ((buffer.size - size) % step == 0) {
                        emit(buffer.takeLast(size))
                        emitted++
                    }
                }
                
                // Keep buffer size manageable
                if (buffer.size > size + step) {
                    buffer.removeAt(0)
                }
            }
        }
        
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .slidingWindow(size = 3, step = 2)
            .collect { window ->
                println("  Window: $window, Product: ${window.fold(1) { acc, n -> acc * n }}")
            }
        
        println()
        
        // Custom grouping by condition
        println("3. Group by condition (group consecutive even/odd):")
        
        suspend fun <T> Flow<T>.groupByCondition(
            condition: (T) -> Boolean
        ): Flow<List<T>> = flow {
            var currentGroup = mutableListOf<T>()
            var lastCondition: Boolean? = null
            
            collect { value ->
                val currentCondition = condition(value)
                
                if (lastCondition != null && lastCondition != currentCondition) {
                    if (currentGroup.isNotEmpty()) {
                        emit(currentGroup.toList())
                        currentGroup.clear()
                    }
                }
                
                currentGroup.add(value)
                lastCondition = currentCondition
            }
            
            // Emit final group
            if (currentGroup.isNotEmpty()) {
                emit(currentGroup)
            }
        }
        
        flowOf(2, 4, 6, 1, 3, 5, 8, 10, 12, 7, 9)
            .groupByCondition { it % 2 == 0 }
            .collect { group ->
                val type = if (group.first() % 2 == 0) "Even" else "Odd"
                println("  $type group: $group")
            }
        
        println("Count-based windowing completed\n")
    }
    
    fun demonstrateSessionWindowing() = runBlocking {
        println("=== Session-Based Windowing ===")
        
        data class UserAction(
            val userId: String,
            val action: String,
            val timestamp: Long
        )
        
        // Session windowing - group by user and session timeout
        suspend fun Flow<UserAction>.sessionWindow(
            sessionTimeoutMs: Long
        ): Flow<List<UserAction>> = flow {
            val sessions = mutableMapOf<String, MutableList<UserAction>>()
            val lastActivity = mutableMapOf<String, Long>()
            
            collect { action ->
                val userId = action.userId
                val now = action.timestamp
                
                // Check if session expired
                val lastTime = lastActivity[userId] ?: 0
                if (now - lastTime > sessionTimeoutMs) {
                    // Emit previous session if exists
                    sessions[userId]?.let { session ->
                        if (session.isNotEmpty()) {
                            emit(session.toList())
                        }
                    }
                    sessions[userId] = mutableListOf()
                }
                
                // Add to current session
                sessions.computeIfAbsent(userId) { mutableListOf() }.add(action)
                lastActivity[userId] = now
            }
            
            // Emit remaining sessions
            sessions.values.forEach { session ->
                if (session.isNotEmpty()) {
                    emit(session)
                }
            }
        }
        
        // Generate user actions
        val actions = flow {
            val baseTime = System.currentTimeMillis()
            val actionData = listOf(
                UserAction("user1", "login", baseTime),
                UserAction("user1", "view_page", baseTime + 1000),
                UserAction("user2", "login", baseTime + 1500),
                UserAction("user1", "purchase", baseTime + 2000),
                UserAction("user2", "view_page", baseTime + 2500),
                UserAction("user1", "logout", baseTime + 3000),
                // Gap > 5 seconds - new session for user1
                UserAction("user1", "login", baseTime + 9000),
                UserAction("user2", "purchase", baseTime + 9500),
                UserAction("user1", "view_page", baseTime + 10000)
            )
            
            actionData.forEach { action ->
                emit(action)
                delay(100)
            }
        }
        
        println("User sessions (5 second timeout):")
        actions
            .sessionWindow(sessionTimeoutMs = 5000)
            .collect { session ->
                val user = session.first().userId
                val duration = session.last().timestamp - session.first().timestamp
                val actionTypes = session.map { it.action }
                println("  Session for $user: $actionTypes (duration: ${duration}ms)")
            }
        
        println("Session windowing completed\n")
    }
}

/**
 * Custom transformation operators
 */
class CustomTransformationOperators {
    
    fun demonstrateCustomOperators() = runBlocking {
        println("=== Custom Transformation Operators ===")
        
        // Custom operator: mapWithIndex
        suspend fun <T, R> Flow<T>.mapWithIndex(
            transform: suspend (index: Int, value: T) -> R
        ): Flow<R> = flow {
            var index = 0
            collect { value ->
                emit(transform(index++, value))
            }
        }
        
        // Custom operator: takeWhileInclusive (includes the first failing element)
        suspend fun <T> Flow<T>.takeWhileInclusive(
            predicate: suspend (T) -> Boolean
        ): Flow<T> = flow {
            collect { value ->
                emit(value)
                if (!predicate(value)) {
                    return@flow // Stop after emitting the failing element
                }
            }
        }
        
        // Custom operator: batch with timeout
        suspend fun <T> Flow<T>.batchWithTimeout(
            maxSize: Int,
            timeoutMs: Long
        ): Flow<List<T>> = channelFlow {
            val batch = mutableListOf<T>()
            var timeoutJob: Job? = null
            
            val sendBatch = {
                if (batch.isNotEmpty()) {
                    trySend(batch.toList())
                    batch.clear()
                }
                timeoutJob?.cancel()
                timeoutJob = null
            }
            
            collect { value ->
                batch.add(value)
                
                // Start timeout if this is the first item in batch
                if (batch.size == 1) {
                    timeoutJob = launch {
                        delay(timeoutMs)
                        sendBatch()
                    }
                }
                
                // Send batch if size limit reached
                if (batch.size >= maxSize) {
                    sendBatch()
                }
            }
            
            // Send remaining items
            sendBatch()
        }
        
        println("1. mapWithIndex - add index to values:")
        flowOf("apple", "banana", "cherry", "date")
            .mapWithIndex { index, fruit -> "[$index] $fruit" }
            .collect { println("  $it") }
        
        println()
        
        println("2. takeWhileInclusive - take until condition fails (inclusive):")
        flowOf(2, 4, 6, 7, 8, 10, 12)
            .takeWhileInclusive { it % 2 == 0 }
            .collect { println("  Taken: $it") }
        
        println()
        
        println("3. batchWithTimeout - batch by size or timeout:")
        flow {
            val delays = listOf(100, 50, 300, 80, 400, 60, 70, 200, 90)
            repeat(9) { i ->
                emit("Item-$i")
                delay(delays[i])
            }
        }
            .batchWithTimeout(maxSize = 3, timeoutMs = 200)
            .collect { batch ->
                println("  Batch: $batch")
            }
        
        println("Custom operators completed\n")
    }
    
    fun demonstrateStatefulCustomOperators() = runBlocking {
        println("=== Stateful Custom Operators ===")
        
        // Custom operator: exponential smoothing
        suspend fun Flow<Double>.exponentialSmoothing(alpha: Double): Flow<Double> = flow {
            var smoothedValue: Double? = null
            
            collect { value ->
                smoothedValue = if (smoothedValue == null) {
                    value
                } else {
                    alpha * value + (1 - alpha) * smoothedValue!!
                }
                emit(smoothedValue!!)
            }
        }
        
        // Custom operator: threshold detector
        suspend fun Flow<Double>.thresholdDetector(
            threshold: Double,
            hystéresisRatio: Double = 0.1
        ): Flow<String> = flow {
            var state = "NORMAL" // "NORMAL", "ALARM"
            val upperThreshold = threshold
            val lowerThreshold = threshold * (1 - hystéresisRatio)
            
            collect { value ->
                val newState = when (state) {
                    "NORMAL" -> if (value > upperThreshold) "ALARM" else "NORMAL"
                    "ALARM" -> if (value < lowerThreshold) "NORMAL" else "ALARM"
                    else -> state
                }
                
                if (newState != state) {
                    state = newState
                    emit("State changed to $state (value: $value)")
                } else {
                    emit("State $state (value: $value)")
                }
            }
        }
        
        // Generate noisy sensor data
        val sensorData = flow {
            repeat(20) { i ->
                val baseValue = 50.0
                val noise = Random.nextDouble(-10.0, 10.0)
                val spike = if (i in 8..12) 30.0 else 0.0 // Simulate alarm condition
                emit(baseValue + noise + spike)
                delay(100)
            }
        }
        
        println("1. Exponential smoothing (alpha = 0.3):")
        sensorData
            .exponentialSmoothing(alpha = 0.3)
            .collect { smoothed ->
                println("  Smoothed: ${String.format("%.2f", smoothed)}")
            }
        
        println()
        
        println("2. Threshold detection with hysteresis:")
        sensorData
            .thresholdDetector(threshold = 70.0, hystéresisRatio = 0.2)
            .collect { status ->
                println("  $status")
            }
        
        println("Stateful custom operators completed\n")
    }
    
    fun demonstratePerformanceOptimizedOperators() = runBlocking {
        println("=== Performance-Optimized Operators ===")
        
        // Optimized distinct operator with LRU cache
        suspend fun <T> Flow<T>.distinctLRU(maxCacheSize: Int = 100): Flow<T> = flow {
            val cache = LinkedHashMap<T, Boolean>(maxCacheSize, 0.75f, true)
            
            collect { value ->
                if (!cache.containsKey(value)) {
                    // Add to cache
                    cache[value] = true
                    
                    // Remove oldest if cache is full
                    if (cache.size > maxCacheSize) {
                        val oldest = cache.keys.first()
                        cache.remove(oldest)
                    }
                    
                    emit(value)
                }
            }
        }
        
        // Bulk processing operator
        suspend fun <T, R> Flow<T>.mapBulk(
            batchSize: Int,
            transform: suspend (List<T>) -> List<R>
        ): Flow<R> = flow {
            val batch = mutableListOf<T>()
            
            collect { value ->
                batch.add(value)
                
                if (batch.size >= batchSize) {
                    val results = transform(batch.toList())
                    results.forEach { emit(it) }
                    batch.clear()
                }
            }
            
            // Process remaining items
            if (batch.isNotEmpty()) {
                val results = transform(batch)
                results.forEach { emit(it) }
            }
        }
        
        println("1. Distinct with LRU cache:")
        flowOf(1, 2, 3, 2, 4, 5, 3, 6, 7, 2, 8, 9, 1, 10)
            .distinctLRU(maxCacheSize = 5)
            .collect { println("  Unique: $it") }
        
        println()
        
        println("2. Bulk processing (batch size 3):")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapBulk(batchSize = 3) { batch ->
                // Simulate bulk operation (e.g., database batch insert)
                delay(50)
                println("    Processing batch: $batch")
                batch.map { it * it }
            }
            .collect { result ->
                println("  Result: $result")
            }
        
        println("Performance-optimized operators completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Advanced mapping and filtering
        AdvancedMappingFiltering().demonstrateConditionalMapping()
        AdvancedMappingFiltering().demonstrateFilteringPatterns()
        AdvancedMappingFiltering().demonstrateTransformOperator()
        
        // Stateful transformations
        StatefulTransformations().demonstrateRunningAggregations()
        StatefulTransformations().demonstrateStateManagement()
        StatefulTransformations().demonstrateStatefulFiltering()
        
        // Windowing operations
        WindowingOperations().demonstrateTimeWindowing()
        WindowingOperations().demonstrateCountBasedWindowing()
        WindowingOperations().demonstrateSessionWindowing()
        
        // Custom operators
        CustomTransformationOperators().demonstrateCustomOperators()
        CustomTransformationOperators().demonstrateStatefulCustomOperators()
        CustomTransformationOperators().demonstratePerformanceOptimizedOperators()
        
        println("=== Flow Transformations Summary ===")
        println("✅ Advanced Transformations:")
        println("   - Conditional mapping and enrichment")
        println("   - Complex filtering with side effects")
        println("   - Transform operator for multiple emissions")
        println("   - Stateful transformations with scan")
        println()
        println("✅ Windowing Operations:")
        println("   - Time-based windowing with sliding windows")
        println("   - Count-based chunking and sliding windows")
        println("   - Session-based windowing with timeouts")
        println("   - Custom grouping by conditions")
        println()
        println("✅ Stateful Operations:")
        println("   - Running aggregations and statistics")
        println("   - State management patterns")
        println("   - Rate limiting and duplicate detection")
        println("   - Memory-efficient state handling")
        println()
        println("✅ Custom Operators:")
        println("   - Extension functions for reusable operations")
        println("   - Stateful operators with memory management")
        println("   - Performance-optimized transformations")
        println("   - Domain-specific transformation patterns")
        println()
        println("✅ Performance Considerations:")
        println("   - Memory usage in windowing operations")
        println("   - Bulk processing for efficiency")
        println("   - LRU caching for distinct operations")
        println("   - Backpressure handling in transformations")
    }
}