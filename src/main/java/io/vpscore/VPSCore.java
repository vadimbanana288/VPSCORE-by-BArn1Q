package io.vpscore;

import io.vpscore.bootstrap.Bootstrap;
import io.vpscore.bootstrap.Launcher;
import io.vpscore.config.ConfigManager;
import io.vpscore.config.VPSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class VPSCore {

    private static final Logger log = LoggerFactory.getLogger(VPSCore.class);

    private static VPSCore instance;
    private final Bootstrap bootstrap;
    private final Launcher launcher;
    private volatile boolean running;

    public VPSCore(String[] args) {
        instance = this;
        this.running = true;
        var config = ConfigManager.load(args);
        this.bootstrap = new Bootstrap(config);
        this.launcher = new Launcher(config, bootstrap);
    }

    public static VPSCore getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        System.out.println("[VPS Core] Starting...");
        System.out.println("[VPS Core] Args: " + Arrays.toString(args));
        System.out.println("[VPS Core] Pterodactyl ports: " + System.getenv("PTERODACTYL_ALLOCATED_PORTS"));
        System.out.println("[VPS Core] Server port: " + System.getenv("SERVER_PORT"));
        log.info("VPS Core starting...");
        log.info("Arguments: {}", Arrays.toString(args));
        var core = new VPSCore(args);
        Runtime.getRuntime().addShutdownHook(new Thread(core::shutdown));
        core.start();
    }

    public void start() {
        try {
            bootstrap.init();
            launcher.launch();
            var config = bootstrap.getConfig();
            var port = config.getShell().getWebTerminalPort();
            System.out.println("[VPS Core]  _    ______  _____ __________  ____  ______      ");
            System.out.println("[VPS Core] | |  / / __ \\/ ___// ____/ __ \\/ __ \\/ ____/      ");
            System.out.println("[VPS Core] | | / / /_/ /\\__ \\/ /   / / / / /_/ / __/         ");
            System.out.println("[VPS Core] | |/ / ____/___/ / /___/ /_/ / _, _/ /___         ");
            System.out.println("[VPS Core] |___/_/    /____/\\____/\\____/_/ |_/_____/ _______ ");
            System.out.println("[VPS Core]    / /_  __  __   / __ )/   |  _________ <  / __ \\");
            System.out.println("[VPS Core]   / __ \\/ / / /  / __  / /| | / ___/ __ \\/ / / /");
            System.out.println("[VPS Core]  / /_/ / /_/ /  / /_/ / ___ |/ /  / / / / / /_/ / ");
            System.out.println("[VPS Core] /_.___/\\__, /  /_____/_/  |_/_/  /_/ /_/_/\\___\\_\\ ");
            System.out.println("[VPS Core]       /____/                                       ");
            System.out.println("[VPS Core] Web: http://0.0.0.0:" + port + "/terminal");
            System.out.println("[VPS Core] SSH: ssh root@HOST -p " + config.getShell().getSshPort());
            System.out.println("[VPS Core] API: http://0.0.0.0:" + port + "/api/health");
            log.info("VPS Core started successfully");
            waitForShutdown();
        } catch (Exception e) {
            System.out.println("[VPS Core] FAILED TO START: " + e.getMessage());
            e.printStackTrace(System.out);
            log.error("Failed to start VPS Core", e);
            System.exit(1);
        }
    }

    private void waitForShutdown() {
        synchronized (this) {
            while (running) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    shutdown();
                }
            }
        }
    }

    public void shutdown() {
        System.out.println("[VPS Core] Shutting down...");
        log.info("VPS Core shutting down...");
        running = false;
        if (launcher != null) launcher.stop();
        if (bootstrap != null) bootstrap.destroy();
        synchronized (this) {
            notifyAll();
        }
        System.out.println("[VPS Core] Stopped.");
        log.info("VPS Core stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }
}
