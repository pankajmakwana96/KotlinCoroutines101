/**
 * # Coroutine Context and Inheritance
 * 
 * ## Problem Description
 * Coroutines need context information like which thread to run on, how to
 * handle exceptions, debugging information, and lifecycle management.
 * Understanding how context works and is inherited is crucial for
 * predictable coroutine behavior.
 * 
 * ## Solution Approach
 * CoroutineContext is a set of elements that define coroutine behavior:
 * - Job: Lifecycle management
 * - Dispatcher: Thread assignment  
 * - CoroutineExceptionHandler: Error handling
 * - CoroutineName: Debugging aid
 * Context inheritance follows structured concurrency principles.
 * 
 * ## Key Learning Points
 * - CoroutineContext is a persistent map of context elements
 * - Child coroutines inherit parent context with potential overrides
 * - Context combination with + operator
 * - Context element access and modification
 * - Context propagation in coroutine hierarchies
 * 
 * ## Performance Considerations
 * - Context lookup is O(1) for small contexts
 * - Context creation and copying has minimal overhead
 * - Inherited context reduces redundant configuration
 * - Context switching overhead depends on dispatcher changes
 * 
 * ## Common Pitfalls
 * - Not understanding context inheritance rules
 * - Accidentally overriding important context elements
 * - Missing context elements in nested coroutines
 * - Confusion about when context is copied vs shared
 * 
 * ## Real-World Applications
 * - Request-scoped error handling in web servers
 * - User session context propagation
 * - Debugging context for distributed tracing
 * - Thread-local-like behavior across suspensions
 * - Configuration inheritance in microservices
 * 
 * ## Related Concepts
 * - Dispatchers.kt - Dispatcher context element
 * - StructuredConcurrency.kt - Job context element
 * - JobLifecycle.kt - Job context management
 */

package coroutines

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * CoroutineContext basics and structure
 * 
 * Context Structure:
 * 
 * CoroutineContext = Job + Dispatcher + CoroutineExceptionHandler + CoroutineName + ...
 *                    │     │            │                         │
 *                    │     │            │                         └─ Debugging
 *                    │     │            └─ Exception handling
 *                    │     └─ Thread assignment
 *                    └─ Lifecycle management
 * 
 * Context Inheritance:
 * 
 * Parent Context ──inheritance──> Child Context
 *       │                             │
 *       ├─ Job ──────────────────> Child Job (new)
 *       ├─ Dispatcher ───────────> Same Dispatcher (inherited)
 *       ├─ ExceptionHandler ─────> Same Handler (inherited)
 *       └─ Name ─────────────────> Same Name (inherited)
 */
class CoroutineContextBasics {
    
    fun demonstrateContextElements() = runBlocking {
        println("=== CoroutineContext Elements ===")
        
        // Access current context elements
        val currentContext = coroutineContext
        println("Current context: $currentContext")
        
        val job = currentContext[Job]
        val dispatcher = currentContext[ContinuationInterceptor]
        val name = currentContext[CoroutineName]
        
        println("Job: $job")
        println("Dispatcher: $dispatcher")
        println("Name: $name")
        
        // Create context with specific elements
        val customContext = Job() + 
                           Dispatchers.Default + 
                           CoroutineName("CustomCoroutine") +
                           CoroutineExceptionHandler { _, exception ->
                               println("Exception caught: ${exception.message}")
                           }
        
        println("\nCustom context: $customContext")
        
        // Launch with custom context
        launch(customContext) {
            val context = coroutineContext
            println("Custom coroutine context: $context")
            println("Custom coroutine name: ${context[CoroutineName]}")
            println("Custom coroutine dispatcher: ${context[ContinuationInterceptor]}")
        }.join()
        
        println()
    }
    
    fun demonstrateContextCombination() = runBlocking {
        println("=== Context Combination ===")
        
        // Base context
        val baseContext = Dispatchers.IO + CoroutineName("BaseCoroutine")
        println("Base context: $baseContext")
        
        // Add exception handler
        val contextWithHandler = baseContext + CoroutineExceptionHandler { _, exception ->
            println("Handler caught: ${exception.message}")
        }
        println("With handler: $contextWithHandler")
        
        // Override dispatcher (right side wins)
        val contextWithNewDispatcher = contextWithHandler + Dispatchers.Default
        println("With new dispatcher: $contextWithNewDispatcher")
        
        // Remove element using EmptyCoroutineContext
        val contextWithoutName = contextWithNewDispatcher.minusKey(CoroutineName)
        println("Without name: $contextWithoutName")
        
        println()
    }
    
    fun demonstrateContextAccess() = runBlocking {
        println("=== Context Access Patterns ===")
        
        launch(CoroutineName("AccessDemo") + Dispatchers.Default) {
            val context = coroutineContext
            
            // Check if element exists
            val hasName = context[CoroutineName] != null
            println("Has name: $hasName")
            
            // Get element with default
            val name = context[CoroutineName]?.name ?: "Unnamed"
            println("Coroutine name: $name")
            
            // Access job for lifecycle management
            val job = context[Job]
            println("Job: $job")
            println("Job is active: ${job?.isActive}")
            
            // Check dispatcher
            val dispatcher = context[ContinuationInterceptor]
            println("Running on: $dispatcher")
        }.join()
        
        println()
    }
}

/**
 * Context inheritance in coroutine hierarchies
 */
class ContextInheritance {
    
    fun demonstrateBasicInheritance() = runBlocking {
        println("=== Basic Context Inheritance ===")
        
        // Parent context
        val parentContext = CoroutineName("Parent") + 
                           Dispatchers.Default +
                           CoroutineExceptionHandler { _, exception ->
                               println("Parent handler: ${exception.message}")
                           }
        
        launch(parentContext) {
            println("Parent coroutine:")
            println("  Name: ${coroutineContext[CoroutineName]}")
            println("  Dispatcher: ${coroutineContext[ContinuationInterceptor]}")
            println("  Job: ${coroutineContext[Job]}")
            
            // Child inherits parent context
            launch {
                println("Child coroutine (inherited):")
                println("  Name: ${coroutineContext[CoroutineName]}")
                println("  Dispatcher: ${coroutineContext[ContinuationInterceptor]}")
                println("  Job: ${coroutineContext[Job]}")
                println("  Parent job: ${coroutineContext[Job]?.parent}")
            }
            
            // Child with overridden elements
            launch(CoroutineName("Child") + Dispatchers.IO) {
                println("Child coroutine (overridden):")
                println("  Name: ${coroutineContext[CoroutineName]}")
                println("  Dispatcher: ${coroutineContext[ContinuationInterceptor]}")
                println("  Job: ${coroutineContext[Job]}")
                println("  Parent job: ${coroutineContext[Job]?.parent}")
            }
            
            delay(100) // Wait for children
        }.join()
        
        println()
    }
    
    fun demonstrateJobInheritance() = runBlocking {
        println("=== Job Inheritance ===")
        
        launch(CoroutineName("GrandParent")) {
            val grandParentJob = coroutineContext[Job]
            println("GrandParent job: $grandParentJob")
            
            launch(CoroutineName("Parent")) {
                val parentJob = coroutineContext[Job]
                println("Parent job: $parentJob")
                println("Parent's parent: ${parentJob?.parent}")
                println("Is parent child of grandparent: ${parentJob?.parent == grandParentJob}")
                
                launch(CoroutineName("Child")) {
                    val childJob = coroutineContext[Job]
                    println("Child job: $childJob")
                    println("Child's parent: ${childJob?.parent}")
                    println("Is child child of parent: ${childJob?.parent == parentJob}")
                    
                    // Demonstrate hierarchy
                    var currentJob = childJob
                    var level = 0
                    while (currentJob != null) {
                        val indent = "  ".repeat(level)
                        println("${indent}Level $level: $currentJob")
                        currentJob = currentJob.parent
                        level++
                    }
                }
                
                delay(50)
            }
            
            delay(100)
        }.join()
        
        println()
    }
    
    fun demonstrateExceptionHandlerInheritance() = runBlocking {
        println("=== Exception Handler Inheritance ===")
        
        val parentHandler = CoroutineExceptionHandler { _, exception ->
            println("Parent handler caught: ${exception.message}")
        }
        
        launch(parentHandler + CoroutineName("ParentWithHandler")) {
            println("Parent with handler started")
            
            // Child inherits exception handler
            launch(CoroutineName("Child1")) {
                delay(100)
                throw RuntimeException("Child1 exception")
            }
            
            // Child with its own handler
            launch(CoroutineName("Child2") + CoroutineExceptionHandler { _, exception ->
                println("Child2 handler caught: ${exception.message}")
            }) {
                delay(200)
                throw RuntimeException("Child2 exception")
            }
            
            delay(300)
        }.join()
        
        println()
    }
}

/**
 * Custom context elements
 */
class CustomContextElements {
    
    // Custom context element for user information
    data class UserContext(val userId: String, val userName: String) : AbstractCoroutineContextElement(UserContext) {
        companion object Key : CoroutineContext.Key<UserContext>
        
        override fun toString(): String = "UserContext(userId=$userId, userName=$userName)"
    }
    
    // Custom context element for request tracing
    data class TraceContext(val traceId: String, val spanId: String) : AbstractCoroutineContextElement(TraceContext) {
        companion object Key : CoroutineContext.Key<TraceContext>
        
        override fun toString(): String = "TraceContext(traceId=$traceId, spanId=$spanId)"
    }
    
    fun demonstrateCustomContextElements() = runBlocking {
        println("=== Custom Context Elements ===")
        
        val userContext = UserContext("user123", "Alice")
        val traceContext = TraceContext("trace-456", "span-789")
        
        launch(userContext + traceContext + CoroutineName("CustomContextDemo")) {
            println("Coroutine with custom context:")
            println("  User: ${coroutineContext[UserContext]}")
            println("  Trace: ${coroutineContext[TraceContext]}")
            println("  Name: ${coroutineContext[CoroutineName]}")
            
            // Custom context is inherited by children
            launch {
                println("Child coroutine:")
                println("  User: ${coroutineContext[UserContext]}")
                println("  Trace: ${coroutineContext[TraceContext]}")
                
                // Simulate business logic that uses context
                processRequest()
            }
            
            delay(100)
        }.join()
        
        println()
    }
    
    private suspend fun processRequest() {
        val userContext = coroutineContext[UserContext]
        val traceContext = coroutineContext[TraceContext]
        
        println("  Processing request for user: ${userContext?.userName}")
        println("  Trace ID: ${traceContext?.traceId}")
        
        // Simulate async operations that preserve context
        withContext(Dispatchers.IO) {
            println("  I/O operation with context:")
            println("    User: ${coroutineContext[UserContext]}")
            println("    Trace: ${coroutineContext[TraceContext]}")
        }
    }
    
    fun demonstrateContextPropagation() = runBlocking {
        println("=== Context Propagation in Async Operations ===")
        
        val requestContext = UserContext("req-user", "Bob") + 
                           TraceContext("req-trace", "req-span") +
                           CoroutineName("RequestProcessor")
        
        launch(requestContext) {
            println("Request processing started")
            println("  Context: ${coroutineContext[UserContext]}")
            
            // Parallel processing preserves context
            val results = listOf("task1", "task2", "task3").map { taskName ->
                async {
                    processTask(taskName)
                }
            }
            
            val completedResults = results.awaitAll()
            println("All tasks completed: $completedResults")
        }.join()
        
        println()
    }
    
    private suspend fun processTask(taskName: String): String {
        val userContext = coroutineContext[UserContext]
        val traceContext = coroutineContext[TraceContext]
        
        println("  Task $taskName processing:")
        println("    User: ${userContext?.userId}")
        println("    Trace: ${traceContext?.traceId}")
        
        delay(100) // Simulate work
        return "Result-$taskName"
    }
}

/**
 * Context debugging and inspection
 */
class ContextDebugging {
    
    fun demonstrateContextDebugging() = runBlocking {
        println("=== Context Debugging ===")
        
        // Enable coroutine debugging
        System.setProperty("kotlinx.coroutines.debug", "on")
        
        val debugContext = CoroutineName("DebugDemo") + 
                          Dispatchers.Default +
                          CoroutineExceptionHandler { context, exception ->
                              println("Exception in context $context: ${exception.message}")
                          }
        
        launch(debugContext) {
            println("Debug coroutine context:")
            inspectContext(coroutineContext)
            
            launch(CoroutineName("ChildDebug")) {
                println("\nChild coroutine context:")
                inspectContext(coroutineContext)
                
                // Simulate different contexts
                withContext(Dispatchers.IO + CoroutineName("IOContext")) {
                    println("\nWithContext IO:")
                    inspectContext(coroutineContext)
                }
            }
            
            delay(100)
        }.join()
        
        println()
    }
    
    private fun inspectContext(context: CoroutineContext) {
        println("  Full context: $context")
        
        // Inspect individual elements
        context.fold("Elements:") { acc, element ->
            println("  $acc")
            println("    ${element.key}: $element")
            acc
        }
        
        // Check for specific elements
        val job = context[Job]
        val dispatcher = context[ContinuationInterceptor]
        val name = context[CoroutineName]
        val handler = context[CoroutineExceptionHandler]
        
        println("  Job: $job")
        println("  Dispatcher: $dispatcher")  
        println("  Name: $name")
        println("  Exception Handler: $handler")
    }
    
    fun demonstrateContextInAndroidLike() = runBlocking {
        println("=== Android-like Context Usage ===")
        
        // Simulate Android-like lifecycle context
        class ActivityScope {
            private val activityJob = SupervisorJob()
            private val activityContext = Dispatchers.Main + activityJob + CoroutineName("Activity")
            val scope = CoroutineScope(activityContext)
            
            fun onCreate() {
                scope.launch {
                    println("Activity created with context: ${coroutineContext[CoroutineName]}")
                    
                    // Launch background task
                    launch(Dispatchers.IO + CoroutineName("BackgroundTask")) {
                        println("Background task context: ${coroutineContext[CoroutineName]}")
                        delay(500)
                        
                        // Update UI
                        withContext(Dispatchers.Main) {
                            println("UI update context: ${coroutineContext[CoroutineName]}")
                        }
                    }
                }
            }
            
            fun onDestroy() {
                activityJob.cancel()
                println("Activity destroyed, all coroutines cancelled")
            }
        }
        
        val activity = ActivityScope()
        activity.onCreate()
        
        delay(300)
        activity.onDestroy()
        
        println()
    }
}

/**
 * Context performance and best practices
 */
class ContextBestPractices {
    
    fun demonstrateBestPractices() = runBlocking {
        println("=== Context Best Practices ===")
        
        // ✅ Good: Minimal context creation
        val baseContext = Dispatchers.Default + CoroutineName("BaseTask")
        
        // ✅ Good: Reuse context for similar operations
        repeat(3) { i ->
            launch(baseContext) {
                println("Task $i with reused context: ${coroutineContext[CoroutineName]}")
                delay(100)
            }
        }
        
        delay(150)
        
        // ✅ Good: Override only what's necessary
        launch(baseContext + Dispatchers.IO) {
            println("I/O task with minimal override: ${coroutineContext[CoroutineName]}")
            println("  Dispatcher: ${coroutineContext[ContinuationInterceptor]}")
        }
        
        // ❌ Avoid: Unnecessary context elements
        val bloatedContext = Dispatchers.Default + 
                           CoroutineName("Bloated") +
                           CoroutineExceptionHandler { _, _ -> } +
                           // Don't add elements you don't need
                           Job() // Usually inherited automatically
        
        println("Bloated context (avoid): $bloatedContext")
        
        // ✅ Good: Context validation
        validateContext(baseContext)
        
        delay(100)
        println()
    }
    
    private fun validateContext(context: CoroutineContext) {
        // Check for required elements
        require(context[ContinuationInterceptor] != null) { "Dispatcher required" }
        require(context[CoroutineName] != null) { "Name required for debugging" }
        
        println("Context validation passed: $context")
    }
    
    fun demonstrateContextPerformance() = runBlocking {
        println("=== Context Performance ===")
        
        val iterations = 100_000
        
        // Measure context creation overhead
        val creationTime = kotlin.system.measureTimeMillis {
            repeat(iterations) {
                val context = Dispatchers.Default + CoroutineName("Test$it")
                // Use context to prevent optimization
                context[CoroutineName]
            }
        }
        println("Context creation time: ${creationTime}ms for $iterations iterations")
        
        // Measure context lookup overhead
        val testContext = Dispatchers.Default + CoroutineName("LookupTest") + Job()
        val lookupTime = kotlin.system.measureTimeMillis {
            repeat(iterations) {
                val name = testContext[CoroutineName]
                val job = testContext[Job]
                val dispatcher = testContext[ContinuationInterceptor]
                // Use elements to prevent optimization
                "$name $job $dispatcher"
            }
        }
        println("Context lookup time: ${lookupTime}ms for $iterations iterations")
        
        println()
    }
    
    fun demonstrateContextPatterns() = runBlocking {
        println("=== Common Context Patterns ===")
        
        // Pattern 1: Service context
        val serviceContext = SupervisorJob() + 
                           Dispatchers.Default + 
                           CoroutineName("UserService") +
                           CoroutineExceptionHandler { _, exception ->
                               println("Service error: ${exception.message}")
                           }
        
        // Pattern 2: Request context
        suspend fun handleRequest(requestId: String) = withContext(
            serviceContext + CoroutineName("Request-$requestId")
        ) {
            println("Handling request $requestId")
            println("  Context: ${coroutineContext[CoroutineName]}")
            delay(100)
            "Response for $requestId"
        }
        
        // Pattern 3: Background processing context
        val backgroundContext = Dispatchers.IO + CoroutineName("BackgroundProcessor")
        
        // Use patterns
        val responses = listOf("req1", "req2", "req3").map { requestId ->
            async {
                handleRequest(requestId)
            }
        }.awaitAll()
        
        println("Request responses: $responses")
        
        launch(backgroundContext) {
            println("Background processing with: ${coroutineContext[CoroutineName]}")
        }.join()
        
        println()
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Context basics
    CoroutineContextBasics().demonstrateContextElements()
    CoroutineContextBasics().demonstrateContextCombination()
    CoroutineContextBasics().demonstrateContextAccess()
    
    // Context inheritance
    ContextInheritance().demonstrateBasicInheritance()
    ContextInheritance().demonstrateJobInheritance()
    ContextInheritance().demonstrateExceptionHandlerInheritance()
    
    // Custom context elements
    CustomContextElements().demonstrateCustomContextElements()
    CustomContextElements().demonstrateContextPropagation()
    
    // Context debugging
    ContextDebugging().demonstrateContextDebugging()
    ContextDebugging().demonstrateContextInAndroidLike()
    
    // Best practices
    ContextBestPractices().demonstrateBestPractices()
    ContextBestPractices().demonstrateContextPerformance()
    ContextBestPractices().demonstrateContextPatterns()
    
    println("=== CoroutineContext Summary ===")
    println("- CoroutineContext is a set of elements defining coroutine behavior")
    println("- Child coroutines inherit parent context with possible overrides")
    println("- Use + operator to combine context elements")
    println("- Job context element is usually created automatically")
    println("- Custom context elements enable domain-specific data propagation")
    println("- Context inheritance follows structured concurrency principles")
    println("- Minimize context creation for better performance")
    println("- Use context for debugging, tracing, and request-scoped data")
}