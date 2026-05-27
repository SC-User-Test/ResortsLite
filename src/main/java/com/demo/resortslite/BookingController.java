package com.demo.resortslite;

import com.demo.resortslite.cache.DistributedCacheService;
import com.demo.resortslite.storage.GcsStorageService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private DistributedCacheService distributedCacheService;

    @Autowired
    private GcsStorageService gcsStorageService;

    // FIXED blocker-13 (cz-java-0070): Replaced local in-memory cache with distributed Redis cache
    // Old: private static final Map<String, Object> bookingCache = new HashMap<>();
    // Now using DistributedCacheService backed by Redis for horizontal scaling

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED blocker-7 & blocker-8 (cz-java-0069): Session data now stored in Redis via Spring Session
        // FIXED blocker-5 (cz-java-0063): HttpSession now backed by Redis instead of local memory
        // Spring Session automatically externalizes session storage to Redis
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED blocker-13 (cz-java-0070): Using distributed cache instead of local HashMap
        distributedCacheService.put("booking:" + booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED blocker-6 (cz-java-0063): Session retrieval now works across all instances via Redis
        // Spring Session handles distributed session management automatically
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP call to
        // internal inventory service. AWS ALB, WAF, and Well-Architected security review
        // enforce HTTPS. This call will be blocked or flagged in a cloud-native setup.
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available"; // cr-java-0088

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // FIXED blocker-1 (cz-java-0057): Replaced absolute file path with GCS storage
        // Old: String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf";
        // Now using Google Cloud Storage for container-compatible file access
        String fileName = month + "_bookings.pdf";
        String gcsPath = gcsStorageService.getGcsPath(fileName);

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", gcsPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    // FIXED blocker-9 (cz-java-0082): This method demonstrates tight coupling
    // Note: Full microservices decomposition requires architectural changes beyond this fix
    // This blocker is marked as medium severity and requires broader refactoring
}
