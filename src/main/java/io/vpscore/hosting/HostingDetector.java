package io.vpscore.hosting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HostingDetector {

    private static final Logger log = LoggerFactory.getLogger(HostingDetector.class);

    public static final String ENV_PTERODACTYL = "PTERODACTYL_ALLOCATED_PORTS";
    public static final String ENV_SERVER_PORT = "SERVER_PORT";

    private static final List<HostingSignature> SIGNATURES = List.of(
        new HostingSignature("aternos", HostingType.FREE, true, List.of("/home/aternos", "aternos")),
        new HostingSignature("minehut", HostingType.FREE, true, List.of("/home/minehut", "minehut")),
        new HostingSignature("serverpro", HostingType.PAID, true, List.of("/home/serverpro", "serverpro")),
        new HostingSignature("limehost", HostingType.PAID, false, List.of("/home/limehost")),
        new HostingSignature("foxcraft", HostingType.PAID, false, List.of("/home/foxcraft")),
        new HostingSignature("minenode", HostingType.PAID, false, List.of("/home/mghome")),
        new HostingSignature("bisecthosting", HostingType.PAID, false, List.of("/home/bisect")),
        new HostingSignature("shockbyte", HostingType.PAID, false, List.of("/home/shockbyte")),
        new HostingSignature("apexhosting", HostingType.PAID, false, List.of("/home/apex")),
        new HostingSignature("ggservers", HostingType.PAID, false, List.of("/home/ggservers")),
        new HostingSignature("sparkedhost", HostingType.PAID, false, List.of("/home/sparkedhost")),
        new HostingSignature("vultr", HostingType.VPS, false, List.of("/root")),
        new HostingSignature("digitalocean", HostingType.VPS, false, List.of("/root")),
        new HostingSignature("aws", HostingType.VPS, false, List.of("/home/ec2-user")),
        new HostingSignature("azure", HostingType.VPS, false, List.of("/home/azure")),
        new HostingSignature("google_cloud", HostingType.VPS, false, List.of("/home/gcp")),
        new HostingSignature("hetzner", HostingType.VPS, false, List.of("/home/hetzner")),
        new HostingSignature("contabo", HostingType.VPS, false, List.of("/home/contabo")),
        new HostingSignature("ovh", HostingType.VPS, false, List.of("/home/ovh"))
    );

    public HostingInfo detect() {
        var userDir = System.getProperty("user.dir");
        var userName = System.getProperty("user.name");

        // Pterodactyl detection first (by env vars)
        var pterodactylPorts = getPterodactylPorts();
        if (pterodactylPorts != null) {
            log.info("Detected Pterodactyl environment. Allocated ports: {}", pterodactylPorts);
            return new HostingInfo("pterodactyl", HostingType.CONTAINER, true, true, pterodactylPorts);
        }

        for (var sig : SIGNATURES) {
            for (var path : sig.paths()) {
                if (userDir.contains(path) || userName.contains(path.replace("/home/", ""))) {
                    log.info("Detected hosting: {} (match: {})", sig.name(), path);
                    return new HostingInfo(sig.name(), sig.type(), sig.restricted(), false, List.of());
                }
            }
        }

        if (userDir.contains("server") || userName.equals("container") || !canAccessRoot()) {
            return new HostingInfo("unknown_hosting", HostingType.UNKNOWN, true, false, List.of());
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new HostingInfo("local_windows", HostingType.VPS, false, false, List.of());
        }

        return new HostingInfo("local_linux", HostingType.VPS, false, false, List.of());
    }

    public static List<Integer> getPterodactylPorts() {
        var env = System.getenv(ENV_PTERODACTYL);
        if (env != null && !env.isBlank()) {
            var ports = new ArrayList<Integer>();
            for (var part : env.split(",")) {
                try {
                    ports.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid port in PTERODACTYL_ALLOCATED_PORTS: {}", part);
                }
            }
            if (!ports.isEmpty()) return List.copyOf(ports);
        }

        var serverPort = System.getenv(ENV_SERVER_PORT);
        if (serverPort != null && !serverPort.isBlank()) {
            try {
                var port = Integer.parseInt(serverPort.trim());
                return List.of(port);
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    private boolean canAccessRoot() {
        try {
            var pb = new ProcessBuilder("ls", "/root");
            var process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public record HostingInfo(String name, HostingType type, boolean restricted, boolean autoDetected, List<Integer> allocatedPorts) {}

    public enum HostingType {
        FREE, PAID, VPS, CONTAINER, UNKNOWN
    }

    record HostingSignature(String name, HostingType type, boolean restricted, List<String> paths) {}
}
