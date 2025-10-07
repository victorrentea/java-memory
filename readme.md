Memory Leaks@Devoxx.be
0. OOME = 5m ⭐️slide: prep
1. LongFrame = 3m
2. inner (≤javac 17)  -> visualVM = 5m
    new Predicate (≤javac 17)
    new HashMap() {{}}
3. subList🔥 => retained -> MAT + IntelliJ > Dominator Tree = 4m ⭐️slide:leak,retained
4. xml dom -> visualVM = 2m
5. ThreadLocal + Pool = 5m ⭐️slide:GC Root
5. ThreadLocal in Lib + VT🔥 = 4m
6. Async process = 10m
    - ForkJoinPool.commonPool()
    - Executor.newFixedSize/single
    - Push large data out of heap ⭐️slide:streamline
-------------
7. Thread leak: unclosed fixed pool = 5m ⭐️slide: mem model
8. Deadlock: ⚠️PT + VT = 10m
10. Library: add to ♾️ shutdownHook list = 5m
11. Filter = 5m
12. Cache key messup = 10m
    - in-house baked
    - accidental @Cacheable signature
    - key class without hashCode/equals
    - key class mutable
13. Hibernate 1st level cache = 7m
14. ClassLoader leak = 5m ⭐️slide: mem model
15. Direct Memory = 5m
16. GUI observer = 4m

BEFORE start:
- javac 17 (pom.xml)
- jre 21
- REM jvm flags on MemoryApp
- disable virtual threads
