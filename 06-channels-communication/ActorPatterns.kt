/**
 * # Actor Patterns - Message-Passing Concurrency Model
 * 
 * ## Problem Description
 * Traditional shared state concurrency is prone to race conditions, deadlocks, and complex
 * synchronization issues. The Actor model provides an alternative approach where concurrent
 * entities (actors) communicate only through message passing, eliminating shared mutable state.
 * Each actor processes messages sequentially, making the system more predictable and easier to reason about.
 * 
 * ## Solution Approach
 * Actor patterns in Kotlin coroutines include:
 * - Channel-based actors using the actor builder
 * - State encapsulation within actors
 * - Message-driven computation and side effects
 * - Actor supervision and fault tolerance
 * - Actor hierarchies and communication patterns
 * - Typed actors with sealed class messages
 * 
 * ## Key Learning Points
 * - Actors encapsulate state and behavior behind message interfaces
 * - Message passing eliminates race conditions and synchronization issues
 * - Each actor processes messages sequentially in its own coroutine
 * - Actors provide natural boundaries for error isolation
 * - Actor systems scale well across threads and processes
 * - Proper actor design requires careful message protocol definition
 * 
 * ## Performance Considerations
 * - Actor creation overhead: ~1-5μs per actor
 * - Message sending overhead: ~100-500ns per message
 * - Message processing throughput: ~1-10M messages/second per actor
 * - Memory per actor: ~200-500 bytes base overhead
 * - Actor supervision adds ~10-20% overhead
 * 
 * ## Common Pitfalls
 * - Creating too many fine-grained actors
 * - Blocking operations inside actor message handlers
 * - Circular message dependencies causing deadlocks
 * - Not handling actor failure and supervision properly
 * - Forgetting to close actor channels for proper cleanup
 * 
 * ## Real-World Applications
 * - Web server request processing
 * - Game entity systems
 * - Financial transaction processing
 * - IoT device management
 * - Distributed system coordination
 * 
 * ## Related Concepts
 * - CSP (Communicating Sequential Processes)
 * - Erlang/Elixir OTP (Open Telecom Platform)
 * - Akka framework patterns
 * - Reactive programming
 */

package channels.communication.actors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Basic actor patterns and message handling
 * 
 * Actor Model Fundamentals:
 * 
 * Actor Components:
 * ┌─────────────────────────────────┐
 * │             Actor               │
 * │  ┌─────────────────────────────┐ │
 * │  │        Mailbox              │ │ ← Messages
 * │  │    (Channel Queue)          │ │
 * │  └─────────────────────────────┘ │
 * │  ┌─────────────────────────────┐ │
 * │  │       Private State         │ │
 * │  │    (Encapsulated Data)      │ │
 * │  └─────────────────────────────┘ │
 * │  ┌─────────────────────────────┐ │
 * │  │    Message Processor        │ │ → Behavior
 * │  │   (Sequential Handler)      │ │
 * │  └─────────────────────────────┘ │
 * └─────────────────────────────────┘
 * 
 * Actor Communication:
 * Actor1 ──send(msg)──> Actor2 ──send(response)──> Actor1
 */
class BasicActorPatterns {
    
    fun demonstrateSimpleActor() = runBlocking {
        println("=== Simple Actor Pattern ===")
        
        // Define message types for the actor
        sealed class CounterMessage {
            object Increment : CounterMessage()
            object Decrement : CounterMessage()
            data class Add(val value: Int) : CounterMessage()
            data class GetValue(val response: CompletableDeferred<Int>) : CounterMessage()
        }
        
        // Simple counter actor
        fun CoroutineScope.counterActor() = actor<CounterMessage> {
            var counter = 0 // Private state - only accessible within actor
            
            for (message in channel) { // Process messages sequentially
                when (message) {
                    is CounterMessage.Increment -> {
                        counter++
                        println("  Counter incremented to $counter")
                    }
                    is CounterMessage.Decrement -> {
                        counter--
                        println("  Counter decremented to $counter")
                    }
                    is CounterMessage.Add -> {
                        counter += message.value
                        println("  Counter increased by ${message.value} to $counter")
                    }
                    is CounterMessage.GetValue -> {
                        message.response.complete(counter)
                        println("  Counter value requested: $counter")
                    }
                }
            }
        }
        
        println("1. Basic counter actor:")
        
        val counter = counterActor()
        
        // Send messages to actor
        counter.send(CounterMessage.Increment)
        counter.send(CounterMessage.Increment)
        counter.send(CounterMessage.Add(5))
        counter.send(CounterMessage.Decrement)
        
        // Get current value
        val currentValue = CompletableDeferred<Int>()
        counter.send(CounterMessage.GetValue(currentValue))
        val value = currentValue.await()
        println("  Final counter value: $value")
        
        // Close the actor
        counter.close()
        
        println()
        
        // Multiple actors working concurrently
        println("2. Multiple concurrent actors:")
        
        val actors = (1..3).map { id ->
            id to counterActor()
        }
        
        // Send different operations to different actors
        actors.forEach { (id, actor) ->
            launch {
                repeat(5) { iteration ->
                    when (Random.nextInt(3)) {
                        0 -> actor.send(CounterMessage.Increment)
                        1 -> actor.send(CounterMessage.Add(Random.nextInt(1, 10)))
                        2 -> actor.send(CounterMessage.Decrement)
                    }
                    delay(Random.nextLong(50, 150))
                }
                
                // Get final value for this actor
                val finalValue = CompletableDeferred<Int>()
                actor.send(CounterMessage.GetValue(finalValue))
                val result = finalValue.await()
                println("  Actor $id final value: $result")
            }
        }
        
        delay(1000) // Let all actors finish
        
        // Close all actors
        actors.forEach { (_, actor) -> actor.close() }
        
        println("Simple actor pattern completed\n")
    }
    
    fun demonstrateStatefulActor() = runBlocking {
        println("=== Stateful Actor Pattern ===")
        
        // Bank account actor with complex state
        data class Account(val id: String, var balance: Double, val transactions: MutableList<Transaction> = mutableListOf())
        data class Transaction(val type: String, val amount: Double, val timestamp: Long = System.currentTimeMillis())
        
        sealed class AccountMessage {
            data class Deposit(val amount: Double, val response: CompletableDeferred<Boolean>) : AccountMessage()
            data class Withdraw(val amount: Double, val response: CompletableDeferred<Boolean>) : AccountMessage()
            data class Transfer(val toAccount: SendChannel<AccountMessage>, val amount: Double, val response: CompletableDeferred<Boolean>) : AccountMessage()
            data class GetBalance(val response: CompletableDeferred<Double>) : AccountMessage()
            data class GetTransactionHistory(val response: CompletableDeferred<List<Transaction>>) : AccountMessage()
        }
        
        fun CoroutineScope.bankAccountActor(accountId: String, initialBalance: Double = 0.0) = actor<AccountMessage> {
            val account = Account(accountId, initialBalance)
            
            for (message in channel) {
                when (message) {
                    is AccountMessage.Deposit -> {
                        if (message.amount > 0) {
                            account.balance += message.amount
                            account.transactions.add(Transaction("DEPOSIT", message.amount))
                            println("  Account ${account.id}: Deposited ${message.amount}, new balance: ${account.balance}")
                            message.response.complete(true)
                        } else {
                            message.response.complete(false)
                        }
                    }
                    
                    is AccountMessage.Withdraw -> {
                        if (message.amount > 0 && account.balance >= message.amount) {
                            account.balance -= message.amount
                            account.transactions.add(Transaction("WITHDRAWAL", -message.amount))
                            println("  Account ${account.id}: Withdrew ${message.amount}, new balance: ${account.balance}")
                            message.response.complete(true)
                        } else {
                            println("  Account ${account.id}: Insufficient funds for withdrawal of ${message.amount}")
                            message.response.complete(false)
                        }
                    }
                    
                    is AccountMessage.Transfer -> {
                        if (message.amount > 0 && account.balance >= message.amount) {
                            // Withdraw from this account
                            account.balance -= message.amount
                            account.transactions.add(Transaction("TRANSFER_OUT", -message.amount))
                            
                            // Deposit to target account
                            val depositResponse = CompletableDeferred<Boolean>()
                            message.toAccount.send(AccountMessage.Deposit(message.amount, depositResponse))
                            
                            if (depositResponse.await()) {
                                println("  Account ${account.id}: Transferred ${message.amount}, new balance: ${account.balance}")
                                message.response.complete(true)
                            } else {
                                // Rollback if target deposit failed
                                account.balance += message.amount
                                account.transactions.removeLastOrNull()
                                message.response.complete(false)
                            }
                        } else {
                            message.response.complete(false)
                        }
                    }
                    
                    is AccountMessage.GetBalance -> {
                        message.response.complete(account.balance)
                    }
                    
                    is AccountMessage.GetTransactionHistory -> {
                        message.response.complete(account.transactions.toList())
                    }
                }
            }
        }
        
        println("1. Bank account actors:")
        
        val account1 = bankAccountActor("ACC-001", 1000.0)
        val account2 = bankAccountActor("ACC-002", 500.0)
        val account3 = bankAccountActor("ACC-003", 0.0)
        
        // Perform various banking operations
        val deposit1 = CompletableDeferred<Boolean>()
        account1.send(AccountMessage.Deposit(200.0, deposit1))
        println("  Deposit result: ${deposit1.await()}")
        
        val withdraw1 = CompletableDeferred<Boolean>()
        account2.send(AccountMessage.Withdraw(100.0, withdraw1))
        println("  Withdrawal result: ${withdraw1.await()}")
        
        val transfer1 = CompletableDeferred<Boolean>()
        account1.send(AccountMessage.Transfer(account3, 300.0, transfer1))
        println("  Transfer result: ${transfer1.await()}")
        
        // Check final balances
        val balance1 = CompletableDeferred<Double>()
        val balance2 = CompletableDeferred<Double>()
        val balance3 = CompletableDeferred<Double>()
        
        account1.send(AccountMessage.GetBalance(balance1))
        account2.send(AccountMessage.GetBalance(balance2))
        account3.send(AccountMessage.GetBalance(balance3))
        
        println("  Final balances:")
        println("    Account 1: ${balance1.await()}")
        println("    Account 2: ${balance2.await()}")
        println("    Account 3: ${balance3.await()}")
        
        // Get transaction history
        val history1 = CompletableDeferred<List<Transaction>>()
        account1.send(AccountMessage.GetTransactionHistory(history1))
        val transactions = history1.await()
        
        println("  Account 1 transactions:")
        transactions.forEach { transaction ->
            println("    ${transaction.type}: ${transaction.amount}")
        }
        
        // Close actors
        account1.close()
        account2.close()
        account3.close()
        
        println("Stateful actor pattern completed\n")
    }
}

/**
 * Actor supervision and fault tolerance
 */
class ActorSupervision {
    
    fun demonstrateActorSupervision() = runBlocking {
        println("=== Actor Supervision Pattern ===")
        
        // Messages for worker actors
        sealed class WorkerMessage {
            data class DoWork(val taskId: String, val shouldFail: Boolean = false) : WorkerMessage()
            object GetStatus : WorkerMessage()
            object Reset : WorkerMessage()
        }
        
        // Messages for supervisor actor
        sealed class SupervisorMessage {
            data class StartWorker(val workerId: String) : SupervisorMessage()
            data class StopWorker(val workerId: String) : SupervisorMessage()
            data class SendWork(val workerId: String, val task: WorkerMessage) : SupervisorMessage()
            object GetWorkerStatus : SupervisorMessage()
        }
        
        // Worker actor that can fail
        fun CoroutineScope.workerActor(workerId: String) = actor<WorkerMessage> {
            var tasksCompleted = 0
            var errors = 0
            
            try {
                for (message in channel) {
                    when (message) {
                        is WorkerMessage.DoWork -> {
                            try {
                                if (message.shouldFail) {
                                    throw RuntimeException("Simulated worker failure for task ${message.taskId}")
                                }
                                
                                delay(Random.nextLong(50, 200)) // Simulate work
                                tasksCompleted++
                                println("    Worker $workerId: Completed task ${message.taskId} (total: $tasksCompleted)")
                                
                            } catch (e: Exception) {
                                errors++
                                println("    Worker $workerId: Failed task ${message.taskId} - ${e.message} (errors: $errors)")
                                
                                // Worker continues processing despite individual task failures
                                if (errors >= 3) {
                                    println("    Worker $workerId: Too many errors, stopping")
                                    break
                                }
                            }
                        }
                        
                        is WorkerMessage.GetStatus -> {
                            println("    Worker $workerId: Status - Completed: $tasksCompleted, Errors: $errors")
                        }
                        
                        is WorkerMessage.Reset -> {
                            tasksCompleted = 0
                            errors = 0
                            println("    Worker $workerId: Reset completed")
                        }
                    }
                }
            } catch (e: Exception) {
                println("    Worker $workerId: Fatal error - ${e.message}")
            } finally {
                println("    Worker $workerId: Shutting down")
            }
        }
        
        // Supervisor actor managing worker actors
        fun CoroutineScope.supervisorActor() = actor<SupervisorMessage> {
            val workers = mutableMapOf<String, SendChannel<WorkerMessage>>()
            val workerJobs = mutableMapOf<String, Job>()
            
            for (message in channel) {
                when (message) {
                    is SupervisorMessage.StartWorker -> {
                        if (!workers.containsKey(message.workerId)) {
                            val worker = workerActor(message.workerId)
                            workers[message.workerId] = worker
                            
                            // Monitor worker
                            val workerJob = launch {
                                try {
                                    worker.invokeOnClose { cause ->
                                        println("  Supervisor: Worker ${message.workerId} closed${cause?.let { " due to $it" } ?: ""}")
                                        workers.remove(message.workerId)
                                        workerJobs.remove(message.workerId)
                                    }
                                } catch (e: Exception) {
                                    println("  Supervisor: Worker ${message.workerId} failed - ${e.message}")
                                    // Restart worker
                                    launch {
                                        delay(1000) // Wait before restart
                                        channel.send(SupervisorMessage.StartWorker(message.workerId))
                                    }
                                }
                            }
                            
                            workerJobs[message.workerId] = workerJob
                            println("  Supervisor: Started worker ${message.workerId}")
                        }
                    }
                    
                    is SupervisorMessage.StopWorker -> {
                        workers[message.workerId]?.close()
                        workerJobs[message.workerId]?.cancel()
                        println("  Supervisor: Stopped worker ${message.workerId}")
                    }
                    
                    is SupervisorMessage.SendWork -> {
                        val worker = workers[message.workerId]
                        if (worker != null) {
                            worker.send(message.task)
                        } else {
                            println("  Supervisor: Worker ${message.workerId} not found")
                        }
                    }
                    
                    is SupervisorMessage.GetWorkerStatus -> {
                        println("  Supervisor: Active workers: ${workers.keys}")
                        workers.forEach { (workerId, worker) ->
                            worker.send(WorkerMessage.GetStatus)
                        }
                    }
                }
            }
            
            // Cleanup all workers when supervisor stops
            workers.values.forEach { it.close() }
            workerJobs.values.forEach { it.cancel() }
        }
        
        println("1. Supervised worker actors:")
        
        val supervisor = supervisorActor()
        
        // Start workers
        supervisor.send(SupervisorMessage.StartWorker("worker-1"))
        supervisor.send(SupervisorMessage.StartWorker("worker-2"))
        supervisor.send(SupervisorMessage.StartWorker("worker-3"))
        
        delay(100)
        
        // Send work to workers
        repeat(15) { taskId ->
            val workerId = "worker-${(taskId % 3) + 1}"
            val shouldFail = Random.nextDouble() < 0.2 // 20% chance of failure
            supervisor.send(SupervisorMessage.SendWork(workerId, WorkerMessage.DoWork("task-$taskId", shouldFail)))
        }
        
        delay(2000) // Let work complete
        
        // Check worker status
        supervisor.send(SupervisorMessage.GetWorkerStatus)
        
        delay(500)
        
        // Stop some workers
        supervisor.send(SupervisorMessage.StopWorker("worker-2"))
        
        delay(100)
        supervisor.send(SupervisorMessage.GetWorkerStatus)
        
        delay(500)
        supervisor.close()
        
        println("Actor supervision completed\n")
    }
    
    fun demonstrateActorRecovery() = runBlocking {
        println("=== Actor Recovery Patterns ===")
        
        // Resilient actor with state persistence
        data class ActorState(val processedItems: Int, val errors: Int, val lastProcessedTime: Long)
        
        sealed class ResilientMessage {
            data class ProcessItem(val item: String, val shouldFail: Boolean = false) : ResilientMessage()
            object SaveState : ResilientMessage()
            object LoadState : ResilientMessage()
            data class GetState(val response: CompletableDeferred<ActorState>) : ResilientMessage()
        }
        
        fun CoroutineScope.resilientActor(actorId: String) = actor<ResilientMessage> {
            var state = ActorState(0, 0, System.currentTimeMillis())
            val stateStore = mutableMapOf<String, ActorState>() // Simulated persistence
            
            // Recovery logic
            fun recover() {
                val savedState = stateStore[actorId]
                if (savedState != null) {
                    state = savedState
                    println("    Actor $actorId: Recovered state - processed: ${state.processedItems}, errors: ${state.errors}")
                }
            }
            
            fun saveState() {
                stateStore[actorId] = state.copy(lastProcessedTime = System.currentTimeMillis())
                println("    Actor $actorId: State saved")
            }
            
            // Initial recovery
            recover()
            
            try {
                for (message in channel) {
                    when (message) {
                        is ResilientMessage.ProcessItem -> {
                            try {
                                if (message.shouldFail) {
                                    throw RuntimeException("Processing failed for ${message.item}")
                                }
                                
                                delay(100) // Simulate processing
                                state = state.copy(
                                    processedItems = state.processedItems + 1,
                                    lastProcessedTime = System.currentTimeMillis()
                                )
                                
                                println("    Actor $actorId: Processed ${message.item} (total: ${state.processedItems})")
                                
                            } catch (e: Exception) {
                                state = state.copy(
                                    errors = state.errors + 1,
                                    lastProcessedTime = System.currentTimeMillis()
                                )
                                println("    Actor $actorId: Error processing ${message.item} - ${e.message} (errors: ${state.errors})")
                                
                                // Auto-save state after errors
                                saveState()
                            }
                        }
                        
                        is ResilientMessage.SaveState -> {
                            saveState()
                        }
                        
                        is ResilientMessage.LoadState -> {
                            recover()
                        }
                        
                        is ResilientMessage.GetState -> {
                            message.response.complete(state)
                        }
                    }
                }
            } catch (e: Exception) {
                println("    Actor $actorId: Fatal error - ${e.message}")
                saveState() // Save state before dying
            }
        }
        
        println("1. Resilient actor with state persistence:")
        
        var actor = resilientActor("resilient-1")
        
        // Process some items
        actor.send(ResilientMessage.ProcessItem("item-1"))
        actor.send(ResilientMessage.ProcessItem("item-2"))
        actor.send(ResilientMessage.ProcessItem("item-3", shouldFail = true))
        actor.send(ResilientMessage.ProcessItem("item-4"))
        
        delay(1000)
        
        // Save state
        actor.send(ResilientMessage.SaveState)
        
        // Get current state
        val currentState1 = CompletableDeferred<ActorState>()
        actor.send(ResilientMessage.GetState(currentState1))
        val state1 = currentState1.await()
        println("  Current state: $state1")
        
        // Simulate actor failure and restart
        println("  Simulating actor restart...")
        actor.close()
        delay(100)
        
        // Create new actor (simulating restart)
        actor = resilientActor("resilient-1") // Same ID, will recover state
        
        // Continue processing
        actor.send(ResilientMessage.ProcessItem("item-5"))
        actor.send(ResilientMessage.ProcessItem("item-6"))
        
        delay(500)
        
        // Get final state
        val currentState2 = CompletableDeferred<ActorState>()
        actor.send(ResilientMessage.GetState(currentState2))
        val state2 = currentState2.await()
        println("  Final state after recovery: $state2")
        
        actor.close()
        
        println("Actor recovery patterns completed\n")
    }
}

/**
 * Actor communication patterns and protocols
 */
class ActorCommunicationPatterns {
    
    fun demonstrateRequestResponsePattern() = runBlocking {
        println("=== Request-Response Communication Pattern ===")
        
        // Calculator actor that processes mathematical operations
        sealed class CalculatorMessage {
            data class Add(val a: Double, val b: Double, val response: CompletableDeferred<Double>) : CalculatorMessage()
            data class Multiply(val a: Double, val b: Double, val response: CompletableDeferred<Double>) : CalculatorMessage()
            data class Divide(val a: Double, val b: Double, val response: CompletableDeferred<Result<Double>>) : CalculatorMessage()
            data class Power(val base: Double, val exponent: Double, val response: CompletableDeferred<Double>) : CalculatorMessage()
        }
        
        fun CoroutineScope.calculatorActor() = actor<CalculatorMessage> {
            for (message in channel) {
                when (message) {
                    is CalculatorMessage.Add -> {
                        val result = message.a + message.b
                        println("    Calculator: ${message.a} + ${message.b} = $result")
                        message.response.complete(result)
                    }
                    
                    is CalculatorMessage.Multiply -> {
                        val result = message.a * message.b
                        println("    Calculator: ${message.a} * ${message.b} = $result")
                        message.response.complete(result)
                    }
                    
                    is CalculatorMessage.Divide -> {
                        if (message.b != 0.0) {
                            val result = message.a / message.b
                            println("    Calculator: ${message.a} / ${message.b} = $result")
                            message.response.complete(Result.success(result))
                        } else {
                            println("    Calculator: Division by zero error")
                            message.response.complete(Result.failure(ArithmeticException("Division by zero")))
                        }
                    }
                    
                    is CalculatorMessage.Power -> {
                        val result = kotlin.math.pow(message.base, message.exponent)
                        println("    Calculator: ${message.base}^${message.exponent} = $result")
                        message.response.complete(result)
                    }
                }
            }
        }
        
        println("1. Calculator actor with request-response:")
        
        val calculator = calculatorActor()
        
        // Perform calculations
        val addResult = CompletableDeferred<Double>()
        calculator.send(CalculatorMessage.Add(10.0, 5.0, addResult))
        println("  Addition result: ${addResult.await()}")
        
        val multiplyResult = CompletableDeferred<Double>()
        calculator.send(CalculatorMessage.Multiply(3.0, 7.0, multiplyResult))
        println("  Multiplication result: ${multiplyResult.await()}")
        
        val divideResult1 = CompletableDeferred<Result<Double>>()
        calculator.send(CalculatorMessage.Divide(20.0, 4.0, divideResult1))
        divideResult1.await().fold(
            onSuccess = { println("  Division result: $it") },
            onFailure = { println("  Division error: ${it.message}") }
        )
        
        val divideResult2 = CompletableDeferred<Result<Double>>()
        calculator.send(CalculatorMessage.Divide(10.0, 0.0, divideResult2))
        divideResult2.await().fold(
            onSuccess = { println("  Division result: $it") },
            onFailure = { println("  Division error: ${it.message}") }
        )
        
        val powerResult = CompletableDeferred<Double>()
        calculator.send(CalculatorMessage.Power(2.0, 8.0, powerResult))
        println("  Power result: ${powerResult.await()}")
        
        calculator.close()
        
        println()
        
        // Concurrent request-response pattern
        println("2. Concurrent calculator requests:")
        
        val calculator2 = calculatorActor()
        
        val concurrentResults = (1..10).map { i ->
            async {
                val result = CompletableDeferred<Double>()
                calculator2.send(CalculatorMessage.Add(i.toDouble(), (i * 2).toDouble(), result))
                "Request $i: ${result.await()}"
            }
        }
        
        concurrentResults.awaitAll().forEach { result ->
            println("  $result")
        }
        
        calculator2.close()
        
        println("Request-response pattern completed\n")
    }
    
    fun demonstratePublishSubscribePattern() = runBlocking {
        println("=== Publish-Subscribe Communication Pattern ===")
        
        // Event types
        data class Event(val type: String, val data: String, val timestamp: Long = System.currentTimeMillis())
        
        // Publisher actor messages
        sealed class PublisherMessage {
            data class Subscribe(val subscriber: SendChannel<Event>) : PublisherMessage()
            data class Unsubscribe(val subscriber: SendChannel<Event>) : PublisherMessage()
            data class Publish(val event: Event) : PublisherMessage()
            object GetSubscriberCount : PublisherMessage()
        }
        
        // Subscriber actor messages
        sealed class SubscriberMessage {
            data class ProcessEvent(val event: Event) : SubscriberMessage()
            object GetProcessedCount : SubscriberMessage()
        }
        
        // Publisher actor that manages subscribers and distributes events
        fun CoroutineScope.publisherActor() = actor<PublisherMessage> {
            val subscribers = mutableSetOf<SendChannel<Event>>()
            
            for (message in channel) {
                when (message) {
                    is PublisherMessage.Subscribe -> {
                        subscribers.add(message.subscriber)
                        println("    Publisher: Subscriber added (${subscribers.size} total)")
                    }
                    
                    is PublisherMessage.Unsubscribe -> {
                        subscribers.remove(message.subscriber)
                        println("    Publisher: Subscriber removed (${subscribers.size} remaining)")
                    }
                    
                    is PublisherMessage.Publish -> {
                        println("    Publisher: Publishing event ${message.event.type}: ${message.event.data}")
                        subscribers.forEach { subscriber ->
                            launch {
                                try {
                                    subscriber.send(message.event)
                                } catch (e: Exception) {
                                    println("    Publisher: Failed to send to subscriber - ${e.message}")
                                    subscribers.remove(subscriber)
                                }
                            }
                        }
                    }
                    
                    is PublisherMessage.GetSubscriberCount -> {
                        println("    Publisher: Current subscribers: ${subscribers.size}")
                    }
                }
            }
        }
        
        // Subscriber actor that processes events
        fun CoroutineScope.subscriberActor(subscriberId: String) = actor<Event> {
            var processedCount = 0
            
            for (event in channel) {
                processedCount++
                delay(Random.nextLong(50, 150)) // Simulate processing time
                println("      Subscriber $subscriberId: Processed ${event.type} event #$processedCount")
            }
        }
        
        println("1. Publisher-subscriber pattern:")
        
        val publisher = publisherActor()
        
        // Create subscribers
        val subscriber1 = subscriberActor("SUB-1")
        val subscriber2 = subscriberActor("SUB-2")
        val subscriber3 = subscriberActor("SUB-3")
        
        // Subscribe to publisher
        publisher.send(PublisherMessage.Subscribe(subscriber1))
        publisher.send(PublisherMessage.Subscribe(subscriber2))
        publisher.send(PublisherMessage.Subscribe(subscriber3))
        
        delay(100)
        
        // Publish events
        val eventTypes = listOf("USER_LOGIN", "ORDER_PLACED", "PAYMENT_PROCESSED", "NOTIFICATION_SENT")
        repeat(12) { i ->
            val event = Event(
                type = eventTypes.random(),
                data = "Event data $i"
            )
            publisher.send(PublisherMessage.Publish(event))
            delay(200)
        }
        
        delay(1000) // Let events process
        
        // Unsubscribe one subscriber
        publisher.send(PublisherMessage.Unsubscribe(subscriber2))
        subscriber2.close()
        
        // Publish more events
        repeat(5) { i ->
            val event = Event(
                type = eventTypes.random(),
                data = "Late event ${i + 13}"
            )
            publisher.send(PublisherMessage.Publish(event))
            delay(150)
        }
        
        delay(1000)
        
        // Check subscriber count
        publisher.send(PublisherMessage.GetSubscriberCount)
        
        delay(100)
        
        // Close all actors
        subscriber1.close()
        subscriber3.close()
        publisher.close()
        
        println("Publish-subscribe pattern completed\n")
    }
    
    fun demonstratePipelinePattern() = runBlocking {
        println("=== Pipeline Communication Pattern ===")
        
        // Data processing pipeline stages
        data class PipelineData(val id: String, var value: String, val stages: MutableList<String> = mutableListOf())
        
        // Generic pipeline stage actor
        fun CoroutineScope.pipelineStageActor(
            stageName: String,
            processingTime: Long,
            transform: (String) -> String,
            nextStage: SendChannel<PipelineData>?
        ) = actor<PipelineData> {
            
            for (data in channel) {
                try {
                    delay(processingTime) // Simulate processing time
                    
                    // Transform the data
                    data.value = transform(data.value)
                    data.stages.add(stageName)
                    
                    println("    Stage $stageName: Processed ${data.id} -> ${data.value}")
                    
                    // Send to next stage or complete pipeline
                    if (nextStage != null) {
                        nextStage.send(data)
                    } else {
                        println("    Pipeline completed for ${data.id}: ${data.value} (stages: ${data.stages})")
                    }
                    
                } catch (e: Exception) {
                    println("    Stage $stageName: Error processing ${data.id} - ${e.message}")
                }
            }
        }
        
        println("1. Data processing pipeline:")
        
        // Create pipeline stages (in reverse order due to dependencies)
        val outputStage = pipelineStageActor(
            stageName = "OUTPUT",
            processingTime = 50,
            transform = { "FINAL[$it]" },
            nextStage = null // Last stage
        )
        
        val validationStage = pipelineStageActor(
            stageName = "VALIDATION", 
            processingTime = 100,
            transform = { "VALID[$it]" },
            nextStage = outputStage
        )
        
        val enrichmentStage = pipelineStageActor(
            stageName = "ENRICHMENT",
            processingTime = 150,
            transform = { "ENRICHED[$it]" },
            nextStage = validationStage
        )
        
        val inputStage = pipelineStageActor(
            stageName = "INPUT",
            processingTime = 75,
            transform = { "PROCESSED[$it]" },
            nextStage = enrichmentStage
        )
        
        // Send data through pipeline
        repeat(8) { i ->
            val data = PipelineData("item-$i", "data-$i")
            inputStage.send(data)
            delay(100) // Stagger input
        }
        
        delay(3000) // Let pipeline complete
        
        // Close pipeline stages
        inputStage.close()
        enrichmentStage.close()
        validationStage.close()
        outputStage.close()
        
        println()
        
        // Branching pipeline pattern
        println("2. Branching pipeline:")
        
        sealed class BranchMessage {
            data class ProcessData(val data: PipelineData) : BranchMessage()
            object GetStats : BranchMessage()
        }
        
        fun CoroutineScope.branchingActor(branchName: String) = actor<BranchMessage> {
            var processedCount = 0
            
            for (message in channel) {
                when (message) {
                    is BranchMessage.ProcessData -> {
                        delay(Random.nextLong(100, 300))
                        processedCount++
                        message.data.stages.add(branchName)
                        println("    Branch $branchName: Processed ${message.data.id} (count: $processedCount)")
                    }
                    
                    is BranchMessage.GetStats -> {
                        println("    Branch $branchName: Total processed: $processedCount")
                    }
                }
            }
        }
        
        val branchA = branchingActor("BRANCH-A")
        val branchB = branchingActor("BRANCH-B")
        val branchC = branchingActor("BRANCH-C")
        
        // Router that distributes data to branches
        val router = actor<PipelineData> {
            val branches = listOf(branchA, branchB, branchC)
            var currentBranch = 0
            
            for (data in channel) {
                val selectedBranch = branches[currentBranch]
                selectedBranch.send(BranchMessage.ProcessData(data))
                currentBranch = (currentBranch + 1) % branches.size
            }
        }
        
        // Send data to router
        repeat(12) { i ->
            val data = PipelineData("branch-item-$i", "branch-data-$i")
            router.send(data)
            delay(50)
        }
        
        delay(2000) // Let processing complete
        
        // Get branch statistics
        branchA.send(BranchMessage.GetStats)
        branchB.send(BranchMessage.GetStats)
        branchC.send(BranchMessage.GetStats)
        
        delay(100)
        
        // Close all actors
        router.close()
        branchA.close()
        branchB.close()
        branchC.close()
        
        println("Pipeline communication pattern completed\n")
    }
}

/**
 * Actor performance optimization and best practices
 */
class ActorPerformanceOptimization {
    
    fun demonstrateActorPooling() = runBlocking {
        println("=== Actor Pooling for Performance ===")
        
        // Expensive computation actor
        sealed class ComputationMessage {
            data class Compute(val input: Int, val response: CompletableDeferred<Long>) : ComputationMessage()
            object GetStats : ComputationMessage()
        }
        
        fun CoroutineScope.computationActor(actorId: String) = actor<ComputationMessage> {
            var computationsPerformed = 0
            var totalTime = 0L
            
            for (message in channel) {
                when (message) {
                    is ComputationMessage.Compute -> {
                        val startTime = System.currentTimeMillis()
                        
                        // Simulate expensive computation
                        delay(Random.nextLong(100, 300))
                        val result = (1..message.input).fold(1L) { acc, n -> acc * n.toLong() }
                        
                        val endTime = System.currentTimeMillis()
                        computationsPerformed++
                        totalTime += (endTime - startTime)
                        
                        println("      Actor $actorId: Computed factorial(${message.input}) = $result")
                        message.response.complete(result)
                    }
                    
                    is ComputationMessage.GetStats -> {
                        val avgTime = if (computationsPerformed > 0) totalTime / computationsPerformed else 0L
                        println("    Actor $actorId: Stats - computations: $computationsPerformed, avg time: ${avgTime}ms")
                    }
                }
            }
        }
        
        // Actor pool manager
        class ActorPool(private val poolSize: Int, private val scope: CoroutineScope) {
            private val actors = (1..poolSize).map { id ->
                scope.computationActor("pool-$id")
            }
            private var currentActor = 0
            
            suspend fun compute(input: Int): Long {
                val response = CompletableDeferred<Long>()
                val actor = actors[currentActor]
                currentActor = (currentActor + 1) % actors.size
                
                actor.send(ComputationMessage.Compute(input, response))
                return response.await()
            }
            
            suspend fun getStats() {
                actors.forEach { actor ->
                    actor.send(ComputationMessage.GetStats)
                }
            }
            
            fun close() {
                actors.forEach { it.close() }
            }
        }
        
        println("1. Actor pool vs single actor performance:")
        
        // Single actor test
        val singleActor = computationActor("single")
        val singleActorTime = measureTimeMillis {
            val singleActorJobs = (5..12).map { input ->
                async {
                    val response = CompletableDeferred<Long>()
                    singleActor.send(ComputationMessage.Compute(input, response))
                    response.await()
                }
            }
            singleActorJobs.awaitAll()
        }
        
        singleActor.send(ComputationMessage.GetStats)
        delay(100)
        singleActor.close()
        
        // Actor pool test
        val actorPool = ActorPool(poolSize = 4, scope = this)
        val poolTime = measureTimeMillis {
            val poolJobs = (5..12).map { input ->
                async {
                    actorPool.compute(input)
                }
            }
            poolJobs.awaitAll()
        }
        
        actorPool.getStats()
        delay(100)
        actorPool.close()
        
        println("  Single actor time: ${singleActorTime}ms")
        println("  Actor pool time: ${poolTime}ms")
        println("  Speedup: ${"%.2f".format(singleActorTime.toDouble() / poolTime)}x")
        
        println("Actor pooling completed\n")
    }
    
    fun demonstrateActorBestPractices() = runBlocking {
        println("=== Actor Best Practices ===")
        
        println("1. Message batching for high throughput:")
        
        // Batch processing actor
        sealed class BatchMessage {
            data class SingleItem(val item: String) : BatchMessage()
            data class BatchItems(val items: List<String>) : BatchMessage()
            object Flush : BatchMessage()
            object GetStats : BatchMessage()
        }
        
        fun CoroutineScope.batchProcessingActor(batchSize: Int = 10, flushIntervalMs: Long = 1000) = actor<BatchMessage> {
            val batch = mutableListOf<String>()
            var processed = 0
            var lastFlushTime = System.currentTimeMillis()
            
            // Periodic flush
            launch {
                while (isActive) {
                    delay(flushIntervalMs)
                    channel.trySend(BatchMessage.Flush)
                }
            }
            
            suspend fun processBatch(items: List<String>) {
                if (items.isNotEmpty()) {
                    delay(50) // Simulate batch processing
                    processed += items.size
                    println("    Batch processor: Processed batch of ${items.size} items (total: $processed)")
                }
            }
            
            for (message in channel) {
                when (message) {
                    is BatchMessage.SingleItem -> {
                        batch.add(message.item)
                        
                        // Process when batch is full
                        if (batch.size >= batchSize) {
                            processBatch(batch.toList())
                            batch.clear()
                            lastFlushTime = System.currentTimeMillis()
                        }
                    }
                    
                    is BatchMessage.BatchItems -> {
                        processBatch(message.items)
                    }
                    
                    is BatchMessage.Flush -> {
                        if (batch.isNotEmpty()) {
                            processBatch(batch.toList())
                            batch.clear()
                            lastFlushTime = System.currentTimeMillis()
                        }
                    }
                    
                    is BatchMessage.GetStats -> {
                        println("    Batch processor: Total processed: $processed, pending: ${batch.size}")
                    }
                }
            }
            
            // Process remaining items on shutdown
            if (batch.isNotEmpty()) {
                processBatch(batch.toList())
            }
        }
        
        val batchProcessor = batchProcessingActor(batchSize = 5, flushIntervalMs = 500)
        
        // Send individual items
        repeat(17) { i ->
            batchProcessor.send(BatchMessage.SingleItem("item-$i"))
            delay(Random.nextLong(50, 150))
        }
        
        delay(1000) // Let flush occur
        
        // Send batch
        batchProcessor.send(BatchMessage.BatchItems(listOf("batch-1", "batch-2", "batch-3")))
        
        delay(200)
        batchProcessor.send(BatchMessage.GetStats)
        delay(100)
        batchProcessor.close()
        
        println()
        
        println("2. Actor lifecycle management:")
        
        // Actor with proper lifecycle management
        sealed class LifecycleMessage {
            object Initialize : LifecycleMessage()
            data class DoWork(val work: String) : LifecycleMessage()
            object Shutdown : LifecycleMessage()
            data class GetStatus(val response: CompletableDeferred<String>) : LifecycleMessage()
        }
        
        fun CoroutineScope.lifecycleActor(actorId: String) = actor<LifecycleMessage> {
            var isInitialized = false
            var workCount = 0
            var isShuttingDown = false
            
            suspend fun initialize() {
                if (!isInitialized) {
                    delay(200) // Simulation initialization
                    isInitialized = true
                    println("    Actor $actorId: Initialized")
                }
            }
            
            suspend fun shutdown() {
                if (!isShuttingDown) {
                    isShuttingDown = true
                    println("    Actor $actorId: Shutting down...")
                    
                    // Cleanup operations
                    delay(100)
                    println("    Actor $actorId: Cleanup completed")
                }
            }
            
            try {
                for (message in channel) {
                    when (message) {
                        is LifecycleMessage.Initialize -> {
                            initialize()
                        }
                        
                        is LifecycleMessage.DoWork -> {
                            if (!isInitialized) {
                                initialize()
                            }
                            
                            if (!isShuttingDown) {
                                workCount++
                                delay(100)
                                println("    Actor $actorId: Completed work '${message.work}' (count: $workCount)")
                            } else {
                                println("    Actor $actorId: Rejecting work '${message.work}' - shutting down")
                            }
                        }
                        
                        is LifecycleMessage.Shutdown -> {
                            shutdown()
                            break // Exit message loop
                        }
                        
                        is LifecycleMessage.GetStatus -> {
                            val status = "initialized: $isInitialized, work count: $workCount, shutting down: $isShuttingDown"
                            message.response.complete(status)
                        }
                    }
                }
            } finally {
                if (!isShuttingDown) {
                    shutdown()
                }
            }
        }
        
        val lifecycleActor = lifecycleActor("lifecycle-1")
        
        // Use actor with proper lifecycle
        lifecycleActor.send(LifecycleMessage.Initialize)
        delay(300)
        
        lifecycleActor.send(LifecycleMessage.DoWork("task-1"))
        lifecycleActor.send(LifecycleMessage.DoWork("task-2"))
        
        val status1 = CompletableDeferred<String>()
        lifecycleActor.send(LifecycleMessage.GetStatus(status1))
        println("  Status: ${status1.await()}")
        
        lifecycleActor.send(LifecycleMessage.DoWork("task-3"))
        
        // Graceful shutdown
        lifecycleActor.send(LifecycleMessage.Shutdown)
        
        delay(500) // Let shutdown complete
        
        println("Actor best practices completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Basic actor patterns
        BasicActorPatterns().demonstrateSimpleActor()
        BasicActorPatterns().demonstrateStatefulActor()
        
        // Actor supervision
        ActorSupervision().demonstrateActorSupervision()
        ActorSupervision().demonstrateActorRecovery()
        
        // Communication patterns
        ActorCommunicationPatterns().demonstrateRequestResponsePattern()
        ActorCommunicationPatterns().demonstratePublishSubscribePattern()
        ActorCommunicationPatterns().demonstratePipelinePattern()
        
        // Performance optimization
        ActorPerformanceOptimization().demonstrateActorPooling()
        ActorPerformanceOptimization().demonstrateActorBestPractices()
        
        println("=== Actor Patterns Summary ===")
        println("✅ Basic Actor Patterns:")
        println("   - Sequential message processing eliminates race conditions")
        println("   - State encapsulation with message-based interfaces")
        println("   - Typed messages with sealed classes for type safety")
        println("   - Request-response patterns with CompletableDeferred")
        println()
        println("✅ Actor Supervision:")
        println("   - Supervisor actors manage worker actor lifecycles")
        println("   - Fault tolerance through actor restart strategies")
        println("   - State persistence for recovery after failures")
        println("   - Error isolation prevents cascade failures")
        println()
        println("✅ Communication Patterns:")
        println("   - Request-response for synchronous-style interactions")
        println("   - Publish-subscribe for event broadcasting")
        println("   - Pipeline patterns for data processing workflows")
        println("   - Actor hierarchies for complex system organization")
        println()
        println("✅ Performance Optimization:")
        println("   - Actor pooling for parallel processing")
        println("   - Message batching for high-throughput scenarios")
        println("   - Proper lifecycle management for resource efficiency")
        println("   - Channel capacity tuning for optimal performance")
        println()
        println("✅ Best Practices:")
        println("   - Keep actor message handlers non-blocking")
        println("   - Design clear message protocols and interfaces")
        println("   - Implement proper error handling and recovery")
        println("   - Use actor pools for CPU-intensive operations")
        println("   - Monitor actor health and performance metrics")
        println()
        println("✅ Actor Model Benefits:")
        println("   - Eliminates shared mutable state problems")
        println("   - Natural concurrency and distribution boundaries")
        println("   - Simplified reasoning about concurrent systems")
        println("   - Built-in error isolation and fault tolerance")
        println("   - Scales well from single machine to distributed systems")
    }
}