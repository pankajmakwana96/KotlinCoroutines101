/**
 * # Channel Basics and Communication Patterns
 * 
 * ## Problem Description
 * While Flows provide excellent support for stream processing, channels offer
 * direct communication between coroutines with explicit send/receive operations.
 * Channels are hot streams that exist independently of collectors and provide
 * CSP (Communicating Sequential Processes) style communication patterns.
 * 
 * ## Solution Approach
 * Channel fundamentals include:
 * - Basic channel creation and communication patterns
 * - Channel types and their characteristics
 * - Send and receive operation semantics
 * - Channel closing and completion handling
 * - Exception propagation in channels
 * 
 * ## Key Learning Points
 * - Channels are hot streams that exist independently of consumers
 * - Send operations can suspend when channel is full
 * - Receive operations suspend when channel is empty
 * - Channel closing propagates to all receivers
 * - Channels provide CSP-style communication between coroutines
 * 
 * ## Performance Considerations
 * - Channel creation overhead: ~200-500ns per channel
 * - Send/receive operation overhead: ~50-200ns per operation
 * - Memory usage depends on buffer size and element type
 * - Rendezvous channels (capacity 0) have lowest memory overhead
 * - Buffered channels trade memory for reduced suspension
 * 
 * ## Common Pitfalls
 * - Forgetting to close channels leading to resource leaks
 * - Deadlocks with rendezvous channels
 * - Not handling channel exceptions properly
 * - Confusing channels with flows (hot vs cold streams)
 * - Buffer overflow in unlimited capacity channels
 * 
 * ## Real-World Applications
 * - Inter-service communication in microservices
 * - Actor model implementation
 * - Event dispatching and notification systems
 * - Resource pooling and work distribution
 * - Pipeline processing with intermediate buffering
 * 
 * ## Related Concepts
 * - Go channels and CSP theory
 * - Actor model communication
 * - Message passing in distributed systems
 * - Producer-consumer patterns
 */

package channels.communication.basics

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Basic channel operations and patterns
 * 
 * Channel Communication Model:
 * 
 * Producer Coroutine ──send()──> [Channel Buffer] ──receive()──> Consumer Coroutine
 *         ↑                           ↓                              ↓
 *    suspend when full          optional buffer              suspend when empty
 * 
 * Channel Types:
 * ├── Rendezvous (capacity = 0) ── Direct handoff, no buffering
 * ├── Buffered (capacity = N) ─── Fixed buffer size
 * ├── Unlimited (capacity = ∞) ── Unbounded buffer (dangerous!)
 * └── Conflated (capacity = 1) ── Only latest value kept
 */
class BasicChannelOperations {
    
    fun demonstrateBasicChannelUsage() = runBlocking {
        println("=== Basic Channel Usage ===")
        
        // Create a basic channel
        val channel = Channel<String>()
        
        println("1. Basic send and receive:")
        
        // Producer coroutine
        val producer = launch {
            println("  Producer: Sending messages...")
            channel.send("Hello")
            channel.send("World")
            channel.send("from")
            channel.send("Channel")
            channel.close() // Important: close the channel
            println("  Producer: Finished sending")
        }
        
        // Consumer coroutine
        val consumer = launch {
            println("  Consumer: Starting to receive...")
            for (message in channel) {
                println("  Consumer: Received '$message'")
                delay(100) // Simulate processing time
            }
            println("  Consumer: Channel closed, finished receiving")
        }
        
        // Wait for both coroutines to complete
        producer.join()
        consumer.join()
        
        println()
        
        // Alternative receive patterns
        println("2. Different receive patterns:")
        
        val dataChannel = Channel<Int>()
        
        // Producer
        launch {
            repeat(5) { i ->
                dataChannel.send(i)
                println("  Sent: $i")
            }
            dataChannel.close()
        }
        
        // Consumer using different receive methods
        launch {
            // Using receiveCatching for safe receiving
            while (!dataChannel.isClosedForReceive) {
                val result = dataChannel.receiveCatching()
                result.onSuccess { value ->
                    println("  Received safely: $value")
                }.onFailure { exception ->
                    println("  Channel closed or failed: $exception")
                }
            }
        }.join()
        
        println()
        
        // Try send and receive patterns
        println("3. Try operations (non-suspending):")
        
        val tryChannel = Channel<String>(capacity = 2) // Small buffer
        
        // Try send operations
        val sendSuccess1 = tryChannel.trySend("First")
        val sendSuccess2 = tryChannel.trySend("Second")
        val sendSuccess3 = tryChannel.trySend("Third") // Should fail - buffer full
        
        println("  TrySend results: $sendSuccess1, $sendSuccess2, $sendSuccess3")
        
        // Try receive operations
        val receiveResult1 = tryChannel.tryReceive()
        val receiveResult2 = tryChannel.tryReceive()
        val receiveResult3 = tryChannel.tryReceive() // Should fail - channel empty
        
        println("  TryReceive results: $receiveResult1, $receiveResult2, $receiveResult3")
        
        tryChannel.close()
        
        println("Basic channel usage completed\n")
    }
    
    fun demonstrateChannelTypes() = runBlocking {
        println("=== Different Channel Types ===")
        
        // Rendezvous channel (capacity = 0)
        println("1. Rendezvous Channel (capacity = 0):")
        val rendezvousChannel = Channel<String>(Channel.RENDEZVOUS)
        
        val rendezvousDemo = launch {
            launch {
                println("  Producer: About to send...")
                rendezvousChannel.send("Rendezvous Message")
                println("  Producer: Message sent (consumer must have received it)")
            }
            
            delay(100) // Small delay to show send is blocked
            
            launch {
                println("  Consumer: About to receive...")
                val message = rendezvousChannel.receive()
                println("  Consumer: Received '$message'")
            }
        }
        
        rendezvousDemo.join()
        rendezvousChannel.close()
        
        println()
        
        // Buffered channel
        println("2. Buffered Channel (capacity = 3):")
        val bufferedChannel = Channel<Int>(capacity = 3)
        
        launch {
            repeat(5) { i ->
                println("  Producer: Sending $i")
                bufferedChannel.send(i)
                println("  Producer: Sent $i")
                if (i == 2) println("    Buffer should be full now...")
            }
            bufferedChannel.close()
        }
        
        delay(500) // Let producer fill the buffer
        
        launch {
            for (value in bufferedChannel) {
                println("  Consumer: Received $value")
                delay(200) // Slow consumer
            }
        }.join()
        
        println()
        
        // Unlimited channel (dangerous!)
        println("3. Unlimited Channel (use with caution):")
        val unlimitedChannel = Channel<String>(Channel.UNLIMITED)
        
        launch {
            repeat(1000) { i ->
                unlimitedChannel.send("Message-$i")
                if (i % 100 == 0) {
                    println("  Sent ${i + 1} messages to unlimited channel")
                }
            }
            unlimitedChannel.close()
        }
        
        launch {
            var count = 0
            for (message in unlimitedChannel) {
                count++
                if (count % 100 == 0) {
                    println("  Received $count messages")
                }
            }
            println("  Total received: $count messages")
        }.join()
        
        println()
        
        // Conflated channel
        println("4. Conflated Channel (only latest value):")
        val conflatedChannel = Channel<String>(Channel.CONFLATED)
        
        launch {
            repeat(5) { i ->
                conflatedChannel.send("Value-$i")
                println("  Sent: Value-$i")
                delay(50) // Fast producer
            }
            conflatedChannel.close()
        }
        
        delay(300) // Let all values be sent before consuming
        
        launch {
            for (value in conflatedChannel) {
                println("  Received: $value")
            }
        }.join()
        
        println("Different channel types completed\n")
    }
    
    fun demonstrateChannelClosing() = runBlocking {
        println("=== Channel Closing and Completion ===")
        
        println("1. Proper channel closing:")
        val channel = Channel<Int>()
        
        // Producer that closes channel
        val producer = launch {
            try {
                repeat(5) { i ->
                    channel.send(i)
                    println("  Sent: $i")
                    if (i == 2) {
                        // Simulate an early termination condition
                        println("  Early termination condition met")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                println("  Producer exception: ${e.message}")
            } finally {
                channel.close()
                println("  Producer: Channel closed")
            }
        }
        
        // Consumer that handles closed channel
        val consumer = launch {
            try {
                while (true) {
                    val value = channel.receive()
                    println("  Received: $value")
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  Consumer: Channel was closed")
            }
        }
        
        producer.join()
        consumer.join()
        
        println()
        
        // Closing with cause
        println("2. Closing channel with cause:")
        val errorChannel = Channel<String>()
        
        launch {
            try {
                errorChannel.send("First")
                errorChannel.send("Second")
                throw RuntimeException("Something went wrong!")
            } catch (e: Exception) {
                errorChannel.close(e)
                println("  Channel closed with cause: ${e.message}")
            }
        }
        
        launch {
            try {
                while (true) {
                    val value = errorChannel.receive()
                    println("  Received: $value")
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  Channel closed due to: ${e.cause?.message}")
            }
        }.join()
        
        println()
        
        // Using use extension for automatic closing
        println("3. Automatic closing with 'use':")
        
        // Custom use-like extension for channels
        suspend fun <T> Channel<T>.use(block: suspend (Channel<T>) -> Unit) {
            try {
                block(this)
            } finally {
                close()
            }
        }
        
        Channel<String>().use { ch ->
            launch {
                ch.send("Auto-closed message 1")
                ch.send("Auto-closed message 2")
                println("  Sent messages to auto-closing channel")
            }
            
            launch {
                for (message in ch) {
                    println("  Received from auto-closing: $message")
                }
                println("  Auto-closing channel consumption complete")
            }.join()
        }
        
        println("Channel closing completed\n")
    }
    
    fun demonstrateChannelExceptions() = runBlocking {
        println("=== Channel Exception Handling ===")
        
        println("1. Producer exception handling:")
        val channel = Channel<Int>()
        
        val producer = launch {
            try {
                repeat(10) { i ->
                    if (i == 5) {
                        throw RuntimeException("Producer error at $i")
                    }
                    channel.send(i)
                    println("  Sent: $i")
                }
            } catch (e: Exception) {
                println("  Producer caught exception: ${e.message}")
                channel.close(e) // Close with cause
            }
        }
        
        val consumer = launch {
            try {
                for (value in channel) {
                    println("  Received: $value")
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  Consumer: Channel closed due to ${e.cause?.message}")
            }
        }
        
        producer.join()
        consumer.join()
        
        println()
        
        // Consumer exception handling
        println("2. Consumer exception handling:")
        val dataChannel = Channel<String>()
        
        launch {
            repeat(5) { i ->
                dataChannel.send("Data-$i")
            }
            dataChannel.close()
        }
        
        launch {
            try {
                for (data in dataChannel) {
                    println("  Processing: $data")
                    if (data == "Data-2") {
                        throw RuntimeException("Consumer processing error")
                    }
                    println("  Processed: $data")
                }
            } catch (e: Exception) {
                println("  Consumer caught exception: ${e.message}")
                // Channel is automatically closed when consumer fails
            }
        }.join()
        
        println()
        
        // Exception propagation in channel operations
        println("3. Exception in channel operations:")
        
        // Cancelled channel operations
        val cancelledChannel = Channel<Int>()
        
        val cancellableJob = launch {
            try {
                repeat(10) { i ->
                    delay(100)
                    cancelledChannel.send(i)
                    println("  Sent: $i")
                }
            } catch (e: CancellationException) {
                println("  Send operation cancelled")
                cancelledChannel.close()
                throw e
            }
        }
        
        launch {
            try {
                repeat(3) { // Only receive first 3
                    val value = cancelledChannel.receive()
                    println("  Received: $value")
                }
                println("  Consumer stopping early, cancelling producer")
                cancellableJob.cancel()
            } catch (e: Exception) {
                println("  Consumer exception: ${e.message}")
            }
        }.join()
        
        try {
            cancellableJob.join()
        } catch (e: CancellationException) {
            println("  Producer was cancelled as expected")
        }
        
        println("Channel exception handling completed\n")
    }
}

/**
 * Channel performance and characteristics
 */
class ChannelPerformance {
    
    fun demonstrateChannelVsFlowPerformance() = runBlocking {
        println("=== Channel vs Flow Performance ===")
        
        val itemCount = 100_000
        
        println("1. Channel performance:")
        val channelTime = measureTimeMillis {
            val channel = Channel<Int>(capacity = 1000)
            
            launch {
                repeat(itemCount) { i ->
                    channel.send(i)
                }
                channel.close()
            }
            
            launch {
                var sum = 0
                for (value in channel) {
                    sum += value
                }
                println("  Channel sum: $sum")
            }.join()
        }
        
        println("  Channel time: ${channelTime}ms")
        
        println("2. Flow performance:")
        val flowTime = measureTimeMillis {
            kotlinx.coroutines.flow.flow {
                repeat(itemCount) { i ->
                    emit(i)
                }
            }.collect { value ->
                // Consume values
            }
        }
        
        println("  Flow time: ${flowTime}ms")
        
        println("3. Performance comparison:")
        val difference = channelTime - flowTime
        val percentDiff = (difference.toDouble() / flowTime) * 100
        println("  Difference: ${difference}ms (${String.format("%.1f", percentDiff)}%)")
        
        when {
            channelTime < flowTime -> println("  Channels are faster for this use case")
            channelTime > flowTime -> println("  Flows are faster for this use case")
            else -> println("  Performance is similar")
        }
        
        println()
        
        // Memory usage comparison
        println("4. Memory usage analysis:")
        
        fun estimateChannelMemory(capacity: Int, elementSize: Int): Int {
            return capacity * elementSize + 64 // 64 bytes overhead estimate
        }
        
        fun estimateFlowMemory(): Int {
            return 32 // Much smaller overhead for cold flows
        }
        
        val capacities = listOf(0, 10, 100, 1000)
        capacities.forEach { capacity ->
            val channelMem = estimateChannelMemory(capacity, 4) // 4 bytes per Int
            val flowMem = estimateFlowMemory()
            val memType = when (capacity) {
                0 -> "Rendezvous"
                else -> "Buffered($capacity)"
            }
            println("  $memType Channel: ~${channelMem} bytes, Flow: ~${flowMem} bytes")
        }
        
        println("Performance analysis completed\n")
    }
    
    fun demonstrateChannelCapacityImpact() = runBlocking {
        println("=== Channel Capacity Impact ===")
        
        suspend fun testChannelCapacity(capacity: Int, name: String) {
            println("Testing $name:")
            
            val channel = Channel<Int>(capacity)
            var sendOperations = 0
            var receiveOperations = 0
            
            val time = measureTimeMillis {
                launch {
                    repeat(1000) { i ->
                        channel.send(i)
                        sendOperations++
                        if (i % 100 == 0) delay(1) // Occasional pause
                    }
                    channel.close()
                }
                
                launch {
                    for (value in channel) {
                        receiveOperations++
                        delay(2) // Slow consumer
                    }
                }.join()
            }
            
            println("  Completed in ${time}ms")
            println("  Send ops: $sendOperations, Receive ops: $receiveOperations")
            println()
        }
        
        // Test different capacities
        testChannelCapacity(Channel.RENDEZVOUS, "Rendezvous (0)")
        testChannelCapacity(1, "Capacity 1")
        testChannelCapacity(10, "Capacity 10")
        testChannelCapacity(100, "Capacity 100")
        testChannelCapacity(Channel.UNLIMITED, "Unlimited")
        
        println("Channel capacity impact analysis completed\n")
    }
    
    fun demonstrateChannelContention() = runBlocking {
        println("=== Channel Contention Analysis ===")
        
        println("1. Multiple producers, single consumer:")
        val sharedChannel = Channel<String>(capacity = 50)
        
        val producers = (1..5).map { producerId ->
            launch {
                repeat(100) { i ->
                    sharedChannel.send("Producer-$producerId-Item-$i")
                    delay(Random.nextLong(1, 10))
                }
            }
        }
        
        val consumer = launch {
            var count = 0
            val producerCounts = mutableMapOf<String, Int>()
            
            while (count < 500) { // 5 producers × 100 items
                val item = sharedChannel.receive()
                count++
                
                val producerId = item.substringBefore("-Item")
                producerCounts[producerId] = producerCounts.getOrDefault(producerId, 0) + 1
                
                if (count % 100 == 0) {
                    println("  Consumed $count items so far...")
                }
                
                delay(1) // Processing time
            }
            
            println("  Final distribution: $producerCounts")
        }
        
        producers.forEach { it.join() }
        sharedChannel.close()
        consumer.join()
        
        println()
        
        println("2. Single producer, multiple consumers:")
        val distributionChannel = Channel<Int>(capacity = 20)
        
        val producer = launch {
            repeat(500) { i ->
                distributionChannel.send(i)
            }
            distributionChannel.close()
        }
        
        val consumers = (1..3).map { consumerId ->
            launch {
                var count = 0
                try {
                    while (true) {
                        val item = distributionChannel.receive()
                        count++
                        delay(Random.nextLong(1, 5)) // Variable processing time
                    }
                } catch (e: ClosedReceiveChannelException) {
                    println("  Consumer-$consumerId processed $count items")
                }
            }
        }
        
        producer.join()
        consumers.forEach { it.join() }
        
        println("Channel contention analysis completed\n")
    }
}

/**
 * Advanced channel patterns
 */
class AdvancedChannelPatterns {
    
    fun demonstrateChannelTransformation() = runBlocking {
        println("=== Channel Transformation Patterns ===")
        
        // Channel map transformation
        suspend fun <T, R> Channel<T>.map(transform: suspend (T) -> R): Channel<R> {
            val output = Channel<R>()
            launch {
                try {
                    for (item in this@map) {
                        output.send(transform(item))
                    }
                } catch (e: Exception) {
                    output.close(e)
                } finally {
                    output.close()
                }
            }
            return output
        }
        
        // Channel filter transformation
        suspend fun <T> Channel<T>.filter(predicate: suspend (T) -> Boolean): Channel<T> {
            val output = Channel<T>()
            launch {
                try {
                    for (item in this@filter) {
                        if (predicate(item)) {
                            output.send(item)
                        }
                    }
                } catch (e: Exception) {
                    output.close(e)
                } finally {
                    output.close()
                }
            }
            return output
        }
        
        println("1. Channel transformations:")
        
        val sourceChannel = Channel<Int>()
        
        // Start the transformation pipeline
        val mappedChannel = sourceChannel.map { it * 2 }
        val filteredChannel = mappedChannel.filter { it > 10 }
        
        // Producer
        launch {
            repeat(10) { i ->
                sourceChannel.send(i)
            }
            sourceChannel.close()
        }
        
        // Consumer
        launch {
            for (value in filteredChannel) {
                println("  Transformed result: $value")
            }
        }.join()
        
        println()
        
        // Channel merge operation
        suspend fun <T> merge(vararg channels: Channel<T>): Channel<T> {
            val output = Channel<T>()
            
            channels.forEach { channel ->
                launch {
                    try {
                        for (item in channel) {
                            output.send(item)
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        // Channel closed normally
                    }
                }
            }
            
            // Close output when all input channels are closed
            launch {
                channels.forEach { it.join() }
                output.close()
            }
            
            return output
        }
        
        println("2. Channel merging:")
        
        val channel1 = Channel<String>()
        val channel2 = Channel<String>()
        val channel3 = Channel<String>()
        
        val mergedChannel = merge(channel1, channel2, channel3)
        
        // Start producers
        launch {
            repeat(3) { i ->
                channel1.send("Ch1-$i")
                delay(100)
            }
            channel1.close()
        }
        
        launch {
            repeat(3) { i ->
                channel2.send("Ch2-$i")
                delay(150)
            }
            channel2.close()
        }
        
        launch {
            repeat(3) { i ->
                channel3.send("Ch3-$i")
                delay(80)
            }
            channel3.close()
        }
        
        // Consumer
        launch {
            for (value in mergedChannel) {
                println("  Merged: $value")
            }
        }.join()
        
        println("Channel transformation patterns completed\n")
    }
    
    fun demonstrateChannelBroadcast() = runBlocking {
        println("=== Channel Broadcast Patterns ===")
        
        // Simple broadcast implementation
        suspend fun <T> Channel<T>.broadcast(consumerCount: Int): List<Channel<T>> {
            val outputs = List(consumerCount) { Channel<T>() }
            
            launch {
                try {
                    for (item in this@broadcast) {
                        outputs.forEach { output ->
                            output.send(item)
                        }
                    }
                } catch (e: Exception) {
                    outputs.forEach { it.close(e) }
                } finally {
                    outputs.forEach { it.close() }
                }
            }
            
            return outputs
        }
        
        println("1. Broadcasting to multiple consumers:")
        
        val sourceChannel = Channel<String>()
        val broadcastChannels = sourceChannel.broadcast(3)
        
        // Producer
        launch {
            repeat(5) { i ->
                val message = "Broadcast-$i"
                sourceChannel.send(message)
                println("  Broadcasted: $message")
                delay(200)
            }
            sourceChannel.close()
        }
        
        // Multiple consumers
        val consumers = broadcastChannels.mapIndexed { index, channel ->
            launch {
                for (message in channel) {
                    println("  Consumer-$index received: $message")
                    delay(Random.nextLong(50, 300)) // Different processing speeds
                }
                println("  Consumer-$index finished")
            }
        }
        
        consumers.forEach { it.join() }
        
        println()
        
        // Selective broadcast (routing based on content)
        println("2. Selective broadcast (routing):")
        
        data class Message(val type: String, val content: String)
        
        suspend fun <T> Channel<Message>.routeTo(
            routes: Map<String, Channel<Message>>
        ) {
            launch {
                try {
                    for (message in this@routeTo) {
                        val targetChannel = routes[message.type]
                        if (targetChannel != null) {
                            targetChannel.send(message)
                            println("  Routed ${message.type} message to appropriate handler")
                        } else {
                            println("  No route found for message type: ${message.type}")
                        }
                    }
                } catch (e: Exception) {
                    routes.values.forEach { it.close(e) }
                } finally {
                    routes.values.forEach { it.close() }
                }
            }
        }
        
        val messageChannel = Channel<Message>()
        val errorChannel = Channel<Message>()
        val warningChannel = Channel<Message>()
        val infoChannel = Channel<Message>()
        
        val routes = mapOf(
            "ERROR" to errorChannel,
            "WARNING" to warningChannel,
            "INFO" to infoChannel
        )
        
        messageChannel.routeTo(routes)
        
        // Message producer
        launch {
            val messages = listOf(
                Message("ERROR", "Database connection failed"),
                Message("INFO", "User logged in"),
                Message("WARNING", "High memory usage"),
                Message("ERROR", "Network timeout"),
                Message("INFO", "Process completed")
            )
            
            messages.forEach { message ->
                messageChannel.send(message)
                delay(100)
            }
            messageChannel.close()
        }
        
        // Specialized consumers
        val errorConsumer = launch {
            for (message in errorChannel) {
                println("  🚨 ERROR Handler: ${message.content}")
            }
        }
        
        val warningConsumer = launch {
            for (message in warningChannel) {
                println("  ⚠️ WARNING Handler: ${message.content}")
            }
        }
        
        val infoConsumer = launch {
            for (message in infoChannel) {
                println("  ℹ️ INFO Handler: ${message.content}")
            }
        }
        
        listOf(errorConsumer, warningConsumer, infoConsumer).forEach { it.join() }
        
        println("Channel broadcast patterns completed\n")
    }
}

/**
 * Extension function for channel joining (waiting for channel to be closed)
 */
suspend fun <T> Channel<T>.join() {
    while (!isClosedForSend) {
        delay(10)
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic channel operations
        BasicChannelOperations().demonstrateBasicChannelUsage()
        BasicChannelOperations().demonstrateChannelTypes()
        BasicChannelOperations().demonstrateChannelClosing()
        BasicChannelOperations().demonstrateChannelExceptions()
        
        // Channel performance analysis
        ChannelPerformance().demonstrateChannelVsFlowPerformance()
        ChannelPerformance().demonstrateChannelCapacityImpact()
        ChannelPerformance().demonstrateChannelContention()
        
        // Advanced patterns
        AdvancedChannelPatterns().demonstrateChannelTransformation()
        AdvancedChannelPatterns().demonstrateChannelBroadcast()
        
        println("=== Channel Basics Summary ===")
        println("✅ Channel Fundamentals:")
        println("   - Hot streams that exist independently of consumers")
        println("   - Send/receive operations with suspension semantics")
        println("   - Different capacity types: Rendezvous, Buffered, Unlimited, Conflated")
        println("   - Proper closing and exception handling")
        println()
        println("✅ Channel Types:")
        println("   - Rendezvous (0): Direct handoff, no buffering")
        println("   - Buffered (N): Fixed buffer size with suspension on overflow")
        println("   - Unlimited (∞): No capacity limit (memory risk)")
        println("   - Conflated (1): Only latest value kept")
        println()
        println("✅ Performance Characteristics:")
        println("   - Channels have higher overhead than flows for simple pipelines")
        println("   - Buffer size significantly impacts performance and memory")
        println("   - Excellent for concurrent producer-consumer scenarios")
        println("   - Memory usage scales with buffer capacity")
        println()
        println("✅ Advanced Patterns:")
        println("   - Channel transformations (map, filter)")
        println("   - Broadcasting to multiple consumers")
        println("   - Selective routing based on content")
        println("   - Merging multiple channels")
        println()
        println("✅ Best Practices:")
        println("   - Always close channels to prevent resource leaks")
        println("   - Handle exceptions and cancellation properly")
        println("   - Choose appropriate capacity based on use case")
        println("   - Use rendezvous for backpressure, buffered for performance")
        println("   - Consider flows for simple transformation pipelines")
    }
}