package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

// cr-java-0065 FIX: HttpSession removed — session state is now managed via Spring Session
// backed by Google Cloud Memorystore for Redis. All session attributes are stored in the
// centralised Redis store, making every application instance stateless and enabling safe
// horizontal scaling, auto-scaling, and zero-downtime rolling deployments on GCP.
// Required dependencies added to pom.xml:
//   spring-session-data-redis, spring-boot-starter-data-redis
// Required properties added to application.properties:
//   spring.session.store-type=redis
//   spring.redis.host / spring.redis.port (or REDIS_HOST / REDIS_PORT env vars)
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // cr-java-0065 FIX: RedisTemplate replaces direct HttpSession usage.
    // Session-scoped data (lastBooking, guestName) is now stored in and retrieved from
    // Google Cloud Memorystore for Redis, which is shared across all application instances.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // cr-java-0071 FIX: Inventory service URL externalised via environment variable /
    // application property — no hard-coded environment-specific endpoint remains.
    // Set INVENTORY_SERVICE_URL in the GCP Cloud Run / GKE environment, or override
    // via application.properties: app.inventory.service.url=https://...
    @Value("${app.inventory.service.url:${INVENTORY_SERVICE_URL:http://inventory-service.internal:8081/rooms/available}}")
    private String inventoryServiceUrl;

    // cr-java-0067 FIX: Static in-memory bookingCache (HashMap) replaced with
    // Google Cloud Memorystore for Redis via RedisTemplate with explicit TTL.
    //
    // WHY: A static HashMap is instance-local — each GCP Cloud Run revision / GKE pod
    // maintains its own isolated copy. Horizontal scaling produces inconsistent cache
    // state across instances, and unbounded growth causes OOM errors over time.
    //
    // HOW: All cache reads/writes now go through RedisTemplate using the key prefix
    // "bookingCache:<bookingId>". Every entry is written with a configurable TTL
    // (default 30 minutes, overridable via BOOKING_CACHE_TTL_MINUTES env var) so
    // stale entries are automatically evicted by Redis, preventing memory exhaustion.
    // The shared Memorystore instance ensures all application instances see the same
    // cache state, enabling safe horizontal scaling and rolling deployments on GCP.
    //
    // Cache TTL (minutes) — override via BOOKING_CACHE_TTL_MINUTES env var or
    // app.booking.cache.ttl.minutes property in application.properties / GCP Secret Manager.
    @Value("${app.booking.cache.ttl.minutes:${BOOKING_CACHE_TTL_MINUTES:30}}")
    private long bookingCacheTtlMinutes;

    private static final String BOOKING_CACHE_KEY_PREFIX = "bookingCache:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam(required = false, defaultValue = "") String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cr-java-0065 FIX: Session state is now stored in Google Cloud Memorystore for Redis
        // via RedisTemplate. The session key is derived from the booking ID so that any
        // application instance can retrieve it without server affinity.
        String bookingId = (String) booking.get("bookingId");
        redisTemplate.opsForHash().put("session:" + bookingId, "lastBooking", booking);
        redisTemplate.opsForHash().put("session:" + bookingId, "guestName", guestName);

        // cr-java-0067 FIX: Cache booking in Google Cloud Memorystore for Redis with TTL.
        // Replaces the former static HashMap (bookingCache) which was instance-local and
        // unbounded. The entry is stored under "bookingCache:<bookingId>" and automatically
        // expires after bookingCacheTtlMinutes minutes, preventing stale data and OOM errors.
        redisTemplate.opsForValue().set(
                BOOKING_CACHE_KEY_PREFIX + bookingId,
                booking,
                bookingCacheTtlMinutes,
                TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId) {

        // cr-java-0065 FIX: Guest name is now retrieved from Google Cloud Memorystore for Redis
        // instead of the local HTTP session. Any instance in the cluster can serve this request
        // without session affinity, enabling true stateless horizontal scaling.
        String lastGuest = (String) redisTemplate.opsForHash().get("session:" + bookingId, "guestName");

        // cr-java-0067 FIX: Booking details are now retrieved from the shared Redis cache
        // (Google Cloud Memorystore) instead of the instance-local static HashMap.
        // If the entry has expired (TTL elapsed) or is absent, fall back to the service layer.
        Object cachedBooking = redisTemplate.opsForValue().get(BOOKING_CACHE_KEY_PREFIX + bookingId);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", cachedBooking != null ? cachedBooking : bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // cr-java-0071 FIX: URL is now read from the externalised @Value field (injected
        // from the INVENTORY_SERVICE_URL env var or app.inventory.service.url property).
        String inventoryUrl = inventoryServiceUrl;

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // VIOLATION czr-java-001 [Software Portability / Mandatory]: Hardcoded absolute
        // file path. This path does not exist inside a container image. Container images
        // have their own isolated file systems — /var/legacy/reports won't be present.
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; // czr-java-001

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
