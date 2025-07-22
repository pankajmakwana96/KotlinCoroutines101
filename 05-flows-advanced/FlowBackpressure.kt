/**
 * # Advanced Flow Backpressure Handling
 * 
 * ## Problem Description
 * Backpressure occurs when producers emit data faster than consumers can process it,
 * leading to memory issues, performance degradation, or system failures. Traditional
 * reactive streams handle this with complex backpressure strategies, while Kotlin Flows
 * use structured concurrency and suspension to provide natural backpressure handling.
 * 
 * ## Solution Approach
 * Flow backpressure strategies include:
 * - Natural suspension-based backpressure in cold flows
 * - Buffer management and overflow handling
 * - Sampling and throttling techniques
 * - Custom backpressure operators
 * - Producer rate limiting and adaptive consumption
 * 
 * ## Key Learning Points
 * - Cold flows naturally provide backpressure through suspension
 * - Buffer overflow strategies determine behavior under high load
 * - Conflation helps drop intermediate values when processing is slow
 * - Hot flows require explicit backpressure handling strategies
 * - Channel-based flows offer more granular backpressure control
 * 
 * ## Performance Considerations
 * - Buffer size affects memory usage (typical range: 16-1024)
 * - Conflation reduces memory but may lose data
 * - Sampling strategies reduce CPU load but affect data completeness
 * - Buffer overflow handling impacts system reliability
 * - Producer throttling prevents system overload
 * 
 * ## Common Pitfalls
 * - Unlimited buffering leading to OOM errors
 * - Ignoring slow consumer scenarios
 * - Inappropriate buffer overflow strategies
 * - Not handling producer-consumer rate mismatches
 * - Missing cancellation in backpressure scenarios
 * 
 * ## Real-World Applications
 * - High-frequency sensor data processing
 * - Network stream processing with rate limiting
 * - Database batch operations with flow control
 * - Real-time data feeds and market data
 * - IoT data ingestion and processing pipelines
 * 
 * ## Related Concepts
 * - Reactive Streams backpressure (comparison)
 * - Channel buffer management
 * - Flow operators and transformations
 * - Structured concurrency principles
 */

package flow.advanced.backpressure

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/**
 * Natural backpressure in cold flows
 * 
 * Flow Backpressure Model:
 * 
 * Cold Flow Backpressure:
 * Producer ──suspend──> [Buffer] ──suspend──> Consumer
 *     ↑                               ↓
 *     └────── natural backpressure ───┘
 * 
 * Hot Flow Backpressure:
 * Producer ──> [Buffer/Overflow Strategy] ──> Consumer
 *                      ↓
 *              [DROP_OLDEST/DROP_LATEST/SUSPEND]
 */
class NaturalBackpressure {
    
    fun demonstrateNaturalSuspension() = runBlocking {
        println("=== Natural Backpressure with Suspension ===")
        
        // Fast producer, slow consumer
        val fastProducerFlow = flow {
            repeat(10) { i ->
                println("  Producing: $i at ${System.currentTimeMillis()}")
                emit(i)
                // Producer is fast (no delay)
            }
        }
        
        println("1. Fast producer, slow consumer (natural backpressure):")
        
        val startTime = System.currentTimeMillis()
        fastProducerFlow.collect { value ->
            println("  Consuming: $value at ${System.currentTimeMillis() - startTime}ms")
            delay(200) // Slow consumer
        }
        
        println()
        
        // Demonstrate suspension points
        println("2. Suspension points in flow:")
        
        flow {
            repeat(5) { i ->
                println("  About to emit $i")
                emit(i) // Suspension point - waits for consumer
                println("  Emitted $i, continuing...")
            }
        }.collect { value ->
            println("  Processing $value...")
            delay(300)
            println("  Finished processing $value")
        }
        
        println("Natural suspension backpressure completed\n")
    }
    
    fun demonstrateChannelBackpressure() = runBlocking {
        println("=== Channel-based Flow Backpressure ===")
        
        // Channel flow with different buffer sizes
        suspend fun createChannelFlow(bufferSize: Int, name: String) = channelFlow {
            repeat(10) { i ->
                println("  $name: Sending $i")
                send(i)
                delay(50) // Fast producer
            }
        }.buffer(bufferSize)
        
        println("1. Small buffer (backpressure kicks in early):")
        val time1 = measureTimeMillis {
            createChannelFlow(2, "SmallBuffer").collect { value ->
                println("    Received: $value")
                delay(200) // Slow consumer
            }
        }
        println("  Time with small buffer: ${time1}ms")
        
        println()
        
        println("2. Large buffer (less backpressure):")
        val time2 = measureTimeMillis {
            createChannelFlow(10, "LargeBuffer").collect { value ->
                println("    Received: $value")
                delay(200) // Same slow consumer
            }
        }
        println("  Time with large buffer: ${time2}ms")
        
        println()
        
        // Unbounded buffer (dangerous!)
        println("3. Unlimited buffer (potential memory issues):")
        try {
            withTimeout(2000) {
                channelFlow {
                    repeat(100) { i ->
                        send(i)
                        delay(10) // Very fast producer
                    }
                }.buffer(Channel.UNLIMITED).collect { value ->
                    println("    Processing: $value")
                    delay(100) // Slow consumer
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("    Timed out - demonstrates unbounded buffer growth")
        }
        
        println("Channel backpressure completed\n")
    }
    
    fun demonstrateProducerConsumerRates() = runBlocking {
        println("=== Producer vs Consumer Rate Analysis ===")
        
        data class RateResult(val produced: Int, val consumed: Int, val timeMs: Long)
        
        suspend fun measureRates(
            productionDelay: Long,
            consumptionDelay: Long,
            bufferSize: Int = 16
        ): RateResult {
            var produced = 0
            var consumed = 0
            
            val time = measureTimeMillis {
                flow {
                    repeat(20) { i ->
                        emit(i)
                        produced++
                        delay(productionDelay)
                    }
                }
                    .buffer(bufferSize)
                    .collect { value ->
                        consumed++
                        delay(consumptionDelay)
                    }
            }
            
            return RateResult(produced, consumed, time)
        }
        
        println("1. Balanced rates:")
        val balanced = measureRates(productionDelay = 100, consumptionDelay = 100)
        println("  Produced: ${balanced.produced}, Consumed: ${balanced.consumed}, Time: ${balanced.timeMs}ms")
        
        println("2. Fast producer, slow consumer:")
        val fastProducer = measureRates(productionDelay = 50, consumptionDelay = 150)
        println("  Produced: ${fastProducer.produced}, Consumed: ${fastProducer.consumed}, Time: ${fastProducer.timeMs}ms")
        
        println("3. Slow producer, fast consumer:")
        val slowProducer = measureRates(productionDelay = 150, consumptionDelay = 50)
        println("  Produced: ${slowProducer.produced}, Consumed: ${slowProducer.consumed}, Time: ${slowProducer.timeMs}ms")
        
        println("Producer-consumer rate analysis completed\n")
    }
}

/**
 * Buffer strategies and overflow handling
 */
class BufferStrategies {
    
    fun demonstrateBufferSizes() = runBlocking {
        println("=== Buffer Size Impact ===")
        
        suspend fun testBufferSize(size: Int): Pair<Long, Int> {
            var itemsProcessed = 0
            val time = measureTimeMillis {
                try {
                    withTimeout(1000) {
                        flow {
                            repeat(100) { i ->
                                emit(i)
                                delay(10) // Fast producer
                            }
                        }
                            .buffer(size)
                            .collect { value ->
                                itemsProcessed++
                                delay(50) // Slow consumer
                            }
                    }
                } catch (e: TimeoutCancellationException) {
                    // Expected timeout
                }
            }
            return time to itemsProcessed
        }
        
        val bufferSizes = listOf(1, 4, 16, 64, 256)
        
        for (size in bufferSizes) {
            val (time, processed) = testBufferSize(size)
            println("  Buffer size $size: processed $processed items in ${time}ms")
        }
        
        println()
        
        // Memory usage with different buffer sizes
        println("Buffer size analysis:")
        bufferSizes.forEach { size ->
            val memoryEstimate = size * 8 // Rough estimate for Int buffer
            println("  Buffer $size: ~${memoryEstimate} bytes memory overhead")
        }
        
        println("Buffer size impact completed\n")
    }
    
    fun demonstrateOverflowStrategies() = runBlocking {
        println("=== Buffer Overflow Strategies ===")
        
        suspend fun testOverflowStrategy(
            strategy: BufferOverflow,
            strategyName: String
        ) {
            println("Testing $strategyName strategy:")
            
            try {
                withTimeout(1000) {
                    flow {
                        repeat(20) { i ->
                            println("    Emitting: $i")
                            emit(i)
                            delay(20) // Fast producer
                        }
                    }
                        .buffer(capacity = 3, onBufferOverflow = strategy)
                        .collect { value ->
                            println("    Collected: $value")
                            delay(200) // Very slow consumer
                        }
                }
            } catch (e: TimeoutCancellationException) {
                println("    Timed out (expected for demonstration)")
            }
            
            println()
        }
        
        // Test different overflow strategies
        testOverflowStrategy(BufferOverflow.SUSPEND, "SUSPEND")
        testOverflowStrategy(BufferOverflow.DROP_OLDEST, "DROP_OLDEST")
        testOverflowStrategy(BufferOverflow.DROP_LATEST, "DROP_LATEST")
        
        println("Buffer overflow strategies completed\n")
    }
    
    fun demonstrateConflation() = runBlocking {
        println("=== Flow Conflation ===")
        
        // Regular buffer vs conflated buffer
        println("1. Regular buffer (preserves all values):")
        flow {
            repeat(10) { i ->
                println("  Emitting: $i")
                emit(i)
                delay(50)
            }
        }
            .buffer(5)
            .collect { value ->
                println("    Buffered collected: $value")
                delay(200)
            }
        
        println()
        
        println("2. Conflated buffer (keeps latest value only):")
        flow {
            repeat(10) { i ->
                println("  Emitting: $i")
                emit(i)
                delay(50)
            }
        }
            .conflate() // Same as buffer(capacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .collect { value ->
                println("    Conflated collected: $value")
                delay(200)
            }
        
        println()
        
        // Custom conflation with latest values
        println("3. Custom conflation strategy:")
        flow {
            repeat(15) { i ->
                emit("Value-$i")
                delay(30)
            }
        }
            .buffer(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .collect { value ->
                println("    Custom conflated: $value")
                delay(150)
            }
        
        println("Flow conflation completed\n")
    }
    
    fun demonstrateAdaptiveBuffering() = runBlocking {
        println("=== Adaptive Buffering Strategies ===")
        
        // Adaptive buffer that adjusts based on processing time
        class AdaptiveBuffer<T> {
            private var currentCapacity = 16
            private val minCapacity = 4
            private val maxCapacity = 256
            private var lastProcessingTime = 0L
            private var avgProcessingTime = 100L // Initial estimate
            
            fun adjustBuffer(processingTime: Long): Int {
                // Simple adaptive logic
                avgProcessingTime = (avgProcessingTime * 0.7 + processingTime * 0.3).toLong()
                
                when {
                    avgProcessingTime > 200 && currentCapacity < maxCapacity -> {
                        currentCapacity = (currentCapacity * 1.5).toInt().coerceAtMost(maxCapacity)
                        println("    Increased buffer to $currentCapacity (slow processing)")
                    }
                    avgProcessingTime < 50 && currentCapacity > minCapacity -> {
                        currentCapacity = (currentCapacity * 0.8).toInt().coerceAtLeast(minCapacity)
                        println("    Decreased buffer to $currentCapacity (fast processing)")
                    }
                }
                
                return currentCapacity
            }
        }
        
        val adaptiveBuffer = AdaptiveBuffer<Int>()
        
        println("Adaptive buffering demonstration:")
        flow {
            repeat(20) { i ->
                emit(i)
                delay(50)
            }
        }
            .transform { value ->
                val bufferSize = adaptiveBuffer.adjustBuffer(Random.nextLong(20, 300))
                emit(value to bufferSize)
            }
            .collect { (value, bufferSize) ->
                val processingTime = Random.nextLong(20, 300)
                delay(processingTime)
                println("  Processed $value (${processingTime}ms) with buffer $bufferSize")
            }
        
        println("Adaptive buffering completed\n")
    }
}

/**
 * Sampling and throttling for backpressure management
 */
class SamplingStrategies {
    
    fun demonstrateSampling() = runBlocking {
        println("=== Sampling Strategies ===")
        
        // High-frequency data source
        fun createHighFrequencyFlow() = flow {
            repeat(50) { i ->
                emit("Data-$i")
                delay(20) // 50 Hz
            }
        }
        
        println("1. Regular sampling (fixed interval):")
        createHighFrequencyFlow()
            .sample(100) // Sample every 100ms
            .collect { value ->
                println("  Sampled: $value")
            }
        
        println()
        
        println("2. Throttle first (emit first, then wait):")
        createHighFrequencyFlow()
            .throttleFirst(80)
            .collect { value ->
                println("  Throttled first: $value")
            }
        
        println()
        
        println("3. Throttle latest (wait, then emit latest):")
        createHighFrequencyFlow()
            .throttleLatest(80)
            .collect { value ->
                println("  Throttled latest: $value")
            }
        
        println()
        
        // Custom sampling strategy
        println("4. Custom adaptive sampling:")
        var sampleInterval = 100L
        
        createHighFrequencyFlow()
            .transform { value ->
                // Adaptive sampling based on system load
                val systemLoad = Random.nextDouble(0.1, 1.0)
                sampleInterval = when {
                    systemLoad > 0.8 -> 200L // High load - sample less frequently
                    systemLoad < 0.3 -> 50L  // Low load - sample more frequently
                    else -> 100L             // Normal load
                }
                
                emit(value to systemLoad)
            }
            .sample(sampleInterval)
            .collect { (value, load) ->
                println("  Adaptive sampled: $value (load: ${"%.2f".format(load)})")
            }
        
        println("Sampling strategies completed\n")
    }
    
    fun demonstrateDebouncing() = runBlocking {
        println("=== Debouncing for Backpressure ===")
        
        // Simulate user input or sensor spikes
        fun createBurstyFlow() = flow {
            val bursts = listOf(
                listOf("A1", "A2", "A3"), // Burst 1
                listOf("B1", "B2"),       // Burst 2
                listOf("C1", "C2", "C3", "C4"), // Burst 3
                listOf("D1") // Single item
            )
            
            for ((index, burst) in bursts.withIndex()) {
                for (item in burst) {
                    emit(item)
                    delay(30) // Fast emissions within burst
                }
                delay(300) // Gap between bursts
            }
        }
        
        println("1. Without debouncing:")
        createBurstyFlow().collect { value ->
            println("  Raw: $value")
        }
        
        println()
        
        println("2. With debouncing (only emit after 100ms silence):")
        createBurstyFlow()
            .debounce(100) // Only emit if no new value for 100ms
            .collect { value ->
                println("  Debounced: $value")
            }
        
        println()
        
        // Advanced debouncing with dynamic timeout
        println("3. Dynamic debouncing:")
        createBurstyFlow()
            .transform { value ->
                val debounceTime = if (value.startsWith("C")) 50L else 100L
                emit(value to debounceTime)
            }
            .debounce { (_, timeout) -> timeout }
            .collect { (value, timeout) ->
                println("  Dynamic debounced: $value (timeout: ${timeout}ms)")
            }
        
        println("Debouncing completed\n")
    }
    
    fun demonstrateWindowingStrategies() = runBlocking {
        println("=== Windowing Strategies for Backpressure ===")
        
        // High-volume data stream
        fun createDataStream() = flow {
            repeat(100) { i ->
                emit(i)
                delay(20)
            }
        }
        
        println("1. Time-based windowing:")
        createDataStream()
            .chunked(200) // Collect items for 200ms windows
            .collect { window ->
                println("  Time window: ${window.size} items - ${window.first()}..${window.last()}")
            }
        
        println()
        
        println("2. Size-based windowing:")
        createDataStream()
            .chunked(10) { window -> window.sum() } // Sum each window of 10 items
            .collect { sum ->
                println("  Size window sum: $sum")
            }
        
        println()
        
        println("3. Sliding window with overlap:")
        createDataStream()
            .windowed(5, 2) // Window of 5, step of 2
            .collect { window ->
                println("  Sliding window: [${window.joinToString(", ")}]")
            }
        
        println("Windowing strategies completed\n")
    }
}

/**
 * Custom backpressure operators
 */
class CustomBackpressureOperators {
    
    fun demonstrateCustomOperators() = runBlocking {
        println("=== Custom Backpressure Operators ===")
        
        // Custom operator: Rate limiter
        fun <T> Flow<T>.rateLimit(
            maxEmissions: Int,
            timeWindow: Long
        ): Flow<T> = flow {
            val emissions = mutableListOf<Long>()
            
            collect { value ->
                val now = System.currentTimeMillis()
                
                // Remove old emissions outside time window
                emissions.removeAll { it < now - timeWindow }
                
                if (emissions.size < maxEmissions) {
                    emissions.add(now)
                    emit(value)
                } else {
                    // Wait until we can emit again
                    val oldestEmission = emissions.minOrNull() ?: now
                    val waitTime = timeWindow - (now - oldestEmission)
                    if (waitTime > 0) {
                        delay(waitTime)
                    }
                    emissions.clear()
                    emissions.add(System.currentTimeMillis())
                    emit(value)
                }
            }
        }
        
        println("1. Rate limiting operator (max 3 per 500ms):")
        flow {
            repeat(15) { i ->
                emit("Item-$i")
                delay(50) // Fast emission
            }
        }
            .rateLimit(maxEmissions = 3, timeWindow = 500)
            .collect { value ->
                println("  Rate limited: $value at ${System.currentTimeMillis()}")
            }
        
        println()
        
        // Custom operator: Pressure valve
        fun <T> Flow<T>.pressureValve(
            threshold: Int,
            releaseRate: Double = 0.5
        ): Flow<T> = flow {
            val buffer = mutableListOf<T>()
            
            collect { value ->
                buffer.add(value)
                
                if (buffer.size >= threshold) {
                    println("    Pressure valve activated! Buffer size: ${buffer.size}")
                    
                    // Release portion of buffer
                    val releaseCount = (buffer.size * releaseRate).toInt()
                    repeat(releaseCount) {
                        if (buffer.isNotEmpty()) {
                            val item = buffer.removeFirst()
                            emit(item)
                        }
                    }
                    
                    println("    Released $releaseCount items, buffer now: ${buffer.size}")
                } else {
                    // Normal emission
                    emit(buffer.removeFirst())
                }
            }
            
            // Emit remaining buffer items
            buffer.forEach { emit(it) }
        }
        
        println("2. Pressure valve operator (threshold: 5, release: 50%):")
        flow {
            repeat(20) { i ->
                emit(i)
                delay(if (i < 10) 20 else 100) // Fast then slow
            }
        }
            .pressureValve(threshold = 5, releaseRate = 0.5)
            .collect { value ->
                println("  Pressure valve output: $value")
                delay(150) // Slow consumer
            }
        
        println()
        
        // Custom operator: Circuit breaker for backpressure
        fun <T> Flow<T>.circuitBreaker(
            failureThreshold: Int = 5,
            resetTimeout: Long = 1000
        ): Flow<T> = flow {
            var failures = 0
            var lastFailureTime = 0L
            var circuitOpen = false
            
            collect { value ->
                val now = System.currentTimeMillis()
                
                // Check if circuit should reset
                if (circuitOpen && (now - lastFailureTime) > resetTimeout) {
                    circuitOpen = false
                    failures = 0
                    println("    Circuit breaker reset")
                }
                
                if (!circuitOpen) {
                    try {
                        // Simulate processing that might fail under backpressure
                        if (Random.nextDouble() < 0.3) { // 30% failure rate
                            throw RuntimeException("Processing overload")
                        }
                        
                        emit(value)
                        failures = 0 // Reset on success
                        
                    } catch (e: Exception) {
                        failures++
                        lastFailureTime = now
                        
                        if (failures >= failureThreshold) {
                            circuitOpen = true
                            println("    Circuit breaker opened due to failures")
                        }
                        
                        // Don't emit on failure
                        println("    Dropped $value due to failure")
                    }
                } else {
                    // Circuit is open, drop the item
                    println("    Circuit open: dropped $value")
                }
            }
        }
        
        println("3. Circuit breaker for backpressure:")
        flow {
            repeat(20) { i ->
                emit("Data-$i")
                delay(100)
            }
        }
            .circuitBreaker(failureThreshold = 3, resetTimeout = 800)
            .collect { value ->
                println("  Circuit breaker output: $value")
            }
        
        println("Custom backpressure operators completed\n")
    }
}

/**
 * Real-world backpressure scenarios
 */
class RealWorldBackpressure {
    
    fun demonstrateNetworkBackpressure() = runBlocking {
        println("=== Network Stream Backpressure ===")
        
        // Simulate network data stream with varying speeds
        class NetworkDataStream {
            suspend fun getData(): Flow<String> = flow {
                var packetId = 0
                while (packetId < 50) {
                    val networkDelay = Random.nextLong(10, 200) // Variable network conditions
                    delay(networkDelay)
                    
                    emit("Packet-$packetId")
                    packetId++
                }
            }
        }
        
        // Database processor (slow)
        class DatabaseProcessor {
            suspend fun process(data: String): String {
                delay(Random.nextLong(100, 300)) // Database operation
                return "Processed($data)"
            }
        }
        
        val networkStream = NetworkDataStream()
        val processor = DatabaseProcessor()
        
        println("1. Network to database with backpressure:")
        
        try {
            withTimeout(3000) {
                networkStream.getData()
                    .buffer(10) // Buffer network packets
                    .map { data ->
                        processor.process(data)
                    }
                    .conflate() // Drop intermediate processed results if consumer is slow
                    .collect { result ->
                        println("  Final result: $result")
                        delay(200) // Slow consumer (UI updates)
                    }
            }
        } catch (e: TimeoutCancellationException) {
            println("  Demo timed out (expected)")
        }
        
        println()
        
        // Connection pool backpressure
        println("2. Connection pool backpressure simulation:")
        
        class ConnectionPool(private val maxConnections: Int = 3) {
            private var activeConnections = 0
            
            suspend fun withConnection(block: suspend () -> String): String {
                // Wait for available connection
                while (activeConnections >= maxConnections) {
                    println("    Waiting for connection... ($activeConnections/$maxConnections)")
                    delay(100)
                }
                
                activeConnections++
                return try {
                    delay(Random.nextLong(200, 500)) // Connection work
                    block()
                } finally {
                    activeConnections--
                }
            }
        }
        
        val connectionPool = ConnectionPool(maxConnections = 2)
        
        flow {
            repeat(10) { i ->
                emit("Request-$i")
                delay(50) // Fast request generation
            }
        }
            .map { request ->
                connectionPool.withConnection {
                    "Response-to-$request"
                }
            }
            .collect { response ->
                println("  $response")
            }
        
        println("Network backpressure completed\n")
    }
    
    fun demonstrateIoTBackpressure() = runBlocking {
        println("=== IoT Sensor Data Backpressure ===")
        
        // High-frequency sensor data
        data class SensorReading(
            val sensorId: String,
            val value: Double,
            val timestamp: Long,
            val priority: Int = 1 // 1=low, 2=medium, 3=high
        )
        
        fun createSensorStream(sensorId: String) = flow {
            repeat(100) { i ->
                val reading = SensorReading(
                    sensorId = sensorId,
                    value = Random.nextDouble(0.0, 100.0),
                    timestamp = System.currentTimeMillis(),
                    priority = if (Random.nextDouble() < 0.1) 3 else Random.nextInt(1, 3)
                )
                emit(reading)
                delay(Random.nextLong(10, 50)) // Variable sensor frequencies
            }
        }
        
        // Priority-based backpressure handling
        fun <T> Flow<SensorReading>.priorityBuffer(
            capacity: Int = 20
        ): Flow<SensorReading> = channelFlow {
            val buffer = mutableListOf<SensorReading>()
            
            collect { reading ->
                buffer.add(reading)
                
                if (buffer.size > capacity) {
                    // Sort by priority and keep high priority items
                    buffer.sortByDescending { it.priority }
                    val toKeep = buffer.take(capacity * 2 / 3) // Keep top 2/3
                    val dropped = buffer.size - toKeep.size
                    buffer.clear()
                    buffer.addAll(toKeep)
                    
                    println("    Priority buffer overflow: dropped $dropped low priority readings")
                }
                
                // Emit in priority order
                if (buffer.isNotEmpty()) {
                    val highest = buffer.maxByOrNull { it.priority }!!
                    buffer.remove(highest)
                    send(highest)
                }
            }
            
            // Emit remaining buffer
            buffer.sortedByDescending { it.priority }.forEach { send(it) }
        }
        
        println("1. Multi-sensor with priority backpressure:")
        
        try {
            withTimeout(2000) {
                merge(
                    createSensorStream("Temperature"),
                    createSensorStream("Humidity"),
                    createSensorStream("Pressure")
                )
                    .priorityBuffer(capacity = 15)
                    .collect { reading ->
                        println("  Priority reading: ${reading.sensorId}=${reading.value.toInt()} (priority=${reading.priority})")
                        delay(100) // Slow processing
                    }
            }
        } catch (e: TimeoutCancellationException) {
            println("  IoT demo timed out (expected)")
        }
        
        println()
        
        // Batch processing for efficiency
        println("2. Batch processing with backpressure:")
        
        createSensorStream("BatchSensor")
            .chunked(200) { readings -> // Collect readings for 200ms
                readings.groupBy { it.sensorId }
                    .mapValues { (_, readings) ->
                        readings.map { it.value }.average()
                    }
            }
            .take(5)
            .collect { batch ->
                println("  Batch processed: $batch")
            }
        
        println("IoT backpressure completed\n")
    }
}

/**
 * Extension functions for common backpressure patterns
 */

// Throttle first implementation
fun <T> Flow<T>.throttleFirst(intervalMs: Long): Flow<T> = flow {
    var lastEmissionTime = 0L
    
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime >= intervalMs) {
            lastEmissionTime = currentTime
            emit(value)
        }
    }
}

// Throttle latest implementation  
fun <T> Flow<T>.throttleLatest(intervalMs: Long): Flow<T> = flow {
    var latestValue: T? = null
    var lastEmissionTime = 0L
    
    collect { value ->
        latestValue = value
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastEmissionTime >= intervalMs) {
            latestValue?.let { emit(it) }
            lastEmissionTime = currentTime
        }
    }
    
    // Emit final value if exists
    latestValue?.let { emit(it) }
}

// Chunked with time window
fun <T> Flow<T>.chunked(timeWindowMs: Long): Flow<List<T>> = flow {
    val chunks = mutableListOf<T>()
    var lastEmission = System.currentTimeMillis()
    
    collect { value ->
        chunks.add(value)
        val now = System.currentTimeMillis()
        
        if (now - lastEmission >= timeWindowMs) {
            if (chunks.isNotEmpty()) {
                emit(chunks.toList())
                chunks.clear()
            }
            lastEmission = now
        }
    }
    
    // Emit remaining chunk
    if (chunks.isNotEmpty()) {
        emit(chunks.toList())
    }
}

// Chunked with size and transform
fun <T, R> Flow<T>.chunked(size: Int, transform: (List<T>) -> R): Flow<R> = flow {
    val chunks = mutableListOf<T>()
    
    collect { value ->
        chunks.add(value)
        
        if (chunks.size >= size) {
            emit(transform(chunks.toList()))
            chunks.clear()
        }
    }
    
    // Emit remaining chunk if not empty
    if (chunks.isNotEmpty()) {
        emit(transform(chunks.toList()))
    }
}

// Windowed implementation
fun <T> Flow<T>.windowed(size: Int, step: Int): Flow<List<T>> = flow {
    val window = mutableListOf<T>()
    
    collect { value ->
        window.add(value)
        
        if (window.size >= size) {
            emit(window.toList())
            // Remove 'step' items from beginning
            repeat(step) {
                if (window.isNotEmpty()) window.removeFirst()
            }
        }
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Natural backpressure
        NaturalBackpressure().demonstrateNaturalSuspension()
        NaturalBackpressure().demonstrateChannelBackpressure()
        NaturalBackpressure().demonstrateProducerConsumerRates()
        
        // Buffer strategies
        BufferStrategies().demonstrateBufferSizes()
        BufferStrategies().demonstrateOverflowStrategies()
        BufferStrategies().demonstrateConflation()
        BufferStrategies().demonstrateAdaptiveBuffering()
        
        // Sampling and throttling
        SamplingStrategies().demonstrateSampling()
        SamplingStrategies().demonstrateDebouncing()
        SamplingStrategies().demonstrateWindowingStrategies()
        
        // Custom operators
        CustomBackpressureOperators().demonstrateCustomOperators()
        
        // Real-world scenarios
        RealWorldBackpressure().demonstrateNetworkBackpressure()
        RealWorldBackpressure().demonstrateIoTBackpressure()
        
        println("=== Advanced Flow Backpressure Summary ===")
        println("✅ Natural Backpressure:")
        println("   - Cold flows provide natural suspension-based backpressure")
        println("   - Producer suspends when consumer is slow")
        println("   - No memory buildup in simple producer-consumer scenarios")
        println()
        println("✅ Buffer Management:")
        println("   - Buffer size affects memory usage and responsiveness")
        println("   - Overflow strategies: SUSPEND, DROP_OLDEST, DROP_LATEST")
        println("   - Conflation for keeping only latest values")
        println("   - Adaptive buffering based on system conditions")
        println()
        println("✅ Sampling & Throttling:")
        println("   - sample() for fixed-interval sampling")
        println("   - debounce() for eliminating noise/bursts")
        println("   - throttleFirst/throttleLatest for rate limiting")
        println("   - Windowing for batch processing")
        println()
        println("✅ Custom Strategies:")
        println("   - Rate limiters for API protection")
        println("   - Pressure valves for gradual release")
        println("   - Circuit breakers for failure protection")
        println("   - Priority-based buffering")
        println()
        println("✅ Real-world Applications:")
        println("   - Network stream processing")
        println("   - IoT sensor data handling")
        println("   - Database batch operations")
        println("   - Connection pool management")
    }
}