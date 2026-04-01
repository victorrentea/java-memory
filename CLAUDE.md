# Java Memory Leaks Training Lab

## Project Overview
A Spring Boot application with 24 intentional memory leak examples for training purposes. Each leak demonstrates a real-world pattern.

## How to Run
```bash
cd ~/workspace/java-memory
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx500m"
# App starts at http://localhost:8080
```

Requires: Java 21+, Maven 3.8+

## Taking Heap Dumps
```bash
# Find PID
jps -l | grep MemoryApp

# Dump heap
jmap -dump:format=b,file=heap.hprof <PID>
```

## Triggering Leaks
Each leak has an HTTP endpoint (`GET /leakN`). Some require multiple calls, some require concurrent calls, some sleep 30s to give time for a heap dump.

Quick reference:
- **Single call + dump during sleep**: leak1, leak2 (30s sleep), leak10
- **Multiple sequential calls**: leak3 (50+), leak4, leak11 (300+), leak12
- **Concurrent calls needed**: leak5 (10 threads), leak6 (30 calls), leak8 (2 endpoints simultaneously), leak17
- **Special**: leak13 (call /persist first, then /export), leak14 (upload plugin), leak20 (POST XML)

## MAT MCP Server (AI Heap Dump Analyzer)
Located in `mat-mcp/`. See `mat-mcp/README.md` for setup.

```bash
# First build (one time)
cd mat-mcp && mvn verify -Pmcp

# Launch as MCP server
./mat-mcp/launch-mat-mcp.sh
```

## Project Structure
```
src/main/java/victor/training/performance/
  leak/           # All leak examples (Leak0 through Leak29)
  leak/obj/       # Helper objects (Big20MB, Big1KB, Big100MB, Big)
  util/           # Utilities (PerformanceUtil.sleepSeconds, etc.)
mat-mcp/          # Eclipse MAT MCP server for AI-driven analysis
```

## Key Patterns for AI Analysis
When analyzing a heap dump, follow this workflow:
1. `open_heap_dump` - open the .hprof
2. `dominator_tree` - find biggest memory retainers
3. `histogram` - find classes with suspicious instance counts
4. `path_to_gc_roots` - trace why objects can't be GC'd
5. `thread_overview` - check thread-level memory distribution
6. `oql` - run targeted queries for deeper investigation
