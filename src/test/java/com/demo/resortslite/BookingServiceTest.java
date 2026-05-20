package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for BookingService
 * Tests all business logic, database operations, and edge cases
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    // ========== Constructor Tests ==========

    @Test
    void constructor_withValidJdbcTemplate_createsInstance() {
        JdbcTemplate template = mock(JdbcTemplate.class);
        BookingService service = new BookingService(template);
        assertNotNull(service);
    }

    @Test
    void constructor_withNullJdbcTemplate_createsInstance() {
        BookingService service = new BookingService(null);
        assertNotNull(service);
    }

    // ========== createBooking Tests ==========

    @Test
    void createBooking_withValidParameters_createsBooking() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("bookingId"));
        assertEquals(guestName, result.get("guestName"));
        assertEquals(roomType, result.get("roomType"));
        assertEquals(checkIn, result.get("checkIn"));
        assertEquals(checkOut, result.get("checkOut"));
        assertNotNull(result.get("confirmationCode"));
        assertNotNull(result.get("dbHost"));
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    void createBooking_generatesUniqueBookingId() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result1 = bookingService.createBooking("Guest1", "SUITE", "2024-03-01", "2024-03-05");
        Map<String, Object> result2 = bookingService.createBooking("Guest2", "DELUXE", "2024-03-10", "2024-03-15");

        // Assert
        assertNotNull(result1.get("bookingId"));
        assertNotNull(result2.get("bookingId"));
        assertNotEquals(result1.get("bookingId"), result2.get("bookingId"));
    }

    @Test
    void createBooking_bookingIdStartsWithBK() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking("Test Guest", "STANDARD", "2024-04-01", "2024-04-03");

        // Assert
        String bookingId = (String) result.get("bookingId");
        assertTrue(bookingId.startsWith("BK-"));
    }

    @Test
    void createBooking_generatesConfirmationCode() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking("Jane Smith", "VILLA", "2024-05-01", "2024-05-10");

        // Assert
        assertNotNull(result.get("confirmationCode"));
        String confirmCode = (String) result.get("confirmationCode");
        assertTrue(confirmCode.length() > 0);
    }

    @Test
    void createBooking_withEmptyGuestName_stillCreates() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking("", "STANDARD", "2024-06-01", "2024-06-03");

        // Assert
        assertNotNull(result);
        assertEquals("", result.get("guestName"));
    }

    @Test
    void createBooking_withNullParameters_handlesGracefully() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(null, null, null, null);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("bookingId"));
    }

    @Test
    void createBooking_withSpecialCharactersInGuestName_processes() {
        // Arrange
        String guestName = "O'Brien-Smith";
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, "DELUXE", "2024-07-01", "2024-07-05");

        // Assert
        assertNotNull(result);
        assertEquals(guestName, result.get("guestName"));
    }

    // ========== getBookingById Tests ==========

    @Test
    void getBookingById_withValidId_returnsBooking() {
        // Arrange
        String bookingId = "BK-12345678";
        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("id", bookingId);
        mockBooking.put("guest", "John Doe");
        mockBooking.put("room", "DELUXE");

        when(jdbcTemplate.queryForMap(anyString())).thenReturn(mockBooking);

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("id"));
        assertEquals("John Doe", result.get("guest"));
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    @Test
    void getBookingById_withNonExistentId_returnsError() {
        // Arrange
        String bookingId = "BK-99999999";
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new EmptyResultDataAccessException(1));

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).contains(bookingId));
    }

    @Test
    void getBookingById_withEmptyId_callsDatabase() {
        // Arrange
        String bookingId = "";
        Map<String, Object> mockBooking = new HashMap<>();
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(mockBooking);

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    @Test
    void getBookingById_withNullId_handlesGracefully() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("SQL error"));

        // Act
        Map<String, Object> result = bookingService.getBookingById(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
    }

    @Test
    void getBookingById_withDatabaseException_returnsErrorMessage() {
        // Arrange
        String bookingId = "BK-ERROR";
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("Database connection failed"));

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
    }

    // ========== calculateRoomPrice Tests ==========

    @Test
    void calculateRoomPrice_standardRoom_regularSeason_noLoyalty() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 3, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("360.00", price); // 120 * 3
    }

    @Test
    void calculateRoomPrice_deluxeRoom_regularSeason_noLoyalty() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 2, "REGULAR", "NONE");

        // Assert
        assertEquals("400.00", price); // 200 * 2
    }

    @Test
    void calculateRoomPrice_suiteRoom_regularSeason_noLoyalty() {
        // Act
        String price = bookingService.calculateRoomPrice("SUITE", 4, "REGULAR", "NONE");

        // Assert
        assertEquals("1400.00", price); // 350 * 4
    }

    @Test
    void calculateRoomPrice_villaRoom_regularSeason_noLoyalty() {
        // Act
        String price = bookingService.calculateRoomPrice("VILLA", 5, "REGULAR", "NONE");

        // Assert
        assertEquals("3000.00", price); // 600 * 5
    }

    @Test
    void calculateRoomPrice_peakSeason_increasesPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 2, "PEAK", "NONE");

        // Assert
        assertEquals("360.00", price); // 120 * 1.5 * 2
    }

    @Test
    void calculateRoomPrice_offSeason_decreasesPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 2, "OFF", "NONE");

        // Assert
        assertEquals("192.00", price); // 120 * 0.8 * 2
    }

    @Test
    void calculateRoomPrice_goldLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "GOLD");

        // Assert
        assertEquals("540.00", price); // 200 * 0.9 * 3
    }

    @Test
    void calculateRoomPrice_platinumLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "PLATINUM");

        // Assert
        assertEquals("480.00", price); // 200 * 0.8 * 3
    }

    @Test
    void calculateRoomPrice_diamondLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "DIAMOND");

        // Assert
        assertEquals("420.00", price); // 200 * 0.7 * 3
    }

    @Test
    void calculateRoomPrice_sevenNights_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "REGULAR", "NONE");

        // Assert
        assertEquals("798.00", price); // 120 * 0.95 * 7
    }

    @Test
    void calculateRoomPrice_fourteenNights_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 14, "REGULAR", "NONE");

        // Assert
        assertEquals("1596.00", price); // 120 * 0.95 * 14 (note: 14 >= 7 applies first discount)
    }

    @Test
    void calculateRoomPrice_unknownRoomType_usesDefaultPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("UNKNOWN", 2, "REGULAR", "NONE");

        // Assert
        assertEquals("240.00", price); // defaults to 120 * 2
    }

    @Test
    void calculateRoomPrice_combinedDiscounts_peakSeasonAndGold() {
        // Act
        String price = bookingService.calculateRoomPrice("SUITE", 3, "PEAK", "GOLD");

        // Assert
        assertEquals("1417.50", price); // 350 * 1.5 * 0.9 * 3
    }

    @Test
    void calculateRoomPrice_combinedDiscounts_offSeasonAndPlatinum() {
        // Act
        String price = bookingService.calculateRoomPrice("VILLA", 2, "OFF", "PLATINUM");

        // Assert
        assertEquals("768.00", price); // 600 * 0.8 * 0.8 * 2
    }

    @Test
    void calculateRoomPrice_zeroNights_returnsZero() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 0, "REGULAR", "NONE");

        // Assert
        assertEquals("0.00", price);
    }

    @Test
    void calculateRoomPrice_negativeNights_returnsNegativePrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", -1, "REGULAR", "NONE");

        // Assert
        assertTrue(price.startsWith("-"));
    }

    @Test
    void calculateRoomPrice_withNullRoomType_handlesGracefully() {
        // Act
        String price = bookingService.calculateRoomPrice(null, 2, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("240.00", price); // defaults to 120 * 2
    }

    @Test
    void calculateRoomPrice_withNullSeason_handlesGracefully() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 2, null, "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("240.00", price);
    }

    @Test
    void calculateRoomPrice_withNullLoyalty_handlesGracefully() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 2, "REGULAR", null);

        // Assert
        assertNotNull(price);
        assertEquals("240.00", price);
    }

    // ========== isRoomAvailable Tests ==========

    @Test
    void isRoomAvailable_standardRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("STANDARD");

        // Assert
        assertTrue(available);
    }

    @Test
    void isRoomAvailable_deluxeRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("DELUXE");

        // Assert
        assertTrue(available);
    }

    @Test
    void isRoomAvailable_suiteRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("SUITE");

        // Assert
        assertTrue(available);
    }

    @Test
    void isRoomAvailable_villaRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("VILLA");

        // Assert
        assertTrue(available);
    }

    @Test
    void isRoomAvailable_unknownRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("PRESIDENTIAL");

        // Assert
        assertFalse(available);
    }

    @Test
    void isRoomAvailable_emptyRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("");

        // Assert
        assertFalse(available);
    }

    @Test
    void isRoomAvailable_nullRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable(null);

        // Assert
        assertFalse(available);
    }

    @Test
    void isRoomAvailable_lowercaseRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("standard");

        // Assert
        assertFalse(available);
    }

    @Test
    void isRoomAvailable_mixedCaseRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("Standard");

        // Assert
        assertFalse(available);
    }

    // ========== generateReport Tests ==========

    @Test
    void generateReport_withValidMonth_returnsMessage() {
        // Act
        String result = bookingService.generateReport("March");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("March"));
        assertTrue(result.contains("Report generation triggered"));
    }

    @Test
    void generateReport_withEmptyMonth_returnsMessage() {
        // Act
        String result = bookingService.generateReport("");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Report generation triggered"));
    }

    @Test
    void generateReport_withNullMonth_returnsMessage() {
        // Act
        String result = bookingService.generateReport(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Report generation triggered"));
    }

    @Test
    void generateReport_includesPaymentApiUrl() {
        // Act
        String result = bookingService.generateReport("January");

        // Assert
        assertTrue(result.contains("http://"));
        assertTrue(result.contains("payments"));
    }

    @Test
    void generateReport_withSpecialCharacters_handlesGracefully() {
        // Act
        String result = bookingService.generateReport("2024-03");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("2024-03"));
    }
}
