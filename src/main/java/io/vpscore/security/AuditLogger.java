package io.vpscore.security;

import io.vpscore.config.VPSConfig.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class AuditLogger implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityConfig config;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private BufferedWriter writer;

    public AuditLogger(SecurityConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        if (!config.isAuditLog()) return;
        running = true;
        var path = Path.of(config.getAuditLogFile());
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        executor.submit(this::processQueue);
        log.info("Audit logger started: {}", path.toAbsolutePath());
    }

    public void log(String action, String user, String ip, String details) {
        if (!running) return;
        var timestamp = LocalDateTime.now().format(FMT);
        var entry = String.format("[%s] %s | user=%s | ip=%s | action=%s | details=%s%n",
            timestamp, Thread.currentThread().getName(), user, ip, action, details);
        queue.offer(entry);
    }

    public void logExec(String user, String ip, String command) {
        log("EXEC", user, ip, command);
    }

    public void logAuth(String user, String ip, boolean success) {
        log(success ? "AUTH_OK" : "AUTH_FAIL", user, ip, "");
    }

    public void logFs(String user, String ip, String operation, String path) {
        log("FS_" + operation, user, ip, path);
    }

    private void processQueue() {
        while (running) {
            try {
                var entry = queue.poll(1, TimeUnit.SECONDS);
                if (entry != null && writer != null) {
                    writer.write(entry);
                    writer.flush();
                }
            } catch (Exception e) {
                if (running) log.warn("Audit log error", e);
            }
        }
    }

    @Override
    public void close() {
        running = true;
        executor.shutdownNow();
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
        }
        log.info("Audit logger stopped");
    }
}
