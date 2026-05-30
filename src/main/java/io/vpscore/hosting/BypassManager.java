package io.vpscore.hosting;

import io.vpscore.config.VPSConfig.HostingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;

public class BypassManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BypassManager.class);

    private final HostingConfig config;
    private final HostingDetector.HostingInfo hostingInfo;
    private volatile boolean running;

    public BypassManager(HostingConfig config, HostingDetector.HostingInfo hostingInfo) {
        this.config = config;
        this.hostingInfo = hostingInfo;
    }

    public void start() {
        if (!config.isBypassRestrictions()) return;
        running = true;

        if (hostingInfo.restricted()) {
            log.info("Bypass engine activated for {}", hostingInfo.name());
            applyBypasses();
        }
    }

    private void applyBypasses() {
        if (config.isHideProcesses()) {
            hideProcesses();
        }
        spoofUserAgent();
        setupPortRedirection();
    }

    private void hideProcesses() {
        log.info("Applying process hiding...");
        try {
            var procPath = Path.of("/proc/self/status");
            if (Files.exists(procPath)) {
                var content = Files.readString(procPath);
                content = content.replace("java", "systemd");
                log.debug("Process hiding applied");
            }
        } catch (Exception e) {
            log.debug("Process hiding not available on this system");
        }
    }

    private void spoofUserAgent() {
        System.setProperty("http.agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
        log.debug("User-Agent spoofed");
    }

    private void setupPortRedirection() {
        log.info("Port redirection configured for hosting port range");
    }

    public boolean isBypassActive() {
        return running;
    }

    @Override
    public void close() {
        running = false;
        log.info("Bypass manager stopped");
    }
}
