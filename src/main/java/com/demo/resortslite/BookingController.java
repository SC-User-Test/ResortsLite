package com.demo.resortslite;

import io.jsonwebtoken.Claims;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.AddrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * BookingController — stateless JWT-based session management (cz-java-0063 fix).
 *
 * Server-side HttpSession has been eliminated. Guest identity is now carried in a
 * signed JWT returned to the client on booking creation and presented as a Bearer
 * token on subsequent requests. The JWT signing secret is injected at runtime via
 * the JWT_SECRET environment variable, which ECS Fargate resolves from AWS Secrets
 * Manager, ensuring no session state is held in container memory.
 *
 * Occurrences fixed (cz-java-0063):
 *   - Line 6  (original): removed  import javax.servlet.http.HttpSession
 *   - Line 27 (original): removed  HttpSession session  parameter from createBooking()
 *   - Line 48 (original): removed  HttpSession session  parameter from getBookingStatus()
 *
 * ── cz-java-0069 : In-Memory Session Storage — ALB Session Affinity (Transitional) ──
 *
 * PROBLEM  : The original code stored booking state in HttpSession
 *            (session.setAttribute("lastBooking", ...) and session.setAttribute("guestName", ...)).
 *            In-memory session data is lost on container restart and is invisible to other
 *            ECS task instances, breaking user experience under horizontal scaling.
 *
 * TRANSITIONAL FIX (cz-java-0069):
 *   Enable ALB Target Group Stickiness so that a given client is consistently routed to
 *   the same ECS Fargate task for the duration of its session, minimising disruption while
 *   the full stateless/Redis migration is completed.
 *
 *   AWS ECS / ALB configuration required (IaC / Console):
 *     1. Target Group → Attributes → Stickiness: ENABLED
 *        - Stickiness type : lb_cookie  (or app_cookie for application-controlled cookies)
 *        - Duration        : 86400 seconds (1 day) — tune to match session lifetime
 *     2. ECS Service → Load Balancing → Target Group: reference the stickiness-enabled TG
 *     3. ALB Listener Rule: forward /api/bookings/* to the sticky target group
 *
 *   Environment variables consumed by this service (set in ECS Task Definition):
 *     - JWT_SECRET        : signing key for stateless JWT tokens (AWS Secrets Manager)
 *     - REPORT_BASE_PATH  : EFS mount path for report files
 *
 *   Long-term recommendation: migrate session state to Amazon ElastiCache (Redis) and
 *   remove ALB stickiness once all session reads/writes use the distributed cache.
 *
 * Occurrences fixed (cz-java-0069):
 *   - Line 34 (original): session.setAttribute("lastBooking", booking)  → removed; JWT used
 *   - Line 35 (original): session.setAttribute("guestName", guestName)  → removed; JWT used
 */
/**
 * cz-java-0082 — ECS Service Connect for Decoupled Inter-Service Communication
 *
 * PROBLEM  : The original code contained a hardcoded inter-service URL
 *            "http://inventory-service.internal:8081/rooms/available" (line 84 original).
 *            Hardcoded URLs create tight coupling between services, preventing independent
 *            deployment, scaling, and service discovery in a containerised microservices
 *            architecture on ECS Fargate.
 *
 * FIX      : The URL is now resolved at runtime from the INVENTORY_SERVICE_URL environment
 *            variable, injected via the ECS Task Definition. ECS Service Connect provides
 *            automatic service discovery, mTLS, and traffic observability between Fargate
 *            services without hardcoded hostnames or IP addresses.
 *
 * ECS Service Connect configuration required (ECS Service definition):
 *   1. Enable Service Connect on the ECS Service for the "resortslite" namespace.
 *   2. Define a Service Connect client alias for the inventory service:
 *        { "port": 8081, "dnsName": "inventory-service" }
 *   3. Set the environment variable in the ECS Task Definition:
 *        INVENTORY_SERVICE_URL=http://inventory-service:8081/rooms/available
 *      (ECS Service Connect resolves "inventory-service" via its internal DNS.)
 *
 * Occurrence fixed (cz-java-0082):
 *   - Line 84 (original): "http://inventory-service.internal:8081/rooms/available"
 *                          → replaced with ${INVENTORY_SERVICE_URL} env var
 */
/**
 * cz-java-0070 — Local Caches replaced with Amazon ElastiCache for Memcached
 *
 * PROBLEM  : The original code used a static in-process HashMap as a local cache
 *            (bookingCache = new HashMap<>()). Local caches are instance-local and
 *            invisible to other ECS Fargate task instances. Under horizontal scaling,
 *            cache misses occur on every request routed to a different container,
 *            defeating the purpose of caching and causing inconsistent behaviour.
 *
 * FIX      : The local HashMap cache has been replaced with Amazon ElastiCache for
 *            Memcached. The Memcached endpoint is injected at runtime via the
 *            MEMCACHED_ENDPOINT environment variable, which is resolved from AWS SSM
 *            Parameter Store and injected into the ECS Fargate task definition.
 *            All ECS task instances share the same distributed Memcached cluster,
 *            ensuring cache consistency across horizontal scaling events.
 *
 * AWS infrastructure required:
 *   1. Amazon ElastiCache cluster (Memcached engine) in the same VPC as ECS Fargate.
 *   2. AWS SSM Parameter Store entry:
 *        /resortslite/cache/memcached-endpoint  →  <cluster-endpoint>:11211
 *   3. ECS Task Definition environment variable (resolved from SSM):
 *        MEMCACHED_ENDPOINT=<cluster-endpoint>:11211
 *   4. ECS Task IAM Role must include ssm:GetParameter permission for the above path.
 *
 * Occurrence fixed (cz-java-0070):
 *   - Line 19 (original): private static final Map<String, Object> bookingCache = new HashMap<>()
 *                          → replaced with MemcachedClient backed by MEMCACHED_ENDPOINT env var
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    // EFS-backed volume mount path resolved via environment variable REPORT_BASE_PATH.
    // Mount the EFS filesystem in the ECS Fargate task definition at the path specified
    // by REPORT_BASE_PATH so the container can access persistent report files.
    @Value("${REPORT_BASE_PATH:/mnt/efs/reports/}")
    private String reportBasePath;

    /**
     * Inter-service URL for the inventory service, resolved via ECS Service Connect.
     * Set INVENTORY_SERVICE_URL in the ECS Task Definition environment variables.
     * ECS Service Connect will resolve the logical service name to the correct
     * Fargate task endpoint automatically, enabling mTLS and traffic observability.
     */
    @Value("${INVENTORY_SERVICE_URL:http://inventory-service:8081/rooms/available}")
    private String inventoryServiceUrl;

    /**
     * Amazon ElastiCache for Memcached endpoint, injected via the MEMCACHED_ENDPOINT
     * environment variable. The value is stored in AWS SSM Parameter Store and resolved
     * into the ECS Fargate task definition at deployment time.
     *
     * cz-java-0070 FIX: Replaces the former static in-process HashMap bookingCache.
     * Expected format: <cluster-endpoint>:11211
     * SSM Parameter: /resortslite/cache/memcached-endpoint
     */
    @Value("${MEMCACHED_ENDPOINT:localhost:11211}")
    private String memcachedEndpoint;

    // cz-java-0070 FIX: Distributed Memcached client replaces the former local HashMap cache.
    // The MemcachedClient connects to Amazon ElastiCache for Memcached using the endpoint
    // injected via MEMCACHED_ENDPOINT (resolved from AWS SSM Parameter Store).
    // All ECS Fargate task instances share this distributed cache, ensuring consistency
    // under horizontal scaling — unlike the previous instance-local HashMap.
    private MemcachedClient memcachedClient;

    private static final int CACHE_TTL_SECONDS = 3600; // 1-hour TTL for booking entries

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Lazily initialises the MemcachedClient using the MEMCACHED_ENDPOINT environment
     * variable. Lazy initialisation avoids startup failures when the ElastiCache cluster
     * is not yet reachable during container warm-up.
     *
     * cz-java-0070: The endpoint is injected from AWS SSM Parameter Store via the ECS
     * Task Definition, ensuring no hardcoded cache addresses in the container image.
     */
    private MemcachedClient getMemcachedClient() throws IOException {
        if (memcachedClient == null) {
            memcachedClient = new MemcachedClient(AddrUtil.getAddresses(memcachedEndpoint));
        }
        return memcachedClient;
    }

    /**
     * Creates a booking and returns a signed JWT embedding the guest identity.
     *
     * The JWT replaces the former HttpSession attributes "lastBooking" and "guestName"
     * (cz-java-0063). The client stores the token and presents it as a Bearer token on
     * subsequent requests — no server-side session state is required.
     *
     * cz-java-0070: Booking entry is stored in the distributed Memcached cache instead
     * of the former instance-local HashMap, ensuring cache visibility across all ECS tasks.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Build JWT claims — replaces session.setAttribute("lastBooking", booking)
        //                                  session.setAttribute("guestName", guestName)
        Map<String, Object> claims = new HashMap<>();
        claims.put("guestName", guestName);
        claims.put("bookingId", booking.get("bookingId"));
        String token = jwtUtil.generateToken(claims, guestName);

        // cz-java-0070 FIX: Store booking in distributed ElastiCache Memcached cluster.
        // Replaces: bookingCache.put((String) booking.get("bookingId"), booking)
        // The distributed cache is visible to all ECS Fargate task instances, ensuring
        // cache hits regardless of which container handles subsequent requests.
        try {
            getMemcachedClient().set(
                (String) booking.get("bookingId"),
                CACHE_TTL_SECONDS,
                booking.toString()
            );
        } catch (IOException e) {
            // Log cache write failure but do not fail the booking request.
            // The application remains functional without the cache entry.
            System.err.println("[cz-java-0070] Memcached write failed for bookingId="
                + booking.get("bookingId") + ": " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        // Return the JWT so the client can use it for subsequent authenticated requests
        response.put("token", token);
        return response;
    }

    /**
     * Returns booking status. Guest identity is resolved from the JWT Bearer token
     * supplied in the Authorization header, replacing the former HttpSession lookup
     * (cz-java-0063).
     *
     * cz-java-0070: Booking lookup uses the distributed Memcached cache instead of
     * the former instance-local HashMap, ensuring consistent reads across all ECS tasks.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Resolve guest name from JWT — replaces session.getAttribute("guestName")
        String lastGuest = jwtUtil.extractSubjectFromBearer(authHeader);

        // cz-java-0070 FIX: Retrieve booking from distributed ElastiCache Memcached cluster.
        // Replaces: bookingCache.get(bookingId)
        Object cachedBooking = null;
        try {
            cachedBooking = getMemcachedClient().get(bookingId);
        } catch (IOException e) {
            System.err.println("[cz-java-0070] Memcached read failed for bookingId="
                + bookingId + ": " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("cachedEntry", cachedBooking);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // cz-java-0082 FIX: Replaced hardcoded inter-service URL with environment variable.
        // INVENTORY_SERVICE_URL is injected via ECS Task Definition and resolved by
        // ECS Service Connect, providing automatic service discovery and mTLS between
        // independently deployed Fargate services.
        String inventoryUrl = inventoryServiceUrl;

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // EFS-backed volume mount path resolved via environment variable REPORT_BASE_PATH.
        // Mount the EFS filesystem in the ECS Fargate task definition at the path specified
        // by REPORT_BASE_PATH so the container can access persistent report files.
        String reportPath = reportBasePath + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
