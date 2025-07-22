/**
 * # Select Expressions and Advanced Channel Multiplexing
 * 
 * ## Problem Description
 * When working with multiple channels simultaneously, applications need to handle
 * events from any of several channels as they become available. Select expressions
 * provide a powerful mechanism for multiplexing channel operations, allowing
 * coroutines to wait for the first available operation among multiple channels.
 * This enables sophisticated coordination patterns and responsive system designs.
 * 
 * ## Solution Approach  
 * Select expressions enable:
 * - Non-blocking channel operations with alternatives
 * - Timeout handling in channel operations
 * - Priority-based channel selection
 * - Complex coordination patterns between multiple channels
 * - Adaptive channel switching based on availability
 * 
 * ## Key Learning Points
 * - Select expressions choose the first available channel operation
 * - onReceive, onSend, and onTimeout clauses provide different selection options
 * - Select is non-blocking and returns immediately when any clause is ready
 * - Default clauses provide fallback behavior when no channels are ready
 * - Select expressions enable sophisticated multiplexing patterns
 * 
 * ## Performance Considerations
 * - Select overhead: ~200-500ns per selection operation
 * - Multiple channel monitoring adds minimal overhead
 * - Early termination when first operation becomes available
 * - Memory usage scales with number of channels being monitored
 * - More efficient than polling multiple channels manually
 * 
 * ## Common Pitfalls
 * - Not handling all possible selection outcomes
 * - Creating infinite loops without proper termination conditions
 * - Forgetting timeout clauses in long-running selections
 * - Race conditions when channels are closed during selection
 * - Complex select logic that's hard to reason about
 * 
 * ## Real-World Applications
 * - Load balancers distributing requests across multiple backends
 * - Event multiplexers handling input from multiple sources
 * - Timeout handling in network operations
 * - Game engines processing input from multiple sources
 * - Monitoring systems collecting data from multiple sensors
 * 
 * ## Related Concepts
 * - Go select statements
 * - Erlang selective receive
 * - Reactive streams multiplexing
 * - Event loop pattern implementations
 */

package channels.communication.select

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic select expressions and channel multiplexing
 * 
 * Select Expression Model:
 * 
 * select {
 *   channel1.onReceive { ... }  ──┐
 *   channel2.onReceive { ... }  ──┼──> First Available ──> Execute Block
 *   channel3.onSend(value) { }  ──┘
 *   onTimeout(1000) { ... }
 *   default { ... }
 * }
 * 
 * Selection Process:
 * ├── Check all clauses simultaneously
 * ├── Execute first available operation
 * ├── Run corresponding block
 * └── Return result
 */
class BasicSelectExpressions {
    
    fun demonstrateBasicSelect() = runBlocking {
        println("=== Basic Select Expressions ===")
        
        println("1. Simple channel selection:")
        
        val channel1 = Channel<String>()
        val channel2 = Channel<String>()
        
        // Producers with different timing
        launch {
            delay(300)
            channel1.send("Message from Channel 1")
        }
        
        launch {
            delay(100)
            channel2.send("Message from Channel 2")
        }
        
        // Consumer using select
        val result = select<String> {
            channel1.onReceive { value ->
                "Received from channel1: $value"
            }
            channel2.onReceive { value ->
                "Received from channel2: $value"
            }
        }
        
        println("  Select result: $result")
        
        channel1.close()
        channel2.close()
        
        println()
        
        println("2. Select with timeout:")
        
        val slowChannel = Channel<Int>()
        
        // Slow producer
        launch {
            delay(2000) // Will timeout before this
            slowChannel.send(42)
        }
        
        val timeoutResult = select<String> {
            slowChannel.onReceive { value ->
                "Received value: $value"
            }
            onTimeout(500) {
                "Operation timed out after 500ms"
            }
        }
        
        println("  Timeout result: $timeoutResult")
        slowChannel.close()
        
        println()
        
        println("3. Select with default clause:")
        
        val emptyChannel = Channel<String>()
        
        val defaultResult = select<String> {
            emptyChannel.onReceive { value ->
                "Received: $value"
            }
            default {
                "No channels ready - using default"
            }
        }
        
        println("  Default result: $defaultResult")
        emptyChannel.close()
        
        println()
        
        println("4. Select with send operations:")
        
        val sendChannel1 = Channel<String>(1) // Small buffer
        val sendChannel2 = Channel<String>(1) // Small buffer
        
        // Fill one channel
        sendChannel1.trySend("Already buffered")
        
        val sendResult = select<String> {
            sendChannel1.onSend("Message 1") {
                "Successfully sent to channel1"
            }
            sendChannel2.onSend("Message 2") {
                "Successfully sent to channel2"
            }
            onTimeout(100) {
                "Send operations timed out"
            }
        }
        
        println("  Send select result: $sendResult")
        
        sendChannel1.close()
        sendChannel2.close()
        
        println("Basic select expressions completed\n")
    }
    
    fun demonstrateSelectLoops() = runBlocking {
        println("=== Select in Loops ===")
        
        println("1. Event processing loop:")
        
        val eventChannel = Channel<String>()
        val commandChannel = Channel<String>()
        val shutdownChannel = Channel<Unit>()
        
        // Event producers
        launch {
            repeat(5) { i ->
                delay(200)
                eventChannel.send("Event-$i")
            }
            eventChannel.close()
        }
        
        launch {
            delay(800)
            commandChannel.send("PROCESS_ALL")
            delay(300)
            commandChannel.send("STATUS")
            commandChannel.close()
        }
        
        launch {
            delay(1500)
            shutdownChannel.send(Unit)
        }
        
        // Event processor using select loop
        val eventProcessor = launch {
            var eventCount = 0
            var running = true
            
            while (running) {
                select<Unit> {
                    eventChannel.onReceiveCatching { result ->
                        result.onSuccess { event ->
                            eventCount++
                            println("  Processor: Handled $event (total: $eventCount)")
                        }.onFailure {
                            println("  Processor: Event channel closed")
                        }
                    }
                    
                    commandChannel.onReceiveCatching { result ->
                        result.onSuccess { command ->
                            when (command) {
                                "PROCESS_ALL" -> println("  Processor: Processing all pending events")
                                "STATUS" -> println("  Processor: Status - processed $eventCount events")
                                else -> println("  Processor: Unknown command: $command")
                            }
                        }.onFailure {
                            println("  Processor: Command channel closed")
                        }
                    }
                    
                    shutdownChannel.onReceive {
                        println("  Processor: Shutdown signal received")
                        running = false
                    }
                    
                    onTimeout(2000) {
                        println("  Processor: No activity for 2 seconds, continuing...")
                    }
                }
            }
            
            println("  Processor: Event processing completed")
        }
        
        eventProcessor.join()
        shutdownChannel.close()
        
        println()
        
        println("2. Multi-source data aggregation:")
        
        data class SensorReading(val sensorId: String, val value: Double, val timestamp: Long)
        
        val temperatureChannel = Channel<SensorReading>()
        val humidityChannel = Channel<SensorReading>()
        val pressureChannel = Channel<SensorReading>()
        
        // Sensor simulators
        launch {
            repeat(4) { i ->
                delay(Random.nextLong(150, 350))
                temperatureChannel.send(SensorReading("TEMP", 20.0 + Random.nextDouble(-2.0, 2.0), System.currentTimeMillis()))
            }
            temperatureChannel.close()
        }
        
        launch {
            repeat(3) { i ->
                delay(Random.nextLong(200, 400))
                humidityChannel.send(SensorReading("HUMIDITY", 45.0 + Random.nextDouble(-5.0, 5.0), System.currentTimeMillis()))
            }
            humidityChannel.close()
        }
        
        launch {
            repeat(5) { i ->
                delay(Random.nextLong(100, 300))
                pressureChannel.send(SensorReading("PRESSURE", 1013.0 + Random.nextDouble(-10.0, 10.0), System.currentTimeMillis()))
            }
            pressureChannel.close()
        }
        
        // Data aggregator
        val aggregator = launch {
            val readings = mutableMapOf<String, SensorReading>()
            var totalReadings = 0
            var channelsOpen = 3
            
            while (channelsOpen > 0) {
                select<Unit> {
                    temperatureChannel.onReceiveCatching { result ->
                        result.onSuccess { reading ->
                            readings[reading.sensorId] = reading
                            totalReadings++
                            println("  Aggregator: Temperature = ${"%.1f".format(reading.value)}°C")
                        }.onFailure {
                            println("  Aggregator: Temperature sensor offline")
                            channelsOpen--
                        }
                    }
                    
                    humidityChannel.onReceiveCatching { result ->
                        result.onSuccess { reading ->
                            readings[reading.sensorId] = reading
                            totalReadings++
                            println("  Aggregator: Humidity = ${"%.1f".format(reading.value)}%")
                        }.onFailure {
                            println("  Aggregator: Humidity sensor offline")
                            channelsOpen--
                        }
                    }
                    
                    pressureChannel.onReceiveCatching { result ->
                        result.onSuccess { reading ->
                            readings[reading.sensorId] = reading
                            totalReadings++
                            println("  Aggregator: Pressure = ${"%.1f".format(reading.value)} hPa")
                        }.onFailure {
                            println("  Aggregator: Pressure sensor offline")
                            channelsOpen--
                        }
                    }
                }
                
                // Report aggregate when we have all sensor types
                if (readings.size == 3) {
                    val temp = readings["TEMP"]?.value
                    val humidity = readings["HUMIDITY"]?.value
                    val pressure = readings["PRESSURE"]?.value
                    
                    println("  📊 Aggregate: T=${"%.1f".format(temp)}°C, H=${"%.1f".format(humidity)}%, P=${"%.1f".format(pressure)}hPa")
                }
            }
            
            println("  Aggregator: All sensors offline, total readings: $totalReadings")
        }
        
        aggregator.join()
        
        println("Select loops completed\n")
    }
}

/**
 * Advanced select patterns and multiplexing strategies
 */
class AdvancedSelectPatterns {
    
    fun demonstratePrioritySelection() = runBlocking {
        println("=== Priority-Based Selection ===")
        
        println("1. Channel priority selection:")
        
        data class PriorityMessage(val content: String, val priority: Int)
        
        val highPriorityChannel = Channel<PriorityMessage>()
        val mediumPriorityChannel = Channel<PriorityMessage>()
        val lowPriorityChannel = Channel<PriorityMessage>()
        
        // Message producers with different priorities
        launch {
            repeat(3) { i ->
                delay(Random.nextLong(100, 300))
                highPriorityChannel.send(PriorityMessage("High-$i", 3))
            }
            highPriorityChannel.close()
        }
        
        launch {
            repeat(4) { i ->
                delay(Random.nextLong(80, 250))
                mediumPriorityChannel.send(PriorityMessage("Medium-$i", 2))
            }
            mediumPriorityChannel.close()
        }
        
        launch {
            repeat(6) { i ->
                delay(Random.nextLong(50, 200))
                lowPriorityChannel.send(PriorityMessage("Low-$i", 1))
            }
            lowPriorityChannel.close()
        }
        
        // Priority processor - always check higher priority channels first
        val priorityProcessor = launch {
            var processedMessages = 0
            var channelsOpen = 3
            
            while (channelsOpen > 0) {
                // Try high priority first
                var messageProcessed = false
                
                // High priority check
                select<Unit> {
                    highPriorityChannel.onReceiveCatching { result ->
                        result.onSuccess { message ->
                            println("  🔴 HIGH Priority: ${message.content}")
                            processedMessages++
                            messageProcessed = true
                        }.onFailure {
                            println("  High priority channel closed")
                            channelsOpen--
                        }
                    }
                    default {
                        // High priority not available, try medium
                    }
                }
                
                if (!messageProcessed && channelsOpen > 0) {
                    select<Unit> {
                        mediumPriorityChannel.onReceiveCatching { result ->
                            result.onSuccess { message ->
                                println("  🟡 MEDIUM Priority: ${message.content}")
                                processedMessages++
                                messageProcessed = true
                            }.onFailure {
                                println("  Medium priority channel closed")
                                channelsOpen--
                            }
                        }
                        default {
                            // Medium priority not available, try low
                        }
                    }
                }
                
                if (!messageProcessed && channelsOpen > 0) {
                    select<Unit> {
                        lowPriorityChannel.onReceiveCatching { result ->
                            result.onSuccess { message ->
                                println("  🟢 LOW Priority: ${message.content}")
                                processedMessages++
                                messageProcessed = true
                            }.onFailure {
                                println("  Low priority channel closed")
                                channelsOpen--
                            }
                        }
                        onTimeout(50) {
                            // No messages available at any priority level
                        }
                    }
                }
                
                if (!messageProcessed && channelsOpen > 0) {
                    delay(10) // Brief pause when no messages available
                }
            }
            
            println("  Priority processor completed: $processedMessages messages processed")
        }
        
        priorityProcessor.join()
        
        println()
        
        println("2. Adaptive priority selection:")
        
        // Channels representing different workload types
        val criticalChannel = Channel<String>()
        val normalChannel = Channel<String>()
        val backgroundChannel = Channel<String>()
        
        launch {
            repeat(2) { i ->
                delay(400)
                criticalChannel.send("Critical-Task-$i")
            }
            criticalChannel.close()
        }
        
        launch {
            repeat(5) { i ->
                delay(200)
                normalChannel.send("Normal-Task-$i")
            }
            normalChannel.close()
        }
        
        launch {
            repeat(10) { i ->
                delay(100)
                backgroundChannel.send("Background-Task-$i")
            }
            backgroundChannel.close()
        }
        
        // Adaptive processor that adjusts priority based on load
        val adaptiveProcessor = launch {
            var criticalProcessed = 0
            var normalProcessed = 0
            var backgroundProcessed = 0
            var channelsOpen = 3
            
            while (channelsOpen > 0) {
                // Adaptive strategy: after processing many background tasks, prioritize higher level work
                val prioritizeHigher = backgroundProcessed > criticalProcessed + normalProcessed + 3
                
                if (prioritizeHigher) {
                    println("  📈 Adaptive: Prioritizing higher-level work")
                }
                
                select<Unit> {
                    criticalChannel.onReceiveCatching { result ->
                        result.onSuccess { task ->
                            println("  🚨 CRITICAL: $task")
                            criticalProcessed++
                            delay(200) // Longer processing for critical tasks
                        }.onFailure {
                            channelsOpen--
                        }
                    }
                    
                    if (!prioritizeHigher) {
                        normalChannel.onReceiveCatching { result ->
                            result.onSuccess { task ->
                                println("  📋 NORMAL: $task")
                                normalProcessed++
                                delay(100)
                            }.onFailure {
                                channelsOpen--
                            }
                        }
                    }
                    
                    if (!prioritizeHigher) {
                        backgroundChannel.onReceiveCatching { result ->
                            result.onSuccess { task ->
                                println("  🔄 BACKGROUND: $task")
                                backgroundProcessed++
                                delay(50) // Quick processing for background tasks
                            }.onFailure {
                                channelsOpen--
                            }
                        }
                    }
                    
                    onTimeout(100) {
                        // No immediate work available
                    }
                }
            }
            
            println("  Adaptive processor stats: Critical=$criticalProcessed, Normal=$normalProcessed, Background=$backgroundProcessed")
        }
        
        adaptiveProcessor.join()
        
        println("Priority selection completed\n")
    }
    
    fun demonstrateLoadBalancing() = runBlocking {
        println("=== Load Balancing with Select ===")
        
        println("1. Round-robin load balancing:")
        
        // Multiple worker channels
        val worker1Channel = Channel<String>()
        val worker2Channel = Channel<String>()
        val worker3Channel = Channel<String>()
        val workers = listOf(worker1Channel, worker2Channel, worker3Channel)
        
        // Worker implementations
        workers.forEachIndexed { index, channel ->
            launch {
                val workerId = index + 1
                for (task in channel) {
                    val processingTime = Random.nextLong(100, 300)
                    println("  Worker-$workerId: Processing $task")
                    delay(processingTime)
                    println("  Worker-$workerId: ✅ Completed $task (${processingTime}ms)")
                }
                println("  Worker-$workerId: Finished")
            }
        }
        
        // Load balancer
        val loadBalancer = launch {
            var currentWorker = 0
            
            repeat(12) { taskId ->
                val task = "Task-$taskId"
                
                // Find next available worker using round-robin
                var assigned = false
                var attempts = 0
                
                while (!assigned && attempts < workers.size) {
                    val worker = workers[currentWorker]
                    
                    select<Unit> {
                        worker.onSend(task) {
                            println("  LoadBalancer: Assigned $task to Worker-${currentWorker + 1}")
                            assigned = true
                            currentWorker = (currentWorker + 1) % workers.size
                        }
                        default {
                            // Worker busy, try next one
                            currentWorker = (currentWorker + 1) % workers.size
                            attempts++
                        }
                    }
                }
                
                if (!assigned) {
                    println("  LoadBalancer: All workers busy, task $task queued to Worker-1")
                    worker1Channel.send(task) // Fallback to first worker
                }
                
                delay(50) // Task arrival rate
            }
            
            workers.forEach { it.close() }
        }
        
        loadBalancer.join()
        
        println()
        
        println("2. Capacity-based load balancing:")
        
        data class WorkerCapacity(val id: Int, val channel: Channel<String>, var currentLoad: Int, val maxCapacity: Int)
        
        val capacityWorkers = listOf(
            WorkerCapacity(1, Channel(3), 0, 3), // High capacity
            WorkerCapacity(2, Channel(2), 0, 2), // Medium capacity
            WorkerCapacity(3, Channel(1), 0, 1)  // Low capacity
        )
        
        // Capacity-aware workers
        capacityWorkers.forEach { worker ->
            launch {
                for (task in worker.channel) {
                    worker.currentLoad++
                    val processingTime = Random.nextLong(200, 400)
                    
                    println("  Worker-${worker.id}: Processing $task (load: ${worker.currentLoad}/${worker.maxCapacity})")
                    delay(processingTime)
                    println("  Worker-${worker.id}: ✅ Completed $task")
                    
                    worker.currentLoad--
                }
            }
        }
        
        // Capacity-based load balancer
        val capacityBalancer = launch {
            repeat(10) { taskId ->
                val task = "CapTask-$taskId"
                
                // Find worker with available capacity
                var assigned = false
                
                // Sort workers by available capacity (descending)
                val availableWorkers = capacityWorkers
                    .filter { it.currentLoad < it.maxCapacity }
                    .sortedByDescending { it.maxCapacity - it.currentLoad }
                
                for (worker in availableWorkers) {
                    select<Unit> {
                        worker.channel.onSend(task) {
                            println("  CapacityBalancer: Assigned $task to Worker-${worker.id} (capacity: ${worker.maxCapacity - worker.currentLoad})")
                            assigned = true
                        }
                        default {
                            // Worker became unavailable, try next
                        }
                    }
                    
                    if (assigned) break
                }
                
                if (!assigned) {
                    // All workers at capacity, wait and retry
                    println("  CapacityBalancer: All workers at capacity, waiting...")
                    delay(100)
                    capacityWorkers[0].channel.send(task) // Fallback
                }
                
                delay(150) // Task generation rate
            }
            
            capacityWorkers.forEach { it.channel.close() }
        }
        
        capacityBalancer.join()
        
        println("Load balancing completed\n")
    }
    
    fun demonstrateSelectComposition() = runBlocking {
        println("=== Select Composition Patterns ===")
        
        println("1. Nested select expressions:")
        
        val primaryChannel = Channel<String>()
        val secondaryChannel = Channel<String>()
        val fallbackChannel = Channel<String>()
        
        // Data sources with different reliability
        launch {
            delay(500) // Primary source is slow
            primaryChannel.send("Primary Data")
        }
        
        launch {
            delay(200) // Secondary source is faster
            secondaryChannel.send("Secondary Data")
        }
        
        launch {
            repeat(3) { i ->
                delay(100 * (i + 1))
                fallbackChannel.send("Fallback-$i")
            }
            fallbackChannel.close()
        }
        
        // Composed selector with fallback strategy
        val composedSelector = launch {
            // First level: try primary or secondary
            val primaryResult = select<String?> {
                primaryChannel.onReceive { "Primary: $it" }
                secondaryChannel.onReceive { "Secondary: $it" }
                onTimeout(300) { null } // Give primary/secondary 300ms
            }
            
            if (primaryResult != null) {
                println("  ComposedSelector: Got preferred source - $primaryResult")
            } else {
                println("  ComposedSelector: Primary/secondary unavailable, using fallback")
                
                // Second level: fallback selection
                select<Unit> {
                    fallbackChannel.onReceive { data ->
                        println("  ComposedSelector: Fallback data - $data")
                    }
                    onTimeout(1000) {
                        println("  ComposedSelector: All sources unavailable")
                    }
                }
            }
        }
        
        composedSelector.join()
        
        primaryChannel.close()
        secondaryChannel.close()
        
        println()
        
        println("2. Select with dynamic channel sets:")
        
        // Dynamic set of channels that can grow/shrink
        class DynamicChannelMultiplexer<T> {
            private val channels = mutableMapOf<String, Channel<T>>()
            private val results = Channel<Pair<String, T>>()
            
            suspend fun addChannel(name: String, capacity: Int = Channel.UNLIMITED): Channel<T> {
                val channel = Channel<T>(capacity)
                channels[name] = channel
                println("    Multiplexer: Added channel '$name'")
                return channel
            }
            
            suspend fun removeChannel(name: String) {
                channels[name]?.close()
                channels.remove(name)
                println("    Multiplexer: Removed channel '$name'")
            }
            
            suspend fun startMultiplexing() {
                while (channels.isNotEmpty()) {
                    val channelEntries = channels.toList() // Snapshot for select
                    
                    if (channelEntries.isEmpty()) break
                    
                    val result = selectChannelResult(channelEntries)
                    if (result != null) {
                        results.send(result)
                    } else {
                        delay(10) // Brief pause when no channels ready
                    }
                }
                results.close()
            }
            
            private suspend fun selectChannelResult(channelEntries: List<Pair<String, Channel<T>>>): Pair<String, T>? {
                return select {
                    channelEntries.forEach { (name, channel) ->
                        channel.onReceiveCatching { result ->
                            result.onSuccess { value ->
                                name to value
                            }.onFailure {
                                // Channel closed, will be removed in next iteration
                                null
                            }
                        }
                    }
                    onTimeout(100) { null }
                }
            }
            
            fun getResults(): Flow<Pair<String, T>> = results.receiveAsFlow()
        }
        
        val multiplexer = DynamicChannelMultiplexer<String>()
        
        // Start multiplexing in background
        val multiplexingJob = launch {
            multiplexer.startMultiplexing()
        }
        
        // Add channels dynamically
        val channel1 = multiplexer.addChannel("DataSource1")
        val channel2 = multiplexer.addChannel("DataSource2")
        
        // Generate data
        launch {
            repeat(3) { i ->
                delay(200)
                channel1.send("Data1-$i")
            }
            multiplexer.removeChannel("DataSource1")
        }
        
        launch {
            repeat(4) { i ->
                delay(150)
                channel2.send("Data2-$i")
            }
        }
        
        // Add another channel after some time
        launch {
            delay(400)
            val channel3 = multiplexer.addChannel("DataSource3")
            repeat(2) { i ->
                delay(100)
                channel3.send("Data3-$i")
            }
            multiplexer.removeChannel("DataSource3")
            multiplexer.removeChannel("DataSource2")
        }
        
        // Collect results
        val resultCollector = launch {
            multiplexer.getResults().collect { (source, data) ->
                println("  DynamicMultiplexer: Received '$data' from $source")
            }
        }
        
        resultCollector.join()
        multiplexingJob.join()
        
        println("Select composition completed\n")
    }
}

/**
 * Real-world select expression applications
 */
class SelectApplications {
    
    fun demonstrateTimeoutPatterns() = runBlocking {
        println("=== Timeout Patterns with Select ===")
        
        println("1. Progressive timeouts:")
        
        val dataChannel = Channel<String>()
        
        // Unreliable data source
        launch {
            delay(Random.nextLong(100, 1500)) // Random delay
            if (Random.nextBoolean()) {
                dataChannel.send("Important Data")
            }
            // Might not send anything (simulating failure)
        }
        
        // Progressive timeout strategy
        val progressiveTimeout = launch {
            var timeoutMs = 200L
            var attempts = 0
            val maxAttempts = 4
            
            while (attempts < maxAttempts) {
                attempts++
                
                val result = select<String?> {
                    dataChannel.onReceive { data ->
                        "Success: $data"
                    }
                    onTimeout(timeoutMs) {
                        null
                    }
                }
                
                if (result != null) {
                    println("  ProgressiveTimeout: $result (attempt $attempts)")
                    break
                } else {
                    println("  ProgressiveTimeout: Attempt $attempts timed out after ${timeoutMs}ms")
                    timeoutMs = minOf(timeoutMs * 2, 2000L) // Exponential backoff, max 2s
                    
                    if (attempts < maxAttempts) {
                        println("  ProgressiveTimeout: Retrying with ${timeoutMs}ms timeout...")
                        delay(100) // Brief pause between attempts
                    }
                }
            }
            
            if (attempts >= maxAttempts) {
                println("  ProgressiveTimeout: All attempts exhausted")
            }
        }
        
        progressiveTimeout.join()
        dataChannel.close()
        
        println()
        
        println("2. Watchdog pattern:")
        
        val heartbeatChannel = Channel<String>()
        val workCompletionChannel = Channel<String>()
        
        // Simulated worker that sends heartbeats
        val worker = launch {
            repeat(5) { i ->
                val workTime = Random.nextLong(200, 800)
                println("  Worker: Starting task $i (estimated ${workTime}ms)")
                
                // Send heartbeat every 300ms during work
                val heartbeatJob = launch {
                    while (coroutineContext.isActive) {
                        delay(300)
                        heartbeatChannel.trySend("Heartbeat from task $i")
                    }
                }
                
                delay(workTime) // Do actual work
                heartbeatJob.cancel()
                
                workCompletionChannel.send("Completed task $i")
                delay(100) // Brief pause between tasks
            }
            
            heartbeatChannel.close()
            workCompletionChannel.close()
        }
        
        // Watchdog monitoring worker health
        val watchdog = launch {
            var lastHeartbeat = System.currentTimeMillis()
            var completedTasks = 0
            var watchdogActive = true
            
            while (watchdogActive) {
                select<Unit> {
                    heartbeatChannel.onReceiveCatching { result ->
                        result.onSuccess { heartbeat ->
                            lastHeartbeat = System.currentTimeMillis()
                            // println("  Watchdog: Received $heartbeat")
                        }.onFailure {
                            println("  Watchdog: Heartbeat channel closed")
                        }
                    }
                    
                    workCompletionChannel.onReceiveCatching { result ->
                        result.onSuccess { completion ->
                            println("  Watchdog: Monitored completion - $completion")
                            completedTasks++
                        }.onFailure {
                            println("  Watchdog: Work completion channel closed")
                            watchdogActive = false
                        }
                    }
                    
                    onTimeout(1000) { // Check for heartbeat timeout
                        val timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat
                        if (timeSinceHeartbeat > 1000) {
                            println("  🚨 Watchdog: ALERT - No heartbeat for ${timeSinceHeartbeat}ms!")
                        } else {
                            // println("  Watchdog: Health check OK")
                        }
                    }
                }
            }
            
            println("  Watchdog: Monitoring completed - $completedTasks tasks observed")
        }
        
        worker.join()
        watchdog.join()
        
        println("Timeout patterns completed\n")
    }
    
    fun demonstrateEventRouting() = runBlocking {
        println("=== Event Routing with Select ===")
        
        println("1. Message routing system:")
        
        data class RoutedMessage(val destination: String, val content: String, val priority: Int = 1)
        
        val inboxChannel = Channel<RoutedMessage>()
        val userAChannel = Channel<RoutedMessage>()
        val userBChannel = Channel<RoutedMessage>()
        val systemChannel = Channel<RoutedMessage>()
        val deadLetterChannel = Channel<RoutedMessage>()
        
        // Message producers
        launch {
            val messages = listOf(
                RoutedMessage("userA", "Hello UserA!", 2),
                RoutedMessage("system", "System maintenance scheduled", 3),
                RoutedMessage("userB", "Meeting reminder", 1),
                RoutedMessage("userA", "Project update", 2),
                RoutedMessage("unknown", "Invalid destination", 1),
                RoutedMessage("system", "Critical alert", 3),
                RoutedMessage("userB", "Calendar invite", 1)
            )
            
            messages.forEach { message ->
                inboxChannel.send(message)
                delay(200)
            }
            inboxChannel.close()
        }
        
        // Message router using select
        val messageRouter = launch {
            for (message in inboxChannel) {
                println("  Router: Routing message to '${message.destination}': ${message.content}")
                
                val routed = select<Boolean> {
                    if (message.destination == "userA") {
                        userAChannel.onSend(message) { true }
                    }
                    if (message.destination == "userB") {
                        userBChannel.onSend(message) { true }
                    }
                    if (message.destination == "system") {
                        systemChannel.onSend(message) { true }
                    }
                    onTimeout(100) { false } // Route to dead letter if delivery fails
                }
                
                if (!routed) {
                    println("  Router: Message undeliverable, sending to dead letter")
                    deadLetterChannel.send(message)
                }
            }
            
            // Close all channels
            userAChannel.close()
            userBChannel.close()
            systemChannel.close()
            deadLetterChannel.close()
        }
        
        // Message handlers
        val userAHandler = launch {
            for (message in userAChannel) {
                println("  📬 UserA: Received '${message.content}' (priority: ${message.priority})")
                delay(100)
            }
        }
        
        val userBHandler = launch {
            for (message in userBChannel) {
                println("  📬 UserB: Received '${message.content}' (priority: ${message.priority})")
                delay(150)
            }
        }
        
        val systemHandler = launch {
            for (message in systemChannel) {
                println("  🖥️ System: Received '${message.content}' (priority: ${message.priority})")
                delay(50) // System messages processed quickly
            }
        }
        
        val deadLetterHandler = launch {
            for (message in deadLetterChannel) {
                println("  💀 DeadLetter: Unroutable message to '${message.destination}': ${message.content}")
            }
        }
        
        listOf(messageRouter, userAHandler, userBHandler, systemHandler, deadLetterHandler).forEach { it.join() }
        
        println("Event routing completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic select expressions
        BasicSelectExpressions().demonstrateBasicSelect()
        BasicSelectExpressions().demonstrateSelectLoops()
        
        // Advanced select patterns
        AdvancedSelectPatterns().demonstratePrioritySelection()
        AdvancedSelectPatterns().demonstrateLoadBalancing()
        AdvancedSelectPatterns().demonstrateSelectComposition()
        
        // Real-world applications
        SelectApplications().demonstrateTimeoutPatterns()
        SelectApplications().demonstrateEventRouting()
        
        println("=== Select Expressions Summary ===")
        println("✅ Basic Select Operations:")
        println("   - onReceive: Wait for channel receive operations")
        println("   - onSend: Wait for channel send operations")
        println("   - onTimeout: Handle timeout scenarios")
        println("   - default: Non-blocking fallback when no operations ready")
        println()
        println("✅ Select Loop Patterns:")
        println("   - Event processing loops with multiple input sources")
        println("   - Multi-source data aggregation")
        println("   - Shutdown and control signal handling")
        println()
        println("✅ Advanced Patterns:")
        println("   - Priority-based channel selection")
        println("   - Load balancing across multiple workers")
        println("   - Adaptive selection strategies")
        println("   - Dynamic channel set multiplexing")
        println()
        println("✅ Real-World Applications:")
        println("   - Progressive timeout strategies")
        println("   - Watchdog monitoring patterns")
        println("   - Message routing and event dispatching")
        println("   - Health checking and failure detection")
        println()
        println("✅ Best Practices:")
        println("   - Always handle channel closing with onReceiveCatching")
        println("   - Use timeouts to prevent indefinite blocking")
        println("   - Consider priority and fairness in selection logic")
        println("   - Design clear termination conditions for select loops")
        println("   - Test edge cases like channel closing and timeouts")
    }
}