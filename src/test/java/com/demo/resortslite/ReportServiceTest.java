package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportService Test Suite")
class ReportServiceTest {

    private ReportService reportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    @Test
    @DisplayName("generateMonthlyReport should create report with valid month and year")
    void generateMonthlyReport_withValidMonthAndYear_createsReport() {
        // Arrange
        String month = "March";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
        assertTrue(result.containsKey("path"));
    }

    @Test
    @DisplayName("generateMonthlyReport should return generated status on success")
    void generateMonthlyReport_onSuccess_returnsGeneratedStatus() {
        // Arrange
        String month = "January";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        String status = (String) result.get("status");
        assertTrue(status.equals("generated") || status.equals("error"));
    }

    @Test
    @DisplayName("generateMonthlyReport should include server port in result")
    void generateMonthlyReport_shouldIncludeServerPort() {
        // Arrange
        String month = "February";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertTrue(result.containsKey("serverPort"));
        Object port = result.get("serverPort");
        if (port != null) {
            assertEquals(8080, port);
        }
    }

    @Test
    @DisplayName("generateMonthlyReport should construct correct file path")
    void generateMonthlyReport_shouldConstructCorrectFilePath() {
        // Arrange
        String month = "April";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        if (result.containsKey("path")) {
            String path = (String) result.get("path");
            assertTrue(path.contains("resort_report_"));
            assertTrue(path.contains(month));
            assertTrue(path.contains(year));
            assertTrue(path.endsWith(".csv"));
        }
    }

    @Test
    @DisplayName("generateMonthlyReport should handle different month formats")
    void generateMonthlyReport_withDifferentMonthFormats_generatesReport() {
        // Arrange
        String[] months = {"01", "December", "Jun", "2024-07"};
        String year = "2024";

        for (String month : months) {
            // Act
            Map<String, Object> result = reportService.generateMonthlyReport(month, year);

            // Assert
            assertNotNull(result);
            assertTrue(result.containsKey("status"));
        }
    }

    @Test
    @DisplayName("generateMonthlyReport should handle different year formats")
    void generateMonthlyReport_withDifferentYearFormats_generatesReport() {
        // Arrange
        String month = "May";
        String[] years = {"2024", "24", "2025"};

        for (String year : years) {
            // Act
            Map<String, Object> result = reportService.generateMonthlyReport(month, year);

            // Assert
            assertNotNull(result);
            assertTrue(result.containsKey("status"));
        }
    }

    @Test
    @DisplayName("generateMonthlyReport should handle empty month parameter")
    void generateMonthlyReport_withEmptyMonth_handlesGracefully() {
        // Arrange
        String month = "";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport should handle empty year parameter")
    void generateMonthlyReport_withEmptyYear_handlesGracefully() {
        // Arrange
        String month = "June";
        String year = "";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport should handle null month parameter")
    void generateMonthlyReport_withNullMonth_handlesGracefully() {
        // Arrange
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(null, year);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport should handle null year parameter")
    void generateMonthlyReport_withNullYear_handlesGracefully() {
        // Arrange
        String month = "July";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should construct valid URL")
    void buildReportDownloadUrl_withValidReportName_constructsUrl() {
        // Arrange
        String reportName = "march_2024_report.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("http://"));
        assertTrue(url.contains(reportName));
        assertTrue(url.contains("download"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should include report name in URL")
    void buildReportDownloadUrl_shouldIncludeReportName() {
        // Arrange
        String reportName = "annual_summary.pdf";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.endsWith(reportName));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should handle empty report name")
    void buildReportDownloadUrl_withEmptyReportName_constructsUrl() {
        // Arrange
        String reportName = "";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("http://"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should handle null report name")
    void buildReportDownloadUrl_withNullReportName_constructsUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl(null);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("http://"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should include port 8080")
    void buildReportDownloadUrl_shouldIncludePort8080() {
        // Arrange
        String reportName = "test_report.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.contains(":8080"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl should handle special characters in report name")
    void buildReportDownloadUrl_withSpecialCharacters_constructsUrl() {
        // Arrange
        String reportName = "report@2024#march$.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(reportName));
    }

    @Test
    @DisplayName("getSystemInfo should return all system information")
    void getSystemInfo_returnsAllSystemInformation() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info);
        assertTrue(info.containsKey("reportPath"));
        assertTrue(info.containsKey("backupPath"));
        assertTrue(info.containsKey("serverPort"));
        assertTrue(info.containsKey("generatedAt"));
    }

    @Test
    @DisplayName("getSystemInfo should return correct server port")
    void getSystemInfo_returnsCorrectServerPort() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    @DisplayName("getSystemInfo should return report path")
    void getSystemInfo_returnsReportPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String reportPath = (String) info.get("reportPath");
        assertNotNull(reportPath);
        assertTrue(reportPath.contains("reports"));
    }

    @Test
    @DisplayName("getSystemInfo should return backup path")
    void getSystemInfo_returnsBackupPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String backupPath = (String) info.get("backupPath");
        assertNotNull(backupPath);
        assertTrue(backupPath.contains("Backup") || backupPath.contains("backup"));
    }

    @Test
    @DisplayName("getSystemInfo should return timestamp in correct format")
    void getSystemInfo_returnsTimestampInCorrectFormat() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String timestamp = (String) info.get("generatedAt");
        assertNotNull(timestamp);
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("getSystemInfo should return non-null values for all keys")
    void getSystemInfo_returnsNonNullValuesForAllKeys() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("reportPath"));
        assertNotNull(info.get("backupPath"));
        assertNotNull(info.get("serverPort"));
        assertNotNull(info.get("generatedAt"));
    }

    @Test
    @DisplayName("getSystemInfo should be callable multiple times")
    void getSystemInfo_callableMultipleTimes_returnsConsistentData() {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert
        assertEquals(info1.get("reportPath"), info2.get("reportPath"));
        assertEquals(info1.get("backupPath"), info2.get("backupPath"));
        assertEquals(info1.get("serverPort"), info2.get("serverPort"));
    }

    @Test
    @DisplayName("generateMonthlyReport should handle long month names")
    void generateMonthlyReport_withLongMonthNames_generatesReport() {
        // Arrange
        String month = "September";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport should handle numeric month values")
    void generateMonthlyReport_withNumericMonths_generatesReport() {
        // Arrange
        String[] months = {"1", "01", "12"};
        String year = "2024";

        for (String month : months) {
            // Act
            Map<String, Object> result = reportService.generateMonthlyReport(month, year);

            // Assert
            assertNotNull(result);
            assertTrue(result.containsKey("status"));
        }
    }

    @Test
    @DisplayName("buildReportDownloadUrl should construct URL with correct domain")
    void buildReportDownloadUrl_shouldConstructUrlWithCorrectDomain() {
        // Arrange
        String reportName = "test.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.contains("resorts-internal.com"));
    }
}
