package com.wiley.uaxd.mcp.jsonrpc.entity;

/**
 * JSON-RPC response builders.
 */
public interface JsonRPCResponses {

    static String success(Object id, String resultJson) {
        String idStr = id instanceof String ? "\"" + id + "\"" : String.valueOf(id);
        return String.format("""
            {"jsonrpc":"2.0","id":%s,"result":%s}""", idStr, resultJson);
    }

    static String error(Object id, int code, String message) {
        String idStr = id instanceof String ? "\"" + id + "\"" : String.valueOf(id);
        return String.format("""
            {"jsonrpc":"2.0","id":%s,"error":{"code":%d,"message":"%s"}}""",
            idStr, code, escapeJson(message));
    }

    static String notification(String method, String paramsJson) {
        return String.format("""
            {"jsonrpc":"2.0","method":"%s","params":%s}""", method, paramsJson);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
