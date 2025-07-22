/**
 * # Flow Basics and Creation Patterns
 * 
 * ## Problem Description
 * Traditional collections and sequences are synchronous and finite, limiting their
 * use for asynchronous data streams, real-time events, and reactive programming.
 * Flows provide a cold, asynchronous sequence that can emit multiple values over time
 * with built-in cancellation and backpressure support.
 * 
 * ## Solution Approach
 * Flow fundamentals include:
 * - Understanding cold vs hot streams
 * - Various flow creation patterns
 * - Basic flow operations and transformations
 * - Flow lifecycle and execution model
 * - Terminal operations and collectors
 * 
 * ## Key Learning Points
 * - Flows are cold by default (executed on collection)
 * - Flows respect structured concurrency and cancellation
 * - Flow builders provide different creation patterns
 * - Terminal operators trigger flow execution
 * - Flows are sequential by default unless explicitly made concurrent
 * 
 * ## Performance Considerations
 * - Flow creation overhead: ~100-500ns per flow
 * - Emission overhead: ~10-100ns per emitted value
 * - Collection overhead: ~50-200ns per collected value
 * - Memory usage: flows are more memory-efficient than collecting to lists
 * 
 * ## Common Pitfalls
 * - Confusing cold vs hot flows
 * - Not understanding when flows execute
 * - Blocking operations in flow builders
 * - Missing exception handling in flows
 * - Creating infinite flows without proper cancellation
 * 
 * ## Real-World Applications
 * - Real-time data processing
 * - Event streaming and reactive programming
 * - Database query result streams
 * - Network response processing
 * - UI state management
 * 
 * ## Related Concepts
 * - Sequences - Synchronous equivalent
 * - Channels - Hot streams for communication
 * - StateFlow/SharedFlow - Hot flow variants
 */

package flow.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Flow creation patterns and builders
 * 
 * Flow Creation Hierarchy:
 * 
 * Flow Creation
 * ├── flow { } ────────── Custom flow builder
 * ├── flowOf() ───────── Fixed values
 * ├── asFlow() ───────── Convert collections/sequences
 * ├── channelFlow { } ── Channel-based concurrent flow
 * └── callbackFlow { } ── Callback-based integration
 * 
 * Flow Execution:
 * Creation ──> Intermediate Operations ──> Terminal Operation ──> Execution
 */
class FlowCreationPatterns {
    
    fun demonstrateBasicFlowCreation() = runBlocking {
        println("=== Basic Flow Creation ===")
        
        // 1. flow { } builder - most common
        println("1. flow { } builder:")
        val numbersFlow = flow {
            println("  Flow started")
            repeat(5) { i ->
                println("  Emitting $i")
                emit(i)
                delay(100) // Simulate async work
            }
            println("  Flow completed")
        }
        
        println("Flow created but not executed yet...")
        
        // Flow execution starts only when collected
        numbersFlow.collect { value ->
            println("  Collected: $value")
        }
        
        println()
        
        // 2. flowOf() - for fixed values
        println("2. flowOf() - fixed values:")
        flowOf("Apple", "Banana", "Cherry")
            .collect { fruit ->
                println("  Fruit: $fruit")
            }
        
        println()
        
        // 3. asFlow() - convert collections
        println("3. asFlow() - from collections:")
        listOf(10, 20, 30, 40)
            .asFlow()
            .collect { number ->
                println("  Number: $number")
            }
        
        println()
        
        // 4. Empty and single value flows
        println("4. Special flows:")
        
        println("  Empty flow:")
        emptyFlow<String>()
            .collect { println("  This won't print: $it") }
        
        println("  Single value flow:")
        flowOf("Single value")
            .collect { println("  Single: $it") }
        
        println("Flow creation patterns completed\n")
    }
    
    fun demonstrateFlowFromSuspendFunction() = runBlocking {
        println("=== Flow from Suspend Functions ===")
        
        suspend fun fetchUserData(userId: Int): String {
            delay(Random.nextLong(100, 300))
            return "User-$userId-Data"
        }
        
        // Create flow from suspend function calls
        val userDataFlow = flow {
            val userIds = listOf(1, 2, 3, 4, 5)
            for (userId in userIds) {
                val userData = fetchUserData(userId)
                emit(userData)
            }
        }
        
        println("Fetching user data...")
        userDataFlow.collect { userData ->
            println("  Received: $userData")
        }
        
        println("Suspend function flow completed\n")
    }
    
    fun demonstrateFlowFromCallbacks() = runBlocking {
        println("=== Flow from Callbacks ===")
        
        // Simulate callback-based API
        interface EventListener {
            fun onEvent(event: String)
            fun onError(error: Throwable)
            fun onComplete()
        }
        
        class EventSource {
            private var listener: EventListener? = null
            
            fun setListener(listener: EventListener) {
                this.listener = listener
            }
            
            fun start() {
                // Simulate async events
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repeat(5) { i ->
                            delay(200)
                            listener?.onEvent("Event-$i")
                        }
                        listener?.onComplete()
                    } catch (e: Exception) {
                        listener?.onError(e)
                    }
                }
            }
            
            fun stop() {
                listener = null
            }
        }
        
        // Convert callback API to Flow using callbackFlow
        fun eventSourceFlow(): Flow<String> = callbackFlow {
            val eventSource = EventSource()
            
            val listener = object : EventListener {
                override fun onEvent(event: String) {
                    trySend(event)
                }
                
                override fun onError(error: Throwable) {
                    close(error)
                }
                
                override fun onComplete() {
                    close()
                }
            }
            
            eventSource.setListener(listener)
            eventSource.start()
            
            // Cleanup when flow is cancelled
            awaitClose {
                eventSource.stop()
                println("  Event source stopped")
            }
        }
        
        // Collect events from callback-based API
        eventSourceFlow().collect { event ->
            println("  Event received: $event")
        }
        
        println("Callback flow completed\n")
    }
    
    fun demonstrateChannelFlow() = runBlocking {
        println("=== Channel Flow ===")
        
        // channelFlow allows concurrent emission
        val concurrentFlow = channelFlow {
            // Launch multiple coroutines to emit concurrently
            launch {
                repeat(3) { i ->
                    delay(150)
                    send("Producer-A-$i")
                }
            }
            
            launch {
                repeat(3) { i ->
                    delay(100)
                    send("Producer-B-$i")
                }
            }
            
            launch {
                repeat(3) { i ->
                    delay(200)
                    send("Producer-C-$i")
                }
            }
        }
        
        println("Collecting from concurrent producers:")
        concurrentFlow.collect { value ->
            println("  Received: $value")
        }
        
        println("Channel flow completed\n")
    }
}

/**
 * Flow operators and transformations
 */
class FlowOperators {
    
    fun demonstrateTransformOperators() = runBlocking {
        println("=== Transform Operators ===")
        
        val sourceFlow = flowOf(1, 2, 3, 4, 5)
        
        // map - transform each value
        println("1. map - transform values:")
        sourceFlow
            .map { it * it }
            .collect { println("  Squared: $it") }
        
        println()
        
        // filter - keep values that match predicate
        println("2. filter - even numbers only:")
        sourceFlow
            .filter { it % 2 == 0 }
            .collect { println("  Even: $it") }
        
        println()
        
        // transform - custom transformation with multiple emissions
        println("3. transform - custom transformation:")
        sourceFlow
            .transform { value ->
                emit("Start-$value")
                emit("End-$value")
            }
            .collect { println("  Transformed: $it") }
        
        println()
        
        // take - limit number of items
        println("4. take - first 3 items:")
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .take(3)
            .collect { println("  Taken: $it") }
        
        println()
        
        // drop - skip first N items
        println("5. drop - skip first 2 items:")
        sourceFlow
            .drop(2)
            .collect { println("  After drop: $it") }
        
        println("Transform operators completed\n")
    }
    
    fun demonstrateTimeBasedOperators() = runBlocking {
        println("=== Time-Based Operators ===")
        
        // debounce - emit only after silence period
        println("1. debounce - emit after silence:")
        flow {
            emit("A")
            delay(50)
            emit("B")
            delay(50)
            emit("C")
            delay(200) // Longer delay
            emit("D")
            delay(300) // Longer delay
        }
            .debounce(100) // Only emit if no new value for 100ms
            .collect { println("  Debounced: $it") }
        
        println()
        
        // sample/throttle - emit latest value at regular intervals
        println("2. sample - emit at intervals:")
        flow {
            repeat(10) { i ->
                emit("Item-$i")
                delay(80)
            }
        }
            .sample(200) // Emit latest value every 200ms
            .collect { println("  Sampled: $it") }
        
        println()
        
        // distinctUntilChanged - emit only when value changes
        println("3. distinctUntilChanged - emit only on change:")
        flowOf(1, 1, 2, 2, 2, 3, 3, 1, 1)
            .distinctUntilChanged()
            .collect { println("  Distinct: $it") }
        
        println("Time-based operators completed\n")
    }
    
    fun demonstrateFlowCombination() = runBlocking {
        println("=== Flow Combination ===")
        
        val flow1 = flow {
            repeat(5) { i ->
                emit("A$i")
                delay(100)
            }
        }
        
        val flow2 = flow {
            repeat(3) { i ->
                emit("B$i")
                delay(150)
            }
        }
        
        // zip - combine values pairwise
        println("1. zip - combine pairwise:")
        flow1.zip(flow2) { a, b -> "$a+$b" }
            .collect { println("  Zipped: $it") }
        
        println()
        
        // combine - combine latest values
        println("2. combine - latest values:")
        flow {
            repeat(4) { i ->
                emit("X$i")
                delay(120)
            }
        }.combine(
            flow {
                repeat(3) { i ->
                    emit("Y$i")
                    delay(180)
                }
            }
        ) { x, y -> "$x+$y" }
            .collect { println("  Combined: $it") }
        
        println()
        
        // merge - merge multiple flows
        println("3. merge - merge flows:")
        merge(
            flowOf("Apple", "Banana").onEach { delay(100) },
            flowOf("Cat", "Dog").onEach { delay(150) },
            flowOf("1", "2", "3").onEach { delay(80) }
        ).collect { println("  Merged: $it") }
        
        println("Flow combination completed\n")
    }
    
    fun demonstrateFlowFlattening() = runBlocking {
        println("=== Flow Flattening ===")
        
        fun createSubFlow(id: Int): Flow<String> = flow {
            repeat(3) { i ->
                emit("SubFlow-$id-Item-$i")
                delay(Random.nextLong(50, 150))
            }
        }
        
        val mainFlow = flowOf(1, 2, 3)
        
        // flatMapConcat - flatten sequentially
        println("1. flatMapConcat - sequential flattening:")
        mainFlow
            .flatMapConcat { id -> createSubFlow(id) }
            .collect { println("  Concat: $it") }
        
        println()
        
        // flatMapMerge - flatten concurrently
        println("2. flatMapMerge - concurrent flattening:")
        mainFlow
            .flatMapMerge { id -> createSubFlow(id) }
            .collect { println("  Merge: $it") }
        
        println()
        
        // flatMapLatest - flatten with cancellation of previous
        println("3. flatMapLatest - cancel previous:")
        flow {
            repeat(3) { i ->
                emit(i)
                delay(200)
            }
        }
            .flatMapLatest { id ->
                flow {
                    repeat(5) { j ->
                        emit("Latest-$id-$j")
                        delay(100)
                    }
                }
            }
            .collect { println("  Latest: $it") }
        
        println("Flow flattening completed\n")
    }
}

/**
 * Flow execution context and dispatchers
 */
class FlowContext {
    
    fun demonstrateFlowContext() = runBlocking {
        println("=== Flow Context and Dispatchers ===")
        
        // flowOn - change execution context
        println("1. flowOn - change dispatcher:")
        flow {
            repeat(5) { i ->
                println("  Emitting $i on ${Thread.currentThread().name}")
                emit(i)
                delay(50)
            }
        }
            .flowOn(Dispatchers.IO) // Upstream operations run on IO dispatcher
            .map { 
                println("  Transforming $it on ${Thread.currentThread().name}")
                it * 2 
            }
            .flowOn(Dispatchers.Default) // map runs on Default dispatcher
            .collect { 
                println("  Collected $it on ${Thread.currentThread().name}")
            }
        
        println()
        
        // Multiple flowOn operators
        println("2. Multiple flowOn operators:")
        flowOf(1, 2, 3)
            .map { 
                println("  Map1 on ${Thread.currentThread().name}")
                it * 2 
            }
            .flowOn(Dispatchers.IO)
            .map { 
                println("  Map2 on ${Thread.currentThread().name}")
                it + 10 
            }
            .flowOn(Dispatchers.Default)
            .collect { 
                println("  Final collection on ${Thread.currentThread().name}: $it")
            }
        
        println("Flow context demo completed\n")
    }
    
    fun demonstrateFlowCancellation() = runBlocking {
        println("=== Flow Cancellation ===")
        
        // Cancellable flow
        val cancellableFlow = flow {
            repeat(10) { i ->
                println("  Emitting $i")
                emit(i)
                delay(200)
            }
        }
        
        // Cancel flow collection
        println("1. Flow cancellation:")
        val job = launch {
            cancellableFlow.collect { value ->
                println("  Collected: $value")
            }
        }
        
        delay(600) // Let it collect a few values
        println("Cancelling flow...")
        job.cancel()
        job.join()
        
        println()
        
        // Timeout with flow
        println("2. Flow with timeout:")
        try {
            withTimeout(500) {
                flow {
                    repeat(5) { i ->
                        emit(i)
                        delay(200)
                    }
                }.collect { 
                    println("  Timed collection: $it")
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("  Flow collection timed out")
        }
        
        println("Flow cancellation demo completed\n")
    }
    
    fun demonstrateFlowBuffering() = runBlocking {
        println("=== Flow Buffering ===")
        
        fun createSlowFlow() = flow {
            repeat(5) { i ->
                delay(100) // Slow producer
                emit(i)
            }
        }
        
        fun measureCollectionTime(flow: Flow<Int>) = measureTimeMillis {
            flow.collect { 
                delay(200) // Slow consumer
                println("  Processed: $it")
            }
        }
        
        // Without buffering
        println("1. Without buffering:")
        val timeWithoutBuffer = measureCollectionTime(createSlowFlow())
        println("  Time without buffer: ${timeWithoutBuffer}ms")
        
        println()
        
        // With buffering
        println("2. With buffering:")
        val timeWithBuffer = measureCollectionTime(
            createSlowFlow().buffer()
        )
        println("  Time with buffer: ${timeWithBuffer}ms")
        
        println()
        
        // Custom buffer size
        println("3. Custom buffer size:")
        createSlowFlow()
            .buffer(capacity = 2)
            .collect { 
                delay(150)
                println("  Buffered processed: $it")
            }
        
        println("Flow buffering demo completed\n")
    }
}

/**
 * Flow exception handling
 */
class FlowExceptionHandling {
    
    fun demonstrateBasicExceptionHandling() = runBlocking {
        println("=== Basic Flow Exception Handling ===")
        
        // Flow that throws exception
        val flakyFlow = flow {
            repeat(5) { i ->
                if (i == 3) {
                    throw RuntimeException("Error at index $i")
                }
                emit(i)
            }
        }
        
        // try-catch around collect
        println("1. try-catch around collect:")
        try {
            flakyFlow.collect { value ->
                println("  Collected: $value")
            }
        } catch (e: Exception) {
            println("  Caught exception: ${e.message}")
        }
        
        println()
        
        // catch operator
        println("2. catch operator:")
        flakyFlow
            .catch { exception ->
                println("  Caught in catch operator: ${exception.message}")
                emit(-1) // Emit fallback value
            }
            .collect { value ->
                println("  Final value: $value")
            }
        
        println("Exception handling demo completed\n")
    }
    
    fun demonstrateAdvancedExceptionHandling() = runBlocking {
        println("=== Advanced Exception Handling ===")
        
        // Exception in different operators
        println("1. Exception in different operators:")
        
        flow {
            emit(1)
            emit(2)
            emit(3)
        }
            .map { value ->
                if (value == 2) {
                    throw IllegalStateException("Map failed for $value")
                }
                value * 10
            }
            .catch { exception ->
                println("  Caught map exception: ${exception.message}")
                emit(999) // Recovery value
            }
            .collect { println("  Result: $it") }
        
        println()
        
        // Retry on exception
        println("2. Retry on exception:")
        var attempts = 0
        
        flow {
            attempts++
            println("  Attempt $attempts")
            if (attempts < 3) {
                throw RuntimeException("Attempt $attempts failed")
            }
            emit("Success on attempt $attempts")
        }
            .retry(retries = 3) { exception ->
                println("  Retrying due to: ${exception.message}")
                delay(100) // Wait before retry
                true
            }
            .catch { exception ->
                println("  Final failure: ${exception.message}")
                emit("Fallback value")
            }
            .collect { println("  Final result: $it") }
        
        println()
        
        // Exception transparency
        println("3. Exception transparency:")
        flow {
            emit(1)
            emit(2)
            throw RuntimeException("Upstream exception")
        }
            .map { it * 2 }
            .onEach { println("  Processing: $it") }
            .catch { exception ->
                // catch only handles upstream exceptions
                println("  Handled upstream exception: ${exception.message}")
            }
            .onEach { 
                if (it == 4) {
                    throw RuntimeException("Downstream exception")
                }
            }
            .catch { exception ->
                // This won't catch the downstream exception
                println("  This won't be called: ${exception.message}")
            }
            .collect { 
                try {
                    println("  Final: $it")
                } catch (e: Exception) {
                    println("  Caught in collect: ${e.message}")
                }
            }
        
        println("Advanced exception handling completed\n")
    }
    
    fun demonstrateFlowCompletion() = runBlocking {
        println("=== Flow Completion ===")
        
        // onCompletion operator
        println("1. onCompletion operator:")
        flow {
            repeat(3) { i ->
                emit(i)
                if (i == 1) {
                    throw RuntimeException("Error in flow")
                }
            }
        }
            .onCompletion { exception ->
                if (exception != null) {
                    println("  Flow completed with exception: ${exception.message}")
                } else {
                    println("  Flow completed successfully")
                }
            }
            .catch { exception ->
                println("  Handled exception: ${exception.message}")
            }
            .collect { value ->
                println("  Value: $value")
            }
        
        println()
        
        // finally block equivalent
        println("2. finally block pattern:")
        try {
            flowOf(1, 2, 3)
                .onEach { 
                    if (it == 2) throw RuntimeException("Middle error")
                    println("  Processing: $it")
                }
                .collect()
        } catch (e: Exception) {
            println("  Exception caught: ${e.message}")
        } finally {
            println("  Cleanup in finally block")
        }
        
        println("Flow completion demo completed\n")
    }
}

/**
 * Terminal operators and collectors
 */
class FlowTerminalOperators {
    
    fun demonstrateCollectionOperators() = runBlocking {
        println("=== Terminal Collection Operators ===")
        
        val sourceFlow = flowOf(1, 2, 3, 4, 5)
        
        // collect - basic collection
        println("1. collect - basic collection:")
        sourceFlow.collect { value ->
            println("  Collected: $value")
        }
        
        println()
        
        // toList - collect to list
        println("2. toList - collect to list:")
        val list = sourceFlow.toList()
        println("  List: $list")
        
        println()
        
        // toSet - collect to set
        println("3. toSet - collect to set:")
        val set = flowOf(1, 2, 2, 3, 3, 4).toSet()
        println("  Set: $set")
        
        println()
        
        // first and last
        println("4. first and last:")
        val first = sourceFlow.first()
        val last = sourceFlow.last()
        println("  First: $first, Last: $last")
        
        println()
        
        // single - expect exactly one element
        println("5. single - exactly one element:")
        try {
            val single = flowOf(42).single()
            println("  Single: $single")
            
            // This will throw exception
            flowOf(1, 2).single()
        } catch (e: Exception) {
            println("  Expected exception for multiple elements: ${e.message}")
        }
        
        println("Collection operators completed\n")
    }
    
    fun demonstrateReductionOperators() = runBlocking {
        println("=== Reduction Operators ===")
        
        val numbersFlow = flowOf(1, 2, 3, 4, 5)
        
        // reduce - accumulate values
        println("1. reduce - sum all values:")
        val sum = numbersFlow.reduce { accumulator, value ->
            accumulator + value
        }
        println("  Sum: $sum")
        
        println()
        
        // fold - like reduce but with initial value
        println("2. fold - with initial value:")
        val product = numbersFlow.fold(1) { accumulator, value ->
            accumulator * value
        }
        println("  Product: $product")
        
        println()
        
        // count - count elements
        println("3. count - count elements:")
        val count = numbersFlow.count()
        val evenCount = numbersFlow.count { it % 2 == 0 }
        println("  Total count: $count, Even count: $evenCount")
        
        println("Reduction operators completed\n")
    }
    
    fun demonstrateSearchOperators() = runBlocking {
        println("=== Search Operators ===")
        
        val numbersFlow = flowOf(1, 3, 5, 8, 12, 15)
        
        // any - check if any element matches predicate
        println("1. any - check conditions:")
        val hasEven = numbersFlow.any { it % 2 == 0 }
        val hasLarge = numbersFlow.any { it > 20 }
        println("  Has even: $hasEven, Has > 20: $hasLarge")
        
        println()
        
        // all - check if all elements match predicate
        println("2. all - check all conditions:")
        val allPositive = numbersFlow.all { it > 0 }
        val allEven = numbersFlow.all { it % 2 == 0 }
        println("  All positive: $allPositive, All even: $allEven")
        
        println()
        
        // none - check if no elements match predicate
        println("3. none - check none conditions:")
        val noneNegative = numbersFlow.none { it < 0 }
        val noneZero = numbersFlow.none { it == 0 }
        println("  None negative: $noneNegative, None zero: $noneZero")
        
        println("Search operators completed\n")
    }
    
    fun demonstrateCustomCollectors() = runBlocking {
        println("=== Custom Collectors ===")
        
        // Custom collector using collect
        suspend fun <T> Flow<T>.collectToString(separator: String = ", "): String {
            val result = StringBuilder()
            var first = true
            
            collect { value ->
                if (first) {
                    first = false
                } else {
                    result.append(separator)
                }
                result.append(value.toString())
            }
            
            return result.toString()
        }
        
        // Custom collector with state
        suspend fun <T> Flow<T>.collectWithStats(): Pair<List<T>, Int> {
            val items = mutableListOf<T>()
            var count = 0
            
            collect { value ->
                items.add(value)
                count++
            }
            
            return Pair(items, count)
        }
        
        val dataFlow = flowOf("Apple", "Banana", "Cherry", "Date")
        
        println("1. Custom string collector:")
        val joinedString = dataFlow.collectToString(" | ")
        println("  Joined: $joinedString")
        
        println()
        
        println("2. Custom stats collector:")
        val (items, count) = dataFlow.collectWithStats()
        println("  Items: $items")
        println("  Count: $count")
        
        println("Custom collectors completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Flow creation patterns
        FlowCreationPatterns().demonstrateBasicFlowCreation()
        FlowCreationPatterns().demonstrateFlowFromSuspendFunction()
        FlowCreationPatterns().demonstrateFlowFromCallbacks()
        FlowCreationPatterns().demonstrateChannelFlow()
        
        // Flow operators
        FlowOperators().demonstrateTransformOperators()
        FlowOperators().demonstrateTimeBasedOperators()
        FlowOperators().demonstrateFlowCombination()
        FlowOperators().demonstrateFlowFlattening()
        
        // Flow context and execution
        FlowContext().demonstrateFlowContext()
        FlowContext().demonstrateFlowCancellation()
        FlowContext().demonstrateFlowBuffering()
        
        // Exception handling
        FlowExceptionHandling().demonstrateBasicExceptionHandling()
        FlowExceptionHandling().demonstrateAdvancedExceptionHandling()
        FlowExceptionHandling().demonstrateFlowCompletion()
        
        // Terminal operators
        FlowTerminalOperators().demonstrateCollectionOperators()
        FlowTerminalOperators().demonstrateReductionOperators()
        FlowTerminalOperators().demonstrateSearchOperators()
        FlowTerminalOperators().demonstrateCustomCollectors()
        
        println("=== Flow Basics Summary ===")
        println("✅ Flow Creation:")
        println("   - flow { } for custom flows")
        println("   - flowOf() for fixed values")
        println("   - asFlow() for collections")
        println("   - callbackFlow { } for callback integration")
        println("   - channelFlow { } for concurrent emission")
        println()
        println("✅ Flow Properties:")
        println("   - Cold streams (execute on collection)")
        println("   - Respect structured concurrency")
        println("   - Sequential by default")
        println("   - Cancellation support")
        println("   - Backpressure handling")
        println()
        println("✅ Core Operators:")
        println("   - Transform: map, filter, transform")
        println("   - Time-based: debounce, sample, distinctUntilChanged")
        println("   - Combine: zip, combine, merge")
        println("   - Flatten: flatMapConcat, flatMapMerge, flatMapLatest")
        println()
        println("✅ Execution Control:")
        println("   - flowOn() for context switching")
        println("   - buffer() for performance")
        println("   - Exception handling with catch")
        println("   - Completion handling with onCompletion")
        println()
        println("✅ Terminal Operations:")
        println("   - collect() for basic consumption")
        println("   - toList(), toSet() for collection")
        println("   - reduce(), fold() for aggregation")
        println("   - first(), last(), single() for specific values")
    }
}