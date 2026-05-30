package io.vpscore.shell;

import io.vpscore.config.VPSConfig.ShellConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

    private final ShellConfig config;
    private final Map<Integer, ManagedProcess> processes = new ConcurrentHashMap<>();
    private final AtomicInteger pidCounter = new AtomicInteger(1000);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "vps-proc-" + pidCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;

    public ProcessManager(ShellConfig config) {
        this.config = config;
    }

    public void start() {
        running = true;
        log.info("Process manager started (max: {})", config.getMaxProcesses());
    }

    public int execute(String command, boolean background) throws IOException {
        return execute(command, background, null);
    }

    public int execute(String command, boolean background, String stdin) throws IOException {
        if (processes.size() >= config.getMaxProcesses()) {
            throw new IllegalStateException("Max processes limit reached: " + config.getMaxProcesses());
        }

        var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        var builder = new ProcessBuilder();
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("/bin/bash", "-c", command);
        }
        builder.redirectErrorStream(false);
        var process = builder.start();

        var pid = (int) process.pid();
        var mp = new ManagedProcess(pid, command, process, System.currentTimeMillis());

        if (background) {
            processes.put(pid, mp);
            executor.submit(() -> {
                try {
                    readStream(mp, process.getInputStream(), false);
                } catch (Exception e) {
                    log.warn("Process {} stdout error", pid, e);
                }
            });
            executor.submit(() -> {
                try {
                    readStream(mp, process.getErrorStream(), true);
                } catch (Exception e) {
                    log.warn("Process {} stderr error", pid, e);
                }
            });
            if (stdin != null && process.getOutputStream() != null) {
                try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(stdin);
                    writer.flush();
                }
            }
            executor.submit(() -> {
                try {
                    var exitCode = process.waitFor();
                    mp.setExitCode(exitCode);
                    mp.setEndTime(System.currentTimeMillis());
                    log.debug("Process {} exited with code {}", pid, exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return pid;
        }

        processes.put(pid, mp);
        executor.submit(() -> readStream(mp, process.getInputStream(), false));
        executor.submit(() -> readStream(mp, process.getErrorStream(), true));

        if (stdin != null && process.getOutputStream() != null) {
            try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(stdin);
                writer.flush();
            }
        }

        return pid;
    }

    public int executeAndWait(String command, long timeoutMs) throws IOException, TimeoutException {
        var pid = execute(command, true, null);
        var mp = processes.get(pid);
        if (mp == null) return -1;
        try {
            if (timeoutMs > 0) {
                mp.process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                mp.process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return mp.exitCode;
    }

    private void readStream(ManagedProcess mp, InputStream stream, boolean isError) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isError) {
                    mp.addStderr(line);
                } else {
                    mp.addStdout(line);
                }
            }
        } catch (IOException e) {
            if (running) log.debug("Stream read error for process {}", mp.pid, e);
        }
    }

    public boolean kill(int pid) {
        var mp = processes.get(pid);
        if (mp == null) return false;
        mp.process.destroyForcibly();
        return true;
    }

    public boolean kill(int pid, boolean force) {
        var mp = processes.get(pid);
        if (mp == null) return false;
        if (force) mp.process.destroyForcibly();
        else mp.process.destroy();
        return true;
    }

    public List<ManagedProcess> listProcesses() {
        return List.copyOf(processes.values());
    }

    public ManagedProcess getProcess(int pid) {
        return processes.get(pid);
    }

    @Override
    public void close() {
        running = false;
        processes.values().forEach(mp -> mp.process.destroyForcibly());
        processes.clear();
        executor.shutdownNow();
        log.info("Process manager stopped");
    }

    public static class ManagedProcess {
        private final int pid;
        private final String command;
        private final Process process;
        private final long startTime;
        private long endTime;
        private int exitCode = -1;
        private final List<String> stdout = new ArrayList<>();
        private final List<String> stderr = new ArrayList<>();

        public ManagedProcess(int pid, String command, Process process, long startTime) {
            this.pid = pid;
            this.command = command;
            this.process = process;
            this.startTime = startTime;
        }

        public void setExitCode(int code) { this.exitCode = code; }
        public void setEndTime(long t) { this.endTime = t; }
        public void addStdout(String line) { stdout.add(line); }
        public void addStderr(String line) { stderr.add(line); }

        public int getPid() { return pid; }
        public String getCommand() { return command; }
        public Process getProcess() { return process; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public int getExitCode() { return exitCode; }
        public boolean isRunning() { return process.isAlive(); }
        public List<String> getStdout() { return List.copyOf(stdout); }
        public List<String> getStderr() { return List.copyOf(stderr); }
        public long getUptime() {
            return isRunning() ? System.currentTimeMillis() - startTime : endTime - startTime;
        }
    }
}
