package com.victorrentea.mat.mcp;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.*;
import org.eclipse.mat.snapshot.*;
import org.eclipse.mat.snapshot.model.*;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Wraps MAT programmatic API (ISnapshot) and SWT/UI operations.
 * All public methods return Map/List structures for JSON serialization via McpProtocol.toJson().
 */
public class MatOperations {

    private ISnapshot snapshot;

    // --- Snapshot lifecycle ---

    /**
     * Open a heap dump file in MAT.
     * 1. Opens the editor in the UI via IDE.openEditorOnFileStore
     * 2. Waits for parsing to complete (Getting Started wizard appears)
     * 3. Opens the snapshot via SnapshotFactory for programmatic API access
     * 4. Returns overview info
     */
    public Map<String, Object> openHeapDump(String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        File file = new File(path);
        if (!file.exists()) {
            result.put("status", "error");
            result.put("message", "File not found: " + path);
            return result;
        }

        // Close previous snapshot if any
        if (snapshot != null) {
            try {
                SnapshotFactory.dispose(snapshot);
            } catch (Exception ignored) {}
            snapshot = null;
        }

        // Open snapshot directly via SnapshotFactory API.
        // This parses the hprof (creating index files next to it) if not already parsed,
        // or reuses existing index files. No UI needed for this step.
        System.err.println("[mat-mcp] Opening snapshot: " + path);
        try {
            snapshot = SnapshotFactory.openSnapshot(file,
                    Collections.<String, String>emptyMap(), new VoidProgressListener());
            System.err.println("[mat-mcp] Snapshot opened successfully");
        } catch (SnapshotException e) {
            result.put("status", "error");
            result.put("message", "Failed to open snapshot: " + e.getMessage());
            return result;
        }

        // 5. Return overview
        try {
            SnapshotInfo info = snapshot.getSnapshotInfo();
            result.put("status", "ok");
            result.put("heapSize", info.getUsedHeapSize());
            result.put("objectCount", info.getNumberOfObjects());
            result.put("classCount", info.getNumberOfClasses());
            result.put("path", info.getPath());
            result.put("jvmInfo", info.getJvmInfo());
            if (info.getCreationDate() != null) {
                result.put("creationDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getCreationDate()));
            }
        } catch (Exception e) {
            result.put("status", "ok_partial");
            result.put("message", "Snapshot opened but failed to read info: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get overview of currently open heap dump.
     */
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            SnapshotInfo info = snapshot.getSnapshotInfo();
            result.put("heapSize", info.getUsedHeapSize());
            result.put("objectCount", info.getNumberOfObjects());
            result.put("classCount", info.getNumberOfClasses());
            result.put("classLoaderCount", info.getNumberOfClassLoaders());
            result.put("gcRootCount", info.getNumberOfGCRoots());
            result.put("path", info.getPath());
            result.put("jvmInfo", info.getJvmInfo());
            if (info.getCreationDate() != null) {
                result.put("creationDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getCreationDate()));
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to get overview: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get histogram — class-level summary of object counts and heap sizes.
     * @param classFilter regex to filter class names (null or empty for all)
     * @param maxRows maximum number of rows to return
     */
    public Map<String, Object> getHistogram(String classFilter, int maxRows) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            Histogram histogram = snapshot.getHistogram(new VoidProgressListener());
            Collection<ClassHistogramRecord> records = histogram.getClassHistogramRecords();

            // Sort by shallow heap descending (retained heap is often 0 until explicitly calculated)
            List<ClassHistogramRecord> sorted = new ArrayList<>(records);
            sorted.sort((a, b) -> {
                long cmp = Long.compare(b.getUsedHeapSize(), a.getUsedHeapSize());
                return cmp != 0 ? (int) cmp : Long.compare(b.getNumberOfObjects(), a.getNumberOfObjects());
            });

            // Apply class filter if provided
            Pattern pattern = null;
            if (classFilter != null && !classFilter.trim().isEmpty()) {
                pattern = Pattern.compile(classFilter, Pattern.CASE_INSENSITIVE);
            }

            List<String> columns = Arrays.asList("className", "objectCount", "shallowHeap", "retainedHeap");
            List<List<Object>> rows = new ArrayList<>();
            int totalClasses = 0;

            for (ClassHistogramRecord rec : sorted) {
                totalClasses++;
                if (pattern != null && !pattern.matcher(rec.getLabel()).find()) {
                    continue;
                }
                if (rows.size() >= maxRows) continue; // keep counting totalClasses
                List<Object> row = new ArrayList<>();
                row.add(rec.getLabel());
                row.add(rec.getNumberOfObjects());
                row.add(rec.getUsedHeapSize());
                row.add(rec.getRetainedHeapSize());
                rows.add(row);
            }

            result.put("columns", columns);
            result.put("rows", rows);
            result.put("totalClasses", totalClasses);
            result.put("filteredRows", rows.size());
        } catch (SnapshotException e) {
            result.put("status", "error");
            result.put("message", "Failed to get histogram: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get dominator tree entries.
     * @param objectId if < 0, returns top-level dominators; otherwise returns children of the given object
     * @param maxRows maximum number of rows to return
     */
    public Map<String, Object> getDominatorTree(int objectId, int maxRows) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            int[] objectIds;
            if (objectId < 0) {
                // Top-level dominators
                objectIds = snapshot.getImmediateDominatedIds(-1);
            } else {
                // Children of the given object
                objectIds = snapshot.getImmediateDominatedIds(objectId);
            }

            long totalHeapSize = snapshot.getSnapshotInfo().getUsedHeapSize();
            List<String> columns = Arrays.asList("objectId", "className", "shallowHeap",
                    "retainedHeap", "percentage", "label", "hasChildren");

            // Build rows, limited to maxRows
            // First, sort by retained heap descending — compute retained sizes
            List<int[]> idWithRetained = new ArrayList<>();
            for (int id : objectIds) {
                idWithRetained.add(new int[]{id});
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            int count = 0;
            for (int id : objectIds) {
                if (count >= maxRows) break;
                try {
                    IObject obj = snapshot.getObject(id);
                    long retainedHeap = obj.getRetainedHeapSize();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("objectId", id);
                    row.put("className", obj.getClazz().getName());
                    row.put("shallowHeap", obj.getUsedHeapSize());
                    row.put("retainedHeap", retainedHeap);
                    row.put("percentage", totalHeapSize > 0
                            ? Math.round(retainedHeap * 10000.0 / totalHeapSize) / 100.0 : 0);
                    row.put("label", obj.getClassSpecificName() != null
                            ? obj.getClassSpecificName() : "");
                    // Check if this object has children in the dominator tree
                    int[] children = snapshot.getImmediateDominatedIds(id);
                    row.put("hasChildren", children != null && children.length > 0);
                    rows.add(row);
                    count++;
                } catch (SnapshotException e) {
                    System.err.println("[mat-ops] Skipping object " + id + ": " + e.getMessage());
                }
            }

            // Sort by retained heap descending
            rows.sort((a, b) -> Long.compare(
                    ((Number) b.get("retainedHeap")).longValue(),
                    ((Number) a.get("retainedHeap")).longValue()));

            result.put("columns", columns);
            result.put("rows", rows);
            result.put("totalChildren", objectIds.length);
            result.put("parentObjectId", objectId);
        } catch (SnapshotException e) {
            result.put("status", "error");
            result.put("message", "Failed to get dominator tree: " + e.getMessage());
        }
        return result;
    }

    /**
     * Execute an OQL (Object Query Language) query.
     * @param query the OQL query string
     * @param maxRows maximum number of result rows
     */
    public Map<String, Object> executeOql(String query, int maxRows) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            // Use SnapshotQuery.lookup("oql") to execute OQL queries
            SnapshotQuery sq = SnapshotQuery.lookup("oql", snapshot);
            sq.setArgument("queryString", query);
            IResult queryResult = sq.execute(new VoidProgressListener());

            if (queryResult instanceof IResultTable) {
                IResultTable table = (IResultTable) queryResult;
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < table.getColumns().length; i++) {
                    columns.add(table.getColumns()[i].getLabel());
                }
                List<List<Object>> rows = new ArrayList<>();
                int rowCount = Math.min(table.getRowCount(), maxRows);
                for (int r = 0; r < rowCount; r++) {
                    Object rowObj = table.getRow(r);
                    List<Object> row = new ArrayList<>();
                    for (int c = 0; c < table.getColumns().length; c++) {
                        Object val = table.getColumnValue(rowObj, c);
                        row.add(val != null ? val.toString() : null);
                    }
                    rows.add(row);
                }
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("totalRows", table.getRowCount());
            } else if (queryResult instanceof IResultTree) {
                IResultTree tree = (IResultTree) queryResult;
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < tree.getColumns().length; i++) {
                    columns.add(tree.getColumns()[i].getLabel());
                }
                List<List<Object>> rows = new ArrayList<>();
                List<?> elements = tree.getElements();
                int rowCount = Math.min(elements.size(), maxRows);
                for (int r = 0; r < rowCount; r++) {
                    Object elem = elements.get(r);
                    List<Object> row = new ArrayList<>();
                    for (int c = 0; c < tree.getColumns().length; c++) {
                        Object val = tree.getColumnValue(elem, c);
                        row.add(val != null ? val.toString() : null);
                    }
                    rows.add(row);
                }
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("totalRows", elements.size());
            } else {
                // Fallback: return string representation
                result.put("text", queryResult != null ? queryResult.toString() : "No results");
            }
        } catch (SnapshotException e) {
            result.put("status", "error");
            result.put("message", "OQL execution failed: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "OQL error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Run leak suspects analysis.
     */
    public Map<String, Object> leakSuspects() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            // Try different query names — MAT derives them from class names
            SnapshotQuery query = null;
            String[] names = {"leakhunter", "find_leaks", "leak_suspects"};
            Exception lastError = null;
            for (String name : names) {
                try {
                    query = SnapshotQuery.lookup(name, snapshot);
                    System.err.println("[mat-mcp] Found leak query as: " + name);
                    break;
                } catch (Exception e) {
                    lastError = e;
                }
            }
            if (query == null) {
                result.put("status", "error");
                result.put("message", "No leak suspects query found. Last error: " +
                        (lastError != null ? lastError.getMessage() : "unknown"));
                return result;
            }
            IResult queryResult = query.execute(new VoidProgressListener());

            if (queryResult instanceof IResultTree) {
                IResultTree tree = (IResultTree) queryResult;
                List<Map<String, Object>> suspects = new ArrayList<>();
                List<?> elements = tree.getElements();
                for (Object elem : elements) {
                    Map<String, Object> suspect = new LinkedHashMap<>();
                    for (int c = 0; c < tree.getColumns().length; c++) {
                        Object val = tree.getColumnValue(elem, c);
                        suspect.put(tree.getColumns()[c].getLabel(),
                                val != null ? val.toString() : null);
                    }
                    suspects.add(suspect);
                }
                result.put("suspects", suspects);
                result.put("count", suspects.size());
            } else if (queryResult instanceof IResultTable) {
                IResultTable table = (IResultTable) queryResult;
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < table.getColumns().length; i++) {
                    columns.add(table.getColumns()[i].getLabel());
                }
                List<List<Object>> rows = new ArrayList<>();
                for (int r = 0; r < table.getRowCount(); r++) {
                    Object rowObj = table.getRow(r);
                    List<Object> row = new ArrayList<>();
                    for (int c = 0; c < table.getColumns().length; c++) {
                        Object val = table.getColumnValue(rowObj, c);
                        row.add(val != null ? val.toString() : null);
                    }
                    rows.add(row);
                }
                result.put("columns", columns);
                result.put("rows", rows);
            } else {
                result.put("text", queryResult != null ? queryResult.toString() : "No results");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Leak suspects analysis failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Get paths from an object to GC roots.
     * @param objectId the object ID to find paths for
     * @param maxPaths maximum number of paths to return
     */
    public Map<String, Object> pathToGcRoots(int objectId, int maxPaths) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            // excludeMap: map of field names to exclude (e.g. weak/soft references)
            Map<IClass, Set<String>> excludeMap = new HashMap<>();

            // Exclude weak and soft reference fields to get meaningful paths
            String[] refClasses = {
                    "java.lang.ref.WeakReference",
                    "java.lang.ref.SoftReference",
                    "java.lang.ref.PhantomReference"
            };
            for (String refClass : refClasses) {
                try {
                    Collection<IClass> classes = snapshot.getClassesByName(refClass, false);
                    if (classes != null) {
                        for (IClass cls : classes) {
                            Set<String> fields = new HashSet<>();
                            fields.add("referent");
                            excludeMap.put(cls, fields);
                        }
                    }
                } catch (SnapshotException ignored) {}
            }

            IPathsFromGCRootsComputer computer = snapshot.getPathsFromGCRoots(objectId, excludeMap);
            List<List<Map<String, Object>>> paths = new ArrayList<>();

            for (int i = 0; i < maxPaths; i++) {
                int[] path = computer.getNextShortestPath();
                if (path == null) break;

                List<Map<String, Object>> pathEntries = new ArrayList<>();
                for (int id : path) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("objectId", id);
                    try {
                        IObject obj = snapshot.getObject(id);
                        entry.put("className", obj.getClazz().getName());
                        entry.put("label", obj.getClassSpecificName() != null
                                ? obj.getClassSpecificName() : "");
                        entry.put("shallowHeap", obj.getUsedHeapSize());

                        // Check if this is a GC root
                        GCRootInfo[] gcRoots = snapshot.getGCRootInfo(id);
                        if (gcRoots != null && gcRoots.length > 0) {
                            entry.put("gcRootType", gcRoots[0].getType());
                            entry.put("gcRootTypeName", GCRootInfo.getTypeAsString(gcRoots[0].getType()));
                        }
                    } catch (SnapshotException e) {
                        entry.put("error", e.getMessage());
                    }
                    pathEntries.add(entry);
                }
                paths.add(pathEntries);
            }

            result.put("paths", paths);
            result.put("pathCount", paths.size());
            result.put("objectId", objectId);
        } catch (SnapshotException e) {
            result.put("status", "error");
            result.put("message", "Path to GC roots failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Find an object ID by class name. Helper for path_to_gc_roots when called with a class name.
     * Returns the object ID of the first instance of the given class.
     */
    public int findObjectIdByClassName(String className) throws SnapshotException {
        if (snapshot == null) {
            throw new SnapshotException("No heap dump is currently open");
        }
        Collection<IClass> classes = snapshot.getClassesByName(className, false);
        if (classes == null || classes.isEmpty()) {
            throw new SnapshotException("Class not found: " + className);
        }
        IClass cls = classes.iterator().next();
        int[] objectIds = cls.getObjectIds();
        if (objectIds == null || objectIds.length == 0) {
            throw new SnapshotException("No instances of class: " + className);
        }
        return objectIds[0];
    }

    /**
     * Thread overview: list all threads with their name, state, retained heap, and
     * number of ThreadLocal entries.
     */
    public Map<String, Object> threadOverview(int maxThreads) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (snapshot == null) {
            result.put("status", "error");
            result.put("message", "No heap dump is currently open. Use open_heap_dump first.");
            return result;
        }
        try {
            // Use the thread_overview query if available, otherwise enumerate manually
            Collection<IClass> threadClasses = snapshot.getClassesByName("java.lang.Thread", true);
            List<Map<String, Object>> threads = new ArrayList<>();

            for (IClass cls : threadClasses) {
                int[] objectIds = cls.getObjectIds();
                for (int id : objectIds) {
                    IObject obj = snapshot.getObject(id);
                    Map<String, Object> thread = new LinkedHashMap<>();
                    thread.put("objectId", id);
                    thread.put("className", obj.getClazz().getName());

                    // Get thread name
                    String name = "";
                    try {
                        IObject nameObj = (IObject) obj.resolveValue("name");
                        if (nameObj != null) {
                            name = nameObj.getClassSpecificName();
                            if (name == null) name = nameObj.toString();
                        }
                    } catch (Exception e) {
                        name = obj.getClassSpecificName() != null ? obj.getClassSpecificName() : "";
                    }
                    thread.put("name", name);

                    // Retained heap
                    long retainedHeap = obj.getRetainedHeapSize();
                    thread.put("retainedHeap", retainedHeap);
                    thread.put("shallowHeap", obj.getUsedHeapSize());

                    // Count ThreadLocal entries
                    int threadLocalCount = 0;
                    try {
                        IObject threadLocals = (IObject) obj.resolveValue("threadLocals");
                        if (threadLocals != null) {
                            IObject table = (IObject) threadLocals.resolveValue("table");
                            if (table instanceof IObjectArray) {
                                IObjectArray arr = (IObjectArray) table;
                                // Count non-null entries
                                long len = arr.getLength();
                                // Use array references for counting
                                long[] refs = ((IObjectArray) table).getReferenceArray();
                                if (refs != null) {
                                    for (long ref : refs) {
                                        if (ref > 0) threadLocalCount++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ThreadLocal counting is best-effort
                    }
                    thread.put("threadLocalCount", threadLocalCount);

                    threads.add(thread);
                }
            }

            // Sort by retained heap descending
            threads.sort((a, b) -> Long.compare(
                    ((Number) b.get("retainedHeap")).longValue(),
                    ((Number) a.get("retainedHeap")).longValue()));

            // Limit results
            if (threads.size() > maxThreads) {
                threads = threads.subList(0, maxThreads);
            }

            result.put("threads", threads);
            result.put("totalThreads", threads.size());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Thread overview failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Check if a snapshot is currently open.
     */
    public boolean isSnapshotOpen() {
        return snapshot != null;
    }
}
