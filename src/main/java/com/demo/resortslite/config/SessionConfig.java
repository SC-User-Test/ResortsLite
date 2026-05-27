package com.demo.resortslite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Configuration for Spring Session with Redis.
 * Enables distributed session management for containerized environments.
 * Sessions are stored in Redis (Memorystore) instead of local memory,
 * allowing horizontal scaling and preventing session loss on pod restart.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class SessionConfig {
    // Spring Session auto-configuration handles Redis connection
    // Configuration properties are in application.properties
}
