package io.vpscore.monitor;

import io.vpscore.config.VPSConfig.MonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

public class ResourceLimiter {

    private static final Logger log = LoggerFactory.getLogger(ResourceLimiter.class);

    private final MonitorConfig config;

    public ResourceLimiter(MonitorConfig config) {
        this.config = config;
    }

    public void check() {
        if (config.getRamLimitMb() > 0) {
            checkMemory();
        }
        if (config.getDiskLimitMb() > 0) {
            checkDisk();
        }
    }

    private void checkMemory() {
        var usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        var usedMb = usedMem / (1024 * 1024);
        if (usedMb > config.getRamLimitMb()) {
            log.warn("Memory limit exceeded: {}MB > {}MB", usedMb, config.getRamLimitMb());
            applyCgroupMemoryLimit();
        }
    }

    private void checkDisk() {
        var disk = new java.io.File(System.getProperty("user.dir"));
        var usedBytes = disk.getTotalSpace() - disk.getFreeSpace();
        var usedMb = usedBytes / (1024 * 1024);
        if (usedMb > config.getDiskLimitMb()) {
            log.warn("Disk limit exceeded: {}MB > {}MB", usedMb, config.getDiskLimitMb());
        }
    }

    private void applyCgroupMemoryLimit() {
        if (!config.isCgroupsEnable()) return;
        try {
            var cgroupPath = Path.of("/sys/fs/cgroup/memory/vpscore/memory.limit_in_bytes");
            if (Files.exists(cgroupPath)) {
                Files.writeString(cgroupPath, String.valueOf(config.getRamLimitMb() * 1024 * 1024));
                log.info("Applied cgroup memory limit: {}MB", config.getRamLimitMb());
            }
        } catch (IOException e) {
            log.debug("Could not apply cgroup limit (not available)", e);
        }
    }
}
