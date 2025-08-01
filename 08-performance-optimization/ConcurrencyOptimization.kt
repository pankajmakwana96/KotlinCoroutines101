/**
 * # Concurrency Optimization and Scaling Patterns
 * 
 * ## Problem Description
 * Optimizing concurrency in coroutine applications requires understanding how to
 * effectively scale concurrent operations, manage resource contention, implement
 * efficient synchronization patterns, and balance throughput with latency requirements.
 * Common challenges include thread contention, lock overhead, inefficient work
 * distribution, poor cache locality, and suboptimal parallelization strategies that
 * can severely limit application scalability and performance.
 * 
 * ## Solution Approach
 * Concurrency optimization strategies include:
 * - Implementing lock-free and wait-free data structures
 * - Optimizing work distribution and load balancing algorithms
 * - Using actor patterns and message passing for synchronization
 * - Applying parallel decomposition and fork-join strategies
 * - Implementing efficient batching and streaming patterns
 * 
 * ## Key Learning Points
 * - Lock-free programming with atomic operations and CAS
 * - Work stealing algorithms and parallel task decomposition
 * - Actor model implementation for concurrent state management
 * - Channel-based communication patterns for scalable architectures
 * - Batching strategies for reducing coordination overhead
 * 
 * ## Performance Considerations
 * - Lock contention can reduce performance by 10-100x
 * - Cache line sharing causes false sharing penalties (~50-100ns)
 * - Work stealing efficiency depends on task granularity
 * - Message passing overhead: ~10-100ns per message
 * - Batch processing can improve throughput by 5-50x
 * 
 * ## Common Pitfalls
 * - Over-synchronization leading to sequential bottlenecks
 * - Fine-grained locking causing excessive overhead
 * - Poor work distribution creating load imbalance
 * - Ignoring NUMA topology and cache effects
 * - Inappropriate granularity in parallel decomposition
 * 
 * ## Real-World Applications
 * - High-frequency trading systems with microsecond latency
 * - Real-time data processing and stream analytics
 * - Parallel computation frameworks and scientific computing
 * - Web server request processing and load balancing
 * - Game engines and real-time simulation systems
 * 
 * ## Related Concepts
 * - Lock-free data structures and algorithms
 * - Work stealing and load balancing techniques
 * - Memory consistency models and cache coherence
 * - Parallel algorithms and divide-and-conquer strategies
 */

package performance.optimization.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlin.system.*
import kotlin.time.*
import kotlin.random.Random
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

/**
 * Lock-free and wait-free concurrency patterns
 * 
 * Concurrency Optimization Hierarchy:
 * 
 * Synchronization Strategies:
 * ├── Wait-Free ─────── No blocking, guaranteed progress for all threads
 * ├── Lock-Free ─────── No blocking, system-wide progress guaranteed
 * ├── Obstruction-Free ── Progress when running alone
 * ├── Non-Blocking ───── Uses atomic operations, no locks
 * └── Blocking ──────── Traditional locks and synchronization
 * 
 * Performance Ranking (best to worst):
 * Wait-Free > Lock-Free > Non-Blocking > Fine-Grained Locking > Coarse-Grained Locking
 */
class LockFreeOptimization {
    
    // Lock-free counter implementation
    class LockFreeCounter {
        private val value = AtomicLong(0)
        
        fun increment(): Long {
            return value.incrementAndGet()
        }
        
        fun add(delta: Long): Long {
            return value.addAndGet(delta)
        }
        
        fun compareAndSet(expected: Long, update: Long): Boolean {
            return value.compareAndSet(expected, update)
        }
        
        fun get(): Long = value.get()
    }
    
    // Lock-free queue implementation using CAS
    class LockFreeQueue<T> {
        private data class Node<T>(
            val data: T?,
            @Volatile var next: Node<T>? = null
        )
        
        private val head = AtomicReference<Node<T>>(Node(null))
        private val tail = AtomicReference<Node<T>>(head.get())
        
        fun enqueue(item: T) {
            val newNode = Node(item)
            
            while (true) {
                val currentTail = tail.get()
                val tailNext = currentTail.next
                
                if (currentTail == tail.get()) {
                    if (tailNext == null) {
                        // Try to link new node at end of list
                        if (compareAndSetNext(currentTail, null, newNode)) {
                            // Try to swing tail to new node
                            tail.compareAndSet(currentTail, newNode)
                            break
                        }
                    } else {
                        // Try to swing tail to next node
                        tail.compareAndSet(currentTail, tailNext)
                    }
                }
            }
        }
        
        fun dequeue(): T? {
            while (true) {
                val currentHead = head.get()
                val currentTail = tail.get()
                val headNext = currentHead.next
                
                if (currentHead == head.get()) {
                    if (currentHead == currentTail) {
                        if (headNext == null) {
                            // Queue is empty
                            return null
                        }
                        // Help advance tail
                        tail.compareAndSet(currentTail, headNext)
                    } else {
                        // Read data before CAS, otherwise another dequeue might free the next node
                        val data = headNext?.data
                        
                        // Try to swing head to next node
                        if (head.compareAndSet(currentHead, headNext)) {
                            return data
                        }
                    }
                }
            }
        }
        
        private fun compareAndSetNext(node: Node<T>, expected: Node<T>?, update: Node<T>?): Boolean {
            // Simplified CAS for next pointer
            synchronized(node) {
                if (node.next == expected) {
                    node.next = update
                    return true
                }
                return false
            }
        }
    }
    
    // Lock-free stack implementation
    class LockFreeStack<T> {
        private data class Node<T>(val data: T, val next: Node<T>?)
        
        private val top = AtomicReference<Node<T>?>(null)
        
        fun push(item: T) {
            val newNode = Node(item, null)
            while (true) {
                val currentTop = top.get()
                newNode.next = currentTop
                if (top.compareAndSet(currentTop, newNode)) {
                    return
                }
            }
        }
        
        fun pop(): T? {
            while (true) {
                val currentTop = top.get() ?: return null
                val newTop = currentTop.next
                if (top.compareAndSet(currentTop, newTop)) {
                    return currentTop.data
                }
            }
        }
        
        fun peek(): T? = top.get()?.data
        
        fun isEmpty(): Boolean = top.get() == null
    }
    
    suspend fun demonstrateLockFreePerformance() {
        println("=== Lock-Free vs Synchronized Performance ===")
        
        val iterations = 100000
        val concurrency = 8
        
        // Test 1: Lock-free counter vs synchronized counter
        println("\n--- Counter Performance Comparison ---")
        
        // Lock-free counter
        val lockFreeCounter = LockFreeCounter()
        val lockFreeTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            lockFreeCounter.increment()
                        }
                    }
                }
            }
        }
        
        // Synchronized counter
        var synchronizedCounter = 0L
        val mutex = Mutex()
        val synchronizedTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            mutex.withLock {
                                synchronizedCounter++
                            }
                        }
                    }
                }
            }
        }
        
        println("Lock-free counter: ${lockFreeTime.inWholeMilliseconds}ms, final value: ${lockFreeCounter.get()}")
        println("Synchronized counter: ${synchronizedTime.inWholeMilliseconds}ms, final value: $synchronizedCounter")
        println("Lock-free speedup: ${String.format("%.1f", synchronizedTime.inWholeNanoseconds.toDouble() / lockFreeTime.inWholeNanoseconds)}x")
        
        // Test 2: Lock-free queue vs channel
        println("\n--- Queue Performance Comparison ---")
        
        val lockFreeQueue = LockFreeQueue<Int>()
        val queueTime = measureTime {
            coroutineScope {
                // Producers
                repeat(concurrency / 2) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) { i ->
                            lockFreeQueue.enqueue(i)
                        }
                    }
                }
                
                // Consumers
                repeat(concurrency / 2) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            while (lockFreeQueue.dequeue() == null) {
                                yield()
                            }
                        }
                    }
                }
            }
        }
        
        val channel = Channel<Int>(1000)
        val channelTime = measureTime {
            coroutineScope {
                // Producers
                repeat(concurrency / 2) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) { i ->
                            channel.send(i)
                        }
                    }
                }
                
                // Consumers
                repeat(concurrency / 2) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            channel.receive()
                        }
                    }
                }
            }
        }
        
        channel.close()
        
        println("Lock-free queue: ${queueTime.inWholeMilliseconds}ms")
        println("Channel: ${channelTime.inWholeMilliseconds}ms")
        println("Queue speedup: ${String.format("%.1f", channelTime.inWholeNanoseconds.toDouble() / queueTime.inWholeNanoseconds)}x")
        
        println("✅ Lock-free performance demo completed")
    }
    
    suspend fun demonstrateContentionReduction() {
        println("=== Contention Reduction Strategies ===")
        
        val iterations = 50000
        val concurrency = 8
        
        // Strategy 1: Coarse-grained locking (high contention)
        var sharedResource = 0
        val coarseMutex = Mutex()
        
        val coarseTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            coarseMutex.withLock {
                                sharedResource++
                                // Simulate some work
                                var temp = 0
                                repeat(100) { temp += it }
                            }
                        }
                    }
                }
            }
        }
        
        // Strategy 2: Per-thread counters with final aggregation (no contention)
        val perThreadCounters = Array(concurrency) { AtomicLong(0) }
        
        val perThreadTime = measureTime {
            coroutineScope {
                repeat(concurrency) { threadIndex ->
                    launch(Dispatchers.Default) {
                        repeat(iterations / concurrency) {
                            perThreadCounters[threadIndex].incrementAndGet()
                            // Simulate same work
                            var temp = 0
                            repeat(100) { temp += it }
                        }
                    }
                }
            }
        }
        
        val totalPerThread = perThreadCounters.sumOf { it.get() }
        
        // Strategy 3: Batched updates (reduced contention)
        var batchedResource = 0L
        val batchMutex = Mutex()
        val batchSize = 100
        
        val batchTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch(Dispatchers.Default) {
                        var localBatch = 0
                        repeat(iterations / concurrency) {
                            localBatch++
                            // Simulate work
                            var temp = 0
                            repeat(100) { temp += it }
                            
                            if (localBatch >= batchSize) {
                                batchMutex.withLock {
                                    batchedResource += localBatch
                                }
                                localBatch = 0
                            }
                        }
                        
                        // Handle remaining items
                        if (localBatch > 0) {
                            batchMutex.withLock {
                                batchedResource += localBatch
                            }
                        }
                    }
                }
            }
        }
        
        println("Coarse-grained locking: ${coarseTime.inWholeMilliseconds}ms, result: $sharedResource")
        println("Per-thread counters: ${perThreadTime.inWholeMilliseconds}ms, result: $totalPerThread")
        println("Batched updates: ${batchTime.inWholeMilliseconds}ms, result: $batchedResource")
        
        println("Per-thread speedup: ${String.format("%.1f", coarseTime.inWholeNanoseconds.toDouble() / perThreadTime.inWholeNanoseconds)}x")
        println("Batching speedup: ${String.format("%.1f", coarseTime.inWholeNanoseconds.toDouble() / batchTime.inWholeNanoseconds)}x")
        
        println("✅ Contention reduction demo completed")
    }
}

/**
 * Work stealing and parallel decomposition patterns
 */
class WorkStealingOptimization {
    
    // Work-stealing deque implementation
    class WorkStealingDeque<T> {
        private val deque = ArrayDeque<T>()
        private val lock = Mutex()
        
        suspend fun pushBottom(item: T) {
            lock.withLock {
                deque.addLast(item)
            }
        }
        
        suspend fun popBottom(): T? {
            lock.withLock {
                return if (deque.isNotEmpty()) deque.removeLast() else null
            }
        }
        
        suspend fun steal(): T? {
            lock.withLock {
                return if (deque.isNotEmpty()) deque.removeFirst() else null
            }
        }
        
        suspend fun size(): Int {
            lock.withLock {
                return deque.size
            }
        }
    }
    
    // Parallel fork-join style computation
    class ParallelComputation<T, R>(
        private val threshold: Int = 1000,
        private val compute: suspend (List<T>) -> R,
        private val combine: suspend (List<R>) -> R
    ) {
        
        suspend fun computeParallel(data: List<T>): R {
            return if (data.size <= threshold) {
                compute(data)
            } else {
                val mid = data.size / 2
                val left = data.subList(0, mid)
                val right = data.subList(mid, data.size)
                
                coroutineScope {
                    val leftResult = async { computeParallel(left) }
                    val rightResult = async { computeParallel(right) }
                    
                    combine(listOf(leftResult.await(), rightResult.await()))
                }
            }
        }
    }
    
    // Work-stealing thread pool simulation
    class WorkStealingPool(private val workerCount: Int = 4) {
        private val workers = Array(workerCount) { WorkStealingDeque<suspend () -> Unit>() }
        private val workerIndex = AtomicInteger(0)
        
        suspend fun submit(task: suspend () -> Unit) {
            val index = workerIndex.getAndIncrement() % workerCount
            workers[index].pushBottom(task)
        }
        
        suspend fun executeAll() {
            coroutineScope {
                repeat(workerCount) { workerId ->
                    launch(Dispatchers.Default) {
                        workerLoop(workerId)
                    }
                }
            }
        }
        
        private suspend fun workerLoop(workerId: Int) {
            val myDeque = workers[workerId]
            
            while (true) {
                // Try to get work from own deque first
                var task = myDeque.popBottom()
                
                if (task == null) {
                    // Try to steal work from other workers
                    for (i in 0 until workerCount) {
                        if (i != workerId) {
                            task = workers[i].steal()
                            if (task != null) break
                        }
                    }
                }
                
                if (task != null) {
                    task()
                } else {
                    // No work available, check if all workers are idle
                    val totalWork = workers.sumOf { it.size() }
                    if (totalWork == 0) break
                    
                    yield() // Give other coroutines a chance
                }
            }
        }
    }
    
    suspend fun demonstrateParallelDecomposition() {
        println("=== Parallel Decomposition Performance ===")
        
        val dataSize = 1000000
        val data = (1..dataSize).toList()
        
        // Sequential computation
        val sequentialTime = measureTime {
            data.sum()
        }
        
        // Parallel computation using fork-join
        val parallelComputation = ParallelComputation<Int, Long>(
            threshold = 10000,
            compute = { list -> list.sumOf { it.toLong() } },
            combine = { results -> results.sum() }
        )
        
        val parallelTime = measureTime {
            parallelComputation.computeParallel(data)
        }
        
        // Coroutine-based parallel computation
        val coroutineTime = measureTime {
            val chunkSize = dataSize / 8
            val results = data.chunked(chunkSize).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.sumOf { it.toLong() }
                }
            }.awaitAll()
            
            results.sum()
        }
        
        println("Sequential: ${sequentialTime.inWholeMilliseconds}ms")
        println("Fork-join parallel: ${parallelTime.inWholeMilliseconds}ms")
        println("Coroutine parallel: ${coroutineTime.inWholeMilliseconds}ms")
        
        println("Fork-join speedup: ${String.format("%.1f", sequentialTime.inWholeNanoseconds.toDouble() / parallelTime.inWholeNanoseconds)}x")
        println("Coroutine speedup: ${String.format("%.1f", sequentialTime.inWholeNanoseconds.toDouble() / coroutineTime.inWholeNanoseconds)}x")
        
        println("✅ Parallel decomposition demo completed")
    }
    
    suspend fun demonstrateWorkStealing() {
        println("=== Work Stealing Performance ===")
        
        val taskCount = 10000
        val workVariance = 100 // Some tasks take longer than others
        
        // Create tasks with varying work loads
        val tasks = (1..taskCount).map { taskId ->
            val workAmount = Random.nextInt(1, workVariance)
            suspend {
                // Simulate work
                var result = 0
                repeat(workAmount * 100) { i ->
                    result += i * taskId
                }
            }
        }
        
        // Work-stealing execution
        val workStealingPool = WorkStealingPool(4)
        val workStealingTime = measureTime {
            tasks.forEach { task ->
                workStealingPool.submit(task)
            }
            workStealingPool.executeAll()
        }
        
        // Simple parallel execution without work stealing
        val simpleParallelTime = measureTime {
            val chunkSize = taskCount / 4
            tasks.chunked(chunkSize).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.forEach { task ->
                        task()
                    }
                }
            }.awaitAll()
        }
        
        println("Work stealing: ${workStealingTime.inWholeMilliseconds}ms")
        println("Simple parallel: ${simpleParallelTime.inWholeMilliseconds}ms")
        
        val improvement = if (simpleParallelTime > Duration.ZERO) {
            simpleParallelTime.inWholeNanoseconds.toDouble() / workStealingTime.inWholeNanoseconds
        } else 1.0
        
        println("Work stealing improvement: ${String.format("%.1f", improvement)}x")
        
        println("✅ Work stealing demo completed")
    }
    
    suspend fun demonstrateLoadBalancing() {
        println("=== Dynamic Load Balancing ===")
        
        // Simulate uneven workload distribution
        data class Task(val id: Int, val complexity: Int)
        
        val tasks = (1..1000).map { id ->
            val complexity = when {
                id % 10 == 0 -> Random.nextInt(500, 1000) // Heavy tasks
                id % 5 == 0 -> Random.nextInt(100, 300)   // Medium tasks
                else -> Random.nextInt(10, 100)           // Light tasks
            }
            Task(id, complexity)
        }
        
        // Static load balancing (equal task distribution)
        val staticTime = measureTime {
            val workers = 4
            val tasksPerWorker = tasks.size / workers
            
            coroutineScope {
                repeat(workers) { workerId ->
                    launch(Dispatchers.Default) {
                        val startIndex = workerId * tasksPerWorker
                        val endIndex = if (workerId == workers - 1) tasks.size else (workerId + 1) * tasksPerWorker
                        
                        for (i in startIndex until endIndex) {
                            val task = tasks[i]
                            // Simulate work proportional to complexity
                            repeat(task.complexity) { j ->
                                kotlin.math.sqrt((j + 1).toDouble())
                            }
                        }
                    }
                }
            }
        }
        
        // Dynamic load balancing using channels
        val dynamicTime = measureTime {
            val taskChannel = Channel<Task>(100)
            val workers = 4
            
            coroutineScope {
                // Producer
                launch {
                    tasks.forEach { task ->
                        taskChannel.send(task)
                    }
                    taskChannel.close()
                }
                
                // Workers
                repeat(workers) { workerId ->
                    launch(Dispatchers.Default) {
                        for (task in taskChannel) {
                            // Simulate work proportional to complexity
                            repeat(task.complexity) { j ->
                                kotlin.math.sqrt((j + 1).toDouble())
                            }
                        }
                    }
                }
            }
        }
        
        println("Static load balancing: ${staticTime.inWholeMilliseconds}ms")
        println("Dynamic load balancing: ${dynamicTime.inWholeMilliseconds}ms")
        
        val improvement = if (staticTime > Duration.ZERO) {
            staticTime.inWholeNanoseconds.toDouble() / dynamicTime.inWholeNanoseconds
        } else 1.0
        
        println("Dynamic improvement: ${String.format("%.1f", improvement)}x")
        
        println("✅ Load balancing demo completed")
    }
}

/**
 * Actor model and message passing optimization
 */
class ActorModelOptimization {
    
    // High-performance actor implementation
    abstract class HighPerformanceActor<T> {
        private val mailbox = Channel<T>(1000)
        private var isActive = true
        
        abstract suspend fun receive(message: T)
        
        suspend fun send(message: T) {
            if (isActive) {
                mailbox.send(message)
            }
        }
        
        fun start() = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (message in mailbox) {
                    receive(message)
                }
            } catch (e: Exception) {
                println("Actor error: ${e.message}")
            }
        }
        
        suspend fun stop() {
            isActive = false
            mailbox.close()
        }
        
        fun getQueueSize(): Int = mailbox.capacity
    }
    
    // Counter actor for concurrent counting
    class CounterActor : HighPerformanceActor<CounterActor.Message>() {
        sealed class Message {
            object Increment : Message()
            data class Add(val value: Long) : Message()
            data class GetCount(val response: CompletableDeferred<Long>) : Message()
        }
        
        private var count = 0L
        
        override suspend fun receive(message: Message) {
            when (message) {
                is Message.Increment -> count++
                is Message.Add -> count += message.value
                is Message.GetCount -> message.response.complete(count)
            }
        }
    }
    
    // Bank account actor for concurrent transactions
    class BankAccountActor(initialBalance: Double) : HighPerformanceActor<BankAccountActor.Message>() {
        sealed class Message {
            data class Deposit(val amount: Double, val response: CompletableDeferred<Boolean>) : Message()
            data class Withdraw(val amount: Double, val response: CompletableDeferred<Boolean>) : Message()
            data class GetBalance(val response: CompletableDeferred<Double>) : Message()
            data class Transfer(val amount: Double, val targetAccount: BankAccountActor, val response: CompletableDeferred<Boolean>) : Message()
        }
        
        private var balance = initialBalance
        
        override suspend fun receive(message: Message) {
            when (message) {
                is Message.Deposit -> {
                    balance += message.amount
                    message.response.complete(true)
                }
                is Message.Withdraw -> {
                    val success = balance >= message.amount
                    if (success) {
                        balance -= message.amount
                    }
                    message.response.complete(success)
                }
                is Message.GetBalance -> {
                    message.response.complete(balance)
                }
                is Message.Transfer -> {
                    val success = balance >= message.amount
                    if (success) {
                        balance -= message.amount
                        val depositResponse = CompletableDeferred<Boolean>()
                        message.targetAccount.send(Message.Deposit(message.amount, depositResponse))
                        depositResponse.await()
                    }
                    message.response.complete(success)
                }
            }
        }
    }
    
    // Message batching actor for improved throughput
    class BatchingActor<T>(
        private val batchSize: Int = 100,
        private val batchTimeout: Duration = 10.milliseconds,
        private val processor: suspend (List<T>) -> Unit
    ) : HighPerformanceActor<T>() {
        
        private val batch = mutableListOf<T>()
        private var lastBatchTime = System.currentTimeMillis()
        
        override suspend fun receive(message: T) {
            batch.add(message)
            
            val shouldFlush = batch.size >= batchSize || 
                            (System.currentTimeMillis() - lastBatchTime) > batchTimeout.inWholeMilliseconds
            
            if (shouldFlush) {
                if (batch.isNotEmpty()) {
                    processor(batch.toList())
                    batch.clear()
                    lastBatchTime = System.currentTimeMillis()
                }
            }
        }
    }
    
    suspend fun demonstrateActorPerformance() {
        println("=== Actor Model Performance ===")
        
        val iterations = 100000
        val concurrency = 8
        
        // Test 1: Actor vs Mutex for concurrent counting
        println("\n--- Concurrent Counting Comparison ---")
        
        // Actor-based counting
        val counterActor = CounterActor()
        val actorJob = counterActor.start()
        
        val actorTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch {
                        repeat(iterations / concurrency) {
                            counterActor.send(CounterActor.Message.Increment)
                        }
                    }
                }
            }
            
            // Wait for processing
            delay(100)
        }
        
        val finalCountResponse = CompletableDeferred<Long>()
        counterActor.send(CounterActor.Message.GetCount(finalCountResponse))
        val actorFinalCount = finalCountResponse.await()
        
        counterActor.stop()
        actorJob.cancel()
        
        // Mutex-based counting
        var mutexCounter = 0L
        val mutex = Mutex()
        
        val mutexTime = measureTime {
            coroutineScope {
                repeat(concurrency) {
                    launch {
                        repeat(iterations / concurrency) {
                            mutex.withLock {
                                mutexCounter++
                            }
                        }
                    }
                }
            }
        }
        
        println("Actor counting: ${actorTime.inWholeMilliseconds}ms, final count: $actorFinalCount")
        println("Mutex counting: ${mutexTime.inWholeMilliseconds}ms, final count: $mutexCounter")
        
        val actorAdvantage = if (mutexTime > Duration.ZERO) {
            mutexTime.inWholeNanoseconds.toDouble() / actorTime.inWholeNanoseconds
        } else 1.0
        
        println("Actor advantage: ${String.format("%.1f", actorAdvantage)}x")
        
        println("✅ Actor performance demo completed")
    }
    
    suspend fun demonstrateActorTransactions() {
        println("=== Actor-Based Transaction System ===")
        
        val accountCount = 100
        val transactionCount = 10000
        
        // Create accounts
        val accounts = (1..accountCount).map { id ->
            BankAccountActor(1000.0).also { it.start() }
        }
        
        val transactionTime = measureTime {
            coroutineScope {
                repeat(transactionCount) { txId ->
                    launch {
                        val fromAccount = accounts[Random.nextInt(accountCount)]
                        val toAccount = accounts[Random.nextInt(accountCount)]
                        val amount = Random.nextDouble(1.0, 100.0)
                        
                        val transferResponse = CompletableDeferred<Boolean>()
                        fromAccount.send(BankAccountActor.Message.Transfer(amount, toAccount, transferResponse))
                        transferResponse.await()
                    }
                }
            }
        }
        
        // Calculate total balance
        var totalBalance = 0.0
        accounts.forEach { account ->
            val balanceResponse = CompletableDeferred<Double>()
            account.send(BankAccountActor.Message.GetBalance(balanceResponse))
            totalBalance += balanceResponse.await()
        }
        
        // Cleanup
        accounts.forEach { it.stop() }
        
        val expectedBalance = accountCount * 1000.0
        val balanceError = kotlin.math.abs(totalBalance - expectedBalance)
        
        println("Transaction processing: ${transactionTime.inWholeMilliseconds}ms")
        println("Transactions per second: ${String.format("%.0f", transactionCount / transactionTime.inWholeSeconds.toDouble())}")
        println("Total balance: ${String.format("%.2f", totalBalance)} (expected: ${String.format("%.2f", expectedBalance)})")
        println("Balance error: ${String.format("%.2f", balanceError)}")
        
        println("✅ Actor transaction demo completed")
    }
    
    suspend fun demonstrateBatchingOptimization() {
        println("=== Message Batching Optimization ===")
        
        val messageCount = 50000
        val batchSizes = listOf(1, 10, 100, 1000)
        
        for (batchSize in batchSizes) {
            val processedMessages = mutableListOf<Int>()
            
            val batchingActor = BatchingActor<Int>(
                batchSize = batchSize,
                batchTimeout = 50.milliseconds
            ) { batch ->
                // Simulate batch processing overhead
                delay(1)
                processedMessages.addAll(batch)
            }
            
            val actorJob = batchingActor.start()
            
            val processingTime = measureTime {
                repeat(messageCount) { i ->
                    batchingActor.send(i)
                }
                
                // Wait for processing to complete
                while (processedMessages.size < messageCount) {
                    delay(10)
                }
            }
            
            batchingActor.stop()
            actorJob.cancel()
            
            val throughput = messageCount / processingTime.inWholeSeconds.toDouble()
            println("Batch size $batchSize: ${processingTime.inWholeMilliseconds}ms, " +
                   "throughput: ${String.format("%.0f", throughput)} msg/sec")
        }
        
        println("✅ Batching optimization demo completed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Concurrency Optimization Demo ===")
    
    try {
        // Lock-free optimization
        val lockFree = LockFreeOptimization()
        lockFree.demonstrateLockFreePerformance()
        println()
        
        lockFree.demonstrateContentionReduction()
        println()
        
        // Work stealing optimization
        val workStealing = WorkStealingOptimization()
        workStealing.demonstrateParallelDecomposition()
        println()
        
        workStealing.demonstrateWorkStealing()
        println()
        
        workStealing.demonstrateLoadBalancing()
        println()
        
        // Actor model optimization
        val actorModel = ActorModelOptimization()
        actorModel.demonstrateActorPerformance()
        println()
        
        actorModel.demonstrateActorTransactions()
        println()
        
        actorModel.demonstrateBatchingOptimization()
        println()
        
        println("=== All Concurrency Optimization Demos Completed ===")
        println()
        
        println("Key Concurrency Optimization Concepts:")
        println("✅ Lock-free and wait-free data structures for maximum performance")
        println("✅ Contention reduction through per-thread counters and batching")
        println("✅ Work stealing algorithms for dynamic load balancing")
        println("✅ Parallel decomposition using fork-join patterns")
        println("✅ Actor model for safe concurrent state management")
        println("✅ Message batching for improved throughput")
        println("✅ Dynamic load balancing using channels")
        println()
        
        println("Concurrency Optimization Guidelines:")
        println("✅ Prefer lock-free approaches when possible")
        println("✅ Use batching to reduce coordination overhead")
        println("✅ Implement work stealing for uneven workloads")
        println("✅ Consider actor model for complex state management")
        println("✅ Minimize contention through careful design")
        println("✅ Profile and measure before optimizing")
        println("✅ Balance throughput vs latency requirements")
        println("✅ Consider NUMA topology and cache effects")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}