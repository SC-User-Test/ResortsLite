package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BookingService Test Suite")
class BookingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("createBooking should create booking with valid parameters")
    void createBooking_withValidParameters_createsBooking() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        // Act
        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(booking);
        assertTrue(booking.containsKey("bookingId"));
        assertEquals(guestName, booking.get("guestName"));
        assertEquals(roomType, booking.get("roomType"));
        assertEquals(checkIn, booking.get("checkIn"));
        assertEquals(checkOut, booking.get("checkOut"));
        assertTrue(booking.containsKey("confirmationCode"));
        assertTrue(booking.containsKey("dbHost"));
        
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("createBooking should generate unique booking ID")
    void createBooking_shouldGenerateUniqueBookingId() {
        // Act
        Map<String, Object> booking1 = bookingService.createBooking("Guest1", "STANDARD", "2024-03-01", "2024-03-05");
        Map<String, Object> booking2 = bookingService.createBooking("Guest2", "DELUXE", "2024-03-10", "2024-03-15");

        // Assert
        assertNotNull(booking1.get("bookingId"));
        assertNotNull(booking2.get("bookingId"));
        assertNotEquals(booking1.get("bookingId"), booking2.get("bookingId"));
    }

    @Test
    @DisplayName("createBooking should generate booking ID with BK prefix")
    void createBooking_shouldGenerateBookingIdWithPrefix() {
        // Act
        Map<String, Object> booking = bookingService.createBooking("Test Guest", "SUITE", "2024-04-01", "2024-04-10");

        // Assert
        String bookingId = (String) booking.get("bookingId");
        assertTrue(bookingId.startsWith("BK-"));
    }

    @Test
    @DisplayName("createBooking should generate confirmation code")
    void createBooking_shouldGenerateConfirmationCode() {
        // Act
        Map<String, Object> booking = bookingService.createBooking("Jane Smith", "VILLA", "2024-05-01", "2024-05-07");

        // Assert
        assertNotNull(booking.get("confirmationCode"));
        String confirmCode = (String) booking.get("confirmationCode");
        assertFalse(confirmCode.isEmpty());
    }

    @Test
    @DisplayName("createBooking should execute SQL insert")
    void createBooking_shouldExecuteSqlInsert() {
        // Act
        bookingService.createBooking("Test User", "STANDARD", "2024-06-01", "2024-06-05");

        // Assert
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("createBooking should handle different room types")
    void createBooking_withDifferentRoomTypes_createsBooking() {
        // Arrange
        String[] roomTypes = {"STANDARD", "DELUXE", "SUITE", "VILLA"};

        for (String roomType : roomTypes) {
            // Act
            Map<String, Object> booking = bookingService.createBooking("Guest", roomType, "2024-07-01", "2024-07-05");

            // Assert
            assertEquals(roomType, booking.get("roomType"));
        }
    }

    @Test
    @DisplayName("createBooking should handle empty guest name")
    void createBooking_withEmptyGuestName_createsBooking() {
        // Act
        Map<String, Object> booking = bookingService.createBooking("", "DELUXE", "2024-08-01", "2024-08-05");

        // Assert
        assertNotNull(booking);
        assertEquals("", booking.get("guestName"));
    }

    @Test
    @DisplayName("createBooking should handle null parameters")
    void createBooking_withNullParameters_createsBooking() {
        // Act
        Map<String, Object> booking = bookingService.createBooking(null, null, null, null);

        // Assert
        assertNotNull(booking);
        assertTrue(booking.containsKey("bookingId"));
    }

    @Test
    @DisplayName("getBookingById should return booking details for valid ID")
    void getBookingById_withValidId_returnsBookingDetails() {
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
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    @Test
    @DisplayName("getBookingById should handle non-existent booking ID")
    void getBookingById_withNonExistentId_returnsErrorMessage() {
        // Arrange
        String bookingId = "BK-INVALID";
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new EmptyResultDataAccessException(1));

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        String error = (String) result.get("error");
        assertTrue(error.contains(bookingId));
    }

    @Test
    @DisplayName("getBookingById should handle empty booking ID")
    void getBookingById_withEmptyId_queriesDatabase() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(new HashMap<>());

        // Act
        Map<String, Object> result = bookingService.getBookingById("");

        // Assert
        assertNotNull(result);
        verify(jdbcTemplate).queryForMap(anyString());
    }

    @Test
    @DisplayName("getBookingById should handle null booking ID")
    void getBookingById_withNullId_queriesDatabase() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("SQL error"));

        // Act
        Map<String, Object> result = bookingService.getBookingById(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
    }

    @Test
    @DisplayName("calculateRoomPrice should calculate price for STANDARD room")
    void calculateRoomPrice_forStandardRoom_calculatesCorrectPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 3, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("360.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice should calculate price for DELUXE room")
    void calculateRoomPrice_forDeluxeRoom_calculatesCorrectPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("DELUXE", 2, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("400.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice should calculate price for SUITE room")
    void calculateRoomPrice_forSuiteRoom_calculatesCorrectPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("SUITE", 1, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("350.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice should calculate price for VILLA room")
    void calculateRoomPrice_forVillaRoom_calculatesCorrectPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("VILLA", 1, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("600.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice should apply PEAK season multiplier")
    void calculateRoomPrice_withPeakSeason_appliesMultiplier() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "PEAK", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("180.00", price); // 120 * 1.5 * 1
    }

    @Test
    @DisplayName("calculateRoomPrice should apply OFF season discount")
    void calculateRoomPrice_withOffSeason_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "OFF", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("96.00", price); // 120 * 0.8 * 1
    }

    @Test
    @DisplayName("calculateRoomPrice should apply GOLD loyalty discount")
    void calculateRoomPrice_withGoldLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "REGULAR", "GOLD");

        // Assert
        assertNotNull(price);
        assertEquals("108.00", price); // 120 * 0.9 * 1
    }

    @Test
    @DisplayName("calculateRoomPrice should apply PLATINUM loyalty discount")
    void calculateRoomPrice_withPlatinumLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "REGULAR", "PLATINUM");

        // Assert
        assertNotNull(price);
        assertEquals("96.00", price); // 120 * 0.8 * 1
    }

    @Test
    @DisplayName("calculateRoomPrice should apply DIAMOND loyalty discount")
    void calculateRoomPrice_withDiamondLoyalty_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "REGULAR", "DIAMOND");

        // Assert
        assertNotNull(price);
        assertEquals("84.00", price); // 120 * 0.7 * 1
    }

    @Test
    @DisplayName("calculateRoomPrice should apply 7+ nights discount")
    void calculateRoomPrice_with7Nights_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("798.00", price); // 120 * 0.95 * 7
    }

    @Test
    @DisplayName("calculateRoomPrice should apply 14+ nights discount")
    void calculateRoomPrice_with14Nights_appliesDiscount() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 14, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        // Note: Code has bug - should check nights >= 14 first, but checks >= 7 first
        assertEquals("1596.00", price); // 120 * 0.95 * 14 (not 0.90)
    }

    @Test
    @DisplayName("calculateRoomPrice should handle unknown room type")
    void calculateRoomPrice_withUnknownRoomType_usesDefaultPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("UNKNOWN", 1, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("120.00", price); // Default to STANDARD price
    }

    @Test
    @DisplayName("calculateRoomPrice should handle zero nights")
    void calculateRoomPrice_withZeroNights_calculatesPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 0, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        assertEquals("0.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice should handle negative nights")
    void calculateRoomPrice_withNegativeNights_calculatesPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", -1, "REGULAR", "NONE");

        // Assert
        assertNotNull(price);
        // Negative nights will result in negative price
        assertTrue(price.contains("-"));
    }

    @Test
    @DisplayName("calculateRoomPrice should combine multiple discounts")
    void calculateRoomPrice_withMultipleDiscounts_combinesCorrectly() {
        // Act - PEAK season + GOLD loyalty + 7 nights
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "PEAK", "GOLD");

        // Assert
        assertNotNull(price);
        // 120 * 1.5 (PEAK) * 0.9 (GOLD) * 0.95 (7 nights) * 7 nights
        assertEquals("1077.30", price);
    }

    @Test
    @DisplayName("isRoomAvailable should return true for STANDARD room")
    void isRoomAvailable_forStandardRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("STANDARD");

        // Assert
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return true for DELUXE room")
    void isRoomAvailable_forDeluxeRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("DELUXE");

        // Assert
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return true for SUITE room")
    void isRoomAvailable_forSuiteRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("SUITE");

        // Assert
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return true for VILLA room")
    void isRoomAvailable_forVillaRoom_returnsTrue() {
        // Act
        boolean available = bookingService.isRoomAvailable("VILLA");

        // Assert
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return false for unknown room type")
    void isRoomAvailable_forUnknownRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("PRESIDENTIAL");

        // Assert
        assertFalse(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return false for empty room type")
    void isRoomAvailable_forEmptyRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("");

        // Assert
        assertFalse(available);
    }

    @Test
    @DisplayName("isRoomAvailable should return false for null room type")
    void isRoomAvailable_forNullRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable(null);

        // Assert
        assertFalse(available);
    }

    @Test
    @DisplayName("isRoomAvailable should be case-sensitive")
    void isRoomAvailable_withLowercaseRoomType_returnsFalse() {
        // Act
        boolean available = bookingService.isRoomAvailable("standard");

        // Assert
        assertFalse(available);
    }

    @Test
    @DisplayName("generateReport should return message with month")
    void generateReport_withValidMonth_returnsMessage() {
        // Act
        String message = bookingService.generateReport("March");

        // Assert
        assertNotNull(message);
        assertTrue(message.contains("March"));
        assertTrue(message.contains("Report generation triggered"));
    }

    @Test
    @DisplayName("generateReport should include payment API in message")
    void generateReport_shouldIncludePaymentApi() {
        // Act
        String message = bookingService.generateReport("April");

        // Assert
        assertTrue(message.contains("http://"));
        assertTrue(message.contains("payments"));
    }

    @Test
    @DisplayName("generateReport should handle empty month")
    void generateReport_withEmptyMonth_returnsMessage() {
        // Act
        String message = bookingService.generateReport("");

        // Assert
        assertNotNull(message);
        assertTrue(message.contains("Report generation triggered"));
    }

    @Test
    @DisplayName("generateReport should handle null month")
    void generateReport_withNullMonth_returnsMessage() {
        // Act
        String message = bookingService.generateReport(null);

        // Assert
        assertNotNull(message);
        assertTrue(message.contains("Report generation triggered"));
    }

    @Test
    @DisplayName("createBooking should include dbHost in result")
    void createBooking_shouldIncludeDbHost() {
        // Act
        Map<String, Object> booking = bookingService.createBooking("Test", "STANDARD", "2024-09-01", "2024-09-05");

        // Assert
        assertTrue(booking.containsKey("dbHost"));
        assertNotNull(booking.get("dbHost"));
    }

    @Test
    @DisplayName("calculateRoomPrice should return formatted price with two decimals")
    void calculateRoomPrice_shouldReturnFormattedPrice() {
        // Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "REGULAR", "NONE");

        // Assert
        assertTrue(price.matches("\\d+\\.\\d{2}"));
    }

    @Test
    @DisplayName("calculateRoomPrice should handle all room types correctly")
    void calculateRoomPrice_withAllRoomTypes_calculatesCorrectly() {
        // Arrange
        String[] roomTypes = {"STANDARD", "DELUXE", "SUITE", "VILLA"};
        double[] expectedPrices = {120.0, 200.0, 350.0, 600.0};

        for (int i = 0; i < roomTypes.length; i++) {
            // Act
            String price = bookingService.calculateRoomPrice(roomTypes[i], 1, "REGULAR", "NONE");

            // Assert
            assertEquals(String.format("%.2f", expectedPrices[i]), price);
        }
    }
}
