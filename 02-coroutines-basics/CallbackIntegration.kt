/**
 * # Wrapping Futures & Callbacks - Integration Patterns
 * 
 * ## Problem Description
 * Many existing libraries use callback-based APIs, CompletableFuture, RxJava Observables,
 * or other asynchronous patterns that don't directly integrate with Kotlin coroutines.
 * To use these APIs in coroutine-based code, we need bridge patterns that convert
 * between different asynchronous paradigms while preserving cancellation and error handling.
 * 
 * ## Solution Approach
 * Integration patterns include:
 * - suspendCoroutine for simple callback conversion
 * - suspendCancellableCoroutine for cancellation-aware integration
 * - CompletableFuture.await() for future integration
 * - callbackFlow for streaming callback APIs
 * - Custom adapters for third-party libraries
 * - Error handling and timeout management
 * 
 * ## Key Learning Points
 * - suspendCoroutine bridges callback-based APIs to coroutines
 * - suspendCancellableCoroutine adds cancellation support
 * - Proper resource cleanup is essential in callback integration
 * - Error propagation must be handled correctly
 * - Timeouts and cancellation require special attention
 * - Different callback patterns need different integration strategies
 * 
 * ## Performance Considerations
 * - Callback wrapping overhead: ~100-500ns per call
 * - Future conversion: ~50-200ns per conversion
 * - Memory allocation for continuations: ~100-200 bytes
 * - Cancellation listener registration: ~50-100ns
 * - Resource cleanup cost varies by library
 * 
 * ## Common Pitfalls
 * - Not handling cancellation in callback integration
 * - Memory leaks from uncleaned callback registrations
 * - Race conditions between callback and cancellation
 * - Improper error propagation from callbacks
 * - Missing timeout handling in long-running operations
 * 
 * ## Real-World Applications
 * - Database driver integration (JDBC callbacks)
 * - HTTP client libraries (OkHttp, Retrofit callbacks)
 * - Message queue clients (RabbitMQ, Kafka callbacks)
 * - Cloud SDK integration (AWS, Google Cloud callbacks)
 * - Legacy system integration
 * 
 * ## Related Concepts
 * - Continuation-passing style (CPS)
 * - Promise/Future patterns
 * - Observer pattern integration
 * - Event-driven programming bridges
 */

package coroutines.basics.integration

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.*
import kotlin.random.Random

/**
 * Basic callback integration with suspendCoroutine
 * 
 * Callback Integration Pattern:
 * 
 * Legacy Callback API:
 * interface Callback<T> {
 *     fun onSuccess(result: T)
 *     fun onError(error: Throwable)
 * }
 * 
 * Coroutine Integration:
 * suspend fun legacyOperation(): T = suspendCoroutine { continuation ->
 *     legacyAPI.execute(object : Callback<T> {
 *         override fun onSuccess(result: T) = continuation.resume(result)
 *         override fun onError(error: Throwable) = continuation.resumeWithException(error)
 *     })
 * }
 * 
 * Integration Flow:
 * Coroutine ──suspend──> Callback Registration ──callback──> Continuation Resume
 */
class BasicCallbackIntegration {
    
    fun demonstrateBasicCallbackWrapping() = runBlocking {
        println("=== Basic Callback Wrapping ===")
        
        // Simulate legacy callback-based API
        interface AsyncCallback<T> {
            fun onSuccess(result: T)
            fun onFailure(error: Exception)
        }
        
        class LegacyAsyncService {
            fun fetchDataAsync(id: String, callback: AsyncCallback<String>) {
                // Simulate async operation with callback
                val executor = Executors.newSingleThreadExecutor()
                executor.submit {
                    Thread.sleep(300) // Simulate work
                    
                    when {
                        id == "error" -> callback.onFailure(Exception("Data not found"))
                        id.isEmpty() -> callback.onFailure(IllegalArgumentException("ID cannot be empty"))
                        else -> callback.onSuccess("Data for $id")
                    }
                }
                executor.shutdown()
            }
            
            fun computeAsync(value: Int, callback: AsyncCallback<Int>) {
                val executor = Executors.newSingleThreadExecutor()
                executor.submit {
                    Thread.sleep(200)
                    
                    if (value < 0) {
                        callback.onFailure(IllegalArgumentException("Value must be positive"))
                    } else {
                        callback.onSuccess(value * value)
                    }
                }
                executor.shutdown()
            }
        }
        
        val legacyService = LegacyAsyncService()
        
        // Convert callback-based API to suspend functions
        suspend fun fetchData(id: String): String = suspendCoroutine { continuation ->
            legacyService.fetchDataAsync(id, object : AsyncCallback<String> {
                override fun onSuccess(result: String) {
                    continuation.resume(result)
                }
                
                override fun onFailure(error: Exception) {
                    continuation.resumeWithException(error)
                }
            })
        }
        
        suspend fun compute(value: Int): Int = suspendCoroutine { continuation ->
            legacyService.computeAsync(value, object : AsyncCallback<Int> {
                override fun onSuccess(result: Int) {
                    continuation.resume(result)
                }
                
                override fun onFailure(error: Exception) {
                    continuation.resumeWithException(error)
                }
            })
        }
        
        println("1. Basic callback to coroutine conversion:")
        
        // Test successful operations
        try {
            val data1 = fetchData("user123")
            println("  ✅ Fetched data: $data1")
            
            val result1 = compute(5)
            println("  ✅ Computed result: $result1")
        } catch (e: Exception) {
            println("  ❌ Unexpected error: ${e.message}")
        }
        
        // Test error handling
        try {
            val data2 = fetchData("error")
            println("  ✅ This shouldn't be reached: $data2")
        } catch (e: Exception) {
            println("  ✅ Expected error handled: ${e.message}")
        }
        
        try {
            val result2 = compute(-5)
            println("  ✅ This shouldn't be reached: $result2")
        } catch (e: Exception) {
            println("  ✅ Expected error handled: ${e.message}")
        }
        
        println()
        
        // Demonstrate concurrent callback operations
        println("2. Concurrent callback operations:")
        
        val concurrentResults = listOf("item1", "item2", "item3", "item4").map { id ->
            async {
                try {
                    val data = fetchData(id)
                    "Success: $data"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
        }
        
        concurrentResults.awaitAll().forEachIndexed { index, result ->
            println("  Concurrent result $index: $result")
        }
        
        println("Basic callback wrapping completed\n")
    }
    
    fun demonstrateGenericCallbackWrapper() = runBlocking {
        println("=== Generic Callback Wrapper ===")
        
        // Generic callback wrapper utility
        class CallbackWrapper {
            companion object {
                suspend fun <T> wrapCallback(
                    operation: (onSuccess: (T) -> Unit, onError: (Exception) -> Unit) -> Unit
                ): T = suspendCoroutine { continuation ->
                    operation(
                        onSuccess = { result -> continuation.resume(result) },
                        onError = { error -> continuation.resumeWithException(error) }
                    )
                }
                
                suspend fun <T> wrapSingleCallback(
                    operation: (callback: (T?, Exception?) -> Unit) -> Unit
                ): T = suspendCoroutine { continuation ->
                    operation { result, error ->
                        when {
                            error != null -> continuation.resumeWithException(error)
                            result != null -> continuation.resume(result)
                            else -> continuation.resumeWithException(Exception("Both result and error are null"))
                        }
                    }
                }
            }
        }
        
        // Mock legacy APIs with different callback patterns
        class LegacyNetworkAPI {
            fun get(url: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
                Thread {
                    Thread.sleep(250)
                    if (url.contains("error")) {
                        onError(Exception("Network error for $url"))
                    } else {
                        onSuccess("Response from $url")
                    }
                }.start()
            }
            
            fun post(data: String, callback: (String?, Exception?) -> Unit) {
                Thread {
                    Thread.sleep(200)
                    if (data.isEmpty()) {
                        callback(null, Exception("Empty data"))
                    } else {
                        callback("Posted: $data", null)
                    }
                }.start()
            }
        }
        
        val networkAPI = LegacyNetworkAPI()
        
        // Wrapped network operations
        suspend fun httpGet(url: String): String {
            return CallbackWrapper.wrapCallback { onSuccess, onError ->
                networkAPI.get(url, onSuccess, onError)
            }
        }
        
        suspend fun httpPost(data: String): String {
            return CallbackWrapper.wrapSingleCallback { callback ->
                networkAPI.post(data, callback)
            }
        }
        
        println("1. Generic wrapper usage:")
        
        // Test different callback patterns
        try {
            val getResult = httpGet("https://api.example.com/users")
            println("  GET result: $getResult")
            
            val postResult = httpPost("Hello, World!")
            println("  POST result: $postResult")
        } catch (e: Exception) {
            println("  Error: ${e.message}")
        }
        
        // Test error scenarios
        try {
            val errorResult = httpGet("https://api.example.com/error")
            println("  This shouldn't print: $errorResult")
        } catch (e: Exception) {
            println("  Expected GET error: ${e.message}")
        }
        
        try {
            val emptyPostResult = httpPost("")
            println("  This shouldn't print: $emptyPostResult")
        } catch (e: Exception) {
            println("  Expected POST error: ${e.message}")
        }
        
        println()
        
        // Batch operations with generic wrapper
        println("2. Batch operations:")
        
        val urls = listOf(
            "https://api.example.com/users",
            "https://api.example.com/posts", 
            "https://api.example.com/comments",
            "https://api.example.com/error" // This will fail
        )
        
        val batchResults = urls.map { url ->
            async {
                try {
                    httpGet(url)
                } catch (e: Exception) {
                    "Failed: ${e.message}"
                }
            }
        }
        
        batchResults.awaitAll().forEachIndexed { index, result ->
            println("  Batch ${urls[index]}: $result")
        }
        
        println("Generic callback wrapper completed\n")
    }
}

/**
 * Cancellation-aware callback integration
 */
class CancellableCallbackIntegration {
    
    fun demonstrateCancellableIntegration() = runBlocking {
        println("=== Cancellable Callback Integration ===")
        
        // Legacy service with cancellation support
        class CancellableAsyncService {
            private val executor = Executors.newCachedThreadPool()
            
            fun longRunningOperation(
                duration: Long,
                onProgress: (Int) -> Unit,
                onComplete: (String) -> Unit,
                onError: (Exception) -> Unit
            ): Cancellable {
                
                val future = executor.submit {
                    try {
                        val steps = 10
                        repeat(steps) { step ->
                            if (Thread.currentThread().isInterrupted) {
                                throw InterruptedException("Operation cancelled")
                            }
                            
                            Thread.sleep(duration / steps)
                            val progress = ((step + 1) * 100) / steps
                            onProgress(progress)
                        }
                        
                        onComplete("Operation completed after ${duration}ms")
                    } catch (e: InterruptedException) {
                        onError(Exception("Operation was cancelled"))
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
                
                return object : Cancellable {
                    override fun cancel() {
                        future.cancel(true)
                    }
                }
            }
            
            fun shutdown() {
                executor.shutdown()
            }
        }
        
        interface Cancellable {
            fun cancel()
        }
        
        val service = CancellableAsyncService()
        
        // Cancellation-aware wrapper
        suspend fun longRunningTask(duration: Long): String = suspendCancellableCoroutine { continuation ->
            val cancellable = service.longRunningOperation(
                duration = duration,
                onProgress = { progress ->
                    println("    Progress: $progress%")
                },
                onComplete = { result ->
                    continuation.resume(result)
                },
                onError = { error ->
                    continuation.resumeWithException(error)
                }
            )
            
            // Register cancellation handler
            continuation.invokeOnCancellation {
                println("    Cancelling long-running operation...")
                cancellable.cancel()
            }
        }
        
        println("1. Cancellable long-running operation:")
        
        // Test normal completion
        try {
            val result = longRunningTask(800)
            println("  ✅ Operation completed: $result")
        } catch (e: Exception) {
            println("  ❌ Operation failed: ${e.message}")
        }
        
        println()
        
        // Test cancellation
        println("2. Testing cancellation:")
        
        val job = launch {
            try {
                val result = longRunningTask(2000) // Long operation
                println("  This shouldn't print: $result")
            } catch (e: CancellationException) {
                println("  ✅ Operation was cancelled: ${e.message}")
            } catch (e: Exception) {
                println("  ❌ Unexpected error: ${e.message}")
            }
        }
        
        delay(400) // Let it run partially
        println("  Cancelling job...")
        job.cancel()
        job.join()
        
        println()
        
        // Test timeout with cancellation
        println("3. Timeout with cancellation:")
        
        try {
            withTimeout(600) {
                longRunningTask(1500) // This will timeout
            }
        } catch (e: TimeoutCancellationException) {
            println("  ✅ Operation timed out and was cancelled")
        }
        
        delay(200) // Let cleanup complete
        service.shutdown()
        
        println("Cancellable integration completed\n")
    }
    
    fun demonstrateResourceCleanupIntegration() = runBlocking {
        println("=== Resource Cleanup Integration ===")
        
        // Service that requires resource cleanup
        class ResourcefulAsyncService {
            private val activeConnections = mutableSetOf<String>()
            
            fun connectAndFetch(
                connectionId: String,
                onConnected: () -> Unit,
                onData: (String) -> Unit,
                onError: (Exception) -> Unit
            ): ResourceHandle {
                
                println("    Opening connection: $connectionId")
                activeConnections.add(connectionId)
                
                val thread = Thread {
                    try {
                        Thread.sleep(100) // Connection setup
                        onConnected()
                        
                        Thread.sleep(300) // Data fetching
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Interrupted during fetch")
                        }
                        
                        onData("Data from $connectionId")
                    } catch (e: InterruptedException) {
                        onError(Exception("Connection interrupted: $connectionId"))
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
                thread.start()
                
                return object : ResourceHandle {
                    override fun cleanup() {
                        println("    Cleaning up connection: $connectionId")
                        thread.interrupt()
                        activeConnections.remove(connectionId)
                    }
                }
            }
            
            fun getActiveConnections(): Set<String> = activeConnections.toSet()
        }
        
        interface ResourceHandle {
            fun cleanup()
        }
        
        val resourceService = ResourcefulAsyncService()
        
        // Resource-aware wrapper
        suspend fun fetchWithConnection(connectionId: String): String = suspendCancellableCoroutine { continuation ->
            var resourceHandle: ResourceHandle? = null
            
            resourceHandle = resourceService.connectAndFetch(
                connectionId = connectionId,
                onConnected = {
                    println("    Connected: $connectionId")
                },
                onData = { data ->
                    continuation.resume(data)
                },
                onError = { error ->
                    continuation.resumeWithException(error)
                }
            )
            
            // Ensure cleanup on cancellation
            continuation.invokeOnCancellation {
                println("    Cleaning up due to cancellation: $connectionId")
                resourceHandle?.cleanup()
            }
        }
        
        println("1. Resource cleanup on normal completion:")
        
        try {
            val data = fetchWithConnection("conn-1")
            println("  ✅ Fetched: $data")
            println("  Active connections: ${resourceService.getActiveConnections()}")
        } catch (e: Exception) {
            println("  ❌ Error: ${e.message}")
        }
        
        println()
        
        println("2. Resource cleanup on cancellation:")
        
        val connectionJobs = (1..3).map { id ->
            launch {
                try {
                    val data = fetchWithConnection("conn-cancel-$id")
                    println("  ✅ This shouldn't print: $data")
                } catch (e: CancellationException) {
                    println("  ✅ Connection conn-cancel-$id was cancelled")
                }
            }
        }
        
        delay(150) // Let connections start
        println("  Active connections before cancellation: ${resourceService.getActiveConnections()}")
        
        // Cancel all jobs
        connectionJobs.forEach { it.cancel() }
        connectionJobs.forEach { it.join() }
        
        delay(100) // Let cleanup complete
        println("  Active connections after cancellation: ${resourceService.getActiveConnections()}")
        
        println()
        
        println("3. Exception handling with cleanup:")
        
        // Simulate connection that will fail
        val failingJob = launch {
            try {
                val data = fetchWithConnection("conn-fail") 
                println("  This shouldn't print: $data")
            } catch (e: Exception) {
                println("  ✅ Handled connection failure: ${e.message}")
            }
        }
        
        // Manually trigger failure by interrupting after connection
        delay(200)
        failingJob.cancel()
        failingJob.join()
        
        delay(100)
        println("  Final active connections: ${resourceService.getActiveConnections()}")
        
        println("Resource cleanup integration completed\n")
    }
}

/**
 * CompletableFuture integration patterns
 */
class FutureIntegration {
    
    fun demonstrateFutureIntegration() = runBlocking {
        println("=== CompletableFuture Integration ===")
        
        // Java-style service returning CompletableFuture
        class JavaAsyncService {
            private val executor = ForkJoinPool.commonPool()
            
            fun fetchDataAsync(id: String): CompletableFuture<String> {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(300)
                    when {
                        id.startsWith("error") -> throw RuntimeException("Failed to fetch $id")
                        id.isEmpty() -> throw IllegalArgumentException("ID cannot be empty")
                        else -> "Data for $id"
                    }
                }, executor)
            }
            
            fun processDataAsync(data: String): CompletableFuture<String> {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(200)
                    "Processed: ${data.uppercase()}"
                }, executor)
            }
            
            fun combineDataAsync(data1: String, data2: String): CompletableFuture<String> {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(150)
                    "Combined: $data1 + $data2"
                }, executor)
            }
        }
        
        val javaService = JavaAsyncService()
        
        println("1. Basic Future to coroutine conversion:")
        
        // Direct future integration
        try {
            val data1 = javaService.fetchDataAsync("item1").await()
            println("  ✅ Future result: $data1")
            
            val data2 = javaService.fetchDataAsync("item2").await()
            println("  ✅ Future result: $data2")
            
            val processed = javaService.processDataAsync(data1).await()
            println("  ✅ Processed result: $processed")
        } catch (e: Exception) {
            println("  ❌ Future error: ${e.message}")
        }
        
        println()
        
        // Test error handling
        println("2. Future error handling:")
        
        try {
            val errorData = javaService.fetchDataAsync("error-test").await()
            println("  This shouldn't print: $errorData")
        } catch (e: Exception) {
            println("  ✅ Expected future error: ${e.message}")
        }
        
        println()
        
        // Concurrent future operations
        println("3. Concurrent future operations:")
        
        val concurrentResults = (1..4).map { id ->
            async {
                try {
                    val data = javaService.fetchDataAsync("concurrent-$id").await()
                    val processed = javaService.processDataAsync(data).await()
                    processed
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
        }
        
        concurrentResults.awaitAll().forEachIndexed { index, result ->
            println("  Concurrent future $index: $result")
        }
        
        println()
        
        // Future composition patterns
        println("4. Future composition:")
        
        val composedResult = async {
            val data1Future = javaService.fetchDataAsync("compose1")
            val data2Future = javaService.fetchDataAsync("compose2")
            
            // Wait for both futures
            val data1 = data1Future.await()
            val data2 = data2Future.await()
            
            // Combine results
            javaService.combineDataAsync(data1, data2).await()
        }
        
        try {
            val result = composedResult.await()
            println("  ✅ Composed result: $result")
        } catch (e: Exception) {
            println("  ❌ Composition error: ${e.message}")
        }
        
        println("Future integration completed\n")
    }
    
    fun demonstrateFutureCancellation() = runBlocking {
        println("=== Future Cancellation Integration ===")
        
        // Service with cancellable futures
        class CancellableFutureService {
            fun longRunningFuture(durationMs: Long): CompletableFuture<String> {
                val future = CompletableFuture<String>()
                
                val thread = Thread {
                    try {
                        val steps = 10
                        repeat(steps) { step ->
                            if (Thread.currentThread().isInterrupted) {
                                future.cancel(true)
                                return@Thread
                            }
                            
                            Thread.sleep(durationMs / steps)
                            println("    Future progress: ${((step + 1) * 100) / steps}%")
                        }
                        
                        future.complete("Future completed after ${durationMs}ms")
                    } catch (e: InterruptedException) {
                        future.cancel(true)
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
                
                thread.start()
                return future
            }
        }
        
        val futureService = CancellableFutureService()
        
        println("1. Future cancellation:")
        
        val cancellableJob = launch {
            try {
                val result = futureService.longRunningFuture(2000).await()
                println("  This shouldn't print: $result")
            } catch (e: CancellationException) {
                println("  ✅ Future operation was cancelled")
            }
        }
        
        delay(600) // Let it run partially
        println("  Cancelling coroutine...")
        cancellableJob.cancel()
        cancellableJob.join()
        
        println()
        
        println("2. Future timeout:")
        
        try {
            withTimeout(800) {
                futureService.longRunningFuture(1500).await()
            }
        } catch (e: TimeoutCancellationException) {
            println("  ✅ Future operation timed out")
        }
        
        delay(200) // Let any cleanup complete
        
        println("Future cancellation completed\n")
    }
}

/**
 * Streaming callback integration with callbackFlow
 */
class StreamingCallbackIntegration {
    
    fun demonstrateCallbackFlow() = runBlocking {
        println("=== Streaming Callback Integration ===")
        
        // Event-based streaming service
        interface EventListener {
            fun onEvent(event: Event)
            fun onError(error: Throwable)
            fun onComplete()
        }
        
        data class Event(val id: String, val type: String, val data: String, val timestamp: Long = System.currentTimeMillis())
        
        class EventStreamService {
            private val listeners = mutableSetOf<EventListener>()
            private var isRunning = false
            private var eventThread: Thread? = null
            
            fun subscribe(listener: EventListener) {
                listeners.add(listener)
                println("    Event listener subscribed (${listeners.size} total)")
            }
            
            fun unsubscribe(listener: EventListener) {
                listeners.remove(listener)
                println("    Event listener unsubscribed (${listeners.size} remaining)")
            }
            
            fun start() {
                if (isRunning) return
                
                isRunning = true
                eventThread = Thread {
                    try {
                        var eventId = 0
                        while (isRunning && !Thread.currentThread().isInterrupted) {
                            val event = Event(
                                id = "event-${eventId++}",
                                type = listOf("USER_ACTION", "SYSTEM_EVENT", "DATA_UPDATE").random(),
                                data = "Event data ${Random.nextInt(1000)}"
                            )
                            
                            listeners.forEach { listener ->
                                try {
                                    listener.onEvent(event)
                                } catch (e: Exception) {
                                    listener.onError(e)
                                }
                            }
                            
                            Thread.sleep(Random.nextLong(100, 300))
                        }
                        
                        listeners.forEach { it.onComplete() }
                    } catch (e: InterruptedException) {
                        listeners.forEach { it.onError(e) }
                    }
                }
                eventThread?.start()
            }
            
            fun stop() {
                isRunning = false
                eventThread?.interrupt()
                listeners.clear()
            }
        }
        
        val eventService = EventStreamService()
        
        // Convert streaming callbacks to Flow
        fun eventFlow(): Flow<Event> = callbackFlow {
            val listener = object : EventListener {
                override fun onEvent(event: Event) {
                    trySend(event).isSuccess // Non-blocking send
                }
                
                override fun onError(error: Throwable) {
                    close(error)
                }
                
                override fun onComplete() {
                    close()
                }
            }
            
            eventService.subscribe(listener)
            
            // Cleanup when flow is cancelled
            awaitClose {
                eventService.unsubscribe(listener)
                println("    Event flow cleanup completed")
            }
        }
        
        println("1. Streaming events with callbackFlow:")
        
        eventService.start()
        
        val flowJob = launch {
            eventFlow()
                .take(8) // Take first 8 events
                .collect { event ->
                    println("  📡 Received event: ${event.type} - ${event.data}")
                }
        }
        
        flowJob.join()
        
        println()
        
        // Multiple subscribers
        println("2. Multiple event flow subscribers:")
        
        val userActionFlow = eventFlow()
            .filter { it.type == "USER_ACTION" }
            .take(3)
        
        val systemEventFlow = eventFlow()
            .filter { it.type == "SYSTEM_EVENT" }
            .take(3)
        
        val dataUpdateFlow = eventFlow()
            .filter { it.type == "DATA_UPDATE" }
            .take(3)
        
        val multipleFlowJobs = listOf(
            launch {
                userActionFlow.collect { event ->
                    println("  👤 User Action: ${event.data}")
                }
            },
            launch {
                systemEventFlow.collect { event ->
                    println("  ⚙️ System Event: ${event.data}")
                }
            },
            launch {
                dataUpdateFlow.collect { event ->
                    println("  📊 Data Update: ${event.data}")
                }
            }
        )
        
        multipleFlowJobs.forEach { it.join() }
        
        delay(200)
        eventService.stop()
        
        println()
        
        // Error handling in streaming callbacks
        println("3. Error handling in streaming callbacks:")
        
        class ErrorProneEventService {
            fun createErrorProneFlow(): Flow<String> = callbackFlow {
                var counter = 0
                val timer = java.util.Timer()
                
                val task = object : java.util.TimerTask() {
                    override fun run() {
                        counter++
                        
                        when {
                            counter <= 3 -> {
                                trySend("Event $counter")
                            }
                            counter == 4 -> {
                                close(RuntimeException("Simulated stream error"))
                            }
                            else -> {
                                close()
                            }
                        }
                    }
                }
                
                timer.scheduleAtFixedRate(task, 0, 200)
                
                awaitClose {
                    timer.cancel()
                    println("    Error-prone flow cleanup")
                }
            }
        }
        
        val errorProneService = ErrorProneEventService()
        
        try {
            errorProneService.createErrorProneFlow()
                .catch { e ->
                    println("  ❌ Flow error caught: ${e.message}")
                    emit("Error recovery event")
                }
                .collect { event ->
                    println("  📨 Error-prone event: $event")
                }
        } catch (e: Exception) {
            println("  ❌ Uncaught flow error: ${e.message}")
        }
        
        delay(200)
        
        println("Streaming callback integration completed\n")
    }
}

/**
 * Third-party library integration examples
 */
class ThirdPartyIntegration {
    
    fun demonstrateLibraryIntegration() = runBlocking {
        println("=== Third-Party Library Integration ===")
        
        println("1. HTTP client integration pattern:")
        
        // Simulate OkHttp-style callback API
        interface HttpCallback {
            fun onResponse(response: HttpResponse)
            fun onFailure(error: Exception)
        }
        
        data class HttpResponse(val code: Int, val body: String)
        data class HttpRequest(val url: String, val method: String = "GET")
        
        class MockHttpClient {
            fun enqueue(request: HttpRequest, callback: HttpCallback) {
                Thread {
                    Thread.sleep(200)
                    
                    when {
                        request.url.contains("error") -> {
                            callback.onFailure(Exception("HTTP error for ${request.url}"))
                        }
                        request.url.contains("404") -> {
                            callback.onResponse(HttpResponse(404, "Not Found"))
                        }
                        else -> {
                            callback.onResponse(HttpResponse(200, "Response from ${request.url}"))
                        }
                    }
                }.start()
            }
        }
        
        val httpClient = MockHttpClient()
        
        // Suspend function wrapper
        suspend fun httpCall(request: HttpRequest): HttpResponse = suspendCancellableCoroutine { continuation ->
            httpClient.enqueue(request, object : HttpCallback {
                override fun onResponse(response: HttpResponse) {
                    continuation.resume(response)
                }
                
                override fun onFailure(error: Exception) {
                    continuation.resumeWithException(error)
                }
            })
        }
        
        // Test HTTP integration
        try {
            val response1 = httpCall(HttpRequest("https://api.example.com/users"))
            println("  ✅ HTTP Response: ${response1.code} - ${response1.body}")
            
            val response2 = httpCall(HttpRequest("https://api.example.com/404"))
            println("  ✅ HTTP Response: ${response2.code} - ${response2.body}")
        } catch (e: Exception) {
            println("  ❌ HTTP Error: ${e.message}")
        }
        
        // Test error scenario
        try {
            val errorResponse = httpCall(HttpRequest("https://api.example.com/error"))
            println("  This shouldn't print: $errorResponse")
        } catch (e: Exception) {
            println("  ✅ Expected HTTP error: ${e.message}")
        }
        
        println()
        
        println("2. Database integration pattern:")
        
        // Simulate database callback API  
        interface DatabaseCallback<T> {
            fun onSuccess(result: T)
            fun onError(error: Exception)
        }
        
        data class User(val id: String, val name: String, val email: String)
        
        class MockDatabase {
            private val users = mapOf(
                "1" to User("1", "Alice", "alice@example.com"),
                "2" to User("2", "Bob", "bob@example.com"),
                "3" to User("3", "Charlie", "charlie@example.com")
            )
            
            fun findUserAsync(id: String, callback: DatabaseCallback<User?>) {
                Thread {
                    Thread.sleep(150)
                    
                    when {
                        id == "error" -> callback.onError(Exception("Database connection error"))
                        users.containsKey(id) -> callback.onSuccess(users[id])
                        else -> callback.onSuccess(null)
                    }
                }.start()
            }
            
            fun saveUserAsync(user: User, callback: DatabaseCallback<Boolean>) {
                Thread {
                    Thread.sleep(200)
                    
                    if (user.name.isEmpty()) {
                        callback.onError(Exception("User name cannot be empty"))
                    } else {
                        callback.onSuccess(true)
                    }
                }.start()
            }
        }
        
        val database = MockDatabase()
        
        // Database suspend wrappers
        suspend fun findUser(id: String): User? = suspendCoroutine { continuation ->
            database.findUserAsync(id, object : DatabaseCallback<User?> {
                override fun onSuccess(result: User?) {
                    continuation.resume(result)
                }
                
                override fun onError(error: Exception) {
                    continuation.resumeWithException(error)
                }
            })
        }
        
        suspend fun saveUser(user: User): Boolean = suspendCoroutine { continuation ->
            database.saveUserAsync(user, object : DatabaseCallback<Boolean> {
                override fun onSuccess(result: Boolean) {
                    continuation.resume(result)
                }
                
                override fun onError(error: Exception) {
                    continuation.resumeWithException(error)
                }
            })
        }
        
        // Test database integration
        try {
            val user1 = findUser("1")
            println("  ✅ Found user: $user1")
            
            val user2 = findUser("999") // Non-existent
            println("  ✅ User not found: $user2")
            
            val saved = saveUser(User("4", "Dave", "dave@example.com"))
            println("  ✅ User saved: $saved")
        } catch (e: Exception) {
            println("  ❌ Database error: ${e.message}")
        }
        
        println()
        
        println("3. Message queue integration pattern:")
        
        // Simulate message queue callback API
        interface MessageHandler {
            fun onMessage(message: String)
            fun onError(error: Exception)
        }
        
        class MockMessageQueue {
            private var handler: MessageHandler? = null
            private var isConnected = false
            private var messageThread: Thread? = null
            
            fun connect(handler: MessageHandler) {
                this.handler = handler
                isConnected = true
                
                messageThread = Thread {
                    try {
                        var messageId = 0
                        while (isConnected && !Thread.currentThread().isInterrupted) {
                            Thread.sleep(300)
                            handler.onMessage("Message ${messageId++}: ${Random.nextInt(1000)}")
                        }
                    } catch (e: InterruptedException) {
                        handler.onError(e)
                    }
                }
                messageThread?.start()
            }
            
            fun disconnect() {
                isConnected = false
                messageThread?.interrupt()
                handler = null
            }
        }
        
        val messageQueue = MockMessageQueue()
        
        // Message queue flow
        fun messageFlow(): Flow<String> = callbackFlow {
            val handler = object : MessageHandler {
                override fun onMessage(message: String) {
                    trySend(message)
                }
                
                override fun onError(error: Exception) {
                    close(error)
                }
            }
            
            messageQueue.connect(handler)
            
            awaitClose {
                messageQueue.disconnect()
                println("    Message queue disconnected")
            }
        }
        
        // Test message queue integration
        val messageJob = launch {
            messageFlow()
                .take(5)
                .collect { message ->
                    println("  📨 Received message: $message")
                }
        }
        
        messageJob.join()
        delay(100)
        
        println("Third-party library integration completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic callback integration
        BasicCallbackIntegration().demonstrateBasicCallbackWrapping()
        BasicCallbackIntegration().demonstrateGenericCallbackWrapper()
        
        // Cancellation-aware integration
        CancellableCallbackIntegration().demonstrateCancellableIntegration()
        CancellableCallbackIntegration().demonstrateResourceCleanupIntegration()
        
        // Future integration
        FutureIntegration().demonstrateFutureIntegration()
        FutureIntegration().demonstrateFutureCancellation()
        
        // Streaming callback integration
        StreamingCallbackIntegration().demonstrateCallbackFlow()
        
        // Third-party library integration
        ThirdPartyIntegration().demonstrateLibraryIntegration()
        
        println("=== Callback Integration Summary ===")
        println("✅ Basic Callback Integration:")
        println("   - suspendCoroutine for simple callback conversion")
        println("   - Generic wrapper functions for reusable patterns")
        println("   - Proper error propagation from callbacks")
        println("   - Support for different callback signatures")
        println()
        println("✅ Cancellation-Aware Integration:")
        println("   - suspendCancellableCoroutine for cancellation support")
        println("   - invokeOnCancellation for cleanup registration")
        println("   - Resource management during cancellation")
        println("   - Timeout handling with proper cleanup")
        println()
        println("✅ Future Integration:")
        println("   - CompletableFuture.await() for future conversion")
        println("   - Concurrent future operations with async")
        println("   - Future composition patterns")
        println("   - Cancellation propagation to futures")
        println()
        println("✅ Streaming Integration:")
        println("   - callbackFlow for streaming callback APIs")
        println("   - awaitClose for cleanup on flow cancellation")
        println("   - Error handling in streaming flows")
        println("   - Multiple subscriber patterns")
        println()
        println("✅ Third-Party Integration:")
        println("   - HTTP client wrapper patterns")
        println("   - Database callback integration") 
        println("   - Message queue streaming integration")
        println("   - Resource lifecycle management")
        println()
        println("✅ Best Practices:")
        println("   - Always handle cancellation in long-running operations")
        println("   - Implement proper resource cleanup")
        println("   - Use appropriate integration pattern for the callback type")
        println("   - Test integration with cancellation and timeout scenarios")
        println("   - Consider performance impact of callback wrapping")
    }
}