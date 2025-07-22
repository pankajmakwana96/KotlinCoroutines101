# Phase 1: Threading Fundamentals

## 🎯 Learning Objectives

By the end of this phase, you will understand:
- JVM threading model and lifecycle
- Thread safety concepts and synchronization mechanisms
- Race conditions, deadlocks, and prevention strategies
- Thread pools and their management
- When to use threads vs coroutines

## 📚 Module Structure

```
01-threading-fundamentals/
├── README.md                    # This overview
├── ThreadLifecycle.kt          # Thread creation and states
├── ThreadSafety.kt             # Synchronization and safety
├── RaceConditions.kt           # Race condition examples
├── DeadlockPrevention.kt       # Deadlock scenarios and solutions
├── ThreadPools.kt              # Thread pool management
├── BlockingVsNonBlocking.kt    # Comparing approaches
├── ThreadLocalStorage.kt       # Thread-local variables
├── VolatileAndAtomic.kt        # Memory visibility
└── exercises/                  # Practice problems
    ├── Exercise1_BasicThreading.kt
    ├── Exercise2_ThreadSafety.kt
    ├── Exercise3_ThreadPools.kt
    └── solutions/
```

## 🧵 Threading Mental Model

### Thread Lifecycle Visualization
```
NEW ────start()────> RUNNABLE ────OS Scheduler────> RUNNING
 │                        │                           │
 │                        │                           │
 └─ GC ──────────────────┘                           │
                                                      │
TERMINATED <────────────────────────────────────────┘
     │                                               │
     └─ run() completes                              │
                                                     │
BLOCKED <───── synchronized block/wait() ────────────┘
     │                                               │
WAITING <───── wait()/join()/park() ──────────────────┘
     │                                               │
TIMED_WAITING <─ sleep()/wait(timeout) ──────────────┘
```

### Memory Model Overview
```
┌─────────────────┐    ┌─────────────────┐
│   Thread 1      │    │   Thread 2      │
│                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │Local Cache  │ │    │ │Local Cache  │ │
│ │   var a=1   │ │    │ │   var a=?   │ │
│ └─────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └─────────────────┘
         │                       │
         └───────────────────────┘
                     │
              ┌─────────────┐
              │Main Memory  │
              │   var a=?   │
              └─────────────┘
```

## 🔍 Key Concepts

### 1. Thread Creation Methods
- Extending Thread class
- Implementing Runnable interface  
- Using Executor framework
- Kotlin-specific approaches

### 2. Synchronization Mechanisms
- `synchronized` blocks and methods
- `volatile` keyword for visibility
- `AtomicInteger` and atomic operations
- `ReentrantLock` for advanced locking

### 3. Thread Communication
- `wait()` and `notify()` mechanisms
- `CountDownLatch` for coordination
- `Semaphore` for resource limiting
- `CyclicBarrier` for synchronization points

### 4. Common Problems
- **Race Conditions**: Multiple threads accessing shared data
- **Deadlocks**: Circular waiting for resources
- **Starvation**: Threads never getting CPU time
- **Livelock**: Threads responding to each other indefinitely

## 📖 Study Sequence

### Day 1: Thread Basics
1. Read `ThreadLifecycle.kt` - Understand thread states
2. Run examples and observe thread behavior
3. Complete Exercise 1: Basic Threading

### Day 2: Thread Safety
1. Study `ThreadSafety.kt` - Synchronization mechanisms
2. Analyze `RaceConditions.kt` - See problems firsthand
3. Complete Exercise 2: Thread Safety

### Day 3: Advanced Concepts
1. Explore `DeadlockPrevention.kt` - Deadlock scenarios
2. Learn `ThreadPools.kt` - Efficient thread management
3. Complete Exercise 3: Thread Pools

### Day 4: Memory Model
1. Study `VolatileAndAtomic.kt` - Memory visibility
2. Understand `ThreadLocalStorage.kt` - Per-thread data
3. Review `BlockingVsNonBlocking.kt` - Compare approaches

### Day 5: Practice & Review
1. Work through all exercises
2. Compare threading vs coroutine approaches
3. Prepare for Phase 2: Coroutines

## 🎯 Learning Outcomes Checklist

After completing this phase, you should be able to:

- [ ] Explain the JVM threading model
- [ ] Identify thread safety issues in code
- [ ] Choose appropriate synchronization mechanisms
- [ ] Prevent common threading problems (deadlocks, race conditions)
- [ ] Use thread pools effectively
- [ ] Understand memory visibility issues
- [ ] Compare blocking vs non-blocking operations
- [ ] Recognize when coroutines are a better choice

## 🔗 Prerequisites Covered

This phase establishes the foundation for understanding why coroutines were created and how they solve threading limitations. You'll see firsthand the complexity of manual thread management that coroutines eliminate.

## ➡️ Next Phase

Once you complete this phase, move to [Phase 2: Coroutines Basics](../02-coroutines-basics/README.md) where you'll see how Kotlin coroutines provide elegant solutions to the threading challenges explored here.

---

**Estimated Time**: 1-2 weeks (1-2 hours per day)  
**Difficulty**: Beginner to Intermediate  
**Focus**: Understanding problems that coroutines solve