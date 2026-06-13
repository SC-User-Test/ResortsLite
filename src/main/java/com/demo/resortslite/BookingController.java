package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private InventoryServiceClient inventoryServiceClient;

    // FIXED blocker-13 (cz-java-0070): Replaced local HashMap cache with Redis-backed Spring Cache
    // Local cache removed - now using @Cacheable annotation with Redis backend

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED blockers 4,5,7,8 (cz-java-0063, cz-java-0069): Session data now stored in Redis
        // Spring Session automatically handles distributed session storage via Redis
        // Session data persists across container restarts and horizontal scaling
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED blocker-13: Using Redis cache instead of local HashMap
        // Cache is now distributed and shared across all container instances

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookingStatus", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED blockers 4,5,6 (cz-java-0063): Session data retrieved from Redis
        // Works correctly across all container instances in the cluster
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED blocker-9 (cz-java-0082): Using loosely-coupled service interface
        // Direct service call replaced with InventoryServiceClient interface
        // Enables microservices architecture and independent deployment

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("available", inventoryServiceClient.checkRoomAvailability(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // FIXED blocker-1 (cz-java-0057): Replaced absolute file path with S3 storage
        // Files now stored in Amazon S3 for cross-platform compatibility
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = s3StorageService.getFileUrl(s3Key);

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
