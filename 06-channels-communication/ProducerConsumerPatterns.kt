/**
 * # Producer-Consumer Communication Patterns
 * 
 * ## Problem Description
 * Producer-consumer patterns are fundamental in concurrent systems where one or more
 * producers generate data and one or more consumers process that data. Channels
 * provide excellent support for implementing these patterns with proper backpressure,
 * load balancing, and resource management. These patterns are essential for building
 * scalable and efficient concurrent applications.
 * 
 * ## Solution Approach
 * Producer-consumer patterns include:
 * - Single producer, single consumer (SPSC) patterns
 * - Multiple producers, single consumer (MPSC) patterns  
 * - Single producer, multiple consumers (SPMC) patterns
 * - Multiple producers, multiple consumers (MPMC) patterns
 * - Work stealing and load balancing strategies
 * 
 * ## Key Learning Points
 * - Channels naturally provide thread-safe producer-consumer communication
 * - Different patterns have different performance and scalability characteristics
 * - Load balancing can be achieved through work distribution strategies
 * - Backpressure handling prevents memory issues in mismatched rates
 * - Error handling and recovery strategies are crucial for robustness
 * 
 * ## Performance Considerations
 * - SPSC patterns have lowest overhead (~50-100ns per operation)
 * - MPSC patterns scale well with multiple producers (~100-200ns)
 * - SPMC patterns require careful consumer coordination
 * - MPMC patterns have highest overhead but maximum flexibility
 * - Buffer size significantly impacts throughput and latency
 * 
 * ## Common Pitfalls
 * - Unbalanced producer/consumer rates leading to memory pressure
 * - Missing error handling causing silent failures
 * - Improper channel closing leading to deadlocks
 * - Race conditions in consumer coordination
 * - Not handling consumer failures gracefully
 * 
 * ## Real-World Applications
 * - Web server request processing
 * - Data processing pipelines
 * - Message queue implementations
 * - Resource pooling and connection management
 * - Load balancing in microservices
 * 
 * ## Related Concepts
 * - CSP (Communicating Sequential Processes)
 * - Actor model patterns
 * - Work stealing algorithms
 * - Load balancing strategies
 */

package channels.communication.patterns

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Single Producer, Single Consumer patterns
 * 
 * SPSC Communication Model:
 * 
 * Producer ──send()──> [Channel] ──receive()──> Consumer
 *    ↓                    ↓                        ↓
 * Generate Data      Buffer/Queue             Process Data
 * 
 * SPSC Characteristics:
 * ├── Simplest pattern ──── Lowest overhead
 * ├── Natural ordering ──── FIFO processing
 * ├── Easy backpressure ─── Producer slows when consumer can't keep up
 * └── High performance ──── Minimal contention
 */
class SingleProducerSingleConsumer {
    
    fun demonstrateBasicSPSC() = runBlocking {
        println("=== Basic Single Producer, Single Consumer ===")
        
        data class WorkItem(val id: Int, val data: String, val processingTime: Long)
        
        println("1. Simple SPSC pattern:")
        val workChannel = Channel<WorkItem>(capacity = 10)
        
        // Producer
        val producer = launch {
            repeat(10) { i ->
                val workItem = WorkItem(
                    id = i,
                    data = "Task-$i",
                    processingTime = Random.nextLong(50, 200)
                )
                println("  Producer: Created ${workItem.data}")
                workChannel.send(workItem)
                delay(100) // Simulate work item creation time
            }
            workChannel.close()
            println("  Producer: Finished creating work items")
        }
        
        // Consumer
        val consumer = launch {
            var processedCount = 0
            for (workItem in workChannel) {
                println("  Consumer: Processing ${workItem.data}")
                delay(workItem.processingTime) // Simulate processing
                processedCount++
                println("  Consumer: Completed ${workItem.data} (total: $processedCount)")
            }
            println("  Consumer: Finished processing all items")
        }
        
        producer.join()
        consumer.join()
        
        println()
        
        // SPSC with different rates
        println("2. SPSC with rate mismatch:")
        
        val fastProducerChannel = Channel<Int>(capacity = 5)
        
        // Fast producer
        val fastProducer = launch {
            repeat(20) { i ->
                fastProducerChannel.send(i)
                println("  Fast Producer: Sent $i")
                delay(50) // Fast production
            }
            fastProducerChannel.close()
        }
        
        // Slow consumer  
        val slowConsumer = launch {
            for (item in fastProducerChannel) {
                println("  Slow Consumer: Processing $item")
                delay(200) // Slow processing
                println("  Slow Consumer: Finished $item")
            }
        }
        
        fastProducer.join()
        slowConsumer.join()
        
        println()
        
        // SPSC with error handling
        println("3. SPSC with error handling:")
        
        val errorProneChannel = Channel<String>()
        
        val producerWithErrors = launch {
            try {
                repeat(8) { i ->
                    val item = "Item-$i"
                    if (i == 5) {
                        throw RuntimeException("Producer error at item $i")
                    }
                    errorProneChannel.send(item)
                    println("  Producer: Sent $item")
                    delay(100)
                }
            } catch (e: Exception) {
                println("  Producer: Error occurred - ${e.message}")
                errorProneChannel.close(e)
            } finally {
                if (!errorProneChannel.isClosedForSend) {
                    errorProneChannel.close()
                }
            }
        }
        
        val consumerWithErrorHandling = launch {
            try {
                for (item in errorProneChannel) {
                    println("  Consumer: Processing $item")
                    if (item == "Item-3") {
                        throw RuntimeException("Consumer error processing $item")
                    }
                    delay(150)
                    println("  Consumer: Completed $item")
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  Consumer: Channel closed - ${e.cause?.message}")
            } catch (e: Exception) {
                println("  Consumer: Processing error - ${e.message}")
            }
        }
        
        producerWithErrors.join()
        consumerWithErrorHandling.join()
        
        println("Single Producer Single Consumer completed\n")
    }
    
    fun demonstrateSPSCPipeline() = runBlocking {
        println("=== SPSC Pipeline Processing ===")
        
        data class RawData(val id: Int, val content: String)
        data class ProcessedData(val id: Int, val processed: String, val timestamp: Long)
        data class EnrichedData(val id: Int, val enriched: String, val metadata: Map<String, Any>)
        
        // Multi-stage SPSC pipeline
        println("1. Multi-stage SPSC pipeline:")
        
        val rawDataChannel = Channel<RawData>(10)
        val processedDataChannel = Channel<ProcessedData>(10)
        val enrichedDataChannel = Channel<EnrichedData>(10)
        
        // Stage 1: Raw data producer
        val dataProducer = launch {
            repeat(8) { i ->
                val rawData = RawData(i, "raw-content-$i")
                rawDataChannel.send(rawData)
                println("  Stage 1: Generated raw data $i")
                delay(100)
            }
            rawDataChannel.close()
        }
        
        // Stage 2: Data processor
        val dataProcessor = launch {
            for (rawData in rawDataChannel) {
                delay(50) // Processing time
                val processedData = ProcessedData(
                    id = rawData.id,
                    processed = "PROCESSED[${rawData.content}]",
                    timestamp = System.currentTimeMillis()
                )
                processedDataChannel.send(processedData)
                println("  Stage 2: Processed data ${rawData.id}")
            }
            processedDataChannel.close()
        }
        
        // Stage 3: Data enricher
        val dataEnricher = launch {
            for (processedData in processedDataChannel) {
                delay(75) // Enrichment time (external API call simulation)
                val enrichedData = EnrichedData(
                    id = processedData.id,
                    enriched = "ENRICHED[${processedData.processed}]",
                    metadata = mapOf(
                        "processedAt" to processedData.timestamp,
                        "enrichedAt" to System.currentTimeMillis(),
                        "version" to "1.0"
                    )
                )
                enrichedDataChannel.send(enrichedData)
                println("  Stage 3: Enriched data ${processedData.id}")
            }
            enrichedDataChannel.close()
        }
        
        // Final consumer
        val finalConsumer = launch {
            for (enrichedData in enrichedDataChannel) {
                delay(25) // Final processing
                println("  Stage 4: Final output ${enrichedData.id}: ${enrichedData.enriched}")
            }
        }
        
        listOf(dataProducer, dataProcessor, dataEnricher, finalConsumer).forEach { it.join() }
        
        println()
        
        // SPSC with conditional processing
        println("2. SPSC with conditional processing:")
        
        data class ConditionalTask(val id: Int, val type: String, val priority: Int)
        
        val taskChannel = Channel<ConditionalTask>()
        val highPriorityChannel = Channel<ConditionalTask>()
        val normalPriorityChannel = Channel<ConditionalTask>()
        
        // Task generator
        launch {
            repeat(12) { i ->
                val task = ConditionalTask(
                    id = i,
                    type = listOf("URGENT", "NORMAL", "BATCH").random(),
                    priority = Random.nextInt(1, 4)
                )
                taskChannel.send(task)
                delay(80)
            }
            taskChannel.close()
        }
        
        // Task router
        launch {
            for (task in taskChannel) {
                if (task.priority >= 3 || task.type == "URGENT") {
                    highPriorityChannel.send(task)
                    println("  Router: High priority task ${task.id}")
                } else {
                    normalPriorityChannel.send(task)
                    println("  Router: Normal priority task ${task.id}")
                }
            }
            highPriorityChannel.close()
            normalPriorityChannel.close()
        }
        
        // High priority processor
        val highPriorityProcessor = launch {
            for (task in highPriorityChannel) {
                delay(50) // Fast processing for high priority
                println("  ⚡ High Priority: Processed task ${task.id} (${task.type})")
            }
        }
        
        // Normal priority processor
        val normalPriorityProcessor = launch {
            for (task in normalPriorityChannel) {
                delay(150) // Slower processing for normal priority
                println("  📋 Normal Priority: Processed task ${task.id} (${task.type})")
            }
        }
        
        highPriorityProcessor.join()
        normalPriorityProcessor.join()
        
        println("SPSC Pipeline processing completed\n")
    }
}

/**
 * Multiple Producers, Single Consumer patterns
 */
class MultipleProducersSingleConsumer {
    
    fun demonstrateBasicMPSC() = runBlocking {
        println("=== Basic Multiple Producers, Single Consumer ===")
        
        data class ProducerMessage(val producerId: Int, val messageId: Int, val content: String, val timestamp: Long)
        
        println("1. Basic MPSC pattern:")
        val sharedChannel = Channel<ProducerMessage>(capacity = 20)
        
        // Multiple producers
        val producers = (1..4).map { producerId ->
            launch {
                repeat(5) { messageId ->
                    val message = ProducerMessage(
                        producerId = producerId,
                        messageId = messageId,
                        content = "Message from Producer-$producerId-$messageId",
                        timestamp = System.currentTimeMillis()
                    )
                    sharedChannel.send(message)
                    println("  Producer-$producerId: Sent message $messageId")
                    delay(Random.nextLong(50, 200))
                }
                println("  Producer-$producerId: Finished")
            }
        }
        
        // Single consumer
        val consumer = launch {
            var messageCount = 0
            val producerCounts = mutableMapOf<Int, Int>()
            
            while (messageCount < 20) { // 4 producers × 5 messages
                val message = sharedChannel.receive()
                messageCount++
                producerCounts[message.producerId] = producerCounts.getOrDefault(message.producerId, 0) + 1
                
                println("  Consumer: Received from Producer-${message.producerId}: ${message.content}")
                delay(100) // Processing time
                
                if (messageCount % 5 == 0) {
                    println("  Consumer: Processed $messageCount messages so far")
                }
            }
            
            println("  Consumer: Final distribution: $producerCounts")
        }
        
        producers.forEach { it.join() }
        sharedChannel.close()
        consumer.join()
        
        println()
        
        // MPSC with producer identification and ordering
        println("2. MPSC with ordering and fairness:")
        
        data class OrderedMessage(val producerId: Int, val sequenceNumber: Int, val data: String)
        
        val orderedChannel = Channel<OrderedMessage>(capacity = 15)
        
        // Producers with different rates
        val producerRates = mapOf(1 to 100L, 2 to 150L, 3 to 75L) // Different production rates
        
        val orderedProducers = producerRates.map { (producerId, rate) ->
            launch {
                repeat(8) { seq ->
                    val message = OrderedMessage(
                        producerId = producerId,
                        sequenceNumber = seq,
                        data = "Data-$producerId-$seq"
                    )
                    orderedChannel.send(message)
                    println("  Producer-$producerId: Sent sequence $seq")
                    delay(rate)
                }
            }
        }
        
        // Consumer tracking order
        val orderTrackingConsumer = launch {
            val lastSequence = mutableMapOf<Int, Int>()
            var totalMessages = 0
            
            while (totalMessages < 24) { // 3 producers × 8 messages
                val message = orderedChannel.receive()
                totalMessages++
                
                val expectedSeq = lastSequence.getOrDefault(message.producerId, -1) + 1
                if (message.sequenceNumber == expectedSeq) {
                    println("  Consumer: ✅ In-order message from Producer-${message.producerId}: seq ${message.sequenceNumber}")
                } else {
                    println("  Consumer: ⚠️ Out-of-order message from Producer-${message.producerId}: expected $expectedSeq, got ${message.sequenceNumber}")
                }
                
                lastSequence[message.producerId] = maxOf(
                    lastSequence.getOrDefault(message.producerId, -1),
                    message.sequenceNumber
                )
                
                delay(120) // Processing time
            }
        }
        
        orderedProducers.forEach { it.join() }
        orderedChannel.close()
        orderTrackingConsumer.join()
        
        println("Multiple Producers Single Consumer completed\n")
    }
    
    fun demonstrateMPSCLoadBalancing() = runBlocking {
        println("=== MPSC Load Balancing and Prioritization ===")
        
        data class Task(
            val id: String,
            val priority: Int,
            val processingTime: Long,
            val producerId: Int
        )
        
        println("1. MPSC with priority handling:")
        
        val priorityChannel = Channel<Task>(capacity = 30)
        
        // Producers generating tasks with different priorities
        val taskProducers = (1..3).map { producerId ->
            launch {
                repeat(8) { taskIndex ->
                    val task = Task(
                        id = "P$producerId-T$taskIndex",
                        priority = Random.nextInt(1, 6), // Priority 1-5
                        processingTime = Random.nextLong(50, 200),
                        producerId = producerId
                    )
                    priorityChannel.send(task)
                    println("  Producer-$producerId: Created task ${task.id} (priority ${task.priority})")
                    delay(Random.nextLong(80, 150))
                }
            }
        }
        
        // Consumer with priority-aware processing
        val priorityConsumer = launch {
            val taskBuffer = mutableListOf<Task>()
            var processedCount = 0
            
            // Collect tasks in batches for priority sorting
            launch {
                for (task in priorityChannel) {
                    taskBuffer.add(task)
                }
            }
            
            // Process tasks in priority order
            while (processedCount < 24 || taskBuffer.isNotEmpty()) {
                if (taskBuffer.isNotEmpty()) {
                    // Sort by priority (higher number = higher priority)
                    taskBuffer.sortByDescending { it.priority }
                    val task = taskBuffer.removeFirst()
                    
                    println("  Consumer: Processing ${task.id} (priority ${task.priority})")
                    delay(task.processingTime)
                    processedCount++
                    println("  Consumer: ✅ Completed ${task.id} (total: $processedCount)")
                } else {
                    delay(10) // Wait for more tasks
                }
            }
        }
        
        taskProducers.forEach { it.join() }
        priorityChannel.close()
        priorityConsumer.join()
        
        println()
        
        // MPSC with rate limiting
        println("2. MPSC with rate limiting:")
        
        val rateLimitedChannel = Channel<String>(capacity = 10)
        
        // High-rate producers
        val highRateProducers = (1..5).map { producerId ->
            launch {
                repeat(6) { messageId ->
                    val message = "HighRate-P$producerId-M$messageId"
                    rateLimitedChannel.send(message)
                    println("  Producer-$producerId: Sent $message")
                    delay(30) // Very fast production
                }
            }
        }
        
        // Rate-limited consumer
        val rateLimitedConsumer = launch {
            var lastProcessTime = System.currentTimeMillis()
            val minProcessingInterval = 100L // Minimum time between processing
            var messageCount = 0
            
            for (message in rateLimitedChannel) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastProcess = currentTime - lastProcessTime
                
                if (timeSinceLastProcess < minProcessingInterval) {
                    val waitTime = minProcessingInterval - timeSinceLastProcess
                    println("  Consumer: Rate limiting - waiting ${waitTime}ms")
                    delay(waitTime)
                }
                
                println("  Consumer: Processing $message")
                messageCount++
                lastProcessTime = System.currentTimeMillis()
                
                if (messageCount % 10 == 0) {
                    println("  Consumer: Rate-limited processing - $messageCount messages processed")
                }
            }
        }
        
        highRateProducers.forEach { it.join() }
        rateLimitedChannel.close()
        rateLimitedConsumer.join()
        
        println("MPSC Load balancing completed\n")
    }
}

/**
 * Single Producer, Multiple Consumers patterns
 */
class SingleProducerMultipleConsumers {
    
    fun demonstrateBasicSPMC() = runBlocking {
        println("=== Basic Single Producer, Multiple Consumers ===")
        
        data class WorkUnit(val id: Int, val data: String, val complexity: Int)
        
        println("1. Basic SPMC work distribution:")
        val workChannel = Channel<WorkUnit>(capacity = 15)
        
        // Single producer
        val producer = launch {
            repeat(20) { i ->
                val workUnit = WorkUnit(
                    id = i,
                    data = "WorkData-$i",
                    complexity = Random.nextInt(1, 4) // 1=simple, 3=complex
                )
                workChannel.send(workUnit)
                println("  Producer: Created work unit $i (complexity ${workUnit.complexity})")
                delay(100)
            }
            workChannel.close()
            println("  Producer: Finished creating work")
        }
        
        // Multiple consumers
        val consumers = (1..3).map { consumerId ->
            launch {
                var processedCount = 0
                try {
                    while (true) {
                        val workUnit = workChannel.receive()
                        processedCount++
                        
                        val processingTime = workUnit.complexity * 100L // More complex = longer processing
                        println("  Consumer-$consumerId: Processing work ${workUnit.id}")
                        delay(processingTime)
                        println("  Consumer-$consumerId: ✅ Completed work ${workUnit.id} (total: $processedCount)")
                    }
                } catch (e: ClosedReceiveChannelException) {
                    println("  Consumer-$consumerId: Finished - processed $processedCount work units")
                }
            }
        }
        
        producer.join()
        consumers.forEach { it.join() }
        
        println()
        
        // SPMC with consumer specialization
        println("2. SPMC with specialized consumers:")
        
        data class SpecializedTask(
            val id: Int,
            val type: String,
            val payload: String
        )
        
        val taskChannel = Channel<SpecializedTask>(capacity = 20)
        
        // Producer generating mixed task types
        val mixedTaskProducer = launch {
            val taskTypes = listOf("CPU_INTENSIVE", "IO_BOUND", "MEMORY_HEAVY", "NETWORK_CALL")
            
            repeat(16) { i ->
                val task = SpecializedTask(
                    id = i,
                    type = taskTypes.random(),
                    payload = "TaskPayload-$i"
                )
                taskChannel.send(task)
                println("  Producer: Created ${task.type} task $i")
                delay(150)
            }
            taskChannel.close()
        }
        
        // Specialized consumers
        val cpuConsumer = launch {
            var cpuTasks = 0
            try {
                while (true) {
                    val task = taskChannel.receive()
                    if (task.type == "CPU_INTENSIVE") {
                        cpuTasks++
                        println("  🖥️ CPU Consumer: Processing task ${task.id}")
                        delay(300) // CPU intensive work
                        println("  🖥️ CPU Consumer: Completed task ${task.id}")
                    } else {
                        // Put back for other consumers
                        taskChannel.trySend(task)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  🖥️ CPU Consumer: Processed $cpuTasks CPU tasks")
            }
        }
        
        val ioConsumer = launch {
            var ioTasks = 0
            try {
                while (true) {
                    val task = taskChannel.receive()
                    if (task.type == "IO_BOUND" || task.type == "NETWORK_CALL") {
                        ioTasks++
                        println("  💾 I/O Consumer: Processing task ${task.id}")
                        delay(200) // I/O bound work
                        println("  💾 I/O Consumer: Completed task ${task.id}")
                    } else if (task.type != "CPU_INTENSIVE") { // Don't interfere with CPU consumer
                        // Put back for other consumers
                        taskChannel.trySend(task)
                    } else {
                        taskChannel.trySend(task)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  💾 I/O Consumer: Processed $ioTasks I/O tasks")
            }
        }
        
        val memoryConsumer = launch {
            var memoryTasks = 0
            try {
                while (true) {
                    val task = taskChannel.receive()
                    if (task.type == "MEMORY_HEAVY") {
                        memoryTasks++
                        println("  🧠 Memory Consumer: Processing task ${task.id}")
                        delay(250) // Memory intensive work
                        println("  🧠 Memory Consumer: Completed task ${task.id}")
                    } else if (task.type != "CPU_INTENSIVE" && task.type != "IO_BOUND" && task.type != "NETWORK_CALL") {
                        taskChannel.trySend(task)
                    } else {
                        taskChannel.trySend(task)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  🧠 Memory Consumer: Processed $memoryTasks memory tasks")
            }
        }
        
        mixedTaskProducer.join()
        delay(2000) // Allow consumers to finish processing
        
        // Note: This approach with trySend has limitations - better patterns shown in next examples
        
        println("Single Producer Multiple Consumers completed\n")
    }
    
    fun demonstrateSPMCLoadBalancing() = runBlocking {
        println("=== SPMC Load Balancing Strategies ===")
        
        data class BalancedTask(val id: Int, val weight: Int, val description: String)
        
        println("1. Round-robin load balancing:")
        
        // Better approach: Use multiple channels for distribution
        val numConsumers = 4
        val consumerChannels = List(numConsumers) { Channel<BalancedTask>(5) }
        
        // Producer with round-robin distribution
        val roundRobinProducer = launch {
            repeat(20) { i ->
                val task = BalancedTask(
                    id = i,
                    weight = Random.nextInt(1, 6),
                    description = "RoundRobin-Task-$i"
                )
                
                val targetConsumer = i % numConsumers
                consumerChannels[targetConsumer].send(task)
                println("  Producer: Assigned task $i to consumer $targetConsumer (weight ${task.weight})")
                delay(100)
            }
            
            consumerChannels.forEach { it.close() }
        }
        
        // Consumers
        val roundRobinConsumers = consumerChannels.mapIndexed { consumerId, channel ->
            launch {
                var totalWeight = 0
                var taskCount = 0
                
                for (task in channel) {
                    println("  Consumer-$consumerId: Processing task ${task.id}")
                    delay(task.weight * 50L) // Processing time based on weight
                    totalWeight += task.weight
                    taskCount++
                    println("  Consumer-$consumerId: ✅ Completed task ${task.id}")
                }
                
                println("  Consumer-$consumerId: Final stats - $taskCount tasks, total weight: $totalWeight")
            }
        }
        
        roundRobinProducer.join()
        roundRobinConsumers.forEach { it.join() }
        
        println()
        
        println("2. Weighted load balancing:")
        
        // Consumers with different capabilities
        data class ConsumerCapability(val id: Int, val capacity: Int, val channel: Channel<BalancedTask>)
        
        val capableConsumers = listOf(
            ConsumerCapability(1, 3, Channel(3)), // High capacity
            ConsumerCapability(2, 2, Channel(2)), // Medium capacity
            ConsumerCapability(3, 1, Channel(1))  // Low capacity
        )
        
        // Producer with weighted distribution
        val weightedProducer = launch {
            repeat(18) { i ->
                val task = BalancedTask(
                    id = i,
                    weight = Random.nextInt(1, 4),
                    description = "Weighted-Task-$i"
                )
                
                // Select consumer based on capacity and current load
                val selectedConsumer = capableConsumers.maxByOrNull { consumer ->
                    consumer.capacity - (consumer.channel.trySend(task).isSuccess.let { if (it) 1 else 0 })
                }
                
                if (selectedConsumer != null) {
                    val sent = selectedConsumer.channel.trySend(task).isSuccess
                    if (sent) {
                        println("  Producer: Assigned task $i to capable consumer ${selectedConsumer.id}")
                    } else {
                        // Fallback to any available consumer
                        val fallbackConsumer = capableConsumers.find { it.channel.trySend(task).isSuccess }
                        if (fallbackConsumer != null) {
                            println("  Producer: Fallback assignment task $i to consumer ${fallbackConsumer.id}")
                        }
                    }
                }
                
                delay(120)
            }
            
            capableConsumers.forEach { it.channel.close() }
        }
        
        // Capable consumers
        val capabilityConsumers = capableConsumers.map { consumerCap ->
            launch {
                var processedTasks = 0
                var totalProcessingTime = 0L
                
                for (task in consumerCap.channel) {
                    val startTime = System.currentTimeMillis()
                    println("  Consumer-${consumerCap.id}: Processing task ${task.id}")
                    
                    val processingTime = task.weight * (100L / consumerCap.capacity) // Higher capacity = faster processing
                    delay(processingTime)
                    
                    processedTasks++
                    totalProcessingTime += processingTime
                    println("  Consumer-${consumerCap.id}: ✅ Completed task ${task.id}")
                }
                
                println("  Consumer-${consumerCap.id}: Capacity ${consumerCap.capacity} - processed $processedTasks tasks in ${totalProcessingTime}ms")
            }
        }
        
        weightedProducer.join()
        capabilityConsumers.forEach { it.join() }
        
        println("SPMC Load balancing completed\n")
    }
}

/**
 * Multiple Producers, Multiple Consumers patterns
 */
class MultipleProducersMultipleConsumers {
    
    fun demonstrateBasicMPMC() = runBlocking {
        println("=== Basic Multiple Producers, Multiple Consumers ===")
        
        data class MPMCMessage(
            val producerId: Int,
            val messageId: Int,
            val content: String,
            val priority: Int,
            val timestamp: Long = System.currentTimeMillis()
        )
        
        println("1. Basic MPMC pattern:")
        val sharedChannel = Channel<MPMCMessage>(capacity = 25)
        
        // Multiple producers
        val producers = (1..3).map { producerId ->
            launch {
                repeat(8) { messageId ->
                    val message = MPMCMessage(
                        producerId = producerId,
                        messageId = messageId,
                        content = "Content from P$producerId-M$messageId",
                        priority = Random.nextInt(1, 4)
                    )
                    sharedChannel.send(message)
                    println("  Producer-$producerId: Sent message $messageId (priority ${message.priority})")
                    delay(Random.nextLong(100, 300))
                }
            }
        }
        
        // Multiple consumers
        val consumers = (1..4).map { consumerId ->
            launch {
                var processedCount = 0
                var totalPriority = 0
                
                try {
                    while (processedCount < 6) { // Each consumer processes roughly 6 messages (24 total / 4 consumers)
                        val message = sharedChannel.receive()
                        processedCount++
                        totalPriority += message.priority
                        
                        println("  Consumer-$consumerId: Processing message from Producer-${message.producerId}")
                        delay(message.priority * 75L) // Higher priority = more processing time
                        println("  Consumer-$consumerId: ✅ Completed message ${message.messageId} from P${message.producerId}")
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Channel closed
                }
                
                val avgPriority = if (processedCount > 0) totalPriority.toDouble() / processedCount else 0.0
                println("  Consumer-$consumerId: Finished - processed $processedCount messages, avg priority: ${"%.1f".format(avgPriority)}")
            }
        }
        
        producers.forEach { it.join() }
        sharedChannel.close()
        consumers.forEach { it.join() }
        
        println()
        
        // MPMC with work stealing
        println("2. MPMC with work stealing:")
        
        data class WorkStealingTask(val id: String, val complexity: Int, val producerId: Int)
        
        val workStealingChannel = Channel<WorkStealingTask>(capacity = 20)
        val completedTasks = Channel<String>(capacity = 50)
        
        // Multiple producers with different production rates
        val workStealingProducers = listOf(
            Triple(1, 8, 150L), // Producer 1: 8 tasks, 150ms interval
            Triple(2, 6, 200L), // Producer 2: 6 tasks, 200ms interval  
            Triple(3, 10, 100L) // Producer 3: 10 tasks, 100ms interval
        ).map { (producerId, taskCount, interval) ->
            launch {
                repeat(taskCount) { taskIndex ->
                    val task = WorkStealingTask(
                        id = "P$producerId-T$taskIndex",
                        complexity = Random.nextInt(1, 5),
                        producerId = producerId
                    )
                    workStealingChannel.send(task)
                    println("  Producer-$producerId: Created task ${task.id} (complexity ${task.complexity})")
                    delay(interval)
                }
            }
        }
        
        // Work-stealing consumers
        val workStealingConsumers = (1..3).map { consumerId ->
            launch {
                var tasksProcessed = 0
                var totalComplexity = 0
                
                try {
                    while (true) {
                        val task = workStealingChannel.receive()
                        tasksProcessed++
                        totalComplexity += task.complexity
                        
                        println("  Consumer-$consumerId: Stole task ${task.id}")
                        delay(task.complexity * 80L) // Processing time based on complexity
                        
                        val completionMsg = "Consumer-$consumerId completed ${task.id}"
                        completedTasks.send(completionMsg)
                        println("  Consumer-$consumerId: ✅ Finished ${task.id}")
                    }
                } catch (e: ClosedReceiveChannelException) {
                    println("  Consumer-$consumerId: Work stealing finished - $tasksProcessed tasks, total complexity: $totalComplexity")
                }
            }
        }
        
        // Completion tracker
        val completionTracker = launch {
            var completedCount = 0
            val expectedTotal = 8 + 6 + 10 // Total tasks from all producers
            
            try {
                while (completedCount < expectedTotal) {
                    val completion = completedTasks.receive()
                    completedCount++
                    if (completedCount % 5 == 0) {
                        println("  Tracker: $completedCount/$expectedTotal tasks completed")
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("  Tracker: All tasks completed")
            }
        }
        
        workStealingProducers.forEach { it.join() }
        workStealingChannel.close()
        workStealingConsumers.forEach { it.join() }
        completedTasks.close()
        completionTracker.join()
        
        println("Multiple Producers Multiple Consumers completed\n")
    }
}

/**
 * Advanced producer-consumer patterns
 */
class AdvancedProducerConsumerPatterns {
    
    fun demonstrateAdaptivePatterns() = runBlocking {
        println("=== Adaptive Producer-Consumer Patterns ===")
        
        data class AdaptiveTask(val id: Int, val load: Double, val timestamp: Long = System.currentTimeMillis())
        
        println("1. Adaptive rate control:")
        
        val adaptiveChannel = Channel<AdaptiveTask>(capacity = 15)
        
        // Adaptive producer that adjusts rate based on consumer feedback
        class AdaptiveProducer {
            private var productionRate = 200L // Initial rate
            private var lastFeedback = System.currentTimeMillis()
            
            suspend fun adjustRate(consumerFeedback: String) {
                when (consumerFeedback) {
                    "FAST" -> {
                        productionRate = maxOf(50L, productionRate - 25L)
                        println("  Producer: Increasing rate (${productionRate}ms interval)")
                    }
                    "SLOW" -> {
                        productionRate = minOf(500L, productionRate + 50L)  
                        println("  Producer: Decreasing rate (${productionRate}ms interval)")
                    }
                    "NORMAL" -> {
                        // Gradually return to normal rate
                        productionRate = (productionRate + 150L) / 2
                        println("  Producer: Normalizing rate (${productionRate}ms interval)")
                    }
                }
                lastFeedback = System.currentTimeMillis()
            }
            
            suspend fun produce(taskId: Int): AdaptiveTask {
                delay(productionRate)
                return AdaptiveTask(taskId, Random.nextDouble(0.1, 1.0))
            }
        }
        
        val adaptiveProducer = AdaptiveProducer()
        val feedbackChannel = Channel<String>(capacity = 10)
        
        // Producer with feedback adaptation
        val producerJob = launch {
            // Producer feedback listener
            launch {
                for (feedback in feedbackChannel) {
                    adaptiveProducer.adjustRate(feedback)
                }
            }
            
            repeat(20) { taskId ->
                val task = adaptiveProducer.produce(taskId)
                adaptiveChannel.send(task)
                println("  Producer: Created adaptive task $taskId (load: ${"%.2f".format(task.load)})")
            }
            adaptiveChannel.close()
        }
        
        // Consumer providing feedback
        val consumerJob = launch {
            var processingTimes = mutableListOf<Long>()
            
            for (task in adaptiveChannel) {
                val startTime = System.currentTimeMillis()
                
                // Processing time based on task load
                val processingTime = (task.load * 300).toLong()
                delay(processingTime)
                
                val actualTime = System.currentTimeMillis() - startTime
                processingTimes.add(actualTime)
                
                println("  Consumer: Processed task ${task.id} in ${actualTime}ms")
                
                // Provide feedback every 5 tasks
                if (processingTimes.size >= 5) {
                    val avgTime = processingTimes.average()
                    val feedback = when {
                        avgTime < 150 -> "FAST"
                        avgTime > 350 -> "SLOW" 
                        else -> "NORMAL"
                    }
                    
                    feedbackChannel.trySend(feedback)
                    processingTimes.clear()
                }
            }
            
            feedbackChannel.close()
        }
        
        producerJob.join()
        consumerJob.join()
        
        println()
        
        // Circuit breaker pattern
        println("2. Circuit breaker producer-consumer:")
        
        data class CircuitTask(val id: Int, val shouldFail: Boolean = Random.nextDouble() < 0.3)
        
        enum class CircuitState { CLOSED, OPEN, HALF_OPEN }
        
        class CircuitBreakerConsumer {
            private var state = CircuitState.CLOSED
            private var failures = 0
            private var lastFailureTime = 0L
            private val failureThreshold = 3
            private val recoveryTime = 2000L
            
            suspend fun process(task: CircuitTask): Boolean {
                val currentTime = System.currentTimeMillis()
                
                when (state) {
                    CircuitState.CLOSED -> {
                        return try {
                            if (task.shouldFail) {
                                throw RuntimeException("Task processing failed")
                            }
                            delay(100) // Normal processing
                            failures = 0 // Reset on success
                            true
                        } catch (e: Exception) {
                            failures++
                            lastFailureTime = currentTime
                            if (failures >= failureThreshold) {
                                state = CircuitState.OPEN
                                println("    🔴 Circuit breaker OPENED")
                            }
                            false
                        }
                    }
                    
                    CircuitState.OPEN -> {
                        if (currentTime - lastFailureTime >= recoveryTime) {
                            state = CircuitState.HALF_OPEN
                            println("    🟡 Circuit breaker HALF-OPEN")
                            return process(task) // Retry
                        }
                        return false // Reject immediately
                    }
                    
                    CircuitState.HALF_OPEN -> {
                        return try {
                            if (task.shouldFail) {
                                throw RuntimeException("Half-open test failed")
                            }
                            delay(100)
                            state = CircuitState.CLOSED
                            failures = 0
                            println("    🟢 Circuit breaker CLOSED")
                            true
                        } catch (e: Exception) {
                            state = CircuitState.OPEN
                            lastFailureTime = currentTime
                            println("    🔴 Circuit breaker OPENED again")
                            false
                        }
                    }
                }
            }
        }
        
        val circuitChannel = Channel<CircuitTask>(capacity = 10)
        val circuitConsumer = CircuitBreakerConsumer()
        
        // Producer
        launch {
            repeat(15) { taskId ->
                val task = CircuitTask(taskId)
                circuitChannel.send(task)
                println("  Producer: Sent circuit task $taskId")
                delay(200)
            }
            circuitChannel.close()
        }
        
        // Consumer with circuit breaker
        launch {
            var successCount = 0
            var failureCount = 0
            
            for (task in circuitChannel) {
                val success = circuitConsumer.process(task)
                if (success) {
                    successCount++
                    println("  Consumer: ✅ Successfully processed task ${task.id}")
                } else {
                    failureCount++
                    println("  Consumer: ❌ Failed to process task ${task.id}")
                }
            }
            
            println("  Consumer: Final stats - Success: $successCount, Failures: $failureCount")
        }.join()
        
        println("Advanced producer-consumer patterns completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Single Producer, Single Consumer
        SingleProducerSingleConsumer().demonstrateBasicSPSC()
        SingleProducerSingleConsumer().demonstrateSPSCPipeline()
        
        // Multiple Producers, Single Consumer
        MultipleProducersSingleConsumer().demonstrateBasicMPSC()
        MultipleProducersSingleConsumer().demonstrateMPSCLoadBalancing()
        
        // Single Producer, Multiple Consumers
        SingleProducerMultipleConsumers().demonstrateBasicSPMC()
        SingleProducerMultipleConsumers().demonstrateSPMCLoadBalancing()
        
        // Multiple Producers, Multiple Consumers
        MultipleProducersMultipleConsumers().demonstrateBasicMPMC()
        
        // Advanced patterns
        AdvancedProducerConsumerPatterns().demonstrateAdaptivePatterns()
        
        println("=== Producer-Consumer Patterns Summary ===")
        println("✅ SPSC (Single Producer, Single Consumer):")
        println("   - Simplest pattern with lowest overhead")
        println("   - Natural ordering and backpressure")
        println("   - Perfect for pipeline processing")
        println()
        println("✅ MPSC (Multiple Producers, Single Consumer):")
        println("   - Excellent for aggregating data from multiple sources")
        println("   - Natural load balancing across producers")
        println("   - Single point of processing for consistency")
        println()
        println("✅ SPMC (Single Producer, Multiple Consumers):")
        println("   - Great for distributing work across multiple workers")
        println("   - Requires careful load balancing strategies")
        println("   - Work stealing prevents idle consumers")
        println()
        println("✅ MPMC (Multiple Producers, Multiple Consumers):")
        println("   - Maximum flexibility but highest complexity")
        println("   - Requires coordination between all parties")
        println("   - Best for high-throughput systems")
        println()
        println("✅ Advanced Patterns:")
        println("   - Adaptive rate control based on consumer feedback")
        println("   - Circuit breaker for resilient processing")
        println("   - Priority-based processing and routing")
        println("   - Load balancing with consumer capabilities")
        println()
        println("✅ Best Practices:")
        println("   - Choose pattern based on your specific use case")
        println("   - Implement proper error handling and recovery")
        println("   - Monitor and adapt to changing conditions")
        println("   - Use appropriate buffer sizes for your workload")
    }
}