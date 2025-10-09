Memory Leaks@Devoxx.bex§
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



@Pin Tegelaar: Defragmented native app

@Philip Aricks: Billion laughs attack

@Philip Aricks: log.trace("aaa" + hugeTree);
    ~>log.trace("aaa{}", hugeTree);
    ~>log.onTrace().log(()->"aaa"+ hugeTree);

@Ruben Lommelen: deserialize a class => OOME attack

@Christian: nested tx (REQUIRES_NEW, or NOT_SUPPORTED calle from TX) can deadlock 2 theads 
10 threads 
10 connections in pool
each thread needs 2 connections
all 10 take 1.
all 10 wait for a free conn. But the other 9 are doing the same
!!! -nin a payment processor


@ in  insurance domain
 claim->child->associations(medical, diagnosis..)
 kafka listener
 external publishes us claims
 we convert them into our internal model
 some data is not mapped yet.
 they pushed us a spike of 20000K unmapped medical records
 a microservice maps them and publishes "record mapped event"
 reacting on that in the kafka listener we replace the old medical record with this new one 
 we consumed on ?threads
 kafka consumer batch size was 500 (default) we reduced it to 50 
 to not out of memory x N consumers / app (kafka.concurrency = 3)
 => "fat messges" since they were RAW (unmapped) NO!

for every single ONE message received we loaded the entire Aggregate with hundreds of objects from DB.
FIX: reduce kafka.concurrency to 1 (=> /3 RAM)
FIX: increase pod memory (agains OPS) but traded with elastic instance min:1 + started on a schedule
FIX: cheat on DDD and got native SQL to change (BAD)
??? Inspect a heapdump=> there should only be 1 claim loaded from DB at any given point in time.
FIX: split transactions: for (claim having the record) {statrt TX, java>update commit} => not entirely ATOMIC per message.
FIX:
 entityManager.save(claim);
entityManager.flush(); x§
entityManager.clear(); between the 10K claims you loaded in a for + dereference aggregates changed



CDI @Dependent bean fetched using Cdi.current.select(Bean) from a non-CDI managed instance without .dispose!!



@Alexei Semenov: Groovy scripts used a lib to generate new .class with random name dynamically compiling groovy scripts on the fly pe request.
these new dynamic classes loaded, were never unloaded.


@Alexei Semenov: IoT -> one instance holding ConcurrentHashMap<SensorId, Queue<Values>> we have to consume those values very fast.
ConcurrHM to NOT block the sensor HTTP request.
the values in the map was drained from 10 threads in a busy loop.-
1000.000.000 Map.Entries in the map because we <-__ TODO 
Fix: changed to readwritelock when put/get in the map.
get


@Timon Borter -
you added to a list of Citrus testing framework a java repr of an OpenAPI
if (citrus.containsBean(openApi)) {/// ideal, but missing!
if (aplicationContext.containsBean(openApi)) {
    citrus.add(openApi)
}
in the newer version of citrus, they did not register it as beansanymore => we kept adding 
an integration test would have saved us 


BEFORE start:
- javac 17 (pom.xml)
- jre 21
- REM jvm flags on MemoryApp
- disable virtual threads
