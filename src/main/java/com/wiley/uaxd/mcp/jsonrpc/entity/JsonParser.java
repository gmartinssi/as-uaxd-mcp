package com.wiley.uaxd.mcp.jsonrpc.entity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON parser - handles basic JSON parsing for MCP protocol.
 */
public class JsonParser {

    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return Collections.emptyMap();
        }
        return parseObjectInternal(json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObjectInternal(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < json.length()) {
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;

            // Parse key
            if (json.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = findStringEnd(json, keyStart);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart, keyEnd);
            i = keyEnd + 1;

            // Skip to colon
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) != ':') break;
            i++;

            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;

            // Parse value
            Object[] valueAndEnd = parseValue(json, i);
            if (valueAndEnd == null) break;
            result.put(key, valueAndEnd[0]);
            i = (int) valueAndEnd[1];

            // Skip whitespace and comma
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i < json.length() && json.charAt(i) == ',') i++;
        }

        return result;
    }

    private static Object[] parseValue(String json, int start) {
        if (start >= json.length()) return null;
        char c = json.charAt(start);

        if (c == '"') {
            // String
            int end = findStringEnd(json, start + 1);
            if (end < 0) return null;
            String value = unescapeString(json.substring(start + 1, end));
            return new Object[]{value, end + 1};
        } else if (c == '{') {
            // Object
            int end = findObjectEnd(json, start);
            if (end < 0) return null;
            Map<String, Object> obj = parseObjectInternal(json.substring(start, end + 1));
            return new Object[]{obj, end + 1};
        } else if (c == '[') {
            // Array
            int end = findArrayEnd(json, start);
            if (end < 0) return null;
            List<Object> arr = parseArray(json.substring(start, end + 1));
            return new Object[]{arr, end + 1};
        } else if (c == 't' && json.startsWith("true", start)) {
            return new Object[]{true, start + 4};
        } else if (c == 'f' && json.startsWith("false", start)) {
            return new Object[]{false, start + 5};
        } else if (c == 'n' && json.startsWith("null", start)) {
            return new Object[]{null, start + 4};
        } else if (c == '-' || Character.isDigit(c)) {
            // Number
            int end = start;
            while (end < json.length() && isNumberChar(json.charAt(end))) end++;
            String numStr = json.substring(start, end);
            try {
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    return new Object[]{Double.parseDouble(numStr), end};
                } else {
                    return new Object[]{Long.parseLong(numStr), end};
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static List<Object> parseArray(String json) {
        List<Object> result = new ArrayList<>();
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;

            Object[] valueAndEnd = parseValue(json, i);
            if (valueAndEnd == null) break;
            result.add(valueAndEnd[0]);
            i = (int) valueAndEnd[1];

            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return result;
    }

    private static int findStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++; // Skip escaped character
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findObjectEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < json.length()) {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static int findArrayEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < json.length()) {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static boolean isNumberChar(char c) {
        return Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E';
    }

    private static String unescapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            String hex = s.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u");
                            }
                        }
                    }
                    default -> {
                        sb.append('\\');
                        sb.append(next);
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // JSON building utilities
    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeString(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(valueToJson(item));
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String s) {
            return "\"" + escapeString(s) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map m) {
            return toJson(m);
        } else if (value instanceof List l) {
            return toJson(l);
        } else {
            return "\"" + escapeString(value.toString()) + "\"";
        }
    }

    private static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
