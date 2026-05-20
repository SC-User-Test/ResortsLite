package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ReportService
 * Tests report generation, file operations, and system information retrieval
 */
class ReportServiceTest {

    private ReportService reportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_createsInstance() {
        ReportService service = new ReportService();
        assertNotNull(service);
    }

    // ========== generateMonthlyReport Tests ==========

    @Test
    void generateMonthlyReport_withValidMonthAndYear_attemptsGeneration() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("March", "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
        assertTrue(result.containsKey("path") || result.containsKey("message"));
    }

    @Test
    void generateMonthlyReport_withValidParameters_includesFileName() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("January", "2024");

        // Assert
        assertNotNull(result);
        if (result.containsKey("path")) {
            String path = (String) result.get("path");
            assertTrue(path.contains("resort_report_January_2024.csv"));
        }
    }

    @Test
    void generateMonthlyReport_withDifferentMonth_generatesUniqueFileName() {
        // Act
        Map<String, Object> result1 = reportService.generateMonthlyReport("February", "2024");
        Map<String, Object> result2 = reportService.generateMonthlyReport("March", "2024");

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        if (result1.containsKey("path") && result2.containsKey("path")) {
            assertNotEquals(result1.get("path"), result2.get("path"));
        }
    }

    @Test
    void generateMonthlyReport_withDifferentYear_generatesUniqueFileName() {
        // Act
        Map<String, Object> result1 = reportService.generateMonthlyReport("March", "2023");
        Map<String, Object> result2 = reportService.generateMonthlyReport("March", "2024");

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        if (result1.containsKey("path") && result2.containsKey("path")) {
            assertNotEquals(result1.get("path"), result2.get("path"));
        }
    }

    @Test
    void generateMonthlyReport_includesServerPort() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("April", "2024");

        // Assert
        assertNotNull(result);
        if (result.containsKey("serverPort")) {
            assertEquals(8080, result.get("serverPort"));
        }
    }

    @Test
    void generateMonthlyReport_withEmptyMonth_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("", "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withEmptyYear_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("May", "");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withNullMonth_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(null, "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withNullYear_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("June", null);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withSpecialCharactersInMonth_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03-March", "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withNumericMonth_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_withLongMonthName_handlesGracefully() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("September", "2024");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_statusIsEitherGeneratedOrError() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("July", "2024");

        // Assert
        assertNotNull(result);
        String status = (String) result.get("status");
        assertTrue(status.equals("generated") || status.equals("error"));
    }

    @Test
    void generateMonthlyReport_whenError_includesMessage() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("August", "2024");

        // Assert
        assertNotNull(result);
        if ("error".equals(result.get("status"))) {
            assertTrue(result.containsKey("message"));
        }
    }

    // ========== buildReportDownloadUrl Tests ==========

    @Test
    void buildReportDownloadUrl_withValidReportName_returnsUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("march_2024.pdf");

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("march_2024.pdf"));
    }

    @Test
    void buildReportDownloadUrl_includesHttpProtocol() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.pdf");

        // Assert
        assertTrue(url.startsWith("http://"));
    }

    @Test
    void buildReportDownloadUrl_includesReportsInternalDomain() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.pdf");

        // Assert
        assertTrue(url.contains("reports.resorts-internal.com"));
    }

    @Test
    void buildReportDownloadUrl_includesPort8080() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.pdf");

        // Assert
        assertTrue(url.contains(":8080"));
    }

    @Test
    void buildReportDownloadUrl_includesDownloadPath() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.pdf");

        // Assert
        assertTrue(url.contains("/download/"));
    }

    @Test
    void buildReportDownloadUrl_withEmptyReportName_returnsUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("");

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("http://"));
    }

    @Test
    void buildReportDownloadUrl_withNullReportName_returnsUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl(null);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("http://"));
    }

    @Test
    void buildReportDownloadUrl_withSpecialCharacters_includesInUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("report-2024_03.pdf");

        // Assert
        assertTrue(url.contains("report-2024_03.pdf"));
    }

    @Test
    void buildReportDownloadUrl_withSpaces_includesInUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("monthly report.pdf");

        // Assert
        assertTrue(url.contains("monthly report.pdf"));
    }

    @Test
    void buildReportDownloadUrl_withDifferentExtension_includesInUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.csv");

        // Assert
        assertTrue(url.contains("report.csv"));
    }

    // ========== getSystemInfo Tests ==========

    @Test
    void getSystemInfo_returnsNonNullMap() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info);
    }

    @Test
    void getSystemInfo_includesReportPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("reportPath"));
        assertNotNull(info.get("reportPath"));
    }

    @Test
    void getSystemInfo_reportPathContainsLegacyPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String reportPath = (String) info.get("reportPath");
        assertTrue(reportPath.contains("/var/legacy/reports/"));
    }

    @Test
    void getSystemInfo_includesBackupPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("backupPath"));
        assertNotNull(info.get("backupPath"));
    }

    @Test
    void getSystemInfo_backupPathContainsWindowsPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String backupPath = (String) info.get("backupPath");
        assertTrue(backupPath.contains("C:\\ResortBackups\\nightly\\"));
    }

    @Test
    void getSystemInfo_includesServerPort() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("serverPort"));
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    void getSystemInfo_includesGeneratedAtTimestamp() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("generatedAt"));
        assertNotNull(info.get("generatedAt"));
    }

    @Test
    void getSystemInfo_timestampIsNotEmpty() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String timestamp = (String) info.get("generatedAt");
        assertFalse(timestamp.isEmpty());
    }

    @Test
    void getSystemInfo_timestampContainsDate() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String timestamp = (String) info.get("generatedAt");
        assertTrue(timestamp.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
    }

    @Test
    void getSystemInfo_timestampContainsTime() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String timestamp = (String) info.get("generatedAt");
        assertTrue(timestamp.matches(".*\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    void getSystemInfo_calledMultipleTimes_returnsDifferentTimestamps() throws InterruptedException {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Thread.sleep(1100); // Wait for at least 1 second
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert
        String timestamp1 = (String) info1.get("generatedAt");
        String timestamp2 = (String) info2.get("generatedAt");
        assertNotEquals(timestamp1, timestamp2);
    }

    @Test
    void getSystemInfo_allFieldsAreNonNull() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("reportPath"));
        assertNotNull(info.get("backupPath"));
        assertNotNull(info.get("serverPort"));
        assertNotNull(info.get("generatedAt"));
    }

    @Test
    void getSystemInfo_serverPortIsInteger() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        Object port = info.get("serverPort");
        assertTrue(port instanceof Integer);
    }

    @Test
    void getSystemInfo_serverPortIsPositive() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        Integer port = (Integer) info.get("serverPort");
        assertTrue(port > 0);
    }

    @Test
    void getSystemInfo_containsExactlyFourKeys() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(4, info.size());
    }
}
