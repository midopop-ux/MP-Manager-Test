package io.github.abdurazaaqmohammed.feature.apk;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CloneProjectStoreTest {

    private static CloneProjectStore.Snapshot snapshot(String name, String pkg, long size,
                                                       int dex, String sha) {
        CloneProjectStore.Snapshot s = new CloneProjectStore.Snapshot();
        s.name = name;
        s.newPackage = pkg;
        s.size = size;
        s.dexCount = dex;
        s.sha256 = sha;
        return s;
    }

    @Test
    public void compareReportsIdenticalHashesAndDifferentPackages() {
        CloneProjectStore.Snapshot a = snapshot("A", "com.example.a", 100, 2, "abc");
        CloneProjectStore.Snapshot b = snapshot("B", "com.example.b", 120, 3, "def");
        String report = CloneProjectStore.compare(a, b);
        assertTrue(report.contains("A: A"));
        assertTrue(report.contains("B: B"));
        assertTrue(report.contains("(مختلفة)"));
        assertTrue(report.contains("SHA-256: مختلف"));
        assertTrue(report.contains("abc"));
        assertTrue(report.contains("def"));
    }

    @Test
    public void compareMarksIdenticalOutputs() {
        CloneProjectStore.Snapshot a = snapshot("A", "com.example", 100, 2, "abc");
        CloneProjectStore.Snapshot b = snapshot("B", "com.example", 100, 2, "abc");
        String report = CloneProjectStore.compare(a, b);
        assertTrue(report.contains("(متطابقة)"));
        assertTrue(report.contains("SHA-256: متطابق"));
    }
}
