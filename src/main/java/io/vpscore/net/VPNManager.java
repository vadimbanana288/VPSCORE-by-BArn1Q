package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VPNManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VPNManager.class);

    private final NetworkConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    public VPNManager(NetworkConfig config) {
        this.config = config;
    }

    public void start() {
        if (!config.isVpnEnable()) return;
        running = true;
        executor.submit(this::setupVPN);
        log.info("VPN manager started");
    }

    private void setupVPN() {
        try {
            var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                log.info("Windows VPN setup would be configured here");
            } else {
                var pb = new ProcessBuilder("wg-quick", "up", "vpscore0");
                pb.inheritIO();
                var process = pb.start();
                var exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("WireGuard VPN interface 'vpscore0' created");
                } else {
                    log.warn("WireGuard setup returned exit code: {}", exitCode);
                }
            }
        } catch (Exception e) {
            log.warn("Could not set up VPN (may not be available on this host)", e);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            var pb = new ProcessBuilder("wg-quick", "down", "vpscore0");
            pb.inheritIO();
            pb.start();
        } catch (Exception e) {
            log.debug("VPN cleanup error", e);
        }
        executor.shutdownNow();
    }
}
