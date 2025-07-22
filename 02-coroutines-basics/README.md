# Phase 2: Coroutines Basics

## 🎯 Learning Objectives

By the end of this phase, you will understand:
- How suspend functions work under the hood (continuation-passing style)
- Coroutine builders: launch, async, runBlocking
- Dispatchers and context switching
- Structured concurrency principles
- Job lifecycle and cancellation
- Performance benefits over traditional threading

## 📚 Module Structure

```
02-coroutines-basics/
├── README.md                     # This overview
├── SuspendFunctions.kt          # Suspend function mechanics
├── CoroutineBuilders.kt         # launch, async, runBlocking
├── Dispatchers.kt               # Thread dispatching
├── StructuredConcurrency.kt     # Scope and hierarchy
├── JobLifecycle.kt              # Job states and cancellation
├── ContextAndInheritance.kt     # Context propagation
├── PerformanceComparison.kt     # Coroutines vs threads
└── exercises/                   # Practice problems
    ├── Exercise1_BasicCoroutines.kt
    ├── Exercise2_Dispatchers.kt
    ├── Exercise3_StructuredConcurrency.kt
    └── solutions/
```

## 🚀 From Threads to Coroutines

### The Threading Problem (Phase 1)
```kotlin
// Expensive thread creation
thread {
    doWork() // 1-2MB stack, OS scheduling
}
```

### The Coroutine Solution (Phase 2)
```kotlin
// Lightweight coroutine
launch {
    doWork() // Few bytes, cooperative scheduling
}
```

## 🧠 Mental Model: Suspension vs Blocking

### Traditional Blocking
```
Thread ──[BLOCKED]──> (Thread idle, resources wasted)
      I/O Request      Wait for response
```

### Coroutine Suspension
```
Coroutine ──[SUSPEND]──> Other Work ──[RESUME]──> Continue
        I/O Request      Different coroutines    Response ready
```

## 🔍 Key Concepts Hierarchy

```
Coroutines Foundation
├── Suspend Functions
│   ├── Suspension points
│   ├── Continuation objects
│   └── State machines
│
├── Coroutine Builders
│   ├── launch (fire-and-forget)
│   ├── async (concurrent with result)
│   └── runBlocking (bridge to blocking world)
│
├── Dispatchers
│   ├── Dispatchers.Main (UI thread)
│   ├── Dispatchers.IO (blocking I/O)
│   ├── Dispatchers.Default (CPU-bound)
│   └── Dispatchers.Unconfined (testing)
│
└── Structured Concurrency
    ├── CoroutineScope
    ├── Job hierarchy
    └── Automatic cancellation
```

## 📖 Study Sequence

### Day 1: Suspend Functions
1. Read `SuspendFunctions.kt` - Core mechanics
2. Understand continuation-passing style transformation
3. See state machine generation examples
4. Complete Exercise 1: Basic Coroutines

### Day 2: Coroutine Builders
1. Study `CoroutineBuilders.kt` - launch vs async vs runBlocking
2. Learn when to use each builder
3. Understand structured concurrency basics
4. Practice with different builder patterns

### Day 3: Dispatchers
1. Explore `Dispatchers.kt` - Thread pool management
2. Learn dispatcher selection criteria
3. See context switching in action
4. Complete Exercise 2: Dispatchers

### Day 4: Structured Concurrency
1. Master `StructuredConcurrency.kt` - Scope and hierarchy
2. Understand parent-child relationships
3. Learn automatic cancellation propagation
4. Complete Exercise 3: Structured Concurrency

### Day 5: Jobs and Context
1. Study `JobLifecycle.kt` - Job states and cancellation
2. Learn `ContextAndInheritance.kt` - Context propagation
3. Compare `PerformanceComparison.kt` - Coroutines vs threads
4. Review and practice all concepts

## 🎯 Learning Outcomes Checklist

After completing this phase, you should be able to:

- [ ] Explain how suspend functions work under the hood
- [ ] Choose the right coroutine builder for different scenarios
- [ ] Select appropriate dispatchers for different workloads
- [ ] Create properly structured coroutine scopes
- [ ] Handle job lifecycle and cancellation correctly
- [ ] Understand context inheritance and propagation
- [ ] Measure and compare coroutine vs thread performance
- [ ] Debug common coroutine issues

## 🔄 Performance Benefits

### Memory Usage
```
Traditional Threads: 1MB+ per thread
Coroutines:         ~200 bytes per coroutine
Ratio:              ~5000:1 improvement
```

### Context Switching
```
Thread switching:    1-100 microseconds
Coroutine switching: ~10 nanoseconds  
Ratio:              ~10,000:1 improvement
```

### Scalability
```
Max Threads:    ~1,000s (OS limited)
Max Coroutines: ~100,000s (memory limited)
```

## 🔗 Connection to Phase 1

This phase directly addresses the limitations discovered in Phase 1:
- **Thread creation overhead** → Lightweight coroutines
- **Resource exhaustion** → Efficient thread reuse via dispatchers
- **Complex synchronization** → Structured concurrency
- **Manual lifecycle management** → Automatic cancellation
- **Blocking operations** → Suspending functions

## ➡️ Next Phase

Once you complete this phase, move to [Phase 3: Advanced Coroutines](../03-coroutines-advanced/README.md) where you'll learn advanced patterns like exception handling, custom scopes, and complex cancellation scenarios.

---

**Estimated Time**: 2-3 weeks (2-3 hours per day)  
**Difficulty**: Intermediate  
**Focus**: Foundation concepts for all advanced coroutine programming