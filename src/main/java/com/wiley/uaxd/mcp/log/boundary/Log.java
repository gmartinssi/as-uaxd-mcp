package com.wiley.uaxd.mcp.log.boundary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple file-based logging for MCP server.
 */
public interface Log {
    Path LOG_FILE = Path.of(System.getProperty("user.home"), "uaxd-mcp.log");
    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static void info(String message) {
        log("INFO", message);
    }

    static void debug(String message) {
        log("DEBUG", message);
    }

    static void error(String message) {
        log("ERROR", message);
    }

    static void error(String message, Throwable t) {
        log("ERROR", message + " - " + t.getMessage());
    }

    static void request(String message) {
        log("REQUEST", message);
    }

    static void response(String message) {
        log("RESPONSE", message);
    }

    private static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logLine = String.format("[%s] [%s] %s%n", timestamp, level, message);
        try {
            Files.writeString(LOG_FILE, logLine,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Silent fail - can't log if logging fails
        }
    }
}
