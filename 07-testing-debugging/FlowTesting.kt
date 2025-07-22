/**
 * # Flow Testing Strategies and Patterns
 * 
 * ## Problem Description
 * Testing Flow operations presents unique challenges due to their asynchronous,
 * potentially infinite, and reactive nature. Flows can emit values over time,
 * handle backpressure, and have complex operator chains that need comprehensive
 * testing. Standard testing approaches often fall short when dealing with
 * time-sensitive operations, error propagation, and concurrent flow processing.
 * 
 * ## Solution Approach
 * Flow testing strategies include:
 * - Using test collectors and assertion helpers for flow verification
 * - Testing flow operators and transformation chains
 * - Mocking flow sources and testing reactive patterns
 * - Testing flow cancellation and exception handling
 * - Performance testing of flow operations and backpressure
 * 
 * ## Key Learning Points
 * - Flow.toList() and Flow.first() are useful for testing finite flows
 * - flowOf() and emptyFlow() create predictable test flows
 * - Turbine library provides advanced flow testing capabilities
 * - Hot flows (StateFlow/SharedFlow) require different testing approaches
 * - Flow cancellation and exception propagation must be thoroughly tested
 * 
 * ## Performance Considerations
 * - Test flows should complete quickly using virtual time
 * - Infinite flows require careful timeout and cancellation testing
 * - Large flow collections can impact test memory usage
 * - Flow operator fusion affects performance testing accuracy
 * - Hot flow testing requires proper lifecycle management
 * 
 * ## Common Pitfalls
 * - Not testing flow completion and cancellation scenarios
 * - Forgetting to test error propagation through operator chains
 * - Using real delays instead of virtual time in flow tests
 * - Not testing hot flow subscription timing
 * - Missing edge cases like empty flows or single-item flows
 * 
 * ## Real-World Applications
 * - Testing repository layers with reactive data sources
 * - UI state management testing with StateFlow/SharedFlow
 * - Testing data transformation pipelines
 * - Event stream processing validation
 * - API response flow testing and error handling
 * 
 * ## Related Concepts
 * - Reactive testing patterns (RxJava TestObserver)
 * - Property-based testing with flows
 * - Integration testing with real data sources
 * - Performance benchmarking of flow operations
 */

package testing.debugging.flows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic flow testing patterns and techniques
 * 
 * Flow Testing Architecture:
 * 
 * Test Flow Pipeline:
 * ├── Source Creation ──── flowOf(), emptyFlow(), flow { }
 * ├── Operator Chain ───── map, filter, transform operations
 * ├── Collection ──────── toList(), first(), collect { }
 * └── Assertions ──────── assertEquals, assertFailsWith
 * 
 * Testing Patterns:
 * Cold Flow: Create -> Transform -> Collect -> Assert
 * Hot Flow: Create -> Subscribe -> Emit -> Assert -> Cleanup
 */
class BasicFlowTestPatterns {
    
    // Example data classes for testing
    data class User(val id: Int, val name: String, val age: Int)
    data class Product(val id: String, val name: String, val price: Double)
    
    // Service class with flow operations to test
    class DataProcessingService {
        fun processNumbers(numbers: Flow<Int>): Flow<String> = flow {
            numbers.collect { number ->
                delay(50) // Simulate processing time
                emit("Processed: $number")
            }
        }
        
        fun filterAndTransformUsers(users: Flow<User>): Flow<String> = 
            users
                .filter { it.age >= 18 }
                .map { "${it.name} (${it.age})" }
                .map { it.uppercase() }
        
        fun aggregateProducts(products: Flow<Product>): Flow<Double> = flow {
            var runningTotal = 0.0
            products.collect { product ->
                runningTotal += product.price
                emit(runningTotal)
            }
        }
        
        fun retryableOperation(input: Flow<String>): Flow<String> = 
            input.map { value ->
                if (value.contains("error")) {
                    throw RuntimeException("Processing failed for: $value")
                }
                "Success: $value"
            }.retry(3) { exception ->
                delay(100)
                exception is RuntimeException
            }
    }
    
    @Test
    fun testBasicFlowCollection() = runTest {
        println("=== Testing Basic Flow Collection ===")
        
        // Create test flow
        val testFlow = flowOf(1, 2, 3, 4, 5)
        
        // Collect all values
        val results = testFlow.toList()
        
        // Assert results
        assertEquals(listOf(1, 2, 3, 4, 5), results)
        
        // Test flow count
        val count = testFlow.count()
        assertEquals(5, count)
        
        // Test first and last
        val first = testFlow.first()
        val last = testFlow.last()
        assertEquals(1, first)
        assertEquals(5, last)
        
        println("✅ Basic flow collection test passed")
    }
    
    @Test
    fun testFlowTransformations() = runTest {
        println("=== Testing Flow Transformations ===")
        
        val service = DataProcessingService()
        val inputNumbers = flowOf(1, 2, 3, 4, 5)
        
        // Test transformation
        val results = service.processNumbers(inputNumbers).toList()
        
        val expected = listOf(
            "Processed: 1",
            "Processed: 2", 
            "Processed: 3",
            "Processed: 4",
            "Processed: 5"
        )
        
        assertEquals(expected, results)
        
        // Test that processing takes expected time (virtual)
        val currentTime = currentTime
        assertTrue(currentTime >= 250, "Expected processing delay, time was ${currentTime}ms")
        
        println("✅ Flow transformations test passed")
    }
    
    @Test
    fun testFilterAndMapOperations() = runTest {
        println("=== Testing Filter and Map Operations ===")
        
        val service = DataProcessingService()
        val users = flowOf(
            User(1, "Alice", 25),
            User(2, "Bob", 17),    // Should be filtered out
            User(3, "Charlie", 30),
            User(4, "Diana", 16)   // Should be filtered out
        )
        
        val results = service.filterAndTransformUsers(users).toList()
        
        val expected = listOf(
            "ALICE (25)",
            "CHARLIE (30)"
        )
        
        assertEquals(expected, results)
        assertEquals(2, results.size)
        
        println("✅ Filter and map operations test passed")
    }
    
    @Test
    fun testFlowAggregation() = runTest {
        println("=== Testing Flow Aggregation ===")
        
        val service = DataProcessingService()
        val products = flowOf(
            Product("1", "Widget", 10.0),
            Product("2", "Gadget", 15.5),
            Product("3", "Tool", 7.25),
            Product("4", "Device", 22.75)
        )
        
        val runningTotals = service.aggregateProducts(products).toList()
        
        val expected = listOf(10.0, 25.5, 32.75, 55.5)
        assertEquals(expected, runningTotals)
        
        // Test final total
        val finalTotal = runningTotals.last()
        assertEquals(55.5, finalTotal)
        
        println("✅ Flow aggregation test passed")
    }
    
    @Test
    fun testEmptyFlow() = runTest {
        println("=== Testing Empty Flow ===")
        
        val service = DataProcessingService()
        val emptyNumbers = emptyFlow<Int>()
        
        val results = service.processNumbers(emptyNumbers).toList()
        
        assertTrue(results.isEmpty(), "Empty flow should produce empty results")
        
        // Test empty flow properties
        val count = emptyNumbers.count()
        assertEquals(0, count)
        
        val firstOrNull = emptyNumbers.firstOrNull()
        assertNull(firstOrNull)
        
        println("✅ Empty flow test passed")
    }
    
    @Test
    fun testSingleItemFlow() = runTest {
        println("=== Testing Single Item Flow ===")
        
        val service = DataProcessingService()
        val singleNumber = flowOf(42)
        
        val results = service.processNumbers(singleNumber).toList()
        
        assertEquals(listOf("Processed: 42"), results)
        
        // Test single flow properties
        val single = singleNumber.single()
        assertEquals(42, single)
        
        val first = singleNumber.first()
        val last = singleNumber.last()
        assertEquals(first, last)
        
        println("✅ Single item flow test passed")
    }
}

/**
 * Advanced flow testing with error handling and cancellation
 */
class AdvancedFlowTestPatterns {
    
    // Service with more complex flow operations
    class AdvancedFlowService {
        fun fetchDataWithRetry(ids: Flow<String>): Flow<String> = 
            ids.flatMapConcat { id ->
                flow {
                    delay(100)
                    when {
                        id.contains("error") -> throw RuntimeException("Fetch failed for $id")
                        id.contains("timeout") -> {
                            delay(5000) // Long delay to test timeout
                            emit("Data for $id")
                        }
                        else -> emit("Data for $id")
                    }
                }.retry(2) { exception ->
                    delay(50)
                    exception is RuntimeException && !exception.message!!.contains("timeout")
                }
            }
        
        fun mergeDataStreams(stream1: Flow<String>, stream2: Flow<String>): Flow<String> = 
            merge(stream1, stream2)
        
        fun processWithTimeout(data: Flow<String>, timeoutMs: Long): Flow<String> = 
            data.map { item ->
                withTimeout(timeoutMs) {
                    delay(100) // Simulate processing
                    "Processed: $item"
                }
            }
        
        fun batchProcessing(items: Flow<String>, batchSize: Int): Flow<List<String>> = 
            items
                .chunked(batchSize)
                .map { batch ->
                    delay(200) // Simulate batch processing
                    batch.map { "Batch[$it]" }
                }
    }
    
    @Test
    fun testFlowExceptionHandling() = runTest {
        println("=== Testing Flow Exception Handling ===")
        
        val service = AdvancedFlowService()
        val idsWithError = flowOf("id1", "error-id", "id2", "id3")
        
        // Test that error propagates
        val exception = assertFailsWith<RuntimeException> {
            service.fetchDataWithRetry(idsWithError).toList()
        }
        
        assertTrue(exception.message!!.contains("Fetch failed for error-id"))
        
        // Test successful processing with catch
        val results = service.fetchDataWithRetry(idsWithError)
            .catch { emit("Error handled") }
            .toList()
        
        assertTrue(results.contains("Data for id1"))
        assertTrue(results.contains("Error handled"))
        
        println("✅ Flow exception handling test passed")
    }
    
    @Test
    fun testFlowCancellation() = runTest {
        println("=== Testing Flow Cancellation ===")
        
        val service = AdvancedFlowService()
        val longRunningFlow = flow {
            repeat(100) { i ->
                delay(100)
                emit("Item $i")
            }
        }
        
        val results = mutableListOf<String>()
        var cancellationDetected = false
        
        val job = launch {
            try {
                service.processWithTimeout(longRunningFlow, 1000).collect { item ->
                    results.add(item)
                }
            } catch (e: CancellationException) {
                cancellationDetected = true
                throw e
            }
        }
        
        // Let it run for a bit
        advanceTimeBy(300)
        assertTrue(results.isNotEmpty(), "Should have collected some results")
        
        // Cancel the job
        job.cancel()
        job.join()
        
        assertTrue(job.isCancelled, "Job should be cancelled")
        
        // Note: cancellationDetected might be false if cancellation happens
        // during delay rather than in the collect block
        
        println("✅ Flow cancellation test passed")
    }
    
    @Test
    fun testFlowTimeoutHandling() = runTest {
        println("=== Testing Flow Timeout Handling ===")
        
        val service = AdvancedFlowService()
        val timeoutData = flowOf("normal", "timeout-data", "normal2")
        
        // Test timeout exception
        val exception = assertFailsWith<TimeoutCancellationException> {
            service.processWithTimeout(timeoutData, 50).toList()
        }
        
        assertNotNull(exception)
        
        // Test with sufficient timeout
        val successResults = service.processWithTimeout(
            flowOf("quick1", "quick2"), 
            1000
        ).toList()
        
        assertEquals(2, successResults.size)
        assertTrue(successResults.all { it.startsWith("Processed:") })
        
        println("✅ Flow timeout handling test passed")
    }
    
    @Test
    fun testConcurrentFlowMerging() = runTest {
        println("=== Testing Concurrent Flow Merging ===")
        
        val service = AdvancedFlowService()
        
        val stream1 = flow {
            delay(100)
            emit("Stream1-A")
            delay(100)
            emit("Stream1-B")
        }
        
        val stream2 = flow {
            delay(150)
            emit("Stream2-X")
            delay(100)
            emit("Stream2-Y")
        }
        
        val mergedResults = service.mergeDataStreams(stream1, stream2).toList()
        
        assertEquals(4, mergedResults.size)
        assertTrue(mergedResults.contains("Stream1-A"))
        assertTrue(mergedResults.contains("Stream1-B"))
        assertTrue(mergedResults.contains("Stream2-X"))
        assertTrue(mergedResults.contains("Stream2-Y"))
        
        // Note: Order is not guaranteed in merge, so we just check presence
        
        println("✅ Concurrent flow merging test passed")
    }
    
    @Test
    fun testBatchedFlowProcessing() = runTest {
        println("=== Testing Batched Flow Processing ===")
        
        val service = AdvancedFlowService()
        val items = flow {
            (1..7).forEach { i ->
                emit("Item-$i")
                delay(10)
            }
        }
        
        val batches = service.batchProcessing(items, batchSize = 3).toList()
        
        assertEquals(3, batches.size) // 7 items in batches of 3 = 3 batches (3,3,1)
        
        assertEquals(3, batches[0].size)
        assertEquals(3, batches[1].size)
        assertEquals(1, batches[2].size) // Last batch with remaining items
        
        // Verify batch content
        assertTrue(batches[0].contains("Batch[Item-1]"))
        assertTrue(batches[2].contains("Batch[Item-7]"))
        
        println("✅ Batched flow processing test passed")
    }
}

/**
 * Hot flow testing patterns (StateFlow and SharedFlow)
 */
class HotFlowTestPatterns {
    
    // Example view model for testing hot flows
    class DataViewModel {
        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()
        
        private val _events = MutableSharedFlow<String>()
        val events: SharedFlow<String> = _events.asSharedFlow()
        
        fun updateData(newData: String) {
            _uiState.update { it.copy(data = newData, isLoading = false) }
        }
        
        fun setLoading(loading: Boolean) {
            _uiState.update { it.copy(isLoading = loading) }
        }
        
        fun emitEvent(event: String) {
            _events.tryEmit(event)
        }
        
        suspend fun loadDataAsync(data: String) {
            setLoading(true)
            delay(500) // Simulate async operation
            updateData(data)
            emitEvent("Data loaded: $data")
        }
    }
    
    data class UiState(
        val data: String = "",
        val isLoading: Boolean = false
    )
    
    @Test
    fun testStateFlowBehavior() = runTest {
        println("=== Testing StateFlow Behavior ===")
        
        val viewModel = DataViewModel()
        
        // StateFlow should have initial value
        assertEquals(UiState(), viewModel.uiState.value)
        
        // Update state and verify
        viewModel.updateData("Test Data")
        assertEquals(UiState(data = "Test Data", isLoading = false), viewModel.uiState.value)
        
        // Test state flow collection
        val collectedStates = mutableListOf<UiState>()
        val job = launch {
            viewModel.uiState.take(3).collect {
                collectedStates.add(it)
            }
        }
        
        // Make additional updates
        viewModel.setLoading(true)
        viewModel.updateData("Updated Data")
        
        job.join()
        
        assertEquals(3, collectedStates.size)
        assertEquals(UiState(data = "Test Data", isLoading = false), collectedStates[0])
        assertTrue(collectedStates[1].isLoading)
        assertEquals("Updated Data", collectedStates[2].data)
        
        println("✅ StateFlow behavior test passed")
    }
    
    @Test
    fun testSharedFlowEvents() = runTest {
        println("=== Testing SharedFlow Events ===")
        
        val viewModel = DataViewModel()
        val collectedEvents = mutableListOf<String>()
        
        // Start collecting events
        val eventCollector = launch {
            viewModel.events.collect { event ->
                collectedEvents.add(event)
            }
        }
        
        // Emit some events
        viewModel.emitEvent("Event 1")
        viewModel.emitEvent("Event 2")
        viewModel.emitEvent("Event 3")
        
        // Give time for collection
        advanceTimeBy(100)
        
        assertEquals(3, collectedEvents.size)
        assertEquals("Event 1", collectedEvents[0])
        assertEquals("Event 2", collectedEvents[1])
        assertEquals("Event 3", collectedEvents[2])
        
        eventCollector.cancel()
        
        println("✅ SharedFlow events test passed")
    }
    
    @Test
    fun testHotFlowLifecycle() = runTest {
        println("=== Testing Hot Flow Lifecycle ===")
        
        val viewModel = DataViewModel()
        
        // Test async operation with state changes
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)
        assertEquals("", initialState.data)
        
        // Start async operation
        val asyncJob = launch {
            viewModel.loadDataAsync("Async Data")
        }
        
        // Check loading state
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isLoading)
        
        // Complete async operation
        asyncJob.join()
        
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals("Async Data", finalState.data)
        
        println("✅ Hot flow lifecycle test passed")
    }
    
    @Test
    fun testMultipleStateFlowSubscribers() = runTest {
        println("=== Testing Multiple StateFlow Subscribers ===")
        
        val viewModel = DataViewModel()
        val subscriber1Results = mutableListOf<UiState>()
        val subscriber2Results = mutableListOf<UiState>()
        
        // Start two subscribers
        val subscriber1 = launch {
            viewModel.uiState.take(3).collect {
                subscriber1Results.add(it)
            }
        }
        
        val subscriber2 = launch {
            viewModel.uiState.take(3).collect {
                subscriber2Results.add(it)
            }
        }
        
        // Make state changes
        viewModel.updateData("Shared Data 1")
        viewModel.updateData("Shared Data 2")
        
        // Wait for both subscribers
        subscriber1.join()
        subscriber2.join()
        
        // Both subscribers should get same values
        assertEquals(subscriber1Results, subscriber2Results)
        assertEquals(3, subscriber1Results.size)
        assertEquals("Shared Data 2", subscriber1Results.last().data)
        
        println("✅ Multiple StateFlow subscribers test passed")
    }
}

/**
 * Flow performance testing patterns
 */
class FlowPerformanceTestPatterns {
    
    class PerformanceFlowService {
        fun largeDataProcessing(count: Int): Flow<String> = flow {
            repeat(count) { i ->
                // Simulate processing work
                emit("Item-$i")
                if (i % 1000 == 0) {
                    yield() // Allow other coroutines
                }
            }
        }
        
        fun concurrentProcessing(items: Flow<Int>): Flow<String> = 
            items.flatMapMerge(concurrency = 4) { item ->
                flow {
                    delay(Random.nextLong(10, 50))
                    emit("Processed-$item")
                }
            }
        
        fun bufferedProcessing(items: Flow<String>): Flow<String> = 
            items.buffer(100).map { item ->
                delay(10) // Simulate work
                "Buffered-$item"
            }
    }
    
    @Test
    fun testLargeFlowPerformance() = runTest {
        println("=== Testing Large Flow Performance ===")
        
        val service = PerformanceFlowService()
        val itemCount = 10000
        
        val startTime = currentTime
        val results = service.largeDataProcessing(itemCount).count()
        val endTime = currentTime
        
        assertEquals(itemCount, results)
        val executionTime = endTime - startTime
        
        println("  Processed $itemCount items in ${executionTime}ms")
        assertTrue(executionTime < 1000, "Large flow should process efficiently in virtual time")
        
        println("✅ Large flow performance test passed")
    }
    
    @Test
    fun testConcurrentProcessingPerformance() = runTest {
        println("=== Testing Concurrent Processing Performance ===")
        
        val service = PerformanceFlowService()
        val items = (1..20).asFlow()
        
        val sequentialTime = measureTime {
            items.map { item ->
                delay(30)
                "Sequential-$item"
            }.toList()
        }
        
        val concurrentTime = measureTime {
            service.concurrentProcessing(items).toList()
        }
        
        assertTrue(concurrentTime < sequentialTime, 
            "Concurrent processing (${concurrentTime}) should be faster than sequential (${sequentialTime})")
        
        println("  Sequential time: $sequentialTime")
        println("  Concurrent time: $concurrentTime")
        
        println("✅ Concurrent processing performance test passed")
    }
    
    @Test
    fun testBufferedProcessingPerformance() = runTest {
        println("=== Testing Buffered Processing Performance ===")
        
        val service = PerformanceFlowService()
        val items = (1..100).map { "Item-$it" }.asFlow()
        
        // Test with buffering
        val bufferedTime = measureTime {
            service.bufferedProcessing(items).toList()
        }
        
        // Test without buffering (direct processing)
        val unbufferedTime = measureTime {
            items.map { item ->
                delay(10)
                "Direct-$item"
            }.toList()
        }
        
        println("  Buffered processing time: $bufferedTime")
        println("  Unbuffered processing time: $unbufferedTime")
        
        // Buffering should help with flow processing
        // Note: In virtual time, the difference might not be as pronounced
        assertTrue(bufferedTime <= unbufferedTime * 1.5, 
            "Buffered processing should not be significantly slower")
        
        println("✅ Buffered processing performance test passed")
    }
}

/**
 * Custom flow testing utilities
 */
object FlowTestUtils {
    
    /**
     * Collect flow items with a timeout
     */
    suspend fun <T> Flow<T>.collectWithTimeout(
        timeoutMs: Long,
        maxItems: Int = Int.MAX_VALUE
    ): List<T> = withTimeout(timeoutMs) {
        take(maxItems).toList()
    }
    
    /**
     * Assert that a flow emits expected values in order
     */
    suspend fun <T> Flow<T>.assertEmits(vararg expectedValues: T) {
        val actualValues = toList()
        assertEquals(expectedValues.toList(), actualValues)
    }
    
    /**
     * Assert that a flow emits values in any order
     */
    suspend fun <T> Flow<T>.assertEmitsInAnyOrder(vararg expectedValues: T) {
        val actualValues = toList().toSet()
        val expectedSet = expectedValues.toSet()
        assertEquals(expectedSet, actualValues)
    }
    
    /**
     * Assert that a flow completes within expected time
     */
    suspend fun <T> Flow<T>.assertCompletesWithin(timeoutMs: Long): List<T> {
        return withTimeout(timeoutMs) { toList() }
    }
    
    /**
     * Assert that a flow throws expected exception
     */
    suspend inline fun <reified E : Exception> Flow<*>.assertThrows(): E {
        return assertFailsWith<E> { toList() }
    }
    
    /**
     * Create a test flow that emits values with delays
     */
    fun <T> testFlow(vararg items: Pair<T, Long>): Flow<T> = flow {
        items.forEach { (item, delay) ->
            delay(delay)
            emit(item)
        }
    }
    
    /**
     * Create a test flow that fails after emitting some values
     */
    fun <T> testFlowWithError(
        values: List<T>,
        errorAfter: Int,
        error: Exception = RuntimeException("Test error")
    ): Flow<T> = flow {
        values.forEachIndexed { index, value ->
            if (index == errorAfter) {
                throw error
            }
            emit(value)
        }
    }
    
    /**
     * Test flow cancellation behavior
     */
    suspend fun <T> testFlowCancellation(
        flow: Flow<T>,
        cancelAfterMs: Long
    ): Pair<List<T>, Boolean> {
        val results = mutableListOf<T>()
        var wasCancelled = false
        
        val job = CoroutineScope(currentCoroutineContext()).launch {
            try {
                flow.collect { results.add(it) }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
        
        delay(cancelAfterMs)
        job.cancel()
        job.join()
        
        return results to wasCancelled
    }
}

// Custom chunked operator for testing
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    val chunk = mutableListOf<T>()
    collect { item ->
        chunk.add(item)
        if (chunk.size == size) {
            emit(chunk.toList())
            chunk.clear()
        }
    }
    if (chunk.isNotEmpty()) {
        emit(chunk.toList())
    }
}

/**
 * Main demonstration function
 */
fun main() {
    println("=== Flow Testing Patterns Demo ===")
    println("This file contains comprehensive testing patterns for Kotlin Flows.")
    println("In a real project, these tests would be executed by testing frameworks.")
    println()
    println("Key Flow Testing Concepts Covered:")
    println("✅ Basic flow collection and assertion patterns")
    println("✅ Testing flow transformations and operator chains")
    println("✅ Exception handling and error propagation testing")
    println("✅ Flow cancellation and timeout testing")
    println("✅ Hot flow testing (StateFlow/SharedFlow)")
    println("✅ Performance testing and benchmarking")
    println("✅ Custom testing utilities and helpers")
    println()
    println("Flow Testing Best Practices:")
    println("✅ Use runTest for controlled flow execution")
    println("✅ Test both success and failure scenarios")
    println("✅ Verify flow completion and cancellation behavior")
    println("✅ Test hot flow lifecycle and subscription timing")
    println("✅ Use virtual time for deterministic testing")
    println("✅ Test edge cases like empty flows and single items")
    println("✅ Verify concurrent flow behavior and merging")
    println("✅ Test performance characteristics and backpressure")
    println()
    println("Common Flow Testing Tools:")
    println("- kotlinx-coroutines-test for runTest and virtual time")
    println("- Flow.toList() for collecting finite flows")
    println("- Flow.first(), Flow.last() for single value testing")
    println("- assertFailsWith for exception testing")
    println("- Turbine library for advanced flow testing (external)")
    println("- Custom test utilities for specific use cases")
}