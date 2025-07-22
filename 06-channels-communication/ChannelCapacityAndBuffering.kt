/**
 * # Channel Capacity and Buffering Strategies
 * 
 * ## Problem Description
 * Channel capacity and buffering strategies are critical for performance and
 * resource management in concurrent systems. The choice of capacity affects
 * memory usage, latency, throughput, and backpressure behavior. Understanding
 * different buffering strategies helps optimize applications for specific use
 * cases and resource constraints.
 * 
 * ## Solution Approach
 * Capacity and buffering strategies include:
 * - Understanding different capacity types and their trade-offs
 * - Buffer overflow handling and backpressure strategies
 * - Dynamic capacity management and adaptive buffering
 * - Memory management and resource optimization
 * - Performance tuning for different workload patterns
 * 
 * ## Key Learning Points
 * - Capacity affects both memory usage and performance characteristics
 * - Rendezvous channels provide natural backpressure with zero memory overhead
 * - Buffered channels trade memory for reduced suspension and better throughput
 * - Unlimited channels can cause memory leaks if not managed carefully
 * - Conflated channels are perfect for state updates and latest-value scenarios
 * 
 * ## Performance Considerations
 * - Rendezvous (0): Minimal memory, maximum synchronization overhead
 * - Small buffers (1-16): Good balance for most use cases (~100-2KB memory)
 * - Medium buffers (32-256): Better throughput for high-volume scenarios (~4-32KB memory)
 * - Large buffers (512+): High throughput but significant memory usage (64KB+ memory)
 * - Buffer overflow strategies impact both performance and reliability
 * 
 * ## Common Pitfalls
 * - Using unlimited capacity leading to out-of-memory errors
 * - Buffer sizes that don't match workload characteristics
 * - Not considering memory pressure in buffer size selection
 * - Ignoring the impact of buffer overflow on application behavior
 * - Misunderstanding the relationship between capacity and performance
 * 
 * ## Real-World Applications
 * - HTTP request buffering in web servers
 * - Message queue implementations with configurable capacity
 * - Real-time data processing with memory constraints
 * - IoT device communication with limited memory
 * - High-frequency trading systems with latency requirements
 * 
 * ## Related Concepts
 * - Operating system buffer management
 * - Network socket buffer tuning
 * - Message queue capacity planning
 * - Memory-mapped I/O buffering
 */

package channels.communication.capacity

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Basic channel capacity types and characteristics
 * 
 * Channel Capacity Spectrum:
 * 
 * Rendezvous (0) ──> Buffered (N) ──> Unlimited (∞) ──> Conflated (Latest)
 *      ↓                 ↓                ↓                   ↓
 *  Direct sync      Fixed buffer    Unbounded queue     Single slot
 *  No memory        Bounded memory   Memory risk        Fixed memory
 *  Max backpressure Controlled flow  No backpressure    Value replacement
 * 
 * Performance Characteristics:
 * ├── Latency ──────── Rendezvous > Conflated > Buffered > Unlimited
 * ├── Throughput ───── Unlimited > Buffered > Conflated > Rendezvous  
 * ├── Memory Usage ─── Unlimited > Buffered > Conflated > Rendezvous
 * └── Backpressure ─── Rendezvous > Buffered > Conflated > Unlimited
 */
class ChannelCapacityTypes {
    
    fun demonstrateCapacityComparison() = runBlocking {
        println("=== Channel Capacity Types Comparison ===")
        
        data class PerformanceMetrics(
            val name: String,
            val totalTime: Long,
            val avgLatency: Double,
            val throughput: Double,
            val memoryEstimate: Int
        )
        
        suspend fun testChannelCapacity(
            capacity: Int,
            name: String,
            messageCount: Int = 1000,
            producerDelay: Long = 1,
            consumerDelay: Long = 5
        ): PerformanceMetrics {
            val channel = Channel<Int>(capacity)
            val latencies = mutableListOf<Long>()
            
            val totalTime = measureTimeMillis {
                val producer = launch {
                    repeat(messageCount) { i ->
                        val sendStart = System.nanoTime()
                        channel.send(i)
                        val sendTime = (System.nanoTime() - sendStart) / 1_000_000 // Convert to ms
                        if (i % 100 == 0) latencies.add(sendTime)
                        delay(producerDelay)
                    }
                    channel.close()
                }
                
                val consumer = launch {
                    for (value in channel) {
                        delay(consumerDelay)
                    }
                }
                
                producer.join()
                consumer.join()
            }
            
            val avgLatency = if (latencies.isNotEmpty()) latencies.average() else 0.0
            val throughput = messageCount.toDouble() / (totalTime / 1000.0) // messages per second
            val memoryEstimate = when (capacity) {
                Channel.RENDEZVOUS -> 64 // Just channel overhead
                Channel.UNLIMITED -> messageCount * 8 + 64 // Could grow to this
                Channel.CONFLATED -> 8 + 64 // One slot + overhead
                else -> capacity * 8 + 64 // Buffer size * element size + overhead
            }
            
            return PerformanceMetrics(name, totalTime, avgLatency, throughput, memoryEstimate)
        }
        
        println("1. Performance comparison across capacity types:")
        
        val capacityTypes = listOf(
            Channel.RENDEZVOUS to "Rendezvous",
            1 to "Buffered-1", 
            16 to "Buffered-16",
            128 to "Buffered-128",
            Channel.UNLIMITED to "Unlimited",
            Channel.CONFLATED to "Conflated"
        )
        
        val results = capacityTypes.map { (capacity, name) ->
            println("  Testing $name capacity...")
            testChannelCapacity(capacity, name)
        }
        
        println("\n  Performance Results:")
        println("  %-12s %-8s %-12s %-15s %-12s".format("Type", "Time(ms)", "Avg Lat(ms)", "Throughput(msg/s)", "Memory(bytes)"))
        println("  " + "-".repeat(65))
        
        results.forEach { metrics ->
            println("  %-12s %-8d %-12.2f %-15.1f %-12d".format(
                metrics.name,
                metrics.totalTime,
                metrics.avgLatency,
                metrics.throughput,
                metrics.memoryEstimate
            ))
        }
        
        println()
        
        // Find optimal capacity for workload
        println("2. Finding optimal capacity:")
        val testCapacities = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
        
        println("  Testing different buffer sizes for balanced workload...")
        val capacityResults = testCapacities.map { capacity ->
            val metrics = testChannelCapacity(capacity, "Buffer-$capacity", 500, 2, 4)
            capacity to metrics.throughput
        }
        
        val optimalCapacity = capacityResults.maxByOrNull { it.second }
        println("  Optimal capacity: ${optimalCapacity?.first} (throughput: ${"%.1f".format(optimalCapacity?.second)} msg/s)")
        
        // Show diminishing returns
        println("\n  Capacity vs Throughput analysis:")
        capacityResults.forEach { (capacity, throughput) ->
            val efficiency = throughput / capacity // Throughput per unit of buffer
            println("    Capacity $capacity: ${"%.1f".format(throughput)} msg/s (efficiency: ${"%.2f".format(efficiency)})")
        }
        
        println("Channel capacity comparison completed\n")
    }
    
    fun demonstrateCapacityImpactOnBehavior() = runBlocking {
        println("=== Capacity Impact on Channel Behavior ===")
        
        println("1. Suspension behavior with different capacities:")
        
        suspend fun demonstrateSuspension(capacity: Int, name: String) {
            println("  Testing $name:")
            val channel = Channel<String>(capacity)
            val suspensions = mutableListOf<Long>()
            
            val producer = launch {
                repeat(10) { i ->
                    val startTime = System.nanoTime()
                    channel.send("Message-$i")
                    val suspensionTime = (System.nanoTime() - startTime) / 1_000_000
                    suspensions.add(suspensionTime)
                    
                    if (suspensionTime > 1) {
                        println("    Producer suspended for ${suspensionTime}ms at message $i")
                    }
                }
                channel.close()
            }
            
            delay(200) // Let producer fill buffer
            
            val consumer = launch {
                for (message in channel) {
                    delay(100) // Slow consumer
                    println("    Consumed: $message")
                }
            }
            
            producer.join()
            consumer.join()
            
            val avgSuspension = suspensions.average()
            val maxSuspension = suspensions.maxOrNull() ?: 0
            println("    Average suspension: ${"%.2f".format(avgSuspension)}ms, Max: ${maxSuspension}ms")
            println()
        }
        
        demonstrateSuspension(Channel.RENDEZVOUS, "Rendezvous (immediate suspension)")
        demonstrateSuspension(3, "Small buffer (suspends when full)")
        demonstrateSuspension(20, "Large buffer (rarely suspends)")
        
        println("2. Memory pressure simulation:")
        
        data class LargeMessage(val id: Int, val data: ByteArray) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as LargeMessage
                return id == other.id && data.contentEquals(other.data)
            }
            
            override fun hashCode(): Int {
                return 31 * id + data.contentHashCode()
            }
        }
        
        suspend fun simulateMemoryPressure(capacity: Int, name: String) {
            println("  Testing $name with large messages:")
            val channel = Channel<LargeMessage>(capacity)
            val messageSize = 1024 * 10 // 10KB per message
            
            var peakMemoryEstimate = 0
            
            val producer = launch {
                repeat(50) { i ->
                    val largeMessage = LargeMessage(i, ByteArray(messageSize) { it.toByte() })
                    
                    val currentBufferEstimate = when (capacity) {
                        Channel.RENDEZVOUS -> messageSize // One message in transit
                        Channel.UNLIMITED -> (i + 1) * messageSize // All messages could be buffered
                        else -> minOf(capacity, i + 1) * messageSize // Actual buffer usage
                    }
                    
                    peakMemoryEstimate = maxOf(peakMemoryEstimate, currentBufferEstimate)
                    
                    channel.send(largeMessage)
                    if (i % 10 == 0) {
                        println("    Sent message $i, estimated memory: ${currentBufferEstimate / 1024}KB")
                    }
                    delay(20)
                }
                channel.close()
            }
            
            val consumer = launch {
                for (message in channel) {
                    delay(100) // Slower than producer
                }
            }
            
            producer.join()
            consumer.join()
            
            println("    Peak memory estimate: ${peakMemoryEstimate / 1024}KB")
            println()
        }
        
        simulateMemoryPressure(Channel.RENDEZVOUS, "Rendezvous")
        simulateMemoryPressure(5, "Small buffer")
        simulateMemoryPressure(Channel.UNLIMITED, "Unlimited (dangerous!)")
        
        println("Channel behavior impact completed\n")
    }
}

/**
 * Buffer overflow strategies and handling
 */
class BufferOverflowStrategies {
    
    fun demonstrateOverflowHandling() = runBlocking {
        println("=== Buffer Overflow Strategies ===")
        
        data class TimestampedMessage(val id: Int, val content: String, val timestamp: Long = System.currentTimeMillis())
        
        println("1. SUSPEND strategy (default):")
        val suspendChannel = Channel<TimestampedMessage>(
            capacity = 3,
            onBufferOverflow = BufferOverflow.SUSPEND
        )
        
        val suspendDemo = launch {
            val producer = launch {
                repeat(8) { i ->
                    val message = TimestampedMessage(i, "Suspend-Message-$i")
                    println("  Producer: Attempting to send message $i")
                    val startTime = System.currentTimeMillis()
                    suspendChannel.send(message)
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > 10) {
                        println("  Producer: Suspended for ${elapsed}ms while sending message $i")
                    }
                    delay(50) // Fast producer
                }
                suspendChannel.close()
            }
            
            delay(200) // Let buffer fill up
            
            val consumer = launch {
                for (message in suspendChannel) {
                    println("  Consumer: Received ${message.content}")
                    delay(200) // Slow consumer
                }
            }
            
            producer.join()
            consumer.join()
        }
        suspendDemo.join()
        
        println()
        
        println("2. DROP_OLDEST strategy:")
        val dropOldestChannel = Channel<TimestampedMessage>(
            capacity = 3,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        
        val dropOldestDemo = launch {
            val producer = launch {
                repeat(8) { i ->
                    val message = TimestampedMessage(i, "DropOldest-Message-$i")
                    val sendResult = dropOldestChannel.trySend(message)
                    if (sendResult.isSuccess) {
                        println("  Producer: Successfully sent message $i")
                    } else {
                        println("  Producer: Failed to send message $i (buffer management)")
                    }
                    delay(50) // Fast producer
                }
                dropOldestChannel.close()
            }
            
            delay(200) // Let messages accumulate
            
            val consumer = launch {
                for (message in dropOldestChannel) {
                    println("  Consumer: Received ${message.content}")
                    delay(300) // Very slow consumer
                }
            }
            
            producer.join()
            consumer.join()
        }
        dropOldestDemo.join()
        
        println()
        
        println("3. DROP_LATEST strategy:")
        val dropLatestChannel = Channel<TimestampedMessage>(
            capacity = 3,
            onBufferOverflow = BufferOverflow.DROP_LATEST
        )
        
        val dropLatestDemo = launch {
            val producer = launch {
                repeat(8) { i ->
                    val message = TimestampedMessage(i, "DropLatest-Message-$i")
                    val sendResult = dropLatestChannel.trySend(message)
                    if (sendResult.isSuccess) {
                        println("  Producer: Successfully sent message $i")
                    } else {
                        println("  Producer: Message $i was dropped (buffer full)")
                    }
                    delay(50) // Fast producer
                }
                dropLatestChannel.close()
            }
            
            delay(200) // Let messages accumulate
            
            val consumer = launch {
                for (message in dropLatestChannel) {
                    println("  Consumer: Received ${message.content}")
                    delay(300) // Very slow consumer
                }
            }
            
            producer.join()
            consumer.join()
        }
        dropLatestDemo.join()
        
        println()
        
        // Compare message loss in different strategies
        println("4. Message loss comparison:")
        
        suspend fun testMessageLoss(
            overflow: BufferOverflow,
            strategyName: String
        ): Pair<Int, Int> { // sent, received
            val channel = Channel<Int>(capacity = 5, onBufferOverflow = overflow)
            var sentCount = 0
            var receivedCount = 0
            
            val producer = launch {
                repeat(20) { i ->
                    val result = channel.trySend(i)
                    if (result.isSuccess) sentCount++
                    delay(25) // Fast producer
                }
                channel.close()
            }
            
            delay(300) // Let producer get ahead
            
            val consumer = launch {
                for (value in channel) {
                    receivedCount++
                    delay(100) // Slow consumer
                }
            }
            
            producer.join()
            consumer.join()
            
            return sentCount to receivedCount
        }
        
        val suspendResult = testMessageLoss(BufferOverflow.SUSPEND, "SUSPEND")
        val dropOldestResult = testMessageLoss(BufferOverflow.DROP_OLDEST, "DROP_OLDEST") 
        val dropLatestResult = testMessageLoss(BufferOverflow.DROP_LATEST, "DROP_LATEST")
        
        println("  Message Loss Analysis:")
        println("  Strategy      Sent  Received  Loss%")
        println("  --------      ----  --------  -----")
        
        listOf(
            "SUSPEND" to suspendResult,
            "DROP_OLDEST" to dropOldestResult,
            "DROP_LATEST" to dropLatestResult
        ).forEach { (name, result) ->
            val (sent, received) = result
            val lossPercent = if (sent > 0) ((sent - received).toDouble() / sent * 100) else 0.0
            println("  %-12s  %-4d  %-8d  ${"%.1f".format(lossPercent)}%")
        }
        
        println("Buffer overflow strategies completed\n")
    }
    
    fun demonstrateCustomOverflowHandling() = runBlocking {
        println("=== Custom Overflow Handling ===")
        
        data class PriorityMessage(val id: Int, val priority: Int, val content: String)
        
        println("1. Priority-based overflow handling:")
        
        // Custom channel with priority-based overflow
        class PriorityChannel<T : PriorityMessage>(private val capacity: Int) {
            private val buffer = mutableListOf<T>()
            private val waitingConsumers = mutableListOf<Continuation<T?>>()
            
            suspend fun send(item: T) {
                if (buffer.size < capacity) {
                    buffer.add(item)
                    buffer.sortByDescending { it.priority } // Keep highest priority first
                    
                    // Notify waiting consumer
                    if (waitingConsumers.isNotEmpty()) {
                        val consumer = waitingConsumers.removeFirst()
                        val nextItem = buffer.removeFirst()
                        consumer.resumeWith(Result.success(nextItem))
                    }
                } else {
                    // Buffer full - check if new item has higher priority than lowest
                    val lowestPriority = buffer.minByOrNull { it.priority }
                    if (lowestPriority != null && item.priority > lowestPriority.priority) {
                        buffer.remove(lowestPriority)
                        buffer.add(item)
                        buffer.sortByDescending { it.priority }
                        println("    Replaced lower priority item ${lowestPriority.id} with higher priority item ${item.id}")
                    } else {
                        println("    Dropped message ${item.id} (priority ${item.priority}) - buffer full with higher priority items")
                    }
                }
            }
            
            suspend fun receive(): T? = suspendCancellableCoroutine { continuation ->
                if (buffer.isNotEmpty()) {
                    val item = buffer.removeFirst()
                    continuation.resumeWith(Result.success(item))
                } else {
                    waitingConsumers.add(continuation)
                }
            }
            
            fun close() {
                waitingConsumers.forEach { it.resumeWith(Result.success(null)) }
                waitingConsumers.clear()
            }
        }
        
        val priorityChannel = PriorityChannel<PriorityMessage>(capacity = 5)
        
        val priorityProducer = launch {
            val messages = listOf(
                PriorityMessage(1, 1, "Low priority message 1"),
                PriorityMessage(2, 5, "High priority message"),
                PriorityMessage(3, 2, "Medium priority message 1"),
                PriorityMessage(4, 1, "Low priority message 2"),
                PriorityMessage(5, 4, "High priority message 2"),
                PriorityMessage(6, 3, "Medium priority message 2"),
                PriorityMessage(7, 5, "Critical message"),
                PriorityMessage(8, 1, "Low priority message 3"),
                PriorityMessage(9, 2, "Medium priority message 3")
            )
            
            messages.forEach { message ->
                priorityChannel.send(message)
                println("  Producer: Sent ${message.content}")
                delay(100)
            }
            priorityChannel.close()
        }
        
        val priorityConsumer = launch {
            while (true) {
                val message = priorityChannel.receive() ?: break
                println("  Consumer: Processing priority ${message.priority} message: ${message.content}")
                delay(200) // Slow consumer to create backpressure
            }
        }
        
        priorityProducer.join()
        priorityConsumer.join()
        
        println()
        
        println("2. Adaptive overflow handling:")
        
        // Adaptive channel that changes overflow strategy based on conditions
        class AdaptiveChannel<T>(
            private val baseCapacity: Int,
            private var currentStrategy: BufferOverflow = BufferOverflow.SUSPEND
        ) {
            private var channel = Channel<T>(baseCapacity, currentStrategy)
            private var droppedCount = 0
            private var totalSent = 0
            
            suspend fun send(item: T) {
                totalSent++
                val result = channel.trySend(item)
                
                if (!result.isSuccess) {
                    droppedCount++
                    val dropRate = droppedCount.toDouble() / totalSent
                    
                    // Adapt strategy based on drop rate
                    when {
                        dropRate > 0.3 && currentStrategy != BufferOverflow.DROP_OLDEST -> {
                            println("    High drop rate (${"%.1f".format(dropRate * 100)}%) - switching to DROP_OLDEST")
                            adaptStrategy(BufferOverflow.DROP_OLDEST)
                        }
                        dropRate < 0.1 && currentStrategy != BufferOverflow.SUSPEND -> {
                            println("    Low drop rate (${"%.1f".format(dropRate * 100)}%) - switching to SUSPEND")
                            adaptStrategy(BufferOverflow.SUSPEND)
                        }
                    }
                    
                    // Retry with new strategy
                    channel.trySend(item)
                }
            }
            
            private fun adaptStrategy(newStrategy: BufferOverflow) {
                // Note: In practice, you'd need to migrate existing items
                channel.close()
                currentStrategy = newStrategy
                channel = Channel(baseCapacity, newStrategy)
            }
            
            fun receiveAsFlow(): Flow<T> = channel.receiveAsFlow()
            
            fun close() = channel.close()
            
            fun getStats() = "Total sent: $totalSent, Dropped: $droppedCount, Strategy: $currentStrategy"
        }
        
        val adaptiveChannel = AdaptiveChannel<String>(capacity = 3)
        
        val adaptiveProducer = launch {
            repeat(15) { i ->
                adaptiveChannel.send("Adaptive-Message-$i")
                println("  Producer: Sent message $i")
                delay(50) // Fast producer
            }
            adaptiveChannel.close()
        }
        
        delay(200) // Let producer get ahead to create pressure
        
        val adaptiveConsumer = launch {
            adaptiveChannel.receiveAsFlow().collect { message ->
                println("  Consumer: Received $message")
                delay(250) // Slow consumer
            }
        }
        
        adaptiveProducer.join()
        adaptiveConsumer.join()
        
        println("  Final stats: ${adaptiveChannel.getStats()}")
        
        println("Custom overflow handling completed\n")
    }
}

/**
 * Dynamic capacity management and adaptive strategies
 */
class DynamicCapacityManagement {
    
    fun demonstrateAdaptiveCapacity() = runBlocking {
        println("=== Dynamic Capacity Management ===")
        
        println("1. Load-based capacity adaptation:")
        
        // Channel that adapts capacity based on load
        class LoadAdaptiveChannel<T> {
            private var currentCapacity = 10
            private val minCapacity = 5
            private val maxCapacity = 50
            
            private var channel = Channel<T>(currentCapacity)
            private val sendTimes = mutableListOf<Long>()
            private var lastAdaptation = System.currentTimeMillis()
            
            suspend fun send(item: T) {
                val startTime = System.currentTimeMillis()
                channel.send(item)
                val sendTime = System.currentTimeMillis() - startTime
                
                sendTimes.add(sendTime)
                if (sendTimes.size > 10) {
                    sendTimes.removeFirst()
                }
                
                // Adapt capacity every 1 second
                if (System.currentTimeMillis() - lastAdaptation > 1000 && sendTimes.size >= 5) {
                    adaptCapacity()
                    lastAdaptation = System.currentTimeMillis()
                }
            }
            
            private fun adaptCapacity() {
                val avgSendTime = sendTimes.average()
                val newCapacity = when {
                    avgSendTime > 100 -> minOf(currentCapacity * 2, maxCapacity) // High latency - increase capacity
                    avgSendTime < 10 -> maxOf(currentCapacity / 2, minCapacity) // Low latency - decrease capacity
                    else -> currentCapacity // No change needed
                }
                
                if (newCapacity != currentCapacity) {
                    println("    Adapting capacity: $currentCapacity -> $newCapacity (avg send time: ${"%.1f".format(avgSendTime)}ms)")
                    
                    // Migrate to new channel (simplified approach)
                    val oldChannel = channel
                    channel = Channel(newCapacity)
                    currentCapacity = newCapacity
                    
                    // In practice, you'd need to handle in-flight messages
                    oldChannel.close()
                    sendTimes.clear()
                }
            }
            
            fun receiveAsFlow(): Flow<T> = channel.receiveAsFlow()
            fun close() = channel.close()
            fun getCurrentCapacity() = currentCapacity
        }
        
        val adaptiveChannel = LoadAdaptiveChannel<String>()
        
        val loadProducer = launch {
            repeat(50) { i ->
                adaptiveChannel.send("Load-Message-$i")
                
                // Simulate varying load
                val delay = when {
                    i < 15 -> 50L // Fast production initially
                    i < 30 -> 200L // Slow down to create pressure
                    else -> 75L // Medium speed
                }
                delay(delay)
                
                if (i % 10 == 0) {
                    println("  Producer: Sent $i messages, current capacity: ${adaptiveChannel.getCurrentCapacity()}")
                }
            }
            adaptiveChannel.close()
        }
        
        val loadConsumer = launch {
            var processedCount = 0
            adaptiveChannel.receiveAsFlow().collect { message ->
                processedCount++
                if (processedCount % 15 == 0) {
                    println("  Consumer: Processed $processedCount messages")
                }
                delay(150) // Consistent consumer speed
            }
        }
        
        loadProducer.join()
        loadConsumer.join()
        
        println()
        
        println("2. Memory-aware capacity management:")
        
        // Channel that adapts based on memory pressure
        class MemoryAwareChannel<T>(
            private val maxMemoryMB: Int = 10
        ) {
            private var currentCapacity = 20
            private var channel = Channel<T>(currentCapacity)
            private var estimatedItemSize = 1024 // 1KB per item estimate
            
            suspend fun send(item: T) {
                checkMemoryPressure()
                channel.send(item)
            }
            
            private fun checkMemoryPressure() {
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // MB
                val memoryPressure = usedMemory.toDouble() / runtime.maxMemory() * (1024 * 1024) // Pressure ratio
                
                val estimatedChannelMemory = (currentCapacity * estimatedItemSize) / (1024 * 1024) // MB
                
                val newCapacity = when {
                    estimatedChannelMemory > maxMemoryMB -> {
                        maxOf(currentCapacity / 2, 5)
                    }
                    memoryPressure > 0.8 -> {
                        maxOf(currentCapacity * 3 / 4, 5)
                    }
                    memoryPressure < 0.4 && currentCapacity < 100 -> {
                        currentCapacity * 2
                    }
                    else -> currentCapacity
                }
                
                if (newCapacity != currentCapacity) {
                    println("    Memory adaptation: capacity $currentCapacity -> $newCapacity " +
                           "(estimated channel memory: ${estimatedChannelMemory}MB)")
                    
                    val oldChannel = channel
                    channel = Channel(newCapacity)
                    currentCapacity = newCapacity
                    oldChannel.close()
                }
            }
            
            fun receiveAsFlow(): Flow<T> = channel.receiveAsFlow()
            fun close() = channel.close()
            fun getCapacity() = currentCapacity
        }
        
        val memoryAwareChannel = MemoryAwareChannel<ByteArray>(maxMemoryMB = 5)
        
        val memoryProducer = launch {
            repeat(30) { i ->
                val dataSize = Random.nextInt(512, 2048) // Variable size data
                val data = ByteArray(dataSize) { it.toByte() }
                
                memoryAwareChannel.send(data)
                if (i % 5 == 0) {
                    println("  Producer: Sent item $i (${data.size} bytes), capacity: ${memoryAwareChannel.getCapacity()}")
                }
                delay(100)
            }
            memoryAwareChannel.close()
        }
        
        val memoryConsumer = launch {
            var totalBytes = 0L
            var itemCount = 0
            
            memoryAwareChannel.receiveAsFlow().collect { data ->
                totalBytes += data.size
                itemCount++
                
                if (itemCount % 8 == 0) {
                    println("  Consumer: Processed $itemCount items, total: ${totalBytes / 1024}KB")
                }
                delay(200)
            }
        }
        
        memoryProducer.join()
        memoryConsumer.join()
        
        println("Dynamic capacity management completed\n")
    }
    
    fun demonstrateCapacityOptimization() = runBlocking {
        println("=== Capacity Optimization Strategies ===")
        
        println("1. Workload-based optimization:")
        
        data class WorkloadPattern(
            val burstSize: Int,
            val burstInterval: Long,
            val sustainedRate: Long
        )
        
        suspend fun optimizeForWorkload(pattern: WorkloadPattern): Int {
            // Simulate different capacities and find optimal one
            val capacities = listOf(1, 5, 10, 20, 50, 100)
            var bestCapacity = 1
            var bestThroughput = 0.0
            
            for (capacity in capacities) {
                val channel = Channel<Int>(capacity)
                var sentCount = 0
                var receivedCount = 0
                
                val testTime = measureTimeMillis {
                    val producer = launch {
                        repeat(3) { burst ->
                            // Burst phase
                            repeat(pattern.burstSize) { i ->
                                channel.trySend(burst * pattern.burstSize + i)
                                sentCount++
                            }
                            delay(pattern.burstInterval)
                            
                            // Sustained phase
                            repeat(pattern.burstSize / 2) { i ->
                                channel.trySend(burst * pattern.burstSize + pattern.burstSize + i)
                                sentCount++
                                delay(pattern.sustainedRate)
                            }
                        }
                        channel.close()
                    }
                    
                    val consumer = launch {
                        for (item in channel) {
                            receivedCount++
                            delay(50) // Processing time
                        }
                    }
                    
                    producer.join()
                    consumer.join()
                }
                
                val throughput = receivedCount.toDouble() / (testTime / 1000.0)
                if (throughput > bestThroughput) {
                    bestThroughput = throughput
                    bestCapacity = capacity
                }
                
                println("    Capacity $capacity: $receivedCount/$sentCount messages, throughput: ${"%.1f".format(throughput)} msg/s")
            }
            
            return bestCapacity
        }
        
        val burstWorkload = WorkloadPattern(burstSize = 10, burstInterval = 200L, sustainedRate = 30L)
        val optimalCapacity = optimizeForWorkload(burstWorkload)
        println("  Optimal capacity for burst workload: $optimalCapacity")
        
        println()
        
        println("2. Multi-objective optimization:")
        
        data class OptimizationResult(
            val capacity: Int,
            val throughput: Double,
            val memoryUsage: Int,
            val latency: Double,
            val score: Double
        )
        
        suspend fun multiObjectiveOptimization(): OptimizationResult {
            val capacities = listOf(1, 8, 16, 32, 64, 128)
            val results = mutableListOf<OptimizationResult>()
            
            for (capacity in capacities) {
                val channel = Channel<Int>(capacity)
                val latencies = mutableListOf<Long>()
                var receivedCount = 0
                
                val testTime = measureTimeMillis {
                    val producer = launch {
                        repeat(100) { i ->
                            val startTime = System.nanoTime()
                            channel.send(i)
                            val latency = (System.nanoTime() - startTime) / 1_000_000
                            latencies.add(latency)
                            delay(20)
                        }
                        channel.close()
                    }
                    
                    val consumer = launch {
                        for (item in channel) {
                            receivedCount++
                            delay(30)
                        }
                    }
                    
                    producer.join()
                    consumer.join()
                }
                
                val throughput = receivedCount.toDouble() / (testTime / 1000.0)
                val memoryUsage = capacity * 4 + 64 // Estimated bytes
                val avgLatency = latencies.average()
                
                // Multi-objective score (higher is better)
                // Normalize and weight: 40% throughput, 30% memory efficiency, 30% latency
                val normalizedThroughput = throughput / 20.0 // Assuming max ~20 msg/s
                val normalizedMemory = 1.0 / (memoryUsage / 100.0) // Inverse of memory usage
                val normalizedLatency = 1.0 / (avgLatency / 10.0) // Inverse of latency
                
                val score = 0.4 * normalizedThroughput + 0.3 * normalizedMemory + 0.3 * normalizedLatency
                
                results.add(OptimizationResult(capacity, throughput, memoryUsage, avgLatency, score))
            }
            
            results.forEach { result ->
                println("    Capacity ${result.capacity}: throughput=${"%.1f".format(result.throughput)}, " +
                       "memory=${result.memoryUsage}B, latency=${"%.2f".format(result.latency)}ms, " +
                       "score=${"%.3f".format(result.score)}")
            }
            
            return results.maxByOrNull { it.score }!!
        }
        
        val optimalResult = multiObjectiveOptimization()
        println("  Multi-objective optimal capacity: ${optimalResult.capacity} (score: ${"%.3f".format(optimalResult.score)})")
        
        println()
        
        println("3. Real-time capacity tuning:")
        
        // Channel that continuously tunes its capacity based on performance metrics
        class SelfTuningChannel<T> {
            private var capacity = 16
            private var channel = Channel<T>(capacity)
            
            private val performanceWindow = mutableListOf<Double>()
            private var lastTuning = System.currentTimeMillis()
            private val tuningInterval = 2000L
            
            suspend fun send(item: T) {
                val startTime = System.nanoTime()
                channel.send(item)
                val sendTime = (System.nanoTime() - startTime) / 1_000_000.0
                
                recordPerformance(sendTime)
                
                if (System.currentTimeMillis() - lastTuning > tuningInterval) {
                    tune()
                    lastTuning = System.currentTimeMillis()
                }
            }
            
            private fun recordPerformance(sendTime: Double) {
                performanceWindow.add(sendTime)
                if (performanceWindow.size > 20) {
                    performanceWindow.removeFirst()
                }
            }
            
            private fun tune() {
                if (performanceWindow.size < 10) return
                
                val avgSendTime = performanceWindow.average()
                val variance = performanceWindow.map { (it - avgSendTime).pow(2) }.average()
                val stdDev = sqrt(variance)
                
                val newCapacity = when {
                    avgSendTime > 50 && stdDev > 20 -> minOf(capacity * 2, 128) // High latency and variance
                    avgSendTime < 5 && stdDev < 5 -> maxOf(capacity / 2, 4) // Low latency and variance
                    else -> capacity
                }
                
                if (newCapacity != capacity) {
                    println("    Auto-tuning: $capacity -> $newCapacity " +
                           "(avg: ${"%.1f".format(avgSendTime)}ms, stddev: ${"%.1f".format(stdDev)}ms)")
                    
                    val oldChannel = channel
                    channel = Channel(newCapacity)
                    capacity = newCapacity
                    oldChannel.close()
                    performanceWindow.clear()
                }
            }
            
            fun receiveAsFlow(): Flow<T> = channel.receiveAsFlow()
            fun close() = channel.close()
            fun getCapacity() = capacity
        }
        
        val selfTuningChannel = SelfTuningChannel<String>()
        
        val tuningProducer = launch {
            repeat(60) { i ->
                selfTuningChannel.send("Tuning-Message-$i")
                
                // Simulate varying conditions
                val delay = when {
                    i < 20 -> 30L // Fast initially
                    i < 40 -> 100L // Slower to create pressure
                    else -> 50L // Medium speed
                }
                delay(delay)
                
                if (i % 15 == 0) {
                    println("  Producer: Message $i, current capacity: ${selfTuningChannel.getCapacity()}")
                }
            }
            selfTuningChannel.close()
        }
        
        val tuningConsumer = launch {
            var count = 0
            selfTuningChannel.receiveAsFlow().collect { message ->
                count++
                if (count % 20 == 0) {
                    println("  Consumer: Processed $count messages")
                }
                delay(80) // Consistent processing time
            }
        }
        
        tuningProducer.join()
        tuningConsumer.join()
        
        println("Capacity optimization completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Channel capacity types
        ChannelCapacityTypes().demonstrateCapacityComparison()
        ChannelCapacityTypes().demonstrateCapacityImpactOnBehavior()
        
        // Buffer overflow strategies
        BufferOverflowStrategies().demonstrateOverflowHandling()
        BufferOverflowStrategies().demonstrateCustomOverflowHandling()
        
        // Dynamic capacity management
        DynamicCapacityManagement().demonstrateAdaptiveCapacity()
        DynamicCapacityManagement().demonstrateCapacityOptimization()
        
        println("=== Channel Capacity and Buffering Summary ===")
        println("✅ Capacity Types:")
        println("   - Rendezvous (0): Zero memory overhead, natural backpressure")
        println("   - Buffered (N): Fixed memory usage, reduced suspension")
        println("   - Unlimited (∞): No capacity limit but memory risk")
        println("   - Conflated (1): Latest value only, perfect for state updates")
        println()
        println("✅ Buffer Overflow Strategies:")
        println("   - SUSPEND: Block producer when buffer is full (default)")
        println("   - DROP_OLDEST: Remove oldest items to make room for new ones")
        println("   - DROP_LATEST: Discard new items when buffer is full")
        println("   - Custom strategies: Priority-based, adaptive handling")
        println()
        println("✅ Performance Trade-offs:")
        println("   - Memory vs. Latency: Larger buffers use more memory but reduce latency")
        println("   - Throughput vs. Backpressure: Unlimited capacity maximizes throughput")
        println("   - Consistency vs. Performance: Different overflow strategies affect guarantees")
        println()
        println("✅ Dynamic Management:")
        println("   - Load-based adaptation: Adjust capacity based on send/receive patterns")
        println("   - Memory-aware scaling: Consider system memory pressure")
        println("   - Multi-objective optimization: Balance throughput, memory, and latency")
        println("   - Real-time tuning: Continuously adapt to changing conditions")
        println()
        println("✅ Best Practices:")
        println("   - Start with small buffers (8-16) and measure performance")
        println("   - Choose overflow strategy based on data importance")
        println("   - Monitor memory usage with larger buffers")
        println("   - Consider workload patterns when sizing capacity")
        println("   - Use adaptive strategies for varying workloads")
    }
}