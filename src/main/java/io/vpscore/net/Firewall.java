package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Firewall implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Firewall.class);

    private final NetworkConfig config;
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> connectionCount = new ConcurrentHashMap<>();
    private volatile boolean running;

    public Firewall(NetworkConfig config) {
        this.config = config;
    }

    public void start() {
        running = true;
        log.info("Firewall started");
    }

    public boolean allow(InetAddress address, int port) {
        var ip = address.getHostAddress();
        if (blacklist.contains(ip)) return false;
        if (!whitelist.isEmpty() && !whitelist.contains(ip)) return false;
        if (port < config.getProxyStartPort() || port > config.getProxyEndPort()) {
            if (port < 1024) return false;
        }
        return true;
    }

    public void blockIp(String ip) {
        blacklist.add(ip);
        log.info("Blocked IP: {}", ip);
    }

    public void unblockIp(String ip) {
        blacklist.remove(ip);
        log.info("Unblocked IP: {}", ip);
    }

    public void allowIp(String ip) {
        whitelist.add(ip);
        log.info("Whitelisted IP: {}", ip);
    }

    public void trackConnection(InetAddress address) {
        var ip = address.getHostAddress();
        connectionCount.merge(ip, 1, Integer::sum);
    }

    public int getConnectionCount(InetAddress address) {
        return connectionCount.getOrDefault(address.getHostAddress(), 0);
    }

    public List<String> getBlacklist() {
        return List.copyOf(blacklist);
    }

    public List<String> getWhitelist() {
        return List.copyOf(whitelist);
    }

    @Override
    public void close() {
        running = false;
        whitelist.clear();
        blacklist.clear();
        connectionCount.clear();
    }
}
