/**
 * # Memory and Resource Optimization for Coroutines
 * 
 * ## Problem Description
 * Memory and resource management in coroutine applications requires careful attention
 * to allocation patterns, garbage collection pressure, resource pooling, and lifecycle
 * management. Poor resource management can lead to memory leaks, excessive GC overhead,
 * resource exhaustion, and degraded application performance. Coroutines create unique
 * challenges due to their lightweight nature, potential for creating millions of
 * instances, and complex cleanup requirements in structured concurrency hierarchies.
 * 
 * ## Solution Approach
 * Resource optimization strategies include:
 * - Implementing efficient object pooling and resource reuse patterns
 * - Minimizing allocation overhead through careful data structure selection
 * - Optimizing garbage collection through allocation pattern improvements
 * - Managing coroutine lifecycles to prevent resource leaks
 * - Using memory-mapped files and off-heap storage for large datasets
 * 
 * ## Key Learning Points
 * - Object pooling reduces allocation overhead by 80-95%
 * - Proper resource cleanup prevents memory leaks and resource exhaustion
 * - GC-friendly allocation patterns improve overall application performance
 * - Memory-mapped files enable efficient handling of large datasets
 * - Resource monitoring and alerting prevent production issues
 * 
 * ## Performance Considerations
 * - Object allocation cost: ~50-200ns per small object
 * - GC pause impact: 1-100ms depending on heap size and generation
 * - Memory overhead: 8-24 bytes per object header (JVM dependent)
 * - Pool lookup cost: ~5-20ns for efficient implementations
 * - Memory mapped I/O: 2-10x faster than traditional I/O for large files
 * 
 * ## Common Pitfalls
 * - Creating excessive short-lived objects in hot paths
 * - Forgetting to return pooled objects after use
 * - Not cleaning up resources in exception scenarios
 * - Ignoring GC pressure from allocation patterns
 * - Memory leaks from uncancelled coroutines holding references
 * 
 * ## Real-World Applications
 * - High-throughput message processing systems
 * - Large-scale data processing and ETL pipelines
 * - Real-time systems with strict latency requirements
 * - Memory-constrained environments (mobile, embedded)
 * - Long-running server applications with stability requirements
 * 
 * ## Related Concepts
 * - Garbage collection tuning and heap management
 * - Off-heap storage solutions and memory mapping
 * - Resource pooling patterns and lifecycle management
 * - Memory profiling and leak detection techniques
 */

package performance.optimization.memory

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlin.system.*
import kotlin.time.*
import java.lang.management.ManagementFactory
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

/**
 * Object pooling and resource reuse optimization patterns
 * 
 * Resource Management Hierarchy:
 * 
 * Memory Optimization Levels:
 * ├── Object Pooling ─────── Reuse expensive objects (ByteBuffers, Connections)
 * ├── Allocation Reduction ── Minimize new object creation in hot paths
 * ├── GC Optimization ────── Reduce garbage collection pressure
 * ├── Memory Mapping ─────── Use OS virtual memory for large data
 * └── Off-Heap Storage ───── Store data outside JVM heap
 * 
 * Optimization Priority:
 * Hot Path Allocation > Object Pooling > GC Tuning > Memory Mapping > Off-Heap
 */
class ObjectPoolingOptimization {
    
    // Generic object pool with lifecycle management
    class ObjectPool<T>(
        private val factory: () -> T,
        private val reset: (T) -> Unit = {},
        private val validate: (T) -> Boolean = { true },
        private val maxSize: Int = 100,
        private val maxIdleTime: Long = 60000 // 1 minute
    ) {
        private val pool = ConcurrentLinkedQueue<PooledObject<T>>()
        private val borrowed = AtomicInteger(0)
        private val created = AtomicInteger(0)
        
        private data class PooledObject<T>(
            val obj: T,
            val lastUsed: Long = System.currentTimeMillis()
        )
        
        suspend fun borrow(): T {
            // Try to get from pool first
            var pooledObj = pool.poll()
            
            // Clean up stale objects
            while (pooledObj != null && !isValid(pooledObj)) {
                pooledObj = pool.poll()
            }
            
            val obj = if (pooledObj != null) {
                pooledObj.obj
            } else {
                factory().also { created.incrementAndGet() }
            }
            
            borrowed.incrementAndGet()
            return obj
        }
        
        suspend fun return(obj: T) {
            borrowed.decrementAndGet()
            
            if (validate(obj) && pool.size < maxSize) {
                reset(obj)
                pool.offer(PooledObject(obj))
            }
        }
        
        suspend fun <R> use(action: suspend (T) -> R): R {
            val obj = borrow()
            return try {
                action(obj)
            } finally {
                return(obj)
            }
        }
        
        private fun isValid(pooledObj: PooledObject<T>): Boolean {
            val age = System.currentTimeMillis() - pooledObj.lastUsed
            return age <= maxIdleTime && validate(pooledObj.obj)
        }
        
        fun getStats(): PoolStats {
            return PoolStats(
                poolSize = pool.size,
                borrowedCount = borrowed.get(),
                createdCount = created.get()
            )
        }
        
        data class PoolStats(
            val poolSize: Int,
            val borrowedCount: Int,
            val createdCount: Int
        )
    }
    
    // Specialized ByteBuffer pool for high-performance I/O
    class ByteBufferPool(
        private val bufferSize: Int = 8192,
        private val maxPoolSize: Int = 100,
        private val isDirect: Boolean = true
    ) {
        private val pool = ConcurrentLinkedQueue<ByteBuffer>()
        private val allocated = AtomicLong(0)
        private val poolHits = AtomicLong(0)
        private val poolMisses = AtomicLong(0)
        
        fun acquire(): ByteBuffer {
            val buffer = pool.poll()
            
            return if (buffer != null) {
                poolHits.incrementAndGet()
                buffer.clear()
                buffer
            } else {
                poolMisses.incrementAndGet()
                allocated.addAndGet(bufferSize.toLong())
                
                if (isDirect) {
                    ByteBuffer.allocateDirect(bufferSize)
                } else {
                    ByteBuffer.allocate(bufferSize)
                }
            }
        }
        
        fun release(buffer: ByteBuffer) {
            if (pool.size < maxPoolSize) {
                pool.offer(buffer)
            } else {
                // Buffer will be GC'd, subtract from allocated
                allocated.addAndGet(-bufferSize.toLong())
            }
        }
        
        fun getMetrics(): BufferPoolMetrics {
            val totalRequests = poolHits.get() + poolMisses.get()
            val hitRate = if (totalRequests > 0) poolHits.get().toDouble() / totalRequests else 0.0
            
            return BufferPoolMetrics(
                poolSize = pool.size,
                allocatedBytes = allocated.get(),
                hitRate = hitRate,
                totalRequests = totalRequests
            )
        }
        
        data class BufferPoolMetrics(
            val poolSize: Int,
            val allocatedBytes: Long,
            val hitRate: Double,
            val totalRequests: Long
        )
    }
    
    // Connection pool for database/network resources
    class ConnectionPool<T>(
        private val factory: suspend () -> T,
        private val destroyer: suspend (T) -> Unit,
        private val validator: suspend (T) -> Boolean,
        private val maxConnections: Int = 20,
        private val acquireTimeout: Duration = 30.seconds
    ) {
        private val connections = Channel<T>(maxConnections)
        private val activeCount = AtomicInteger(0)
        
        suspend fun initialize() {
            repeat(maxConnections / 2) { // Pre-create half the pool
                val connection = factory()
                connections.send(connection)
            }
        }
        
        suspend fun acquire(): T = withTimeout(acquireTimeout) {
            val connection = connections.tryReceive().getOrNull()
            
            if (connection != null && validator(connection)) {
                activeCount.incrementAndGet()
                connection
            } else {
                // Create new connection if pool is empty or connection is invalid
                if (connection != null) {
                    destroyer(connection) // Clean up invalid connection
                }
                
                val newConnection = factory()
                activeCount.incrementAndGet()
                newConnection
            }
        }
        
        suspend fun release(connection: T) {
            activeCount.decrementAndGet()
            
            if (validator(connection)) {
                connections.trySend(connection)
            } else {
                destroyer(connection)
            }
        }
        
        suspend fun <R> use(action: suspend (T) -> R): R {
            val connection = acquire()
            return try {
                action(connection)
            } finally {
                release(connection)
            }
        }
        
        suspend fun shutdown() {
            connections.close()
            
            // Clean up remaining connections
            for (connection in connections) {
                destroyer(connection)
            }
        }
        
        fun getStats(): ConnectionPoolStats {
            return ConnectionPoolStats(
                activeConnections = activeCount.get(),
                availableConnections = connections.capacity - activeCount.get()
            )
        }
        
        data class ConnectionPoolStats(
            val activeConnections: Int,
            val availableConnections: Int
        )
    }
    
    suspend fun demonstrateObjectPooling() {
        println("=== Object Pooling Performance ===")
        
        val iterations = 10000
        
        // Test 1: Without pooling (creates new objects every time)
        data class ExpensiveObject(val data: ByteArray = ByteArray(1024))
        
        val withoutPoolingTime = measureTime {
            coroutineScope {
                repeat(iterations) {
                    launch {
                        val obj = ExpensiveObject()
                        // Simulate work
                        obj.data[0] = it.toByte()
                    }
                }
            }
        }
        
        // Test 2: With object pooling
        val objectPool = ObjectPool(
            factory = { ExpensiveObject() },
            reset = { it.data.fill(0) },
            maxSize = 50
        )
        
        val withPoolingTime = measureTime {
            coroutineScope {
                repeat(iterations) { i ->
                    launch {
                        objectPool.use { obj ->
                            obj.data[0] = i.toByte()
                        }
                    }
                }
            }
        }
        
        val poolStats = objectPool.getStats()
        
        println("Without pooling: ${withoutPoolingTime.inWholeMilliseconds}ms")
        println("With pooling: ${withPoolingTime.inWholeMilliseconds}ms")
        println("Pool stats: $poolStats")
        
        val improvement = if (withPoolingTime > Duration.ZERO) {
            withoutPoolingTime.inWholeNanoseconds.toDouble() / withPoolingTime.inWholeNanoseconds
        } else 1.0
        
        println("Pooling improvement: ${String.format("%.1f", improvement)}x")
        
        println("✅ Object pooling demo completed")
    }
    
    suspend fun demonstrateByteBufferPooling() {
        println("=== ByteBuffer Pooling Performance ===")
        
        val iterations = 5000
        val bufferSize = 8192
        
        // Test without pooling
        val withoutPoolingTime = measureTime {
            coroutineScope {
                repeat(iterations) {
                    launch {
                        val buffer = ByteBuffer.allocateDirect(bufferSize)
                        // Simulate I/O work
                        buffer.putInt(it)
                        buffer.flip()
                        buffer.getInt()
                    }
                }
            }
        }
        
        // Test with pooling
        val bufferPool = ByteBufferPool(bufferSize, maxPoolSize = 100)
        
        val withPoolingTime = measureTime {
            coroutineScope {
                repeat(iterations) { i ->
                    launch {
                        val buffer = bufferPool.acquire()
                        try {
                            // Simulate I/O work
                            buffer.putInt(i)
                            buffer.flip()
                            buffer.getInt()
                        } finally {
                            bufferPool.release(buffer)
                        }
                    }
                }
            }
        }
        
        val metrics = bufferPool.getMetrics()
        
        println("Without buffer pooling: ${withoutPoolingTime.inWholeMilliseconds}ms")
        println("With buffer pooling: ${withPoolingTime.inWholeMilliseconds}ms")
        println("Buffer pool metrics: $metrics")
        
        val improvement = if (withPoolingTime > Duration.ZERO) {
            withoutPoolingTime.inWholeNanoseconds.toDouble() / withPoolingTime.inWholeNanoseconds
        } else 1.0
        
        println("Buffer pooling improvement: ${String.format("%.1f", improvement)}x")
        
        println("✅ ByteBuffer pooling demo completed")
    }
    
    suspend fun demonstrateConnectionPooling() {
        println("=== Connection Pooling Demo ===")
        
        // Simulate database connections
        data class DatabaseConnection(val id: Int, var isValid: Boolean = true)
        
        val connectionPool = ConnectionPool<DatabaseConnection>(
            factory = { 
                delay(10) // Simulate connection creation overhead
                DatabaseConnection(Random.nextInt())
            },
            destroyer = { connection ->
                delay(5) // Simulate cleanup
                connection.isValid = false
            },
            validator = { it.isValid },
            maxConnections = 10
        )
        
        connectionPool.initialize()
        
        val operations = 100
        val connectionTime = measureTime {
            coroutineScope {
                repeat(operations) { i ->
                    launch {
                        connectionPool.use { connection ->
                            // Simulate database operation
                            delay(Random.nextLong(1, 10))
                            
                            // Occasionally invalidate connection to test cleanup
                            if (i % 20 == 0) {
                                connection.isValid = false
                            }
                        }
                    }
                }
            }
        }
        
        val stats = connectionPool.getStats()
        connectionPool.shutdown()
        
        println("Connection pool operations: ${operations}")
        println("Total time: ${connectionTime.inWholeMilliseconds}ms")
        println("Average time per operation: ${connectionTime.inWholeMilliseconds / operations}ms")
        println("Final pool stats: $stats")
        
        println("✅ Connection pooling demo completed")
    }
}

/**
 * Memory allocation reduction and garbage collection optimization
 */
class GarbageCollectionOptimization {
    
    // Memory pressure monitor
    class MemoryMonitor {
        private val memoryBean = ManagementFactory.getMemoryMXBean()
        private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        
        fun getMemoryStats(): MemoryStats {
            val heapUsage = memoryBean.heapMemoryUsage
            val nonHeapUsage = memoryBean.nonHeapMemoryUsage
            
            val gcStats = gcBeans.map { bean ->
                GCStats(
                    name = bean.name,
                    collectionCount = bean.collectionCount,
                    collectionTime = bean.collectionTime
                )
            }
            
            return MemoryStats(
                heapUsed = heapUsage.used,
                heapMax = heapUsage.max,
                heapCommitted = heapUsage.committed,
                nonHeapUsed = nonHeapUsage.used,
                gcStats = gcStats
            )
        }
        
        data class MemoryStats(
            val heapUsed: Long,
            val heapMax: Long,
            val heapCommitted: Long,
            val nonHeapUsed: Long,
            val gcStats: List<GCStats>
        ) {
            val heapUtilization: Double get() = heapUsed.toDouble() / heapMax
        }
        
        data class GCStats(
            val name: String,
            val collectionCount: Long,
            val collectionTime: Long
        )
    }
    
    // Allocation-free data processing
    class AllocationFreeProcessor {
        private val reusableBuffer = ByteArray(8192)
        private val stringBuilder = StringBuilder(1000)
        
        suspend fun processDataWithAllocation(data: List<String>): List<String> {
            return data.map { item ->
                "processed-${item.uppercase()}-${System.currentTimeMillis()}"
            }
        }
        
        suspend fun processDataWithoutAllocation(data: List<String>, result: MutableList<String>) {
            data.forEach { item ->
                stringBuilder.clear()
                stringBuilder.append("processed-")
                stringBuilder.append(item.uppercase())
                stringBuilder.append("-")
                stringBuilder.append(System.currentTimeMillis())
                
                result.add(stringBuilder.toString())
            }
        }
        
        suspend fun processStreamWithBuffering(input: Flow<ByteArray>): Flow<String> = flow {
            var bufferPosition = 0
            
            input.collect { chunk ->
                if (bufferPosition + chunk.size <= reusableBuffer.size) {
                    // Copy to reusable buffer
                    chunk.copyInto(reusableBuffer, bufferPosition)
                    bufferPosition += chunk.size
                } else {
                    // Process current buffer and start new one
                    if (bufferPosition > 0) {
                        emit(String(reusableBuffer, 0, bufferPosition))
                        bufferPosition = 0
                    }
                    
                    if (chunk.size <= reusableBuffer.size) {
                        chunk.copyInto(reusableBuffer, 0)
                        bufferPosition = chunk.size
                    } else {
                        // Chunk too large for buffer, process directly
                        emit(String(chunk))
                    }
                }
            }
            
            // Emit remaining data
            if (bufferPosition > 0) {
                emit(String(reusableBuffer, 0, bufferPosition))
            }
        }
    }
    
    // Bulk allocation optimizer
    class BulkAllocationOptimizer<T> {
        fun allocateInBulk(count: Int, factory: (Int) -> T): List<T> {
            // Pre-allocate list to avoid resizing
            val result = ArrayList<T>(count)
            
            repeat(count) { i ->
                result.add(factory(i))
            }
            
            return result
        }
        
        fun processInBatches(
            items: List<T>,
            batchSize: Int,
            processor: (List<T>) -> Unit
        ) {
            var index = 0
            while (index < items.size) {
                val endIndex = minOf(index + batchSize, items.size)
                val batch = items.subList(index, endIndex)
                processor(batch)
                index = endIndex
            }
        }
    }
    
    suspend fun demonstrateAllocationReduction() {
        println("=== Allocation Reduction Performance ===")
        
        val monitor = MemoryMonitor()
        val processor = AllocationFreeProcessor()
        
        val testData = (1..10000).map { "item-$it" }
        
        // Test with allocation
        val beforeAllocation = monitor.getMemoryStats()
        
        val allocationTime = measureTime {
            repeat(100) {
                processor.processDataWithAllocation(testData)
            }
        }
        
        val afterAllocation = monitor.getMemoryStats()
        
        // Force GC to see allocation impact
        System.gc()
        Thread.sleep(100)
        
        // Test without allocation
        val beforeOptimized = monitor.getMemoryStats()
        val result = mutableListOf<String>()
        
        val optimizedTime = measureTime {
            repeat(100) {
                result.clear()
                processor.processDataWithoutAllocation(testData, result)
            }
        }
        
        val afterOptimized = monitor.getMemoryStats()
        
        println("With allocation: ${allocationTime.inWholeMilliseconds}ms")
        println("Without allocation: ${optimizedTime.inWholeMilliseconds}ms")
        
        val allocatedDuring = afterAllocation.heapUsed - beforeAllocation.heapUsed
        val optimizedAllocated = afterOptimized.heapUsed - beforeOptimized.heapUsed
        
        println("Memory allocated (with): ${allocatedDuring / 1024}KB")
        println("Memory allocated (without): ${optimizedAllocated / 1024}KB")
        
        val gcDiff = afterAllocation.gcStats.zip(beforeAllocation.gcStats) { after, before ->
            after.collectionCount - before.collectionCount
        }.sum()
        
        println("GC collections during allocation test: $gcDiff")
        
        println("✅ Allocation reduction demo completed")
    }
    
    suspend fun demonstrateStreamBuffering() {
        println("=== Stream Buffering Optimization ===")
        
        val processor = AllocationFreeProcessor()
        val monitor = MemoryMonitor()
        
        // Create test stream
        val testStream = flow {
            repeat(1000) { i ->
                emit("chunk-$i-${Random.nextInt(1000)}".toByteArray())
                if (i % 100 == 0) yield() // Allow monitoring
            }
        }
        
        val beforeBuffering = monitor.getMemoryStats()
        
        val bufferingTime = measureTime {
            processor.processStreamWithBuffering(testStream)
                .collect { processed ->
                    // Simulate processing each chunk
                }
        }
        
        val afterBuffering = monitor.getMemoryStats()
        
        println("Stream buffering time: ${bufferingTime.inWholeMilliseconds}ms")
        println("Memory usage before: ${beforeBuffering.heapUsed / 1024}KB")
        println("Memory usage after: ${afterBuffering.heapUsed / 1024}KB")
        println("Memory increase: ${(afterBuffering.heapUsed - beforeBuffering.heapUsed) / 1024}KB")
        
        println("✅ Stream buffering demo completed")
    }
    
    suspend fun demonstrateBulkAllocation() {
        println("=== Bulk Allocation Optimization ===")
        
        val optimizer = BulkAllocationOptimizer<String>()
        val monitor = MemoryMonitor()
        val itemCount = 100000
        
        // Individual allocation
        val beforeIndividual = monitor.getMemoryStats()
        
        val individualTime = measureTime {
            val result = mutableListOf<String>()
            repeat(itemCount) { i ->
                result.add("item-$i-${Random.nextInt()}")
            }
        }
        
        val afterIndividual = monitor.getMemoryStats()
        
        // Bulk allocation
        System.gc() // Clean up before bulk test
        Thread.sleep(100)
        
        val beforeBulk = monitor.getMemoryStats()
        
        val bulkTime = measureTime {
            optimizer.allocateInBulk(itemCount) { i ->
                "item-$i-${Random.nextInt()}"
            }
        }
        
        val afterBulk = monitor.getMemoryStats()
        
        println("Individual allocation: ${individualTime.inWholeMilliseconds}ms")
        println("Bulk allocation: ${bulkTime.inWholeMilliseconds}ms")
        
        val individualGC = afterIndividual.gcStats.zip(beforeIndividual.gcStats) { after, before ->
            after.collectionCount - before.collectionCount
        }.sum()
        
        val bulkGC = afterBulk.gcStats.zip(beforeBulk.gcStats) { after, before ->
            after.collectionCount - before.collectionCount
        }.sum()
        
        println("GC during individual allocation: $individualGC")
        println("GC during bulk allocation: $bulkGC")
        
        val improvement = if (bulkTime > Duration.ZERO) {
            individualTime.inWholeNanoseconds.toDouble() / bulkTime.inWholeNanoseconds
        } else 1.0
        
        println("Bulk allocation improvement: ${String.format("%.1f", improvement)}x")
        
        println("✅ Bulk allocation demo completed")
    }
}

/**
 * Memory mapping and off-heap storage optimization
 */
class MemoryMappingOptimization {
    
    // Memory-mapped file processor
    class MemoryMappedProcessor(private val filePath: Path) {
        private var fileChannel: FileChannel? = null
        private var mappedBuffer: MappedByteBuffer? = null
        
        suspend fun initializeMapping(size: Long) {
            fileChannel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            )
            
            // Ensure file has required size
            if (fileChannel!!.size() < size) {
                fileChannel!!.position(size - 1)
                fileChannel!!.write(ByteBuffer.wrap(byteArrayOf(0)))
            }
            
            mappedBuffer = fileChannel!!.map(FileChannel.MapMode.READ_WRITE, 0, size)
        }
        
        fun writeData(offset: Long, data: ByteArray) {
            mappedBuffer?.position(offset.toInt())
            mappedBuffer?.put(data)
        }
        
        fun readData(offset: Long, length: Int): ByteArray {
            val buffer = ByteArray(length)
            mappedBuffer?.position(offset.toInt())
            mappedBuffer?.get(buffer)
            return buffer
        }
        
        suspend fun close() {
            mappedBuffer = null // Unmap
            fileChannel?.close()
        }
        
        fun getStats(): MappingStats {
            return MappingStats(
                fileSize = fileChannel?.size() ?: 0,
                mappedSize = mappedBuffer?.capacity()?.toLong() ?: 0,
                position = mappedBuffer?.position()?.toLong() ?: 0
            )
        }
        
        data class MappingStats(
            val fileSize: Long,
            val mappedSize: Long,
            val position: Long
        )
    }
    
    // Off-heap cache implementation using memory mapping
    class OffHeapCache<K, V>(
        private val maxEntries: Int = 10000,
        private val entrySize: Int = 1024
    ) where K : Any, V : Any {
        
        private val tempFile = Files.createTempFile("offheap-cache", ".dat")
        private val mappedProcessor = MemoryMappedProcessor(tempFile)
        private val keyToOffset = ConcurrentHashMap<K, Long>()
        private val nextOffset = AtomicLong(0)
        
        suspend fun initialize() {
            val totalSize = maxEntries.toLong() * entrySize
            mappedProcessor.initializeMapping(totalSize)
        }
        
        suspend fun put(key: K, value: V) {
            val serializedValue = serializeValue(value)
            val offset = nextOffset.getAndAdd(entrySize.toLong())
            
            mappedProcessor.writeData(offset, serializedValue)
            keyToOffset[key] = offset
        }
        
        suspend fun get(key: K): V? {
            val offset = keyToOffset[key] ?: return null
            val data = mappedProcessor.readData(offset, entrySize)
            return deserializeValue(data)
        }
        
        private fun serializeValue(value: V): ByteArray {
            // Simplified serialization - in real implementation use proper serialization
            val valueStr = value.toString()
            val bytes = valueStr.toByteArray()
            return ByteArray(entrySize).also { array ->
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                lengthBytes.copyInto(array, 0)
                bytes.copyInto(array, 4, 0, minOf(bytes.size, entrySize - 4))
            }
        }
        
        private fun deserializeValue(data: ByteArray): V? {
            try {
                val length = ByteBuffer.wrap(data, 0, 4).int
                val valueBytes = data.copyOfRange(4, 4 + length)
                @Suppress("UNCHECKED_CAST")
                return String(valueBytes) as V
            } catch (e: Exception) {
                return null
            }
        }
        
        suspend fun close() {
            mappedProcessor.close()
            Files.deleteIfExists(tempFile)
        }
        
        fun getStats(): CacheStats {
            val mappingStats = mappedProcessor.getStats()
            return CacheStats(
                entryCount = keyToOffset.size,
                memoryUsed = nextOffset.get(),
                fileSize = mappingStats.fileSize
            )
        }
        
        data class CacheStats(
            val entryCount: Int,
            val memoryUsed: Long,
            val fileSize: Long
        )
    }
    
    suspend fun demonstrateMemoryMapping() {
        println("=== Memory Mapping Performance ===")
        
        val testFile = Files.createTempFile("memory-mapping-test", ".dat")
        val fileSize = 10L * 1024 * 1024 // 10MB
        val chunkSize = 1024
        val chunks = (fileSize / chunkSize).toInt()
        
        try {
            // Test 1: Traditional file I/O
            val traditionalTime = measureTime {
                Files.newByteChannel(testFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                    val buffer = ByteBuffer.allocate(chunkSize)
                    
                    repeat(chunks) { i ->
                        buffer.clear()
                        buffer.putInt(i)
                        buffer.flip()
                        channel.write(buffer)
                    }
                }
            }
            
            // Test 2: Memory-mapped I/O
            val mappedProcessor = MemoryMappedProcessor(testFile)
            mappedProcessor.initializeMapping(fileSize)
            
            val mappedTime = measureTime {
                repeat(chunks) { i ->
                    val data = ByteBuffer.allocate(4).putInt(i).array()
                    mappedProcessor.writeData(i.toLong() * chunkSize, data)
                }
            }
            
            val stats = mappedProcessor.getStats()
            mappedProcessor.close()
            
            println("Traditional I/O: ${traditionalTime.inWholeMilliseconds}ms")
            println("Memory-mapped I/O: ${mappedTime.inWholeMilliseconds}ms")
            println("Mapping stats: $stats")
            
            val improvement = if (mappedTime > Duration.ZERO) {
                traditionalTime.inWholeNanoseconds.toDouble() / mappedTime.inWholeNanoseconds
            } else 1.0
            
            println("Memory mapping improvement: ${String.format("%.1f", improvement)}x")
            
        } finally {
            Files.deleteIfExists(testFile)
        }
        
        println("✅ Memory mapping demo completed")
    }
    
    suspend fun demonstrateOffHeapCache() {
        println("=== Off-Heap Cache Performance ===")
        
        val cacheSize = 10000
        val cache = OffHeapCache<String, String>(maxEntries = cacheSize)
        cache.initialize()
        
        try {
            // Fill cache
            val fillTime = measureTime {
                coroutineScope {
                    repeat(cacheSize) { i ->
                        launch {
                            cache.put("key-$i", "value-$i-${Random.nextInt(10000)}")
                        }
                    }
                }
            }
            
            // Read from cache
            val readTime = measureTime {
                coroutineScope {
                    repeat(cacheSize) { i ->
                        launch {
                            cache.get("key-$i")
                        }
                    }
                }
            }
            
            val stats = cache.getStats()
            
            println("Cache fill time: ${fillTime.inWholeMilliseconds}ms")
            println("Cache read time: ${readTime.inWholeMilliseconds}ms")
            println("Cache stats: $stats")
            
            val fillThroughput = cacheSize / fillTime.inWholeSeconds.toDouble()
            val readThroughput = cacheSize / readTime.inWholeSeconds.toDouble()
            
            println("Fill throughput: ${String.format("%.0f", fillThroughput)} ops/sec")
            println("Read throughput: ${String.format("%.0f", readThroughput)} ops/sec")
            
        } finally {
            cache.close()
        }
        
        println("✅ Off-heap cache demo completed")
    }
    
    suspend fun demonstrateLargeDataProcessing() {
        println("=== Large Data Processing with Memory Mapping ===")
        
        val dataSize = 50L * 1024 * 1024 // 50MB
        val recordSize = 100
        val recordCount = (dataSize / recordSize).toInt()
        
        val testFile = Files.createTempFile("large-data-test", ".dat")
        
        try {
            // Create test data
            val mappedProcessor = MemoryMappedProcessor(testFile)
            mappedProcessor.initializeMapping(dataSize)
            
            val writeTime = measureTime {
                repeat(recordCount) { i ->
                    val record = "Record-$i".padEnd(recordSize, ' ').take(recordSize).toByteArray()
                    mappedProcessor.writeData(i.toLong() * recordSize, record)
                }
            }
            
            // Process data sequentially
            val processTime = measureTime {
                var processedCount = 0
                repeat(recordCount) { i ->
                    val data = mappedProcessor.readData(i.toLong() * recordSize, recordSize)
                    if (data.isNotEmpty()) {
                        processedCount++
                    }
                }
                println("Processed $processedCount records")
            }
            
            val stats = mappedProcessor.getStats()
            mappedProcessor.close()
            
            println("Data write time: ${writeTime.inWholeMilliseconds}ms")
            println("Data process time: ${processTime.inWholeMilliseconds}ms")
            println("Final stats: $stats")
            
            val writeThroughput = recordCount / writeTime.inWholeSeconds.toDouble()
            val processThroughput = recordCount / processTime.inWholeSeconds.toDouble()
            
            println("Write throughput: ${String.format("%.0f", writeThroughput)} records/sec")
            println("Process throughput: ${String.format("%.0f", processThroughput)} records/sec")
            
        } finally {
            Files.deleteIfExists(testFile)
        }
        
        println("✅ Large data processing demo completed")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("=== Memory and Resource Optimization Demo ===")
    
    try {
        // Object pooling optimization
        val pooling = ObjectPoolingOptimization()
        pooling.demonstrateObjectPooling()
        println()
        
        pooling.demonstrateByteBufferPooling()
        println()
        
        pooling.demonstrateConnectionPooling()
        println()
        
        // Garbage collection optimization
        val gcOptimization = GarbageCollectionOptimization()
        gcOptimization.demonstrateAllocationReduction()
        println()
        
        gcOptimization.demonstrateStreamBuffering()
        println()
        
        gcOptimization.demonstrateBulkAllocation()
        println()
        
        // Memory mapping optimization
        val memoryMapping = MemoryMappingOptimization()
        memoryMapping.demonstrateMemoryMapping()
        println()
        
        memoryMapping.demonstrateOffHeapCache()
        println()
        
        memoryMapping.demonstrateLargeDataProcessing()
        println()
        
        println("=== All Memory Optimization Demos Completed ===")
        println()
        
        println("Key Memory Optimization Concepts:")
        println("✅ Object pooling for expensive resource reuse")
        println("✅ ByteBuffer pooling for high-performance I/O")
        println("✅ Connection pooling for network and database resources")
        println("✅ Allocation reduction through buffer reuse")
        println("✅ GC pressure reduction via allocation patterns")
        println("✅ Memory mapping for efficient large file access")
        println("✅ Off-heap storage to reduce GC overhead")
        println("✅ Bulk allocation strategies for improved performance")
        println()
        
        println("Memory Optimization Guidelines:")
        println("✅ Profile allocation patterns and identify hot paths")
        println("✅ Use object pools for expensive or frequently created objects")
        println("✅ Prefer buffer reuse over allocation in tight loops")
        println("✅ Consider memory mapping for large dataset processing")
        println("✅ Monitor GC pressure and optimize allocation patterns")
        println("✅ Implement proper resource lifecycle management")
        println("✅ Use off-heap storage for large caches")
        println("✅ Batch operations to reduce overhead and improve efficiency")
        
    } catch (e: Exception) {
        println("❌ Demo failed with error: ${e.message}")
        e.printStackTrace()
    }
}