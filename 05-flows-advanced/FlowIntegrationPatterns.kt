/**
 * # Real-World Flow Integration Patterns
 * 
 * ## Problem Description
 * Real-world applications require integrating Flows with various systems, frameworks,
 * and external APIs. This includes database integration, network communication,
 * UI frameworks, external libraries, and legacy systems. Each integration has
 * unique challenges regarding lifecycle management, error handling, and performance.
 * 
 * ## Solution Approach
 * Integration patterns include:
 * - Database integration with reactive queries and transactions
 * - Network API integration with retry and caching strategies
 * - UI framework integration with state management
 * - Event-driven architecture with message queues
 * - Legacy system adapters and protocol bridges
 * 
 * ## Key Learning Points
 * - Flow integration requires careful lifecycle management
 * - Error boundaries prevent cascading failures
 * - Resource cleanup is crucial in integration scenarios
 * - Context switching between different execution environments
 * - Adaptation layers abstract external system complexities
 * 
 * ## Performance Considerations
 * - Network latency affects flow throughput (typical: 10-500ms per request)
 * - Database queries can be optimized with batching (5-50x improvement)
 * - UI updates should be throttled to prevent frame drops
 * - Memory usage scales with concurrent connections
 * - Connection pooling reduces overhead by 30-80%
 * 
 * ## Common Pitfalls
 * - Not handling connection failures and retries
 * - Missing proper resource cleanup in integration layers
 * - Blocking UI thread with heavy flow operations
 * - Memory leaks from unclosed database/network resources
 * - Not handling back-pressure from external systems
 * 
 * ## Real-World Applications
 * - Microservices communication patterns
 * - Real-time dashboard data feeds
 * - Mobile app offline/online synchronization
 * - IoT device telemetry processing
 * - Financial trading system integration
 * 
 * ## Related Concepts
 * - Hexagonal architecture patterns
 * - Event sourcing and CQRS
 * - Reactive messaging patterns
 * - Circuit breaker patterns
 */

package flow.advanced.integration

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Database integration patterns
 * 
 * Database Integration Architecture:
 * 
 * Application Layer
 * ├── Repository Pattern ─── Flow-based data access
 * ├── Transaction Management ─── Atomic operations with flows
 * ├── Connection Pooling ─── Resource management
 * └── Query Optimization ─── Batching and caching
 * 
 * Flow Patterns:
 * Query -> Transform -> Cache -> Emit -> UI Update
 */
class DatabaseIntegration {
    
    // Simulated database entities
    data class User(val id: Int, val name: String, val email: String, val createdAt: Long = System.currentTimeMillis())
    data class Order(val id: Int, val userId: Int, val amount: Double, val status: String, val createdAt: Long = System.currentTimeMillis())
    
    // Simulated database interface
    interface Database {
        suspend fun findUserById(id: Int): User?
        suspend fun findUsersByName(name: String): List<User>
        suspend fun saveUser(user: User): User
        suspend fun findOrdersByUserId(userId: Int): List<Order>
        suspend fun saveOrder(order: Order): Order
        fun watchUserChanges(): Flow<User>
        fun watchOrderChanges(): Flow<Order>
    }
    
    // Mock database implementation
    class MockDatabase : Database {
        private val users = mutableMapOf<Int, User>()
        private val orders = mutableMapOf<Int, Order>()
        private val userChanges = MutableSharedFlow<User>()
        private val orderChanges = MutableSharedFlow<Order>()
        
        init {
            // Seed data
            users[1] = User(1, "Alice", "alice@example.com")
            users[2] = User(2, "Bob", "bob@example.com")
            users[3] = User(3, "Charlie", "charlie@example.com")
            
            orders[1] = Order(1, 1, 100.0, "COMPLETED")
            orders[2] = Order(2, 1, 150.0, "PENDING")
            orders[3] = Order(3, 2, 200.0, "COMPLETED")
        }
        
        override suspend fun findUserById(id: Int): User? {
            delay(Random.nextLong(10, 50)) // Simulate database latency
            return users[id]
        }
        
        override suspend fun findUsersByName(name: String): List<User> {
            delay(Random.nextLong(20, 100))
            return users.values.filter { it.name.contains(name, ignoreCase = true) }
        }
        
        override suspend fun saveUser(user: User): User {
            delay(Random.nextLong(30, 80))
            users[user.id] = user
            userChanges.tryEmit(user)
            return user
        }
        
        override suspend fun findOrdersByUserId(userId: Int): List<Order> {
            delay(Random.nextLong(15, 60))
            return orders.values.filter { it.userId == userId }
        }
        
        override suspend fun saveOrder(order: Order): Order {
            delay(Random.nextLong(25, 70))
            orders[order.id] = order
            orderChanges.tryEmit(order)
            return order
        }
        
        override fun watchUserChanges(): Flow<User> = userChanges.asSharedFlow()
        override fun watchOrderChanges(): Flow<Order> = orderChanges.asSharedFlow()
    }
    
    fun demonstrateBasicDatabaseFlow() = runBlocking {
        println("=== Basic Database Flow Integration ===")
        
        val database = MockDatabase()
        
        // Repository pattern with Flow
        class UserRepository(private val db: Database) {
            
            fun getUserById(id: Int): Flow<User?> = flow {
                emit(db.findUserById(id))
            }
            
            fun searchUsers(query: String): Flow<List<User>> = flow {
                emit(db.findUsersByName(query))
            }
            
            fun getUserWithOrders(userId: Int): Flow<Pair<User?, List<Order>>> = flow {
                val user = db.findUserById(userId)
                val orders = if (user != null) db.findOrdersByUserId(userId) else emptyList()
                emit(user to orders)
            }
            
            fun watchUser(id: Int): Flow<User?> = merge(
                flow { emit(db.findUserById(id)) }, // Initial value
                db.watchUserChanges().filter { it.id == id } // Updates
            ).distinctUntilChanged()
        }
        
        val userRepository = UserRepository(database)
        
        println("1. Basic database queries:")
        userRepository.getUserById(1)
            .collect { user -> println("  User: $user") }
        
        println()
        
        println("2. Search functionality:")
        userRepository.searchUsers("A")
            .collect { users -> println("  Search results: ${users.map { it.name }}") }
        
        println()
        
        println("3. Complex query with joins:")
        userRepository.getUserWithOrders(1)
            .collect { (user, orders) -> 
                println("  User: ${user?.name} has ${orders.size} orders")
                orders.forEach { order ->
                    println("    Order ${order.id}: $${order.amount} (${order.status})")
                }
            }
        
        println()
        
        println("4. Reactive database watching:")
        val watchJob = launch {
            userRepository.watchUser(1)
                .collect { user ->
                    println("  User update: ${user?.name}")
                }
        }
        
        delay(100)
        
        // Trigger database change
        database.saveUser(User(1, "Alice Updated", "alice.updated@example.com"))
        
        delay(200)
        watchJob.cancel()
        
        println("Basic database integration completed\n")
    }
    
    fun demonstrateTransactionManagement() = runBlocking {
        println("=== Database Transaction Management ===")
        
        // Transaction manager interface
        interface TransactionManager {
            suspend fun <T> withTransaction(block: suspend () -> T): T
        }
        
        class MockTransactionManager : TransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T {
                println("    Starting transaction")
                return try {
                    val result = block()
                    println("    Committing transaction")
                    result
                } catch (e: Exception) {
                    println("    Rolling back transaction: ${e.message}")
                    throw e
                }
            }
        }
        
        // Transactional repository
        class TransactionalOrderRepository(
            private val db: Database,
            private val txManager: TransactionManager
        ) {
            
            fun processOrderBatch(orders: List<Order>): Flow<Order> = flow {
                txManager.withTransaction {
                    for (order in orders) {
                        // Simulate business logic that might fail
                        if (order.amount < 0) {
                            throw IllegalArgumentException("Invalid order amount")
                        }
                        
                        val savedOrder = db.saveOrder(order)
                        emit(savedOrder)
                    }
                }
            }
            
            fun transferOrdersBetweenUsers(
                fromUserId: Int,
                toUserId: Int,
                orderIds: List<Int>
            ): Flow<Order> = flow {
                txManager.withTransaction {
                    val existingOrders = db.findOrdersByUserId(fromUserId)
                    val ordersToTransfer = existingOrders.filter { it.id in orderIds }
                    
                    for (order in ordersToTransfer) {
                        val transferredOrder = order.copy(userId = toUserId)
                        val savedOrder = db.saveOrder(transferredOrder)
                        emit(savedOrder)
                    }
                }
            }
        }
        
        val database = MockDatabase()
        val txManager = MockTransactionManager()
        val orderRepository = TransactionalOrderRepository(database, txManager)
        
        println("1. Successful transaction:")
        val validOrders = listOf(
            Order(10, 1, 50.0, "NEW"),
            Order(11, 1, 75.0, "NEW"),
            Order(12, 2, 100.0, "NEW")
        )
        
        orderRepository.processOrderBatch(validOrders)
            .collect { order -> println("  Processed: Order ${order.id} for $${order.amount}") }
        
        println()
        
        println("2. Failed transaction (rollback):")
        val invalidOrders = listOf(
            Order(20, 1, 25.0, "NEW"),
            Order(21, 1, -50.0, "NEW"), // Invalid amount
            Order(22, 2, 30.0, "NEW")
        )
        
        try {
            orderRepository.processOrderBatch(invalidOrders)
                .collect { order -> println("  Processed: Order ${order.id}") }
        } catch (e: Exception) {
            println("  Transaction failed: ${e.message}")
        }
        
        println("Transaction management completed\n")
    }
    
    fun demonstrateDatabaseStreaming() = runBlocking {
        println("=== Database Streaming Patterns ===")
        
        // Streaming query processor
        class StreamingQueryProcessor(private val db: Database) {
            
            fun streamUserUpdates(): Flow<User> = channelFlow {
                // Initial snapshot
                val initialUsers = listOf(
                    db.findUserById(1),
                    db.findUserById(2),
                    db.findUserById(3)
                ).filterNotNull()
                
                initialUsers.forEach { send(it) }
                
                // Stream updates
                db.watchUserChanges().collect { send(it) }
            }
            
            fun batchedUserQuery(userIds: List<Int>): Flow<List<User>> = flow {
                // Batch process IDs to reduce database calls
                userIds.chunked(10).forEach { batch ->
                    val users = batch.mapNotNull { id -> db.findUserById(id) }
                    emit(users)
                }
            }
            
            fun paginatedUserQuery(pageSize: Int = 10): Flow<List<User>> = flow {
                var offset = 0
                var hasMore = true
                
                while (hasMore) {
                    // Simulate paginated query
                    val users = (1..pageSize).mapNotNull { i ->
                        db.findUserById(offset + i)
                    }
                    
                    if (users.isEmpty()) {
                        hasMore = false
                    } else {
                        emit(users)
                        offset += pageSize
                    }
                }
            }
        }
        
        val database = MockDatabase()
        val queryProcessor = StreamingQueryProcessor(database)
        
        println("1. Streaming user updates:")
        val streamingJob = launch {
            queryProcessor.streamUserUpdates()
                .take(5) // Take first 5 updates
                .collect { user ->
                    println("  Stream update: ${user.name}")
                }
        }
        
        delay(100)
        
        // Trigger some updates
        database.saveUser(User(4, "Diana", "diana@example.com"))
        database.saveUser(User(5, "Eve", "eve@example.com"))
        
        streamingJob.join()
        
        println()
        
        println("2. Batched query processing:")
        val userIds = (1..25).toList()
        queryProcessor.batchedUserQuery(userIds)
            .collect { batch ->
                println("  Batch: ${batch.size} users - ${batch.map { it.name }}")
            }
        
        println("Database streaming completed\n")
    }
}

/**
 * Network API integration patterns
 */
class NetworkIntegration {
    
    // Simulated API response models
    data class ApiResponse<T>(val data: T?, val error: String? = null, val status: Int = 200)
    data class WeatherData(val temperature: Double, val humidity: Int, val description: String)
    data class StockPrice(val symbol: String, val price: Double, val change: Double, val timestamp: Long)
    
    // Mock HTTP client
    interface HttpClient {
        suspend fun <T> get(url: String): ApiResponse<T>
        suspend fun <T> post(url: String, body: Any): ApiResponse<T>
        fun <T> websocket(url: String): Flow<T>
    }
    
    class MockHttpClient : HttpClient {
        override suspend fun <T> get(url: String): ApiResponse<T> {
            delay(Random.nextLong(100, 500)) // Simulate network delay
            
            return when {
                Random.nextDouble() < 0.1 -> { // 10% failure rate
                    ApiResponse(data = null, error = "Network timeout", status = 500)
                }
                url.contains("weather") -> {
                    @Suppress("UNCHECKED_CAST")
                    ApiResponse(
                        data = WeatherData(
                            temperature = Random.nextDouble(15.0, 35.0),
                            humidity = Random.nextInt(30, 90),
                            description = listOf("Sunny", "Cloudy", "Rainy").random()
                        ) as T
                    )
                }
                url.contains("stock") -> {
                    @Suppress("UNCHECKED_CAST")
                    ApiResponse(
                        data = StockPrice(
                            symbol = "AAPL",
                            price = Random.nextDouble(150.0, 200.0),
                            change = Random.nextDouble(-5.0, 5.0),
                            timestamp = System.currentTimeMillis()
                        ) as T
                    )
                }
                else -> ApiResponse(data = null, error = "Not found", status = 404)
            }
        }
        
        override suspend fun <T> post(url: String, body: Any): ApiResponse<T> {
            delay(Random.nextLong(200, 600))
            return ApiResponse(data = null, error = null, status = if (Random.nextBoolean()) 200 else 400)
        }
        
        override fun <T> websocket(url: String): Flow<T> = flow {
            repeat(20) { i ->
                delay(Random.nextLong(500, 1500))
                when {
                    url.contains("stock") -> {
                        @Suppress("UNCHECKED_CAST")
                        emit(StockPrice(
                            symbol = "AAPL",
                            price = 175.0 + Random.nextDouble(-10.0, 10.0),
                            change = Random.nextDouble(-2.0, 2.0),
                            timestamp = System.currentTimeMillis()
                        ) as T)
                    }
                }
            }
        }
    }
    
    fun demonstrateApiIntegrationWithRetry() = runBlocking {
        println("=== API Integration with Retry Logic ===")
        
        val httpClient = MockHttpClient()
        
        // API service with retry logic
        class WeatherService(private val client: HttpClient) {
            
            fun getCurrentWeather(city: String): Flow<WeatherData> = flow {
                val response: ApiResponse<WeatherData> = client.get("weather/$city")
                if (response.data != null) {
                    emit(response.data)
                } else {
                    throw RuntimeException(response.error ?: "Unknown error")
                }
            }.retryWhen { cause, attempt ->
                if (attempt < 3 && cause is RuntimeException) {
                    println("    Retrying weather request (attempt ${attempt + 1})")
                    delay(1000 * (attempt + 1)) // Exponential backoff
                    true
                } else {
                    false
                }
            }
            
            fun getWeatherUpdates(city: String): Flow<WeatherData> = flow {
                while (currentCoroutineContext().isActive) {
                    try {
                        getCurrentWeather(city).collect { emit(it) }
                        delay(30000) // Update every 30 seconds
                    } catch (e: Exception) {
                        println("    Weather update failed: ${e.message}")
                        delay(60000) // Wait longer on failure
                    }
                }
            }
        }
        
        val weatherService = WeatherService(httpClient)
        
        println("1. API call with retry:")
        weatherService.getCurrentWeather("London")
            .catch { e -> println("  Final failure: ${e.message}") }
            .collect { weather ->
                println("  Weather: ${weather.temperature}°C, ${weather.description}")
            }
        
        println()
        
        println("2. Continuous weather updates:")
        weatherService.getWeatherUpdates("New York")
            .take(3) // Take first 3 updates
            .collect { weather ->
                println("  Update: ${weather.temperature}°C, ${weather.humidity}% humidity")
            }
        
        println("API integration with retry completed\n")
    }
    
    fun demonstrateWebSocketIntegration() = runBlocking {
        println("=== WebSocket Integration ===")
        
        val httpClient = MockHttpClient()
        
        // WebSocket service with connection management
        class RealtimeStockService(private val client: HttpClient) {
            
            fun subscribeToStock(symbol: String): Flow<StockPrice> = client
                .websocket<StockPrice>("ws://api.example.com/stock/$symbol")
                .retryWhen { cause, attempt ->
                    println("    WebSocket connection failed, retrying... (attempt ${attempt + 1})")
                    delay(2000 * (attempt + 1))
                    attempt < 5
                }
                .catch { e ->
                    println("    WebSocket connection permanently failed: ${e.message}")
                }
            
            fun aggregateStockData(symbol: String, windowSizeMs: Long): Flow<StockSummary> = 
                subscribeToStock(symbol)
                    .windowed(windowSizeMs) { prices ->
                        StockSummary(
                            symbol = symbol,
                            count = prices.size,
                            avgPrice = prices.map { it.price }.average(),
                            minPrice = prices.minByOrNull { it.price }?.price ?: 0.0,
                            maxPrice = prices.maxByOrNull { it.price }?.price ?: 0.0,
                            totalVolume = prices.size * 100 // Mock volume
                        )
                    }
        }
        
        data class StockSummary(
            val symbol: String,
            val count: Int,
            val avgPrice: Double,
            val minPrice: Double,
            val maxPrice: Double,
            val totalVolume: Int
        )
        
        // Custom windowed operator for time-based aggregation
        fun <T, R> Flow<T>.windowed(windowSizeMs: Long, transform: (List<T>) -> R): Flow<R> = flow {
            val window = mutableListOf<T>()
            var windowStart = System.currentTimeMillis()
            
            collect { item ->
                val now = System.currentTimeMillis()
                
                if (now - windowStart >= windowSizeMs && window.isNotEmpty()) {
                    emit(transform(window.toList()))
                    window.clear()
                    windowStart = now
                }
                
                window.add(item)
            }
            
            // Emit final window if not empty
            if (window.isNotEmpty()) {
                emit(transform(window.toList()))
            }
        }
        
        val stockService = RealtimeStockService(httpClient)
        
        println("1. Real-time stock price stream:")
        stockService.subscribeToStock("AAPL")
            .take(5)
            .collect { stock ->
                val changeSign = if (stock.change >= 0) "+" else ""
                println("  AAPL: $${String.format("%.2f", stock.price)} (${changeSign}${String.format("%.2f", stock.change)})")
            }
        
        println()
        
        println("2. Aggregated stock data:")
        stockService.aggregateStockData("AAPL", 2000) // 2-second windows
            .take(3)
            .collect { summary ->
                println("  Summary: ${summary.count} ticks, avg: $${String.format("%.2f", summary.avgPrice)}")
                println("    Range: $${String.format("%.2f", summary.minPrice)} - $${String.format("%.2f", summary.maxPrice)}")
            }
        
        println("WebSocket integration completed\n")
    }
    
    fun demonstrateApiCachingPattern() = runBlocking {
        println("=== API Caching Pattern ===")
        
        // Cache implementation
        class ApiCache<K, V>(private val ttlMs: Long = 300000) { // 5 minutes TTL
            private val cache = mutableMapOf<K, Pair<V, Long>>()
            
            fun get(key: K): V? {
                val entry = cache[key]
                return if (entry != null && System.currentTimeMillis() - entry.second < ttlMs) {
                    entry.first
                } else {
                    cache.remove(key)
                    null
                }
            }
            
            fun put(key: K, value: V) {
                cache[key] = value to System.currentTimeMillis()
            }
            
            fun clear() = cache.clear()
        }
        
        // Cached API service
        class CachedStockService(private val client: HttpClient) {
            private val cache = ApiCache<String, StockPrice>(ttlMs = 10000) // 10 seconds
            
            fun getStockPrice(symbol: String): Flow<StockPrice> = flow {
                // Check cache first
                val cached = cache.get(symbol)
                if (cached != null) {
                    println("    Cache hit for $symbol")
                    emit(cached)
                } else {
                    println("    Cache miss for $symbol, fetching from API")
                    val response: ApiResponse<StockPrice> = client.get("stock/$symbol")
                    if (response.data != null) {
                        cache.put(symbol, response.data)
                        emit(response.data)
                    } else {
                        throw RuntimeException(response.error ?: "API error")
                    }
                }
            }
            
            fun getMultipleStockPrices(symbols: List<String>): Flow<StockPrice> = flow {
                symbols.forEach { symbol ->
                    try {
                        getStockPrice(symbol).collect { emit(it) }
                    } catch (e: Exception) {
                        println("    Failed to get price for $symbol: ${e.message}")
                    }
                }
            }
        }
        
        val stockService = CachedStockService(httpClient)
        
        println("1. First API call (cache miss):")
        stockService.getStockPrice("AAPL")
            .collect { stock ->
                println("  AAPL: $${String.format("%.2f", stock.price)}")
            }
        
        println()
        
        println("2. Second API call (cache hit):")
        stockService.getStockPrice("AAPL")
            .collect { stock ->
                println("  AAPL: $${String.format("%.2f", stock.price)}")
            }
        
        println()
        
        println("3. Multiple stock prices:")
        stockService.getMultipleStockPrices(listOf("AAPL", "GOOGL", "MSFT"))
            .collect { stock ->
                println("  ${stock.symbol}: $${String.format("%.2f", stock.price)}")
            }
        
        println("API caching pattern completed\n")
    }
}

/**
 * Message queue and event-driven integration
 */
class EventDrivenIntegration {
    
    // Event models
    sealed class DomainEvent {
        abstract val eventId: String
        abstract val timestamp: Long
        
        data class UserRegistered(
            override val eventId: String,
            override val timestamp: Long,
            val userId: String,
            val email: String
        ) : DomainEvent()
        
        data class OrderPlaced(
            override val eventId: String,
            override val timestamp: Long,
            val orderId: String,
            val userId: String,
            val amount: Double
        ) : DomainEvent()
        
        data class PaymentProcessed(
            override val eventId: String,
            override val timestamp: Long,
            val paymentId: String,
            val orderId: String,
            val status: String
        ) : DomainEvent()
    }
    
    fun demonstrateEventSourcing() = runBlocking {
        println("=== Event Sourcing Pattern ===")
        
        // Event store interface
        interface EventStore {
            suspend fun saveEvent(event: DomainEvent)
            fun getEventStream(aggregateId: String): Flow<DomainEvent>
            fun getAllEvents(): Flow<DomainEvent>
        }
        
        // In-memory event store implementation
        class InMemoryEventStore : EventStore {
            private val events = mutableListOf<DomainEvent>()
            private val eventSubject = MutableSharedFlow<DomainEvent>()
            
            override suspend fun saveEvent(event: DomainEvent) {
                events.add(event)
                eventSubject.emit(event)
            }
            
            override fun getEventStream(aggregateId: String): Flow<DomainEvent> {
                return flow {
                    // Emit historical events
                    events.filter { event ->
                        when (event) {
                            is DomainEvent.UserRegistered -> event.userId == aggregateId
                            is DomainEvent.OrderPlaced -> event.userId == aggregateId
                            is DomainEvent.PaymentProcessed -> event.orderId == aggregateId
                        }
                    }.forEach { emit(it) }
                }.merge(
                    // Emit new events
                    eventSubject.asSharedFlow().filter { event ->
                        when (event) {
                            is DomainEvent.UserRegistered -> event.userId == aggregateId
                            is DomainEvent.OrderPlaced -> event.userId == aggregateId
                            is DomainEvent.PaymentProcessed -> event.orderId == aggregateId
                        }
                    }
                )
            }
            
            override fun getAllEvents(): Flow<DomainEvent> = flow {
                events.forEach { emit(it) }
            }.merge(eventSubject.asSharedFlow())
        }
        
        // Event handlers
        class EmailService {
            fun handleUserEvents(events: Flow<DomainEvent>): Flow<String> = flow {
                events.collect { event ->
                    when (event) {
                        is DomainEvent.UserRegistered -> {
                            emit("📧 Welcome email sent to ${event.email}")
                        }
                        is DomainEvent.OrderPlaced -> {
                            emit("📧 Order confirmation sent for order ${event.orderId}")
                        }
                        else -> { /* ignore */ }
                    }
                }
            }
        }
        
        class NotificationService {
            fun handleEvents(events: Flow<DomainEvent>): Flow<String> = flow {
                events.collect { event ->
                    when (event) {
                        is DomainEvent.PaymentProcessed -> {
                            val status = if (event.status == "SUCCESS") "✅" else "❌"
                            emit("$status Payment ${event.status} for order ${event.orderId}")
                        }
                        else -> { /* ignore */ }
                    }
                }
            }
        }
        
        val eventStore = InMemoryEventStore()
        val emailService = EmailService()
        val notificationService = NotificationService()
        
        // Set up event handlers
        val emailJob = launch {
            emailService.handleUserEvents(eventStore.getAllEvents())
                .collect { message -> println("  $message") }
        }
        
        val notificationJob = launch {
            notificationService.handleEvents(eventStore.getAllEvents())
                .collect { message -> println("  $message") }
        }
        
        delay(100)
        
        println("1. Publishing domain events:")
        
        // Simulate business events
        eventStore.saveEvent(DomainEvent.UserRegistered(
            eventId = "event-1",
            timestamp = System.currentTimeMillis(),
            userId = "user-123",
            email = "alice@example.com"
        ))
        
        delay(100)
        
        eventStore.saveEvent(DomainEvent.OrderPlaced(
            eventId = "event-2",
            timestamp = System.currentTimeMillis(),
            orderId = "order-456",
            userId = "user-123",
            amount = 99.99
        ))
        
        delay(100)
        
        eventStore.saveEvent(DomainEvent.PaymentProcessed(
            eventId = "event-3",
            timestamp = System.currentTimeMillis(),
            paymentId = "payment-789",
            orderId = "order-456",
            status = "SUCCESS"
        ))
        
        delay(500)
        
        emailJob.cancel()
        notificationJob.cancel()
        
        println("Event sourcing pattern completed\n")
    }
    
    fun demonstrateMessageQueue() = runBlocking {
        println("=== Message Queue Integration ===")
        
        // Message queue interface
        data class Message(val topic: String, val payload: String, val headers: Map<String, String> = emptyMap())
        
        interface MessageQueue {
            suspend fun publish(message: Message)
            fun subscribe(topic: String): Flow<Message>
            fun subscribeToMultiple(topics: List<String>): Flow<Message>
        }
        
        // In-memory message queue
        class InMemoryMessageQueue : MessageQueue {
            private val topics = mutableMapOf<String, MutableSharedFlow<Message>>()
            
            private fun getOrCreateTopic(topic: String): MutableSharedFlow<Message> {
                return topics.getOrPut(topic) { MutableSharedFlow() }
            }
            
            override suspend fun publish(message: Message) {
                getOrCreateTopic(message.topic).emit(message)
            }
            
            override fun subscribe(topic: String): Flow<Message> {
                return getOrCreateTopic(topic).asSharedFlow()
            }
            
            override fun subscribeToMultiple(topics: List<String>): Flow<Message> {
                return topics.map { subscribe(it) }.merge()
            }
        }
        
        // Message processors
        class OrderProcessor {
            fun processOrders(messages: Flow<Message>): Flow<String> = flow {
                messages
                    .filter { it.topic == "orders" }
                    .collect { message ->
                        delay(Random.nextLong(100, 300)) // Simulate processing
                        emit("🛒 Processed order: ${message.payload}")
                    }
            }
        }
        
        class InventoryProcessor {
            fun updateInventory(messages: Flow<Message>): Flow<String> = flow {
                messages
                    .filter { it.topic == "orders" }
                    .collect { message ->
                        delay(Random.nextLong(50, 200))
                        emit("📦 Inventory updated for: ${message.payload}")
                    }
            }
        }
        
        val messageQueue = InMemoryMessageQueue()
        val orderProcessor = OrderProcessor()
        val inventoryProcessor = InventoryProcessor()
        
        // Set up message processors
        val orderJob = launch {
            orderProcessor.processOrders(messageQueue.subscribeToMultiple(listOf("orders")))
                .collect { result -> println("  $result") }
        }
        
        val inventoryJob = launch {
            inventoryProcessor.updateInventory(messageQueue.subscribeToMultiple(listOf("orders")))
                .collect { result -> println("  $result") }
        }
        
        delay(100)
        
        println("1. Publishing messages to queue:")
        
        // Publish messages
        messageQueue.publish(Message("orders", "Order-1: Widget x2"))
        messageQueue.publish(Message("orders", "Order-2: Gadget x1"))
        messageQueue.publish(Message("notifications", "Welcome email"))
        messageQueue.publish(Message("orders", "Order-3: Tool x3"))
        
        delay(1000)
        
        orderJob.cancel()
        inventoryJob.cancel()
        
        println("Message queue integration completed\n")
    }
}

/**
 * UI framework integration patterns
 */
class UIIntegration {
    
    // Simulated UI state models
    data class UIState(
        val isLoading: Boolean = false,
        val data: List<String> = emptyList(),
        val error: String? = null
    )
    
    data class UserProfile(val name: String, val email: String, val avatar: String)
    
    fun demonstrateMVVMPattern() = runBlocking {
        println("=== MVVM Pattern with Flows ===")
        
        // Repository layer
        class UserRepository {
            suspend fun loadUsers(): List<String> {
                delay(1000) // Simulate network call
                return listOf("Alice", "Bob", "Charlie", "Diana")
            }
            
            suspend fun loadUserProfile(name: String): UserProfile {
                delay(500)
                return UserProfile(
                    name = name,
                    email = "$name@example.com",
                    avatar = "https://example.com/$name.jpg"
                )
            }
        }
        
        // ViewModel with Flow-based state management
        class UserViewModel(private val repository: UserRepository) {
            private val _uiState = MutableStateFlow(UIState())
            val uiState: StateFlow<UIState> = _uiState.asStateFlow()
            
            private val _selectedUser = MutableStateFlow<UserProfile?>(null)
            val selectedUser: StateFlow<UserProfile?> = _selectedUser.asStateFlow()
            
            fun loadUsers() {
                flow {
                    emit(UIState(isLoading = true))
                    try {
                        val users = repository.loadUsers()
                        emit(UIState(isLoading = false, data = users))
                    } catch (e: Exception) {
                        emit(UIState(isLoading = false, error = e.message))
                    }
                }
                    .onEach { state -> _uiState.value = state }
                    .launchIn(CoroutineScope(Dispatchers.IO))
            }
            
            fun selectUser(name: String) {
                flow {
                    try {
                        val profile = repository.loadUserProfile(name)
                        _selectedUser.value = profile
                    } catch (e: Exception) {
                        println("Failed to load user profile: ${e.message}")
                    }
                }
                    .launchIn(CoroutineScope(Dispatchers.IO))
            }
        }
        
        val repository = UserRepository()
        val viewModel = UserViewModel(repository)
        
        // Simulate UI layer observing ViewModel
        val uiJob = launch {
            viewModel.uiState.collect { state ->
                when {
                    state.isLoading -> println("  UI: Loading...")
                    state.error != null -> println("  UI: Error - ${state.error}")
                    state.data.isNotEmpty() -> println("  UI: Users loaded - ${state.data}")
                }
            }
        }
        
        val profileJob = launch {
            viewModel.selectedUser
                .filterNotNull()
                .collect { profile ->
                    println("  UI: Selected user - ${profile.name} (${profile.email})")
                }
        }
        
        println("1. Loading users:")
        viewModel.loadUsers()
        
        delay(1200)
        
        println("2. Selecting user:")
        viewModel.selectUser("Alice")
        
        delay(600)
        
        uiJob.cancel()
        profileJob.cancel()
        
        println("MVVM pattern completed\n")
    }
    
    fun demonstrateProgressAndErrorHandling() = runBlocking {
        println("=== Progress and Error Handling ===")
        
        // Progress tracking
        data class ProgressState(val current: Int, val total: Int, val message: String)
        
        class DataProcessor {
            fun processWithProgress(items: List<String>): Flow<Either<ProgressState, String>> = flow {
                val total = items.size
                
                items.forEachIndexed { index, item ->
                    emit(Either.Left(ProgressState(index, total, "Processing $item")))
                    
                    delay(Random.nextLong(200, 500))
                    
                    // Simulate occasional failures
                    if (Random.nextDouble() < 0.2) {
                        throw RuntimeException("Failed to process $item")
                    }
                    
                    emit(Either.Right("✅ Processed $item"))
                }
                
                emit(Either.Left(ProgressState(total, total, "Completed")))
            }
        }
        
        sealed class Either<out L, out R> {
            data class Left<out L>(val value: L) : Either<L, Nothing>()
            data class Right<out R>(val value: R) : Either<Nothing, R>()
        }
        
        val processor = DataProcessor()
        
        println("1. Processing with progress tracking:")
        val items = listOf("Document1", "Document2", "Document3", "Document4", "Document5")
        
        processor.processWithProgress(items)
            .catch { e -> println("  ❌ Processing failed: ${e.message}") }
            .collect { result ->
                when (result) {
                    is Either.Left -> {
                        val progress = result.value
                        val percentage = if (progress.total > 0) (progress.current * 100) / progress.total else 0
                        println("  Progress: $percentage% - ${progress.message}")
                    }
                    is Either.Right -> println("  Result: ${result.value}")
                }
            }
        
        println("Progress and error handling completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Database integration
        DatabaseIntegration().demonstrateBasicDatabaseFlow()
        DatabaseIntegration().demonstrateTransactionManagement()
        DatabaseIntegration().demonstrateDatabaseStreaming()
        
        // Network integration  
        NetworkIntegration().demonstrateApiIntegrationWithRetry()
        NetworkIntegration().demonstrateWebSocketIntegration()
        NetworkIntegration().demonstrateApiCachingPattern()
        
        // Event-driven integration
        EventDrivenIntegration().demonstrateEventSourcing()
        EventDrivenIntegration().demonstrateMessageQueue()
        
        // UI integration
        UIIntegration().demonstrateMVVMPattern()
        UIIntegration().demonstrateProgressAndErrorHandling()
        
        println("=== Flow Integration Patterns Summary ===")
        println("✅ Database Integration:")
        println("   - Repository pattern with Flow-based queries")
        println("   - Transaction management with rollback support")
        println("   - Streaming queries and real-time updates")
        println("   - Pagination and batch processing")
        println()
        println("✅ Network Integration:")
        println("   - HTTP API calls with retry and exponential backoff")
        println("   - WebSocket connections with automatic reconnection")
        println("   - Response caching with TTL management")
        println("   - Error handling and fallback strategies")
        println()
        println("✅ Event-Driven Architecture:")
        println("   - Event sourcing with domain events")
        println("   - Message queue integration patterns")
        println("   - Event handlers and processing pipelines")
        println("   - Distributed system communication")
        println()
        println("✅ UI Integration:")
        println("   - MVVM pattern with StateFlow/SharedFlow")
        println("   - Progress tracking and loading states")
        println("   - Error handling and user feedback")
        println("   - Reactive UI updates with backpressure")
        println()
        println("✅ Best Practices:")
        println("   - Proper lifecycle management and resource cleanup")
        println("   - Error boundaries and graceful degradation")
        println("   - Performance optimization with caching and batching")
        println("   - Testable architecture with clear separation of concerns")
    }
}