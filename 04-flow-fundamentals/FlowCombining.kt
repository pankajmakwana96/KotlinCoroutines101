/**
 * # Flow Combining and Composition Patterns
 * 
 * ## Problem Description
 * Real-world applications often need to combine data from multiple sources:
 * user inputs, database queries, network requests, and sensor readings.
 * Simple sequential processing is insufficient when sources have different
 * update frequencies, latencies, and availability patterns.
 * 
 * ## Solution Approach
 * Advanced flow combination patterns include:
 * - Merging flows with different characteristics
 * - Zipping flows for synchronized processing
 * - Combining latest values for reactive UIs
 * - Flattening nested flows with control strategies
 * - Fan-out and fan-in patterns for parallel processing
 * 
 * ## Key Learning Points
 * - Different combination operators serve different use cases
 * - combine vs combineLatest vs zip semantics
 * - Concurrency control in flatMap variants
 * - Backpressure handling in combined flows
 * - Performance implications of different strategies
 * 
 * ## Performance Considerations
 * - Merge overhead: ~50-200ns per emission
 * - Combine overhead: ~100-500ns per combination
 * - Zip overhead: ~100-300ns per pair
 * - FlatMap overhead: depends on concurrency level
 * - Memory usage scales with buffer sizes and source count
 * 
 * ## Common Pitfalls
 * - Wrong operator choice for use case
 * - Uncontrolled concurrency in flatMap operations
 * - Memory leaks in long-running combinations
 * - Incorrect handling of source completion
 * - Performance degradation with many sources
 * 
 * ## Real-World Applications
 * - Reactive UI state management
 * - Multi-sensor data fusion
 * - Real-time analytics and monitoring
 * - Event sourcing and CQRS
 * - Distributed system coordination
 * 
 * ## Related Concepts
 * - Channels - Hot stream coordination
 * - StateFlow/SharedFlow - Reactive state management
 * - Reactive programming patterns
 */

package flow.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic flow combination operators
 * 
 * Flow Combination Patterns:
 * 
 * Merge (Union):
 * Flow A: ──1────3────5────
 * Flow B: ────2────4────6──
 * Merged: ──1─2──3─4──5─6──
 * 
 * Zip (Synchronized):
 * Flow A: ──1────3────5────
 * Flow B: ────2────4────6──
 * Zipped: ──────(1,2)──(3,4)──(5,6)
 * 
 * Combine (Latest):
 * Flow A: ──1────3─────────
 * Flow B: ────2────4───────
 * Combined: ────(1,2)─(3,2)─(3,4)
 */
class BasicFlowCombination {
    
    fun demonstrateMerge() = runBlocking {
        println("=== Flow Merge Operations ===")
        
        // Basic merge
        println("1. Basic merge - combining multiple sources:")
        
        val userActions = flow {
            listOf("click", "scroll", "type").forEach { action ->
                emit("User: $action")
                delay(Random.nextLong(100, 300))
            }
        }
        
        val systemEvents = flow {
            listOf("startup", "memory_warning", "network_change").forEach { event ->
                emit("System: $event")
                delay(Random.nextLong(150, 400))
            }
        }
        
        val notifications = flow {
            listOf("message", "update_available").forEach { notification ->
                emit("Notification: $notification")
                delay(Random.nextLong(200, 500))
            }
        }
        
        merge(userActions, systemEvents, notifications)
            .collect { event ->
                println("  Event: $event at ${System.currentTimeMillis() % 10000}")
            }
        
        println()
        
        // Merge with different priorities
        println("2. Prioritized merge:")
        
        data class PrioritizedEvent(val priority: Int, val message: String, val timestamp: Long)
        
        val highPriorityEvents = flow {
            repeat(3) { i ->
                emit(PrioritizedEvent(1, "Critical-$i", System.currentTimeMillis()))
                delay(300)
            }
        }
        
        val mediumPriorityEvents = flow {
            repeat(5) { i ->
                emit(PrioritizedEvent(2, "Warning-$i", System.currentTimeMillis()))
                delay(200)
            }
        }
        
        val lowPriorityEvents = flow {
            repeat(8) { i ->
                emit(PrioritizedEvent(3, "Info-$i", System.currentTimeMillis()))
                delay(100)
            }
        }
        
        merge(highPriorityEvents, mediumPriorityEvents, lowPriorityEvents)
            .take(10) // Limit output for demo
            .collect { event ->
                val priorityName = when (event.priority) {
                    1 -> "🔴 HIGH"
                    2 -> "🟡 MED "
                    else -> "🟢 LOW "
                }
                println("  $priorityName: ${event.message}")
            }
        
        println("Merge operations completed\n")
    }
    
    fun demonstrateZip() = runBlocking {
        println("=== Flow Zip Operations ===")
        
        // Basic zip
        println("1. Basic zip - synchronized combination:")
        
        val temperatures = flow {
            listOf(20.5, 21.0, 22.5, 23.0, 21.5).forEach { temp ->
                emit(temp)
                delay(150)
            }
        }
        
        val humidity = flow {
            listOf(45, 47, 50, 48, 46).forEach { humid ->
                emit(humid)
                delay(100) // Different timing than temperature
            }
        }
        
        val timestamps = flow {
            repeat(5) { i ->
                emit("T${i + 1}")
                delay(200)
            }
        }
        
        temperatures.zip(humidity) { temp, humid ->
            "Temperature: ${temp}°C, Humidity: ${humid}%"
        }.zip(timestamps) { weather, time ->
            "$time: $weather"
        }.collect { reading ->
            println("  Weather reading: $reading")
        }
        
        println()
        
        // Zip with transformation
        println("2. Zip with complex transformation:")
        
        data class StockPrice(val symbol: String, val price: Double, val timestamp: Long)
        data class Volume(val symbol: String, val volume: Long, val timestamp: Long)
        data class MarketData(val symbol: String, val price: Double, val volume: Long, val vwap: Double)
        
        val prices = flow {
            val symbols = listOf("AAPL", "GOOGL", "MSFT")
            symbols.forEach { symbol ->
                val price = Random.nextDouble(100.0, 200.0)
                emit(StockPrice(symbol, price, System.currentTimeMillis()))
                delay(200)
            }
        }
        
        val volumes = flow {
            val symbols = listOf("AAPL", "GOOGL", "MSFT")
            symbols.forEach { symbol ->
                val volume = Random.nextLong(1000, 10000)
                emit(Volume(symbol, volume, System.currentTimeMillis()))
                delay(180)
            }
        }
        
        prices.zip(volumes) { price, volume ->
            // Calculate Volume Weighted Average Price (simplified)
            val vwap = price.price * 0.98 + Random.nextDouble(-1.0, 1.0)
            MarketData(price.symbol, price.price, volume.volume, vwap)
        }.collect { marketData ->
            println("  ${marketData.symbol}: Price=${String.format("%.2f", marketData.price)}, " +
                    "Volume=${marketData.volume}, VWAP=${String.format("%.2f", marketData.vwap)}")
        }
        
        println()
        
        // Multiple zip
        println("3. Multiple sources zip:")
        
        val source1 = flowOf("A1", "A2", "A3")
        val source2 = flowOf("B1", "B2", "B3") 
        val source3 = flowOf("C1", "C2", "C3")
        
        combine(source1, source2, source3) { a, b, c ->
            "Combined: $a + $b + $c"
        }.collect { result ->
            println("  $result")
        }
        
        println("Zip operations completed\n")
    }
    
    fun demonstrateCombine() = runBlocking {
        println("=== Flow Combine Operations ===")
        
        // Basic combine vs zip comparison
        println("1. Combine vs Zip comparison:")
        
        val fastFlow = flow {
            repeat(6) { i ->
                emit("Fast-$i")
                delay(100)
            }
        }
        
        val slowFlow = flow {
            repeat(3) { i ->
                emit("Slow-$i")
                delay(250)
            }
        }
        
        println("   Zip results (waits for both):")
        fastFlow.zip(slowFlow) { fast, slow -> "$fast + $slow" }
            .collect { println("     $it") }
        
        println("   Combine results (latest from each):")
        fastFlow.combine(slowFlow) { fast, slow -> "$fast + $slow" }
            .collect { println("     $it") }
        
        println()
        
        // Reactive UI state example
        println("2. Reactive UI state management:")
        
        data class UIState(
            val isLoading: Boolean = false,
            val userData: String? = null,
            val notifications: Int = 0,
            val connectionStatus: String = "unknown"
        )
        
        val loadingState = flow {
            emit(true)
            delay(500)
            emit(false)
        }
        
        val userDataFlow = flow {
            delay(300)
            emit("John Doe")
            delay(400)
            emit("John Doe (Premium)")
        }
        
        val notificationCount = flow {
            var count = 0
            repeat(5) {
                emit(count++)
                delay(200)
            }
        }
        
        val connectionStatus = flow {
            val statuses = listOf("connecting", "connected", "slow", "connected")
            statuses.forEach { status ->
                emit(status)
                delay(300)
            }
        }
        
        combine(loadingState, userDataFlow, notificationCount, connectionStatus) { 
            loading, userData, notifications, connection ->
            UIState(loading, userData, notifications, connection)
        }.collect { uiState ->
            val loadingIndicator = if (uiState.isLoading) "🔄" else "✅"
            val connectionIndicator = when (uiState.connectionStatus) {
                "connected" -> "🟢"
                "slow" -> "🟡"
                "connecting" -> "🔵"
                else -> "🔴"
            }
            
            println("  UI: $loadingIndicator User: ${uiState.userData ?: "Loading..."} " +
                    "| Notifications: ${uiState.notifications} | $connectionIndicator ${uiState.connectionStatus}")
        }
        
        println("Combine operations completed\n")
    }
}

/**
 * Advanced flow combination patterns
 */
class AdvancedFlowCombination {
    
    fun demonstrateConditionalCombination() = runBlocking {
        println("=== Conditional Flow Combination ===")
        
        // Dynamic source selection
        println("1. Dynamic source selection:")
        
        data class ConfigFlow(val source: String, val enabled: Boolean)
        
        val configFlow = flow {
            val configs = listOf(
                ConfigFlow("primary", true),
                ConfigFlow("secondary", false),
                ConfigFlow("primary", false),
                ConfigFlow("backup", true),
                ConfigFlow("primary", true)
            )
            
            configs.forEach { config ->
                emit(config)
                delay(400)
            }
        }
        
        val primarySource = flow {
            repeat(10) { i ->
                emit("Primary-$i")
                delay(200)
            }
        }
        
        val secondarySource = flow {
            repeat(10) { i ->
                emit("Secondary-$i")
                delay(250)
            }
        }
        
        val backupSource = flow {
            repeat(10) { i ->
                emit("Backup-$i")
                delay(300)
            }
        }
        
        // Dynamically switch sources based on configuration
        configFlow
            .flatMapLatest { config ->
                when {
                    config.source == "primary" && config.enabled -> primarySource
                    config.source == "secondary" && config.enabled -> secondarySource
                    config.source == "backup" && config.enabled -> backupSource
                    else -> flowOf("Source disabled")
                }
            }
            .take(15)
            .collect { data ->
                println("  Active data: $data")
            }
        
        println()
        
        // Conditional merge based on criteria
        println("2. Conditional merge with filtering:")
        
        data class DataSource(val id: String, val priority: Int, val isHealthy: Boolean)
        
        val healthStatus = flow {
            val statuses = listOf(
                DataSource("A", 1, true),
                DataSource("B", 2, true),
                DataSource("C", 3, false),
                DataSource("A", 1, false),
                DataSource("B", 2, true),
                DataSource("C", 3, true)
            )
            
            statuses.forEach { status ->
                emit(status)
                delay(300)
            }
        }
        
        val sourceA = flow {
            repeat(8) { i ->
                emit("SourceA-Data-$i")
                delay(150)
            }
        }
        
        val sourceB = flow {
            repeat(8) { i ->
                emit("SourceB-Data-$i")
                delay(200)
            }
        }
        
        val sourceC = flow {
            repeat(8) { i ->
                emit("SourceC-Data-$i")
                delay(250)
            }
        }
        
        // Only merge healthy sources, prioritize by priority
        healthStatus
            .scan(emptyMap<String, DataSource>()) { acc, status ->
                acc + (status.id to status)
            }
            .flatMapLatest { healthMap ->
                val healthySources = healthMap.values
                    .filter { it.isHealthy }
                    .sortedBy { it.priority }
                
                when {
                    healthySources.any { it.id == "A" } -> sourceA.map { "Priority: $it" }
                    healthySources.any { it.id == "B" } -> sourceB.map { "Fallback: $it" }
                    healthySources.any { it.id == "C" } -> sourceC.map { "Backup: $it" }
                    else -> flowOf("No healthy sources available")
                }
            }
            .take(10)
            .collect { data ->
                println("  Selected data: $data")
            }
        
        println("Conditional combination completed\n")
    }
    
    fun demonstrateRateLimitedCombination() = runBlocking {
        println("=== Rate-Limited Flow Combination ===")
        
        // Throttled combine
        suspend fun <T> Flow<T>.throttleLatest(intervalMs: Long): Flow<T> = flow {
            var lastEmissionTime = 0L
            var latestValue: T? = null
            var hasValue = false
            
            collect { value ->
                latestValue = value
                hasValue = true
                
                val now = System.currentTimeMillis()
                if (now - lastEmissionTime >= intervalMs) {
                    emit(value)
                    lastEmissionTime = now
                    hasValue = false
                }
            }
            
            // Emit last value if we have one pending
            if (hasValue && latestValue != null) {
                emit(latestValue!!)
            }
        }
        
        println("1. Throttled combination:")
        
        val rapidUpdates = flow {
            repeat(20) { i ->
                emit("Update-$i")
                delay(50) // Very frequent updates
            }
        }
        
        val slowSensor = flow {
            repeat(5) { i ->
                emit("Sensor-$i")
                delay(300)
            }
        }
        
        rapidUpdates
            .throttleLatest(200) // Throttle rapid updates
            .combine(slowSensor) { update, sensor ->
                "Combined: $update with $sensor"
            }
            .collect { result ->
                println("  Throttled result: $result")
            }
        
        println()
        
        // Sampled combination
        println("2. Sampled combination:")
        
        val continuousData = flow {
            var value = 0.0
            while (currentCoroutineContext().isActive) {
                emit(value)
                value += Random.nextDouble(-0.5, 0.5)
                delay(25)
            }
        }
        
        val periodicTrigger = flow {
            repeat(8) { i ->
                emit("Sample-$i")
                delay(200)
            }
        }
        
        periodicTrigger
            .withLatestFrom(continuousData) { trigger, data ->
                "$trigger: Data=${String.format("%.2f", data)}"
            }
            .collect { sample ->
                println("  Sample: $sample")
            }
        
        println("Rate-limited combination completed\n")
    }
    
    private suspend fun <T, R> Flow<T>.withLatestFrom(other: Flow<R>): Flow<Pair<T, R>> = flow {
        var latestOther: R? = null
        var hasOtherValue = false
        
        // Start collecting from other flow
        val otherJob = launch {
            other.collect { value ->
                latestOther = value
                hasOtherValue = true
            }
        }
        
        try {
            collect { value ->
                if (hasOtherValue) {
                    emit(value to latestOther!!)
                }
            }
        } finally {
            otherJob.cancel()
        }
    }
    
    private suspend fun <T, R, V> Flow<T>.withLatestFrom(
        other: Flow<R>,
        transform: (T, R) -> V
    ): Flow<V> = withLatestFrom(other).map { (t, r) -> transform(t, r) }
    
    fun demonstrateComplexFanInFanOut() = runBlocking {
        println("=== Complex Fan-In/Fan-Out Patterns ===")
        
        // Fan-out: Split single source to multiple processors
        println("1. Fan-out pattern:")
        
        data class RawEvent(val id: Int, val type: String, val payload: String)
        
        val eventSource = flow {
            repeat(12) { i ->
                val eventTypes = listOf("user_action", "system_event", "error", "metric")
                val event = RawEvent(
                    id = i,
                    type = eventTypes.random(),
                    payload = "payload_$i"
                )
                emit(event)
                delay(100)
            }
        }
        
        // Split processing by event type
        val userActionProcessor = eventSource
            .filter { it.type == "user_action" }
            .map { "👤 User Action: ${it.payload}" }
        
        val systemEventProcessor = eventSource
            .filter { it.type == "system_event" }
            .map { "⚙️ System: ${it.payload}" }
        
        val errorProcessor = eventSource
            .filter { it.type == "error" }
            .map { "❌ Error: ${it.payload}" }
        
        val metricProcessor = eventSource
            .filter { it.type == "metric" }
            .map { "📊 Metric: ${it.payload}" }
        
        // Fan-in: Merge all processed results
        merge(userActionProcessor, systemEventProcessor, errorProcessor, metricProcessor)
            .collect { processed ->
                println("  Processed: $processed")
            }
        
        println()
        
        // Complex aggregation pattern
        println("2. Multi-stage aggregation:")
        
        data class SensorReading(val sensorId: String, val value: Double, val timestamp: Long)
        data class AggregatedData(
            val averageValue: Double,
            val maxValue: Double,
            val minValue: Double,
            val sampleCount: Int,
            val timeWindow: String
        )
        
        // Multiple sensor sources
        val sensors = listOf("temp", "pressure", "humidity")
        val sensorFlows = sensors.map { sensorId ->
            flow {
                repeat(15) { i ->
                    val reading = SensorReading(
                        sensorId = sensorId,
                        value = Random.nextDouble(0.0, 100.0),
                        timestamp = System.currentTimeMillis()
                    )
                    emit(reading)
                    delay(Random.nextLong(80, 150))
                }
            }
        }
        
        // Merge all sensor data
        val allReadings = merge(*sensorFlows.toTypedArray())
        
        // Aggregate by time windows
        allReadings
            .chunked(5) // Group into chunks of 5 readings
            .map { readings ->
                val values = readings.map { it.value }
                AggregatedData(
                    averageValue = values.average(),
                    maxValue = values.maxOrNull() ?: 0.0,
                    minValue = values.minOrNull() ?: 0.0,
                    sampleCount = readings.size,
                    timeWindow = "Window-${readings.first().timestamp % 10000}"
                )
            }
            .collect { aggregated ->
                println("  Aggregated: Avg=${String.format("%.1f", aggregated.averageValue)}, " +
                        "Max=${String.format("%.1f", aggregated.maxValue)}, " +
                        "Min=${String.format("%.1f", aggregated.minValue)}, " +
                        "Samples=${aggregated.sampleCount}")
            }
        
        println("Complex fan-in/fan-out completed\n")
    }
}

/**
 * Flow flattening strategies and concurrency control
 */
class FlowFlattening {
    
    fun demonstrateFlatMapStrategies() = runBlocking {
        println("=== FlatMap Strategies Comparison ===")
        
        suspend fun createSubFlow(id: Int): Flow<String> = flow {
            repeat(3) { i ->
                emit("SubFlow-$id-Item-$i")
                delay(Random.nextLong(100, 300))
            }
        }
        
        val mainFlow = flowOf(1, 2, 3, 4)
        
        // flatMapConcat - Sequential processing
        println("1. flatMapConcat (sequential):")
        val concatStart = System.currentTimeMillis()
        mainFlow
            .flatMapConcat { id -> createSubFlow(id) }
            .collect { item ->
                val elapsed = System.currentTimeMillis() - concatStart
                println("  [$elapsed ms] Concat: $item")
            }
        
        println()
        
        // flatMapMerge - Concurrent processing
        println("2. flatMapMerge (concurrent):")
        val mergeStart = System.currentTimeMillis()
        mainFlow
            .flatMapMerge(concurrency = 2) { id -> createSubFlow(id) }
            .collect { item ->
                val elapsed = System.currentTimeMillis() - mergeStart
                println("  [$elapsed ms] Merge: $item")
            }
        
        println()
        
        // flatMapLatest - Switch to latest
        println("3. flatMapLatest (switch to latest):")
        flow {
            repeat(4) { i ->
                emit(i + 1)
                delay(200) // Emit faster than subflows complete
            }
        }
            .flatMapLatest { id -> 
                flow {
                    repeat(5) { i ->
                        emit("Latest-$id-$i")
                        delay(150)
                    }
                }
            }
            .collect { item ->
                println("  Latest: $item")
            }
        
        println("FlatMap strategies completed\n")
    }
    
    fun demonstrateConcurrencyControl() = runBlocking {
        println("=== Concurrency Control in FlatMap ===")
        
        // Controlled concurrency for resource management
        println("1. Database connection pool simulation:")
        
        data class DatabaseQuery(val id: Int, val query: String)
        
        suspend fun executeQuery(query: DatabaseQuery): Flow<String> = flow {
            println("    Starting query ${query.id}: ${query.query}")
            delay(Random.nextLong(200, 500)) // Simulate query execution
            
            repeat(3) { i ->
                emit("Query-${query.id}-Result-$i")
                delay(50)
            }
            
            println("    Completed query ${query.id}")
        }
        
        val queries = flow {
            repeat(8) { i ->
                val query = DatabaseQuery(i + 1, "SELECT * FROM table_$i")
                emit(query)
                delay(100) // Queries arrive every 100ms
            }
        }
        
        println("   With concurrency limit (max 2 concurrent queries):")
        queries
            .flatMapMerge(concurrency = 2) { query ->
                executeQuery(query)
            }
            .collect { result ->
                println("  Result: $result")
            }
        
        println()
        
        // Dynamic concurrency adjustment
        println("2. Dynamic concurrency based on load:")
        
        class AdaptiveConcurrencyController {
            private var currentConcurrency = 1
            private var successCount = 0
            private var errorCount = 0
            
            fun getCurrentConcurrency(): Int = currentConcurrency
            
            fun onSuccess() {
                successCount++
                if (successCount > 5 && currentConcurrency < 4) {
                    currentConcurrency++
                    println("    Increased concurrency to $currentConcurrency")
                    successCount = 0
                }
            }
            
            fun onError() {
                errorCount++
                if (errorCount > 2 && currentConcurrency > 1) {
                    currentConcurrency--
                    println("    Decreased concurrency to $currentConcurrency")
                    errorCount = 0
                }
            }
        }
        
        val controller = AdaptiveConcurrencyController()
        
        suspend fun adaptiveProcess(id: Int): Flow<String> = flow {
            try {
                // Simulate varying load and occasional failures
                if (Random.nextDouble() < 0.2) {
                    throw RuntimeException("Process $id failed")
                }
                
                delay(Random.nextLong(50, 200))
                emit("Process-$id-Success")
                controller.onSuccess()
            } catch (e: Exception) {
                controller.onError()
                throw e
            }
        }
        
        flow {
            repeat(20) { i ->
                emit(i + 1)
                delay(80)
            }
        }
            .flatMapMerge(concurrency = controller.getCurrentConcurrency()) { id ->
                adaptiveProcess(id).catch { emit("Process-$id-Failed") }
            }
            .collect { result ->
                println("  Adaptive result: $result")
            }
        
        println("Concurrency control completed\n")
    }
    
    fun demonstrateNestedFlowHandling() = runBlocking {
        println("=== Nested Flow Handling ===")
        
        // Complex nested structure
        println("1. Nested flow processing:")
        
        data class Department(val name: String, val employees: List<String>)
        data class Company(val name: String, val departments: List<Department>)
        
        val companies = flowOf(
            Company("TechCorp", listOf(
                Department("Engineering", listOf("Alice", "Bob", "Carol")),
                Department("Marketing", listOf("David", "Eve"))
            )),
            Company("DataInc", listOf(
                Department("Analytics", listOf("Frank", "Grace")),
                Department("Sales", listOf("Henry", "Iris", "Jack"))
            ))
        )
        
        // Flatten nested structure
        companies
            .flatMapConcat { company ->
                company.departments.asFlow()
                    .flatMapConcat { department ->
                        department.employees.asFlow()
                            .map { employee -> 
                                "${company.name} -> ${department.name} -> $employee" 
                            }
                    }
            }
            .collect { employee ->
                println("  Employee path: $employee")
            }
        
        println()
        
        // Async nested processing
        println("2. Async nested processing with error handling:")
        
        data class ProcessingTask(val batchId: Int, val items: List<String>)
        
        suspend fun processItem(batchId: Int, item: String): Flow<String> = flow {
            // Simulate processing with potential failures
            if (item.contains("error")) {
                throw RuntimeException("Processing failed for $item")
            }
            
            delay(Random.nextLong(50, 150))
            emit("Processed: Batch-$batchId-$item")
        }
        
        val processingTasks = flowOf(
            ProcessingTask(1, listOf("item1", "item2", "error_item", "item3")),
            ProcessingTask(2, listOf("item4", "item5", "item6")),
            ProcessingTask(3, listOf("item7", "error_item2", "item8"))
        )
        
        processingTasks
            .flatMapMerge(concurrency = 2) { task ->
                task.items.asFlow()
                    .flatMapMerge(concurrency = 3) { item ->
                        processItem(task.batchId, item)
                            .catch { exception ->
                                emit("Error: Batch-${task.batchId}-$item failed: ${exception.message}")
                            }
                    }
            }
            .collect { result ->
                println("  Processing result: $result")
            }
        
        println("Nested flow handling completed\n")
    }
}

/**
 * Custom combination operators and patterns
 */
class CustomCombinationOperators {
    
    fun demonstrateCustomOperators() = runBlocking {
        println("=== Custom Combination Operators ===")
        
        // Custom operator: combineWith timeout
        suspend fun <T, R, V> Flow<T>.combineWithTimeout(
            other: Flow<R>,
            timeoutMs: Long,
            transform: (T?, R?) -> V
        ): Flow<V> = channelFlow {
            var latestT: T? = null
            var latestR: R? = null
            var timeoutJob: Job? = null
            
            val sendCombined = {
                send(transform(latestT, latestR))
                timeoutJob?.cancel()
                timeoutJob = launch {
                    delay(timeoutMs)
                    send(transform(latestT, latestR))
                }
            }
            
            launch {
                collect { value ->
                    latestT = value
                    sendCombined()
                }
            }
            
            launch {
                other.collect { value ->
                    latestR = value
                    sendCombined()
                }
            }
        }
        
        println("1. Combine with timeout:")
        
        val fastSource = flow {
            repeat(5) { i ->
                emit("Fast-$i")
                delay(100)
            }
        }
        
        val slowSource = flow {
            repeat(3) { i ->
                emit("Slow-$i")
                delay(400)
            }
        }
        
        fastSource
            .combineWithTimeout(slowSource, timeoutMs = 200) { fast, slow ->
                "Combined: $fast + ${slow ?: "timeout"}"
            }
            .take(8)
            .collect { result ->
                println("  Timeout combined: $result")
            }
        
        println()
        
        // Custom operator: weighted merge
        suspend fun <T> Flow<T>.weightedMergeWith(
            other: Flow<T>,
            weight: Double // 0.0 = only other, 1.0 = only this
        ): Flow<T> = channelFlow {
            var thisCount = 0
            var otherCount = 0
            val thisQueue = mutableListOf<T>()
            val otherQueue = mutableListOf<T>()
            
            suspend fun sendNext() {
                val totalCount = thisCount + otherCount
                val expectedThisRatio = if (totalCount > 0) thisCount.toDouble() / totalCount else 0.0
                
                when {
                    thisQueue.isNotEmpty() && (otherQueue.isEmpty() || expectedThisRatio < weight) -> {
                        send(thisQueue.removeAt(0))
                    }
                    otherQueue.isNotEmpty() -> {
                        send(otherQueue.removeAt(0))
                    }
                }
            }
            
            launch {
                collect { value ->
                    thisCount++
                    thisQueue.add(value)
                    sendNext()
                }
            }
            
            launch {
                other.collect { value ->
                    otherCount++
                    otherQueue.add(value)
                    sendNext()
                }
            }
        }
        
        println("2. Weighted merge (70% primary, 30% secondary):")
        
        val primaryFlow = flow {
            repeat(7) { i ->
                emit("Primary-$i")
                delay(100)
            }
        }
        
        val secondaryFlow = flow {
            repeat(7) { i ->
                emit("Secondary-$i")
                delay(120)
            }
        }
        
        primaryFlow
            .weightedMergeWith(secondaryFlow, weight = 0.7)
            .take(10)
            .collect { result ->
                println("  Weighted: $result")
            }
        
        println("Custom operators completed\n")
    }
    
    fun demonstrateAdvancedComposition() = runBlocking {
        println("=== Advanced Flow Composition ===")
        
        // Multi-stage data pipeline with different combination strategies
        println("1. Multi-stage data pipeline:")
        
        data class RawData(val id: Int, val value: Double)
        data class EnrichedData(val id: Int, val value: Double, val metadata: String)
        data class ProcessedData(val id: Int, val result: Double, val confidence: Double)
        
        // Stage 1: Raw data sources
        val sensor1 = flow {
            repeat(5) { i ->
                emit(RawData(i, Random.nextDouble(0.0, 100.0)))
                delay(200)
            }
        }
        
        val sensor2 = flow {
            repeat(5) { i ->
                emit(RawData(i, Random.nextDouble(50.0, 150.0)))
                delay(250)
            }
        }
        
        // Stage 2: Enrichment service
        val enrichmentService = flow {
            repeat(5) { i ->
                emit("Enrichment-$i")
                delay(180)
            }
        }
        
        // Stage 3: Merge and enrich
        val enrichedData = merge(sensor1, sensor2)
            .zip(enrichmentService) { data, enrichment ->
                EnrichedData(data.id, data.value, enrichment)
            }
        
        // Stage 4: Processing with multiple algorithms
        val algorithm1 = enrichedData.map { data ->
            ProcessedData(data.id, data.value * 1.1, 0.8)
        }
        
        val algorithm2 = enrichedData.map { data ->
            ProcessedData(data.id, data.value * 0.9, 0.9)
        }
        
        // Stage 5: Combine results with confidence weighting
        algorithm1.combine(algorithm2) { result1, result2 ->
            val weightedResult = (result1.result * result1.confidence + result2.result * result2.confidence) /
                               (result1.confidence + result2.confidence)
            val combinedConfidence = (result1.confidence + result2.confidence) / 2
            
            ProcessedData(result1.id, weightedResult, combinedConfidence)
        }.collect { finalResult ->
            println("  Final result: ID=${finalResult.id}, " +
                    "Value=${String.format("%.2f", finalResult.result)}, " +
                    "Confidence=${String.format("%.2f", finalResult.confidence)}")
        }
        
        println()
        
        // Event-driven coordination pattern
        println("2. Event-driven coordination:")
        
        data class CoordinationEvent(val type: String, val data: String)
        
        val eventBus = flow {
            val events = listOf(
                CoordinationEvent("start", "process1"),
                CoordinationEvent("data", "item1"),
                CoordinationEvent("data", "item2"),
                CoordinationEvent("control", "pause"),
                CoordinationEvent("data", "item3"),
                CoordinationEvent("control", "resume"),
                CoordinationEvent("data", "item4"),
                CoordinationEvent("stop", "process1")
            )
            
            events.forEach { event ->
                emit(event)
                delay(200)
            }
        }
        
        // Split event bus into different event types
        val controlEvents = eventBus.filter { it.type == "control" }
        val dataEvents = eventBus.filter { it.type == "data" }
        val lifecycleEvents = eventBus.filter { it.type in listOf("start", "stop") }
        
        // Coordinate processing based on control events
        var isProcessing = true
        
        controlEvents
            .onEach { event ->
                isProcessing = when (event.data) {
                    "pause" -> {
                        println("  Processing paused")
                        false
                    }
                    "resume" -> {
                        println("  Processing resumed")
                        true
                    }
                    else -> isProcessing
                }
            }
            .combine(dataEvents) { _, dataEvent ->
                if (isProcessing) {
                    "Processing: ${dataEvent.data}"
                } else {
                    "Buffered: ${dataEvent.data}"
                }
            }
            .collect { result ->
                println("  Coordination result: $result")
            }
        
        println("Advanced composition completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic combination operations
        BasicFlowCombination().demonstrateMerge()
        BasicFlowCombination().demonstrateZip()
        BasicFlowCombination().demonstrateCombine()
        
        // Advanced combination patterns
        AdvancedFlowCombination().demonstrateConditionalCombination()
        AdvancedFlowCombination().demonstrateRateLimitedCombination()
        AdvancedFlowCombination().demonstrateComplexFanInFanOut()
        
        // Flow flattening strategies
        FlowFlattening().demonstrateFlatMapStrategies()
        FlowFlattening().demonstrateConcurrencyControl()
        FlowFlattening().demonstrateNestedFlowHandling()
        
        // Custom combination operators
        CustomCombinationOperators().demonstrateCustomOperators()
        CustomCombinationOperators().demonstrateAdvancedComposition()
        
        println("=== Flow Combining Summary ===")
        println("✅ Basic Combination:")
        println("   - merge: Union of multiple flows")
        println("   - zip: Synchronized pairing of values")
        println("   - combine: Latest values from all sources")
        println("   - Different semantics for different use cases")
        println()
        println("✅ Advanced Patterns:")
        println("   - Conditional combination based on runtime criteria")
        println("   - Rate-limited and throttled combinations")
        println("   - Fan-out/fan-in patterns for parallel processing")
        println("   - Multi-stage aggregation pipelines")
        println()
        println("✅ Flow Flattening:")
        println("   - flatMapConcat: Sequential processing")
        println("   - flatMapMerge: Concurrent with controlled concurrency")
        println("   - flatMapLatest: Switch to latest source")
        println("   - Nested flow handling with error isolation")
        println()
        println("✅ Concurrency Control:")
        println("   - Resource pool management with concurrency limits")
        println("   - Dynamic concurrency adjustment based on load")
        println("   - Error handling in concurrent operations")
        println("   - Backpressure management in combinations")
        println()
        println("✅ Custom Operators:")
        println("   - Timeout-aware combinations")
        println("   - Weighted merging strategies")
        println("   - Event-driven coordination patterns")
        println("   - Domain-specific combination logic")
    }
}