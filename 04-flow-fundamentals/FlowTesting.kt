/**
 * # Flow Testing Patterns and Strategies
 * 
 * ## Problem Description
 * Testing asynchronous flows presents unique challenges: timing dependencies,
 * non-deterministic emission orders, exception handling verification, and
 * resource cleanup validation. Traditional testing approaches are insufficient
 * for flows due to their cold nature and complex operator chains.
 * 
 * ## Solution Approach
 * Comprehensive flow testing includes:
 * - Unit testing individual flow operators and transformations
 * - Integration testing of complex flow pipelines
 * - Time-based testing with virtual time progression
 * - Exception and error scenario testing
 * - Performance and load testing patterns
 * 
 * ## Key Learning Points
 * - Flow testing requires special test utilities and patterns
 * - Virtual time enables deterministic timing tests
 * - toList() and single() are common testing collection methods
 * - Exception testing needs proper async handling
 * - Test flows should be independent and repeatable
 * 
 * ## Performance Considerations
 * - Test execution time optimization with virtual time
 * - Memory usage in test data collection
 * - Parallel test execution considerations
 * - Resource cleanup in test environments
 * 
 * ## Common Pitfalls
 * - Non-deterministic test failures due to timing
 * - Missing exception testing scenarios
 * - Inadequate cleanup in test flows
 * - Over-complicated test setup
 * - Missing edge case coverage
 * 
 * ## Real-World Applications
 * - CI/CD pipeline flow testing
 * - Data processing pipeline validation
 * - Reactive UI testing
 * - Real-time system integration testing
 * - Performance regression testing
 * 
 * ## Related Concepts
 * - Coroutine testing utilities
 * - TestCoroutineDispatcher patterns
 * - MockK/Mockito for flow mocking
 */

package flow.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic flow testing patterns
 * 
 * Flow Testing Strategy:
 * 
 * Test Flow Creation ──> Execute Operations ──> Collect Results ──> Assert
 *       │                      │                     │              │
 *       ├─ Test Data          ├─ Apply Operators    ├─ toList()    ├─ Values
 *       ├─ Mock Sources       ├─ Error Injection    ├─ single()    ├─ Exceptions
 *       └─ Edge Cases         └─ Timing Control     └─ count()     └─ Timing
 */
class BasicFlowTesting {
    
    @Test
    fun testBasicFlowOperations() = runTest {
        println("=== Basic Flow Operation Testing ===")
        
        // Test map operation
        val sourceFlow = flowOf(1, 2, 3, 4, 5)
        val mappedResults = sourceFlow
            .map { it * 2 }
            .toList()
        
        assertEquals(listOf(2, 4, 6, 8, 10), mappedResults)
        println("✅ Map operation test passed")
        
        // Test filter operation
        val filteredResults = sourceFlow
            .filter { it % 2 == 0 }
            .toList()
        
        assertEquals(listOf(2, 4), filteredResults)
        println("✅ Filter operation test passed")
        
        // Test combined operations
        val combinedResults = sourceFlow
            .filter { it > 2 }
            .map { it * 3 }
            .toList()
        
        assertEquals(listOf(9, 12, 15), combinedResults)
        println("✅ Combined operations test passed")
    }
    
    @Test
    fun testFlowWithSuspendingOperations() = runTest {
        println("=== Suspending Operations Testing ===")
        
        // Create flow with delays
        val delayedFlow = flow {
            emit(1)
            delay(100)
            emit(2)
            delay(100)
            emit(3)
        }
        
        // Test collection timing
        val startTime = currentTime
        val results = delayedFlow.toList()
        val endTime = currentTime
        
        assertEquals(listOf(1, 2, 3), results)
        assertTrue(endTime - startTime >= 200, "Should take at least 200ms")
        println("✅ Delayed flow test passed (${endTime - startTime}ms)")
        
        // Test with timeout
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(150) {
                delayedFlow.collect()
            }
        }
        println("✅ Timeout test passed")
    }
    
    @Test
    fun testFlowExceptionHandling() = runTest {
        println("=== Exception Handling Testing ===")
        
        // Test flow that throws exception
        val errorFlow = flow {
            emit(1)
            emit(2)
            throw RuntimeException("Test exception")
        }
        
        // Test exception propagation
        val exception = assertFailsWith<RuntimeException> {
            errorFlow.toList()
        }
        assertEquals("Test exception", exception.message)
        println("✅ Exception propagation test passed")
        
        // Test catch operator
        val recoveredResults = errorFlow
            .catch { emit(-1) }
            .toList()
        
        assertEquals(listOf(1, 2, -1), recoveredResults)
        println("✅ Exception recovery test passed")
        
        // Test retry behavior
        var attemptCount = 0
        val retryFlow = flow {
            attemptCount++
            if (attemptCount < 3) {
                throw RuntimeException("Attempt $attemptCount failed")
            }
            emit("Success on attempt $attemptCount")
        }
        
        val retryResult = retryFlow
            .retry(retries = 3)
            .single()
        
        assertEquals("Success on attempt 3", retryResult)
        assertEquals(3, attemptCount)
        println("✅ Retry behavior test passed")
    }
    
    @Test
    fun testFlowCompletion() = runTest {
        println("=== Flow Completion Testing ===")
        
        // Test successful completion
        var completionCalled = false
        var completionException: Throwable? = null
        
        flowOf(1, 2, 3)
            .onCompletion { exception ->
                completionCalled = true
                completionException = exception
            }
            .collect()
        
        assertTrue(completionCalled)
        assertNull(completionException)
        println("✅ Successful completion test passed")
        
        // Test completion with exception
        completionCalled = false
        completionException = null
        
        try {
            flow {
                emit(1)
                throw RuntimeException("Test error")
            }
                .onCompletion { exception ->
                    completionCalled = true
                    completionException = exception
                }
                .collect()
        } catch (e: Exception) {
            // Expected
        }
        
        assertTrue(completionCalled)
        assertNotNull(completionException)
        assertEquals("Test error", completionException?.message)
        println("✅ Exception completion test passed")
    }
}

/**
 * Advanced flow testing with virtual time
 */
class VirtualTimeFlowTesting {
    
    @Test
    fun testTimeBasedOperators() = runTest {
        println("=== Virtual Time Testing ===")
        
        // Test debounce operator
        val debouncedFlow = flow {
            emit(1)
            delay(50)
            emit(2)
            delay(50)
            emit(3)
            delay(200) // Longer delay to trigger debounce
            emit(4)
        }.debounce(100)
        
        val debouncedResults = debouncedFlow.toList()
        assertEquals(listOf(3, 4), debouncedResults)
        println("✅ Debounce test passed: $debouncedResults")
        
        // Test sample operator
        val sampledFlow = flow {
            repeat(10) { i ->
                emit(i)
                delay(50)
            }
        }.sample(150) // Sample every 150ms
        
        val sampledResults = sampledFlow.toList()
        assertTrue(sampledResults.size <= 4) // Should sample about 3-4 values
        println("✅ Sample test passed: $sampledResults")
        
        // Test timeout behavior
        val timeoutFlow = flow {
            emit(1)
            delay(200)
            emit(2) // This should timeout
        }
        
        assertFailsWith<TimeoutCancellationException> {
            timeoutFlow
                .timeout(150)
                .toList()
        }
        println("✅ Timeout test passed")
    }
    
    @Test
    fun testFlowBuffering() = runTest {
        println("=== Buffer Testing ===")
        
        var emissionCount = 0
        var collectionCount = 0
        
        val bufferedFlow = flow {
            repeat(5) { i ->
                emit(i)
                emissionCount++
                delay(10) // Fast emission
            }
        }.buffer().onEach {
            collectionCount++
            delay(50) // Slow collection
        }
        
        val results = bufferedFlow.toList()
        
        assertEquals(listOf(0, 1, 2, 3, 4), results)
        assertEquals(5, emissionCount)
        assertEquals(5, collectionCount)
        println("✅ Buffer test passed - emissions: $emissionCount, collections: $collectionCount")
        
        // Test buffer capacity
        val capacityResults = mutableListOf<Int>()
        
        flow {
            repeat(3) { i ->
                emit(i)
                delay(10)
            }
        }
            .buffer(capacity = 1)
            .collect { value ->
                capacityResults.add(value)
                delay(50)
            }
        
        assertEquals(listOf(0, 1, 2), capacityResults)
        println("✅ Buffer capacity test passed")
    }
    
    @Test
    fun testFlowCancellation() = runTest {
        println("=== Cancellation Testing ===")
        
        var emissionsBeforeCancellation = 0
        var cleanupCalled = false
        
        val cancellableFlow = flow {
            try {
                repeat(10) { i ->
                    emit(i)
                    emissionsBeforeCancellation++
                    delay(100)
                }
            } finally {
                cleanupCalled = true
            }
        }
        
        val job = launch {
            cancellableFlow.collect { value ->
                println("  Collected: $value")
            }
        }
        
        delay(250) // Let it emit a few values
        job.cancel()
        job.join()
        
        assertTrue(emissionsBeforeCancellation < 10)
        assertTrue(cleanupCalled)
        println("✅ Cancellation test passed - emissions: $emissionsBeforeCancellation, cleanup: $cleanupCalled")
        
        // Test cancellation with finally block
        var finallyCalled = false
        
        try {
            flow {
                try {
                    repeat(5) { i ->
                        emit(i)
                        delay(100)
                    }
                } finally {
                    finallyCalled = true
                }
            }
                .onCompletion { cause ->
                    if (cause is CancellationException) {
                        println("  Flow cancelled properly")
                    }
                }
                .collect { 
                    if (it == 2) {
                        throw CancellationException("Manual cancellation")
                    }
                }
        } catch (e: CancellationException) {
            // Expected
        }
        
        assertTrue(finallyCalled)
        println("✅ Finally block test passed")
    }
}

/**
 * Flow combination testing
 */
class FlowCombinationTesting {
    
    @Test
    fun testMergeOperator() = runTest {
        println("=== Merge Testing ===")
        
        val flow1 = flowOf("A", "B", "C").onEach { delay(100) }
        val flow2 = flowOf("1", "2", "3").onEach { delay(150) }
        
        val mergedResults = merge(flow1, flow2).toList()
        
        assertEquals(6, mergedResults.size)
        assertTrue(mergedResults.containsAll(listOf("A", "B", "C", "1", "2", "3")))
        println("✅ Merge test passed: $mergedResults")
        
        // Test merge with different emission rates
        val fastFlow = flow {
            repeat(3) { i -> 
                emit("Fast-$i")
                delay(50)
            }
        }
        
        val slowFlow = flow {
            repeat(2) { i ->
                emit("Slow-$i")
                delay(200)
            }
        }
        
        val rateMergedResults = merge(fastFlow, slowFlow).toList()
        assertEquals(5, rateMergedResults.size)
        println("✅ Rate-based merge test passed: $rateMergedResults")
    }
    
    @Test
    fun testZipOperator() = runTest {
        println("=== Zip Testing ===")
        
        val numbers = flowOf(1, 2, 3, 4)
        val letters = flowOf("A", "B", "C")
        
        val zippedResults = numbers.zip(letters) { num, letter ->
            "$num$letter"
        }.toList()
        
        assertEquals(listOf("1A", "2B", "3C"), zippedResults)
        println("✅ Zip test passed: $zippedResults")
        
        // Test zip with different timing
        val timedNumbers = flow {
            listOf(1, 2, 3).forEach { num ->
                emit(num)
                delay(100)
            }
        }
        
        val timedLetters = flow {
            listOf("X", "Y", "Z").forEach { letter ->
                emit(letter)
                delay(150)
            }
        }
        
        val timedZippedResults = timedNumbers.zip(timedLetters) { num, letter ->
            "$num$letter"
        }.toList()
        
        assertEquals(listOf("1X", "2Y", "3Z"), timedZippedResults)
        println("✅ Timed zip test passed: $timedZippedResults")
    }
    
    @Test
    fun testCombineOperator() = runTest {
        println("=== Combine Testing ===")
        
        val sourceA = flow {
            listOf("A1", "A2", "A3").forEach { value ->
                emit(value)
                delay(150)
            }
        }
        
        val sourceB = flow {
            listOf("B1", "B2").forEach { value ->
                emit(value)
                delay(200)
            }
        }
        
        val combinedResults = sourceA.combine(sourceB) { a, b ->
            "$a+$b"
        }.toList()
        
        // combine emits when any source emits with the latest from both
        assertTrue(combinedResults.isNotEmpty())
        assertTrue(combinedResults.all { it.contains("+") })
        println("✅ Combine test passed: $combinedResults")
        
        // Test initial values behavior
        val initialSourceA = flow {
            emit("Initial-A")
            delay(300)
            emit("Updated-A")
        }
        
        val initialSourceB = flow {
            delay(150)
            emit("Initial-B")
            delay(300)
            emit("Updated-B")
        }
        
        val initialCombinedResults = initialSourceA.combine(initialSourceB) { a, b ->
            "$a<->$b"
        }.toList()
        
        assertEquals(listOf("Initial-A<->Initial-B", "Updated-A<->Initial-B", "Updated-A<->Updated-B"), initialCombinedResults)
        println("✅ Initial values combine test passed: $initialCombinedResults")
    }
}

/**
 * Flow transformation testing
 */
class FlowTransformationTesting {
    
    @Test
    fun testFlatMapOperators() = runTest {
        println("=== FlatMap Testing ===")
        
        fun createSubFlow(value: Int): Flow<String> = flow {
            repeat(2) { i ->
                emit("$value-$i")
                delay(50)
            }
        }
        
        val sourceFlow = flowOf(1, 2, 3)
        
        // Test flatMapConcat
        val concatResults = sourceFlow
            .flatMapConcat { createSubFlow(it) }
            .toList()
        
        assertEquals(listOf("1-0", "1-1", "2-0", "2-1", "3-0", "3-1"), concatResults)
        println("✅ FlatMapConcat test passed: $concatResults")
        
        // Test flatMapMerge
        val mergeResults = sourceFlow
            .flatMapMerge { createSubFlow(it) }
            .toList()
        
        assertEquals(6, mergeResults.size)
        assertTrue(mergeResults.containsAll(listOf("1-0", "1-1", "2-0", "2-1", "3-0", "3-1")))
        println("✅ FlatMapMerge test passed: $mergeResults")
        
        // Test flatMapLatest with fast switching
        val switchingResults = flow {
            listOf(1, 2, 3).forEach { value ->
                emit(value)
                delay(75) // Switch before subflow completes
            }
        }
            .flatMapLatest { value ->
                flow {
                    repeat(3) { i ->
                        emit("$value-$i")
                        delay(50)
                    }
                }
            }
            .toList()
        
        // Should only have results from the last emission (3)
        assertTrue(switchingResults.any { it.startsWith("3-") })
        println("✅ FlatMapLatest test passed: $switchingResults")
    }
    
    @Test
    fun testCustomTransformations() = runTest {
        println("=== Custom Transformation Testing ===")
        
        // Test custom windowing operator
        suspend fun <T> Flow<T>.window(size: Int): Flow<List<T>> = flow {
            val window = mutableListOf<T>()
            collect { value ->
                window.add(value)
                if (window.size == size) {
                    emit(window.toList())
                    window.clear()
                }
            }
            if (window.isNotEmpty()) {
                emit(window.toList())
            }
        }
        
        val windowResults = flowOf(1, 2, 3, 4, 5, 6, 7)
            .window(size = 3)
            .toList()
        
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), windowResults)
        println("✅ Custom windowing test passed: $windowResults")
        
        // Test stateful transformation
        var state = 0
        val statefulResults = flowOf(1, 2, 3, 4, 5)
            .transform { value ->
                state += value
                emit("Sum: $state")
            }
            .toList()
        
        assertEquals(listOf("Sum: 1", "Sum: 3", "Sum: 6", "Sum: 10", "Sum: 15"), statefulResults)
        println("✅ Stateful transformation test passed: $statefulResults")
    }
    
    @Test
    fun testErrorTransformations() = runTest {
        println("=== Error Transformation Testing ===")
        
        // Test transformation with error recovery
        val errorResults = flowOf(1, 2, 3, 4, 5)
            .map { value ->
                if (value == 3) throw RuntimeException("Error at $value")
                value * 2
            }
            .catch { exception ->
                emit(-1) // Error marker
            }
            .toList()
        
        assertEquals(listOf(2, 4, -1), errorResults)
        println("✅ Error transformation test passed: $errorResults")
        
        // Test retry with transformation
        var attempts = 0
        val retryResults = flow {
            repeat(3) { i ->
                attempts++
                if (i == 1 && attempts < 4) {
                    throw RuntimeException("Retry error")
                }
                emit(i)
            }
        }
            .retry(retries = 3)
            .map { it * 10 }
            .toList()
        
        assertEquals(listOf(0, 10, 20), retryResults)
        assertTrue(attempts > 3) // Should have retried
        println("✅ Retry transformation test passed: $retryResults, attempts: $attempts")
    }
}

/**
 * Performance and load testing
 */
class FlowPerformanceTesting {
    
    @Test
    fun testHighVolumeFlow() = runTest {
        println("=== High Volume Testing ===")
        
        val itemCount = 10000
        val startTime = currentTime
        
        val processedCount = flow {
            repeat(itemCount) { i ->
                emit(i)
            }
        }
            .map { it * 2 }
            .filter { it % 4 == 0 }
            .count()
        
        val endTime = currentTime
        val duration = endTime - startTime
        
        assertEquals(itemCount / 2, processedCount.toInt()) // Half should pass filter
        assertTrue(duration < 1000, "Processing should be fast: ${duration}ms")
        println("✅ High volume test passed: $processedCount items in ${duration}ms")
        
        // Test memory efficiency
        val memoryEfficientCount = flow {
            repeat(100000) { i ->
                emit(i)
                if (i % 10000 == 0) {
                    yield() // Allow other coroutines to run
                }
            }
        }
            .chunked(1000)
            .map { chunk -> chunk.size }
            .sum()
        
        assertEquals(100000, memoryEfficientCount)
        println("✅ Memory efficient test passed: $memoryEfficientCount items processed")
    }
    
    @Test
    fun testConcurrentFlows() = runTest {
        println("=== Concurrent Flow Testing ===")
        
        val concurrentResults = mutableListOf<String>()
        
        // Launch multiple flows concurrently
        val jobs = (1..5).map { id ->
            launch {
                flow {
                    repeat(3) { i ->
                        emit("Flow-$id-Item-$i")
                        delay(Random.nextLong(10, 50))
                    }
                }
                    .collect { item ->
                        synchronized(concurrentResults) {
                            concurrentResults.add(item)
                        }
                    }
            }
        }
        
        jobs.joinAll()
        
        assertEquals(15, concurrentResults.size) // 5 flows * 3 items each
        
        // Verify all flows contributed
        (1..5).forEach { id ->
            assertTrue(concurrentResults.any { it.contains("Flow-$id") })
        }
        
        println("✅ Concurrent flows test passed: ${concurrentResults.size} items")
        
        // Test concurrent processing with shared state
        var sharedCounter = 0
        val mutex = kotlinx.coroutines.sync.Mutex()
        
        val concurrentJobs = (1..10).map { id ->
            launch {
                flowOf(1, 2, 3, 4, 5)
                    .collect { value ->
                        mutex.withLock {
                            sharedCounter += value
                        }
                    }
            }
        }
        
        concurrentJobs.joinAll()
        
        assertEquals(150, sharedCounter) // 10 flows * (1+2+3+4+5) = 10 * 15 = 150
        println("✅ Shared state test passed: counter = $sharedCounter")
    }
    
    @Test
    fun testBackpressureHandling() = runTest {
        println("=== Backpressure Testing ===")
        
        var emissionCount = 0
        var consumptionCount = 0
        
        // Fast producer, slow consumer scenario
        val backpressureFlow = flow {
            repeat(10) { i ->
                emit(i)
                emissionCount++
                delay(10) // Fast emission
            }
        }
            .buffer(capacity = 3) // Limited buffer
            .onEach {
                delay(50) // Slow consumption
                consumptionCount++
            }
        
        val results = backpressureFlow.toList()
        
        assertEquals(10, results.size)
        assertEquals(10, emissionCount)
        assertEquals(10, consumptionCount)
        println("✅ Backpressure test passed: emissions=$emissionCount, consumptions=$consumptionCount")
        
        // Test conflated flow (drops intermediate values)
        emissionCount = 0
        consumptionCount = 0
        
        val conflatedResults = flow {
            repeat(10) { i ->
                emit(i)
                emissionCount++
                delay(10)
            }
        }
            .conflate() // Drop intermediate values
            .onEach {
                delay(80) // Very slow consumption
                consumptionCount++
            }
            .toList()
        
        assertTrue(conflatedResults.size <= emissionCount)
        assertTrue(consumptionCount <= emissionCount)
        println("✅ Conflated flow test passed: results=${conflatedResults.size}, " +
                "emissions=$emissionCount, consumptions=$consumptionCount")
    }
}

/**
 * Mock and test utilities
 */
class FlowTestUtilities {
    
    // Mock flow factory for testing
    class MockFlowFactory {
        fun createUserDataFlow(userId: String): Flow<String> = flow {
            delay(100) // Simulate network delay
            if (userId == "error_user") {
                throw RuntimeException("User not found")
            }
            emit("User data for $userId")
        }
        
        fun createNotificationFlow(): Flow<String> = flow {
            val notifications = listOf("Email", "SMS", "Push")
            notifications.forEach { notification ->
                emit(notification)
                delay(50)
            }
        }
    }
    
    @Test
    fun testWithMockFlows() = runTest {
        println("=== Mock Flow Testing ===")
        
        val mockFactory = MockFlowFactory()
        
        // Test successful case
        val userData = mockFactory.createUserDataFlow("valid_user").single()
        assertEquals("User data for valid_user", userData)
        println("✅ Mock success test passed")
        
        // Test error case
        assertFailsWith<RuntimeException> {
            mockFactory.createUserDataFlow("error_user").single()
        }
        println("✅ Mock error test passed")
        
        // Test notification flow
        val notifications = mockFactory.createNotificationFlow().toList()
        assertEquals(listOf("Email", "SMS", "Push"), notifications)
        println("✅ Mock notification test passed")
    }
    
    // Test utilities for common flow testing patterns
    suspend fun <T> Flow<T>.collectValues(maxCount: Int = Int.MAX_VALUE): List<T> {
        val values = mutableListOf<T>()
        take(maxCount).collect { values.add(it) }
        return values
    }
    
    suspend fun <T> Flow<T>.collectWithTimeout(timeoutMs: Long): List<T> {
        val values = mutableListOf<T>()
        try {
            withTimeout(timeoutMs) {
                collect { values.add(it) }
            }
        } catch (e: TimeoutCancellationException) {
            // Return collected values so far
        }
        return values
    }
    
    @Test
    fun testFlowUtilities() = runTest {
        println("=== Flow Test Utilities ===")
        
        // Test collectValues utility
        val limitedValues = flow {
            repeat(10) { emit(it) }
        }.collectValues(maxCount = 5)
        
        assertEquals(listOf(0, 1, 2, 3, 4), limitedValues)
        println("✅ collectValues utility test passed")
        
        // Test collectWithTimeout utility
        val timeoutValues = flow {
            repeat(10) { i ->
                emit(i)
                delay(100)
            }
        }.collectWithTimeout(timeoutMs = 350)
        
        assertTrue(timeoutValues.size <= 4) // Should collect 3-4 values before timeout
        println("✅ collectWithTimeout utility test passed: $timeoutValues")
        
        // Test assertion helpers
        fun <T> List<T>.assertContainsInOrder(vararg expected: T) {
            expected.forEachIndexed { index, expectedValue ->
                assertTrue(contains(expectedValue), "List should contain $expectedValue")
                if (index > 0) {
                    val prevIndex = indexOf(expected[index - 1])
                    val currentIndex = indexOf(expectedValue)
                    assertTrue(currentIndex > prevIndex, "Elements should be in order")
                }
            }
        }
        
        val orderedResults = listOf("A", "B", "C", "D")
        orderedResults.assertContainsInOrder("A", "C", "D")
        println("✅ Assertion helper test passed")
    }
}

/**
 * Integration testing patterns
 */
class FlowIntegrationTesting {
    
    // Simulate a complex data processing pipeline
    class DataProcessingPipeline {
        suspend fun processUserEvents(userId: String): Flow<String> = flow {
            // Stage 1: Fetch user data
            emit("Fetching user data for $userId")
            delay(100)
            
            if (userId == "invalid") {
                throw IllegalArgumentException("Invalid user ID")
            }
            
            // Stage 2: Process events
            emit("Processing events for $userId")
            delay(150)
            
            // Stage 3: Generate insights
            emit("Generating insights for $userId")
            delay(80)
            
            emit("Processing completed for $userId")
        }
        
        suspend fun combineUserData(userIds: List<String>): Flow<String> {
            return userIds.asFlow()
                .flatMapMerge(concurrency = 2) { userId ->
                    processUserEvents(userId).catch { emit("Error processing $userId") }
                }
        }
    }
    
    @Test
    fun testDataProcessingPipeline() = runTest {
        println("=== Integration Testing ===")
        
        val pipeline = DataProcessingPipeline()
        
        // Test single user processing
        val singleUserResults = pipeline.processUserEvents("user123").toList()
        assertEquals(4, singleUserResults.size)
        assertTrue(singleUserResults.first().contains("Fetching"))
        assertTrue(singleUserResults.last().contains("completed"))
        println("✅ Single user processing test passed")
        
        // Test error handling in pipeline
        val errorResults = pipeline.processUserEvents("invalid")
            .catch { emit("Error handled: ${it.message}") }
            .toList()
        
        assertTrue(errorResults.any { it.contains("Error handled") })
        println("✅ Pipeline error handling test passed")
        
        // Test combined processing
        val combinedResults = pipeline.combineUserData(listOf("user1", "user2", "invalid", "user3"))
            .toList()
        
        assertTrue(combinedResults.size > 4) // Should have multiple user results
        assertTrue(combinedResults.any { it.contains("Error processing invalid") })
        println("✅ Combined processing test passed: ${combinedResults.size} results")
        
        // Test concurrent processing timing
        val startTime = currentTime
        pipeline.combineUserData(listOf("user1", "user2")).toList()
        val endTime = currentTime
        
        // Should be faster than sequential due to concurrency
        assertTrue(endTime - startTime < 600, "Concurrent processing should be faster")
        println("✅ Concurrency test passed: ${endTime - startTime}ms")
    }
    
    @Test
    fun testComplexFlowComposition() = runTest {
        println("=== Complex Composition Testing ===")
        
        // Simulate real-time data processing system
        val sensorData = flow {
            repeat(8) { i ->
                emit("Sensor-$i-${Random.nextDouble(0.0, 100.0)}")
                delay(100)
            }
        }
        
        val controlCommands = flow {
            val commands = listOf("START", "PAUSE", "RESUME", "STOP")
            commands.forEach { command ->
                emit(command)
                delay(250)
            }
        }
        
        val alerts = flow {
            delay(300)
            emit("ALERT: High temperature")
            delay(400)
            emit("ALERT: Low pressure")
        }
        
        // Combine all streams
        val systemResults = merge(
            sensorData.map { "DATA: $it" },
            controlCommands.map { "CONTROL: $it" },
            alerts
        ).toList()
        
        assertTrue(systemResults.any { it.contains("DATA:") })
        assertTrue(systemResults.any { it.contains("CONTROL:") })
        assertTrue(systemResults.any { it.contains("ALERT:") })
        println("✅ Complex composition test passed: ${systemResults.size} events")
        
        // Test state management in flows
        var systemState = "IDLE"
        val stateResults = mutableListOf<String>()
        
        controlCommands
            .onEach { command ->
                systemState = when (command) {
                    "START" -> "RUNNING"
                    "PAUSE" -> "PAUSED"
                    "RESUME" -> "RUNNING"
                    "STOP" -> "STOPPED"
                    else -> systemState
                }
                stateResults.add("State: $systemState")
            }
            .collect()
        
        assertEquals(listOf("State: RUNNING", "State: PAUSED", "State: RUNNING", "State: STOPPED"), stateResults)
        println("✅ State management test passed")
    }
}

/**
 * Main test execution function
 */
fun main() {
    runBlocking {
        println("=== Flow Testing Demo ===")
        
        // Run test classes (in real testing, these would be run by test framework)
        try {
            val basicTesting = BasicFlowTesting()
            basicTesting.testBasicFlowOperations()
            basicTesting.testFlowWithSuspendingOperations()
            basicTesting.testFlowExceptionHandling()
            basicTesting.testFlowCompletion()
            
            val virtualTimeTesting = VirtualTimeFlowTesting()
            virtualTimeTesting.testTimeBasedOperators()
            virtualTimeTesting.testFlowBuffering()
            virtualTimeTesting.testFlowCancellation()
            
            val combinationTesting = FlowCombinationTesting()
            combinationTesting.testMergeOperator()
            combinationTesting.testZipOperator()
            combinationTesting.testCombineOperator()
            
            val transformationTesting = FlowTransformationTesting()
            transformationTesting.testFlatMapOperators()
            transformationTesting.testCustomTransformations()
            transformationTesting.testErrorTransformations()
            
            val performanceTesting = FlowPerformanceTesting()
            performanceTesting.testHighVolumeFlow()
            performanceTesting.testConcurrentFlows()
            performanceTesting.testBackpressureHandling()
            
            val utilityTesting = FlowTestUtilities()
            utilityTesting.testWithMockFlows()
            utilityTesting.testFlowUtilities()
            
            val integrationTesting = FlowIntegrationTesting()
            integrationTesting.testDataProcessingPipeline()
            integrationTesting.testComplexFlowComposition()
            
            println("\n=== All Tests Completed Successfully ===")
            
        } catch (e: Exception) {
            println("❌ Test failed: ${e.message}")
            e.printStackTrace()
        }
        
        println("\n=== Flow Testing Summary ===")
        println("✅ Basic Testing:")
        println("   - Flow operator verification")
        println("   - Exception handling validation")
        println("   - Completion behavior testing")
        println("   - Timing and suspension testing")
        println()
        println("✅ Advanced Testing:")
        println("   - Virtual time progression")
        println("   - Cancellation behavior")
        println("   - Buffer and backpressure testing")
        println("   - Concurrency and parallelism")
        println()
        println("✅ Integration Testing:")
        println("   - Complex flow pipelines")
        println("   - Multi-source coordination")
        println("   - Error propagation across stages")
        println("   - Performance and load testing")
        println()
        println("✅ Test Utilities:")
        println("   - Mock flow factories")
        println("   - Custom assertion helpers")
        println("   - Timeout and collection utilities")
        println("   - Deterministic testing patterns")
    }
}