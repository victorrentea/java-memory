package com.victorrentea.mat.mcp;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/**
 * JUnit 4 test that acts as an MCP stdio server.
 * When invoked via Tycho surefire with the 'mcp' profile,
 * it keeps MAT running and processes MCP requests interactively.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class MatMcpServer {

    private static SWTWorkbenchBot bot;
    private static MatOperations ops;
    private static final String SCREENSHOT_DIR =
            System.getProperty("user.home") + "/workspace/mat-automate/target/screenshots";

    @BeforeClass
    public static void setUp() throws Exception {
        SWTBotPreferences.TIMEOUT = 15000;
        SWTBotPreferences.PLAYBACK_DELAY = 50;
        SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US";
        new File(SCREENSHOT_DIR).mkdirs();
        bot = new SWTWorkbenchBot();
        ops = new MatOperations();

        // Wait for shell to appear
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            AtomicReference<String> title = new AtomicReference<>();
            Display.getDefault().syncExec(() -> {
                for (Shell s : Display.getDefault().getShells()) {
                    if (!s.isDisposed() && s.isVisible()) {
                        title.set(s.getText());
                        s.forceActive();
                        s.setFocus();
                        break;
                    }
                }
            });
            if (title.get() != null && !title.get().isEmpty()) {
                System.err.println("[mat-mcp] Shell ready: " + title.get());
                break;
            }
            Thread.sleep(200);
        }

        // Dismiss Welcome/Getting Started views
        for (String v : new String[]{"Welcome", "Getting Started"}) {
            try { bot.viewByTitle(v).close(); } catch (Exception e) { }
        }
        System.err.println("[mat-mcp] MAT UI ready");
    }

    @Test
    public void mcpServerLoop() throws Exception {
        // Use FileDescriptor.out directly to bypass any Tycho stdout redirection
        PrintStream mcpOut = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        InputStream mcpIn = new FileInputStream(FileDescriptor.in);

        McpProtocol protocol = new McpProtocol(mcpIn, mcpOut);
        System.err.println("[mat-mcp] MCP server ready, waiting for requests...");

        while (true) {
            McpProtocol.McpMessage msg = protocol.readMessage();
            if (msg == null) break; // EOF — client disconnected

            try {
                handleMessage(msg, protocol);
            } catch (Exception e) {
                System.err.println("[mat-mcp] Error handling " + msg.method + ": " + e.getMessage());
                if (msg.id != null) {
                    protocol.sendError(msg.id, -32603, "Internal error: " + e.getMessage());
                }
            }
        }
        System.err.println("[mat-mcp] MCP server shutting down");
    }

    private void handleMessage(McpProtocol.McpMessage msg, McpProtocol protocol) throws Exception {
        String method = msg.method;
        if (method == null) return;

        switch (method) {
            case "initialize":
                handleInitialize(msg, protocol);
                break;
            case "notifications/initialized":
                // No response needed for notifications
                System.err.println("[mat-mcp] Client initialized");
                break;
            case "tools/list":
                handleToolsList(msg, protocol);
                break;
            case "tools/call":
                handleToolsCall(msg, protocol);
                break;
            default:
                System.err.println("[mat-mcp] Unknown method: " + method);
                if (msg.id != null) {
                    protocol.sendError(msg.id, -32601, "Method not found: " + method);
                }
        }
    }

    private void handleInitialize(McpProtocol.McpMessage msg, McpProtocol protocol) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "mat-mcp-server");
        serverInfo.put("version", "0.1.0");
        result.put("serverInfo", serverInfo);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        Map<String, Object> tools = new LinkedHashMap<>();
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        protocol.sendResult(msg.id, result);
    }

    private void handleToolsList(McpProtocol.McpMessage msg, McpProtocol protocol) {
        List<Map<String, Object>> toolList = new ArrayList<>();

        toolList.add(toolDef("screenshot", "Take a screenshot of the MAT application window",
                optionalStringParam("name", "Screenshot filename (without extension). Default: 'screenshot'")));

        toolList.add(toolDef("open_heap_dump", "Open a heap dump file (.hprof) in MAT",
                requiredStringParam("path", "Absolute path to the .hprof file")));

        toolList.add(toolDef("get_overview", "Get the overview information of the currently opened heap dump",
                Collections.emptyMap()));

        Map<String, Object> histogramProps = new LinkedHashMap<>();
        addStringParam(histogramProps, "class_filter", "Regex to filter class names (optional)");
        addIntParam(histogramProps, "max_rows", "Maximum rows to return (default: 50)");
        toolList.add(toolDef("histogram", "Get class histogram showing object counts and heap sizes per class", histogramProps));

        Map<String, Object> domTreeProps = new LinkedHashMap<>();
        addIntParam(domTreeProps, "object_id", "Object ID to expand (-1 or omit for top-level dominators)");
        addIntParam(domTreeProps, "max_rows", "Maximum rows to return (default: 25)");
        toolList.add(toolDef("dominator_tree", "Get dominator tree entries showing retained heap by object. Use object_id from results to drill deeper.", domTreeProps));

        Map<String, Object> oqlProps = new LinkedHashMap<>();
        addStringParam(oqlProps, "query", "The OQL query to execute");
        addIntParam(oqlProps, "max_rows", "Maximum rows to return (default: 100)");
        toolList.add(toolDef("oql", "Execute an OQL (Object Query Language) query", oqlProps));

        toolList.add(toolDef("leak_suspects", "Run the leak suspects report",
                Collections.emptyMap()));

        Map<String, Object> gcRootProps = new LinkedHashMap<>();
        addIntParam(gcRootProps, "object_id", "Object ID to find GC root paths for");
        addStringParam(gcRootProps, "class_name", "Fully qualified class name (used if object_id not provided, picks first instance)");
        addIntParam(gcRootProps, "max_paths", "Maximum paths to return (default: 5)");
        toolList.add(toolDef("path_to_gc_roots", "Show shortest paths from an object to GC roots", gcRootProps));

        Map<String, Object> threadProps = new LinkedHashMap<>();
        addIntParam(threadProps, "max_threads", "Maximum threads to return (default: 20)");
        toolList.add(toolDef("thread_overview", "List all Java threads sorted by retained heap, showing thread name, retained heap size, and ThreadLocal entry count", threadProps));

        toolList.add(toolDef("list_tabs", "List all open editor tabs in MAT",
                Collections.emptyMap()));

        toolList.add(toolDef("switch_tab", "Switch to a specific editor tab",
                requiredStringParam("title", "Title of the tab to switch to")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", toolList);
        protocol.sendResult(msg.id, result);
    }

    private void handleToolsCall(McpProtocol.McpMessage msg, McpProtocol protocol) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = msg.params.get("arguments") instanceof Map
                ? (Map<String, Object>) msg.params.get("arguments") : new LinkedHashMap<>();
        String toolName = msg.params.get("name") != null ? msg.params.get("name").toString() : "";

        System.err.println("[mat-mcp] Tool call: " + toolName + " args=" + arguments);

        switch (toolName) {
            case "screenshot":
                handleScreenshot(msg, protocol, arguments);
                break;
            case "open_heap_dump": {
                String path = arguments.get("path") != null ? arguments.get("path").toString() : "";
                Map<String, Object> res = ops.openHeapDump(path);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "get_overview": {
                Map<String, Object> res = ops.getOverview();
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "histogram": {
                String classFilter = arguments.get("class_filter") != null
                        ? arguments.get("class_filter").toString() : null;
                int maxRows = getIntArg(arguments, "max_rows", 50);
                Map<String, Object> res = ops.getHistogram(classFilter, maxRows);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "dominator_tree": {
                int objectId = getIntArg(arguments, "object_id", -1);
                int maxRows = getIntArg(arguments, "max_rows", 25);
                Map<String, Object> res = ops.getDominatorTree(objectId, maxRows);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "oql": {
                String query = arguments.get("query") != null ? arguments.get("query").toString() : "";
                int maxRows = getIntArg(arguments, "max_rows", 100);
                Map<String, Object> res = ops.executeOql(query, maxRows);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "leak_suspects": {
                Map<String, Object> res = ops.leakSuspects();
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "path_to_gc_roots": {
                int maxPaths = getIntArg(arguments, "max_paths", 5);
                int objectId;
                if (arguments.get("object_id") != null) {
                    objectId = getIntArg(arguments, "object_id", -1);
                } else if (arguments.get("class_name") != null) {
                    try {
                        objectId = ops.findObjectIdByClassName(arguments.get("class_name").toString());
                    } catch (Exception e) {
                        sendJsonResult(msg.id, protocol, errorMap(e.getMessage()));
                        break;
                    }
                } else {
                    sendJsonResult(msg.id, protocol, errorMap("Either object_id or class_name is required"));
                    break;
                }
                Map<String, Object> res = ops.pathToGcRoots(objectId, maxPaths);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "thread_overview": {
                int maxThreads = getIntArg(arguments, "max_threads", 20);
                Map<String, Object> res = ops.threadOverview(maxThreads);
                sendJsonResult(msg.id, protocol, res);
                break;
            }
            case "list_tabs":
            case "switch_tab":
                sendTextResult(msg.id, protocol, "Tool '" + toolName + "' is not yet implemented. Coming soon!");
                break;
            default:
                protocol.sendError(msg.id, -32602, "Unknown tool: " + toolName);
        }
    }

    // --- Tool implementations ---

    private void handleScreenshot(McpProtocol.McpMessage msg, McpProtocol protocol, Map<String, Object> args) {
        String name = args.get("name") != null ? args.get("name").toString() : "screenshot";
        try {
            BufferedImage capture = new Robot().createScreenCapture(
                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            File f = new File(SCREENSHOT_DIR, name + ".png");
            ImageIO.write(capture, "png", f);
            System.err.println("[mat-mcp] Screenshot saved: " + f.getAbsolutePath());
            sendTextResult(msg.id, protocol, "Screenshot saved: " + f.getAbsolutePath());
        } catch (Exception e) {
            protocol.sendError(msg.id, -32603, "Screenshot failed: " + e.getMessage());
        }
    }

    // --- Helpers ---

    private void sendTextResult(Object id, McpProtocol protocol, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", Collections.singletonList(content));
        protocol.sendResult(id, result);
    }

    private static Map<String, Object> toolDef(String name, String description, Map<String, Object> properties) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        tool.put("inputSchema", inputSchema);

        return tool;
    }

    private static Map<String, Object> requiredStringParam(String name, String description) {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "string");
        param.put("description", description);
        props.put(name, param);
        return props;
    }

    private static Map<String, Object> optionalStringParam(String name, String description) {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "string");
        param.put("description", description);
        props.put(name, param);
        return props;
    }

    private static void addStringParam(Map<String, Object> props, String name, String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "string");
        param.put("description", description);
        props.put(name, param);
    }

    private static void addIntParam(Map<String, Object> props, String name, String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "integer");
        param.put("description", description);
        props.put(name, param);
    }

    private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private void sendJsonResult(Object id, McpProtocol protocol, Map<String, Object> data) {
        String json = McpProtocol.toJson(data);
        sendTextResult(id, protocol, json);
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "error");
        m.put("message", message);
        return m;
    }
}
