package io.vpscore.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VpsDesktop implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VpsDesktop.class);

    private static final String BASE_DIR = "/home/container/.vpscore/desktop";
    private static final String DISPLAY = ":99";
    private static final int VNC_PORT = 5900;
    private static final Path X_LOCK = Path.of("/tmp/.X99-lock");
    private static final Path X_UNIX = Path.of("/tmp/.X11-unix/X99");

    private final AtomicBoolean installed = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger exitCode = new AtomicInteger(-1);

    private Process vncProcess;
    private Process wmProcess;
    private Process appProcess;
    private Thread watcher;

    public CompletableFuture<String> install() {
        if (isInstalled()) {
            return CompletableFuture.completedFuture("already_installed");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var base = Path.of(BASE_DIR);
                Files.createDirectories(base);

                var scriptContent = """
#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ARCH="x86_64" ;;
    aarch64) ARCH="aarch64" ;;
    *)       echo "ERROR: Unsupported arch: $ARCH"; exit 1 ;;
esac
ALPINE_ROOT="$DIR/alpine"
ALPINE_VER="v3.20"
BASE_URL="https://dl-cdn.alpinelinux.org/alpine/$ALPINE_VER"
rm -rf "$ALPINE_ROOT"
mkdir -p "$ALPINE_ROOT"
APK_TOOLS_PKG=$(curl -sL "$BASE_URL/main/$ARCH/" | grep -o 'apk-tools-static-[^"]*\\.apk' | tail -1)
if [ -z "$APK_TOOLS_PKG" ]; then echo "ERROR: Cannot find apk-tools-static"; exit 2; fi
echo "Downloading apk.static: $APK_TOOLS_PKG..."
APK_TMP="$DIR/apk-tools-static.apk"
curl -sL "$BASE_URL/main/$ARCH/$APK_TOOLS_PKG" -o "$APK_TMP"
if [ ! -s "$APK_TMP" ]; then echo "ERROR: Failed to download apk.static"; exit 2; fi
echo "Extracting apk.static..."
tar -xzf "$APK_TMP" -C /tmp/
APK_STATIC="/tmp/sbin/apk.static"
chmod +x "$APK_STATIC"
echo "Configuring Alpine repositories..."
mkdir -p "$ALPINE_ROOT/etc/apk"
cat > "$ALPINE_ROOT/etc/apk/repositories" <<EOF
$BASE_URL/main
$BASE_URL/community
EOF
echo "Installing tigervnc, fluxbox, xterm via apk.static..."
"$APK_STATIC" --root "$ALPINE_ROOT" --arch "$ARCH" add --initdb --allow-untrusted tigervnc fluxbox xterm 2>&1 || true
echo "Installing gcc and musl-dev for LD_PRELOAD shim..."
"$APK_STATIC" --root "$ALPINE_ROOT" --arch "$ARCH" add --allow-untrusted gcc musl-dev 2>&1 || echo "  gcc install FAILED (may fall back to host gcc)"
if [ ! -x "$ALPINE_ROOT/usr/bin/Xvnc" ]; then
    echo "ERROR: Xvnc not found after installation"
    exit 1
fi
if [ ! -x "$ALPINE_ROOT/usr/bin/fluxbox" ]; then
    echo "ERROR: fluxbox not found after installation"
    exit 1
fi
echo "Creating symlinks..."
ln -sf "$ALPINE_ROOT/usr/bin/Xvnc" "$DIR/Xvnc"
ln -sf "$ALPINE_ROOT/usr/bin/fluxbox" "$DIR/fluxbox"
echo "Creating wrapper..."
cat > "$DIR/run-desktop.sh" << 'WRAPEOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export HOME="/home/container"
export XKB_BINDIR="$DIR/bin"
export XKB_CONFIG_ROOT="$DIR/alpine/usr/share/X11/xkb"
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ALPINE_LD="$DIR/alpine/lib/ld-musl-x86_64.so.1" ;;
    aarch64) ALPINE_LD="$DIR/alpine/lib/ld-musl-aarch64.so.1" ;;
    *)       ALPINE_LD="" ;;
esac
if [ -f "$ALPINE_LD" ]; then
    chmod +x "$ALPINE_LD" 2>/dev/null || true
    if [ -x "$ALPINE_LD" ]; then
        exec "$ALPINE_LD" --library-path "$DIR/alpine/usr/lib:$DIR/alpine/lib" "$@"
    fi
fi
export LD_LIBRARY_PATH="$DIR/alpine/usr/lib:$DIR/alpine/lib:$LD_LIBRARY_PATH"
exec "$@"
WRAPEOF
chmod +x "$DIR/run-desktop.sh"
echo "Fixing permissions..."
find "$DIR/alpine" -type d -exec chmod a+rx {} ';' 2>/dev/null || true
find "$DIR/alpine" -type f -exec chmod a+r {} ';' 2>/dev/null || true
echo "Pre-compiling XKB keymaps..."
XKB_DIR="$ALPINE_ROOT/usr/share/X11/xkb"
case "$ARCH" in
    x86_64)  MUSB_LD="$ALPINE_ROOT/lib/ld-musl-x86_64.so.1" ;;
    aarch64) MUSB_LD="$ALPINE_ROOT/lib/ld-musl-aarch64.so.1" ;;
    *)       MUSB_LD="" ;;
esac
if [ -x "$MUSB_LD" ] && [ -x "$ALPINE_ROOT/usr/bin/xkbcomp" ]; then
    mkdir -p "$XKB_DIR/compiled"
    chmod 1777 "$XKB_DIR/compiled" 2>/dev/null || true
    KEYMAP_FILE="/tmp/vpscore-keymap.txt"
    cat > "$KEYMAP_FILE" << 'KMEOF'
xkb_keymap "default" {
    xkb_keycodes { include "evdev+aliases(qwerty)" };
    xkb_types    { include "complete" };
    xkb_compat   { include "complete+mousekeys" };
    xkb_symbols  { include "pc+us+inet(evdev)" };
    xkb_geometry { include "pc(pc105)" };
};
KMEOF
    echo -n "  base keymap: "
    "$MUSB_LD" --library-path "$ALPINE_ROOT/usr/lib:$ALPINE_ROOT/lib" \
        "$ALPINE_ROOT/usr/bin/xkbcomp" \
        -R"$XKB_DIR" -xkm "$KEYMAP_FILE" \
        -o "$XKB_DIR/compiled/base.xkm" 2>&1 && echo "OK" || echo "FAIL"
    echo -n "  evdev keymap: "
    "$MUSB_LD" --library-path "$ALPINE_ROOT/usr/lib:$ALPINE_ROOT/lib" \
        "$ALPINE_ROOT/usr/bin/xkbcomp" \
        -R"$XKB_DIR" -xkm "$KEYMAP_FILE" \
        -o "$XKB_DIR/compiled/evdev.xkm" 2>&1 && echo "OK" || echo "FAIL"
    rm -f "$KEYMAP_FILE"
    chmod -R a+rX "$XKB_DIR/compiled" 2>/dev/null || true
else
    echo "  xkbcomp not available, skipping"
fi
echo "Creating /tmp/xkbcomp wrapper..."
XKBWRAP="/tmp/xkbcomp"
cat > "$XKBWRAP" << 'XKBEOF'
#!/bin/bash
DIR="/home/container/.vpscore/desktop"
ALPINE_LD="$DIR/alpine/lib/ld-musl-x86_64.so.1"
XKB_REAL="$DIR/alpine/usr/bin/xkbcomp"
if [ -x "$ALPINE_LD" ] && [ -x "$XKB_REAL" ]; then
    exec "$ALPINE_LD" --library-path "$DIR/alpine/usr/lib:$DIR/alpine/lib" "$XKB_REAL" "$@"
fi
exit 0
XKBEOF
chmod +x "$XKBWRAP" 2>/dev/null || true
echo "Compiling xkbhook (LD_PRELOAD system() interceptor)..."
cat > "$DIR/xkbhook.c" << 'CEOF'
#define _GNU_SOURCE
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>

typedef int (*system_func_t)(const char *);

int system(const char *command) {
    static system_func_t real_system = NULL;
    if (!real_system)
        real_system = (system_func_t)dlsym(RTLD_NEXT, "system");

    const char *cmd = command;
    char *modified = NULL;

    if (command && strstr(command, "xkbcomp")) {
        const char *old = "/usr/bin/xkbcomp";
        const char *new_ = "/tmp/xkbcomp";
        size_t olen = strlen(old), nlen = strlen(new_);
        size_t clen = strlen(command);
        size_t cnt = 0;
        const char *p = command;
        while ((p = strstr(p, old))) { cnt++; p += olen; }
        modified = malloc(clen + cnt * (nlen - olen) + 1);
        if (modified) {
            char *dst = modified;
            const char *src = command;
            while (*src) {
                if (strncmp(src, old, olen) == 0) {
                    memcpy(dst, new_, nlen);
                    dst += nlen;
                    src += olen;
                } else {
                    *dst++ = *src++;
                }
            }
            *dst = '\0';
            cmd = modified;
        }
    }

    pid_t pid = fork();
    if (pid == 0) {
        unsetenv("LD_PRELOAD");
        execl("/bin/sh", "sh", "-c", cmd, (char *)NULL);
        _exit(127);
    }
    if (pid > 0) {
        int status;
        waitpid(pid, &status, 0);
        free(modified);
        if (WIFEXITED(status)) return WEXITSTATUS(status);
        return 0;
    }
    free(modified);
    return -1;
}
CEOF
echo "Creating gcc subprogram wrappers (cc1, collect2, as, ld)..."
GCC_BIN="$DIR/gcc-bin"
mkdir -p "$GCC_BIN"
MUSB_LD_ABS="$ALPINE_ROOT/lib/ld-musl-x86_64.so.1"
ALPINE_LIBS="$ALPINE_ROOT/usr/lib:$ALPINE_ROOT/lib"
GCC_LIBEXEC="$ALPINE_ROOT/usr/libexec/gcc"
if [ -d "$GCC_LIBEXEC" ]; then
    GCC_TARGET=$(ls "$GCC_LIBEXEC" 2>/dev/null | head -1)
    if [ -n "$GCC_TARGET" ]; then
        GCC_VER=$(ls "$GCC_LIBEXEC/$GCC_TARGET" 2>/dev/null | head -1)
        for prog in cc1 collect2 lto-wrapper lto1 as ld; do
            REAL_PATH=""
            if [ -x "$GCC_LIBEXEC/$GCC_TARGET/$GCC_VER/$prog" ]; then
                REAL_PATH="$GCC_LIBEXEC/$GCC_TARGET/$GCC_VER/$prog"
            elif [ -x "$ALPINE_ROOT/usr/bin/$prog" ]; then
                REAL_PATH="$ALPINE_ROOT/usr/bin/$prog"
            fi
            if [ -n "$REAL_PATH" ]; then
                cat > "$GCC_BIN/$prog" << WRAPEOF
#!/bin/sh
exec "$MUSB_LD_ABS" --library-path "$ALPINE_LIBS" "$REAL_PATH" "\\$@"
WRAPEOF
                chmod +x "$GCC_BIN/$prog"
                echo "    wrapper: $GCC_BIN/$prog -> $REAL_PATH"
            fi
        done
    fi
fi
COMPILED=0
if command -v gcc &>/dev/null; then
    # Host gcc (glibc)
    gcc -B "$GCC_BIN" -shared -fPIC -o "$DIR/xkbhook.so" "$DIR/xkbhook.c" -ldl 2>&1 && COMPILED=1 && echo "  xkbhook compiled OK (host gcc)"
fi
if [ "$COMPILED" != "1" ] && [ -x "$MUSB_LD" ] && [ -x "$ALPINE_ROOT/usr/bin/gcc" ]; then
    # Alpine gcc (musl) through ld-musl
    "$MUSB_LD" --library-path "$ALPINE_ROOT/usr/lib:$ALPINE_ROOT/lib" \
        "$ALPINE_ROOT/usr/bin/gcc" \
        -B "$GCC_BIN" \
        -shared -fPIC -o "$DIR/xkbhook.so" \
        -I"$ALPINE_ROOT/usr/include" \
        -L"$ALPINE_ROOT/usr/lib" -L"$ALPINE_ROOT/lib" \
        "$DIR/xkbhook.c" -ldl 2>&1 && COMPILED=1 && echo "  xkbhook compiled OK (Alpine gcc)"
fi
if [ "$COMPILED" != "1" ]; then
    echo "  xkbhook compilation FAILED (no gcc available)"
fi
rm -f "$DIR/xkbhook.c"
rm -f "$APK_TMP"
echo "Desktop binaries installed successfully!"
""";
                var script = base.resolve("setup.sh");
                Files.writeString(script, scriptContent);
                script.toFile().setExecutable(true);

                var wrapperContent = """
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export HOME="/home/container"
export XKB_BINDIR="$DIR/bin"
export XKB_CONFIG_ROOT="$DIR/alpine/usr/share/X11/xkb"
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ALPINE_LD="$DIR/alpine/lib/ld-musl-x86_64.so.1" ;;
    aarch64) ALPINE_LD="$DIR/alpine/lib/ld-musl-aarch64.so.1" ;;
    *)       ALPINE_LD="" ;;
esac
if [ -f "$ALPINE_LD" ]; then
    chmod +x "$ALPINE_LD" 2>/dev/null || true
    if [ -x "$ALPINE_LD" ]; then
        exec "$ALPINE_LD" --library-path "$DIR/alpine/usr/lib:$DIR/alpine/lib" "$@"
    fi
fi
export LD_LIBRARY_PATH="$DIR/alpine/usr/lib:$DIR/alpine/lib:$LD_LIBRARY_PATH"
exec "$@"
""";
                var wrapper = base.resolve("run-desktop.sh");
                Files.writeString(wrapper, wrapperContent);
                wrapper.toFile().setExecutable(true);

                var pb = new ProcessBuilder("bash", script.toAbsolutePath().toString());
                pb.inheritIO();
                var proc = pb.start();
                var code = proc.waitFor();
                if (code != 0) {
                    return "Script failed with exit code " + code;
                }
                installed.set(true);
                return "ok";
            } catch (Exception e) {
                log.error("Install failed", e);
                return "error: " + e.getMessage();
            }
        });
    }

    private void cleanupStale() {
        try {
            new ProcessBuilder("bash", "-c",
                "pkill -9 Xvnc 2>/dev/null; pkill -9 fluxbox 2>/dev/null")
                .inheritIO().start().waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Stale process cleanup failed", e);
        }
        try {
            Files.deleteIfExists(X_LOCK);
            Files.deleteIfExists(X_UNIX);
        } catch (Exception e) {
            log.warn("X lock cleanup failed", e);
        }
        // Try to drop /tmp/.X11-unix so it gets recreated owned by us.
        // If we can't (owned by someone else), we still continue — Xvnc will be
        // launched with -nolisten unix and won't touch the directory.
        try {
            new ProcessBuilder("bash", "-c",
                "rm -rf /tmp/.X11-unix 2>/dev/null; " +
                "mkdir -p /tmp/.X11-unix 2>/dev/null; " +
                "chmod 1777 /tmp/.X11-unix 2>/dev/null; true")
                .inheritIO().start().waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("/tmp/.X11-unix reset failed", e);
        }
    }

    public CompletableFuture<String> startAsync(String appCommand) {
        if (running.get()) {
            return CompletableFuture.completedFuture("already_running");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var base = Path.of(BASE_DIR);
                if (!Files.exists(base.resolve("alpine/usr/bin/Xvnc"))) {
                    return "Xvnc not found, please reinstall";
                }

                cleanupStale();

                // Ensure /tmp/.X11-unix exists
                try {
                    Files.createDirectories(Path.of("/tmp/.X11-unix"));
                    var unixDir = new java.io.File("/tmp/.X11-unix");
                    unixDir.setWritable(true, false);
                    unixDir.setReadable(true, false);
                    unixDir.setExecutable(true, false);
                } catch (Exception e) {
                    log.warn("Could not create /tmp/.X11-unix", e);
                }

                var env = new java.util.HashMap<String, String>();
                env.put("HOME", "/home/container");
                env.put("DISPLAY", DISPLAY);
                env.put("PATH", "/usr/local/bin:/usr/bin:/bin:" + base);
                var xkbHook = base.resolve("xkbhook.so");
                if (Files.exists(xkbHook)) {
                    env.put("LD_PRELOAD", xkbHook.toAbsolutePath().toString());
                }

                var wrapper = base.resolve("run-desktop.sh").toAbsolutePath().toString();
                var xvncBin = base.resolve("Xvnc").toAbsolutePath().toString();
                var fluxboxBin = base.resolve("alpine/usr/bin/fluxbox").toAbsolutePath().toString();
                var logFile = base.resolve("xvnc.log").toFile();

                // Fix permissions on Alpine rootfs
                try {
                    Runtime.getRuntime().exec(new String[]{
                        "bash", "-c",
                        "chmod -R a+rX " + base.resolve("alpine").toAbsolutePath()
                    }).waitFor(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Permissions fix failed", e);
                }

                // Create xkbcomp wrapper — Xvnc needs it to compile keymaps.
                // xkbcomp is musl-linked and can't exec directly on glibc.
                // Try musl first, no-op fallback so Xvnc doesn't crash.
                var binDir = base.resolve("bin");
                Files.createDirectories(binDir);
                var xkbWrapper = binDir.resolve("xkbcomp");
                if (!Files.exists(xkbWrapper)) {
                    Files.writeString(xkbWrapper, """
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && cd .. && pwd)"
WRAPPER="$DIR/run-desktop.sh"
XKBCOMP="$DIR/alpine/usr/bin/xkbcomp"
if [ -x "$WRAPPER" ] && [ -x "$XKBCOMP" ]; then
    exec "$WRAPPER" "$XKBCOMP" "$@" 2>/dev/null || true
fi
exit 0
""");
                    xkbWrapper.toFile().setExecutable(true);
                }
                env.put("XKB_BINDIR", binDir.toAbsolutePath().toString());
                env.put("XKB_CONFIG_ROOT", base.resolve("alpine/usr/share/X11/xkb").toAbsolutePath().toString());
                env.put("XKB_DEFAULT_LAYOUT", "us");
                env.put("XKB_DEFAULT_MODEL", "pc105");
                env.put("XKB_DEFAULT_RULES", "base");

                // Diagnostic: check ld-musl
                var ldPath = base.resolve("alpine/lib/ld-musl-x86_64.so.1");
                log.info("ld-musl: exists={}, executable={}, size={}",
                    Files.exists(ldPath), Files.isExecutable(ldPath),
                    Files.exists(ldPath) ? Files.size(ldPath) : -1);

                // 1. Xvnc — combined X server + VNC server
                var xkbDir = base.resolve("alpine/usr/share/X11/xkb").toAbsolutePath().toString();
                var compiledMap = base.resolve("alpine/usr/share/X11/xkb/compiled/base.xkm");
                if (!Files.exists(compiledMap)) {
                    // Attempt runtime compilation via wrapper
                    var xkbReal = base.resolve("alpine/usr/bin/xkbcomp");
                    if (Files.exists(xkbReal)) {
                        try {
                            var keymapContent = """
xkb_keymap "default" {
    xkb_keycodes { include "evdev+aliases(qwerty)" };
    xkb_types    { include "complete" };
    xkb_compat   { include "complete+mousekeys" };
    xkb_symbols  { include "pc+us+inet(evdev)" };
    xkb_geometry { include "pc(pc105)" };
};
""";
                            var kmFile = base.resolve("keymap.txt");
                            Files.writeString(kmFile, keymapContent);
                            var pb = new ProcessBuilder(wrapper,
                                xkbReal.toAbsolutePath().toString(),
                                "-R" + xkbDir,
                                "-xkm", kmFile.toAbsolutePath().toString(),
                                "-o", compiledMap.toAbsolutePath().toString());
                            pb.environment().put("HOME", "/home/container");
                            pb.redirectErrorStream(true);
                            var p = pb.start();
                            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                                log.info("Keymap compiled at startup");
                            }
                        } catch (Exception e) {
                            log.warn("Startup keymap compilation failed", e);
                        }
                    }
                }
                var args = new java.util.ArrayList<String>();
                args.addAll(java.util.List.of(wrapper, xvncBin, DISPLAY,
                    "-geometry", "1024x768",
                    "-depth", "24",
                    "-SecurityTypes", "None",
                    "-rfbport", String.valueOf(VNC_PORT),
                    "-xkbdir", xkbDir));
                args.addAll(java.util.List.of("-nolisten", "unix", "-ac", "-noreset", "-localhost"));
                var pbVnc = new ProcessBuilder(args);
                pbVnc.environment().putAll(env);
                pbVnc.redirectErrorStream(false);
                pbVnc.redirectOutput(ProcessBuilder.Redirect.to(logFile));
                vncProcess = pbVnc.start();

                // Wait for X lock file (max 5s)
                for (int i = 0; i < 25; i++) {
                    if (Files.exists(X_LOCK)) break;
                    Thread.sleep(200);
                }

                Thread.sleep(1000);
                if (!vncProcess.isAlive()) {
                    var code = vncProcess.exitValue();
                    var error = "";
                    try {
                        var buf = vncProcess.getErrorStream().readAllBytes();
                        if (buf != null) error = new String(buf, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                    var vncLogContent = "";
                    try {
                        if (logFile.exists()) vncLogContent = Files.readString(logFile.toPath());
                    } catch (Exception ignored) {}
                    log.warn("Xvnc died code={}, stderr: {}, log: {}", code, error, vncLogContent);
                    var detail = !error.isBlank() ? error : vncLogContent;
                    if (detail.length() > 500) detail = detail.substring(0, 500);
                    return "Xvnc exited with code " + code + (!detail.isBlank() ? ": " + detail : "");
                }

                // 2. fluxbox — ensure config exists
                var fluxboxDir = Path.of("/home/container/.fluxbox");
                Files.createDirectories(fluxboxDir);
                if (!Files.exists(fluxboxDir.resolve("menu"))) {
                    Files.writeString(fluxboxDir.resolve("menu"), """
                        [begin] (Desktop)
                        [exec] (Terminal) {xterm}
                        [end]
                        """.stripIndent());
                }
                var pbWm = new ProcessBuilder(wrapper, fluxboxBin, "-display", DISPLAY);
                pbWm.environment().putAll(env);
                pbWm.redirectErrorStream(true);
                pbWm.redirectOutput(ProcessBuilder.Redirect.to(logFile));
                wmProcess = pbWm.start();

                log.info("Desktop started: Xvnc PID={}, fluxbox PID={}, log={}",
                    vncProcess.pid(), wmProcess.pid(), logFile);

                Thread.sleep(500);
                if (!wmProcess.isAlive()) {
                    var code = wmProcess.exitValue();
                    log.warn("fluxbox died immediately with exit code {}", code);
                    return "fluxbox exited with code " + code;
                }

                if (appCommand != null && !appCommand.isBlank()) {
                    var pbApp = new ProcessBuilder("bash", "-c", appCommand);
                    pbApp.environment().putAll(env);
                    pbApp.redirectErrorStream(true);
                    appProcess = pbApp.start();
                }

                running.set(true);
                exitCode.set(0);

                watcher = new Thread(() -> {
                    try {
                        while (running.get()) {
                            if (!vncProcess.isAlive() || !wmProcess.isAlive()) {
                                log.warn("Desktop process died: Xvnc={} fluxbox={}",
                                    vncProcess.isAlive(), wmProcess.isAlive());
                                running.set(false);
                                exitCode.set(vncProcess.isAlive() ? -1 : vncProcess.exitValue());
                                cleanupStale();
                                break;
                            }
                            Thread.sleep(2000);
                        }
                    } catch (InterruptedException ignored) {}
                });
                watcher.setDaemon(true);
                watcher.start();

                return "started";
            } catch (Exception e) {
                log.error("Start failed", e);
                return "error: " + e.getMessage();
            }
        });
    }

    // Sync wrapper for backward compat; blocks up to 15s
    public String start(String appCommand) {
        try {
            return startAsync(appCommand).get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "timeout";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    public void stop() {
        running.set(false);
        for (var p : new Process[]{vncProcess, wmProcess, appProcess}) {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
        cleanupStale();
    }

    public boolean isInstalled() {
        if (installed.get()) return true;
        if (Files.exists(Path.of(BASE_DIR, "Xvnc"))
            || Files.exists(Path.of(BASE_DIR, "alpine", "usr", "bin", "Xvnc"))) {
            if (Files.exists(Path.of(BASE_DIR, "alpine", "usr", "bin", "fluxbox"))) {
                installed.set(true);
                return true;
            }
        }
        return false;
    }
    public boolean isRunning() { return running.get(); }
    public int getExitCode() { return exitCode.get(); }
    public int getVncPort() { return VNC_PORT; }

    public java.util.Map<String, Object> getStatus() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("installed", installed.get());
        map.put("running", running.get());
        map.put("exit_code", exitCode.get());
        map.put("display", DISPLAY);
        map.put("vnc_port", VNC_PORT);
        return map;
    }

    @Override
    public void close() {
        stop();
    }
}
