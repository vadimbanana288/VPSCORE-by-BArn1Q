package io.vpscore.config;

import io.vpscore.bootstrap.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "vpscore.yml";

    private static boolean configLoadedFromFile = false;

    public static VPSConfig load(String[] args) {
        var config = new VPSConfig();
        var parsedArgs = parseArgs(args);

        if (parsedArgs.containsKey("config")) {
            loadFromFile(parsedArgs.get("config"), config);
            configLoadedFromFile = true;
        } else if (Files.exists(Path.of(CONFIG_FILE))) {
            loadFromFile(CONFIG_FILE, config);
            configLoadedFromFile = true;
        } else {
            generateDefaultConfig();
        }

        // Авто-порты Pterodactyl — только если нет своего конфига
        if (!configLoadedFromFile) {
            applyPterodactylPorts(config);
        }
        applyCliOverrides(parsedArgs, config);

        // Если режим не задан — авто-standalone
        if (!parsedArgs.containsKey("mode") && config.getMode() == null) {
            config.setMode(Mode.STANDALONE);
        }

        return config;
    }

    private static Map<String, String> parseArgs(String[] args) {
        var map = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--standalone" -> map.put("mode", "standalone");
                case "--attach" -> { map.put("mode", "attach"); if (i + 1 < args.length) map.put("pid", args[++i]); }
                case "--wrapper" -> map.put("mode", "wrapper");
                case "--minimal" -> map.put("minimal", "true");
                case "--config" -> { if (i + 1 < args.length) map.put("config", args[++i]); }
                case "--pterodactyl-ports" -> { if (i + 1 < args.length) map.put("pterodactyl_ports", args[++i]); }
                default -> {
                    if (args[i].startsWith("--")) {
                        var kv = args[i].substring(2).split("=", 2);
                        if (kv.length == 2) map.put(kv[0], kv[1]);
                    }
                }
            }
        }
        return map;
    }

    private static void loadFromFile(String path, VPSConfig config) {
        try {
            var yaml = new Yaml();
            try (var input = new FileInputStream(path)) {
                var data = yaml.load(input);
                if (data instanceof Map<?, ?> map) {
                    applyYamlToConfig(map, config);
                }
            }
            log.info("Loaded config from {}", path);
        } catch (Exception e) {
            log.error("Failed to load config from {}", path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyYamlToConfig(Map<?, ?> yaml, VPSConfig config) {
        if (yaml.get("mode") instanceof String m) config.setMode(Mode.fromString(m));
        if (yaml.get("minimal") instanceof Boolean b) config.setMinimal(b);
        if (yaml.get("pid") instanceof Integer p) config.setPid(p);
        if (yaml.get("working_dir") instanceof String d) config.setWorkingDir(d);
        if (yaml.get("java_home") instanceof String j) config.setJavaHome(j);

        if (yaml.get("shell") instanceof Map<?, ?> s) applyShell(s, config.getShell());
        if (yaml.get("network") instanceof Map<?, ?> n) applyNetwork(n, config.getNetwork());
        if (yaml.get("filesystem") instanceof Map<?, ?> f) applyFS(f, config.getFs());
        if (yaml.get("monitor") instanceof Map<?, ?> m) applyMonitor(m, config.getMonitor());
        if (yaml.get("bot") instanceof Map<?, ?> b) applyBot(b, config.getBot());
        if (yaml.get("security") instanceof Map<?, ?> se) applySecurity(se, config.getSecurity());
        if (yaml.get("hosting") instanceof Map<?, ?> h) applyHosting(h, config.getHosting());
        if (yaml.get("update") instanceof Map<?, ?> u) applyUpdate(u, config.getUpdate());
    }

    private static void applyShell(Map<?, ?> s, VPSConfig.ShellConfig c) {
        if (s.get("enable") instanceof Boolean v) c.setEnable(v);
        if (s.get("ssh_port") instanceof Integer v) c.setSshPort(v);
        if (s.get("ssh_enabled") instanceof Boolean v) c.setSshEnabled(v);
        if (s.get("telnet_port") instanceof Integer v) c.setTelnetPort(v);
        if (s.get("telnet_enabled") instanceof Boolean v) c.setTelnetEnabled(v);
        if (s.get("web_terminal_port") instanceof Integer v) c.setWebTerminalPort(v);
        if (s.get("web_terminal_enabled") instanceof Boolean v) c.setWebTerminalEnabled(v);
        if (s.get("default_shell") instanceof String v) c.setDefaultShell(v);
        if (s.get("max_processes") instanceof Integer v) c.setMaxProcesses(v);
    }

    private static void applyNetwork(Map<?, ?> n, VPSConfig.NetworkConfig c) {
        if (n.get("enable") instanceof Boolean v) c.setEnable(v);
        if (n.get("proxy_start_port") instanceof Integer v) c.setProxyStartPort(v);
        if (n.get("proxy_end_port") instanceof Integer v) c.setProxyEndPort(v);
        if (n.get("firewall_enable") instanceof Boolean v) c.setFirewallEnable(v);
        if (n.get("dns_enable") instanceof Boolean v) c.setDnsEnable(v);
        if (n.get("dns_port") instanceof Integer v) c.setDnsPort(v);
        if (n.get("reverse_proxy_enable") instanceof Boolean v) c.setReverseProxyEnable(v);
        if (n.get("auto_tls") instanceof Boolean v) c.setAutoTls(v);
        if (n.get("domain") instanceof String v) c.setDomain(v);
    }

    private static void applyFS(Map<?, ?> f, VPSConfig.FileSystemConfig c) {
        if (f.get("enable") instanceof Boolean v) c.setEnable(v);
        if (f.get("sandbox_enable") instanceof Boolean v) c.setSandboxEnable(v);
        if (f.get("sftp_port") instanceof Integer v) c.setSftpPort(v);
        if (f.get("sftp_enabled") instanceof Boolean v) c.setSftpEnabled(v);
        if (f.get("webdav_port") instanceof Integer v) c.setWebdavPort(v);
        if (f.get("backups_enable") instanceof Boolean v) c.setBackupsEnable(v);
        if (f.get("backup_dir") instanceof String v) c.setBackupDir(v);
    }

    private static void applyMonitor(Map<?, ?> m, VPSConfig.MonitorConfig c) {
        if (m.get("enable") instanceof Boolean v) c.setEnable(v);
        if (m.get("metrics_port") instanceof Integer v) c.setMetricsPort(v);
        if (m.get("prometheus_enable") instanceof Boolean v) c.setPrometheusEnable(v);
        if (m.get("cpu_limit") instanceof Integer v) c.setCpuLimit(v);
        if (m.get("ram_limit_mb") instanceof Integer v) c.setRamLimitMb(v.longValue());
    }

    private static void applyBot(Map<?, ?> b, VPSConfig.BotConfig c) {
        if (b.get("enable") instanceof Boolean v) c.setEnable(v);
        if (b.get("telegram_token") instanceof String v) c.setTelegramToken(v);
        if (b.get("discord_token") instanceof String v) c.setDiscordToken(v);
    }

    private static void applySecurity(Map<?, ?> s, VPSConfig.SecurityConfig c) {
        if (s.get("enable") instanceof Boolean v) c.setEnable(v);
        if (s.get("auth_required") instanceof Boolean v) c.setAuthRequired(v);
        if (s.get("auth_mode") instanceof String v) c.setAuthMode(v);
        if (s.get("password") instanceof String v) c.setPassword(v);
        if (s.get("totp_required") instanceof Boolean v) c.setTotpRequired(v);
        if (s.get("max_login_attempts") instanceof Integer v) c.setMaxLoginAttempts(v);
    }

    private static void applyHosting(Map<?, ?> h, VPSConfig.HostingConfig c) {
        if (h.get("enable") instanceof Boolean v) c.setEnable(v);
        if (h.get("auto_detect") instanceof Boolean v) c.setAutoDetect(v);
        if (h.get("bypass_restrictions") instanceof Boolean v) c.setBypassRestrictions(v);
        if (h.get("hide_processes") instanceof Boolean v) c.setHideProcesses(v);
    }

    private static void applyUpdate(Map<?, ?> u, VPSConfig.UpdateConfig c) {
        if (u.get("enable") instanceof Boolean v) c.setEnable(v);
        if (u.get("auto_update") instanceof Boolean v) c.setAutoUpdate(v);
        if (u.get("update_url") instanceof String v) c.setUpdateUrl(v);
    }

    private static void applyCliOverrides(Map<String, String> args, VPSConfig config) {
        if (args.containsKey("mode")) config.setMode(Mode.fromString(args.get("mode")));
        if (args.containsKey("pid")) config.setPid(Integer.parseInt(args.get("pid")));
        if (args.containsKey("minimal")) config.setMinimal(true);

        if (args.containsKey("ssh-port")) config.getShell().setSshPort(Integer.parseInt(args.get("ssh-port")));
        if (args.containsKey("telnet-port")) config.getShell().setTelnetPort(Integer.parseInt(args.get("telnet-port")));
        if (args.containsKey("web-port")) config.getShell().setWebTerminalPort(Integer.parseInt(args.get("web-port")));
        if (args.containsKey("metrics-port")) config.getMonitor().setMetricsPort(Integer.parseInt(args.get("metrics-port")));
        if (args.containsKey("password")) config.getSecurity().setPassword(args.get("password"));

        // Override Pterodactyl allocated ports from CLI
        if (args.containsKey("pterodactyl_ports")) {
            System.setProperty(io.vpscore.hosting.HostingDetector.ENV_PTERODACTYL, args.get("pterodactyl_ports"));
        }
    }

    private static void applyPterodactylPorts(VPSConfig config) {
        var ports = io.vpscore.hosting.HostingDetector.getPterodactylPorts();
        if (ports == null || ports.isEmpty()) return;

        log.info("Pterodactyl allocated ports: {}. Auto-configuring services...", ports);

        var base = ports.get(0);
        config.getShell().setWebTerminalPort(base);

        if (ports.size() >= 2) {
            config.getShell().setSshPort(ports.get(1));
        } else {
            config.getShell().setSshPort(base + 1);
        }

        if (ports.size() >= 3) {
            config.getShell().setTelnetPort(ports.get(2));
        } else {
            config.getShell().setTelnetPort(base + 2);
        }

        if (ports.size() >= 4) {
            config.getFs().setSftpPort(ports.get(3));
        } else {
            config.getFs().setSftpPort(base + 3);
        }

        if (ports.size() >= 5) {
            config.getMonitor().setMetricsPort(ports.get(4));
        } else {
            config.getMonitor().setMetricsPort(base + 4);
        }

        if (ports.size() >= 6) {
            config.getFs().setWebdavPort(ports.get(5));
        } else {
            config.getFs().setWebdavPort(base + 5);
        }

        config.getNetwork().setProxyStartPort(base + 10);
        config.getNetwork().setProxyEndPort(base + 20);

        log.info("VPS Core services configured on ports: web={}, ssh={}, telnet={}, sftp={}, metrics={}, webdav={}",
            config.getShell().getWebTerminalPort(),
            config.getShell().getSshPort(),
            config.getShell().getTelnetPort(),
            config.getFs().getSftpPort(),
            config.getMonitor().getMetricsPort(),
            config.getFs().getWebdavPort());
    }

    private static void generateDefaultConfig() {
        var path = Path.of(CONFIG_FILE);
        if (Files.exists(path)) return;
        try {
            try (var in = ConfigManager.class.getResourceAsStream("/vpscore.yml")) {
                if (in != null) Files.copy(in, path);
            }
            log.info("Generated default config at {}", path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not generate default config", e);
        }
    }
}
