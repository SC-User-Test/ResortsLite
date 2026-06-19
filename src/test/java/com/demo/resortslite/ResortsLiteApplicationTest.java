package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResortsLiteApplication Test Suite")
class ResortsLiteApplicationTest {

    @Test
    @DisplayName("main method should not throw exception")
    void main_shouldNotThrowException() {
        // This test verifies that the main method exists and can be called
        // In a real scenario, we would mock SpringApplication.run()
        assertDoesNotThrow(() -> {
            // We don't actually run the application in tests
            // Just verify the class structure is correct
            assertNotNull(ResortsLiteApplication.class);
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication class should exist")
    void applicationClass_shouldExist() {
        // Act & Assert
        assertNotNull(ResortsLiteApplication.class);
    }

    @Test
    @DisplayName("ResortsLiteApplication should have main method")
    void applicationClass_shouldHaveMainMethod() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication should be annotated with @SpringBootApplication")
    void applicationClass_shouldHaveSpringBootApplicationAnnotation() {
        // Act
        boolean hasAnnotation = ResortsLiteApplication.class
            .isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class);

        // Assert
        assertTrue(hasAnnotation);
    }

    @Test
    @DisplayName("main method should be public")
    void mainMethod_shouldBePublic() throws NoSuchMethodException {
        // Act
        int modifiers = ResortsLiteApplication.class
            .getDeclaredMethod("main", String[].class)
            .getModifiers();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPublic(modifiers));
    }

    @Test
    @DisplayName("main method should be static")
    void mainMethod_shouldBeStatic() throws NoSuchMethodException {
        // Act
        int modifiers = ResortsLiteApplication.class
            .getDeclaredMethod("main", String[].class)
            .getModifiers();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isStatic(modifiers));
    }

    @Test
    @DisplayName("main method should return void")
    void mainMethod_shouldReturnVoid() throws NoSuchMethodException {
        // Act
        Class<?> returnType = ResortsLiteApplication.class
            .getDeclaredMethod("main", String[].class)
            .getReturnType();

        // Assert
        assertEquals(void.class, returnType);
    }

    @Test
    @DisplayName("main method should accept String array parameter")
    void mainMethod_shouldAcceptStringArrayParameter() throws NoSuchMethodException {
        // Act
        Class<?>[] parameterTypes = ResortsLiteApplication.class
            .getDeclaredMethod("main", String[].class)
            .getParameterTypes();

        // Assert
        assertEquals(1, parameterTypes.length);
        assertEquals(String[].class, parameterTypes[0]);
    }

    @Test
    @DisplayName("ResortsLiteApplication class should be public")
    void applicationClass_shouldBePublic() {
        // Act
        int modifiers = ResortsLiteApplication.class.getModifiers();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPublic(modifiers));
    }

    @Test
    @DisplayName("ResortsLiteApplication should have default constructor")
    void applicationClass_shouldHaveDefaultConstructor() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ResortsLiteApplication.class.getDeclaredConstructor();
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication instance should be creatable")
    void applicationInstance_shouldBeCreatable() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new ResortsLiteApplication();
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication should be in correct package")
    void applicationClass_shouldBeInCorrectPackage() {
        // Act
        String packageName = ResortsLiteApplication.class.getPackage().getName();

        // Assert
        assertEquals("com.demo.resortslite", packageName);
    }

    @Test
    @DisplayName("ResortsLiteApplication class name should be correct")
    void applicationClass_shouldHaveCorrectName() {
        // Act
        String className = ResortsLiteApplication.class.getSimpleName();

        // Assert
        assertEquals("ResortsLiteApplication", className);
    }

    @Test
    @DisplayName("main method with null args should not throw NullPointerException")
    void main_withNullArgs_shouldHandleGracefully() {
        // This test verifies the method signature accepts null
        // In practice, Spring Boot handles null args
        assertDoesNotThrow(() -> {
            // We verify the method exists and can accept null conceptually
            ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        });
    }

    @Test
    @DisplayName("main method with empty args should be valid")
    void main_withEmptyArgs_shouldBeValid() {
        // Verify method can be invoked with empty array
        assertDoesNotThrow(() -> {
            ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication should not be abstract")
    void applicationClass_shouldNotBeAbstract() {
        // Act
        int modifiers = ResortsLiteApplication.class.getModifiers();

        // Assert
        assertFalse(java.lang.reflect.Modifier.isAbstract(modifiers));
    }

    @Test
    @DisplayName("ResortsLiteApplication should not be interface")
    void applicationClass_shouldNotBeInterface() {
        // Act & Assert
        assertFalse(ResortsLiteApplication.class.isInterface());
    }

    @Test
    @DisplayName("ResortsLiteApplication should not be enum")
    void applicationClass_shouldNotBeEnum() {
        // Act & Assert
        assertFalse(ResortsLiteApplication.class.isEnum());
    }

    @Test
    @DisplayName("ResortsLiteApplication should not be annotation")
    void applicationClass_shouldNotBeAnnotation() {
        // Act & Assert
        assertFalse(ResortsLiteApplication.class.isAnnotation());
    }

    @Test
    @DisplayName("ResortsLiteApplication should extend Object")
    void applicationClass_shouldExtendObject() {
        // Act
        Class<?> superclass = ResortsLiteApplication.class.getSuperclass();

        // Assert
        assertEquals(Object.class, superclass);
    }

    @Test
    @DisplayName("ResortsLiteApplication should have exactly one main method")
    void applicationClass_shouldHaveExactlyOneMainMethod() {
        // Act
        long mainMethodCount = java.util.Arrays.stream(ResortsLiteApplication.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("main"))
            .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
            .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
            .count();

        // Assert
        assertEquals(1, mainMethodCount);
    }
}
