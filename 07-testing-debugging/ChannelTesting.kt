/**
 * # Channel Testing Patterns and Techniques
 * 
 * ## Problem Description
 * Testing channel-based communication in coroutines presents unique challenges
 * due to their concurrent nature, buffering behavior, and complex producer-consumer
 * patterns. Channels can have different capacities, closing behaviors, and
 * exception handling characteristics that all need thorough testing. Standard
 * testing approaches often struggle with the asynchronous and potentially
 * infinite nature of channel operations.
 * 
 * ## Solution Approach
 * Channel testing strategies include:
 * - Testing different channel types (rendezvous, buffered, unlimited, conflated)
 * - Verifying producer-consumer communication patterns
 * - Testing channel closing and cancellation scenarios
 * - Mocking channel-based dependencies and services
 * - Performance testing of channel throughput and backpressure
 * 
 * ## Key Learning Points
 * - Channel.send() and Channel.receive() behavior in tests
 * - Testing channel capacity and overflow strategies
 * - Verifying proper channel closing and exception propagation
 * - Mocking channel producers and consumers for isolation
 * - Testing concurrent channel access and synchronization
 * 
 * ## Performance Considerations
 * - Virtual time enables fast testing of channel timeouts
 * - Buffered channels reduce coordination overhead in tests
 * - Test execution speed improved by eliminating real delays
 * - Memory usage controlled by limiting channel buffer sizes
 * - Concurrent testing patterns verify scalability characteristics
 * 
 * ## Common Pitfalls
 * - Not testing channel closing scenarios properly
 * - Forgetting to test different channel capacities
 * - Missing exception handling in producer-consumer patterns
 * - Not testing concurrent access and race conditions
 * - Inadequate cleanup leading to resource leaks in tests
 * 
 * ## Real-World Applications
 * - Testing message passing systems and event buses
 * - Validating data pipeline processing components
 * - Testing rate limiting and throttling mechanisms
 * - Worker pool and job queue testing
 * - Inter-service communication testing
 * 
 * ## Related Concepts
 * - Actor model testing patterns
 * - Message queue testing strategies
 * - Load balancing and distribution testing
 * - Reactive streams testing approaches
 */

package testing.debugging.channels

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic channel testing patterns and techniques
 * 
 * Channel Testing Architecture:
 * 
 * Test Structure:
 * ├── Channel Creation ──── Channel(), produce(), actor()
 * ├── Producer Testing ──── Send operations, closing behavior
 * ├── Consumer Testing ──── Receive operations, iteration patterns
 * ├── Communication ─────── Producer-consumer coordination
 * └── Cleanup Testing ───── Resource management, cancellation
 * 
 * Testing Patterns:
 * Setup Channel -> Start Producer -> Start Consumer -> Assert Communication -> Cleanup
 */
class BasicChannelTestPatterns {
    
    // Example data classes for testing
    data class Message(val id: Int, val content: String, val timestamp: Long = System.currentTimeMillis())
    data class Task(val id: String, val priority: Int, val data: String)
    data class Result(val taskId: String, val success: Boolean, val output: String)
    
    // Service classes for channel-based operations
    class MessageService {
        suspend fun sendMessages(channel: SendChannel<Message>, count: Int, delay: Long = 100) {
            repeat(count) { i ->
                delay(delay)
                val message = Message(i, "Message $i")
                channel.send(message)
            }
            channel.close()
        }
        
        suspend fun processMessages(channel: ReceiveChannel<Message>): List<String> {
            val results = mutableListOf<String>()
            for (message in channel) {
                delay(50) // Simulate processing time
                results.add("Processed: ${message.content}")
            }
            return results
        }
        
        suspend fun filterMessages(
            input: ReceiveChannel<Message>, 
            output: SendChannel<Message>,
            predicate: (Message) -> Boolean
        ) {
            try {
                for (message in input) {
                    if (predicate(message)) {
                        output.send(message)
                    }
                }
            } finally {
                output.close()
            }
        }
    }
    
    @Test
    fun testBasicChannelCommunication() = runTest {
        println("=== Testing Basic Channel Communication ===")
        
        val channel = Channel<String>()
        val results = mutableListOf<String>()
        
        // Start consumer
        val consumerJob = launch {
            for (message in channel) {
                results.add(message)
            }
        }
        
        // Start producer
        val producerJob = launch {
            repeat(3) { i ->
                channel.send("Message $i")
                delay(50)
            }
            channel.close()
        }
        
        // Wait for completion
        producerJob.join()
        consumerJob.join()
        
        assertEquals(listOf("Message 0", "Message 1", "Message 2"), results)
        assertTrue(channel.isClosedForSend)
        assertTrue(channel.isClosedForReceive)
        
        println("✅ Basic channel communication test passed")
    }
    
    @Test
    fun testRendezvousChannel() = runTest {
        println("=== Testing Rendezvous Channel ===")
        
        val channel = Channel<Int>() // Rendezvous channel (capacity = 0)
        val sendTimes = mutableListOf<Long>()
        val receiveTimes = mutableListOf<Long>()
        
        // Producer that records send times
        val producer = launch {
            repeat(3) { i ->
                val startTime = currentTime
                channel.send(i)
                sendTimes.add(currentTime - startTime)
            }
            channel.close()
        }
        
        // Consumer that receives with delay
        val consumer = launch {
            for (value in channel) {
                val startTime = currentTime
                delay(100) // Processing time
                receiveTimes.add(currentTime - startTime)
            }
        }
        
        producer.join()
        consumer.join()
        
        // In rendezvous channel, sends should block until receive
        assertEquals(3, sendTimes.size)
        assertEquals(3, receiveTimes.size)
        
        // Each send should wait for corresponding receive
        sendTimes.forEach { sendTime ->
            assertTrue(sendTime >= 100, "Send should block until receive completes")
        }
        
        println("✅ Rendezvous channel test passed")
    }
    
    @Test
    fun testBufferedChannel() = runTest {
        println("=== Testing Buffered Channel ===")
        
        val bufferSize = 3
        val channel = Channel<String>(bufferSize)
        val results = mutableListOf<String>()
        
        // Send more than buffer size without consumer
        val producer = launch {
            repeat(5) { i ->
                val message = "Item $i"
                println("  Sending: $message")
                channel.send(message)
                println("  Sent: $message")
            }
            channel.close()
        }
        
        // Let producer run
        advanceTimeBy(100)
        
        // Verify first 3 items are buffered, producer blocks on 4th
        assertFalse(producer.isCompleted, "Producer should be blocked after filling buffer")
        
        // Start consumer
        val consumer = launch {
            for (item in channel) {
                results.add(item)
                delay(50) // Slow consumer
            }
        }
        
        // Complete both
        producer.join()
        consumer.join()
        
        assertEquals(5, results.size)
        assertEquals("Item 0", results[0])
        assertEquals("Item 4", results[4])
        
        println("✅ Buffered channel test passed")
    }
    
    @Test
    fun testUnlimitedChannel() = runTest {
        println("=== Testing Unlimited Channel ===")
        
        val channel = Channel<Int>(Channel.UNLIMITED)
        
        // Producer should never block
        val producer = launch {
            repeat(1000) { i ->
                channel.send(i)
                // No delay - should send immediately
            }
            channel.close()
        }
        
        // Producer should complete quickly
        producer.join()
        assertTrue(producer.isCompleted, "Producer should complete without blocking")
        
        // Verify all items are in channel
        val results = mutableListOf<Int>()
        for (item in channel) {
            results.add(item)
        }
        
        assertEquals(1000, results.size)
        assertEquals((0 until 1000).toList(), results)
        
        println("✅ Unlimited channel test passed")
    }
    
    @Test
    fun testConflatedChannel() = runTest {
        println("=== Testing Conflated Channel ===")
        
        val channel = Channel<String>(Channel.CONFLATED)
        
        // Send multiple values quickly
        val producer = launch {
            repeat(10) { i ->
                channel.send("Value $i")
                delay(10) // Fast producer
            }
            channel.close()
        }
        
        // Slow consumer
        val consumer = launch {
            delay(50) // Let producer get ahead
            val results = mutableListOf<String>()
            for (value in channel) {
                results.add(value)
                delay(100) // Slow processing
            }
            
            // Should receive fewer items due to conflation
            assertTrue(results.size < 10, "Conflated channel should drop intermediate values")
            println("  Received ${results.size} items: $results")
        }
        
        producer.join()
        consumer.join()
        
        println("✅ Conflated channel test passed")
    }
    
    @Test
    fun testChannelClosing() = runTest {
        println("=== Testing Channel Closing ===")
        
        val channel = Channel<String>()
        val results = mutableListOf<String>()
        
        // Consumer that handles closing
        val consumer = launch {
            try {
                for (message in channel) {
                    results.add(message)
                }
                results.add("COMPLETED_NORMALLY")
            } catch (e: ClosedReceiveChannelException) {
                results.add("CLOSED_EXCEPTION")
            }
        }
        
        // Producer that sends some messages then closes
        launch {
            channel.send("Message 1")
            channel.send("Message 2")
            delay(50)
            channel.close()
        }
        
        consumer.join()
        
        assertEquals(listOf("Message 1", "Message 2", "COMPLETED_NORMALLY"), results)
        assertTrue(channel.isClosedForSend)
        assertTrue(channel.isClosedForReceive)
        
        // Verify that trying to send after close fails
        assertFailsWith<ClosedSendChannelException> {
            channel.send("Should fail")
        }
        
        println("✅ Channel closing test passed")
    }
    
    @Test
    fun testChannelExceptionHandling() = runTest {
        println("=== Testing Channel Exception Handling ===")
        
        val channel = Channel<String>()
        val results = mutableListOf<String>()
        var exceptionCaught: Exception? = null
        
        // Consumer that might receive exception
        val consumer = launch {
            try {
                for (message in channel) {
                    results.add(message)
                }
            } catch (e: Exception) {
                exceptionCaught = e
                results.add("EXCEPTION_CAUGHT: ${e.message}")
            }
        }
        
        // Producer that fails after sending some messages
        val producer = launch {
            try {
                channel.send("Message 1")
                channel.send("Message 2")
                delay(50)
                throw RuntimeException("Producer failed")
            } catch (e: Exception) {
                channel.close(e) // Close with exception
                throw e
            }
        }
        
        // Wait for producer to fail
        try {
            producer.join()
        } catch (e: RuntimeException) {
            // Expected
        }
        
        consumer.join()
        
        assertEquals(listOf("Message 1", "Message 2", "EXCEPTION_CAUGHT: Producer failed"), results)
        assertNotNull(exceptionCaught)
        assertTrue(channel.isClosedForSend)
        
        println("✅ Channel exception handling test passed")
    }
}

/**
 * Advanced channel testing patterns with producer-consumer scenarios
 */
class AdvancedChannelTestPatterns {
    
    class TaskProcessor {
        suspend fun processTasksPipeline(
            inputTasks: ReceiveChannel<Task>,
            outputResults: SendChannel<Result>
        ) {
            for (task in inputTasks) {
                try {
                    delay(task.priority * 10) // Processing time based on priority
                    val result = Result(
                        taskId = task.id,
                        success = !task.data.contains("error"),
                        output = "Processed: ${task.data}"
                    )
                    outputResults.send(result)
                } catch (e: Exception) {
                    val errorResult = Result(
                        taskId = task.id,
                        success = false,
                        output = "Error: ${e.message}"
                    )
                    outputResults.send(errorResult)
                }
            }
        }
        
        suspend fun loadBalanceWork(
            tasks: ReceiveChannel<Task>,
            workers: List<SendChannel<Task>>
        ) {
            var workerIndex = 0
            for (task in tasks) {
                val worker = workers[workerIndex % workers.size]
                worker.send(task)
                workerIndex++
            }
            // Close all worker channels
            workers.forEach { it.close() }
        }
        
        suspend fun aggregateResults(
            results: List<ReceiveChannel<Result>>,
            output: SendChannel<Result>
        ) {
            val allResults = results.map { channel ->
                async {
                    val channelResults = mutableListOf<Result>()
                    for (result in channel) {
                        channelResults.add(result)
                    }
                    channelResults
                }
            }.awaitAll().flatten()
            
            allResults.forEach { output.send(it) }
            output.close()
        }
    }
    
    @Test
    fun testProducerConsumerPattern() = runTest {
        println("=== Testing Producer-Consumer Pattern ===")
        
        val messageService = MessageService()
        val channel = Channel<Message>(capacity = 5)
        val results = mutableListOf<String>()
        
        // Start consumer
        val consumer = launch {
            val processed = messageService.processMessages(channel)
            results.addAll(processed)
        }
        
        // Start producer
        val producer = launch {
            messageService.sendMessages(channel, count = 3, delay = 50)
        }
        
        // Wait for completion
        producer.join()
        consumer.join()
        
        assertEquals(3, results.size)
        assertTrue(results.all { it.startsWith("Processed: Message") })
        
        println("✅ Producer-consumer pattern test passed")
    }
    
    @Test
    fun testPipelineProcessing() = runTest {
        println("=== Testing Pipeline Processing ===")
        
        val processor = TaskProcessor()
        val inputChannel = Channel<Task>()
        val outputChannel = Channel<Result>()
        
        val tasks = listOf(
            Task("1", 1, "task data 1"),
            Task("2", 3, "task data 2"),
            Task("3", 2, "error task"), // Should fail
            Task("4", 1, "task data 4")
        )
        
        // Start pipeline processor
        val processorJob = launch {
            processor.processTasksPipeline(inputChannel, outputChannel)
            outputChannel.close()
        }
        
        // Send tasks
        val producer = launch {
            tasks.forEach { task ->
                inputChannel.send(task)
                delay(25)
            }
            inputChannel.close()
        }
        
        // Collect results
        val results = mutableListOf<Result>()
        val consumer = launch {
            for (result in outputChannel) {
                results.add(result)
            }
        }
        
        // Wait for completion
        producer.join()
        processorJob.join()
        consumer.join()
        
        assertEquals(4, results.size)
        assertEquals(3, results.count { it.success })
        assertEquals(1, results.count { !it.success && it.taskId == "3" })
        
        println("✅ Pipeline processing test passed")
    }
    
    @Test
    fun testLoadBalancing() = runTest {
        println("=== Testing Load Balancing ===")
        
        val processor = TaskProcessor()
        val numWorkers = 3
        val numTasks = 9
        
        val taskChannel = Channel<Task>()
        val workerChannels = (0 until numWorkers).map { Channel<Task>() }
        val workerResults = workerChannels.map { mutableListOf<Task>() }
        
        // Start workers
        val workerJobs = workerChannels.mapIndexed { index, workerChannel ->
            launch {
                for (task in workerChannel) {
                    delay(50) // Simulate work
                    workerResults[index].add(task)
                }
            }
        }
        
        // Start load balancer
        val balancer = launch {
            processor.loadBalanceWork(taskChannel, workerChannels)
        }
        
        // Send tasks
        val producer = launch {
            repeat(numTasks) { i ->
                taskChannel.send(Task("task-$i", 1, "data-$i"))
                delay(10)
            }
            taskChannel.close()
        }
        
        // Wait for completion
        producer.join()
        balancer.join()
        workerJobs.forEach { it.join() }
        
        // Verify load balancing
        val totalProcessed = workerResults.sumOf { it.size }
        assertEquals(numTasks, totalProcessed)
        
        // Each worker should have approximately equal tasks
        workerResults.forEach { results ->
            assertTrue(results.size >= 2, "Each worker should process at least 2 tasks")
        }
        
        println("  Worker loads: ${workerResults.map { it.size }}")
        println("✅ Load balancing test passed")
    }
    
    @Test
    fun testFanInPattern() = runTest {
        println("=== Testing Fan-In Pattern ===")
        
        val processor = TaskProcessor()
        val numProducers = 3
        val producerChannels = (0 until numProducers).map { Channel<Result>() }
        val aggregatedChannel = Channel<Result>()
        
        // Start producers
        val producerJobs = producerChannels.mapIndexed { index, channel ->
            launch {
                repeat(3) { i ->
                    val result = Result("producer-$index-task-$i", true, "result from producer $index")
                    channel.send(result)
                    delay(50)
                }
                channel.close()
            }
        }
        
        // Start aggregator
        val aggregator = launch {
            processor.aggregateResults(producerChannels, aggregatedChannel)
        }
        
        // Collect aggregated results
        val allResults = mutableListOf<Result>()
        val collector = launch {
            for (result in aggregatedChannel) {
                allResults.add(result)
            }
        }
        
        // Wait for completion
        producerJobs.forEach { it.join() }
        aggregator.join()
        collector.join()
        
        assertEquals(9, allResults.size) // 3 producers × 3 results each
        
        // Verify results from all producers are present
        (0 until numProducers).forEach { producerIndex ->
            val producerResults = allResults.filter { it.taskId.contains("producer-$producerIndex") }
            assertEquals(3, producerResults.size)
        }
        
        println("✅ Fan-in pattern test passed")
    }
    
    @Test
    fun testChannelTimeouts() = runTest {
        println("=== Testing Channel Timeouts ===")
        
        val channel = Channel<String>()
        var timeoutOccurred = false
        
        // Consumer with timeout
        val consumer = launch {
            try {
                withTimeout(200) {
                    for (message in channel) {
                        delay(150) // Slow processing
                    }
                }
            } catch (e: TimeoutCancellationException) {
                timeoutOccurred = true
            }
        }
        
        // Slow producer
        val producer = launch {
            repeat(3) { i ->
                channel.send("Message $i")
                delay(100)
            }
            channel.close()
        }
        
        producer.join()
        consumer.join()
        
        assertTrue(timeoutOccurred, "Timeout should have occurred")
        
        println("✅ Channel timeouts test passed")
    }
    
    @Test
    fun testChannelBackpressure() = runTest {
        println("=== Testing Channel Backpressure ===")
        
        val bufferSize = 2
        val channel = Channel<String>(bufferSize)
        val sendTimes = mutableListOf<Long>()
        
        // Fast producer
        val producer = launch {
            repeat(5) { i ->
                val start = currentTime
                channel.send("Item $i")
                val elapsed = currentTime - start
                sendTimes.add(elapsed)
                println("  Sent 'Item $i' in ${elapsed}ms")
            }
            channel.close()
        }
        
        // Slow consumer that starts after delay
        val consumer = launch {
            delay(100) // Let producer fill buffer first
            for (item in channel) {
                println("  Processing: $item")
                delay(50) // Slow processing
            }
        }
        
        producer.join()
        consumer.join()
        
        // First sends should be fast (buffer), later sends should be slower (backpressure)
        assertTrue(sendTimes[0] < 10, "First send should be fast")
        assertTrue(sendTimes[1] < 10, "Second send should be fast")
        
        // Later sends should take longer due to backpressure
        assertTrue(sendTimes.drop(2).any { it > 40 }, "Later sends should experience backpressure")
        
        println("  Send times: $sendTimes")
        println("✅ Channel backpressure test passed")
    }
}

/**
 * Channel mocking and testing utilities
 */
class ChannelMockingPatterns {
    
    // Interface for channel-based services
    interface EventBus {
        suspend fun publish(event: String)
        suspend fun subscribe(): ReceiveChannel<String>
        suspend fun unsubscribe()
    }
    
    interface DataPipeline {
        suspend fun process(input: ReceiveChannel<String>): ReceiveChannel<String>
        suspend fun filter(input: ReceiveChannel<String>, predicate: (String) -> Boolean): ReceiveChannel<String>
        suspend fun transform(input: ReceiveChannel<String>, transformer: (String) -> String): ReceiveChannel<String>
    }
    
    // Mock implementations
    class MockEventBus : EventBus {
        private val events = mutableListOf<String>()
        private val subscribers = mutableListOf<SendChannel<String>>()
        
        override suspend fun publish(event: String) {
            events.add(event)
            subscribers.forEach { channel ->
                try {
                    channel.send(event)
                } catch (e: Exception) {
                    // Ignore closed channels
                }
            }
        }
        
        override suspend fun subscribe(): ReceiveChannel<String> {
            val channel = Channel<String>()
            subscribers.add(channel)
            return channel
        }
        
        override suspend fun unsubscribe() {
            subscribers.forEach { it.close() }
            subscribers.clear()
        }
        
        fun getPublishedEvents(): List<String> = events.toList()
        fun getSubscriberCount(): Int = subscribers.size
    }
    
    class MockDataPipeline : DataPipeline {
        private val processedItems = mutableListOf<String>()
        private val filteredItems = mutableListOf<String>()
        private val transformedItems = mutableListOf<String>()
        
        override suspend fun process(input: ReceiveChannel<String>): ReceiveChannel<String> {
            val output = Channel<String>()
            launch {
                for (item in input) {
                    delay(50) // Simulate processing
                    val processed = "Processed: $item"
                    processedItems.add(processed)
                    output.send(processed)
                }
                output.close()
            }
            return output
        }
        
        override suspend fun filter(input: ReceiveChannel<String>, predicate: (String) -> Boolean): ReceiveChannel<String> {
            val output = Channel<String>()
            launch {
                for (item in input) {
                    if (predicate(item)) {
                        filteredItems.add(item)
                        output.send(item)
                    }
                }
                output.close()
            }
            return output
        }
        
        override suspend fun transform(input: ReceiveChannel<String>, transformer: (String) -> String): ReceiveChannel<String> {
            val output = Channel<String>()
            launch {
                for (item in input) {
                    val transformed = transformer(item)
                    transformedItems.add(transformed)
                    output.send(transformed)
                }
                output.close()
            }
            return output
        }
        
        fun getProcessedItems(): List<String> = processedItems.toList()
        fun getFilteredItems(): List<String> = filteredItems.toList()
        fun getTransformedItems(): List<String> = transformedItems.toList()
    }
    
    @Test
    fun testMockEventBus() = runTest {
        println("=== Testing Mock Event Bus ===")
        
        val eventBus = MockEventBus()
        val receivedEvents = mutableListOf<String>()
        
        // Subscribe to events
        val subscription = eventBus.subscribe()
        val subscriber = launch {
            for (event in subscription) {
                receivedEvents.add(event)
            }
        }
        
        // Publish events
        eventBus.publish("Event 1")
        eventBus.publish("Event 2")
        eventBus.publish("Event 3")
        
        // Give time for events to propagate
        advanceTimeBy(100)
        
        // Unsubscribe
        eventBus.unsubscribe()
        subscriber.join()
        
        // Verify events
        assertEquals(listOf("Event 1", "Event 2", "Event 3"), receivedEvents)
        assertEquals(listOf("Event 1", "Event 2", "Event 3"), eventBus.getPublishedEvents())
        assertEquals(0, eventBus.getSubscriberCount())
        
        println("✅ Mock event bus test passed")
    }
    
    @Test
    fun testMockDataPipeline() = runTest {
        println("=== Testing Mock Data Pipeline ===")
        
        val pipeline = MockDataPipeline()
        val inputData = listOf("item1", "item2", "item3", "special", "item5")
        
        // Create input channel
        val inputChannel = Channel<String>()
        launch {
            inputData.forEach { inputChannel.send(it) }
            inputChannel.close()
        }
        
        // Process data
        val processedChannel = pipeline.process(inputChannel)
        
        // Filter processed data (only items containing "special")
        val filteredChannel = pipeline.filter(processedChannel) { it.contains("special") }
        
        // Transform filtered data
        val transformedChannel = pipeline.transform(filteredChannel) { it.uppercase() }
        
        // Collect final results
        val finalResults = mutableListOf<String>()
        for (result in transformedChannel) {
            finalResults.add(result)
        }
        
        // Verify pipeline stages
        assertEquals(5, pipeline.getProcessedItems().size)
        assertEquals(1, pipeline.getFilteredItems().size) // Only "Processed: special"
        assertEquals(1, pipeline.getTransformedItems().size)
        assertEquals(1, finalResults.size)
        
        assertTrue(finalResults[0].contains("PROCESSED: SPECIAL"))
        
        println("✅ Mock data pipeline test passed")
    }
    
    @Test
    fun testChannelMockingWithDependencyInjection() = runTest {
        println("=== Testing Channel Mocking with Dependency Injection ===")
        
        class MessageHandler(private val eventBus: EventBus) {
            suspend fun handleMessage(message: String): String {
                eventBus.publish("Handling: $message")
                delay(100) // Simulate processing
                val result = "Handled: $message"
                eventBus.publish("Completed: $message")
                return result
            }
        }
        
        val mockEventBus = MockEventBus()
        val handler = MessageHandler(mockEventBus)
        
        // Handle a message
        val result = handler.handleMessage("test message")
        
        // Verify result
        assertEquals("Handled: test message", result)
        
        // Verify events were published
        val events = mockEventBus.getPublishedEvents()
        assertEquals(2, events.size)
        assertEquals("Handling: test message", events[0])
        assertEquals("Completed: test message", events[1])
        
        println("✅ Channel mocking with dependency injection test passed")
    }
}

/**
 * Performance testing for channels
 */
class ChannelPerformanceTestPatterns {
    
    class PerformanceChannelService {
        suspend fun measureThroughput(
            channel: Channel<String>,
            messageCount: Int,
            producerDelay: Long = 0,
            consumerDelay: Long = 0
        ): Pair<Long, Long> { // Returns (producer time, consumer time)
            
            var producerTime = 0L
            var consumerTime = 0L
            val results = mutableListOf<String>()
            
            // Consumer
            val consumer = launch {
                val startTime = currentTime
                for (message in channel) {
                    if (consumerDelay > 0) delay(consumerDelay)
                    results.add(message)
                }
                consumerTime = currentTime - startTime
            }
            
            // Producer
            val producer = launch {
                val startTime = currentTime
                repeat(messageCount) { i ->
                    if (producerDelay > 0) delay(producerDelay)
                    channel.send("Message $i")
                }
                channel.close()
                producerTime = currentTime - startTime
            }
            
            producer.join()
            consumer.join()
            
            assertEquals(messageCount, results.size)
            return producerTime to consumerTime
        }
        
        suspend fun testConcurrentAccess(channel: Channel<String>, concurrency: Int): List<Long> {
            val executionTimes = mutableListOf<Long>()
            
            val jobs = (1..concurrency).map { workerId ->
                launch {
                    val startTime = currentTime
                    repeat(100) { i ->
                        channel.send("Worker-$workerId-Message-$i")
                    }
                    executionTimes.add(currentTime - startTime)
                }
            }
            
            jobs.forEach { it.join() }
            channel.close()
            
            return executionTimes
        }
    }
    
    @Test
    fun testChannelThroughputComparison() = runTest {
        println("=== Testing Channel Throughput Comparison ===")
        
        val service = PerformanceChannelService()
        val messageCount = 1000
        val results = mutableMapOf<String, Pair<Long, Long>>()
        
        // Test different channel types
        val channelConfigs = mapOf(
            "Rendezvous" to Channel<String>(),
            "Buffered(10)" to Channel<String>(10),
            "Buffered(100)" to Channel<String>(100),
            "Unlimited" to Channel<String>(Channel.UNLIMITED),
            "Conflated" to Channel<String>(Channel.CONFLATED)
        )
        
        for ((name, channel) in channelConfigs) {
            val (producerTime, consumerTime) = service.measureThroughput(channel, messageCount)
            results[name] = producerTime to consumerTime
            
            println("  $name: Producer=${producerTime}ms, Consumer=${consumerTime}ms")
        }
        
        // Verify some expected performance characteristics
        val rendezvousTimes = results["Rendezvous"]!!
        val bufferedTimes = results["Buffered(100)"]!!
        val unlimitedTimes = results["Unlimited"]!!
        
        // Unlimited should be fastest for producer
        assertTrue(unlimitedTimes.first <= bufferedTimes.first,
            "Unlimited channel producer should be fastest")
        
        // Buffered should be faster than rendezvous for producer
        assertTrue(bufferedTimes.first <= rendezvousTimes.first,
            "Buffered channel producer should be faster than rendezvous")
        
        println("✅ Channel throughput comparison test passed")
    }
    
    @Test
    fun testConcurrentChannelAccess() = runTest {
        println("=== Testing Concurrent Channel Access ===")
        
        val service = PerformanceChannelService()
        val channel = Channel<String>(Channel.UNLIMITED) // Use unlimited to avoid blocking
        val concurrencyLevels = listOf(1, 2, 4, 8)
        val results = mutableMapOf<Int, List<Long>>()
        
        for (concurrency in concurrencyLevels) {
            val executionTimes = service.testConcurrentAccess(
                Channel<String>(Channel.UNLIMITED), // Fresh channel for each test
                concurrency
            )
            results[concurrency] = executionTimes
            
            val avgTime = executionTimes.average()
            val maxTime = executionTimes.maxOrNull() ?: 0L
            
            println("  Concurrency $concurrency: Avg=${avgTime.toInt()}ms, Max=${maxTime}ms")
        }
        
        // Verify that concurrent access works
        results.forEach { (concurrency, times) ->
            assertEquals(concurrency, times.size, "Should have timing for each worker")
            assertTrue(times.all { it > 0 }, "All workers should take some time")
        }
        
        println("✅ Concurrent channel access test passed")
    }
    
    @Test
    fun testBackpressurePerformance() = runTest {
        println("=== Testing Backpressure Performance ===")
        
        val service = PerformanceChannelService()
        val messageCount = 100
        
        // Test with fast producer, slow consumer
        val fastProducerSlowConsumer = service.measureThroughput(
            Channel<String>(5), // Small buffer
            messageCount,
            producerDelay = 10, // Fast producer
            consumerDelay = 50  // Slow consumer
        )
        
        // Test with slow producer, fast consumer
        val slowProducerFastConsumer = service.measureThroughput(
            Channel<String>(5),
            messageCount,
            producerDelay = 50, // Slow producer
            consumerDelay = 10  // Fast consumer
        )
        
        println("  Fast Producer/Slow Consumer: Producer=${fastProducerSlowConsumer.first}ms, Consumer=${fastProducerSlowConsumer.second}ms")
        println("  Slow Producer/Fast Consumer: Producer=${slowProducerFastConsumer.first}ms, Consumer=${slowProducerFastConsumer.second}ms")
        
        // In backpressure scenario, producer should be slower due to blocking
        assertTrue(fastProducerSlowConsumer.first > messageCount * 10,
            "Producer should be slowed down by backpressure")
        
        // When producer is slow, consumer waits
        assertTrue(slowProducerFastConsumer.second >= slowProducerFastConsumer.first,
            "Consumer should wait for slow producer")
        
        println("✅ Backpressure performance test passed")
    }
}

/**
 * Channel testing utilities and helpers
 */
object ChannelTestUtils {
    
    /**
     * Create a test channel with specified items and delays
     */
    fun <T> testChannel(vararg items: Pair<T, Long>): ReceiveChannel<T> {
        val channel = Channel<T>()
        GlobalScope.launch { // Using GlobalScope for utility function
            items.forEach { (item, delay) ->
                delay(delay)
                channel.send(item)
            }
            channel.close()
        }
        return channel
    }
    
    /**
     * Collect all items from a channel with timeout
     */
    suspend fun <T> ReceiveChannel<T>.collectWithTimeout(timeoutMs: Long): List<T> {
        return withTimeout(timeoutMs) {
            val results = mutableListOf<T>()
            for (item in this@collectWithTimeout) {
                results.add(item)
            }
            results
        }
    }
    
    /**
     * Assert that a channel receives expected items in order
     */
    suspend fun <T> ReceiveChannel<T>.assertReceives(vararg expectedItems: T) {
        val actualItems = mutableListOf<T>()
        for (item in this) {
            actualItems.add(item)
        }
        assertEquals(expectedItems.toList(), actualItems)
    }
    
    /**
     * Assert that a channel is closed
     */
    fun Channel<*>.assertClosed() {
        assertTrue(isClosedForSend, "Channel should be closed for send")
        assertTrue(isClosedForReceive, "Channel should be closed for receive")
    }
    
    /**
     * Create a mock channel that fails after sending N items
     */
    fun <T> failingChannel(items: List<T>, failAfter: Int): ReceiveChannel<T> {
        val channel = Channel<T>()
        GlobalScope.launch {
            items.forEachIndexed { index, item ->
                if (index == failAfter) {
                    channel.close(RuntimeException("Channel failed at item $index"))
                    return@launch
                }
                channel.send(item)
            }
            channel.close()
        }
        return channel
    }
    
    /**
     * Measure channel operation performance
     */
    suspend fun <T> measureChannelOperation(operation: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = operation()
        val endTime = System.currentTimeMillis()
        return result to (endTime - startTime)
    }
    
    /**
     * Create a rate-limited channel
     */
    fun <T> rateLimitedChannel(items: List<T>, rateMs: Long): ReceiveChannel<T> {
        val channel = Channel<T>()
        GlobalScope.launch {
            items.forEach { item ->
                delay(rateMs)
                channel.send(item)
            }
            channel.close()
        }
        return channel
    }
}

/**
 * Main demonstration function
 */
fun main() {
    println("=== Channel Testing Patterns Demo ===")
    println("This file contains comprehensive testing patterns for Kotlin Channels.")
    println("In a real project, these tests would be executed by testing frameworks.")
    println()
    println("Key Channel Testing Concepts Covered:")
    println("✅ Basic channel communication patterns")
    println("✅ Different channel types (rendezvous, buffered, unlimited, conflated)")
    println("✅ Channel closing and exception handling")
    println("✅ Producer-consumer and pipeline testing")
    println("✅ Load balancing and fan-in patterns")
    println("✅ Channel mocking and dependency injection")
    println("✅ Performance and throughput testing")
    println("✅ Backpressure and timeout scenarios")
    println()
    println("Channel Testing Best Practices:")
    println("✅ Test different channel capacities and overflow behaviors")
    println("✅ Verify proper channel closing and resource cleanup")
    println("✅ Test exception propagation through channels")
    println("✅ Mock channel dependencies for unit testing")
    println("✅ Test concurrent access and synchronization")
    println("✅ Verify performance characteristics and backpressure")
    println("✅ Test timeout and cancellation scenarios")
    println("✅ Validate producer-consumer coordination")
    println()
    println("Common Channel Testing Tools:")
    println("- runTest for controlled channel execution")
    println("- Channel factory functions for different types")
    println("- advanceTimeBy for testing timing-sensitive operations")
    println("- assertFailsWith for exception testing")
    println("- Mock implementations for channel-based services")
    println("- Performance measurement utilities")
}