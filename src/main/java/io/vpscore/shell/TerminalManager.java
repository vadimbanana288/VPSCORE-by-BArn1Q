package io.vpscore.shell;

import io.vpscore.config.VPSConfig.ShellConfig;
import io.vpscore.security.AuthManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TerminalManager.class);

    private final ShellConfig config;
    private final AuthManager authManager;
    private final ProcessManager processManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;

    private SshServerComponent sshServer;

    public TerminalManager(ShellConfig config, AuthManager authManager, ProcessManager processManager) {
        this.config = config;
        this.authManager = authManager;
        this.processManager = processManager;
    }

    public void start() throws Exception {
        running = true;

        if (config.isSshEnabled()) {
            sshServer = new SshServerComponent(config, authManager);
            sshServer.start();
            System.out.println("[VPS Core] SSH server started on port " + config.getSshPort());
            log.info("SSH server started on port {}", config.getSshPort());
        }

        log.info("Terminal manager started");
    }

    @Override
    public void close() {
        running = false;
        if (sshServer != null) sshServer.stop();
        executor.shutdownNow();
        log.info("Terminal manager stopped");
    }

    static class SshServerComponent {
        private final ShellConfig config;
        private final AuthManager authManager;
        private SshServer sshServer;

        SshServerComponent(ShellConfig config, AuthManager authManager) {
            this.config = config;
            this.authManager = authManager;
        }

        void start() throws Exception {
            sshServer = SshServer.setUpDefaultServer();
            sshServer.setPort(config.getSshPort());
            sshServer.setHost("0.0.0.0");

            var keyPath = Path.of("ssh_host_rsa.key");
            sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(keyPath));

            sshServer.setPasswordAuthenticator((username, password, session) -> {
                var addr = session.getIoSession().getRemoteAddress().toString();
                if (addr.startsWith("/")) addr = addr.substring(1);
                var portIdx = addr.lastIndexOf(':');
                if (portIdx > 0) addr = addr.substring(0, portIdx);
                var ok = authManager.authenticate(username, password, addr);
                System.out.println("[VPS Core] SSH auth: user=" + username + " ip=" + addr + " result=" + ok);
                return ok;
            });

            var shell = findShell();
            System.out.println("[VPS Core] SSH using shell: " + shell);

            sshServer.setShellFactory(new VpsShellFactory(shell));

            sshServer.start();
            System.out.println("[VPS Core] SSH listening on 0.0.0.0:" + config.getSshPort());
            log.info("SSH server listening on 0.0.0.0:{}", config.getSshPort());
        }

        private String findShell() {
            var configured = config.getDefaultShell();
            if (configured != null && !configured.isBlank()) {
                var p = Path.of(configured);
                if (Files.exists(p)) return configured;
            }
            for (var s : new String[]{"/bin/bash", "/usr/bin/bash", "/bin/sh", "/usr/bin/sh"}) {
                if (Files.exists(Path.of(s))) return s;
            }
            if (System.getProperty("os.name").toLowerCase().contains("win")) return "cmd.exe";
            return "/bin/sh";
        }

        void stop() {
            if (sshServer != null) {
                try { sshServer.stop(true); } catch (Exception e) { log.warn("SSH stop error", e); }
            }
        }
    }
}
