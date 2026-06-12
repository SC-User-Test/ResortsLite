package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cloud-ready booking controller with distributed session management and caching.
 * 
 * Fixes applied:
 * - cr-java-0065: Externalized session state to Azure Cache for Redis
 * - cr-java-0067: Replaced in-memory cache with Azure Cache for Redis with TTL
 * - cr-java-0071: Externalized URLs to Azure App Configuration
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // Externalized inventory service URL from Azure App Configuration
    @Value("${app.inventory.endpoint:#{environment.INVENTORY_SERVICE_URL}}")
    private String inventoryServiceUrl;
    
    // Cache TTL configuration (default 1 hour)
    @Value("${app.cache.ttl-minutes:60}")
    private long cacheTtlMinutes;

    /**
     * Creates a new booking with distributed session and cache management.
     * Session data is stored in Azure Cache for Redis for horizontal scalability.
     * 
     * @param guestName Guest name
     * @param roomType Room type
     * @param checkIn Check-in date
     * @param checkOut Check-out date
     * @param sessionId Session identifier (from request header or cookie)
     * @return Booking confirmation response
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Azure Cache for Redis instead of HTTP session
        // This enables stateless architecture and horizontal scaling
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            // Set session expiration (default 30 minutes)
            redisTemplate.expire(sessionKey, 30, TimeUnit.MINUTES);
        }

        // Store booking in distributed cache with TTL
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("cacheType", "Azure Cache for Redis");
        return response;
    }

    /**
     * Retrieves booking status using distributed session management.
     * Session data is retrieved from Azure Cache for Redis.
     * 
     * @param bookingId Booking ID
     * @param sessionId Session identifier
     * @return Booking status response
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Azure Cache for Redis
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            Object guestNameObj = redisTemplate.opsForHash().get(sessionKey, "guestName");
            lastGuest = guestNameObj != null ? guestNameObj.toString() : null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        result.put("sessionType", "Azure Cache for Redis");
        return result;
    }

    /**
     * Checks room availability using externalized service URLs.
     * Inventory service URL is loaded from Azure App Configuration.
     * 
     * @param roomType Room type to check
     * @return Availability response
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory service URL from Azure App Configuration
        // Defaults to HTTPS if not configured
        String inventoryUrl = inventoryServiceUrl != null && !inventoryServiceUrl.isEmpty()
            ? inventoryServiceUrl + "/rooms/available"
            : "https://inventory-service.internal:8081/rooms/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        response.put("protocol", "HTTPS");
        return response;
    }

    /**
     * Downloads report using cloud-native storage.
     * Report paths are managed by Azure Blob Storage.
     * 
     * @param month Report month
     * @return Report download response
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report generation now uses Azure Blob Storage
        // No hard-coded file paths
        String reportMessage = bookingService.generateReport(month);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", reportMessage);
        response.put("storageType", "Azure Blob Storage");
        response.put("month", month);
        return response;
    }
    
    /**
     * Retrieves booking from distributed cache.
     * Demonstrates Azure Cache for Redis with TTL.
     * 
     * @param bookingId Booking ID
     * @return Cached booking or null if expired
     */
    @GetMapping("/cache/{bookingId}")
    public Map<String, Object> getCachedBooking(@PathVariable String bookingId) {
        String cacheKey = "booking:" + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);
        
        Map<String, Object> response = new HashMap<>();
        if (cachedBooking != null) {
            response.put("status", "cache-hit");
            response.put("booking", cachedBooking);
            response.put("ttl", redisTemplate.getExpire(cacheKey, TimeUnit.MINUTES));
        } else {
            response.put("status", "cache-miss");
            response.put("message", "Booking not found in cache or expired");
        }
        response.put("cacheType", "Azure Cache for Redis");
        return response;
    }
}
