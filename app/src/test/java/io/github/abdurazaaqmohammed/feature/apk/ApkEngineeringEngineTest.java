package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ApkEngineeringEngineTest {

    @Test
    public void toastScannerFindsMessageAndShowBlock() {
        String smali = ".class public Lcom/example/Main;\n"
                + ".method public show()V\n"
                + "    .locals 2\n"
                + "    const-string v0, \"Hello toast\"\n"
                + "    const/4 v1, 0x1\n"
                + "    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n"
                + "    move-result-object v0\n"
                + "    invoke-virtual {v0}, Landroid/widget/Toast;->show()V\n"
                + "    return-void\n"
                + ".end method\n";
        List<ToastRemovalEngine.Hit> hits = ToastRemovalEngine.parseSmaliForTest(smali);
        assertEquals(1, hits.size());
        assertEquals("Hello toast", hits.get(0).message);
        assertTrue(hits.get(0).method.contains("show"));
        assertTrue(hits.get(0).id.contains("Lcom/example/Main;"));
    }

    @Test
    public void toastScannerFindsMessageStoredInParameterRegister() {
        String smali = ".method public show(Ljava/lang/String;)V\n"
                + "    .locals 2\n"
                + "    const-string p0, \"Parameter toast\"\n"
                + "    const/4 v0, 0x0\n"
                + "    invoke-static {p1, p0, v0}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n"
                + "    move-result-object v1\n"
                + "    invoke-virtual {v1}, Landroid/widget/Toast;->show()V\n"
                + "    return-void\n"
                + ".end method\n";
        List<ToastRemovalEngine.Hit> hits = ToastRemovalEngine.parseSmaliForTest(smali);
        assertEquals(1, hits.size());
        assertEquals("Parameter toast", hits.get(0).message);
    }

    @Test
    public void injectionScannerRecognizesHiddenStringsInsideDexCode() {
        assertTrue(InjectionRemovalEngine.isSuspiciousTextForTest("LSPosed bridge"));
        assertTrue(InjectionRemovalEngine.isSuspiciousTextForTest("frida-gadget"));
        assertFalse(InjectionRemovalEngine.isSuspiciousTextForTest("com.example.regular.App"));
    }

    @Test
    public void toastScannerReportsDirectShowWithoutInventingMessage() {
        String smali = ".method public hide()V\n"
                + "    .locals 1\n"
                + "    invoke-virtual {p0}, Landroid/widget/Toast;->show()V\n"
                + "    return-void\n"
                + ".end method\n";
        List<ToastRemovalEngine.Hit> hits = ToastRemovalEngine.parseSmaliForTest(smali);
        assertEquals(1, hits.size());
        assertEquals("Toast.show()", hits.get(0).message);
    }

    @Test
    public void injectionScannerRecognizesFrameworkNamesAndFiles() {
        assertTrue(InjectionRemovalEngine.isSuspiciousNameForTest("de.robv.android.xposed.IXposedHookLoadPackage"));
        assertTrue(InjectionRemovalEngine.isSuspiciousNameForTest("org.lsposed.manager.Main"));
        assertFalse(InjectionRemovalEngine.isSuspiciousNameForTest("com.example.app.MainActivity"));
        assertTrue(InjectionRemovalEngine.isSuspiciousFileForTest("assets/lspatch/loader.dex"));
        assertTrue(InjectionRemovalEngine.isSuspiciousFileForTest("lib/arm64-v8a/libfrida-gadget.so"));
        assertFalse(InjectionRemovalEngine.isSuspiciousFileForTest("res/drawable/icon.png"));
    }

    @Test
    public void injectionRemovalDeletesOnlyNamedManifestComponents() {
        String manifest = "<manifest><application>"
                + "<activity android:name=\"com.example.app.Main\" android:exported=\"true\"/>"
                + "<service android:name=\"com.example.xposed.HookService\" android:exported=\"true\">"
                + "<intent-filter/></service>"
                + "<meta-data android:name=\"xposedmodule\" android:value=\"true\"/>"
                + "</application></manifest>";
        Set<String> names = new LinkedHashSet<>(Arrays.asList(
                "com.example.xposed.HookService", "xposedmodule"));
        String edited = InjectionRemovalEngine.removeManifestEntriesForTest(manifest, names);
        assertFalse(edited.contains("HookService"));
        assertFalse(edited.contains("xposedmodule"));
        assertTrue(edited.contains("com.example.app.Main"));
    }

    @Test
    public void backupRestoreWritesAValidCopyWithoutOverwritingOriginal() throws Exception {
        File dir = Files.createTempDirectory("mp-restore").toFile();
        File original = new File(dir, "sample.apk");
        File backup = new File(dir, "sample_before.apk");
        try (FileOutputStream o = new FileOutputStream(original)) {
            o.write("original".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream o = new FileOutputStream(backup)) {
            o.write("backup".getBytes(StandardCharsets.UTF_8));
        }
        File restored = InjectionRemovalEngine.restore(backup, original);
        assertTrue(restored.isFile());
        assertEquals("backup", new String(Files.readAllBytes(restored.toPath()), StandardCharsets.UTF_8));
        assertEquals("original", new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void clonePackageNameValidationRejectsMalformedIdentifiers() {
        assertTrue(ApkCloneEngine.isValidPackageName("com.example.clone"));
        assertTrue(ApkCloneEngine.isValidPackageName("io.github.demo.app2"));
        assertFalse(ApkCloneEngine.isValidPackageName("singleword"));
        assertFalse(ApkCloneEngine.isValidPackageName("com..example"));
        assertFalse(ApkCloneEngine.isValidPackageName("com.example.1bad"));
    }
}
