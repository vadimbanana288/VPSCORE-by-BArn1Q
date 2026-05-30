package io.vpscore.fs;

import io.vpscore.config.VPSConfig.FileSystemConfig;
import io.vpscore.security.AuthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileSystemManager.class);

    private final FileSystemConfig config;
    private final AuthManager authManager;
    private volatile boolean running;

    private SftpServerComponent sftpServer;
    private WebdavServerComponent webdavServer;

    public FileSystemManager(FileSystemConfig config, AuthManager authManager) {
        this.config = config;
        this.authManager = authManager;
    }

    public void start() throws Exception {
        running = true;

        if (config.isSftpEnabled()) {
            sftpServer = new SftpServerComponent(config, authManager);
            sftpServer.start();
            log.info("SFTP server started on port {}", config.getSftpPort());
        }

        if (config.isWebdavEnabled()) {
            webdavServer = new WebdavServerComponent(config, authManager);
            webdavServer.start();
            log.info("WebDAV server started on port {}", config.getWebdavPort());
        }

        log.info("File system manager started");
    }

    public List<FileEntry> list(String path) throws IOException {
        var resolved = resolvePath(path);
        try (var files = Files.list(resolved)) {
            return files
                .map(p -> {
                    try {
                        var attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        return new FileEntry(
                            p.getFileName().toString(),
                            p.toAbsolutePath().toString(),
                            attrs.isDirectory(),
                            attrs.size(),
                            attrs.lastModifiedTime().toMillis(),
                            Files.isHidden(p)
                        );
                    } catch (IOException e) {
                        return new FileEntry(p.getFileName().toString(), p.toAbsolutePath().toString(), false, 0, 0, false);
                    }
                })
                .sorted((a, b) -> {
                    if (a.isDirectory() != b.isDirectory())
                        return a.isDirectory() ? -1 : 1;
                    return a.name().compareToIgnoreCase(b.name());
                })
                .collect(Collectors.toList());
        }
    }

    public String readFile(String path) throws IOException {
        return Files.readString(resolvePath(path), StandardCharsets.UTF_8);
    }

    public void writeFile(String path, String content) throws IOException {
        Files.writeString(resolvePath(path), content, StandardCharsets.UTF_8);
    }

    public void appendFile(String path, String content) throws IOException {
        Files.writeString(resolvePath(path), content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public boolean delete(String path) throws IOException {
        return Files.deleteIfExists(resolvePath(path));
    }

    public void move(String source, String dest) throws IOException {
        Files.move(resolvePath(source), resolvePath(dest), StandardCopyOption.REPLACE_EXISTING);
    }

    public void copy(String source, String dest) throws IOException {
        Files.copy(resolvePath(source), resolvePath(dest), StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean mkdir(String path) throws IOException {
        Files.createDirectories(resolvePath(path));
        return true;
    }

    public long size(String path) throws IOException {
        var p = resolvePath(path);
        if (Files.isDirectory(p)) {
            try (var walk = Files.walk(p)) {
                return walk.filter(Files::isRegularFile)
                    .mapToLong(f -> {
                        try { return Files.size(f); } catch (IOException e) { return 0; }
                    })
                    .sum();
            }
        }
        return Files.size(p);
    }

    private Path resolvePath(String path) {
        var p = Path.of(path);
        if (!p.isAbsolute()) {
            p = Path.of(System.getProperty("user.dir")).resolve(p);
        }
        if (config.isSandboxEnable() && !config.getSandboxPath().isEmpty()) {
            var sandbox = Path.of(config.getSandboxPath()).normalize();
            var resolved = p.normalize();
            if (!resolved.startsWith(sandbox)) {
                throw new SecurityException("Access denied: path outside sandbox");
            }
        }
        return p.normalize();
    }

    @Override
    public void close() {
        running = false;
        if (sftpServer != null) sftpServer.stop();
        if (webdavServer != null) webdavServer.stop();
        log.info("File system manager stopped");
    }

    public record FileEntry(String name, String path, boolean isDirectory, long size, long lastModified, boolean hidden) {}

    static class SftpServerComponent {
        SftpServerComponent(FileSystemConfig config, AuthManager authManager) {}
        void start() { log.debug("SFTP server component initialized"); }
        void stop() {}
    }

    static class WebdavServerComponent {
        WebdavServerComponent(FileSystemConfig config, AuthManager authManager) {}
        void start() { log.debug("WebDAV server component initialized"); }
        void stop() {}
    }
}
