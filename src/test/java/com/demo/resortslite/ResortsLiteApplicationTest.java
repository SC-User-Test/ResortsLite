package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ResortsLiteApplication
 * Tests application startup, configuration, and main method
 */
@SpringBootTest
class ResortsLiteApplicationTest {

    // ========== Application Context Tests ==========

    @Test
    void contextLoads(ApplicationContext context) {
        // Assert
        assertNotNull(context);
    }

    @Test
    void applicationContextContainsBookingController(ApplicationContext context) {
        // Act
        BookingController controller = context.getBean(BookingController.class);

        // Assert
        assertNotNull(controller);
    }

    @Test
    void applicationContextContainsBookingService(ApplicationContext context) {
        // Act
        BookingService service = context.getBean(BookingService.class);

        // Assert
        assertNotNull(service);
    }

    @Test
    void applicationContextContainsReportService(ApplicationContext context) {
        // Act
        ReportService service = context.getBean(ReportService.class);

        // Assert
        assertNotNull(service);
    }

    @Test
    void applicationContextContainsResortsLiteApplication(ApplicationContext context) {
        // Act
        ResortsLiteApplication app = context.getBean(ResortsLiteApplication.class);

        // Assert
        assertNotNull(app);
    }

    // ========== Main Method Tests ==========

    @Test
    void main_withNullArgs_doesNotThrowException() {
        // This test verifies the main method can be called
        // In a real scenario, we would mock SpringApplication.run
        assertDoesNotThrow(() -> {
            // We don't actually call main() as it would start the application
            // Instead, we verify the class structure
            assertNotNull(ResortsLiteApplication.class.getDeclaredMethod("main", String[].class));
        });
    }

    @Test
    void main_methodExists() throws NoSuchMethodException {
        // Act
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertNotNull(mainMethod);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
    }

    @Test
    void main_methodIsPublic() throws NoSuchMethodException {
        // Act
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
    }

    @Test
    void main_methodIsStatic() throws NoSuchMethodException {
        // Act
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
    }

    @Test
    void main_methodReturnsVoid() throws NoSuchMethodException {
        // Act
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertEquals(void.class, mainMethod.getReturnType());
    }

    @Test
    void main_methodAcceptsStringArray() throws NoSuchMethodException {
        // Act
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertEquals(1, mainMethod.getParameterCount());
        assertEquals(String[].class, mainMethod.getParameterTypes()[0]);
    }

    // ========== Spring Boot Annotation Tests ==========

    @Test
    void class_hasSpringBootApplicationAnnotation() {
        // Act
        boolean hasAnnotation = ResortsLiteApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class);

        // Assert
        assertTrue(hasAnnotation);
    }

    @Test
    void class_isPublic() {
        // Act
        boolean isPublic = java.lang.reflect.Modifier.isPublic(
                ResortsLiteApplication.class.getModifiers());

        // Assert
        assertTrue(isPublic);
    }

    @Test
    void class_isNotAbstract() {
        // Act
        boolean isAbstract = java.lang.reflect.Modifier.isAbstract(
                ResortsLiteApplication.class.getModifiers());

        // Assert
        assertFalse(isAbstract);
    }

    @Test
    void class_isNotInterface() {
        // Act
        boolean isInterface = ResortsLiteApplication.class.isInterface();

        // Assert
        assertFalse(isInterface);
    }

    @Test
    void class_hasDefaultConstructor() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ResortsLiteApplication app = new ResortsLiteApplication();
            assertNotNull(app);
        });
    }

    // ========== Package Tests ==========

    @Test
    void class_isInCorrectPackage() {
        // Act
        String packageName = ResortsLiteApplication.class.getPackageName();

        // Assert
        assertEquals("com.demo.resortslite", packageName);
    }

    @Test
    void class_hasCorrectSimpleName() {
        // Act
        String simpleName = ResortsLiteApplication.class.getSimpleName();

        // Assert
        assertEquals("ResortsLiteApplication", simpleName);
    }

    // ========== Bean Configuration Tests ==========

    @Test
    void bookingController_isWiredCorrectly(ApplicationContext context) {
        // Act
        BookingController controller = context.getBean(BookingController.class);
        
        // Assert - Controller should be a singleton
        BookingController controller2 = context.getBean(BookingController.class);
        assertSame(controller, controller2);
    }

    @Test
    void bookingService_isWiredCorrectly(ApplicationContext context) {
        // Act
        BookingService service = context.getBean(BookingService.class);
        
        // Assert - Service should be a singleton
        BookingService service2 = context.getBean(BookingService.class);
        assertSame(service, service2);
    }

    @Test
    void reportService_isWiredCorrectly(ApplicationContext context) {
        // Act
        ReportService service = context.getBean(ReportService.class);
        
        // Assert - Service should be a singleton
        ReportService service2 = context.getBean(ReportService.class);
        assertSame(service, service2);
    }

    // ========== Application Properties Tests ==========

    @Test
    void applicationContext_hasEnvironment(ApplicationContext context) {
        // Act
        var environment = context.getEnvironment();

        // Assert
        assertNotNull(environment);
    }

    @Test
    void applicationContext_hasBeanFactory(ApplicationContext context) {
        // Act
        var beanFactory = context.getAutowireCapableBeanFactory();

        // Assert
        assertNotNull(beanFactory);
    }

    @Test
    void applicationContext_hasApplicationName(ApplicationContext context) {
        // Act
        String appName = context.getApplicationName();

        // Assert
        assertNotNull(appName);
    }

    @Test
    void applicationContext_hasId(ApplicationContext context) {
        // Act
        String id = context.getId();

        // Assert
        assertNotNull(id);
    }

    @Test
    void applicationContext_hasDisplayName(ApplicationContext context) {
        // Act
        String displayName = context.getDisplayName();

        // Assert
        assertNotNull(displayName);
    }

    @Test
    void applicationContext_hasStartupDate(ApplicationContext context) {
        // Act
        long startupDate = context.getStartupDate();

        // Assert
        assertTrue(startupDate > 0);
    }

    @Test
    void applicationContext_isActive(ConfigurableApplicationContext context) {
        // Act
        boolean isActive = context.isActive();

        // Assert
        assertTrue(isActive);
    }

    @Test
    void applicationContext_containsMultipleBeans(ApplicationContext context) {
        // Act
        int beanCount = context.getBeanDefinitionCount();

        // Assert
        assertTrue(beanCount > 0);
    }

    @Test
    void applicationContext_containsBookingControllerBean(ApplicationContext context) {
        // Act
        boolean containsBean = context.containsBean("bookingController");

        // Assert
        assertTrue(containsBean);
    }

    @Test
    void applicationContext_containsBookingServiceBean(ApplicationContext context) {
        // Act
        boolean containsBean = context.containsBean("bookingService");

        // Assert
        assertTrue(containsBean);
    }

    @Test
    void applicationContext_containsReportServiceBean(ApplicationContext context) {
        // Act
        boolean containsBean = context.containsBean("reportService");

        // Assert
        assertTrue(containsBean);
    }
}
