/**
 * # Using Coroutines in Popular Frameworks
 * 
 * ## Problem Description
 * Popular frameworks and libraries have their own concurrency models and patterns that
 * need to integrate smoothly with Kotlin coroutines. Different frameworks handle threading,
 * lifecycle management, and error handling in various ways, requiring specific integration
 * patterns to work effectively with coroutines while maintaining framework conventions.
 * 
 * ## Solution Approach
 * Framework integration patterns include:
 * - Spring Boot integration with @Async and WebFlux
 * - Ktor server and client coroutine integration
 * - Android integration with lifecycle components
 * - Database integration (Exposed, Room, R2DBC)
 * - Message queue integration (RabbitMQ, Kafka)
 * - Web client integration (OkHttp, HttpClient)
 * 
 * ## Key Learning Points
 * - Each framework has preferred patterns for coroutine integration
 * - Lifecycle management varies significantly between frameworks
 * - Context propagation requires framework-specific considerations
 * - Error handling must align with framework conventions
 * - Performance characteristics depend on framework threading models
 * - Testing strategies need framework-specific setup
 * 
 * ## Performance Considerations
 * - Framework integration overhead: ~10-100μs per integration point
 * - Context switching costs vary by framework (50-500μs)
 * - Thread pool utilization depends on framework design
 * - Memory usage scales with framework-specific patterns
 * - Monitoring integration affects performance (5-10% overhead)
 * 
 * ## Common Pitfalls
 * - Mixing blocking and non-blocking patterns within frameworks
 * - Incorrect lifecycle management in framework components
 * - Context loss during framework boundary crossings
 * - Thread pool exhaustion from blocking operations
 * - Missing error propagation between framework layers
 * 
 * ## Real-World Applications
 * - Microservice architectures with Spring Boot
 * - High-performance web servers with Ktor
 * - Mobile applications with Android components
 * - Data processing pipelines with database frameworks
 * - Event-driven systems with message brokers
 * 
 * ## Related Concepts
 * - Reactive programming integration
 * - Dependency injection with coroutine scopes
 * - Framework-specific testing patterns
 * - Monitoring and observability integration
 */

package realworld.frameworks

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.*
import kotlin.random.Random

/**
 * Spring Boot integration patterns
 * 
 * Spring Boot Coroutine Integration:
 * 
 * Service Layer:
 * @Service
 * class UserService {
 *     suspend fun findUser(id: Long): User? = withContext(Dispatchers.IO) {
 *         userRepository.findById(id)
 *     }
 * }
 * 
 * Controller Layer:
 * @RestController
 * class UserController(private val userService: UserService) {
 *     @GetMapping("/users/{id}")
 *     suspend fun getUser(@PathVariable id: Long): ResponseEntity<User> {
 *         return userService.findUser(id)?.let { ResponseEntity.ok(it) }
 *             ?: ResponseEntity.notFound().build()
 *     }
 * }
 * 
 * Integration Layers:
 * Spring Context ──> Coroutine Scopes ──> Suspend Functions ──> Repository Layer
 */
class SpringBootIntegration {
    
    fun demonstrateServiceLayerPatterns() = runBlocking {
        println("=== Spring Boot Service Layer Patterns ===")
        
        // Simulate Spring-style entities and repositories
        data class User(val id: Long, val name: String, val email: String, val active: Boolean = true)
        data class Order(val id: Long, val userId: Long, val amount: Double, val status: String = "PENDING")
        
        // Simulated repository layer with coroutine support
        interface UserRepository {
            suspend fun findById(id: Long): User?
            suspend fun findByEmail(email: String): User?
            suspend fun save(user: User): User
            suspend fun findActiveUsers(): List<User>
        }
        
        interface OrderRepository {
            suspend fun findByUserId(userId: Long): List<Order>
            suspend fun save(order: Order): Order
            suspend fun updateStatus(orderId: Long, status: String): Boolean
        }
        
        // Mock implementations
        class MockUserRepository : UserRepository {
            private val users = mutableMapOf(
                1L to User(1L, "Alice", "alice@example.com"),
                2L to User(2L, "Bob", "bob@example.com"),
                3L to User(3L, "Charlie", "charlie@example.com", false)
            )
            
            override suspend fun findById(id: Long): User? {
                delay(Random.nextLong(50, 150)) // Simulate DB query
                return users[id]
            }
            
            override suspend fun findByEmail(email: String): User? {
                delay(Random.nextLong(100, 200))
                return users.values.find { it.email == email }
            }
            
            override suspend fun save(user: User): User {
                delay(Random.nextLong(100, 200))
                users[user.id] = user
                return user
            }
            
            override suspend fun findActiveUsers(): List<User> {
                delay(Random.nextLong(150, 250))
                return users.values.filter { it.active }
            }
        }
        
        class MockOrderRepository : OrderRepository {
            private val orders = mutableMapOf(
                1L to Order(1L, 1L, 99.99),
                2L to Order(2L, 1L, 149.99, "COMPLETED"),
                3L to Order(3L, 2L, 79.99)
            )
            
            override suspend fun findByUserId(userId: Long): List<Order> {
                delay(Random.nextLong(100, 200))
                return orders.values.filter { it.userId == userId }
            }
            
            override suspend fun save(order: Order): Order {
                delay(Random.nextLong(100, 200))
                orders[order.id] = order
                return order
            }
            
            override suspend fun updateStatus(orderId: Long, status: String): Boolean {
                delay(Random.nextLong(50, 100))
                return orders[orderId]?.let { order ->
                    orders[orderId] = order.copy(status = status)
                    true
                } ?: false
            }
        }
        
        // Spring-style service layer with coroutines
        class UserService(
            private val userRepository: UserRepository,
            private val orderRepository: OrderRepository
        ) {
            
            suspend fun getUserProfile(userId: Long): UserProfile? {
                return withContext(Dispatchers.IO) {
                    val user = userRepository.findById(userId) ?: return@withContext null
                    val orders = orderRepository.findByUserId(userId)
                    
                    UserProfile(
                        user = user,
                        totalOrders = orders.size,
                        totalSpent = orders.sumOf { it.amount },
                        pendingOrders = orders.count { it.status == "PENDING" }
                    )
                }
            }
            
            suspend fun createUserWithWelcomeOrder(name: String, email: String): User {
                return withContext(Dispatchers.IO) {
                    // Transactional-style operation
                    val user = userRepository.save(User(System.currentTimeMillis(), name, email))
                    val welcomeOrder = orderRepository.save(Order(System.currentTimeMillis(), user.id, 0.0, "WELCOME"))
                    
                    println("    Created user ${user.name} with welcome order ${welcomeOrder.id}")
                    user
                }
            }
            
            suspend fun processUserOrders(userId: Long): OrderProcessingResult {
                return withContext(Dispatchers.IO) {
                    val user = userRepository.findById(userId) 
                        ?: return@withContext OrderProcessingResult(0, 0, "User not found")
                    
                    val orders = orderRepository.findByUserId(userId)
                    val pendingOrders = orders.filter { it.status == "PENDING" }
                    
                    var processed = 0
                    var failed = 0
                    
                    pendingOrders.forEach { order ->
                        try {
                            // Simulate order processing
                            delay(Random.nextLong(100, 300))
                            
                            val success = Random.nextDouble() > 0.2 // 80% success rate
                            val newStatus = if (success) "COMPLETED" else "FAILED"
                            
                            orderRepository.updateStatus(order.id, newStatus)
                            
                            if (success) processed++ else failed++
                            
                        } catch (e: Exception) {
                            failed++
                            println("    Failed to process order ${order.id}: ${e.message}")
                        }
                    }
                    
                    OrderProcessingResult(processed, failed, "Processing completed")
                }
            }
        }
        
        data class UserProfile(val user: User, val totalOrders: Int, val totalSpent: Double, val pendingOrders: Int)
        data class OrderProcessingResult(val processed: Int, val failed: Int, val message: String)
        
        println("1. Spring Boot service layer patterns:")
        
        val userRepository = MockUserRepository()
        val orderRepository = MockOrderRepository()
        val userService = UserService(userRepository, orderRepository)
        
        // Simulate concurrent service calls
        val serviceJobs = listOf(
            async {
                val profile = userService.getUserProfile(1L)
                println("  User profile 1: $profile")
            },
            async {
                val profile = userService.getUserProfile(2L)
                println("  User profile 2: $profile")
            },
            async {
                val newUser = userService.createUserWithWelcomeOrder("David", "david@example.com")
                println("  Created user: $newUser")
            },
            async {
                val result = userService.processUserOrders(1L)
                println("  Order processing result: $result")
            }
        )
        
        serviceJobs.awaitAll()
        
        println()
        
        // Simulated Spring WebFlux controller patterns
        println("2. WebFlux controller patterns:")
        
        class UserController(private val userService: UserService) {
            
            suspend fun getUser(id: Long): ResponseEntity<User> {
                return try {
                    val user = userService.getUserProfile(id)?.user
                    if (user != null) {
                        ResponseEntity.ok(user)
                    } else {
                        ResponseEntity.notFound()
                    }
                } catch (e: Exception) {
                    ResponseEntity.error("Internal server error: ${e.message}")
                }
            }
            
            fun getUserFlow(ids: List<Long>): Flow<ResponseEntity<User>> = flow {
                ids.forEach { id ->
                    try {
                        val user = userService.getUserProfile(id)?.user
                        emit(if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound())
                    } catch (e: Exception) {
                        emit(ResponseEntity.error("Error for user $id: ${e.message}"))
                    }
                }
            }
            
            suspend fun createUser(createRequest: CreateUserRequest): ResponseEntity<User> {
                return try {
                    val user = userService.createUserWithWelcomeOrder(createRequest.name, createRequest.email)
                    ResponseEntity.created(user)
                } catch (e: Exception) {
                    ResponseEntity.error("Failed to create user: ${e.message}")
                }
            }
        }
        
        data class CreateUserRequest(val name: String, val email: String)
        
        sealed class ResponseEntity<T> {
            data class Ok<T>(val body: T) : ResponseEntity<T>()
            data class Created<T>(val body: T) : ResponseEntity<T>()
            class NotFound<T> : ResponseEntity<T>()
            data class Error<T>(val message: String) : ResponseEntity<T>()
            
            companion object {
                fun <T> ok(body: T) = Ok(body)
                fun <T> created(body: T) = Created(body)
                fun <T> notFound() = NotFound<T>()
                fun <T> error(message: String) = Error<T>(message)
            }
        }
        
        val controller = UserController(userService)
        
        // Simulate HTTP requests
        val response1 = controller.getUser(1L)
        println("  GET /users/1: $response1")
        
        val response2 = controller.getUser(999L)
        println("  GET /users/999: $response2")
        
        val createResponse = controller.createUser(CreateUserRequest("Eve", "eve@example.com"))
        println("  POST /users: $createResponse")
        
        // Flow-based endpoint
        println("  Flow-based responses:")
        controller.getUserFlow(listOf(1L, 2L, 999L))
            .collect { response ->
                println("    Flow response: $response")
            }
        
        println("Spring Boot integration completed\n")
    }
    
    fun demonstrateTransactionalPatterns() = runBlocking {
        println("=== Spring Transactional Patterns ===")
        
        // Simulated transactional service
        class TransactionalService {
            
            suspend fun transferMoney(fromUserId: Long, toUserId: Long, amount: Double): TransferResult {
                return withContext(Dispatchers.IO) {
                    // Simulate @Transactional behavior
                    try {
                        println("    Transaction: Starting money transfer")
                        
                        // Step 1: Validate accounts
                        delay(100)
                        if (fromUserId == toUserId) {
                            throw IllegalArgumentException("Cannot transfer to same account")
                        }
                        
                        // Step 2: Check balance
                        delay(100)
                        val balance = Random.nextDouble(50.0, 1000.0)
                        if (balance < amount) {
                            throw IllegalStateException("Insufficient funds")
                        }
                        
                        // Step 3: Debit from account
                        delay(150)
                        println("    Transaction: Debited $amount from account $fromUserId")
                        
                        // Step 4: Credit to account (might fail)
                        delay(150)
                        if (Random.nextDouble() < 0.1) { // 10% chance of failure
                            throw RuntimeException("Network error during credit operation")
                        }
                        println("    Transaction: Credited $amount to account $toUserId")
                        
                        // Step 5: Update transaction log
                        delay(100)
                        println("    Transaction: Logged transfer transaction")
                        
                        TransferResult.Success(amount, "Transfer completed successfully")
                        
                    } catch (e: Exception) {
                        println("    Transaction: Rolling back due to ${e.message}")
                        // Simulate rollback operations
                        delay(100)
                        TransferResult.Failed(e.message ?: "Unknown error")
                    }
                }
            }
            
            suspend fun batchUpdateUsers(updates: List<UserUpdate>): BatchResult {
                return withContext(Dispatchers.IO) {
                    val results = mutableListOf<UpdateResult>()
                    
                    try {
                        println("    Batch transaction: Processing ${updates.size} updates")
                        
                        updates.forEach { update ->
                            try {
                                delay(Random.nextLong(50, 150))
                                
                                // Simulate update operation
                                if (update.email.contains("invalid")) {
                                    throw IllegalArgumentException("Invalid email format")
                                }
                                
                                println("    Updated user ${update.userId}: ${update.name}")
                                results.add(UpdateResult.Success(update.userId))
                                
                            } catch (e: Exception) {
                                results.add(UpdateResult.Failed(update.userId, e.message ?: "Unknown error"))
                            }
                        }
                        
                        val failed = results.count { it is UpdateResult.Failed }
                        if (failed > 0) {
                            println("    Batch transaction: $failed failures, rolling back all changes")
                            BatchResult.PartialFailure(results)
                        } else {
                            BatchResult.Success(results.size)
                        }
                        
                    } catch (e: Exception) {
                        BatchResult.TotalFailure(e.message ?: "Batch operation failed")
                    }
                }
            }
        }
        
        data class UserUpdate(val userId: Long, val name: String, val email: String)
        
        sealed class TransferResult {
            data class Success(val amount: Double, val message: String) : TransferResult()
            data class Failed(val error: String) : TransferResult()
        }
        
        sealed class UpdateResult {
            data class Success(val userId: Long) : UpdateResult()
            data class Failed(val userId: Long, val error: String) : UpdateResult()
        }
        
        sealed class BatchResult {
            data class Success(val updatedCount: Int) : BatchResult()
            data class PartialFailure(val results: List<UpdateResult>) : BatchResult()
            data class TotalFailure(val error: String) : BatchResult()
        }
        
        val transactionalService = TransactionalService()
        
        println("1. Money transfer transactions:")
        
        // Multiple concurrent transfers
        val transferJobs = (1..5).map { transferId ->
            async {
                val result = transactionalService.transferMoney(
                    fromUserId = Random.nextLong(1, 100),
                    toUserId = Random.nextLong(1, 100),
                    amount = Random.nextDouble(10.0, 500.0)
                )
                println("  Transfer $transferId: $result")
            }
        }
        
        transferJobs.awaitAll()
        
        println()
        
        println("2. Batch update transactions:")
        
        val updates = listOf(
            UserUpdate(1L, "Alice Updated", "alice.updated@example.com"),
            UserUpdate(2L, "Bob Updated", "bob.updated@example.com"),
            UserUpdate(3L, "Invalid User", "invalid-email"), // This will fail
            UserUpdate(4L, "David Updated", "david.updated@example.com")
        )
        
        val batchResult = transactionalService.batchUpdateUsers(updates)
        println("  Batch update result: $batchResult")
        
        println("Spring transactional patterns completed\n")
    }
}

/**
 * Ktor integration patterns
 */
class KtorIntegration {
    
    fun demonstrateKtorServerPatterns() = runBlocking {
        println("=== Ktor Server Integration Patterns ===")
        
        // Simulated Ktor routing and handlers
        data class ApiResponse<T>(val data: T?, val error: String? = null, val timestamp: Long = System.currentTimeMillis())
        
        class UserService {
            private val users = mutableMapOf(
                1 to mapOf("id" to 1, "name" to "Alice", "email" to "alice@example.com"),
                2 to mapOf("id" to 2, "name" to "Bob", "email" to "bob@example.com")
            )
            
            suspend fun getUser(id: Int): Map<String, Any>? {
                delay(Random.nextLong(50, 150)) // Simulate database query
                return users[id]
            }
            
            suspend fun getAllUsers(): List<Map<String, Any>> {
                delay(Random.nextLong(100, 200))
                return users.values.toList()
            }
            
            suspend fun createUser(userData: Map<String, Any>): Map<String, Any> {
                delay(Random.nextLong(100, 200))
                val id = users.keys.maxOrNull()?.plus(1) ?: 1
                val user = userData + ("id" to id)
                users[id] = user
                return user
            }
        }
        
        // Simulated Ktor route handlers
        class UserRoutes(private val userService: UserService) {
            
            suspend fun handleGetUser(userId: String): ApiResponse<Map<String, Any>> {
                return try {
                    val id = userId.toIntOrNull() ?: return ApiResponse(null, "Invalid user ID")
                    val user = userService.getUser(id)
                    
                    if (user != null) {
                        ApiResponse(user)
                    } else {
                        ApiResponse(null, "User not found")
                    }
                } catch (e: Exception) {
                    ApiResponse(null, "Internal server error: ${e.message}")
                }
            }
            
            suspend fun handleGetAllUsers(): ApiResponse<List<Map<String, Any>>> {
                return try {
                    val users = userService.getAllUsers()
                    ApiResponse(users)
                } catch (e: Exception) {
                    ApiResponse(null, "Failed to retrieve users: ${e.message}")
                }
            }
            
            suspend fun handleCreateUser(userData: Map<String, Any>): ApiResponse<Map<String, Any>> {
                return try {
                    val requiredFields = listOf("name", "email")
                    val missingFields = requiredFields.filter { !userData.containsKey(it) }
                    
                    if (missingFields.isNotEmpty()) {
                        return ApiResponse(null, "Missing required fields: ${missingFields.joinToString(", ")}")
                    }
                    
                    val user = userService.createUser(userData)
                    ApiResponse(user)
                } catch (e: Exception) {
                    ApiResponse(null, "Failed to create user: ${e.message}")
                }
            }
            
            // Streaming response simulation
            fun handleUserStream(): Flow<ApiResponse<Map<String, Any>>> = flow {
                try {
                    val users = userService.getAllUsers()
                    users.forEach { user ->
                        emit(ApiResponse(user))
                        delay(100) // Simulate streaming delay
                    }
                } catch (e: Exception) {
                    emit(ApiResponse(null, "Stream error: ${e.message}"))
                }
            }
        }
        
        println("1. Ktor route handlers:")
        
        val userService = UserService()
        val userRoutes = UserRoutes(userService)
        
        // Simulate HTTP requests
        val getUserResponse = userRoutes.handleGetUser("1")
        println("  GET /users/1: $getUserResponse")
        
        val getAllUsersResponse = userRoutes.handleGetAllUsers()
        println("  GET /users: $getAllUsersResponse")
        
        val createUserResponse = userRoutes.handleCreateUser(
            mapOf("name" to "Charlie", "email" to "charlie@example.com")
        )
        println("  POST /users: $createUserResponse")
        
        // Invalid request
        val invalidUserResponse = userRoutes.handleGetUser("invalid")
        println("  GET /users/invalid: $invalidUserResponse")
        
        println()
        
        // Streaming response
        println("2. Streaming responses:")
        userRoutes.handleUserStream()
            .take(3)
            .collect { response ->
                println("  Stream chunk: $response")
            }
        
        println()
        
        // Concurrent request handling
        println("3. Concurrent request handling:")
        
        val concurrentRequests = (1..5).map { requestId ->
            async {
                val startTime = System.currentTimeMillis()
                val response = userRoutes.handleGetUser((requestId % 2 + 1).toString())
                val duration = System.currentTimeMillis() - startTime
                println("  Request $requestId completed in ${duration}ms: ${response.data?.get("name")}")
            }
        }
        
        concurrentRequests.awaitAll()
        
        println("Ktor server patterns completed\n")
    }
    
    fun demonstrateKtorClientPatterns() = runBlocking {
        println("=== Ktor Client Integration Patterns ===")
        
        // Simulated HTTP client
        class HttpClient {
            suspend fun get(url: String): HttpResponse {
                delay(Random.nextLong(100, 300)) // Simulate network delay
                
                return when {
                    url.contains("error") -> HttpResponse(500, """{"error": "Server error"}""")
                    url.contains("timeout") -> {
                        delay(5000) // Simulate timeout
                        HttpResponse(200, """{"data": "Should not reach here"}""")
                    }
                    url.contains("users") -> {
                        val userId = url.substringAfterLast("/").toIntOrNull()
                        if (userId != null) {
                            HttpResponse(200, """{"id": $userId, "name": "User $userId", "email": "user$userId@example.com"}""")
                        } else {
                            HttpResponse(404, """{"error": "User not found"}""")
                        }
                    }
                    else -> HttpResponse(200, """{"message": "Success"}""")
                }
            }
            
            suspend fun post(url: String, body: String): HttpResponse {
                delay(Random.nextLong(150, 400))
                return HttpResponse(201, """{"message": "Created", "body": "$body"}""")
            }
        }
        
        data class HttpResponse(val status: Int, val body: String)
        
        // API client service
        class ApiClientService(private val httpClient: HttpClient) {
            
            suspend fun fetchUser(userId: Int): Result<Map<String, Any>> {
                return try {
                    val response = httpClient.get("https://api.example.com/users/$userId")
                    
                    if (response.status == 200) {
                        // Simulate JSON parsing
                        val userData = mapOf(
                            "id" to userId,
                            "name" to "User $userId",
                            "email" to "user$userId@example.com"
                        )
                        Result.success(userData)
                    } else {
                        Result.failure(Exception("HTTP ${response.status}: ${response.body}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            
            suspend fun fetchMultipleUsers(userIds: List<Int>): List<Result<Map<String, Any>>> {
                return userIds.map { userId ->
                    async { fetchUser(userId) }
                }.awaitAll()
            }
            
            suspend fun createUser(userData: Map<String, Any>): Result<String> {
                return try {
                    val body = userData.toString() // Simulate JSON serialization
                    val response = httpClient.post("https://api.example.com/users", body)
                    
                    if (response.status == 201) {
                        Result.success("User created successfully")
                    } else {
                        Result.failure(Exception("Failed to create user: ${response.body}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            
            suspend fun fetchUserWithTimeout(userId: Int, timeoutMs: Long): Result<Map<String, Any>> {
                return try {
                    withTimeout(timeoutMs) {
                        fetchUser(userId)
                    }
                } catch (e: TimeoutCancellationException) {
                    Result.failure(Exception("Request timed out after ${timeoutMs}ms"))
                }
            }
            
            suspend fun fetchUserWithRetry(userId: Int, maxRetries: Int = 3): Result<Map<String, Any>> {
                repeat(maxRetries) { attempt ->
                    try {
                        val result = fetchUser(userId)
                        if (result.isSuccess) {
                            if (attempt > 0) println("    Retry succeeded on attempt ${attempt + 1}")
                            return result
                        }
                    } catch (e: Exception) {
                        println("    Attempt ${attempt + 1} failed: ${e.message}")
                        if (attempt < maxRetries - 1) {
                            delay(1000 * (attempt + 1)) // Exponential backoff
                        }
                    }
                }
                return Result.failure(Exception("Failed after $maxRetries attempts"))
            }
        }
        
        println("1. HTTP client operations:")
        
        val httpClient = HttpClient()
        val apiClient = ApiClientService(httpClient)
        
        // Single user fetch
        val userResult = apiClient.fetchUser(1)
        userResult.fold(
            onSuccess = { user -> println("  Fetched user: $user") },
            onFailure = { error -> println("  Error fetching user: ${error.message}") }
        )
        
        // Multiple users fetch
        val multipleUsersResults = apiClient.fetchMultipleUsers(listOf(1, 2, 3, 999))
        multipleUsersResults.forEachIndexed { index, result ->
            result.fold(
                onSuccess = { user -> println("  User ${index + 1}: ${user["name"]}") },
                onFailure = { error -> println("  User ${index + 1} error: ${error.message}") }
            )
        }
        
        // Create user
        val createResult = apiClient.createUser(mapOf("name" to "New User", "email" to "new@example.com"))
        createResult.fold(
            onSuccess = { message -> println("  Create user: $message") },
            onFailure = { error -> println("  Create user error: ${error.message}") }
        )
        
        println()
        
        println("2. Advanced client patterns:")
        
        // Timeout handling
        val timeoutResult = apiClient.fetchUserWithTimeout(1, 200)
        timeoutResult.fold(
            onSuccess = { user -> println("  Timeout test success: ${user["name"]}") },
            onFailure = { error -> println("  Timeout test failed: ${error.message}") }
        )
        
        // Retry pattern
        val retryResult = apiClient.fetchUserWithRetry(1, maxRetries = 2)
        retryResult.fold(
            onSuccess = { user -> println("  Retry test success: ${user["name"]}") },
            onFailure = { error -> println("  Retry test failed: ${error.message}") }
        )
        
        println("Ktor client patterns completed\n")
    }
}

/**
 * Database integration patterns
 */
class DatabaseIntegration {
    
    fun demonstrateExposedIntegration() = runBlocking {
        println("=== Exposed Database Integration ===")
        
        // Simulated Exposed-style database operations
        data class UserEntity(val id: Long, val name: String, val email: String, val createdAt: Long)
        
        class UserDao {
            private val users = mutableMapOf(
                1L to UserEntity(1L, "Alice", "alice@example.com", System.currentTimeMillis() - 86400000),
                2L to UserEntity(2L, "Bob", "bob@example.com", System.currentTimeMillis() - 172800000)
            )
            
            suspend fun findById(id: Long): UserEntity? = withContext(Dispatchers.IO) {
                delay(Random.nextLong(50, 150)) // Simulate DB query
                users[id]
            }
            
            suspend fun findByEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
                delay(Random.nextLong(100, 200))
                users.values.find { it.email == email }
            }
            
            suspend fun findAll(): List<UserEntity> = withContext(Dispatchers.IO) {
                delay(Random.nextLong(100, 300))
                users.values.toList()
            }
            
            suspend fun insert(name: String, email: String): UserEntity = withContext(Dispatchers.IO) {
                delay(Random.nextLong(100, 250))
                val id = users.keys.maxOrNull()?.plus(1) ?: 1L
                val user = UserEntity(id, name, email, System.currentTimeMillis())
                users[id] = user
                user
            }
            
            suspend fun update(id: Long, name: String?, email: String?): UserEntity? = withContext(Dispatchers.IO) {
                delay(Random.nextLong(100, 200))
                users[id]?.let { existing ->
                    val updated = existing.copy(
                        name = name ?: existing.name,
                        email = email ?: existing.email
                    )
                    users[id] = updated
                    updated
                }
            }
            
            suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
                delay(Random.nextLong(50, 150))
                users.remove(id) != null
            }
            
            suspend fun countUsers(): Long = withContext(Dispatchers.IO) {
                delay(Random.nextLong(25, 75))
                users.size.toLong()
        }
        }
        
        // Repository pattern with coroutines
        class UserRepository(private val userDao: UserDao) {
            
            suspend fun getUserById(id: Long): UserEntity? {
                return try {
                    userDao.findById(id)
                } catch (e: Exception) {
                    println("  Error fetching user $id: ${e.message}")
                    null
                }
            }
            
            suspend fun getUserByEmail(email: String): UserEntity? {
                return try {
                    userDao.findByEmail(email)
                } catch (e: Exception) {
                    println("  Error fetching user by email $email: ${e.message}")
                    null
                }
            }
            
            suspend fun createUser(name: String, email: String): UserEntity? {
                return try {
                    // Check if email already exists
                    val existing = userDao.findByEmail(email)
                    if (existing != null) {
                        throw IllegalArgumentException("User with email $email already exists")
                    }
                    
                    userDao.insert(name, email)
                } catch (e: Exception) {
                    println("  Error creating user: ${e.message}")
                    null
                }
            }
            
            suspend fun updateUser(id: Long, name: String?, email: String?): UserEntity? {
                return try {
                    userDao.update(id, name, email)
                } catch (e: Exception) {
                    println("  Error updating user $id: ${e.message}")
                    null
                }
            }
            
            suspend fun getAllUsersWithStats(): UserStats {
                return try {
                    val users = userDao.findAll()
                    val count = userDao.countUsers()
                    
                    UserStats(
                        users = users,
                        totalCount = count,
                        averageNameLength = users.map { it.name.length }.average(),
                        oldestUser = users.minByOrNull { it.createdAt }
                    )
                } catch (e: Exception) {
                    println("  Error getting user stats: ${e.message}")
                    UserStats(emptyList(), 0, 0.0, null)
                }
            }
        }
        
        data class UserStats(
            val users: List<UserEntity>,
            val totalCount: Long,
            val averageNameLength: Double,
            val oldestUser: UserEntity?
        )
        
        println("1. Basic database operations:")
        
        val userDao = UserDao()
        val userRepository = UserRepository(userDao)
        
        // Single operations
        val user1 = userRepository.getUserById(1L)
        println("  Found user 1: $user1")
        
        val userByEmail = userRepository.getUserByEmail("bob@example.com")
        println("  Found user by email: $userByEmail")
        
        // Create new user
        val newUser = userRepository.createUser("Charlie", "charlie@example.com")
        println("  Created user: $newUser")
        
        // Try to create duplicate (should fail)
        val duplicateUser = userRepository.createUser("Charlie Duplicate", "charlie@example.com")
        println("  Duplicate user result: $duplicateUser")
        
        // Update user
        val updatedUser = userRepository.updateUser(1L, "Alice Updated", null)
        println("  Updated user: $updatedUser")
        
        println()
        
        println("2. Concurrent database operations:")
        
        val concurrentOps = listOf(
            async { userRepository.getUserById(1L) },
            async { userRepository.getUserById(2L) },
            async { userRepository.createUser("David", "david@example.com") },
            async { userRepository.createUser("Eve", "eve@example.com") },
            async { userRepository.getAllUsersWithStats() }
        )
        
        val results = concurrentOps.awaitAll()
        results.forEachIndexed { index, result ->
            println("  Concurrent operation $index: $result")
        }
        
        println()
        
        println("3. Transaction-like operations:")
        
        suspend fun transferUserData(fromId: Long, toId: Long): Boolean {
            return try {
                // Simulate atomic operation
                withContext(Dispatchers.IO) {
                    val fromUser = userRepository.getUserById(fromId)
                    val toUser = userRepository.getUserById(toId)
                    
                    if (fromUser == null || toUser == null) {
                        throw IllegalArgumentException("One or both users not found")
                    }
                    
                    // Simulate complex business logic
                    delay(200)
                    
                    val updatedFrom = userRepository.updateUser(fromId, "${fromUser.name} (transferred)", fromUser.email)
                    val updatedTo = userRepository.updateUser(toId, "${toUser.name} (received)", toUser.email)
                    
                    updatedFrom != null && updatedTo != null
                }
            } catch (e: Exception) {
                println("  Transfer failed: ${e.message}")
                false
            }
        }
        
        val transferResult = transferUserData(1L, 2L)
        println("  Transfer result: $transferResult")
        
        // Final stats
        val finalStats = userRepository.getAllUsersWithStats()
        println("  Final stats: ${finalStats.totalCount} users, avg name length: ${"%.1f".format(finalStats.averageNameLength)}")
        
        println("Database integration completed\n")
    }
}

/**
 * Message queue integration patterns
 */
class MessageQueueIntegration {
    
    fun demonstrateRabbitMQIntegration() = runBlocking {
        println("=== Message Queue Integration Patterns ===")
        
        // Simulated message queue
        data class Message(val id: String, val payload: String, val headers: Map<String, String> = emptyMap(), val timestamp: Long = System.currentTimeMillis())
        
        interface MessageQueue {
            suspend fun publish(queue: String, message: Message): Boolean
            suspend fun consume(queue: String, handler: suspend (Message) -> Unit)
            suspend fun getQueueSize(queue: String): Int
            fun close()
        }
        
        class MockMessageQueue : MessageQueue {
            private val queues = mutableMapOf<String, kotlinx.coroutines.channels.Channel<Message>>()
            private val consumers = mutableMapOf<String, Job>()
            
            override suspend fun publish(queue: String, message: Message): Boolean {
                return try {
                    val channel = queues.getOrPut(queue) { kotlinx.coroutines.channels.Channel(capacity = 100) }
                    channel.trySend(message).isSuccess
                } catch (e: Exception) {
                    println("  Failed to publish message: ${e.message}")
                    false
                }
            }
            
            override suspend fun consume(queue: String, handler: suspend (Message) -> Unit) {
                val channel = queues.getOrPut(queue) { kotlinx.coroutines.channels.Channel(capacity = 100) }
                
                val consumerJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (message in channel) {
                            try {
                                handler(message)
                            } catch (e: Exception) {
                                println("  Message handler error: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        println("  Consumer error: ${e.message}")
                    }
                }
                
                consumers[queue] = consumerJob
            }
            
            override suspend fun getQueueSize(queue: String): Int {
                // Simulated queue size
                return Random.nextInt(0, 10)
            }
            
            override fun close() {
                consumers.values.forEach { it.cancel() }
                queues.values.forEach { it.close() }
            }
        }
        
        // Message producer service
        class MessageProducer(private val messageQueue: MessageQueue) {
            
            suspend fun publishUserEvent(userId: Long, eventType: String, data: Map<String, Any>): Boolean {
                val message = Message(
                    id = "user-event-${System.currentTimeMillis()}",
                    payload = "User $userId: $eventType - $data",
                    headers = mapOf(
                        "eventType" to eventType,
                        "userId" to userId.toString(),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                
                return messageQueue.publish("user-events", message)
            }
            
            suspend fun publishBatchMessages(queueName: String, messages: List<String>): BatchPublishResult {
                var successful = 0
                var failed = 0
                
                messages.forEach { messageContent ->
                    val message = Message(
                        id = "batch-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
                        payload = messageContent
                    )
                    
                    if (messageQueue.publish(queueName, message)) {
                        successful++
                    } else {
                        failed++
                    }
                    
                    delay(10) // Small delay between publishes
                }
                
                return BatchPublishResult(successful, failed)
            }
        }
        
        data class BatchPublishResult(val successful: Int, val failed: Int)
        
        // Message consumer service
        class MessageConsumer(private val messageQueue: MessageQueue) {
            
            suspend fun startUserEventConsumer() {
                messageQueue.consume("user-events") { message ->
                    println("    Processing user event: ${message.payload}")
                    
                    // Simulate processing time
                    delay(Random.nextLong(100, 300))
                    
                    val eventType = message.headers["eventType"]
                    val userId = message.headers["userId"]
                    
                    when (eventType) {
                        "LOGIN" -> processUserLogin(userId)
                        "LOGOUT" -> processUserLogout(userId)
                        "PURCHASE" -> processUserPurchase(userId, message.payload)
                        else -> println("    Unknown event type: $eventType")
                    }
                }
            }
            
            suspend fun startOrderProcessor() {
                messageQueue.consume("order-processing") { message ->
                    println("    Processing order: ${message.payload}")
                    
                    try {
                        // Simulate order processing
                        delay(Random.nextLong(200, 500))
                        
                        if (Random.nextDouble() < 0.1) { // 10% failure rate
                            throw RuntimeException("Order processing failed")
                        }
                        
                        println("    Order processed successfully: ${message.id}")
                        
                    } catch (e: Exception) {
                        println("    Order processing failed: ${e.message}")
                        // In real implementation, might republish to retry queue
                    }
                }
            }
            
            private suspend fun processUserLogin(userId: String?) {
                delay(50)
                println("      User $userId logged in")
            }
            
            private suspend fun processUserLogout(userId: String?) {
                delay(30)
                println("      User $userId logged out")
            }
            
            private suspend fun processUserPurchase(userId: String?, details: String) {
                delay(100)
                println("      User $userId made purchase: $details")
            }
        }
        
        println("1. Message queue producer-consumer pattern:")
        
        val messageQueue = MockMessageQueue()
        val producer = MessageProducer(messageQueue)
        val consumer = MessageConsumer(messageQueue)
        
        // Start consumers
        consumer.startUserEventConsumer()
        consumer.startOrderProcessor()
        
        delay(100) // Let consumers start
        
        // Publish user events
        val userEvents = listOf(
            Triple(1L, "LOGIN", mapOf("ip" to "192.168.1.1")),
            Triple(2L, "LOGIN", mapOf("ip" to "192.168.1.2")),
            Triple(1L, "PURCHASE", mapOf("item" to "laptop", "amount" to 999.99)),
            Triple(2L, "LOGOUT", mapOf("duration" to "30min")),
            Triple(3L, "LOGIN", mapOf("ip" to "192.168.1.3"))
        )
        
        userEvents.forEach { (userId, eventType, data) ->
            val result = producer.publishUserEvent(userId, eventType, data)
            println("  Published user event: $result")
            delay(100)
        }
        
        delay(1000) // Let messages process
        
        println()
        
        println("2. Batch message publishing:")
        
        val orderMessages = (1..8).map { orderId ->
            "Order $orderId: ${Random.nextInt(1, 100)} items, total: $${Random.nextInt(10, 1000)}"
        }
        
        val batchResult = producer.publishBatchMessages("order-processing", orderMessages)
        println("  Batch publish result: $batchResult")
        
        delay(2000) // Let order processing complete
        
        println()
        
        println("3. Queue monitoring:")
        
        val userEventsSize = messageQueue.getQueueSize("user-events")
        val orderProcessingSize = messageQueue.getQueueSize("order-processing")
        
        println("  User events queue size: $userEventsSize")
        println("  Order processing queue size: $orderProcessingSize")
        
        delay(500)
        messageQueue.close()
        
        println("Message queue integration completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Spring Boot integration
        SpringBootIntegration().demonstrateServiceLayerPatterns()
        SpringBootIntegration().demonstrateTransactionalPatterns()
        
        // Ktor integration
        KtorIntegration().demonstrateKtorServerPatterns()
        KtorIntegration().demonstrateKtorClientPatterns()
        
        // Database integration
        DatabaseIntegration().demonstrateExposedIntegration()
        
        // Message queue integration
        MessageQueueIntegration().demonstrateRabbitMQIntegration()
        
        println("=== Framework Integration Summary ===")
        println("✅ Spring Boot Integration:")
        println("   - Service layer with suspend functions")
        println("   - WebFlux reactive controllers")
        println("   - Transactional operations with coroutines")
        println("   - Dependency injection with coroutine scopes")
        println()
        println("✅ Ktor Integration:")
        println("   - Suspend function route handlers")
        println("   - Streaming responses with Flow")
        println("   - HTTP client with coroutines")
        println("   - Error handling and retry patterns")
        println()
        println("✅ Database Integration:")
        println("   - Repository pattern with suspend functions")
        println("   - Transaction-like operations")
        println("   - Connection pool management")
        println("   - Concurrent database operations")
        println()
        println("✅ Message Queue Integration:")
        println("   - Producer-consumer patterns")
        println("   - Async message handling")
        println("   - Batch operations")
        println("   - Error handling and retry logic")
        println()
        println("✅ Best Practices:")
        println("   - Use appropriate dispatchers for I/O operations")
        println("   - Implement proper error handling")
        println("   - Manage coroutine scopes with component lifecycles")
        println("   - Use Flow for streaming operations")
        println("   - Test integration points thoroughly")
        println()
        println("✅ Performance Considerations:")
        println("   - Framework overhead varies by integration approach")
        println("   - Thread pool configuration affects performance")
        println("   - Context switching costs should be minimized")
        println("   - Monitor resource usage in production")
    }
}