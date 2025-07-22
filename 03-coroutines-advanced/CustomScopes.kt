/**
 * # Custom Coroutine Scopes and Lifecycle Management
 * 
 * ## Problem Description
 * Different parts of an application have different lifecycle requirements.
 * Using appropriate scopes ensures coroutines are properly managed and
 * cancelled when their associated component (Activity, Service, Request, etc.)
 * ends, preventing memory leaks and resource waste.
 * 
 * ## Solution Approach
 * Custom scopes provide:
 * - Lifecycle-aware coroutine management
 * - Resource-bound scope hierarchies
 * - Request-scoped coroutine execution
 * - Service-level scope management
 * - Automatic cleanup and cancellation
 * 
 * ## Key Learning Points
 * - Scope lifecycle should match component lifecycle
 * - Different scope types for different use cases
 * - Proper scope cancellation prevents leaks
 * - Scope hierarchies enable structured management
 * - Context inheritance and override patterns
 * 
 * ## Performance Considerations
 * - Scope creation overhead: ~0.1-1μs per scope
 * - Lifecycle operations: ~0.1-10μs per operation
 * - Cleanup operations: ~1-100μs depending on resources
 * - Proper scoping prevents memory leaks
 * 
 * ## Common Pitfalls
 * - Using GlobalScope instead of appropriate scopes
 * - Not cancelling custom scopes in cleanup
 * - Creating too many nested scopes
 * - Scope leaks in long-running applications
 * - Missing exception handling in custom scopes
 * 
 * ## Real-World Applications
 * - Android Activity/Fragment lifecycle scopes
 * - Web request-scoped coroutines
 * - Background service management
 * - Database connection pool scopes
 * - User session management
 * 
 * ## Related Concepts
 * - StructuredConcurrency.kt - Scope hierarchies
 * - ExceptionHandling.kt - Scope-level exception handling
 * - JobLifecycle.kt - Job management within scopes
 */

package coroutines.advanced

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Application-level scope hierarchy
 * 
 * Scope Hierarchy:
 * 
 * ApplicationScope (Global lifecycle)
 *     │
 *     ├── ServiceScope (Service lifecycle)
 *     │   ├── RequestScope (Request lifecycle)
 *     │   │   ├── TaskScope (Task lifecycle)
 *     │   │   └── TaskScope (Task lifecycle)
 *     │   └── RequestScope (Request lifecycle)
 *     │
 *     ├── BackgroundScope (Background tasks)
 *     │   ├── ScheduledTaskScope
 *     │   └── MaintenanceScope
 *     │
 *     └── ResourceScope (Resource-bound)
 *         ├── DatabaseScope
 *         ├── NetworkScope
 *         └── FileSystemScope
 */
class ApplicationScopeHierarchy {
    
    // Application-level scope
    class ApplicationScope {
        private val applicationJob = SupervisorJob()
        val scope = CoroutineScope(
            applicationJob + 
            Dispatchers.Default + 
            CoroutineName("Application") +
            CoroutineExceptionHandler { _, exception ->
                println("Application-level exception: ${exception.message}")
            }
        )
        
        fun shutdown() {
            println("Shutting down application scope...")
            applicationJob.cancel()
            println("Application scope shutdown completed")
        }
        
        fun isActive(): Boolean = applicationJob.isActive
    }
    
    // Service-level scope
    class ServiceScope(
        private val serviceName: String,
        parentScope: CoroutineScope
    ) {
        private val serviceJob = SupervisorJob(parentScope.coroutineContext[Job])
        val scope = CoroutineScope(
            serviceJob + 
            Dispatchers.Default + 
            CoroutineName(serviceName) +
            CoroutineExceptionHandler { _, exception ->
                println("$serviceName exception: ${exception.message}")
            }
        )
        
        fun shutdown() {
            println("Shutting down $serviceName scope...")
            serviceJob.cancel()
        }
        
        fun isActive(): Boolean = serviceJob.isActive
    }
    
    // Request-level scope
    class RequestScope(
        private val requestId: String,
        parentScope: CoroutineScope
    ) {
        private val requestJob = Job(parentScope.coroutineContext[Job])
        val scope = CoroutineScope(
            requestJob + 
            Dispatchers.IO + 
            CoroutineName("Request-$requestId")
        )
        
        fun complete() {
            requestJob.complete()
        }
        
        fun cancel() {
            requestJob.cancel()
        }
        
        fun isActive(): Boolean = requestJob.isActive
    }
    
    fun demonstrateHierarchy() = runBlocking {
        println("=== Application Scope Hierarchy ===")
        
        // Create application scope
        val appScope = ApplicationScope()
        println("Application scope created: ${appScope.isActive()}")
        
        // Create service scopes
        val userService = ServiceScope("UserService", appScope.scope)
        val orderService = ServiceScope("OrderService", appScope.scope)
        val notificationService = ServiceScope("NotificationService", appScope.scope)
        
        // Start some background services
        userService.scope.launch {
            repeat(5) { i ->
                println("  UserService: Processing batch $i")
                delay(200)
            }
        }
        
        orderService.scope.launch {
            repeat(3) { i ->
                println("  OrderService: Processing orders $i")
                delay(300)
            }
        }
        
        // Simulate request processing
        val request1 = RequestScope("REQ-001", userService.scope)
        val request2 = RequestScope("REQ-002", orderService.scope)
        
        request1.scope.launch {
            println("    Request REQ-001: Processing user data")
            delay(400)
            println("    Request REQ-001: Completed")
        }
        
        request2.scope.launch {
            println("    Request REQ-002: Processing order")
            delay(350)
            println("    Request REQ-002: Completed")
        }
        
        // Let everything run for a while
        delay(600)
        
        // Shutdown in proper order
        request1.complete()
        request2.complete()
        
        delay(100)
        userService.shutdown()
        orderService.shutdown()
        notificationService.shutdown()
        
        delay(100)
        appScope.shutdown()
        
        println("Hierarchy demo completed\n")
    }
}

/**
 * Lifecycle-aware scopes (Android-like pattern)
 */
class LifecycleAwareScopes {
    
    enum class Lifecycle { CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED }
    
    class LifecycleScope(private val componentName: String) {
        private var currentState = Lifecycle.CREATED
        private var scope: CoroutineScope? = null
        
        fun onCreate() {
            currentState = Lifecycle.CREATED
            scope = CoroutineScope(
                SupervisorJob() + 
                Dispatchers.Main + 
                CoroutineName(componentName)
            )
            println("$componentName: Created scope")
        }
        
        fun onStart() {
            currentState = Lifecycle.STARTED
            println("$componentName: Started")
        }
        
        fun onResume() {
            currentState = Lifecycle.RESUMED
            println("$componentName: Resumed")
        }
        
        fun onPause() {
            currentState = Lifecycle.PAUSED
            println("$componentName: Paused")
        }
        
        fun onStop() {
            currentState = Lifecycle.STOPPED
            println("$componentName: Stopped")
            
            // Cancel non-essential coroutines when stopped
            scope?.coroutineContext?.get(Job)?.children?.forEach { child ->
                if (child.toString().contains("background")) {
                    child.cancel()
                }
            }
        }
        
        fun onDestroy() {
            currentState = Lifecycle.DESTROYED
            scope?.cancel()
            scope = null
            println("$componentName: Destroyed scope")
        }
        
        fun launchWhenStarted(block: suspend CoroutineScope.() -> Unit): Job? {
            return if (currentState >= Lifecycle.STARTED) {
                scope?.launch(block = block)
            } else {
                println("$componentName: Cannot launch - not started")
                null
            }
        }
        
        fun launchWhenResumed(block: suspend CoroutineScope.() -> Unit): Job? {
            return if (currentState >= Lifecycle.RESUMED) {
                scope?.launch(block = block)
            } else {
                println("$componentName: Cannot launch - not resumed")
                null
            }
        }
        
        fun getScope(): CoroutineScope? = scope
        fun getState(): Lifecycle = currentState
    }
    
    fun demonstrateLifecycleScopes() = runBlocking {
        println("=== Lifecycle-Aware Scopes ===")
        
        val activityScope = LifecycleScope("MainActivity")
        val fragmentScope = LifecycleScope("UserFragment")
        
        // Simulate lifecycle events
        activityScope.onCreate()
        fragmentScope.onCreate()
        
        activityScope.onStart()
        fragmentScope.onStart()
        
        // Launch some work when started
        activityScope.launchWhenStarted {
            repeat(5) { i ->
                println("  Activity background task: $i")
                delay(200)
            }
        }
        
        activityScope.onResume()
        fragmentScope.onResume()
        
        // Launch UI-related work when resumed
        fragmentScope.launchWhenResumed {
            repeat(3) { i ->
                println("  Fragment UI task: $i")
                delay(150)
            }
        }
        
        // Simulate lifecycle changes
        delay(400)
        fragmentScope.onPause()
        
        delay(200)
        fragmentScope.onStop()
        fragmentScope.onDestroy()
        
        delay(300)
        activityScope.onPause()
        activityScope.onStop()
        activityScope.onDestroy()
        
        println("Lifecycle scopes demo completed\n")
    }
}

/**
 * Resource-bound scopes
 */
class ResourceBoundScopes {
    
    class DatabaseScope(private val connectionPool: String) {
        private val dbJob = SupervisorJob()
        val scope = CoroutineScope(
            dbJob + 
            Dispatchers.IO + 
            CoroutineName("Database-$connectionPool") +
            CoroutineExceptionHandler { _, exception ->
                println("Database operation failed: ${exception.message}")
            }
        )
        
        fun close() {
            println("Closing database scope for $connectionPool")
            dbJob.cancel()
        }
        
        suspend fun withTransaction(block: suspend () -> Unit) {
            scope.launch {
                try {
                    println("  Starting transaction in $connectionPool")
                    block()
                    println("  Committing transaction in $connectionPool")
                } catch (e: Exception) {
                    println("  Rolling back transaction in $connectionPool: ${e.message}")
                    throw e
                }
            }.join()
        }
    }
    
    class NetworkScope(private val baseUrl: String) {
        private val networkJob = SupervisorJob()
        val scope = CoroutineScope(
            networkJob + 
            Dispatchers.IO + 
            CoroutineName("Network-${baseUrl.substringAfter("//").substringBefore(":")}")
        )
        
        fun disconnect() {
            println("Disconnecting from $baseUrl")
            networkJob.cancel()
        }
        
        suspend fun makeRequest(endpoint: String): String {
            return scope.async {
                delay(100) // Simulate network delay
                "Response from $baseUrl$endpoint"
            }.await()
        }
    }
    
    class FileSystemScope(private val basePath: String) {
        private val fsJob = SupervisorJob()
        val scope = CoroutineScope(
            fsJob + 
            Dispatchers.IO + 
            CoroutineName("FileSystem-$basePath")
        )
        
        fun unmount() {
            println("Unmounting filesystem scope for $basePath")
            fsJob.cancel()
        }
        
        suspend fun processFiles(filePattern: String) {
            scope.launch {
                val files = listOf("file1.txt", "file2.txt", "file3.txt") // Simulated
                files.forEach { file ->
                    println("  Processing $basePath/$file")
                    delay(50)
                }
            }.join()
        }
    }
    
    fun demonstrateResourceBoundScopes() = runBlocking {
        println("=== Resource-Bound Scopes ===")
        
        // Create resource-bound scopes
        val primaryDb = DatabaseScope("primary-pool")
        val replicaDb = DatabaseScope("replica-pool")
        val apiNetwork = NetworkScope("https://api.example.com")
        val fileSystem = FileSystemScope("/app/data")
        
        // Use scopes for operations
        supervisorScope {
            // Database operations
            launch {
                primaryDb.withTransaction {
                    delay(200)
                    println("  Primary DB: User data updated")
                }
            }
            
            launch {
                replicaDb.withTransaction {
                    delay(150)
                    println("  Replica DB: Analytics data synced")
                }
            }
            
            // Network operations
            launch {
                val response = apiNetwork.makeRequest("/users/123")
                println("  Network: $response")
            }
            
            // File operations
            launch {
                fileSystem.processFiles("*.log")
            }
            
            delay(300)
        }
        
        // Cleanup resources
        primaryDb.close()
        replicaDb.close()
        apiNetwork.disconnect()
        fileSystem.unmount()
        
        println("Resource-bound scopes demo completed\n")
    }
}

/**
 * Request-scoped coroutines (Web service pattern)
 */
class RequestScopedCoroutines {
    
    data class RequestContext(
        val requestId: String,
        val userId: String?,
        val sessionId: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    class RequestScope(private val context: RequestContext) {
        private val requestJob = Job()
        val scope = CoroutineScope(
            requestJob + 
            Dispatchers.IO + 
            CoroutineName("Request-${context.requestId}") +
            CoroutineExceptionHandler { _, exception ->
                println("Request ${context.requestId} failed: ${exception.message}")
            }
        )
        
        fun complete() {
            requestJob.complete()
        }
        
        fun cancel(reason: String) {
            println("Cancelling request ${context.requestId}: $reason")
            requestJob.cancel()
        }
        
        fun getContext(): RequestContext = context
    }
    
    class RequestScopeManager {
        private val activeRequests = ConcurrentHashMap<String, RequestScope>()
        
        fun createRequestScope(context: RequestContext): RequestScope {
            val requestScope = RequestScope(context)
            activeRequests[context.requestId] = requestScope
            
            // Set up automatic cleanup
            requestScope.scope.launch {
                try {
                    // Wait for request completion or cancellation
                    requestScope.scope.coroutineContext[Job]?.join()
                } finally {
                    activeRequests.remove(context.requestId)
                    println("Request ${context.requestId} scope cleaned up")
                }
            }
            
            return requestScope
        }
        
        fun cancelRequest(requestId: String, reason: String = "Cancelled by user") {
            activeRequests[requestId]?.cancel(reason)
        }
        
        fun getActiveRequestCount(): Int = activeRequests.size
        
        fun shutdown() {
            println("Shutting down request scope manager...")
            activeRequests.values.forEach { it.cancel("Server shutdown") }
        }
    }
    
    fun demonstrateRequestScoped() = runBlocking {
        println("=== Request-Scoped Coroutines ===")
        
        val requestManager = RequestScopeManager()
        
        // Simulate multiple concurrent requests
        val requests = listOf(
            RequestContext("REQ-001", "user123", "session-abc"),
            RequestContext("REQ-002", "user456", "session-def"),
            RequestContext("REQ-003", null, "session-ghi"), // Anonymous request
            RequestContext("REQ-004", "user123", "session-abc") // Same user, different request
        )
        
        supervisorScope {
            requests.forEach { context ->
                launch {
                    val requestScope = requestManager.createRequestScope(context)
                    
                    // Simulate request processing
                    requestScope.scope.launch {
                        println("  Processing request ${context.requestId} for user ${context.userId ?: "anonymous"}")
                        
                        // Parallel sub-operations
                        val userDataJob = async {
                            delay(100)
                            "User data for ${context.userId ?: "anonymous"}"
                        }
                        
                        val permissionsJob = async {
                            delay(80)
                            "Permissions for session ${context.sessionId}"
                        }
                        
                        val auditJob = launch {
                            delay(50)
                            println("    Audit: Request ${context.requestId} logged")
                        }
                        
                        // Wait for essential operations
                        val userData = userDataJob.await()
                        val permissions = permissionsJob.await()
                        
                        println("    Request ${context.requestId} completed: $userData, $permissions")
                        
                        // Audit completes in background
                        auditJob.join()
                    }
                    
                    // Simulate request completion
                    delay(200)
                    requestScope.complete()
                }
            }
            
            // Monitor active requests
            launch {
                repeat(5) {
                    delay(50)
                    println("Active requests: ${requestManager.getActiveRequestCount()}")
                }
            }
            
            delay(300)
        }
        
        requestManager.shutdown()
        println("Request-scoped demo completed\n")
    }
}

/**
 * Service-level scope management
 */
class ServiceLevelScopes {
    
    abstract class ManagedService(protected val serviceName: String) {
        protected val serviceJob = SupervisorJob()
        protected val serviceScope = CoroutineScope(
            serviceJob + 
            Dispatchers.Default + 
            CoroutineName(serviceName) +
            CoroutineExceptionHandler { _, exception ->
                println("$serviceName error: ${exception.message}")
            }
        )
        
        abstract suspend fun start()
        abstract suspend fun stop()
        
        fun isRunning(): Boolean = serviceJob.isActive
        
        protected fun shutdown() {
            serviceJob.cancel()
        }
    }
    
    class UserService : ManagedService("UserService") {
        override suspend fun start() {
            println("$serviceName: Starting...")
            
            // Background user sync
            serviceScope.launch {
                repeat(Int.MAX_VALUE) {
                    delay(1000)
                    println("  $serviceName: Syncing user data...")
                }
            }
            
            // User session cleanup
            serviceScope.launch {
                repeat(Int.MAX_VALUE) {
                    delay(5000)
                    println("  $serviceName: Cleaning expired sessions...")
                }
            }
            
            println("$serviceName: Started")
        }
        
        override suspend fun stop() {
            println("$serviceName: Stopping...")
            shutdown()
            println("$serviceName: Stopped")
        }
        
        suspend fun authenticateUser(userId: String): Boolean {
            return serviceScope.async {
                delay(50) // Simulate auth check
                userId.isNotEmpty()
            }.await()
        }
    }
    
    class NotificationService : ManagedService("NotificationService") {
        override suspend fun start() {
            println("$serviceName: Starting...")
            
            // Email queue processor
            serviceScope.launch {
                repeat(Int.MAX_VALUE) {
                    delay(500)
                    println("  $serviceName: Processing email queue...")
                }
            }
            
            // Push notification sender
            serviceScope.launch {
                repeat(Int.MAX_VALUE) {
                    delay(800)
                    println("  $serviceName: Sending push notifications...")
                }
            }
            
            println("$serviceName: Started")
        }
        
        override suspend fun stop() {
            println("$serviceName: Stopping...")
            shutdown()
            println("$serviceName: Stopped")
        }
        
        suspend fun sendNotification(userId: String, message: String) {
            serviceScope.launch {
                delay(30) // Simulate sending
                println("  $serviceName: Sent to $userId: $message")
            }.join()
        }
    }
    
    class ServiceManager {
        private val services = mutableListOf<ManagedService>()
        
        suspend fun registerService(service: ManagedService) {
            services.add(service)
            service.start()
        }
        
        suspend fun shutdownAll() {
            println("ServiceManager: Shutting down all services...")
            
            // Stop services in reverse order
            services.reversed().forEach { service ->
                try {
                    service.stop()
                } catch (e: Exception) {
                    println("Error stopping service: ${e.message}")
                }
            }
            
            services.clear()
            println("ServiceManager: All services stopped")
        }
        
        fun getRunningServices(): List<String> {
            return services.filter { it.isRunning() }.map { it.serviceName }
        }
    }
    
    fun demonstrateServiceScopes() = runBlocking {
        println("=== Service-Level Scopes ===")
        
        val serviceManager = ServiceManager()
        val userService = UserService()
        val notificationService = NotificationService()
        
        // Start services
        serviceManager.registerService(userService)
        serviceManager.registerService(notificationService)
        
        delay(100)
        println("Running services: ${serviceManager.getRunningServices()}")
        
        // Use services
        supervisorScope {
            launch {
                val authenticated = userService.authenticateUser("user123")
                if (authenticated) {
                    notificationService.sendNotification("user123", "Welcome back!")
                }
            }
            
            launch {
                val authenticated = userService.authenticateUser("user456")
                if (authenticated) {
                    notificationService.sendNotification("user456", "New message available")
                }
            }
        }
        
        // Let services run for a bit
        delay(2000)
        
        // Graceful shutdown
        serviceManager.shutdownAll()
        
        println("Service-level scopes demo completed\n")
    }
}

/**
 * Best practices for custom scopes
 */
class CustomScopeBestPractices {
    
    fun demonstrateBestPractices() = runBlocking {
        println("=== Custom Scope Best Practices ===")
        
        // ✅ Best Practice 1: Use appropriate Job types
        println("1. Appropriate Job Types:")
        
        // Use SupervisorJob for independent failure handling
        val independentScope = CoroutineScope(
            SupervisorJob() + 
            Dispatchers.Default + 
            CoroutineName("IndependentTasks")
        )
        
        // Use regular Job when failure should cancel siblings
        val dependentScope = CoroutineScope(
            Job() + 
            Dispatchers.Default + 
            CoroutineName("DependentTasks")
        )
        
        // ✅ Best Practice 2: Always include exception handlers
        println("\n2. Exception Handlers:")
        
        val robustScope = CoroutineScope(
            SupervisorJob() + 
            Dispatchers.Default + 
            CoroutineName("RobustService") +
            CoroutineExceptionHandler { context, exception ->
                println("  Unhandled exception in ${context[CoroutineName]}: ${exception.message}")
                // Could send to error tracking service
            }
        )
        
        // ✅ Best Practice 3: Scope lifecycle management
        println("\n3. Lifecycle Management:")
        
        class ManagedScope(name: String) {
            private val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Default + CoroutineName(name))
            
            fun shutdown() {
                job.cancel()
            }
            
            fun isActive(): Boolean = job.isActive
        }
        
        val managedScope = ManagedScope("ManagedExample")
        
        // ✅ Best Practice 4: Proper cancellation
        println("\n4. Proper Cancellation:")
        
        try {
            // Use scopes for work
            independentScope.launch {
                delay(100)
                println("  Independent task completed")
            }
            
            robustScope.launch {
                delay(150)
                throw RuntimeException("Handled by exception handler")
            }
            
            managedScope.scope.launch {
                delay(200)
                println("  Managed task completed")
            }
            
            delay(250)
            
        } finally {
            // Always clean up scopes
            independentScope.cancel()
            dependentScope.cancel()
            robustScope.cancel()
            managedScope.shutdown()
            
            println("  All scopes cleaned up")
        }
        
        // ✅ Best Practice 5: Scope hierarchies
        println("\n5. Scope Hierarchies:")
        
        val parentScope = CoroutineScope(SupervisorJob() + CoroutineName("Parent"))
        val childScope = CoroutineScope(
            Job(parentScope.coroutineContext[Job]) + 
            CoroutineName("Child")
        )
        
        try {
            parentScope.launch {
                println("  Parent scope work")
                delay(100)
            }
            
            childScope.launch {
                println("  Child scope work")
                delay(100)
            }
            
            delay(150)
            
        } finally {
            childScope.cancel() // Cancel child first
            parentScope.cancel() // Then parent
        }
        
        println("Best practices demo completed\n")
    }
    
    fun demonstrateAntiPatterns() = runBlocking {
        println("=== Custom Scope Anti-Patterns ===")
        
        // ❌ Anti-Pattern 1: Using GlobalScope
        println("1. Avoid GlobalScope (use proper scopes):")
        println("  ❌ GlobalScope.launch { /* work */ }")
        println("  ✅ properScope.launch { /* work */ }")
        
        // ❌ Anti-Pattern 2: Not cancelling scopes
        println("\n2. Avoid scope leaks:")
        println("  ❌ Creating scopes without cleanup")
        println("  ✅ Always cancel scopes in finally/cleanup")
        
        // ❌ Anti-Pattern 3: Wrong Job type
        println("\n3. Avoid wrong Job types:")
        println("  ❌ Using Job() when failures should be independent")
        println("  ✅ Using SupervisorJob() for independent failures")
        
        // ❌ Anti-Pattern 4: Missing exception handlers
        println("\n4. Avoid missing exception handlers:")
        println("  ❌ Scope without CoroutineExceptionHandler")
        println("  ✅ Always include exception handling")
        
        // ❌ Anti-Pattern 5: Scope proliferation
        println("\n5. Avoid too many scopes:")
        println("  ❌ Creating new scope for every small operation")
        println("  ✅ Reuse appropriate existing scopes")
        
        println("Anti-patterns demo completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Application scope hierarchy
        ApplicationScopeHierarchy().demonstrateHierarchy()
        
        // Lifecycle-aware scopes
        LifecycleAwareScopes().demonstrateLifecycleScopes()
        
        // Resource-bound scopes
        ResourceBoundScopes().demonstrateResourceBoundScopes()
        
        // Request-scoped coroutines
        RequestScopedCoroutines().demonstrateRequestScoped()
        
        // Service-level scopes
        ServiceLevelScopes().demonstrateServiceScopes()
        
        // Best practices and anti-patterns
        CustomScopeBestPractices().demonstrateBestPractices()
        CustomScopeBestPractices().demonstrateAntiPatterns()
        
        println("=== Custom Scopes Summary ===")
        println("✅ Scope Types:")
        println("   - Application scope: Global lifecycle management")
        println("   - Service scope: Service-level coroutine management")
        println("   - Request scope: Request-bound coroutine execution")
        println("   - Resource scope: Resource-bound lifecycle management")
        println()
        println("✅ Lifecycle Management:")
        println("   - Match scope lifecycle to component lifecycle")
        println("   - Always cancel scopes in cleanup/finally blocks")
        println("   - Use proper Job types (SupervisorJob vs Job)")
        println("   - Include CoroutineExceptionHandler for unhandled exceptions")
        println()
        println("✅ Best Practices:")
        println("   - Avoid GlobalScope in favor of proper scopes")
        println("   - Create scope hierarchies that match component hierarchies")
        println("   - Reuse scopes appropriately to avoid proliferation")
        println("   - Monitor scope health and resource usage")
        println()
        println("✅ Production Considerations:")
        println("   - Implement graceful shutdown sequences")
        println("   - Add monitoring and alerting for scope leaks")
        println("   - Use lifecycle-aware scope patterns")
        println("   - Plan for error isolation and recovery")
    }
}