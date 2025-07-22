/**
 * # Testing Best Practices for Kotlin Coroutines
 * 
 * ## Problem Description
 * Comprehensive testing of coroutine-based applications requires a holistic approach
 * that combines unit testing, integration testing, performance testing, and operational
 * testing. Each level of testing presents unique challenges and requires specific
 * strategies to ensure reliable, maintainable, and scalable test suites. Without
 * proper testing practices, coroutine applications can suffer from flaky tests,
 * poor coverage, and difficult-to-diagnose production issues.
 * 
 * ## Solution Approach
 * Best practices for coroutine testing include:
 * - Establishing testing pyramids specific to async applications
 * - Implementing comprehensive test coverage strategies
 * - Using dependency injection for testable coroutine architectures
 * - Creating reusable testing utilities and frameworks
 * - Integrating testing with CI/CD pipelines for continuous validation
 * 
 * ## Key Learning Points
 * - Test pyramid adapted for coroutine applications (unit, integration, e2e)
 * - Dependency injection patterns for testable coroutine code
 * - Test data builders and fixtures for complex async scenarios
 * - Contract testing for coroutine-based APIs and services
 * - Monitoring and observability in test environments
 * 
 * ## Performance Considerations
 * - Test execution speed optimized through parallel execution
 * - Resource usage controlled through proper test isolation
 * - CI/CD pipeline efficiency through intelligent test selection
 * - Test data management for large-scale async operations
 * - Memory and CPU profiling in test environments
 * 
 * ## Common Pitfalls
 * - Inadequate test coverage of async edge cases
 * - Flaky tests due to timing dependencies
 * - Poor test organization and maintainability
 * - Insufficient integration testing of coroutine interactions
 * - Missing performance regression detection
 * 
 * ## Real-World Applications
 * - Microservices testing with async communication
 * - API testing with concurrent request handling
 * - Event-driven systems validation
 * - Batch processing pipeline testing
 * - Real-time system performance validation
 * 
 * ## Related Concepts
 * - Test-driven development (TDD) with coroutines
 * - Behavior-driven development (BDD) patterns
 * - Contract testing methodologies
 * - Chaos engineering for async systems
 */

package testing.debugging.bestpractices

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test Architecture and Organization Patterns
 * 
 * Testing Architecture:
 * 
 * Test Pyramid for Coroutines:
 * ├── Unit Tests ────────── Suspend functions, coroutine builders
 * ├── Integration Tests ──── Service interactions, flow pipelines
 * ├── Contract Tests ─────── API contracts, protocol compliance
 * └── End-to-End Tests ───── Full system behavior, performance
 * 
 * Test Organization:
 * Setup -> Execute -> Verify -> Cleanup (with async considerations)
 */
class TestArchitectureBestPractices {
    
    // Example domain model for testing
    data class User(val id: String, val name: String, val email: String)
    data class Order(val id: String, val userId: String, val items: List<String>, val total: Double)
    data class Notification(val id: String, val userId: String, val message: String, val timestamp: Long)
    
    // Repository interfaces for dependency injection
    interface UserRepository {
        suspend fun findById(id: String): User?
        suspend fun save(user: User): User
        suspend fun findAll(): List<User>
    }
    
    interface OrderRepository {
        suspend fun findById(id: String): Order?
        suspend fun findByUserId(userId: String): List<Order>
        suspend fun save(order: Order): Order
    }
    
    interface NotificationService {
        suspend fun sendNotification(notification: Notification): Boolean
        fun subscribeToNotifications(userId: String): Flow<Notification>
    }
    
    // Service under test with dependency injection
    class OrderService(
        private val userRepository: UserRepository,
        private val orderRepository: OrderRepository,
        private val notificationService: NotificationService
    ) {
        suspend fun createOrder(userId: String, items: List<String>): Order {
            // Validate user exists
            val user = userRepository.findById(userId) 
                ?: throw IllegalArgumentException("User not found: $userId")
            
            // Calculate total (simplified)
            val total = items.size * 10.0
            
            // Create order
            val order = Order(
                id = generateOrderId(),
                userId = userId,
                items = items,
                total = total
            )
            
            // Save order
            val savedOrder = orderRepository.save(order)
            
            // Send notification asynchronously
            launch {
                val notification = Notification(
                    id = generateNotificationId(),
                    userId = userId,
                    message = "Order ${order.id} created successfully",
                    timestamp = System.currentTimeMillis()
                )
                notificationService.sendNotification(notification)
            }
            
            return savedOrder
        }
        
        suspend fun getUserOrders(userId: String): List<Order> {
            // Validate user exists
            userRepository.findById(userId) 
                ?: throw IllegalArgumentException("User not found: $userId")
            
            return orderRepository.findByUserId(userId)
        }
        
        fun getOrderNotifications(userId: String): Flow<Notification> {
            return notificationService.subscribeToNotifications(userId)
                .filter { it.message.contains("Order") }
        }
        
        private fun generateOrderId(): String = "order-${System.currentTimeMillis()}"
        private fun generateNotificationId(): String = "notification-${System.currentTimeMillis()}"
    }
    
    // Test implementations of dependencies
    class TestUserRepository : UserRepository {
        private val users = mutableMapOf<String, User>()
        
        override suspend fun findById(id: String): User? {
            delay(10) // Simulate async operation
            return users[id]
        }
        
        override suspend fun save(user: User): User {
            delay(20) // Simulate async operation
            users[user.id] = user
            return user
        }
        
        override suspend fun findAll(): List<User> {
            delay(30) // Simulate async operation
            return users.values.toList()
        }
        
        fun addUser(user: User) {
            users[user.id] = user
        }
        
        fun clear() {
            users.clear()
        }
    }
    
    class TestOrderRepository : OrderRepository {
        private val orders = mutableMapOf<String, Order>()
        
        override suspend fun findById(id: String): Order? {
            delay(15) // Simulate async operation
            return orders[id]
        }
        
        override suspend fun findByUserId(userId: String): List<Order> {
            delay(25) // Simulate async operation
            return orders.values.filter { it.userId == userId }
        }
        
        override suspend fun save(order: Order): Order {
            delay(35) // Simulate async operation
            orders[order.id] = order
            return order
        }
        
        fun clear() {
            orders.clear()
        }
    }
    
    class TestNotificationService : NotificationService {
        private val notifications = mutableListOf<Notification>()
        private val subscribers = mutableMapOf<String, Channel<Notification>>()
        
        override suspend fun sendNotification(notification: Notification): Boolean {
            delay(5) // Simulate async operation
            notifications.add(notification)
            
            // Send to subscribers
            subscribers[notification.userId]?.trySend(notification)
            
            return true
        }
        
        override fun subscribeToNotifications(userId: String): Flow<Notification> {
            val channel = Channel<Notification>(Channel.UNLIMITED)
            subscribers[userId] = channel
            
            return channel.receiveAsFlow()
        }
        
        fun getNotifications(): List<Notification> = notifications.toList()
        
        fun clear() {
            notifications.clear()
            subscribers.values.forEach { it.close() }
            subscribers.clear()
        }
    }
    
    @Test
    fun testOrderServiceUnit_CreateOrder() = runTest {
        println("=== Unit Test: Create Order ===")
        
        // Arrange
        val userRepo = TestUserRepository()
        val orderRepo = TestOrderRepository()
        val notificationService = TestNotificationService()
        
        val orderService = OrderService(userRepo, orderRepo, notificationService)
        
        val testUser = User("user1", "John Doe", "john@example.com")
        userRepo.addUser(testUser)
        
        val items = listOf("item1", "item2", "item3")
        
        // Act
        val order = orderService.createOrder("user1", items)
        
        // Assert
        assertNotNull(order)
        assertEquals("user1", order.userId)
        assertEquals(items, order.items)
        assertEquals(30.0, order.total) // 3 items * 10.0 each
        
        // Verify notification was sent (with small delay for async operation)
        advanceTimeBy(100)
        val notifications = notificationService.getNotifications()
        assertEquals(1, notifications.size)
        assertTrue(notifications[0].message.contains("Order ${order.id} created"))
        
        println("✅ Unit test passed")
    }
    
    @Test
    fun testOrderServiceUnit_UserNotFound() = runTest {
        println("=== Unit Test: User Not Found ===")
        
        // Arrange
        val userRepo = TestUserRepository()
        val orderRepo = TestOrderRepository()
        val notificationService = TestNotificationService()
        
        val orderService = OrderService(userRepo, orderRepo, notificationService)
        
        // Act & Assert
        val exception = assertFailsWith<IllegalArgumentException> {
            orderService.createOrder("nonexistent", listOf("item1"))
        }
        
        assertEquals("User not found: nonexistent", exception.message)
        
        // Verify no notification was sent
        assertTrue(notificationService.getNotifications().isEmpty())
        
        println("✅ Unit test passed")
    }
    
    @Test
    fun testOrderServiceIntegration_NotificationFlow() = runTest {
        println("=== Integration Test: Notification Flow ===")
        
        // Arrange
        val userRepo = TestUserRepository()
        val orderRepo = TestOrderRepository()
        val notificationService = TestNotificationService()
        
        val orderService = OrderService(userRepo, orderRepo, notificationService)
        
        val testUser = User("user2", "Jane Doe", "jane@example.com")
        userRepo.addUser(testUser)
        
        // Subscribe to notifications
        val notifications = mutableListOf<Notification>()
        val notificationJob = launch {
            orderService.getOrderNotifications("user2").collect { notification ->
                notifications.add(notification)
            }
        }
        
        // Act - Create multiple orders
        val order1 = orderService.createOrder("user2", listOf("item1"))
        val order2 = orderService.createOrder("user2", listOf("item2", "item3"))
        
        // Give time for async notifications
        advanceTimeBy(200)
        
        // Assert
        assertEquals(2, notifications.size)
        assertTrue(notifications[0].message.contains("Order ${order1.id}"))
        assertTrue(notifications[1].message.contains("Order ${order2.id}"))
        
        notificationJob.cancel()
        println("✅ Integration test passed")
    }
    
    @Test
    fun testOrderServicePerformance_ConcurrentOrders() = runTest {
        println("=== Performance Test: Concurrent Orders ===")
        
        // Arrange
        val userRepo = TestUserRepository()
        val orderRepo = TestOrderRepository()
        val notificationService = TestNotificationService()
        
        val orderService = OrderService(userRepo, orderRepo, notificationService)
        
        // Create multiple test users
        repeat(10) { i ->
            userRepo.addUser(User("user$i", "User $i", "user$i@example.com"))
        }
        
        val startTime = currentTime
        
        // Act - Create orders concurrently
        val orders = (0 until 10).map { i ->
            async {
                orderService.createOrder("user$i", listOf("item$i"))
            }
        }.awaitAll()
        
        val endTime = currentTime
        val executionTime = endTime - startTime
        
        // Assert
        assertEquals(10, orders.size)
        assertTrue(executionTime < 500, "Concurrent execution should be efficient, took ${executionTime}ms")
        
        // Verify all notifications were sent
        advanceTimeBy(200)
        val allNotifications = notificationService.getNotifications()
        assertEquals(10, allNotifications.size)
        
        println("  Created ${orders.size} orders in ${executionTime}ms")
        println("✅ Performance test passed")
    }
}

/**
 * Test Data Management and Fixtures
 */
class TestDataManagementPatterns {
    
    // Test data builders
    class UserBuilder {
        private var id: String = "default-user"
        private var name: String = "Default User"
        private var email: String = "default@example.com"
        
        fun withId(id: String) = apply { this.id = id }
        fun withName(name: String) = apply { this.name = name }
        fun withEmail(email: String) = apply { this.email = email }
        
        fun build() = User(id, name, email)
    }
    
    class OrderBuilder {
        private var id: String = "default-order"
        private var userId: String = "default-user"
        private var items: List<String> = listOf("default-item")
        private var total: Double = 10.0
        
        fun withId(id: String) = apply { this.id = id }
        fun withUserId(userId: String) = apply { this.userId = userId }
        fun withItems(items: List<String>) = apply { this.items = items; this.total = items.size * 10.0 }
        fun withTotal(total: Double) = apply { this.total = total }
        
        fun build() = Order(id, userId, items, total)
    }
    
    class NotificationBuilder {
        private var id: String = "default-notification"
        private var userId: String = "default-user"
        private var message: String = "Default message"
        private var timestamp: Long = System.currentTimeMillis()
        
        fun withId(id: String) = apply { this.id = id }
        fun withUserId(userId: String) = apply { this.userId = userId }
        fun withMessage(message: String) = apply { this.message = message }
        fun withTimestamp(timestamp: Long) = apply { this.timestamp = timestamp }
        
        fun build() = Notification(id, userId, message, timestamp)
    }
    
    // Test fixture management
    class TestFixtures {
        private val users = mutableMapOf<String, User>()
        private val orders = mutableMapOf<String, Order>()
        private val notifications = mutableMapOf<String, Notification>()
        
        fun addUser(user: User) = apply { users[user.id] = user }
        fun addOrder(order: Order) = apply { orders[order.id] = order }
        fun addNotification(notification: Notification) = apply { notifications[notification.id] = notification }
        
        fun user(id: String) = users[id] ?: throw IllegalStateException("User $id not found in fixtures")
        fun order(id: String) = orders[id] ?: throw IllegalStateException("Order $id not found in fixtures")
        fun notification(id: String) = notifications[id] ?: throw IllegalStateException("Notification $id not found in fixtures")
        
        fun allUsers() = users.values.toList()
        fun allOrders() = orders.values.toList()
        fun allNotifications() = notifications.values.toList()
        
        fun clear() {
            users.clear()
            orders.clear()
            notifications.clear()
        }
        
        // Predefined scenarios
        companion object {
            fun basicScenario() = TestFixtures().apply {
                addUser(UserBuilder().withId("user1").withName("Alice").withEmail("alice@example.com").build())
                addUser(UserBuilder().withId("user2").withName("Bob").withEmail("bob@example.com").build())
                
                addOrder(OrderBuilder().withId("order1").withUserId("user1").withItems(listOf("item1", "item2")).build())
                addOrder(OrderBuilder().withId("order2").withUserId("user2").withItems(listOf("item3")).build())
                
                addNotification(NotificationBuilder().withId("notif1").withUserId("user1").withMessage("Order order1 created").build())
            }
            
            fun performanceScenario(userCount: Int, ordersPerUser: Int) = TestFixtures().apply {
                repeat(userCount) { i ->
                    val userId = "perfuser$i"
                    addUser(UserBuilder().withId(userId).withName("Performance User $i").withEmail("$userId@example.com").build())
                    
                    repeat(ordersPerUser) { j ->
                        val orderId = "perforder${i}_$j"
                        addOrder(OrderBuilder().withId(orderId).withUserId(userId).withItems(listOf("item$j")).build())
                    }
                }
            }
        }
    }
    
    // Async test data generator
    class AsyncTestDataGenerator {
        suspend fun generateUsers(count: Int, delayMs: Long = 0): Flow<User> = flow {
            repeat(count) { i ->
                if (delayMs > 0) delay(delayMs)
                val user = UserBuilder()
                    .withId("generated-user-$i")
                    .withName("Generated User $i")
                    .withEmail("generated$i@example.com")
                    .build()
                emit(user)
            }
        }
        
        suspend fun generateOrdersForUser(userId: String, count: Int, delayMs: Long = 0): Flow<Order> = flow {
            repeat(count) { i ->
                if (delayMs > 0) delay(delayMs)
                val order = OrderBuilder()
                    .withId("generated-order-$userId-$i")
                    .withUserId(userId)
                    .withItems(listOf("generated-item-$i"))
                    .build()
                emit(order)
            }
        }
        
        suspend fun generateNotificationStream(
            userId: String, 
            count: Int, 
            intervalMs: Long = 100
        ): Flow<Notification> = flow {
            repeat(count) { i ->
                delay(intervalMs)
                val notification = NotificationBuilder()
                    .withId("stream-notif-$i")
                    .withUserId(userId)
                    .withMessage("Notification $i for $userId")
                    .build()
                emit(notification)
            }
        }
    }
    
    @Test
    fun testBuilderPatterns() = runTest {
        println("=== Test: Builder Patterns ===")
        
        // Test user builder
        val user = UserBuilder()
            .withId("test-user")
            .withName("Test User")
            .withEmail("test@example.com")
            .build()
        
        assertEquals("test-user", user.id)
        assertEquals("Test User", user.name)
        assertEquals("test@example.com", user.email)
        
        // Test order builder
        val order = OrderBuilder()
            .withId("test-order")
            .withUserId("test-user")
            .withItems(listOf("item1", "item2", "item3"))
            .build()
        
        assertEquals("test-order", order.id)
        assertEquals("test-user", order.userId)
        assertEquals(3, order.items.size)
        assertEquals(30.0, order.total) // Auto-calculated
        
        println("✅ Builder patterns test passed")
    }
    
    @Test
    fun testFixtureManagement() = runTest {
        println("=== Test: Fixture Management ===")
        
        val fixtures = TestFixtures.basicScenario()
        
        // Test predefined data
        val alice = fixtures.user("user1")
        assertEquals("Alice", alice.name)
        
        val order1 = fixtures.order("order1")
        assertEquals("user1", order1.userId)
        assertEquals(2, order1.items.size)
        
        // Test collections
        val allUsers = fixtures.allUsers()
        assertEquals(2, allUsers.size)
        
        val allOrders = fixtures.allOrders()
        assertEquals(2, allOrders.size)
        
        println("✅ Fixture management test passed")
    }
    
    @Test
    fun testAsyncDataGeneration() = runTest {
        println("=== Test: Async Data Generation ===")
        
        val generator = AsyncTestDataGenerator()
        
        // Test user generation
        val users = generator.generateUsers(5, delayMs = 10).toList()
        assertEquals(5, users.size)
        assertTrue(users.all { it.name.startsWith("Generated User") })
        
        // Test order generation for user
        val orders = generator.generateOrdersForUser("test-user", 3, delayMs = 5).toList()
        assertEquals(3, orders.size)
        assertTrue(orders.all { it.userId == "test-user" })
        
        // Test notification stream
        val notifications = mutableListOf<Notification>()
        val job = launch {
            generator.generateNotificationStream("test-user", 3, intervalMs = 50)
                .collect { notifications.add(it) }
        }
        
        job.join()
        assertEquals(3, notifications.size)
        assertTrue(notifications.all { it.userId == "test-user" })
        
        println("✅ Async data generation test passed")
    }
}

/**
 * Test Utilities and Framework Integration
 */
class TestUtilitiesAndFrameworks {
    
    // Custom test runner for coroutine tests
    class CoroutineTestRunner {
        private val testResults = mutableListOf<TestResult>()
        
        data class TestResult(
            val testName: String,
            val success: Boolean,
            val executionTime: Long,
            val error: Throwable? = null
        )
        
        suspend fun runTest(testName: String, test: suspend () -> Unit): TestResult {
            println("🚀 Running test: $testName")
            val startTime = System.currentTimeMillis()
            
            return try {
                test()
                val executionTime = System.currentTimeMillis() - startTime
                val result = TestResult(testName, true, executionTime)
                testResults.add(result)
                println("✅ Test passed: $testName (${executionTime}ms)")
                result
            } catch (e: Throwable) {
                val executionTime = System.currentTimeMillis() - startTime
                val result = TestResult(testName, false, executionTime, e)
                testResults.add(result)
                println("❌ Test failed: $testName (${executionTime}ms) - ${e.message}")
                result
            }
        }
        
        fun runTestSuite(tests: Map<String, suspend () -> Unit>) = runBlocking {
            tests.forEach { (name, test) ->
                runTest(name, test)
            }
        }
        
        fun generateReport(): String = buildString {
            appendLine("=== Test Suite Report ===")
            val totalTests = testResults.size
            val passedTests = testResults.count { it.success }
            val failedTests = totalTests - passedTests
            
            appendLine("Total Tests: $totalTests")
            appendLine("Passed: $passedTests")
            appendLine("Failed: $failedTests")
            
            if (testResults.isNotEmpty()) {
                val avgTime = testResults.map { it.executionTime }.average()
                appendLine("Average Execution Time: ${avgTime.toInt()}ms")
                
                val slowestTest = testResults.maxByOrNull { it.executionTime }
                slowestTest?.let {
                    appendLine("Slowest Test: ${it.testName} (${it.executionTime}ms)")
                }
            }
            
            if (failedTests > 0) {
                appendLine("\nFailed Tests:")
                testResults.filter { !it.success }.forEach { result ->
                    appendLine("  - ${result.testName}: ${result.error?.message}")
                }
            }
        }
        
        fun getResults(): List<TestResult> = testResults.toList()
    }
    
    // Test assertion utilities
    object CoroutineAssertions {
        
        suspend fun assertCompletes(
            timeout: Long = 5000,
            operation: suspend () -> Unit
        ) {
            withTimeout(timeout) {
                operation()
            }
        }
        
        suspend fun assertCompletesWithin(
            expectedMaxTime: Long,
            operation: suspend () -> Unit
        ) = runTest {
            val startTime = currentTime
            operation()
            val actualTime = currentTime - startTime
            assertTrue(actualTime <= expectedMaxTime, 
                "Expected completion within ${expectedMaxTime}ms, but took ${actualTime}ms")
        }
        
        suspend fun assertTakesAtLeast(
            expectedMinTime: Long,
            operation: suspend () -> Unit
        ) = runTest {
            val startTime = currentTime
            operation()
            val actualTime = currentTime - startTime
            assertTrue(actualTime >= expectedMinTime,
                "Expected at least ${expectedMinTime}ms, but took only ${actualTime}ms")
        }
        
        suspend fun <T> assertFlowEmits(
            flow: Flow<T>,
            vararg expectedValues: T
        ) {
            val actualValues = flow.toList()
            assertEquals(expectedValues.toList(), actualValues)
        }
        
        suspend fun <T> assertFlowEmitsInAnyOrder(
            flow: Flow<T>,
            vararg expectedValues: T
        ) {
            val actualValues = flow.toList().toSet()
            val expectedSet = expectedValues.toSet()
            assertEquals(expectedSet, actualValues)
        }
        
        suspend fun assertChannelReceives(
            channel: ReceiveChannel<*>,
            expectedCount: Int,
            timeout: Long = 1000
        ) {
            var count = 0
            withTimeout(timeout) {
                for (item in channel) {
                    count++
                    if (count >= expectedCount) break
                }
            }
            assertEquals(expectedCount, count)
        }
        
        fun assertJobCompleted(job: Job) {
            assertTrue(job.isCompleted, "Job should be completed")
            assertFalse(job.isCancelled, "Job should not be cancelled")
        }
        
        fun assertJobCancelled(job: Job) {
            assertTrue(job.isCancelled, "Job should be cancelled")
        }
    }
    
    // Mock and stub utilities
    class MockCoroutineService<T> {
        private var responses: List<T> = emptyList()
        private var delays: List<Long> = emptyList()
        private var failures: List<Exception> = emptyList()
        private var callCount = 0
        
        fun willReturn(vararg values: T) = apply {
            responses = values.toList()
        }
        
        fun willDelay(vararg delayMs: Long) = apply {
            delays = delayMs.toList()
        }
        
        fun willFail(vararg exceptions: Exception) = apply {
            failures = exceptions.toList()
        }
        
        suspend fun call(): T {
            val index = callCount++
            
            // Apply delay if configured
            if (index < delays.size) {
                delay(delays[index])
            }
            
            // Fail if configured
            if (index < failures.size) {
                throw failures[index]
            }
            
            // Return response if available
            if (index < responses.size) {
                return responses[index]
            }
            
            throw IllegalStateException("Mock service not configured for call $index")
        }
        
        fun getCallCount(): Int = callCount
        
        fun reset() {
            callCount = 0
            responses = emptyList()
            delays = emptyList()
            failures = emptyList()
        }
    }
    
    @Test
    fun testCoroutineTestRunner() = runTest {
        println("=== Test: Coroutine Test Runner ===")
        
        val runner = CoroutineTestRunner()
        
        // Define test suite
        val testSuite = mapOf(
            "passing_test" to {
                delay(50)
                assertEquals(2, 1 + 1)
            },
            "failing_test" to {
                delay(25)
                assertEquals(3, 1 + 1) // This will fail
            },
            "slow_test" to {
                delay(200)
                assertTrue(true)
            }
        )
        
        // Run test suite
        runner.runTestSuite(testSuite)
        
        // Verify results
        val results = runner.getResults()
        assertEquals(3, results.size)
        
        val passedCount = results.count { it.success }
        val failedCount = results.count { !it.success }
        
        assertEquals(2, passedCount)
        assertEquals(1, failedCount)
        
        // Generate and verify report
        val report = runner.generateReport()
        assertTrue(report.contains("Total Tests: 3"))
        assertTrue(report.contains("Passed: 2"))
        assertTrue(report.contains("Failed: 1"))
        
        println("  Generated report:\n$report")
        println("✅ Test runner test passed")
    }
    
    @Test
    fun testCoroutineAssertions() = runTest {
        println("=== Test: Coroutine Assertions ===")
        
        // Test completion assertions
        CoroutineAssertions.assertCompletes(1000) {
            delay(100)
        }
        
        CoroutineAssertions.assertCompletesWithin(200) {
            delay(50)
        }
        
        CoroutineAssertions.assertTakesAtLeast(100) {
            delay(150)
        }
        
        // Test flow assertions
        val testFlow = flowOf(1, 2, 3)
        CoroutineAssertions.assertFlowEmits(testFlow, 1, 2, 3)
        
        // Test channel assertions
        val channel = Channel<String>()
        launch {
            repeat(3) { i ->
                channel.send("item$i")
                delay(25)
            }
            channel.close()
        }
        
        CoroutineAssertions.assertChannelReceives(channel, 3, 1000)
        
        // Test job assertions
        val job = launch {
            delay(50)
        }
        job.join()
        CoroutineAssertions.assertJobCompleted(job)
        
        println("✅ Coroutine assertions test passed")
    }
    
    @Test
    fun testMockCoroutineService() = runTest {
        println("=== Test: Mock Coroutine Service ===")
        
        val mockService = MockCoroutineService<String>()
        
        // Configure mock behavior
        mockService
            .willReturn("response1", "response2", "response3")
            .willDelay(50, 25, 75)
            .willFail(RuntimeException("Test error"))
        
        // Test successful calls
        val response1 = mockService.call()
        assertEquals("response1", response1)
        
        val response2 = mockService.call()
        assertEquals("response2", response2)
        
        val response3 = mockService.call()
        assertEquals("response3", response3)
        
        // Test failure
        assertFailsWith<RuntimeException> {
            mockService.call()
        }
        
        assertEquals(4, mockService.getCallCount())
        
        println("✅ Mock service test passed")
    }
}

/**
 * CI/CD Integration and Test Automation
 */
class CICDIntegrationPatterns {
    
    // Test categorization for CI/CD pipelines
    enum class TestCategory {
        UNIT, INTEGRATION, PERFORMANCE, CONTRACT, E2E
    }
    
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class TestCategory(val category: TestUtilitiesAndFrameworks.TestCategory)
    
    @Target(AnnotationTarget.FUNCTION)  
    @Retention(AnnotationRetention.RUNTIME)
    annotation class SlowTest
    
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class FlakyTest
    
    // CI/CD test configuration
    data class TestConfiguration(
        val maxParallelism: Int = 4,
        val defaultTimeout: Long = 30_000,
        val retryAttempts: Int = 0,
        val enabledCategories: Set<TestUtilitiesAndFrameworks.TestCategory> = setOf(
            TestUtilitiesAndFrameworks.TestCategory.UNIT,
            TestUtilitiesAndFrameworks.TestCategory.INTEGRATION
        )
    )
    
    // Automated test environment management
    class TestEnvironmentManager {
        private var isSetup = false
        private val resources = mutableListOf<suspend () -> Unit>()
        
        suspend fun setupEnvironment() {
            if (isSetup) return
            
            println("🔧 Setting up test environment...")
            
            // Simulate environment setup
            delay(100)
            
            // Setup test databases, services, etc.
            setupTestDatabase()
            setupTestServices()
            
            isSetup = true
            println("✅ Test environment ready")
        }
        
        suspend fun teardownEnvironment() {
            if (!isSetup) return
            
            println("🧹 Tearing down test environment...")
            
            // Cleanup in reverse order
            resources.reversed().forEach { cleanup ->
                try {
                    cleanup()
                } catch (e: Exception) {
                    println("⚠️  Cleanup error: ${e.message}")
                }
            }
            
            resources.clear()
            isSetup = false
            println("✅ Test environment cleaned up")
        }
        
        private suspend fun setupTestDatabase() {
            delay(50)
            resources.add { 
                println("  Cleaning up test database")
            }
        }
        
        private suspend fun setupTestServices() {
            delay(30)
            resources.add {
                println("  Stopping test services")
            }
        }
        
        fun isEnvironmentReady(): Boolean = isSetup
    }
    
    // Test result reporting for CI/CD
    data class CICDTestReport(
        val timestamp: Long,
        val environment: String,
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val skippedTests: Int,
        val executionTime: Long,
        val coverage: Double,
        val categories: Map<TestUtilitiesAndFrameworks.TestCategory, Int>
    ) {
        val successRate: Double get() = if (totalTests > 0) passedTests.toDouble() / totalTests else 0.0
        
        fun toJUnit(): String = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<testsuite name=\"CoroutineTests\" tests=\"$totalTests\" failures=\"$failedTests\" skipped=\"$skippedTests\" time=\"${executionTime / 1000.0}\">")
            appendLine("</testsuite>")
        }
        
        fun toCoverageReport(): String = buildString {
            appendLine("Coverage Report:")
            appendLine("Overall Coverage: ${String.format("%.1f", coverage * 100)}%")
            appendLine("Test Categories:")
            categories.forEach { (category, count) ->
                appendLine("  $category: $count tests")
            }
        }
    }
    
    // Parallel test execution for CI/CD
    class ParallelTestExecutor(
        private val maxConcurrency: Int = 4,
        private val defaultTimeout: Long = 30_000
    ) {
        suspend fun executeTestsInParallel(
            tests: Map<String, suspend () -> Unit>
        ): List<TestUtilitiesAndFrameworks.CoroutineTestRunner.TestResult> {
            
            val testRunner = TestUtilitiesAndFrameworks.CoroutineTestRunner()
            val semaphore = Semaphore(maxConcurrency)
            
            return coroutineScope {
                tests.map { (testName, test) ->
                    async {
                        semaphore.withPermit {
                            withTimeout(defaultTimeout) {
                                testRunner.runTest(testName, test)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }
    
    @Test
    fun testEnvironmentManagement() = runTest {
        println("=== Test: Environment Management ===")
        
        val envManager = TestEnvironmentManager()
        
        // Verify initial state
        assertFalse(envManager.isEnvironmentReady())
        
        // Setup environment
        envManager.setupEnvironment()
        assertTrue(envManager.isEnvironmentReady())
        
        // Teardown environment
        envManager.teardownEnvironment()
        assertFalse(envManager.isEnvironmentReady())
        
        println("✅ Environment management test passed")
    }
    
    @Test
    fun testParallelExecution() = runTest {
        println("=== Test: Parallel Test Execution ===")
        
        val executor = ParallelTestExecutor(maxConcurrency = 2, defaultTimeout = 1000)
        
        val testSuite = mapOf(
            "fast_test_1" to { delay(50) },
            "fast_test_2" to { delay(75) },
            "medium_test_1" to { delay(100) },
            "medium_test_2" to { delay(125) },
            "slow_test" to { delay(200) }
        )
        
        val startTime = currentTime
        val results = executor.executeTestsInParallel(testSuite)
        val executionTime = currentTime - startTime
        
        // Verify all tests completed
        assertEquals(5, results.size)
        assertTrue(results.all { it.success })
        
        // Verify parallel execution efficiency
        assertTrue(executionTime < 500, "Parallel execution should be efficient, took ${executionTime}ms")
        
        println("  Executed ${results.size} tests in ${executionTime}ms")
        println("✅ Parallel execution test passed")
    }
    
    @Test
    fun testReportGeneration() = runTest {
        println("=== Test: Report Generation ===")
        
        val report = CICDTestReport(
            timestamp = System.currentTimeMillis(),
            environment = "CI",
            totalTests = 100,
            passedTests = 95,
            failedTests = 3,
            skippedTests = 2,
            executionTime = 45_000,
            coverage = 0.87,
            categories = mapOf(
                TestUtilitiesAndFrameworks.TestCategory.UNIT to 70,
                TestUtilitiesAndFrameworks.TestCategory.INTEGRATION to 25,
                TestUtilitiesAndFrameworks.TestCategory.PERFORMANCE to 5
            )
        )
        
        // Verify calculated properties
        assertEquals(0.95, report.successRate)
        
        // Test JUnit report generation
        val junitReport = report.toJUnit()
        assertTrue(junitReport.contains("tests=\"100\""))
        assertTrue(junitReport.contains("failures=\"3\""))
        
        // Test coverage report generation
        val coverageReport = report.toCoverageReport()
        assertTrue(coverageReport.contains("87.0%"))
        assertTrue(coverageReport.contains("UNIT: 70"))
        
        println("  JUnit Report:\n$junitReport")
        println("  Coverage Report:\n$coverageReport")
        println("✅ Report generation test passed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Coroutine Testing Best Practices Demo ===")
    
    try {
        // Test architecture patterns
        val architecture = TestArchitectureBestPractices()
        architecture.testOrderServiceUnit_CreateOrder()
        architecture.testOrderServiceUnit_UserNotFound()
        architecture.testOrderServiceIntegration_NotificationFlow()
        architecture.testOrderServicePerformance_ConcurrentOrders()
        println()
        
        // Test data management patterns
        val dataManagement = TestDataManagementPatterns()
        dataManagement.testBuilderPatterns()
        dataManagement.testFixtureManagement()
        dataManagement.testAsyncDataGeneration()
        println()
        
        // Test utilities and frameworks
        val utilities = TestUtilitiesAndFrameworks()
        utilities.testCoroutineTestRunner()
        utilities.testCoroutineAssertions()
        utilities.testMockCoroutineService()
        println()
        
        // CI/CD integration patterns
        val cicd = CICDIntegrationPatterns()
        cicd.testEnvironmentManagement()
        cicd.testParallelExecution()
        cicd.testReportGeneration()
        println()
        
        println("=== All Testing Best Practices Demos Completed ===")
        println()
        
        println("Key Testing Best Practices Covered:")
        println("✅ Test architecture and organization patterns")
        println("✅ Dependency injection for testable coroutine code")
        println("✅ Test data builders and fixture management")
        println("✅ Async test data generation and streaming")
        println("✅ Custom test runners and assertion utilities")
        println("✅ Mock and stub patterns for coroutine services")
        println("✅ CI/CD integration and automated testing")
        println("✅ Parallel test execution and performance optimization")
        println("✅ Test reporting and metrics collection")
        println("✅ Environment management and resource cleanup")
        println()
        
        println("Testing Best Practices Summary:")
        println("✅ Follow the test pyramid: Unit -> Integration -> E2E")
        println("✅ Use dependency injection for testable architectures")
        println("✅ Create reusable test data builders and fixtures")
        println("✅ Implement comprehensive assertion utilities")
        println("✅ Automate test execution in CI/CD pipelines")
        println("✅ Monitor test performance and identify bottlenecks")
        println("✅ Generate detailed reports for stakeholders")
        println("✅ Maintain clean test environments and proper cleanup")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}