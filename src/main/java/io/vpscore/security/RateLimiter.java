package io.vpscore.security;

import io.vpscore.config.VPSConfig.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

public class RateLimiter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final SecurityConfig config;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;

    public RateLimiter(SecurityConfig config) {
        this.config = config;
    }

    public void start() {
        running = true;
        cleaner.scheduleAtFixedRate(this::cleanup, 0, 60, TimeUnit.SECONDS);
        log.info("Rate limiter started");
    }

    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    public boolean tryAcquire(String key, int tokens) {
        var bucket = buckets.computeIfAbsent(key, k -> new Bucket(10, 1000));
        return bucket.tryConsume(tokens);
    }

    public boolean tryAcquire(String key, int maxTokens, long windowMs) {
        var bucket = buckets.computeIfAbsent(key, k -> new Bucket(maxTokens, windowMs));
        return bucket.tryConsume(1);
    }

    public int getRemaining(String key) {
        var bucket = buckets.get(key);
        return bucket != null ? bucket.available() : 0;
    }

    private void cleanup() {
        var now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> now - e.getValue().createdAt > 3600000);
    }

    @Override
    public void close() {
        running = false;
        cleaner.shutdownNow();
        buckets.clear();
    }

    static class Bucket {
        final long windowMs;
        final int maxTokens;
        final long createdAt = System.currentTimeMillis();
        private int tokens;
        private long lastRefill;

        Bucket(int maxTokens, long windowMs) {
            this.maxTokens = maxTokens;
            this.windowMs = windowMs;
            this.tokens = maxTokens;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(int count) {
            refill();
            if (tokens >= count) {
                tokens -= count;
                return true;
            }
            return false;
        }

        synchronized int available() {
            refill();
            return tokens;
        }

        private void refill() {
            var now = System.currentTimeMillis();
            var elapsed = now - lastRefill;
            if (elapsed >= windowMs) {
                tokens = maxTokens;
                lastRefill = now;
            }
        }
    }
}
