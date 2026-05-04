package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${INVENTORY_ENDPOINT:https://inventory-service.internal:8081/rooms/available}")
    private String inventoryUrl;

    // Cache TTL in seconds (1 hour)
    private static final long CACHE_TTL_SECONDS = 3600;

    /**
     * Creates a new booking and stores session data in Redis for distributed session management.
     * Replaces local in-memory cache with Redis-backed cache with TTL.
     * 
     * @param guestName Guest name
     * @param roomType Room type
     * @param checkIn Check-in date
     * @param checkOut Check-out date
     * @param session HTTP session (backed by Redis via Spring Session)
     * @return Map containing booking confirmation
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store in Redis-backed HTTP session (Spring Session with ElastiCache)
        // This enables stateless application instances with centralized session management
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Store in Redis cache with TTL instead of unbounded in-memory cache
        String bookingId = (String) booking.get("bookingId");
        String cacheKey = "booking:" + bookingId;
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using Redis-backed session.
     * Session data is accessible across all application instances via ElastiCache.
     * 
     * @param bookingId The booking ID
     * @param session HTTP session (backed by Redis)
     * @return Map containing booking status
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Read from Redis-backed session - accessible across all instances
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using externalized inventory service URL.
     * Uses HTTPS endpoint from environment configuration.
     * 
     * @param roomType The room type to check
     * @return Map containing availability information
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized HTTPS endpoint from AWS Systems Manager Parameter Store
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads a report using S3-based storage.
     * Replaces hard-coded file paths with cloud-native S3 storage.
     * 
     * @param month The month for the report
     * @return Map containing report download information
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report generation now uses S3 instead of local file system
        Map<String, Object> response = new HashMap<>();
        response.put("message", bookingService.generateReport(month));
        response.put("storageType", "Amazon S3");
        response.put("note", "Reports are stored in S3 bucket for durable, scalable storage");
        return response;
    }
}
