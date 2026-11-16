package com.cronova.scheduler.service;

import com.cronova.scheduler.service.impl.ScriptServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptServiceImplSecurityTest {

    private final ScriptServiceImpl service = new ScriptServiceImpl();

    @Test
    void rejectsParentTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "normalizeZipEntryName", "root/../../etc/passwd"));
    }

    @Test
    void rejectsAbsolutePaths() {
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "normalizeZipEntryName", "/etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "normalizeZipEntryName", "C:\\temp\\file.py"));
    }

    @Test
    void acceptsNormalRelativePath() {
        String normalized = ReflectionTestUtils.invokeMethod(
                service, "normalizeZipEntryName", "project\\src\\main.py");

        assertEquals("project/src/main.py", normalized);
    }
}
