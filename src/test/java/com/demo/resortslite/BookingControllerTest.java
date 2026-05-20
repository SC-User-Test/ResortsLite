package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for BookingController
 * Tests all endpoints, session handling, and service integration
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private MockHttpSession mockSession;

    @BeforeEach
    void setUp() {
        mockSession = new MockHttpSession();
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_withValidBookingService_createsInstance() {
        BookingService service = mock(BookingService.class);
        BookingController controller = new BookingController(service);
        assertNotNull(controller);
    }

    @Test
    void constructor_withNullBookingService_createsInstance() {
        BookingController controller = new BookingController(null);
        assertNotNull(controller);
    }

    // ========== createBooking Tests ==========

    @Test
    void createBooking_withValidParameters_returnsConfirmedBooking() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-12345678");
        mockBooking.put("guestName", guestName);
        mockBooking.put("roomType", roomType);
        mockBooking.put("checkIn", checkIn);
        mockBooking.put("checkOut", checkOut);

        when(bookingService.createBooking(guestName, roomType, checkIn, checkOut))
                .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                guestName, roomType, checkIn, checkOut, mockSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
        assertNotNull(response.get("booking"));
        assertEquals(mockBooking, response.get("booking"));
        verify(bookingService, times(1)).createBooking(guestName, roomType, checkIn, checkOut);
    }

    @Test
    void createBooking_storesBookingInSession() {
        // Arrange
        String guestName = "Jane Smith";
        String roomType = "SUITE";
        String checkIn = "2024-04-01";
        String checkOut = "2024-04-10";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-87654321");
        mockBooking.put("guestName", guestName);

        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockBooking);

        // Act
        bookingController.createBooking(guestName, roomType, checkIn, checkOut, mockSession);

        // Assert
        assertEquals(mockBooking, mockSession.getAttribute("lastBooking"));
        assertEquals(guestName, mockSession.getAttribute("guestName"));
    }

    @Test
    void createBooking_withEmptyGuestName_stillProcesses() {
        // Arrange
        String guestName = "";
        String roomType = "STANDARD";
        String checkIn = "2024-05-01";
        String checkOut = "2024-05-03";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-11111111");

        when(bookingService.createBooking(guestName, roomType, checkIn, checkOut))
                .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                guestName, roomType, checkIn, checkOut, mockSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
    }

    @Test
    void createBooking_withNullParameters_handlesGracefully() {
        // Arrange
        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-99999999");

        when(bookingService.createBooking(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                null, null, null, null, mockSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
    }

    @Test
    void createBooking_withSpecialCharactersInGuestName_processes() {
        // Arrange
        String guestName = "O'Brien-Smith";
        String roomType = "VILLA";
        String checkIn = "2024-06-01";
        String checkOut = "2024-06-15";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-22222222");
        mockBooking.put("guestName", guestName);

        when(bookingService.createBooking(guestName, roomType, checkIn, checkOut))
                .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                guestName, roomType, checkIn, checkOut, mockSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
        assertEquals(guestName, mockSession.getAttribute("guestName"));
    }

    // ========== getBookingStatus Tests ==========

    @Test
    void getBookingStatus_withValidBookingId_returnsStatus() {
        // Arrange
        String bookingId = "BK-12345678";
        String guestName = "Test Guest";
        mockSession.setAttribute("guestName", guestName);

        Map<String, Object> mockDetails = new HashMap<>();
        mockDetails.put("bookingId", bookingId);
        mockDetails.put("status", "confirmed");

        when(bookingService.getBookingById(bookingId)).thenReturn(mockDetails);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, mockSession);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(guestName, result.get("sessionGuest"));
        assertEquals(mockDetails, result.get("details"));
        verify(bookingService, times(1)).getBookingById(bookingId);
    }

    @Test
    void getBookingStatus_withNoSessionGuest_returnsNullGuest() {
        // Arrange
        String bookingId = "BK-87654321";
        Map<String, Object> mockDetails = new HashMap<>();
        mockDetails.put("bookingId", bookingId);

        when(bookingService.getBookingById(bookingId)).thenReturn(mockDetails);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, mockSession);

        // Assert
        assertNotNull(result);
        assertNull(result.get("sessionGuest"));
        assertEquals(bookingId, result.get("bookingId"));
    }

    @Test
    void getBookingStatus_withEmptyBookingId_callsService() {
        // Arrange
        String bookingId = "";
        Map<String, Object> mockDetails = new HashMap<>();

        when(bookingService.getBookingById(bookingId)).thenReturn(mockDetails);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, mockSession);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("bookingId"));
        verify(bookingService, times(1)).getBookingById(bookingId);
    }

    @Test
    void getBookingStatus_withNullBookingId_handlesGracefully() {
        // Arrange
        Map<String, Object> mockDetails = new HashMap<>();
        when(bookingService.getBookingById(null)).thenReturn(mockDetails);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(null, mockSession);

        // Assert
        assertNotNull(result);
        assertNull(result.get("bookingId"));
    }

    // ========== checkAvailability Tests ==========

    @Test
    void checkAvailability_withValidRoomType_returnsAvailability() {
        // Arrange
        String roomType = "DELUXE";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        assertNotNull(response);
        assertEquals(roomType, response.get("roomType"));
        assertNotNull(response.get("inventoryEndpoint"));
        assertTrue((Boolean) response.get("available"));
        verify(bookingService, times(1)).isRoomAvailable(roomType);
    }

    @Test
    void checkAvailability_withUnavailableRoom_returnsFalse() {
        // Arrange
        String roomType = "PRESIDENTIAL";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        assertNotNull(response);
        assertEquals(roomType, response.get("roomType"));
        assertFalse((Boolean) response.get("available"));
    }

    @Test
    void checkAvailability_withEmptyRoomType_callsService() {
        // Arrange
        String roomType = "";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        assertNotNull(response);
        assertEquals(roomType, response.get("roomType"));
        verify(bookingService, times(1)).isRoomAvailable(roomType);
    }

    @Test
    void checkAvailability_withNullRoomType_handlesGracefully() {
        // Arrange
        when(bookingService.isRoomAvailable(null)).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(null);

        // Assert
        assertNotNull(response);
        assertNull(response.get("roomType"));
    }

    @Test
    void checkAvailability_includesInventoryEndpoint() {
        // Arrange
        String roomType = "SUITE";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        String endpoint = (String) response.get("inventoryEndpoint");
        assertNotNull(endpoint);
        assertTrue(endpoint.contains("inventory-service"));
    }

    // ========== downloadReport Tests ==========

    @Test
    void downloadReport_withValidMonth_returnsReportPath() {
        // Arrange
        String month = "March";
        String expectedMessage = "Report generated for March";
        when(bookingService.generateReport(month)).thenReturn(expectedMessage);

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        assertNotNull(response);
        assertNotNull(response.get("reportPath"));
        assertTrue(((String) response.get("reportPath")).contains(month));
        assertEquals(expectedMessage, response.get("message"));
        verify(bookingService, times(1)).generateReport(month);
    }

    @Test
    void downloadReport_withEmptyMonth_stillProcesses() {
        // Arrange
        String month = "";
        when(bookingService.generateReport(month)).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        assertNotNull(response);
        assertNotNull(response.get("reportPath"));
    }

    @Test
    void downloadReport_withNullMonth_handlesGracefully() {
        // Arrange
        when(bookingService.generateReport(null)).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport(null);

        // Assert
        assertNotNull(response);
        assertNotNull(response.get("reportPath"));
    }

    @Test
    void downloadReport_includesLegacyReportPath() {
        // Arrange
        String month = "January";
        when(bookingService.generateReport(month)).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertNotNull(reportPath);
        assertTrue(reportPath.contains("/var/legacy/reports/"));
    }

    @Test
    void downloadReport_withSpecialCharactersInMonth_processes() {
        // Arrange
        String month = "2024-03";
        when(bookingService.generateReport(month)).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        assertNotNull(response);
        assertTrue(((String) response.get("reportPath")).contains(month));
    }
}
