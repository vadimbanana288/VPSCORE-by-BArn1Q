package io.vpscore.update;

import io.vpscore.config.VPSConfig.UpdateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.jar.*;

public class SelfUpdateManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SelfUpdateManager.class);

    private final UpdateConfig config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private volatile boolean running;

    public SelfUpdateManager(UpdateConfig config) {
        this.config = config;
    }

    public void start() {
        if (!config.isAutoUpdate()) return;
        running = true;

        scheduler.scheduleAtFixedRate(this::checkForUpdates, 0, 24, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::checkForUpdates, 0, 1, TimeUnit.HOURS);

        log.info("Update manager started (version: {}, auto-update: {})",
            config.getCurrentVersion(), config.isAutoUpdate());
    }

    public void checkForUpdates() {
        try {
            log.debug("Checking for updates...");
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/user/vpscore/releases/latest"))
                .header("Accept", "application/json")
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.debug("Update check response received");
            }
        } catch (Exception e) {
            log.debug("Update check failed", e);
        }
    }

    public boolean update() {
        try {
            log.info("Starting update...");
            var jarPath = Path.of(SelfUpdateManager.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

            var backupPath = jarPath.resolveSibling(jarPath.getFileName() + ".bak");
            Files.copy(jarPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(config.getUpdateUrl()))
                .GET()
                .build();

            var tempFile = Files.createTempFile("vpscore-update", ".jar");
            httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

            try (var jarFile = new JarFile(tempFile.toFile())) {
                var manifest = jarFile.getManifest();
                if (manifest != null) {
                    Files.move(tempFile, jarPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Update successful. Restart to apply.");
                    return true;
                }
            }

            Files.deleteIfExists(tempFile);
            log.warn("Invalid update file");
            return false;
        } catch (Exception e) {
            log.error("Update failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        log.info("Update manager stopped");
    }
}
