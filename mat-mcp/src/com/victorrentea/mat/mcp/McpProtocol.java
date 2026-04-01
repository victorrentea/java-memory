package com.victorrentea.mat.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON-RPC 2.0 over stdio protocol handler for MCP.
 * Reads/writes newline-delimited JSON messages.
 * Uses a simple hand-rolled JSON parser/writer to avoid external dependencies.
 */
public class McpProtocol {

    private final BufferedReader reader;
    private final PrintStream writer;

    public McpProtocol(InputStream in, PrintStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = out;
    }

    // --- Simple JSON value types ---

    public static class McpMessage {
        public final String jsonrpc;
        public final Object id;       // String, Number, or null
        public final String method;
        public final Map<String, Object> params;
        public final String rawJson;

        public McpMessage(Object id, String method, Map<String, Object> params, String rawJson) {
            this.jsonrpc = "2.0";
            this.id = id;
            this.method = method;
            this.params = params != null ? params : new LinkedHashMap<>();
            this.rawJson = rawJson;
        }
    }

    /**
     * Read one JSON-RPC message from stdin. Returns null on EOF.
     */
    public McpMessage readMessage() throws IOException {
        String line = reader.readLine();
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty()) return readMessage(); // skip blank lines

        System.err.println("[mat-mcp] << " + line);

        Map<String, Object> json = parseJsonObject(line);
        Object id = json.get("id");
        String method = json.get("method") != null ? json.get("method").toString() : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> params = json.get("params") instanceof Map
                ? (Map<String, Object>) json.get("params") : new LinkedHashMap<>();

        return new McpMessage(id, method, params, line);
    }

    /**
     * Send a JSON-RPC result response.
     */
    public void sendResult(Object id, Object result) {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + toJson(id) + ",\"result\":" + toJson(result) + "}";
        System.err.println("[mat-mcp] >> " + json);
        writer.println(json);
        writer.flush();
    }

    /**
     * Send a JSON-RPC error response.
     */
    public void sendError(Object id, int code, String message) {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + toJson(id) + ",\"error\":{\"code\":" + code + ",\"message\":" + toJson(message) + "}}";
        System.err.println("[mat-mcp] >> " + json);
        writer.println(json);
        writer.flush();
    }

    // --- Minimal JSON parser (handles objects, arrays, strings, numbers, booleans, null) ---

    private int pos;
    private String src;

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        this.src = json;
        this.pos = 0;
        Object val = parseValue();
        return val instanceof Map ? (Map<String, Object>) val : new LinkedHashMap<>();
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) return null;
        char c = src.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') return parseNull();
        return parseNumber();
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip '{'
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }
        while (pos < src.length()) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ':') pos++;
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; break; }
        }
        return map;
    }

    private java.util.List<Object> parseArray() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        pos++; // skip '['
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }
        while (pos < src.length()) {
            list.add(parseValue());
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; break; }
        }
        return list;
    }

    private String parseString() {
        if (pos >= src.length() || src.charAt(pos) != '"') return "";
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                pos++;
                char esc = src.charAt(pos);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 < src.length()) {
                            sb.append((char) Integer.parseInt(src.substring(pos + 1, pos + 5), 16));
                            pos += 4;
                        }
                        break;
                    default: sb.append(esc);
                }
            } else if (c == '"') {
                pos++;
                return sb.toString();
            } else {
                sb.append(c);
            }
            pos++;
        }
        return sb.toString();
    }

    private Object parseNumber() {
        int start = pos;
        if (pos < src.length() && src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true; pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true; pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String num = src.substring(start, pos);
        if (isFloat) return Double.parseDouble(num);
        long l = Long.parseLong(num);
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
        return l;
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) { pos += 4; return true; }
        if (src.startsWith("false", pos)) { pos += 5; return false; }
        return false;
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; }
        return null;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    // --- Minimal JSON writer ---

    @SuppressWarnings("unchecked")
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return escapeJsonString((String) obj);
        if (obj instanceof Number) return obj.toString();
        if (obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append(escapeJsonString(e.getKey())).append(":").append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return escapeJsonString(obj.toString());
    }

    private static String escapeJsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
