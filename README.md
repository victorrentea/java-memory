# Java Memory Leaks Training Lab

A comprehensive collection of **24 Java memory leak examples** for hands-on workshop training. Each leak demonstrates a real-world pattern that causes memory issues in production Java applications.

## Quick Start

```bash
mvn spring-boot:run
# Open http://localhost:8080
```

Requires: Java 21+, Maven 3.8+

## Heap Dump Analysis

Take a heap dump while the app is running:
```bash
jmap -dump:format=b,file=heap.hprof $(jps -l | grep MemoryApp | awk '{print $1}')
```

Open in Eclipse MAT (Memory Analyzer Tool) or use the MAT MCP server in `mat-mcp/` for AI-assisted analysis.

JVM flags for automatic dumps on OutOfMemoryError:
```
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
```

---

## Leak Catalog

### Category: Object Retention on Stack/Scope

#### Leak 1 — Long Stack Frame (`GET /leak1`)
A 100MB DTO is fetched from an external API. Only a small field is needed, but the entire DTO stays on the stack during a 30-second processing step.

**Root cause:** Large objects held in local variables longer than necessary.
**Fix:** Extract only what you need into a smaller object. Call external APIs through Adapter classes that return minimal DTOs.

#### Leak 2 — Inner Class Reference (`GET /leak2/inner`, `/implem`, `/subclass`)
A `Calculator` inner class implicitly holds a reference to its enclosing `CalculatorFactory`, which owns a 20MB field. Even though only `Calculator` is passed around, the factory (and its 20MB) stays alive.

**Root cause:** Non-static inner classes hold an implicit `this$0` reference to the enclosing instance. Anonymous classes (`new Interface() {}`) and double-brace initialization (`new HashMap<>() {{ put(...); }}`) do the same.
**Fix:** Use `static` nested classes. Move classes to separate `.java` files. Prefer lambdas over anonymous classes (lambdas only capture what they use, not the whole enclosing instance).

**MAT finding:** `path_to_gc_roots` shows: `Big20MB -> CalculatorFactory -> Calculator -> Thread`

#### Leak 10 — Shutdown Hook (`GET /leak10`)
A library registers a shutdown hook via `Runtime.addShutdownHook()` that captures a 20MB object. Shutdown hooks are held by the JVM until process exit.

**Root cause:** Shutdown hooks are GC roots — anything they reference lives forever.
**Fix:** Avoid libraries that register shutdown hooks in server-side apps. If unavoidable, use reflection to clear the hooks.

**MAT finding:** `dominator_tree` shows `java.lang.Class` retaining 105MB (76.9% of heap).

---

### Category: Collections & Data Structures

#### Leak 3 — SubList View (`GET /leak3`)
A "last 10 items" list is maintained using `list.subList(1, list.size())`. SubList returns a **view** backed by the original list. As new items are added and subList is called again, a chain of views keeps all historical data alive.

**Root cause:** `List.subList()` does not copy — it creates a view referencing the original list.
**Fix:** Use `new ArrayList<>(list.subList(...))` to copy. Or use a `LinkedList`/`ArrayDeque` for sliding window patterns.

**MAT finding:** OQL query `SELECT s.parent.size FROM java.util.ArrayList$SubList s` reveals parent lists growing to hundreds of elements despite only 10 being "visible."

#### Leak 4 — XML DOM Nodes (`GET /leak4`)
DOM parser creates a full document tree. When individual `Node` objects are extracted and stored in a list, each node retains a reference to its parent `Document`, keeping the entire XML tree in memory.

**Root cause:** DOM Node objects reference their parent Document. Storing extracted nodes prevents the Document from being GC'd.
**Fix:** Extract text values (Strings) from nodes instead of storing Node objects. Consider SAX/StAX parsing for large XML.

---

### Category: ThreadLocal & Thread Pool Contamination

#### Leak 5 — ThreadLocal on Pooled Threads (`GET /leak5`)
A `ThreadLocal<RequestContext>` stores a 20MB object per request. Tomcat reuses threads from a pool — the ThreadLocal data from a previous request stays attached to the thread forever.

**Root cause:** ThreadLocal data is bound to the thread, not the request. Pooled threads are never destroyed, so ThreadLocal data accumulates.
**Fix:** Always clean up with `try { ... } finally { threadLocal.remove(); }`. Prefer framework-managed context: MDC, SecurityContextHolder, Baggage.

**MAT finding:** `thread_overview` shows 10 Tomcat threads each retaining ~21MB with 7-10 ThreadLocal entries. `path_to_gc_roots`: `Big20MB -> RequestContext -> ThreadLocalMap$Entry -> ThreadLocalMap -> TaskThread`

#### Leak 5Lib — Library ThreadLocal Cache (`GET /leak5/lib`)
An external library creates a ThreadLocal cache on first use. With Virtual Threads (one per request), each virtual thread gets its own 100KB cache — defeating the purpose of lightweight threads.

**Root cause:** Libraries designed for pooled threads assume ThreadLocal data is reused. Virtual Threads break this assumption.
**Fix:** Upgrade the library. If not possible, use reflection to clean up the library's ThreadLocal after each call.

#### Leak 7 — Thread Leak (`GET /leak7`)
`Executors.newFixedThreadPool(2)` is created per request but never `shutdown()`. Worker threads live forever even after the executor is no longer referenced.

**Root cause:** Thread pools keep their worker threads alive. Without `shutdown()`, threads are GC roots.
**Fix:** Use `try-with-resources` on ExecutorService (Java 19+). Better: inject a singleton Spring-managed `ThreadPoolTaskExecutor`.

---

### Category: Async & Concurrency

#### Leak 6 — Unbounded Async Queue (`GET /leak6/download`)
Each request downloads 10MB of data and submits it to `CompletableFuture.runAsync()` which uses `ForkJoinPool.commonPool()`. The common pool has an **unbounded work queue** — when requests arrive faster than workers process them, 10MB payloads pile up in the queue.

**Root cause:** `ForkJoinPool.commonPool()` has no queue size limit. Large objects retained in queued tasks.
**Fix:** Use a dedicated `ExecutorService` with a bounded queue and rejection policy. Offload large payloads to disk/S3 before async processing.

**MAT finding:** `dominator_tree` shows 5 `ForkJoinTask[]` arrays retaining 15%, 12%, 6%, 6%, 6% of heap = 157MB in queued tasks.

#### Leak 8 — Deadlock (`GET /leak8/one` + `/leak8/two`)
Two services with `synchronized` methods call each other. Thread A holds lock on Service1, waits for Service2. Thread B holds lock on Service2, waits for Service1. Classic circular deadlock.

**Root cause:** Bidirectional synchronized calls between objects.
**Fix:** Avoid `synchronized` methods on service classes. Use explicit locks with timeout. Eliminate bidirectional coupling.

#### Leak 9 — ConcurrentHashMap Deadlock (`GET /leak9/one` + `/leak9/two`)
Two `ConcurrentHashMap` instances with `compute()` calls that reference each other. Deadlock when both threads try to modify both maps simultaneously.

**Root cause:** `ConcurrentHashMap.compute()` holds a lock on the bucket — passing lambdas that access other synchronized structures creates lock ordering issues.
**Fix:** Don't pass complex lambdas to synchronized collection methods.

#### Leak 17 — Thread Starvation (`GET /leak17`)
All Tomcat worker threads blocked on a 20-second sleep. New requests (including liveness probes) queue up. Kubernetes kills the container because the health check times out.

**Root cause:** Long-running synchronous operations exhaust the thread pool.
**Fix:** Use a Semaphore to limit concurrent long operations. Consider Virtual Threads. Move long work to async processing.

#### Leak 22 — Thread Pool Self-Submit (`GET /leak22`)
An async method running on a 3-thread executor submits 3 parallel sub-tasks to the **same executor**. All 3 worker threads are now waiting for their sub-tasks, but no threads are available to run them.

**Root cause:** Submitting work to the executor you're already running on.
**Fix:** Use a separate executor for sub-tasks, or use Virtual Threads.

---

### Category: Caching

#### Leak 11 — Idempotency Key Cache (`GET /leak11`)
An idempotency filter stores every request's key in a `Set<String>` to prevent duplicate processing. The set grows forever — no eviction, no TTL.

**Root cause:** Unbounded in-memory cache with no eviction policy.
**Fix:** Use Caffeine or Redis with TTL-based eviction. For idempotency, 30-60 second TTL is typically sufficient.

#### Leak 12 — Cache Key Problems (`GET /leak12?date=...`, `/signature`, `/objectKey`, `/mutableKey`)
Four variants of caching gone wrong:
1. **DIY cache with no eviction** — `Map<LocalDate, Big20MB>` grows forever
2. **@Cacheable signature change** — adding a parameter changes the cache key, creating duplicate entries
3. **Object key without equals/hashCode** — every lookup is a cache miss
4. **Mutable entity as cache key** — entity is modified after being used as key, corrupting the cache

**Root cause:** Cache key design is critical and subtle. Framework caching (`@Cacheable`) hides key generation logic.
**Fix:** Use immutable keys (records). Monitor cache hit/miss ratio. Write tests that verify caching behavior. Set max size and TTL on all caches.

**MAT finding:** `dominator_tree` shows Caffeine `BoundedLocalManualCache` retaining 461MB (94.2% of heap).

#### Leak 24 — High-Cardinality Metric Tags (`GET /leak24/{uuid}`)
Micrometer timer created with a UUID as a tag value. Every unique UUID creates a new metric time series. Metrics never expire.

**Root cause:** High-cardinality tag values (UUIDs, user IDs, URLs) create unbounded metric expansion.
**Fix:** Only use bounded tag values (status codes, method names, service names). Never use request IDs or user IDs as metric tags.

---

### Category: Resource Management

#### Leak 13 — Hibernate First-Level Cache (`GET /leak13/export`)
Streaming entities from the database within a `@Transactional` method. Hibernate keeps a copy of every loaded `@Entity` in the 1st-level cache (persistence context). Streaming 600MB of data = 600MB in cache.

**Root cause:** Hibernate's persistence context is unbounded within a transaction.
**Fix:** Call `entityManager.detach(entity)` after processing each entity. Or use a `StatelessSession`. Process in batches with periodic `entityManager.clear()`.

#### Leak 14 — ClassLoader Leak (`GET /leak14`)
Dynamic plugin loading creates a new `URLClassLoader` per upload. Old plugins are never properly unloaded — their classes hold static fields with large arrays, and static Timer threads prevent GC of the classloader.

**Root cause:** ClassLoader cannot be GC'd if any of its classes are still referenced (by threads, static fields, or other classloaders).
**Fix:** Stop all threads, clear static fields, and null out all references before discarding a classloader.

#### Leak 18 — Connection Pool Exhaustion (`GET /leak18`)
Database connection obtained but not returned to the pool if an exception occurs before the `finally` block.

**Root cause:** Resource not closed on error paths.
**Fix:** Use try-with-resources for all closeable resources (connections, streams, clients).

---

### Category: Native / Off-Heap Memory

#### Leak 15 — Direct ByteBuffer (`GET /leak15`)
`ByteBuffer.allocateDirect()` allocates memory outside the Java heap. Direct buffers are limited by `-XX:MaxDirectMemorySize` and subject to fragmentation. Not visible in heap dumps.

**Root cause:** Direct memory managed outside GC. Fragmentation prevents reuse of freed space.
**Fix:** Pool direct buffers. Monitor with `-XX:NativeMemoryTracking=detail`. Use `jcmd VM.native_memory`.

#### Leak 21 — Native Memory Fragmentation (`GET /leak21?frag=true`)
Alternating allocate/free pattern on direct buffers creates gaps that larger subsequent allocations can't fill.

**Root cause:** External fragmentation in native memory allocator.
**Fix:** Allocate uniform-sized buffers. Use buffer pools. Monitor RSS vs heap size.

---

### Category: External Input / Security

#### Leak 20 — XML Bomb / Billion Laughs (`POST /leak20`)
XML entity expansion attack. Recursive entity definitions expand exponentially, consuming gigabytes of memory from a tiny XML file.

**Root cause:** XML parser processes external entities and recursive definitions without limits.
**Fix:** Disable DTD processing: `factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`. Set `jdk.xml.entityExpansionLimit`.

---

### Category: GUI / Observer Pattern

#### Leak 16 — Observer/Listener Leak (Desktop GUI)
Anonymous `MouseListener` added to a main frame captures a reference to a child frame. When the child frame is closed, the listener keeps it alive.

**Root cause:** Event listeners create hidden references. Closing a window doesn't remove listeners registered on other windows.
**Fix:** Remove listeners in `WindowListener.windowClosed()`. Use weak references for observers.

---

### Category: Session / State

#### Leak 29 — HTTP Session Bloat (`GET /leak29`)
User preferences (100 x 1KB objects) stored in HTTP session. Under load with 4000 concurrent users, server memory is exhausted by session data.

**Root cause:** Storing large objects in HTTP sessions. Sessions created for every request (including anonymous/API calls).
**Fix:** REST APIs should be stateless — use JWT tokens. If sessions are needed, push them to Redis/database. Set aggressive session timeouts.

---

## Analysis Tools

### Eclipse MAT (Memory Analyzer Tool)
The standard tool for heap dump analysis. Key views:
- **Histogram** — classes sorted by instance count and memory usage
- **Dominator Tree** — what objects retain the most memory
- **Path to GC Roots** — why an object can't be garbage collected
- **OQL** — SQL-like queries against heap objects

### MAT MCP Server (`mat-mcp/`)
An AI-powered heap dump analyzer. See `mat-mcp/README.md` for setup.

### JVM Diagnostic Commands
```bash
# List Java processes
jps -l

# Take heap dump
jmap -dump:format=b,file=heap.hprof <PID>

# Thread dump (deadlock detection)
jstack <PID>

# Native memory tracking
jcmd <PID> VM.native_memory summary
```

---

## Key Takeaways

1. **Retained Heap** is the metric that matters — it's "how much memory would be freed if this object were GC'd"
2. **Dominator Tree** is your first stop — it shows the biggest memory retainers instantly
3. **Path to GC Roots** answers "WHY can't this object be garbage collected?"
4. **ThreadLocal + Thread Pools** is the most common production leak pattern
5. **Unbounded caches** are the second most common — always set max size and TTL
6. **Inner classes** create invisible references to enclosing instances
7. **Off-heap memory** (direct buffers, native memory) won't show in heap dumps
8. **Framework magic** (@Cacheable, @Async, @Transactional) hides memory implications
