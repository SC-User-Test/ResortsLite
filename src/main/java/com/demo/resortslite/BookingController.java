package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// cr-java-0065 FIX: Removed javax.servlet.http.HttpSession import.
// HTTP session state has been migrated to Google Cloud Memorystore for Redis via
// Spring Session / RedisTemplate. This enables stateless application architecture
// with centralised session management across all distributed instances, satisfying
// cloud-native horizontal-scaling and failover requirements.

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // cr-java-0065 FIX: RedisTemplate replaces HttpSession for distributed session state.
    // All session attributes (lastBooking, guestName) are stored in GCP Memorystore for
    // Redis so every application instance shares the same session store. Requests can be
    // routed to any instance without sticky sessions or data loss.
    //
    // cr-java-0067 FIX: RedisTemplate also replaces the former static in-memory bookingCache
    // (HashMap without TTL). Booking entries are now stored in Google Cloud Memorystore for
    // Redis with a configurable TTL (app.booking.cache.ttl-seconds), preventing unbounded
    // memory growth and ensuring consistent cache state across all application instances.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // cr-java-0071: Inventory service URL externalised to environment variable /
    // application property. Sensitive endpoints can be stored in GCP Secret Manager
    // and injected via the sm:// property source; non-sensitive ones use plain env vars.
    // Replaces: String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";
    @Value("${app.inventory.endpoint:http://inventory-service.internal:8081/rooms/available}")
    private String inventoryServiceUrl;

    // Session TTL (seconds) — configurable via environment variable SESSION_TTL_SECONDS.
    // Defaults to 1800 s (30 min) to match typical HTTP session expiry.
    @Value("${app.session.ttl-seconds:1800}")
    private long sessionTtlSeconds;

    // cr-java-0067 FIX: Booking cache TTL (seconds) — configurable via environment variable
    // BOOKING_CACHE_TTL_SECONDS. Defaults to 300 s (5 min). Replaces the former unbounded
    // static HashMap that caused indefinite memory growth and stale data across instances.
    @Value("${app.booking.cache.ttl-seconds:300}")
    private long bookingCacheTtlSeconds;

    // cr-java-0067 FIX: The static in-memory bookingCache (HashMap without TTL) has been
    // REMOVED. It has been replaced with Google Cloud Memorystore for Redis via RedisTemplate
    // with a configurable TTL (app.booking.cache.ttl-seconds). This eliminates:
    //   - Unbounded memory growth / out-of-memory risk
    //   - Stale data inconsistencies across multiple application instances
    //   - Instance-local cache that is invisible to other nodes in the cluster
    // All cache reads and writes now go through the shared Redis store, ensuring consistent
    // cache state regardless of which instance handles the request.

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            // cr-java-0065 FIX: sessionId is now a plain request parameter (e.g. a JWT
            // subject or correlation ID supplied by the caller) used as the Redis key
            // namespace. The server no longer relies on a server-side HttpSession object.
            @RequestParam(required = false, defaultValue = "anonymous") String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cr-java-0065 FIX: State that was previously stored in HttpSession is now written
        // to Google Cloud Memorystore for Redis with a configurable TTL. All application
        // instances share the same Redis store, so any instance can serve subsequent
        // requests for this session without sticky-session routing.
        String redisKeyBooking = "session:" + sessionId + ":lastBooking";
        String redisKeyGuest   = "session:" + sessionId + ":guestName";
        redisTemplate.opsForValue().set(redisKeyBooking, booking,   sessionTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(redisKeyGuest,   guestName, sessionTtlSeconds, TimeUnit.SECONDS);

        // cr-java-0067 FIX: Booking is now cached in Google Cloud Memorystore for Redis
        // with a configurable TTL (app.booking.cache.ttl-seconds, default 300 s).
        // This replaces the former static HashMap (bookingCache) that had no expiration
        // policy, causing indefinite memory growth and stale data across instances.
        // The Redis key follows the pattern "booking:cache:<bookingId>" to avoid
        // collisions with session keys and to allow targeted cache invalidation.
        String bookingId = (String) booking.get("bookingId");
        String redisCacheKey = "booking:cache:" + bookingId;
        redisTemplate.opsForValue().set(redisCacheKey, booking, bookingCacheTtlSeconds, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            // cr-java-0065 FIX: sessionId replaces HttpSession parameter.
            // The caller passes the same sessionId used during booking creation;
            // the guest name is retrieved from the shared Redis store.
            @RequestParam(required = false, defaultValue = "anonymous") String sessionId) {

        // cr-java-0065 FIX: Guest name is now retrieved from Google Cloud Memorystore
        // for Redis instead of a server-local HttpSession. Any instance in the cluster
        // can serve this request and will receive the correct value from Redis.
        String redisKeyGuest = "session:" + sessionId + ":guestName";
        String lastGuest = (String) redisTemplate.opsForValue().get(redisKeyGuest);

        // cr-java-0067 FIX: Cache-aside pattern using Google Cloud Memorystore for Redis.
        // The booking details are first looked up in the Redis cache (TTL-bounded).
        // On a cache miss the authoritative data is fetched from the database via
        // BookingService and then re-populated in Redis with the configured TTL,
        // preventing unbounded memory growth and ensuring cross-instance consistency.
        String redisCacheKey = "booking:cache:" + bookingId;
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedBooking = (Map<String, Object>) redisTemplate.opsForValue().get(redisCacheKey);
        Map<String, Object> bookingDetails;
        if (cachedBooking != null) {
            bookingDetails = cachedBooking;
        } else {
            bookingDetails = bookingService.getBookingById(bookingId);
            if (bookingDetails != null && !bookingDetails.isEmpty()) {
                redisTemplate.opsForValue().set(redisCacheKey, bookingDetails, bookingCacheTtlSeconds, TimeUnit.SECONDS);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingDetails);
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // cr-java-0071 FIX: Hard-coded environment URL replaced with externalised configuration.
        // The URL is now injected from the 'app.inventory.endpoint' property, which is set via
        // the APP_INVENTORY_ENDPOINT environment variable or GCP Secret Manager (sm:// prefix).
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
