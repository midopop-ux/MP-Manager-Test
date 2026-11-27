package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApkCloneEngineTest {

    @Test
    public void acceptsValidPackageNames() {
        assertTrue(ApkCloneEngine.isValidPackageName("com.example.clone"));
        assertTrue(ApkCloneEngine.isValidPackageName("a.b"));
        assertTrue(ApkCloneEngine.isValidPackageName("com.example.app_clone"));
    }

    @Test
    public void riskLabelsReflectSeverity() {
        ApkCloneEngine.Report report = new ApkCloneEngine.Report();
        assertEquals("منخفض", report.riskLabel());
        report.riskLevel = 1;
        assertEquals("متوسط", report.riskLabel());
        report.riskLevel = 2;
        assertEquals("مرتفع / مانع", report.riskLabel());
    }

    @Test
    public void rejectsInvalidPackageNames() {
        assertFalse(ApkCloneEngine.isValidPackageName(null));
        assertFalse(ApkCloneEngine.isValidPackageName(""));
        assertFalse(ApkCloneEngine.isValidPackageName("single"));
        assertFalse(ApkCloneEngine.isValidPackageName("com..clone"));
        assertFalse(ApkCloneEngine.isValidPackageName("com.1clone"));
        assertFalse(ApkCloneEngine.isValidPackageName("com.example.$bad"));
        assertTrue(ApkCloneEngine.isValidPackageName(" com.example.clone "));
    }
}
