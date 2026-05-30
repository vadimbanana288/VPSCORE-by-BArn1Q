package io.vpscore.bootstrap;

import io.vpscore.VPSCore;
import io.vpscore.config.VPSConfig;
import io.vpscore.hosting.HostingDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final VPSConfig config;
    private HostingDetector.HostingInfo hostingInfo;

    public Bootstrap(VPSConfig config) {
        this.config = config;
    }

    public void init() {
        System.out.println("[VPS Core] Initializing...");
        log.info("Initializing VPS Core bootstrap...");
        checkEnvironment();
        detectHosting();
        validateMode();
        setupWorkingDirectory();
        checkResources();
        System.out.println("[VPS Core] Environment check complete");
        log.info("Bootstrap initialization complete");
    }

    private void checkEnvironment() {
        var os = System.getProperty("os.name");
        var osArch = System.getProperty("os.arch");
        var javaVersion = System.getProperty("java.version");
        var javaVm = System.getProperty("java.vm.name");
        var availableProcs = Runtime.getRuntime().availableProcessors();
        var totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        var maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        log.info("OS: {} {} | JVM: {} {} | CPUs: {} | Mem: {}M / {}M",
            os, osArch, javaVm, javaVersion, availableProcs, totalMem, maxMem);

        var majorVersion = javaVersion.contains(".")
            ? Integer.parseInt(javaVersion.split("\\.")[0])
            : Integer.parseInt(javaVersion);
        if (majorVersion < 17) {
            log.error("Java 17+ required, found {}", javaVersion);
            System.exit(1);
        }
    }

    private void detectHosting() {
        if (!config.getHosting().isEnable() || !config.getHosting().isAutoDetect()) {
            log.info("Hosting detection disabled");
            return;
        }
        var detector = new HostingDetector();
        hostingInfo = detector.detect();
        log.info("Detected hosting: {} (type: {}, restricted: {})",
            hostingInfo.name(), hostingInfo.type(), hostingInfo.restricted());
        if (!hostingInfo.allocatedPorts().isEmpty()) {
            log.info("Pterodactyl allocated ports: {}", hostingInfo.allocatedPorts());
        }
        if (hostingInfo.restricted() && config.getHosting().isBypassRestrictions()) {
            log.info("Hosting has restrictions, bypass engine will be activated");
        }
    }

    private void validateMode() {
        var mode = config.getMode();
        log.info("Mode: {} - {}", mode.getKey(), mode.getDescription());

        switch (mode) {
            case ATTACH -> {
                if (config.getPid() <= 0) {
                    log.warn("No PID specified for attach mode, finding Minecraft process...");
                    findMinecraftProcess();
                }
            }
            case WRAPPER -> {
                log.info("Wrapper mode: will start Minecraft server within VPS Core");
            }
            case MINIMAL -> {
                log.info("Minimal mode: network and advanced features disabled");
            }
        }
    }

    private void findMinecraftProcess() {
        try {
            var pid = ProcessHandle.current().pid();
            log.info("Current process PID: {}", pid);
            ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c ->
                    c.contains("java") || c.contains("javaw")).orElse(false))
                .filter(p -> p.pid() != pid)
                .findFirst()
                .ifPresentOrElse(
                    p -> {
                        config.setPid((int) p.pid());
                        log.info("Found Minecraft process PID: {}", p.pid());
                    },
                    () -> log.warn("No Minecraft process found, running standalone")
                );
        } catch (Exception e) {
            log.warn("Could not find Minecraft process", e);
        }
    }

    private void setupWorkingDirectory() {
        var workDir = Path.of(config.getWorkingDir());
        if (!Files.exists(workDir)) {
            try {
                Files.createDirectories(workDir);
            } catch (Exception e) {
                log.error("Could not create working directory", e);
            }
        }
        System.setProperty("user.dir", workDir.toAbsolutePath().toString());
        log.info("Working directory: {}", workDir.toAbsolutePath());
    }

    private void checkResources() {
        var maxMem = Runtime.getRuntime().maxMemory();
        var freeDisk = new File(config.getWorkingDir()).getFreeSpace();

        log.info("Max heap: {} MB | Free disk: {} MB",
            maxMem / (1024 * 1024),
            freeDisk / (1024 * 1024));

        if (maxMem < 128 * 1024 * 1024) {
            log.warn("Very low memory! VPS Core may not work properly");
        }
    }

    public void destroy() {
        log.info("Bootstrap shutdown");
    }

    public HostingDetector.HostingInfo getHostingInfo() {
        return hostingInfo;
    }

    public VPSConfig getConfig() {
        return config;
    }
}
