/**
 * # Channel Pipeline Patterns and Communication Topologies
 * 
 * ## Problem Description
 * Complex applications often require sophisticated communication patterns beyond
 * simple producer-consumer relationships. Pipeline processing, fan-in/fan-out
 * patterns, and complex network topologies enable scalable and efficient data
 * processing architectures. These patterns are essential for building high-
 * performance streaming systems and distributed processing applications.
 * 
 * ## Solution Approach
 * Pipeline patterns include:
 * - Linear pipelines for sequential data transformation
 * - Fan-out patterns for parallel processing distribution
 * - Fan-in patterns for result aggregation and merging
 * - Tree and graph topologies for complex processing networks
 * - Backpressure handling across pipeline stages
 * 
 * ## Key Learning Points
 * - Pipelines break complex processing into manageable stages
 * - Fan-out enables parallel processing and load distribution
 * - Fan-in aggregates results from multiple processing streams
 * - Buffer management is crucial for pipeline performance
 * - Error handling must consider entire pipeline health
 * 
 * ## Performance Considerations
 * - Pipeline throughput limited by slowest stage (~10-1000x improvement possible)
 * - Buffer sizing affects memory usage and latency (1KB-1MB per stage typical)
 * - Fan-out degree impacts CPU utilization and memory pressure
 * - Fan-in synchronization overhead increases with input streams
 * - Network topologies require careful resource management
 * 
 * ## Common Pitfalls
 * - Unbalanced pipeline stages causing bottlenecks
 * - Insufficient buffering leading to backpressure propagation
 * - Memory leaks in complex pipeline topologies
 * - Poor error propagation strategies
 * - Not monitoring pipeline health and performance
 * 
 * ## Real-World Applications
 * - Stream processing systems (Kafka, Pulsar)
 * - Image/video processing pipelines
 * - ETL (Extract, Transform, Load) systems
 * - Distributed computing frameworks
 * - Real-time analytics and machine learning pipelines
 * 
 * ## Related Concepts
 * - Unix pipes and filters
 * - Reactive Streams processing
 * - MapReduce and distributed computing
 * - Actor model message passing
 */

package channels.communication.pipeline

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Linear pipeline processing patterns
 * 
 * Pipeline Processing Model:
 * 
 * Input ──> [Stage1] ──> [Buffer] ──> [Stage2] ──> [Buffer] ──> [Stage3] ──> Output
 *             ↓             ↓            ↓             ↓            ↓
 *         Transform1    Decouple     Transform2    Decouple   Transform3
 * 
 * Pipeline Characteristics:
 * ├── Sequential processing stages
 * ├── Intermediate buffering between stages
 * ├── Independent stage processing rates
 * └── Backpressure propagation through pipeline
 */
class LinearPipelinePatterns {
    
    fun demonstrateBasicPipeline() = runBlocking {
        println("=== Basic Linear Pipeline ===")
        
        data class RawData(val id: Int, val value: String)
        data class ProcessedData(val id: Int, val processed: String, val timestamp: Long)
        data class EnrichedData(val id: Int, val enriched: String, val metadata: Map<String, Any>)
        data class FinalResult(val id: Int, val result: String, val processingTime: Long)
        
        println("1. Multi-stage processing pipeline:")
        
        // Pipeline channels
        val rawDataChannel = Channel<RawData>(capacity = 10)
        val processedDataChannel = Channel<ProcessedData>(capacity = 10)
        val enrichedDataChannel = Channel<EnrichedData>(capacity = 10)
        val finalResultChannel = Channel<FinalResult>(capacity = 10)
        
        // Stage 1: Data Ingestion
        val ingestionStage = launch {
            repeat(15) { i ->
                val rawData = RawData(i, "RawValue-$i")
                rawDataChannel.send(rawData)
                println("  Stage1-Ingestion: Ingested data $i")
                delay(Random.nextLong(50, 150)) // Variable ingestion rate
            }
            rawDataChannel.close()
            println("  Stage1-Ingestion: Completed")
        }
        
        // Stage 2: Data Processing
        val processingStage = launch {
            for (rawData in rawDataChannel) {
                delay(Random.nextLong(100, 200)) // Processing time
                val processedData = ProcessedData(
                    id = rawData.id,
                    processed = "PROCESSED[${rawData.value}]",
                    timestamp = System.currentTimeMillis()
                )
                processedDataChannel.send(processedData)
                println("  Stage2-Processing: Processed data ${rawData.id}")
            }
            processedDataChannel.close()
            println("  Stage2-Processing: Completed")
        }
        
        // Stage 3: Data Enrichment  
        val enrichmentStage = launch {
            for (processedData in processedDataChannel) {
                delay(Random.nextLong(80, 180)) // Enrichment time (external API calls)
                val enrichedData = EnrichedData(
                    id = processedData.id,
                    enriched = "ENRICHED[${processedData.processed}]",
                    metadata = mapOf(
                        "processedAt" to processedData.timestamp,
                        "enrichedAt" to System.currentTimeMillis(),
                        "enrichmentSource" to "ExternalAPI",
                        "version" to "1.2"
                    )
                )
                enrichedDataChannel.send(enrichedData)
                println("  Stage3-Enrichment: Enriched data ${processedData.id}")
            }
            enrichedDataChannel.close()
            println("  Stage3-Enrichment: Completed")
        }
        
        // Stage 4: Final Assembly
        val assemblyStage = launch {
            val startTime = System.currentTimeMillis()
            for (enrichedData in enrichedDataChannel) {
                delay(Random.nextLong(60, 120)) // Assembly time
                val finalResult = FinalResult(
                    id = enrichedData.id,
                    result = "FINAL[${enrichedData.enriched}]",
                    processingTime = System.currentTimeMillis() - 
                        (enrichedData.metadata["processedAt"] as Long)
                )
                finalResultChannel.send(finalResult)
                println("  Stage4-Assembly: Assembled result ${enrichedData.id} (total time: ${finalResult.processingTime}ms)")
            }
            finalResultChannel.close()
            println("  Stage4-Assembly: Completed")
        }
        
        // Result consumer
        val resultConsumer = launch {
            var totalResults = 0
            var totalProcessingTime = 0L
            
            for (result in finalResultChannel) {
                totalResults++
                totalProcessingTime += result.processingTime
                println("  Consumer: Final result ${result.id}: ${result.result}")
            }
            
            val avgProcessingTime = if (totalResults > 0) totalProcessingTime / totalResults else 0
            println("  Consumer: Pipeline completed - $totalResults results, avg processing time: ${avgProcessingTime}ms")
        }
        
        // Wait for all stages to complete
        listOf(ingestionStage, processingStage, enrichmentStage, assemblyStage, resultConsumer).forEach { it.join() }
        
        println()
        
        println("2. Pipeline with error handling:")
        
        data class ErrorProneData(val id: Int, val shouldFail: Boolean = Random.nextDouble() < 0.2)
        
        val inputChannel = Channel<ErrorProneData>(capacity = 5)
        val outputChannel = Channel<String>(capacity = 5)
        val errorChannel = Channel<Pair<ErrorProneData, Exception>>(capacity = 5)
        
        // Error-prone processing stage
        val errorProneStage = launch {
            for (data in inputChannel) {
                try {
                    if (data.shouldFail) {
                        throw RuntimeException("Processing failed for item ${data.id}")
                    }
                    
                    delay(100) // Processing time
                    outputChannel.send("Success: Processed item ${data.id}")
                    println("  ErrorStage: Successfully processed item ${data.id}")
                    
                } catch (e: Exception) {
                    errorChannel.send(data to e)
                    println("  ErrorStage: Failed to process item ${data.id}: ${e.message}")
                }
            }
            outputChannel.close()
            errorChannel.close()
            println("  ErrorStage: Completed")
        }
        
        // Success consumer
        val successConsumer = launch {
            var successCount = 0
            for (result in outputChannel) {
                successCount++
                println("  SuccessConsumer: $result")
            }
            println("  SuccessConsumer: Processed $successCount successful items")
        }
        
        // Error consumer
        val errorConsumer = launch {
            var errorCount = 0
            for ((data, error) in errorChannel) {
                errorCount++
                println("  ErrorConsumer: Error for item ${data.id} - ${error.message}")
                // Could implement retry logic, dead letter queue, etc.
            }
            println("  ErrorConsumer: Handled $errorCount errors")
        }
        
        // Generate test data
        launch {
            repeat(12) { i ->
                val data = ErrorProneData(i)
                inputChannel.send(data)
                delay(50)
            }
            inputChannel.close()
        }
        
        listOf(errorProneStage, successConsumer, errorConsumer).forEach { it.join() }
        
        println("Linear pipeline patterns completed\n")
    }
    
    fun demonstratePipelineOptimization() = runBlocking {
        println("=== Pipeline Optimization ===")
        
        println("1. Buffer size optimization:")
        
        // Test pipeline performance with different buffer sizes
        suspend fun testPipelinePerformance(bufferSize: Int): Pair<Long, Double> {
            val stage1Channel = Channel<Int>(bufferSize)
            val stage2Channel = Channel<Int>(bufferSize)
            val resultChannel = Channel<String>(bufferSize)
            
            var processedCount = 0
            
            val totalTime = measureTimeMillis {
                // Fast producer
                val producer = launch {
                    repeat(100) { i ->
                        stage1Channel.send(i)
                    }
                    stage1Channel.close()
                }
                
                // Medium-speed processor
                val processor = launch {
                    for (value in stage1Channel) {
                        delay(20) // Processing time
                        stage2Channel.send(value * 2)
                    }
                    stage2Channel.close()
                }
                
                // Slow final stage
                val finalStage = launch {
                    for (value in stage2Channel) {
                        delay(50) // Slow final processing
                        resultChannel.send("Result-$value")
                    }
                    resultChannel.close()
                }
                
                // Consumer
                val consumer = launch {
                    for (result in resultChannel) {
                        processedCount++
                    }
                }
                
                listOf(producer, processor, finalStage, consumer).forEach { it.join() }
            }
            
            val throughput = processedCount.toDouble() / (totalTime / 1000.0)
            return totalTime to throughput
        }
        
        val bufferSizes = listOf(1, 5, 10, 20, 50)
        println("  Buffer size performance analysis:")
        
        for (bufferSize in bufferSizes) {
            val (time, throughput) = testPipelinePerformance(bufferSize)
            println("    Buffer $bufferSize: ${time}ms, ${"%.1f".format(throughput)} items/sec")
        }
        
        println()
        
        println("2. Stage balancing:")
        
        // Demonstrate pipeline with unbalanced stages
        suspend fun createUnbalancedPipeline() {
            val fastStageChannel = Channel<Int>(10)
            val slowStageChannel = Channel<Int>(10)
            val resultChannel = Channel<String>(10)
            
            val startTime = System.currentTimeMillis()
            
            // Very fast stage
            val fastStage = launch {
                repeat(20) { i ->
                    delay(25) // Fast processing
                    fastStageChannel.send(i)
                    if (i % 5 == 0) {
                        val elapsed = System.currentTimeMillis() - startTime
                        println("    FastStage: Processed $i items at ${elapsed}ms")
                    }
                }
                fastStageChannel.close()
            }
            
            // Very slow stage (bottleneck)
            val slowStage = launch {
                for (value in fastStageChannel) {
                    delay(200) // Very slow processing - creates bottleneck
                    slowStageChannel.send(value)
                    val elapsed = System.currentTimeMillis() - startTime
                    println("    SlowStage: Processed $value at ${elapsed}ms (bottleneck)")
                }
                slowStageChannel.close()
            }
            
            // Fast final stage
            val finalStage = launch {
                for (value in slowStageChannel) {
                    delay(30) // Fast final processing
                    resultChannel.send("Final-$value")
                }
                resultChannel.close()
            }
            
            val consumer = launch {
                var count = 0
                for (result in resultChannel) {
                    count++
                    val elapsed = System.currentTimeMillis() - startTime
                    if (count % 3 == 0) {
                        println("    Consumer: Received $count results at ${elapsed}ms")
                    }
                }
            }
            
            listOf(fastStage, slowStage, finalStage, consumer).forEach { it.join() }
        }
        
        createUnbalancedPipeline()
        
        println("Pipeline optimization completed\n")
    }
}

/**
 * Fan-out patterns for parallel processing distribution
 */
class FanOutPatterns {
    
    fun demonstrateBasicFanOut() = runBlocking {
        println("=== Basic Fan-Out Patterns ===")
        
        println("1. Work distribution fan-out:")
        
        data class WorkItem(val id: Int, val data: String, val complexity: Int)
        
        val workChannel = Channel<WorkItem>(capacity = 20)
        val resultChannels = List(4) { Channel<String>(capacity = 10) }
        
        // Work generator
        val workGenerator = launch {
            repeat(16) { i ->
                val workItem = WorkItem(
                    id = i,
                    data = "WorkData-$i",
                    complexity = Random.nextInt(1, 4)
                )
                workChannel.send(workItem)
                println("  Generator: Created work item $i (complexity: ${workItem.complexity})")
                delay(100)
            }
            workChannel.close()
        }
        
        // Fan-out distributor
        val distributor = launch {
            var workerIndex = 0
            for (workItem in workChannel) {
                val targetWorker = workerIndex % resultChannels.size
                resultChannels[targetWorker].send("Work for Worker-$targetWorker: ${workItem.data}")
                println("  Distributor: Assigned work ${workItem.id} to Worker-$targetWorker")
                workerIndex++
            }
            resultChannels.forEach { it.close() }
        }
        
        // Workers (fan-out targets)
        val workers = resultChannels.mapIndexed { index, channel ->
            launch {
                var processedCount = 0
                for (work in channel) {
                    val processingTime = Random.nextLong(150, 400)
                    delay(processingTime)
                    processedCount++
                    println("  Worker-$index: Completed work (${processingTime}ms) - total: $processedCount")
                }
                println("  Worker-$index: Finished with $processedCount items processed")
            }
        }
        
        workGenerator.join()
        distributor.join()
        workers.forEach { it.join() }
        
        println()
        
        println("2. Load-aware fan-out:")
        
        data class LoadAwareWorker(val id: Int, val channel: Channel<String>, var currentLoad: Int, val capacity: Int)
        
        val loadAwareWorkers = listOf(
            LoadAwareWorker(1, Channel(5), 0, 3), // Worker 1: capacity 3
            LoadAwareWorker(2, Channel(5), 0, 5), // Worker 2: capacity 5
            LoadAwareWorker(3, Channel(5), 0, 2)  // Worker 3: capacity 2
        )
        
        // Load-aware distributor
        val loadDistributor = launch {
            repeat(20) { taskId ->
                val task = "LoadTask-$taskId"
                
                // Find worker with lowest load percentage
                val selectedWorker = loadAwareWorkers
                    .filter { it.currentLoad < it.capacity }
                    .minByOrNull { it.currentLoad.toDouble() / it.capacity }
                
                if (selectedWorker != null) {
                    selectedWorker.channel.send(task)
                    selectedWorker.currentLoad++
                    println("  LoadDistributor: Assigned $task to Worker-${selectedWorker.id} " +
                           "(load: ${selectedWorker.currentLoad}/${selectedWorker.capacity})")
                } else {
                    // All workers at capacity, assign to worker with highest capacity
                    val fallbackWorker = loadAwareWorkers.maxByOrNull { it.capacity }!!
                    fallbackWorker.channel.send(task)
                    println("  LoadDistributor: All workers busy, assigned $task to Worker-${fallbackWorker.id}")
                }
                
                delay(80) // Task generation rate
            }
            
            loadAwareWorkers.forEach { it.channel.close() }
        }
        
        // Load-aware workers
        val loadWorkers = loadAwareWorkers.map { worker ->
            launch {
                var completedTasks = 0
                for (task in worker.channel) {
                    val processingTime = Random.nextLong(200, 600)
                    delay(processingTime)
                    worker.currentLoad = maxOf(0, worker.currentLoad - 1)
                    completedTasks++
                    println("  Worker-${worker.id}: Completed $task (${processingTime}ms) - " +
                           "completed: $completedTasks, current load: ${worker.currentLoad}")
                }
            }
        }
        
        loadDistributor.join()
        loadWorkers.forEach { it.join() }
        
        println("Fan-out patterns completed\n")
    }
    
    fun demonstrateSpecializedFanOut() = runBlocking {
        println("=== Specialized Fan-Out Patterns ===")
        
        println("1. Type-based fan-out:")
        
        data class TypedMessage(val id: Int, val type: String, val payload: String)
        
        val incomingChannel = Channel<TypedMessage>(capacity = 15)
        val imageChannel = Channel<TypedMessage>(capacity = 10)
        val videoChannel = Channel<TypedMessage>(capacity = 10)
        val documentChannel = Channel<TypedMessage>(capacity = 10)
        val unknownChannel = Channel<TypedMessage>(capacity = 10)
        
        // Message generator
        val messageGenerator = launch {
            val messageTypes = listOf("IMAGE", "VIDEO", "DOCUMENT", "UNKNOWN")
            repeat(20) { i ->
                val messageType = messageTypes.random()
                val message = TypedMessage(i, messageType, "Payload-$i")
                incomingChannel.send(message)
                println("  Generator: Created ${message.type} message $i")
                delay(100)
            }
            incomingChannel.close()
        }
        
        // Type-based router
        val typeRouter = launch {
            for (message in incomingChannel) {
                when (message.type) {
                    "IMAGE" -> {
                        imageChannel.send(message)
                        println("  Router: Routed IMAGE message ${message.id} to image processor")
                    }
                    "VIDEO" -> {
                        videoChannel.send(message)
                        println("  Router: Routed VIDEO message ${message.id} to video processor")
                    }
                    "DOCUMENT" -> {
                        documentChannel.send(message)
                        println("  Router: Routed DOCUMENT message ${message.id} to document processor")
                    }
                    else -> {
                        unknownChannel.send(message)
                        println("  Router: Routed UNKNOWN message ${message.id} to fallback processor")
                    }
                }
            }
            
            imageChannel.close()
            videoChannel.close()
            documentChannel.close()
            unknownChannel.close()
        }
        
        // Specialized processors
        val imageProcessor = launch {
            var imageCount = 0
            for (message in imageChannel) {
                delay(Random.nextLong(200, 400)) // Image processing time
                imageCount++
                println("  🖼️ ImageProcessor: Processed image ${message.id} (total: $imageCount)")
            }
        }
        
        val videoProcessor = launch {
            var videoCount = 0
            for (message in videoChannel) {
                delay(Random.nextLong(500, 1000)) // Video processing time (longer)
                videoCount++
                println("  🎥 VideoProcessor: Processed video ${message.id} (total: $videoCount)")
            }
        }
        
        val documentProcessor = launch {
            var docCount = 0
            for (message in documentChannel) {
                delay(Random.nextLong(100, 300)) // Document processing time
                docCount++
                println("  📄 DocumentProcessor: Processed document ${message.id} (total: $docCount)")
            }
        }
        
        val unknownProcessor = launch {
            var unknownCount = 0
            for (message in unknownChannel) {
                delay(Random.nextLong(50, 150)) // Quick processing for unknown types
                unknownCount++
                println("  ❓ UnknownProcessor: Processed unknown ${message.id} (total: $unknownCount)")
            }
        }
        
        listOf(messageGenerator, typeRouter, imageProcessor, videoProcessor, documentProcessor, unknownProcessor).forEach { it.join() }
        
        println("Specialized fan-out completed\n")
    }
}

/**
 * Fan-in patterns for result aggregation and merging
 */
class FanInPatterns {
    
    fun demonstrateBasicFanIn() = runBlocking {
        println("=== Basic Fan-In Patterns ===")
        
        println("1. Simple result aggregation:")
        
        data class ProcessingResult(val workerId: Int, val result: String, val processingTime: Long)
        
        // Multiple worker result channels
        val workerResultChannels = List(4) { Channel<ProcessingResult>(capacity = 5) }
        val aggregatedResultChannel = Channel<ProcessingResult>(capacity = 20)
        
        // Worker simulators
        val workers = workerResultChannels.mapIndexed { workerId, channel ->
            launch {
                repeat(5) { taskId ->
                    val processingTime = Random.nextLong(100, 500)
                    delay(processingTime)
                    
                    val result = ProcessingResult(
                        workerId = workerId,
                        result = "Worker-$workerId-Task-$taskId",
                        processingTime = processingTime
                    )
                    
                    channel.send(result)
                    println("  Worker-$workerId: Completed task $taskId (${processingTime}ms)")
                }
                channel.close()
                println("  Worker-$workerId: All tasks completed")
            }
        }
        
        // Fan-in aggregator
        val aggregator = launch {
            var activeChannels = workerResultChannels.size
            val channelIndexes = workerResultChannels.withIndex().toList()
            
            while (activeChannels > 0) {
                // Use select to receive from any available channel
                val result = kotlinx.coroutines.selects.select<ProcessingResult?> {
                    channelIndexes.forEach { (index, channel) ->
                        channel.onReceiveCatching { channelResult ->
                            channelResult.onSuccess { result ->
                                result
                            }.onFailure {
                                // Channel closed
                                println("  Aggregator: Worker-$index channel closed")
                                activeChannels--
                                null
                            }
                        }
                    }
                }
                
                result?.let { 
                    aggregatedResultChannel.send(it)
                    println("  Aggregator: Collected result from Worker-${it.workerId}")
                }
            }
            
            aggregatedResultChannel.close()
            println("  Aggregator: All worker channels closed")
        }
        
        // Result consumer
        val resultConsumer = launch {
            var totalResults = 0
            var totalProcessingTime = 0L
            val workerStats = mutableMapOf<Int, Int>()
            
            for (result in aggregatedResultChannel) {
                totalResults++
                totalProcessingTime += result.processingTime
                workerStats[result.workerId] = workerStats.getOrDefault(result.workerId, 0) + 1
                
                println("  Consumer: Final result - ${result.result}")
            }
            
            val avgProcessingTime = if (totalResults > 0) totalProcessingTime / totalResults else 0L
            println("  Consumer: Aggregation complete - $totalResults results, avg time: ${avgProcessingTime}ms")
            println("  Consumer: Worker contributions: $workerStats")
        }
        
        workers.forEach { it.join() }
        aggregator.join()
        resultConsumer.join()
        
        println()
        
        println("2. Ordered result aggregation:")
        
        data class OrderedResult(val sequenceNumber: Int, val workerId: Int, val data: String)
        
        val orderedWorkerChannels = List(3) { Channel<OrderedResult>(capacity = 5) }
        val orderedAggregationChannel = Channel<OrderedResult>(capacity = 15)
        
        // Workers producing results out of order
        val orderedWorkers = orderedWorkerChannels.mapIndexed { workerId, channel ->
            launch {
                val sequences = (0 until 6).toMutableList()
                sequences.shuffle() // Process in random order
                
                for (seq in sequences) {
                    val processingTime = Random.nextLong(100, 400)
                    delay(processingTime)
                    
                    val result = OrderedResult(seq, workerId, "Data-$workerId-$seq")
                    channel.send(result)
                    println("  OrderedWorker-$workerId: Completed sequence $seq")
                }
                channel.close()
            }
        }
        
        // Ordering aggregator
        val orderingAggregator = launch {
            val resultBuffer = mutableListOf<OrderedResult>()
            var nextExpectedSequence = 0
            var activeChannels = orderedWorkerChannels.size
            
            while (activeChannels > 0) {
                val result = kotlinx.coroutines.selects.select<OrderedResult?> {
                    orderedWorkerChannels.forEachIndexed { index, channel ->
                        channel.onReceiveCatching { channelResult ->
                            channelResult.onSuccess { result ->
                                result
                            }.onFailure {
                                activeChannels--
                                null
                            }
                        }
                    }
                }
                
                result?.let { 
                    resultBuffer.add(it)
                    resultBuffer.sortBy { it.sequenceNumber }
                    
                    // Emit results in sequence order
                    while (resultBuffer.isNotEmpty() && resultBuffer.first().sequenceNumber == nextExpectedSequence) {
                        val orderedResult = resultBuffer.removeFirst()
                        orderedAggregationChannel.send(orderedResult)
                        println("  OrderingAggregator: Emitted sequence ${orderedResult.sequenceNumber} from Worker-${orderedResult.workerId}")
                        nextExpectedSequence++
                    }
                }
            }
            
            // Emit any remaining buffered results
            resultBuffer.sortBy { it.sequenceNumber }
            resultBuffer.forEach { orderedAggregationChannel.send(it) }
            orderedAggregationChannel.close()
        }
        
        // Ordered consumer
        val orderedConsumer = launch {
            var lastSequence = -1
            var receivedCount = 0
            
            for (result in orderedAggregationChannel) {
                receivedCount++
                if (result.sequenceNumber != lastSequence + 1) {
                    println("  ⚠️ OrderedConsumer: Out-of-order result! Expected ${lastSequence + 1}, got ${result.sequenceNumber}")
                } else {
                    println("  ✅ OrderedConsumer: In-order result ${result.sequenceNumber}: ${result.data}")
                }
                lastSequence = result.sequenceNumber
            }
            
            println("  OrderedConsumer: Received $receivedCount results in order")
        }
        
        orderedWorkers.forEach { it.join() }
        orderingAggregator.join()
        orderedConsumer.join()
        
        println("Fan-in patterns completed\n")
    }
    
    fun demonstrateAdvancedFanIn() = runBlocking {
        println("=== Advanced Fan-In Patterns ===")
        
        println("1. Weighted result merging:")
        
        data class WeightedResult(val source: String, val weight: Double, val data: String, val timestamp: Long)
        
        val sourceChannels = mapOf(
            "HighPriority" to Channel<WeightedResult>(5),
            "MediumPriority" to Channel<WeightedResult>(5),
            "LowPriority" to Channel<WeightedResult>(5)
        )
        
        val weightedMergeChannel = Channel<WeightedResult>(15)
        
        // Source generators with different priorities
        sourceChannels.forEach { (sourceName, channel) ->
            launch {
                val weight = when (sourceName) {
                    "HighPriority" -> 3.0
                    "MediumPriority" -> 2.0
                    "LowPriority" -> 1.0
                    else -> 1.0
                }
                
                repeat(6) { i ->
                    delay(Random.nextLong(100, 300))
                    val result = WeightedResult(
                        source = sourceName,
                        weight = weight,
                        data = "$sourceName-Data-$i",
                        timestamp = System.currentTimeMillis()
                    )
                    channel.send(result)
                    println("  $sourceName: Generated result $i (weight: $weight)")
                }
                channel.close()
            }
        }
        
        // Weighted merger
        val weightedMerger = launch {
            val resultBuffer = mutableListOf<WeightedResult>()
            var activeChannels = sourceChannels.size
            val batchSize = 5 // Process in batches
            
            while (activeChannels > 0 || resultBuffer.isNotEmpty()) {
                // Collect a batch of results
                while (resultBuffer.size < batchSize && activeChannels > 0) {
                    val result = kotlinx.coroutines.selects.select<WeightedResult?> {
                        sourceChannels.forEach { (name, channel) ->
                            channel.onReceiveCatching { channelResult ->
                                channelResult.onSuccess { result ->
                                    result
                                }.onFailure {
                                    activeChannels--
                                    null
                                }
                            }
                        }
                        
                        // Timeout to process partial batches
                        onTimeout(200) { null }
                    }
                    
                    result?.let { resultBuffer.add(it) }
                }
                
                // Sort by weight (descending) and emit
                if (resultBuffer.isNotEmpty()) {
                    resultBuffer.sortByDescending { it.weight }
                    val toEmit = resultBuffer.take(minOf(3, resultBuffer.size)) // Take top 3 by weight
                    toEmit.forEach { 
                        weightedMergeChannel.send(it)
                        println("  WeightedMerger: Emitted ${it.data} (weight: ${it.weight})")
                    }
                    toEmit.forEach { resultBuffer.remove(it) }
                }
            }
            
            weightedMergeChannel.close()
        }
        
        // Weighted consumer
        val weightedConsumer = launch {
            var totalWeight = 0.0
            var resultCount = 0
            
            for (result in weightedMergeChannel) {
                resultCount++
                totalWeight += result.weight
                println("  WeightedConsumer: Processed ${result.data} from ${result.source}")
            }
            
            val avgWeight = if (resultCount > 0) totalWeight / resultCount else 0.0
            println("  WeightedConsumer: Completed - $resultCount results, avg weight: ${"%.1f".format(avgWeight)}")
        }
        
        weightedMerger.join()
        weightedConsumer.join()
        
        println("Advanced fan-in patterns completed\n")
    }
}

/**
 * Complex network topologies and patterns
 */
class NetworkTopologies {
    
    fun demonstrateTreeTopology() = runBlocking {
        println("=== Tree Processing Topology ===")
        
        data class TreeNode(val id: String, val level: Int, val data: String)
        
        println("1. Hierarchical processing tree:")
        
        // Tree structure: Root -> Level1 (3 nodes) -> Level2 (6 nodes) -> Leaf processing
        val rootChannel = Channel<TreeNode>(capacity = 10)
        val level1Channels = List(3) { Channel<TreeNode>(capacity = 5) }
        val level2Channels = List(6) { Channel<TreeNode>(capacity = 5) }
        val leafResultChannel = Channel<String>(capacity = 20)
        
        // Root node generator
        val rootGenerator = launch {
            repeat(12) { i ->
                val node = TreeNode("Root-$i", 0, "RootData-$i")
                rootChannel.send(node)
                println("  Root: Generated ${node.id}")
                delay(100)
            }
            rootChannel.close()
        }
        
        // Level 1 processors (fan-out from root)
        val level1Processors = level1Channels.mapIndexed { index, channel ->
            launch {
                var nodeCount = 0
                for (rootNode in rootChannel) {
                    if (nodeCount % level1Channels.size == index) {
                        // Transform and split into 2 level2 nodes
                        repeat(2) { subIndex ->
                            val level1Node = TreeNode(
                                id = "L1-$index-${nodeCount}-$subIndex",
                                level = 1,
                                data = "L1[${rootNode.data}]-$subIndex"
                            )
                            val targetLevel2 = (index * 2 + subIndex) % level2Channels.size
                            level2Channels[targetLevel2].send(level1Node)
                            println("  Level1-$index: Processed ${rootNode.id} -> ${level1Node.id} -> Level2-$targetLevel2")
                        }
                    }
                    nodeCount++
                }
            }
        }
        
        // Wait for level 1 to complete, then close level 2 channels
        launch {
            level1Processors.forEach { it.join() }
            level2Channels.forEach { it.close() }
        }
        
        // Level 2 processors (leaf processing)
        val level2Processors = level2Channels.mapIndexed { index, channel ->
            launch {
                for (level1Node in channel) {
                    delay(Random.nextLong(50, 200)) // Leaf processing time
                    val result = "LEAF[${level1Node.data}]"
                    leafResultChannel.send(result)
                    println("  Level2-$index: Leaf processed ${level1Node.id} -> $result")
                }
            }
        }
        
        // Result aggregator
        val resultAggregator = launch {
            level2Processors.forEach { it.join() }
            leafResultChannel.close()
            
            var totalResults = 0
            for (result in leafResultChannel) {
                totalResults++
                println("  Aggregator: Final result $totalResults: $result")
            }
            println("  Aggregator: Tree processing completed - $totalResults results")
        }
        
        rootGenerator.join()
        resultAggregator.join()
        
        println()
        
        println("2. Diamond topology (split-merge):")
        
        data class DiamondData(val id: Int, val content: String)
        data class ProcessedBranch(val id: Int, val branch: String, val result: String)
        
        val diamondInputChannel = Channel<DiamondData>(capacity = 5)
        val branchAChannel = Channel<DiamondData>(capacity = 5)
        val branchBChannel = Channel<DiamondData>(capacity = 5)
        val branchResultsChannel = Channel<ProcessedBranch>(capacity = 10)
        val finalResultChannel = Channel<String>(capacity = 5)
        
        // Input generator
        launch {
            repeat(8) { i ->
                val data = DiamondData(i, "DiamondContent-$i")
                diamondInputChannel.send(data)
                println("  DiamondInput: Generated data $i")
                delay(150)
            }
            diamondInputChannel.close()
        }
        
        // Diamond splitter
        launch {
            for (data in diamondInputChannel) {
                // Send to both branches
                branchAChannel.send(data)
                branchBChannel.send(data)
                println("  DiamondSplitter: Split data ${data.id} to both branches")
            }
            branchAChannel.close()
            branchBChannel.close()
        }
        
        // Branch A processor
        launch {
            for (data in branchAChannel) {
                delay(Random.nextLong(100, 300)) // Branch A processing
                val result = ProcessedBranch(data.id, "A", "BranchA[${data.content}]")
                branchResultsChannel.send(result)
                println("  BranchA: Processed data ${data.id}")
            }
        }
        
        // Branch B processor  
        launch {
            for (data in branchBChannel) {
                delay(Random.nextLong(150, 400)) // Branch B processing (slower)
                val result = ProcessedBranch(data.id, "B", "BranchB[${data.content}]")
                branchResultsChannel.send(result)
                println("  BranchB: Processed data ${data.id}")
            }
        }
        
        // Diamond merger (wait for both branches)
        launch {
            val branchAResults = mutableMapOf<Int, ProcessedBranch>()
            val branchBResults = mutableMapOf<Int, ProcessedBranch>()
            
            var expectedResults = 8 * 2 // 8 inputs × 2 branches
            var receivedResults = 0
            
            while (receivedResults < expectedResults) {
                val branchResult = branchResultsChannel.receive()
                receivedResults++
                
                when (branchResult.branch) {
                    "A" -> branchAResults[branchResult.id] = branchResult
                    "B" -> branchBResults[branchResult.id] = branchResult
                }
                
                // Check if we have both branches for any ID
                val commonIds = branchAResults.keys.intersect(branchBResults.keys)
                for (id in commonIds) {
                    val branchA = branchAResults.remove(id)!!
                    val branchB = branchBResults.remove(id)!!
                    
                    val mergedResult = "MERGED[${branchA.result} + ${branchB.result}]"
                    finalResultChannel.send(mergedResult)
                    println("  DiamondMerger: Merged results for data $id")
                }
            }
            
            finalResultChannel.close()
        }
        
        // Final consumer
        launch {
            var finalCount = 0
            for (result in finalResultChannel) {
                finalCount++
                println("  FinalConsumer: Diamond result $finalCount: $result")
            }
        }.join()
        
        println("Network topologies completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Linear pipeline patterns
        LinearPipelinePatterns().demonstrateBasicPipeline()
        LinearPipelinePatterns().demonstratePipelineOptimization()
        
        // Fan-out patterns
        FanOutPatterns().demonstrateBasicFanOut()
        FanOutPatterns().demonstrateSpecializedFanOut()
        
        // Fan-in patterns
        FanInPatterns().demonstrateBasicFanIn()
        FanInPatterns().demonstrateAdvancedFanIn()
        
        // Network topologies
        NetworkTopologies().demonstrateTreeTopology()
        
        println("=== Channel Pipeline Patterns Summary ===")
        println("✅ Linear Pipelines:")
        println("   - Sequential stages with intermediate buffering")
        println("   - Natural backpressure propagation through stages")
        println("   - Error handling and recovery at each stage")
        println("   - Buffer size optimization for throughput vs latency")
        println()
        println("✅ Fan-Out Patterns:")
        println("   - Work distribution across multiple processors")
        println("   - Load balancing based on capacity and availability")
        println("   - Type-based routing to specialized processors")
        println("   - Dynamic worker allocation and scaling")
        println()
        println("✅ Fan-In Patterns:")
        println("   - Result aggregation from multiple sources")
        println("   - Ordered result merging and sequencing")
        println("   - Weighted prioritization of input streams")
        println("   - Synchronization of related results")
        println()
        println("✅ Network Topologies:")
        println("   - Tree structures for hierarchical processing")
        println("   - Diamond patterns for split-process-merge workflows")
        println("   - Complex routing and coordination between stages")
        println("   - Multi-level processing with intermediate aggregation")
        println()
        println("✅ Best Practices:")
        println("   - Balance pipeline stages to avoid bottlenecks")
        println("   - Use appropriate buffer sizes for each stage")
        println("   - Implement comprehensive error handling and recovery")
        println("   - Monitor pipeline health and performance metrics")
        println("   - Design for graceful shutdown and resource cleanup")
        println()
        println("✅ Performance Considerations:")
        println("   - Pipeline throughput limited by slowest stage")
        println("   - Memory usage scales with buffer sizes and fan-out degree")
        println("   - Network topologies require careful resource management")
        println("   - Coordination overhead increases with complexity")
    }
}