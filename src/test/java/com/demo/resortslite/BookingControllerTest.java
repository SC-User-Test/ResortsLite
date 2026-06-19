package com.demo.resortslite;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BookingController Test Suite")
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private HttpSession httpSession;

    @InjectMocks
    private BookingController bookingController;

    private Map<String, Object> mockBooking;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock booking data
        mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-12345678");
        mockBooking.put("guestName", "John Doe");
        mockBooking.put("roomType", "DELUXE");
        mockBooking.put("checkIn", "2024-03-01");
        mockBooking.put("checkOut", "2024-03-05");
        mockBooking.put("confirmationCode", "ABC123XYZ");
    }

    @Test
    @DisplayName("createBooking should return confirmed booking with valid parameters")
    void createBooking_withValidParameters_returnsConfirmedBooking() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";
        
        when(bookingService.createBooking(guestName, roomType, checkIn, checkOut))
            .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
            guestName, roomType, checkIn, checkOut, httpSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
        assertNotNull(response.get("booking"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) response.get("booking");
        assertEquals("BK-12345678", booking.get("bookingId"));
        assertEquals(guestName, booking.get("guestName"));
        
        verify(bookingService, times(1)).createBooking(guestName, roomType, checkIn, checkOut);
        verify(httpSession, times(1)).setAttribute("lastBooking", mockBooking);
        verify(httpSession, times(1)).setAttribute("guestName", guestName);
    }

    @Test
    @DisplayName("createBooking should store booking in session")
    void createBooking_shouldStoreBookingInSession() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(mockBooking);

        // Act
        bookingController.createBooking("Jane Smith", "SUITE", "2024-04-01", "2024-04-10", httpSession);

        // Assert
        verify(httpSession).setAttribute("lastBooking", mockBooking);
        verify(httpSession).setAttribute("guestName", "Jane Smith");
    }

    @Test
    @DisplayName("createBooking with different room types should work correctly")
    void createBooking_withDifferentRoomTypes_worksCorrectly() {
        // Arrange
        String[] roomTypes = {"STANDARD", "DELUXE", "SUITE", "VILLA"};
        
        for (String roomType : roomTypes) {
            Map<String, Object> booking = new HashMap<>(mockBooking);
            booking.put("roomType", roomType);
            when(bookingService.createBooking(anyString(), eq(roomType), anyString(), anyString()))
                .thenReturn(booking);

            // Act
            Map<String, Object> response = bookingController.createBooking(
                "Test Guest", roomType, "2024-05-01", "2024-05-05", httpSession);

            // Assert
            assertNotNull(response);
            assertEquals("confirmed", response.get("status"));
        }
    }

    @Test
    @DisplayName("getBookingStatus should return booking details with valid bookingId")
    void getBookingStatus_withValidBookingId_returnsBookingDetails() {
        // Arrange
        String bookingId = "BK-12345678";
        String guestName = "John Doe";
        
        when(httpSession.getAttribute("guestName")).thenReturn(guestName);
        when(bookingService.getBookingById(bookingId)).thenReturn(mockBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, httpSession);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(guestName, result.get("sessionGuest"));
        assertNotNull(result.get("details"));
        
        verify(httpSession, times(1)).getAttribute("guestName");
        verify(bookingService, times(1)).getBookingById(bookingId);
    }

    @Test
    @DisplayName("getBookingStatus should handle null session guest")
    void getBookingStatus_withNullSessionGuest_handlesGracefully() {
        // Arrange
        String bookingId = "BK-99999999";
        when(httpSession.getAttribute("guestName")).thenReturn(null);
        when(bookingService.getBookingById(bookingId)).thenReturn(mockBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, httpSession);

        // Assert
        assertNotNull(result);
        assertNull(result.get("sessionGuest"));
        assertEquals(bookingId, result.get("bookingId"));
    }

    @Test
    @DisplayName("getBookingStatus should handle empty bookingId")
    void getBookingStatus_withEmptyBookingId_callsService() {
        // Arrange
        String emptyBookingId = "";
        when(bookingService.getBookingById(emptyBookingId)).thenReturn(new HashMap<>());

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(emptyBookingId, httpSession);

        // Assert
        assertNotNull(result);
        verify(bookingService).getBookingById(emptyBookingId);
    }

    @Test
    @DisplayName("checkAvailability should return availability information")
    void checkAvailability_withValidRoomType_returnsAvailabilityInfo() {
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
    @DisplayName("checkAvailability should handle unavailable rooms")
    void checkAvailability_withUnavailableRoom_returnsFalse() {
        // Arrange
        String roomType = "PRESIDENTIAL";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get("available"));
    }

    @Test
    @DisplayName("checkAvailability should include inventory endpoint URL")
    void checkAvailability_shouldIncludeInventoryEndpoint() {
        // Arrange
        when(bookingService.isRoomAvailable(anyString())).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("SUITE");

        // Assert
        assertTrue(response.containsKey("inventoryEndpoint"));
        String endpoint = (String) response.get("inventoryEndpoint");
        assertTrue(endpoint.contains("inventory-service"));
    }

    @Test
    @DisplayName("downloadReport should return report path and message")
    void downloadReport_withValidMonth_returnsReportInfo() {
        // Arrange
        String month = "March";
        String expectedMessage = "Report generated successfully";
        when(bookingService.generateReport(month)).thenReturn(expectedMessage);

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("reportPath"));
        assertEquals(expectedMessage, response.get("message"));
        
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.contains(month));
        assertTrue(reportPath.endsWith(".pdf"));
        
        verify(bookingService, times(1)).generateReport(month);
    }

    @Test
    @DisplayName("downloadReport should handle different month formats")
    void downloadReport_withDifferentMonthFormats_generatesCorrectPath() {
        // Arrange
        String[] months = {"January", "02", "Mar", "2024-04"};
        
        for (String month : months) {
            when(bookingService.generateReport(month)).thenReturn("Report ready");

            // Act
            Map<String, Object> response = bookingController.downloadReport(month);

            // Assert
            assertNotNull(response);
            String reportPath = (String) response.get("reportPath");
            assertTrue(reportPath.contains(month));
        }
    }

    @Test
    @DisplayName("downloadReport should construct correct file path")
    void downloadReport_shouldConstructCorrectFilePath() {
        // Arrange
        String month = "December";
        when(bookingService.generateReport(month)).thenReturn("Success");

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.startsWith("/var/legacy/reports/"));
        assertTrue(reportPath.contains("December"));
        assertTrue(reportPath.endsWith("_bookings.pdf"));
    }

    @Test
    @DisplayName("createBooking should handle null parameters gracefully")
    void createBooking_withNullParameters_handlesGracefully() {
        // Arrange
        Map<String, Object> nullBooking = new HashMap<>();
        nullBooking.put("bookingId", "BK-NULL");
        when(bookingService.createBooking(isNull(), isNull(), isNull(), isNull()))
            .thenReturn(nullBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
            null, null, null, null, httpSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
    }

    @Test
    @DisplayName("createBooking should handle empty string parameters")
    void createBooking_withEmptyStrings_callsService() {
        // Arrange
        when(bookingService.createBooking("", "", "", "")).thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
            "", "", "", "", httpSession);

        // Assert
        assertNotNull(response);
        verify(bookingService).createBooking("", "", "", "");
    }

    @Test
    @DisplayName("checkAvailability should handle null room type")
    void checkAvailability_withNullRoomType_callsService() {
        // Arrange
        when(bookingService.isRoomAvailable(null)).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(null);

        // Assert
        assertNotNull(response);
        assertNull(response.get("roomType"));
    }

    @Test
    @DisplayName("downloadReport should handle empty month parameter")
    void downloadReport_withEmptyMonth_generatesPath() {
        // Arrange
        when(bookingService.generateReport("")).thenReturn("Empty month report");

        // Act
        Map<String, Object> response = bookingController.downloadReport("");

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("reportPath"));
    }
}
