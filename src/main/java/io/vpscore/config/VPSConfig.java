package io.vpscore.config;

import io.vpscore.bootstrap.Mode;

import java.util.*;

public class VPSConfig {

    private Mode mode = Mode.STANDALONE;
    private int pid = -1;
    private String javaHome = System.getProperty("java.home");
    private String workingDir = System.getProperty("user.dir");
    private String tempDir = System.getProperty("java.io.tmpdir");
    private boolean minimal = false;

    private final ShellConfig shell = new ShellConfig();
    private final NetworkConfig network = new NetworkConfig();
    private final FileSystemConfig fs = new FileSystemConfig();
    private final MonitorConfig monitor = new MonitorConfig();
    private final BotConfig bot = new BotConfig();
    private final SecurityConfig security = new SecurityConfig();
    private final HostingConfig hosting = new HostingConfig();
    private final UpdateConfig update = new UpdateConfig();

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }
    public String getJavaHome() { return javaHome; }
    public void setJavaHome(String javaHome) { this.javaHome = javaHome; }
    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }
    public String getTempDir() { return tempDir; }
    public void setTempDir(String tempDir) { this.tempDir = tempDir; }
    public boolean isMinimal() { return minimal; }
    public void setMinimal(boolean minimal) { this.minimal = minimal; }

    public ShellConfig getShell() { return shell; }
    public NetworkConfig getNetwork() { return network; }
    public FileSystemConfig getFs() { return fs; }
    public MonitorConfig getMonitor() { return monitor; }
    public BotConfig getBot() { return bot; }
    public SecurityConfig getSecurity() { return security; }
    public HostingConfig getHosting() { return hosting; }
    public UpdateConfig getUpdate() { return update; }

    public static class ShellConfig {
        private boolean enable = true;
        private int sshPort = 8022;
        private boolean sshEnabled = true;
        private int telnetPort = 8023;
        private boolean telnetEnabled = true;
        private int webTerminalPort = 8080;
        private boolean webTerminalEnabled = true;
        private String defaultShell = System.getProperty("os.name").toLowerCase().contains("win") ? "cmd.exe" : "/bin/bash";
        private int maxProcesses = 50;

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public int getSshPort() { return sshPort; }
        public void setSshPort(int v) { sshPort = v; }
        public boolean isSshEnabled() { return sshEnabled; }
        public void setSshEnabled(boolean v) { sshEnabled = v; }
        public int getTelnetPort() { return telnetPort; }
        public void setTelnetPort(int v) { telnetPort = v; }
        public boolean isTelnetEnabled() { return telnetEnabled; }
        public void setTelnetEnabled(boolean v) { telnetEnabled = v; }
        public int getWebTerminalPort() { return webTerminalPort; }
        public void setWebTerminalPort(int v) { webTerminalPort = v; }
        public boolean isWebTerminalEnabled() { return webTerminalEnabled; }
        public void setWebTerminalEnabled(boolean v) { webTerminalEnabled = v; }
        public String getDefaultShell() { return defaultShell; }
        public void setDefaultShell(String v) { defaultShell = v; }
        public int getMaxProcesses() { return maxProcesses; }
        public void setMaxProcesses(int v) { maxProcesses = v; }
    }

    public static class NetworkConfig {
        private boolean enable = true;
        private int proxyStartPort = 25565;
        private int proxyEndPort = 25600;
        private boolean firewallEnable = true;
        private boolean dnsEnable = false;
        private int dnsPort = 53;
        private boolean vpnEnable = false;
        private boolean reverseProxyEnable = true;
        private int reverseProxyHttpPort = 80;
        private int reverseProxyHttpsPort = 443;
        private boolean autoTls = true;
        private String domain = "";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public int getProxyStartPort() { return proxyStartPort; }
        public void setProxyStartPort(int v) { proxyStartPort = v; }
        public int getProxyEndPort() { return proxyEndPort; }
        public void setProxyEndPort(int v) { proxyEndPort = v; }
        public boolean isFirewallEnable() { return firewallEnable; }
        public void setFirewallEnable(boolean v) { firewallEnable = v; }
        public boolean isDnsEnable() { return dnsEnable; }
        public void setDnsEnable(boolean v) { dnsEnable = v; }
        public int getDnsPort() { return dnsPort; }
        public void setDnsPort(int v) { dnsPort = v; }
        public boolean isVpnEnable() { return vpnEnable; }
        public void setVpnEnable(boolean v) { vpnEnable = v; }
        public boolean isReverseProxyEnable() { return reverseProxyEnable; }
        public void setReverseProxyEnable(boolean v) { reverseProxyEnable = v; }
        public int getReverseProxyHttpPort() { return reverseProxyHttpPort; }
        public void setReverseProxyHttpPort(int v) { reverseProxyHttpPort = v; }
        public int getReverseProxyHttpsPort() { return reverseProxyHttpsPort; }
        public void setReverseProxyHttpsPort(int v) { reverseProxyHttpsPort = v; }
        public boolean isAutoTls() { return autoTls; }
        public void setAutoTls(boolean v) { autoTls = v; }
        public String getDomain() { return domain; }
        public void setDomain(String v) { domain = v; }
    }

    public static class FileSystemConfig {
        private boolean enable = true;
        private boolean sandboxEnable = false;
        private String sandboxPath = "";
        private int sftpPort = 8024;
        private boolean sftpEnabled = true;
        private int webdavPort = 8025;
        private boolean webdavEnabled = false;
        private boolean backupsEnable = true;
        private String backupInterval = "0 0 * * *";
        private String backupDir = "backups";
        private String backupCloudUrl = "";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public boolean isSandboxEnable() { return sandboxEnable; }
        public void setSandboxEnable(boolean v) { sandboxEnable = v; }
        public String getSandboxPath() { return sandboxPath; }
        public void setSandboxPath(String v) { sandboxPath = v; }
        public int getSftpPort() { return sftpPort; }
        public void setSftpPort(int v) { sftpPort = v; }
        public boolean isSftpEnabled() { return sftpEnabled; }
        public void setSftpEnabled(boolean v) { sftpEnabled = v; }
        public int getWebdavPort() { return webdavPort; }
        public void setWebdavPort(int v) { webdavPort = v; }
        public boolean isWebdavEnabled() { return webdavEnabled; }
        public void setWebdavEnabled(boolean v) { webdavEnabled = v; }
        public boolean isBackupsEnable() { return backupsEnable; }
        public void setBackupsEnable(boolean v) { backupsEnable = v; }
        public String getBackupInterval() { return backupInterval; }
        public void setBackupInterval(String v) { backupInterval = v; }
        public String getBackupDir() { return backupDir; }
        public void setBackupDir(String v) { backupDir = v; }
        public String getBackupCloudUrl() { return backupCloudUrl; }
        public void setBackupCloudUrl(String v) { backupCloudUrl = v; }
    }

    public static class MonitorConfig {
        private boolean enable = true;
        private int metricsPort = 9090;
        private boolean prometheusEnable = true;
        private int cpuLimit = 100;
        private long ramLimitMb = 0;
        private long diskLimitMb = 0;
        private boolean cgroupsEnable = true;

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public int getMetricsPort() { return metricsPort; }
        public void setMetricsPort(int v) { metricsPort = v; }
        public boolean isPrometheusEnable() { return prometheusEnable; }
        public void setPrometheusEnable(boolean v) { prometheusEnable = v; }
        public int getCpuLimit() { return cpuLimit; }
        public void setCpuLimit(int v) { cpuLimit = v; }
        public long getRamLimitMb() { return ramLimitMb; }
        public void setRamLimitMb(long v) { ramLimitMb = v; }
        public long getDiskLimitMb() { return diskLimitMb; }
        public void setDiskLimitMb(long v) { diskLimitMb = v; }
        public boolean isCgroupsEnable() { return cgroupsEnable; }
        public void setCgroupsEnable(boolean v) { cgroupsEnable = v; }
    }

    public static class BotConfig {
        private boolean enable = false;
        private String telegramToken = "";
        private String discordToken = "";
        private String slackToken = "";
        private String adminChatId = "";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public String getTelegramToken() { return telegramToken; }
        public void setTelegramToken(String v) { telegramToken = v; }
        public String getDiscordToken() { return discordToken; }
        public void setDiscordToken(String v) { discordToken = v; }
        public String getSlackToken() { return slackToken; }
        public void setSlackToken(String v) { slackToken = v; }
        public String getAdminChatId() { return adminChatId; }
        public void setAdminChatId(String v) { adminChatId = v; }
    }

    public static class SecurityConfig {
        private boolean enable = true;
        private boolean authRequired = true;
        private String authMode = "password";
        private String password = "vpscore";
        private boolean totpRequired = false;
        private int maxLoginAttempts = 5;
        private int banDurationMinutes = 30;
        private boolean auditLog = true;
        private String auditLogFile = "audit.log";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public boolean isAuthRequired() { return authRequired; }
        public void setAuthRequired(boolean v) { authRequired = v; }
        public String getAuthMode() { return authMode; }
        public void setAuthMode(String v) { authMode = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { password = v; }
        public boolean isTotpRequired() { return totpRequired; }
        public void setTotpRequired(boolean v) { totpRequired = v; }
        public int getMaxLoginAttempts() { return maxLoginAttempts; }
        public void setMaxLoginAttempts(int v) { maxLoginAttempts = v; }
        public int getBanDurationMinutes() { return banDurationMinutes; }
        public void setBanDurationMinutes(int v) { banDurationMinutes = v; }
        public boolean isAuditLog() { return auditLog; }
        public void setAuditLog(boolean v) { auditLog = v; }
        public String getAuditLogFile() { return auditLogFile; }
        public void setAuditLogFile(String v) { auditLogFile = v; }
    }

    public static class HostingConfig {
        private boolean enable = true;
        private boolean autoDetect = true;
        private boolean bypassRestrictions = true;
        private boolean hideProcesses = true;
        private String transportMode = "auto";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public boolean isAutoDetect() { return autoDetect; }
        public void setAutoDetect(boolean v) { autoDetect = v; }
        public boolean isBypassRestrictions() { return bypassRestrictions; }
        public void setBypassRestrictions(boolean v) { bypassRestrictions = v; }
        public boolean isHideProcesses() { return hideProcesses; }
        public void setHideProcesses(boolean v) { hideProcesses = v; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String v) { transportMode = v; }
    }

    public static class UpdateConfig {
        private boolean enable = true;
        private boolean autoUpdate = true;
        private String updateUrl = "https://github.com/user/vpscore/releases/latest/download/vpscore.jar";
        private String updateCheckInterval = "0 0 * * *";
        private String currentVersion = "1.0.0";

        public boolean isEnable() { return enable; }
        public void setEnable(boolean v) { enable = v; }
        public boolean isAutoUpdate() { return autoUpdate; }
        public void setAutoUpdate(boolean v) { autoUpdate = v; }
        public String getUpdateUrl() { return updateUrl; }
        public void setUpdateUrl(String v) { updateUrl = v; }
        public String getUpdateCheckInterval() { return updateCheckInterval; }
        public void setUpdateCheckInterval(String v) { updateCheckInterval = v; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String v) { currentVersion = v; }
    }
}
