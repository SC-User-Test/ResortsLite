package com.demo.resortslite.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed cache service using Redis.
 * Replaces local in-memory caching with distributed cache for horizontal scaling.
 */
@Service
public class DistributedCacheService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final long DEFAULT_TTL_MINUTES = 60;

    /**
     * Store value in distributed cache with TTL
     * @param key Cache key
     * @param value Value to cache
     */
    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL_MINUTES);
    }

    /**
     * Store value in distributed cache with custom TTL
     * @param key Cache key
     * @param value Value to cache
     * @param ttlMinutes Time to live in minutes
     */
    public void put(String key, Object value, long ttlMinutes) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
        }
    }

    /**
     * Retrieve value from distributed cache
     * @param key Cache key
     * @return Cached value or null if not found
     */
    public Object get(String key) {
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(key);
        }
        return null;
    }

    /**
     * Remove value from distributed cache
     * @param key Cache key
     */
    public void remove(String key) {
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        }
    }

    /**
     * Check if key exists in cache
     * @param key Cache key
     * @return true if key exists
     */
    public boolean exists(String key) {
        if (redisTemplate != null) {
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;
        }
        return false;
    }
}
