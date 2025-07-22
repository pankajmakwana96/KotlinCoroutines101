/**
 * # Coroutine Testing Fundamentals
 * 
 * ## Problem Description
 * Testing asynchronous code with coroutines presents unique challenges including
 * timing issues, race conditions, and controlling test execution flow. Standard
 * testing approaches often fail with coroutines due to their asynchronous nature,
 * concurrent execution, and dependency on dispatchers. Proper testing requires
 * specialized tools and patterns to ensure deterministic and reliable tests.
 * 
 * ## Solution Approach
 * Coroutine testing strategies include:
 * - Using kotlinx-coroutines-test library for controlled test execution
 * - TestCoroutineScheduler and runTest for deterministic timing
 * - Mocking and stubbing coroutine-based dependencies
 * - Testing exception handling and cancellation scenarios
 * - Performance and timing verification patterns
 * 
 * ## Key Learning Points
 * - runTest provides controlled coroutine execution environment
 * - TestCoroutineScheduler enables time manipulation in tests
 * - Dispatchers.setMain() allows dispatcher replacement for testing
 * - Coroutine cancellation and exception propagation must be tested
 * - Test isolation requires proper cleanup and scope management
 * 
 * ## Performance Considerations
 * - Test execution speed improved by controlled time advancement (~100-1000x faster)
 * - Virtual time eliminates actual delays in tests
 * - Parallel test execution requires careful dispatcher management
 * - Memory usage reduced by avoiding real network/IO operations
 * - Test determinism prevents flaky failures from timing issues
 * 
 * ## Common Pitfalls
 * - Using runBlocking instead of runTest in coroutine tests
 * - Not cleaning up test dispatchers between tests
 * - Forgetting to test cancellation and exception scenarios
 * - Race conditions in concurrent test scenarios
 * - Not using virtual time advancement properly
 * 
 * ## Real-World Applications
 * - Unit testing repository classes with suspend functions
 * - Testing ViewModel coroutine operations in Android
 * - Integration testing with mock web services
 * - Testing retry and timeout mechanisms
 * - Performance testing of coroutine-based systems
 * 
 * ## Related Concepts
 * - Test-driven development (TDD) with coroutines
 * - Behavior-driven development (BDD) patterns
 * - Mock frameworks integration (MockK, Mockito)
 * - CI/CD pipeline testing strategies
 */

package testing.debugging.fundamentals

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Basic coroutine testing patterns and techniques
 * 
 * Testing Architecture:
 * 
 * Test Environment:
 * ├── runTest ──────────── Controlled coroutine scope with virtual time
 * ├── TestScheduler ────── Time advancement and scheduling control
 * ├── Test Dispatchers ─── Deterministic thread behavior
 * └── Assertions ──────── Verify coroutine behavior and results
 * 
 * Testing Flow:
 * Setup Test Environment -> Execute Coroutines -> Advance Time -> Assert Results
 */
class BasicCoroutineTestPatterns {
    
    // Example service class to test
    class DataService {
        suspend fun fetchData(id: String): String {
            delay(1000) // Simulate network delay
            return "Data for $id"
        }
        
        suspend fun fetchDataWithRetry(id: String, maxRetries: Int = 3): String {
            repeat(maxRetries) { attempt ->
                try {
                    delay(500) // Simulate network call
                    if (attempt < maxRetries - 1) {
                        throw RuntimeException("Network error on attempt ${attempt + 1}")
                    }
                    return "Data for $id after ${attempt + 1} attempts"
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) throw e
                    delay(1000) // Retry delay
                }
            }
            error("Should not reach here")
        }
        
        suspend fun fetchMultipleData(ids: List<String>): List<String> = coroutineScope {
            ids.map { id ->
                async {
                    delay(500)
                    "Data for $id"
                }
            }.awaitAll()
        }
        
        suspend fun fetchWithTimeout(id: String, timeoutMs: Long): String {
            return withTimeout(timeoutMs) {
                delay(1000) // Longer than timeout
                "Data for $id"
            }
        }
    }
    
    @Test
    fun testBasicSuspendFunction() = runTest {
        println("=== Testing Basic Suspend Function ===")
        
        val dataService = DataService()
        
        // Test executes immediately without waiting for actual delay
        val result = dataService.fetchData("test-id")
        
        assertEquals("Data for test-id", result)
        
        // Verify that virtual time was advanced
        val currentTime = currentTime
        assertTrue(currentTime >= 1000, "Expected virtual time to advance by at least 1000ms, was $currentTime")
        
        println("✅ Basic suspend function test passed")
    }
    
    @Test
    fun testCoroutineWithTimeControl() = runTest {
        println("=== Testing with Time Control ===")
        
        val dataService = DataService()
        var resultReceived = false
        var result: String? = null
        
        // Start coroutine but don't wait
        val job = launch {
            result = dataService.fetchData("controlled-time")
            resultReceived = true
        }
        
        // Verify nothing happened yet
        assertFalse(resultReceived, "Result should not be received yet")
        
        // Advance time by less than required
        advanceTimeBy(500)
        assertFalse(resultReceived, "Result should still not be received after 500ms")
        
        // Advance remaining time
        advanceTimeBy(500)
        assertTrue(resultReceived, "Result should be received after 1000ms total")
        assertEquals("Data for controlled-time", result)
        
        job.join() // Ensure completion
        println("✅ Time control test passed")
    }
    
    @Test
    fun testConcurrentCoroutines() = runTest {
        println("=== Testing Concurrent Coroutines ===")
        
        val dataService = DataService()
        val startTime = currentTime
        
        // Launch multiple concurrent operations
        val results = listOf("id1", "id2", "id3").map { id ->
            async {
                dataService.fetchData(id)
            }
        }.awaitAll()
        
        val endTime = currentTime
        val executionTime = endTime - startTime
        
        // Verify results
        assertEquals(listOf("Data for id1", "Data for id2", "Data for id3"), results)
        
        // Verify concurrent execution (should take ~1000ms, not 3000ms)
        assertTrue(executionTime < 2000, "Concurrent execution should be faster than sequential, took ${executionTime}ms")
        
        println("✅ Concurrent coroutines test passed")
    }
    
    @Test
    fun testRetryMechanism() = runTest {
        println("=== Testing Retry Mechanism ===")
        
        val dataService = DataService()
        val startTime = currentTime
        
        // This should retry 3 times with delays
        val result = dataService.fetchDataWithRetry("retry-test", maxRetries = 3)
        
        val endTime = currentTime
        val totalTime = endTime - startTime
        
        assertEquals("Data for retry-test after 3 attempts", result)
        
        // Should take: 3 attempts (500ms each) + 2 retry delays (1000ms each) = 3500ms
        assertTrue(totalTime >= 3500, "Expected total time >= 3500ms, was ${totalTime}ms")
        
        println("✅ Retry mechanism test passed")
    }
    
    @Test
    fun testTimeoutScenario() = runTest {
        println("=== Testing Timeout Scenario ===")
        
        val dataService = DataService()
        
        // Should timeout because operation takes 1000ms but timeout is 500ms
        val exception = assertFailsWith<TimeoutCancellationException> {
            dataService.fetchWithTimeout("timeout-test", 500)
        }
        
        assertNotNull(exception)
        
        // Verify virtual time advancement
        val currentTime = currentTime
        assertTrue(currentTime >= 500, "Expected timeout to advance time by at least 500ms, was ${currentTime}ms")
        
        println("✅ Timeout scenario test passed")
    }
    
    @Test
    fun testCancellation() = runTest {
        println("=== Testing Coroutine Cancellation ===")
        
        val dataService = DataService()
        var operationCompleted = false
        
        val job = launch {
            try {
                dataService.fetchData("cancellation-test")
                operationCompleted = true
            } catch (e: CancellationException) {
                println("  Operation was cancelled as expected")
                throw e
            }
        }
        
        // Let it start
        advanceTimeBy(100)
        assertFalse(operationCompleted, "Operation should not complete yet")
        
        // Cancel the job
        job.cancel()
        job.join()
        
        assertFalse(operationCompleted, "Operation should not complete after cancellation")
        assertTrue(job.isCancelled, "Job should be cancelled")
        
        println("✅ Cancellation test passed")
    }
}

/**
 * Advanced testing patterns with custom test utilities
 */
class AdvancedTestingPatterns {
    
    // Repository pattern commonly used in applications
    interface UserRepository {
        suspend fun getUser(id: String): User
        suspend fun createUser(user: User): User
        suspend fun updateUser(user: User): User
        suspend fun deleteUser(id: String): Boolean
    }
    
    data class User(val id: String, val name: String, val email: String)
    
    // Service class that uses the repository
    class UserService(private val repository: UserRepository) {
        suspend fun getUserWithValidation(id: String): User? {
            return try {
                val user = repository.getUser(id)
                if (user.email.contains("@")) user else null
            } catch (e: Exception) {
                null
            }
        }
        
        suspend fun createUserWithRetry(user: User, maxRetries: Int = 3): User {
            repeat(maxRetries) { attempt ->
                try {
                    return repository.createUser(user)
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) throw e
                    delay(1000 * (attempt + 1)) // Exponential backoff
                }
            }
            error("Should not reach here")
        }
        
        suspend fun batchProcessUsers(users: List<User>): List<User> = coroutineScope {
            users.map { user ->
                async {
                    delay(100) // Simulate processing time
                    repository.createUser(user)
                }
            }.awaitAll()
        }
    }
    
    // Mock repository for testing
    class MockUserRepository : UserRepository {
        private val users = mutableMapOf<String, User>()
        private var shouldFailCreate = false
        private var createFailureCount = 0
        
        fun setShouldFailCreate(fail: Boolean, failureCount: Int = 1) {
            shouldFailCreate = fail
            createFailureCount = failureCount
        }
        
        override suspend fun getUser(id: String): User {
            delay(200) // Simulate network delay
            return users[id] ?: throw RuntimeException("User not found: $id")
        }
        
        override suspend fun createUser(user: User): User {
            delay(300) // Simulate network delay
            
            if (shouldFailCreate && createFailureCount > 0) {
                createFailureCount--
                throw RuntimeException("Create failed")
            }
            
            users[user.id] = user
            return user
        }
        
        override suspend fun updateUser(user: User): User {
            delay(250) // Simulate network delay
            users[user.id] = user
            return user
        }
        
        override suspend fun deleteUser(id: String): Boolean {
            delay(150) // Simulate network delay
            return users.remove(id) != null
        }
        
        fun getAllUsers(): Map<String, User> = users.toMap()
    }
    
    @Test
    fun testServiceWithMockRepository() = runTest {
        println("=== Testing Service with Mock Repository ===")
        
        val mockRepository = MockUserRepository()
        val userService = UserService(mockRepository)
        
        // Setup test data
        val testUser = User("1", "Test User", "test@example.com")
        mockRepository.createUser(testUser)
        
        // Test successful retrieval
        val retrievedUser = userService.getUserWithValidation("1")
        assertNotNull(retrievedUser)
        assertEquals(testUser, retrievedUser)
        
        // Test validation failure
        val invalidUser = User("2", "Invalid User", "invalid-email")
        mockRepository.createUser(invalidUser)
        
        val invalidResult = userService.getUserWithValidation("2")
        assertNull(invalidResult, "Invalid email should result in null")
        
        // Test non-existent user
        val nonExistentResult = userService.getUserWithValidation("999")
        assertNull(nonExistentResult, "Non-existent user should result in null")
        
        println("✅ Service with mock repository test passed")
    }
    
    @Test
    fun testRetryWithExponentialBackoff() = runTest {
        println("=== Testing Retry with Exponential Backoff ===")
        
        val mockRepository = MockUserRepository()
        val userService = UserService(mockRepository)
        
        // Configure mock to fail first 2 attempts
        mockRepository.setShouldFailCreate(true, 2)
        
        val testUser = User("retry-user", "Retry User", "retry@example.com")
        val startTime = currentTime
        
        val result = userService.createUserWithRetry(testUser, maxRetries = 3)
        
        val endTime = currentTime
        val totalTime = endTime - startTime
        
        assertEquals(testUser, result)
        
        // Should take: 2 failures (300ms each) + 2 retry delays (1000ms + 2000ms) + 1 success (300ms) = 3900ms
        assertTrue(totalTime >= 3600, "Expected retry delays, took ${totalTime}ms")
        
        // Verify user was created
        val allUsers = mockRepository.getAllUsers()
        assertTrue(allUsers.containsKey("retry-user"))
        
        println("✅ Retry with exponential backoff test passed")
    }
    
    @Test
    fun testBatchProcessing() = runTest {
        println("=== Testing Batch Processing ===")
        
        val mockRepository = MockUserRepository()
        val userService = UserService(mockRepository)
        
        val users = (1..5).map { i ->
            User("batch-$i", "Batch User $i", "batch$i@example.com")
        }
        
        val startTime = currentTime
        val results = userService.batchProcessUsers(users)
        val endTime = currentTime
        val executionTime = endTime - startTime
        
        assertEquals(5, results.size)
        assertEquals(users, results)
        
        // Should execute concurrently, taking ~400ms (100ms processing + 300ms create) not 2000ms
        assertTrue(executionTime < 1000, "Batch processing should be concurrent, took ${executionTime}ms")
        
        // Verify all users were created
        val allUsers = mockRepository.getAllUsers()
        users.forEach { user ->
            assertTrue(allUsers.containsKey(user.id), "User ${user.id} should be created")
        }
        
        println("✅ Batch processing test passed")
    }
}

/**
 * Testing exception handling and error scenarios
 */
class ExceptionTestingPatterns {
    
    class NetworkService {
        suspend fun fetchWithRetry(url: String, maxRetries: Int = 3): String {
            repeat(maxRetries) { attempt ->
                try {
                    delay(100 * (attempt + 1)) // Increasing delay
                    
                    // Simulate different failure scenarios
                    when {
                        url.contains("timeout") -> throw TimeoutCancellationException("Request timed out")
                        url.contains("server-error") && attempt < 2 -> throw RuntimeException("Server error 500")
                        url.contains("not-found") -> throw IllegalArgumentException("Resource not found")
                        url.contains("network-error") && attempt == 0 -> throw RuntimeException("Network error")
                        else -> return "Response from $url"
                    }
                } catch (e: TimeoutCancellationException) {
                    throw e // Don't retry timeouts
                } catch (e: IllegalArgumentException) {
                    throw e // Don't retry client errors
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) throw e
                    delay(1000 * (attempt + 1)) // Exponential backoff
                }
            }
            error("Should not reach here")
        }
        
        suspend fun fetchConcurrently(urls: List<String>): Map<String, String> = coroutineScope {
            val results = mutableMapOf<String, String>()
            val exceptions = mutableMapOf<String, Exception>()
            
            urls.map { url ->
                async {
                    try {
                        val result = fetchWithRetry(url, maxRetries = 2)
                        results[url] = result
                    } catch (e: Exception) {
                        exceptions[url] = e
                    }
                }
            }.awaitAll()
            
            if (exceptions.isNotEmpty()) {
                throw RuntimeException("Failed URLs: ${exceptions.keys}, first error: ${exceptions.values.first().message}")
            }
            
            results
        }
    }
    
    @Test
    fun testSuccessfulRetry() = runTest {
        println("=== Testing Successful Retry ===")
        
        val networkService = NetworkService()
        
        // This should succeed after 2 retries
        val result = networkService.fetchWithRetry("http://example.com/server-error")
        
        assertEquals("Response from http://example.com/server-error", result)
        
        // Should take some time due to retries and backoff
        val currentTime = currentTime
        assertTrue(currentTime > 1000, "Expected retry delays, time was ${currentTime}ms")
        
        println("✅ Successful retry test passed")
    }
    
    @Test
    fun testTimeoutException() = runTest {
        println("=== Testing Timeout Exception ===")
        
        val networkService = NetworkService()
        
        // Should fail immediately with timeout (no retries)
        val exception = assertFailsWith<TimeoutCancellationException> {
            networkService.fetchWithRetry("http://example.com/timeout")
        }
        
        assertTrue(exception.message!!.contains("Request timed out"))
        
        // Should not take long since no retries for timeouts
        val currentTime = currentTime
        assertTrue(currentTime < 500, "Timeout should fail fast, time was ${currentTime}ms")
        
        println("✅ Timeout exception test passed")
    }
    
    @Test
    fun testClientErrorNoRetry() = runTest {
        println("=== Testing Client Error (No Retry) ===")
        
        val networkService = NetworkService()
        
        // Should fail immediately with client error (no retries)
        val exception = assertFailsWith<IllegalArgumentException> {
            networkService.fetchWithRetry("http://example.com/not-found")
        }
        
        assertTrue(exception.message!!.contains("Resource not found"))
        
        // Should fail quickly without retries
        val currentTime = currentTime
        assertTrue(currentTime < 500, "Client error should fail fast, time was ${currentTime}ms")
        
        println("✅ Client error test passed")
    }
    
    @Test
    fun testMaxRetriesExceeded() = runTest {
        println("=== Testing Max Retries Exceeded ===")
        
        val networkService = NetworkService()
        
        // This will always fail server-error, should exhaust retries
        val exception = assertFailsWith<RuntimeException> {
            networkService.fetchWithRetry("http://example.com/always-server-error", maxRetries = 3)
        }
        
        assertTrue(exception.message!!.contains("Server error 500"))
        
        // Should take time for all retries and backoffs
        val currentTime = currentTime
        assertTrue(currentTime > 3000, "Expected multiple retry delays, time was ${currentTime}ms")
        
        println("✅ Max retries exceeded test passed")
    }
    
    @Test
    fun testConcurrentExceptionHandling() = runTest {
        println("=== Testing Concurrent Exception Handling ===")
        
        val networkService = NetworkService()
        
        val urls = listOf(
            "http://example.com/success1",
            "http://example.com/success2",
            "http://example.com/timeout",
            "http://example.com/success3"
        )
        
        // Should fail because one URL times out
        val exception = assertFailsWith<RuntimeException> {
            networkService.fetchConcurrently(urls)
        }
        
        assertTrue(exception.message!!.contains("Failed URLs"))
        assertTrue(exception.message!!.contains("timeout"))
        
        println("✅ Concurrent exception handling test passed")
    }
}

/**
 * Testing performance and timing characteristics
 */
class PerformanceTestingPatterns {
    
    class PerformanceService {
        suspend fun cpuIntensiveTask(iterations: Int): Long {
            var result = 0L
            repeat(iterations) { i ->
                result += i
                if (i % 1000 == 0) {
                    yield() // Allow other coroutines to run
                }
            }
            return result
        }
        
        suspend fun ioIntensiveTask(requestCount: Int): List<String> {
            return (1..requestCount).map { i ->
                async {
                    delay(100) // Simulate IO delay
                    "Result-$i"
                }
            }.awaitAll()
        }
        
        suspend fun processInBatches(items: List<String>, batchSize: Int): List<String> {
            val results = mutableListOf<String>()
            
            items.chunked(batchSize).forEach { batch ->
                val batchResults = batch.map { item ->
                    async {
                        delay(50) // Process each item
                        "Processed-$item"
                    }
                }.awaitAll()
                
                results.addAll(batchResults)
                yield() // Allow other work
            }
            
            return results
        }
    }
    
    @Test
    fun testConcurrentPerformance() = runTest {
        println("=== Testing Concurrent Performance ===")
        
        val performanceService = PerformanceService()
        
        // Test sequential vs concurrent IO operations
        val startTimeSequential = currentTime
        val sequentialResults = mutableListOf<String>()
        repeat(5) { i ->
            delay(100) // Sequential IO operations
            sequentialResults.add("Sequential-Result-$i")
        }
        val sequentialTime = currentTime - startTimeSequential
        
        // Reset time for concurrent test
        val startTimeConcurrent = currentTime
        val concurrentResults = performanceService.ioIntensiveTask(5)
        val concurrentTime = currentTime - startTimeConcurrent
        
        // Verify results
        assertEquals(5, sequentialResults.size)
        assertEquals(5, concurrentResults.size)
        
        // Concurrent should be much faster
        assertTrue(concurrentTime < sequentialTime, 
            "Concurrent (${concurrentTime}ms) should be faster than sequential (${sequentialTime}ms)")
        
        println("  Sequential time: ${sequentialTime}ms")
        println("  Concurrent time: ${concurrentTime}ms")
        println("  Performance improvement: ${sequentialTime.toDouble() / concurrentTime}x")
        
        println("✅ Concurrent performance test passed")
    }
    
    @Test
    fun testBatchProcessingPerformance() = runTest {
        println("=== Testing Batch Processing Performance ===")
        
        val performanceService = PerformanceService()
        val items = (1..20).map { "Item-$it" }
        
        // Test different batch sizes
        val batchSizes = listOf(1, 5, 10, 20)
        val results = mutableMapOf<Int, Long>()
        
        for (batchSize in batchSizes) {
            val startTime = currentTime
            val batchResults = performanceService.processInBatches(items, batchSize)
            val processingTime = currentTime - startTime
            
            results[batchSize] = processingTime
            
            assertEquals(20, batchResults.size)
            assertTrue(batchResults.all { it.startsWith("Processed-Item-") })
        }
        
        // Print performance results
        results.forEach { (batchSize, time) ->
            println("  Batch size $batchSize: ${time}ms")
        }
        
        // Verify that larger batches (up to a point) are more efficient
        assertTrue(results[1]!! > results[5]!!, "Batch size 5 should be faster than size 1")
        
        println("✅ Batch processing performance test passed")
    }
    
    @Test
    fun testResourceUtilization() = runTest {
        println("=== Testing Resource Utilization ===")
        
        val performanceService = PerformanceService()
        
        // Test CPU-intensive task yielding behavior
        val startTime = currentTime
        var yieldCount = 0
        
        // Monitor yielding by running concurrent task
        val monitorJob = launch {
            while (isActive) {
                delay(1) // Very frequent checks
                yieldCount++
            }
        }
        
        val result = performanceService.cpuIntensiveTask(10000)
        monitorJob.cancel()
        
        val totalTime = currentTime - startTime
        
        assertTrue(result > 0, "CPU task should produce result")
        assertTrue(yieldCount > 0, "CPU task should yield to other coroutines")
        assertTrue(totalTime > 0, "CPU task should take some time")
        
        println("  CPU task result: $result")
        println("  Total time: ${totalTime}ms")
        println("  Monitor yield count: $yieldCount")
        
        println("✅ Resource utilization test passed")
    }
}

/**
 * Test utilities and helper functions
 */
object TestUtils {
    
    /**
     * Measure execution time of a suspend function
     */
    suspend fun <T> measureCoroutineTime(block: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        return result to (endTime - startTime)
    }
    
    /**
     * Assert that a suspend function completes within expected time
     */
    suspend fun <T> assertCompletesWithin(
        expectedMaxTime: Long,
        block: suspend () -> T
    ): T {
        val (result, actualTime) = measureCoroutineTime(block)
        assertTrue(actualTime <= expectedMaxTime, 
            "Expected completion within ${expectedMaxTime}ms, but took ${actualTime}ms")
        return result
    }
    
    /**
     * Assert that a suspend function takes at least minimum time
     */
    suspend fun <T> assertTakesAtLeast(
        expectedMinTime: Long,
        block: suspend () -> T
    ): T {
        val (result, actualTime) = measureCoroutineTime(block)
        assertTrue(actualTime >= expectedMinTime,
            "Expected minimum ${expectedMinTime}ms, but took only ${actualTime}ms")
        return result
    }
    
    /**
     * Create a test dispatcher with custom behavior
     */
    fun createTestDispatcher(): TestDispatcher {
        return StandardTestDispatcher()
    }
    
    /**
     * Verify that a job completes successfully
     */
    suspend fun assertJobCompletes(job: Job, timeoutMs: Long = 5000) {
        withTimeout(timeoutMs) {
            job.join()
        }
        assertTrue(job.isCompleted, "Job should be completed")
        assertFalse(job.isCancelled, "Job should not be cancelled")
    }
    
    /**
     * Verify that a job is cancelled
     */
    suspend fun assertJobCancelled(job: Job, timeoutMs: Long = 5000) {
        withTimeout(timeoutMs) {
            try {
                job.join()
            } catch (e: CancellationException) {
                // Expected
            }
        }
        assertTrue(job.isCancelled, "Job should be cancelled")
    }
}

/**
 * Main demonstration function showing test execution
 */
fun main() {
    // Note: In real testing, these would be run by a test framework like JUnit
    println("=== Coroutine Testing Fundamentals Demo ===")
    println("This file contains comprehensive test patterns for coroutines.")
    println("In a real project, these tests would be executed by:")
    println("- JUnit 5 with @Test annotations")
    println("- Kotlin test framework")
    println("- TestNG or other testing frameworks")
    println()
    println("Key Testing Concepts Covered:")
    println("✅ runTest for controlled coroutine testing")
    println("✅ Virtual time advancement with TestCoroutineScheduler")
    println("✅ Testing concurrent operations and timing")
    println("✅ Exception handling and retry mechanism testing")
    println("✅ Performance and resource utilization testing")
    println("✅ Mock repositories and dependency injection")
    println("✅ Test utilities and helper functions")
    println()
    println("To run these tests in a real project:")
    println("1. Add kotlinx-coroutines-test dependency")
    println("2. Add kotlin-test or JUnit 5 dependency")
    println("3. Configure test runner in build.gradle.kts")
    println("4. Run tests with: ./gradlew test")
    println()
    println("=== Testing Best Practices Summary ===")
    println("✅ Use runTest instead of runBlocking in tests")
    println("✅ Test both success and failure scenarios")
    println("✅ Verify timing and performance characteristics")
    println("✅ Test cancellation and exception propagation")
    println("✅ Use virtual time to make tests deterministic")
    println("✅ Mock external dependencies properly")
    println("✅ Test concurrent scenarios with proper assertions")
    println("✅ Verify resource cleanup and proper disposal")
}