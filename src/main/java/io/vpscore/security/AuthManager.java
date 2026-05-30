package io.vpscore.security;

import io.vpscore.config.VPSConfig.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuthManager.class);

    private final SecurityConfig config;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> bannedIps = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private volatile boolean running;

    public AuthManager(SecurityConfig config) {
        this.config = config;
    }

    public void start() {
        running = true;
        log.info("Auth manager started (mode: {}, totp: {})",
            config.getAuthMode(), config.isTotpRequired());
    }

    public boolean authenticate(String username, String password, String ip) {
        if (bannedIps.contains(ip)) {
            log.warn("Blocked login attempt from banned IP: {}", ip);
            return false;
        }

        if (!config.isAuthRequired()) return true;

        var success = config.getPassword().equals(password);

        if (!success) {
            var attempts = loginAttempts.merge(ip, 1, Integer::sum);
            log.warn("Failed login for '{}' from {} (attempt {}/{})",
                username, ip, attempts, config.getMaxLoginAttempts());
            if (attempts >= config.getMaxLoginAttempts()) {
                banIp(ip);
            }
        } else {
            loginAttempts.remove(ip);
            var session = new Session(username, ip, System.currentTimeMillis());
            sessions.put(session.id, session);
            log.info("Authenticated '{}' from {}", username, ip);
        }

        return success;
    }

    public boolean validateSession(String sessionId) {
        var session = sessions.get(sessionId);
        if (session == null) return false;
        if (System.currentTimeMillis() - session.createdAt > 3600000) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }

    public void invalidateSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void banIp(String ip) {
        bannedIps.add(ip);
        log.warn("IP banned: {} ({} min)", ip, config.getBanDurationMinutes());
        TimerTask unbanTask = new TimerTask() {
            @Override
            public void run() {
                bannedIps.remove(ip);
                loginAttempts.remove(ip);
                log.info("IP unbanned: {}", ip);
            }
        };
        new Timer().schedule(unbanTask, config.getBanDurationMinutes() * 60000L);
    }

    public boolean isBanned(String ip) {
        return bannedIps.contains(ip);
    }

    @Override
    public void close() {
        running = false;
        sessions.clear();
        bannedIps.clear();
        loginAttempts.clear();
    }

    public static class Session {
        private static final SecureRandom random = new SecureRandom();
        public final String id;
        public final String username;
        public final String ip;
        public final long createdAt;

        Session(String username, String ip, long createdAt) {
            this.id = generateId();
            this.username = username;
            this.ip = ip;
            this.createdAt = createdAt;
        }

        private static String generateId() {
            var bytes = new byte[32];
            random.nextBytes(bytes);
            var sb = new StringBuilder();
            for (var b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }
}
