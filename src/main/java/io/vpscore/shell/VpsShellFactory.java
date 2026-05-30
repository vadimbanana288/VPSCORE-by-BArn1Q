package io.vpscore.shell;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class VpsShellFactory implements ShellFactory {

    private final String shell;

    public VpsShellFactory(String shell) {
        this.shell = shell;
    }

    @Override
    public Command createShell(ChannelSession channel) {
        return new VpsShellCommand(shell);
    }

    static class VpsShellCommand implements Command {
        private final String shell;
        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Process process;
        private volatile boolean running;

        VpsShellCommand(String shell) {
            this.shell = shell;
        }

        @Override
        public void setInputStream(InputStream in) { this.in = in; }

        @Override
        public void setOutputStream(OutputStream out) { this.out = out; }

        @Override
        public void setErrorStream(OutputStream err) { this.err = err; }

        @Override
        public void setExitCallback(ExitCallback callback) { this.callback = callback; }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            running = true;
            ProcessBuilder pb = new ProcessBuilder(shell, "-i");
            Map<String, String> procEnv = pb.environment();
            procEnv.putAll(env.getEnv());
            procEnv.put("USER", "root");
            procEnv.put("HOSTNAME", "vps");
            procEnv.put("HOME", "/home/container");
            procEnv.put("SHELL", shell);
            procEnv.put("TERM", env.getEnv().getOrDefault("TERM", "xterm-256color"));
            procEnv.put("LANG", "en_US.UTF-8");
            procEnv.put("POSIXLY_CORRECT", "1");
            procEnv.put("PS1", "\\[\\033[01;32m\\]root@vps\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ ");

            process = pb.start();

            Thread inThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int len;
                    while (running && (len = in.read(buf)) != -1) {
                        process.getOutputStream().write(buf, 0, len);
                        process.getOutputStream().flush();
                    }
                } catch (IOException ignored) { }
            }, "vps-stdin");
            inThread.setDaemon(true);
            inThread.start();

            Thread outThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int len;
                    while (running && (len = process.getInputStream().read(buf)) != -1) {
                        for (int i = 0; i < len; i++) {
                            if (buf[i] == '\n') {
                                out.write('\r');
                            }
                            out.write(buf[i]);
                        }
                        out.flush();
                    }
                } catch (IOException ignored) { }
            }, "vps-stdout");
            outThread.setDaemon(true);
            outThread.start();

            Thread errThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int len;
                    while (running && (len = process.getErrorStream().read(buf)) != -1) {
                        for (int i = 0; i < len; i++) {
                            if (buf[i] == '\n') {
                                err.write('\r');
                            }
                            err.write(buf[i]);
                        }
                        err.flush();
                    }
                } catch (IOException ignored) { }
            }, "vps-stderr");
            errThread.setDaemon(true);
            errThread.start();

            Thread waitThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    running = false;
                    callback.onExit(exitCode);
                } catch (InterruptedException ignored) { }
            }, "vps-wait");
            waitThread.setDaemon(true);
            waitThread.start();
        }

        @Override
        public void destroy(ChannelSession channel) {
            running = false;
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }
}
