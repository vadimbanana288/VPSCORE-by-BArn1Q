package io.vpscore.bot;

import io.vpscore.VPSCore;
import io.vpscore.config.VPSConfig.BotConfig;
import io.vpscore.shell.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class BotManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BotManager.class);

    private final BotConfig config;
    private final ProcessManager processManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Bot> bots = new ArrayList<>();
    private volatile boolean running;

    public BotManager(BotConfig config, ProcessManager processManager) {
        this.config = config;
        this.processManager = processManager;
    }

    public void start() {
        running = true;

        if (config.getTelegramToken() != null && !config.getTelegramToken().isEmpty()) {
            var tg = new TelegramBot(config.getTelegramToken(), config.getAdminChatId());
            bots.add(tg);
            executor.submit(tg::start);
            log.info("Telegram bot started");
        }

        if (config.getDiscordToken() != null && !config.getDiscordToken().isEmpty()) {
            var dc = new DiscordBot(config.getDiscordToken());
            bots.add(dc);
            executor.submit(dc::start);
            log.info("Discord bot started");
        }

        log.info("Bot manager started");
    }

    public void broadcast(String message) {
        for (var bot : bots) {
            bot.sendMessage(message);
        }
    }

    @Override
    public void close() {
        running = false;
        for (var bot : bots) bot.stop();
        executor.shutdownNow();
        log.info("Bot manager stopped");
    }

    interface Bot {
        void start();
        void stop();
        void sendMessage(String message);
    }

    static class TelegramBot implements Bot {
        private final String token;
        private final String adminChatId;

        TelegramBot(String token, String adminChatId) {
            this.token = token;
            this.adminChatId = adminChatId;
        }

        public void start() {
            log.debug("Telegram bot connecting...");
        }

        public void stop() {
            log.debug("Telegram bot disconnected");
        }

        public void sendMessage(String message) {
            log.debug("Telegram message sent: {}", message);
        }

        private void handleCommand(String cmd) {
            try {
                var pm = VPSCore.getInstance().getLauncher().getProcessManager();
                if (cmd.startsWith("/vps shell ")) {
                    var shellCmd = cmd.substring("/vps shell ".length());
                    var pid = pm.execute(shellCmd, true);
                    log.info("Telegram exec: {} (pid={})", shellCmd, pid);
                } else if (cmd.startsWith("/vps fs ls ")) {
                    var path = cmd.substring("/vps fs ls ".length());
                    var fs = VPSCore.getInstance().getLauncher().getFileSystemManager();
                    if (fs != null) {
                        var entries = fs.list(path);
                        var sb = new StringBuilder("Files in ").append(path).append(":\n");
                        for (var e : entries) {
                            sb.append(e.isDirectory() ? "[DIR] " : "[FILE] ")
                                .append(e.name()).append(" (").append(e.size()).append(" bytes)\n");
                        }
                    }
                } else if (cmd.equals("/vps stats")) {
                    var monitor = VPSCore.getInstance().getLauncher().getResourceMonitor();
                    if (monitor != null) {
                        var snap = monitor.getSnapshot();
                        var msg = String.format("""
                            CPU: %.1f%%
                            Memory: %dMB / %dMB
                            Disk: %dMB / %dMB
                            Threads: %d
                            """, snap.cpuUsage(), snap.memoryUsed() / (1024*1024),
                            snap.memoryMax() / (1024*1024),
                            snap.diskFree() / (1024*1024), snap.diskTotal() / (1024*1024),
                            snap.threadCount());
                        sendMessage(msg);
                    }
                } else if (cmd.equals("/vps help")) {
                    sendMessage("""
                        /vps shell <cmd> - Execute command
                        /vps fs ls <path> - List files
                        /vps stats - System stats
                        /vps restart - Restart VPS Core
                        /vps update - Update VPS Core
                        """);
                }
            } catch (Exception e) {
                log.error("Telegram command error", e);
            }
        }
    }

    static class DiscordBot implements Bot {
        private final String token;

        DiscordBot(String token) {
            this.token = token;
        }

        public void start() {
            log.debug("Discord bot connecting...");
        }

        public void stop() {
            log.debug("Discord bot disconnected");
        }

        public void sendMessage(String message) {
            log.debug("Discord message sent: {}", message);
        }
    }
}
