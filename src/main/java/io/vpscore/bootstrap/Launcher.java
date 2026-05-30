package io.vpscore.bootstrap;

import io.vpscore.VPSCore;
import io.vpscore.bot.BotManager;
import io.vpscore.config.VPSConfig;
import io.vpscore.desktop.VpsDesktop;
import io.vpscore.fs.FileSystemManager;
import io.vpscore.monitor.ResourceMonitor;
import io.vpscore.net.NetworkManager;
import io.vpscore.security.AuditLogger;
import io.vpscore.security.AuthManager;
import io.vpscore.security.RateLimiter;
import io.vpscore.shell.ProcessManager;
import io.vpscore.shell.TerminalManager;
import io.vpscore.update.SelfUpdateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    private final VPSConfig config;
    private final Bootstrap bootstrap;
    private final List<AutoCloseable> services = new ArrayList<>();
    private volatile boolean running;

    private AuthManager authManager;
    private AuditLogger auditLogger;
    private RateLimiter rateLimiter;
    private ProcessManager processManager;
    private TerminalManager terminalManager;
    private FileSystemManager fileSystemManager;
    private NetworkManager networkManager;
    private ResourceMonitor resourceMonitor;
    private BotManager botManager;
    private SelfUpdateManager updateManager;
    private VpsDesktop desktop;

    public Launcher(VPSConfig config, Bootstrap bootstrap) {
        this.config = config;
        this.bootstrap = bootstrap;
    }

    public void launch() throws Exception {
        log.info("Launching VPS Core services...");
        running = true;

        if (!config.isMinimal()) {
            startSecurity();
            startFileSystem();
            startNetwork();
            startMonitoring();
            startBots();
            startUpdater();
        }

        startShell();

        startDesktop();

        log.info("All services launched");
    }

    private void startSecurity() throws Exception {
        auditLogger = new AuditLogger(config.getSecurity());
        auditLogger.start();
        services.add(auditLogger);

        rateLimiter = new RateLimiter(config.getSecurity());
        rateLimiter.start();
        services.add(rateLimiter);

        authManager = new AuthManager(config.getSecurity());
        authManager.start();
        services.add(authManager);

        log.info("Security services started");
    }

    private void startShell() throws Exception {
        processManager = new ProcessManager(config.getShell());
        processManager.start();
        services.add(processManager);

        terminalManager = new TerminalManager(config.getShell(), authManager, processManager);
        terminalManager.start();
        services.add(terminalManager);

        log.info("Shell services started");
    }

    private void startFileSystem() throws Exception {
        if (!config.getFs().isEnable()) return;
        fileSystemManager = new FileSystemManager(config.getFs(), authManager);
        fileSystemManager.start();
        services.add(fileSystemManager);
        log.info("File system services started");
    }

    private void startNetwork() throws Exception {
        if (!config.getNetwork().isEnable()) return;
        var webPort = config.getShell().getWebTerminalPort();
        networkManager = new NetworkManager(config.getNetwork(), authManager, webPort);
        networkManager.start();
        services.add(networkManager);
        log.info("Network services started");
    }

    private void startMonitoring() throws Exception {
        if (!config.getMonitor().isEnable()) return;
        resourceMonitor = new ResourceMonitor(config.getMonitor());
        resourceMonitor.start();
        services.add(resourceMonitor);
        log.info("Monitoring services started");
    }

    private void startBots() throws Exception {
        if (!config.getBot().isEnable()) return;
        botManager = new BotManager(config.getBot(), processManager);
        botManager.start();
        services.add(botManager);
        log.info("Bot services started");
    }

    private void startUpdater() throws Exception {
        if (!config.getUpdate().isEnable()) return;
        updateManager = new SelfUpdateManager(config.getUpdate());
        updateManager.start();
        services.add(updateManager);
        log.info("Update service started");
    }

    private void startDesktop() {
        desktop = new VpsDesktop();
        log.info("Desktop service initialized");
    }

    public void stop() {
        if (!running) return;
        running = false;
        log.info("Stopping VPS Core services...");
        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).close();
            } catch (Exception e) {
                log.warn("Error stopping service", e);
            }
        }
        log.info("All services stopped");
    }

    public AuthManager getAuthManager() { return authManager; }
    public ProcessManager getProcessManager() { return processManager; }
    public TerminalManager getTerminalManager() { return terminalManager; }
    public FileSystemManager getFileSystemManager() { return fileSystemManager; }
    public NetworkManager getNetworkManager() { return networkManager; }
    public ResourceMonitor getResourceMonitor() { return resourceMonitor; }
    public BotManager getBotManager() { return botManager; }
    public VpsDesktop getDesktop() { return desktop; }
}
