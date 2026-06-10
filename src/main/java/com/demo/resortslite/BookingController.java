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
    private S3StorageService s3StorageService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint}")
    private String inventoryEndpoint;

    // FIXED blocker-13 (cz-java-0070): Replaced local in-memory cache with Redis distributed cache
    // Original: private static final Map<String, Object> bookingCache = new HashMap<>();

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED blocker-5 & blocker-7 (cz-java-0063, cz-java-0069): Using Spring Session with Redis
        // Session data is now automatically stored in Redis via Spring Session configuration
        // HttpSession is backed by Redis, enabling session persistence across container restarts
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED blocker-13 (cz-java-0070): Store booking in Redis distributed cache instead of local HashMap
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, 24, TimeUnit.HOURS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED blocker-6 & blocker-8 (cz-java-0063, cz-java-0069): Session backed by Redis
        // Session data persists across container instances and restarts
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED blocker-9 (cz-java-0082): Externalized service endpoint to environment variable
        // Service endpoint is now configurable via ${INVENTORY_ENDPOINT} environment variable
        String inventoryUrl = inventoryEndpoint + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // FIXED blocker-1 (cz-java-0057): Replaced absolute file path with S3 object storage
        // Original: String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf";
        String s3Key = s3StorageService.generateS3Key(month + "_bookings.pdf");
        String s3Uri = "s3://" + s3StorageService.getBucketName() + "/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", s3Uri);
        response.put("message", bookingService.generateReport(month));
        response.put("s3Bucket", s3StorageService.getBucketName());
        response.put("s3Key", s3Key);
        return response;
    }
}
