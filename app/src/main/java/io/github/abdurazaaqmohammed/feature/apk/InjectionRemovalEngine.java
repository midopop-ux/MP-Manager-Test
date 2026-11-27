package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.reandroid.apk.APKLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

/**
 * Scans an APK for injected classes, manifest components, loader assets and
 * native hook libraries, reports dependencies, and removes only selected
 * findings. A backup is always written before the APK is rebuilt.
 */
public final class InjectionRemovalEngine {

    public enum Kind { DEX_CLASS, MANIFEST_COMPONENT, FILE }

    public static final class Finding {
        public String id = "";
        public Kind kind = Kind.FILE;
        public String name = "";
        public String location = "";
        public String detail = "";
        public boolean removable = true;
        public String reason = "";
        public boolean selected;
        public String entry = "";
        public String descriptor = "";
        public String tag = "";
        public final List<String> dependencies = new ArrayList<>();

        public String label() {
            return name + "\n" + location + (detail.isEmpty() ? "" : " · " + detail)
                    + (removable ? "" : "\n⛔ " + reason);
        }
    }

    public static final class ScanReport {
        public final List<Finding> findings = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int entries;
        public int dexCount;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("الملفات: ").append(entries).append(" · DEX: ").append(dexCount)
                    .append(" · عناصر مشتبهة: ").append(findings.size()).append('\n');
            for (String w : warnings) sb.append("• ").append(w).append('\n');
            for (String e : errors) sb.append("✕ ").append(e).append('\n');
            return sb.toString();
        }
    }

    public static final class PatchReport {
        public String output = "";
        public String backup = "";
        public int before;
        public int removed;
        public int remaining;
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("قبل: ").append(before).append(" · أزيل: ").append(removed)
                    .append(" · بقي: ").append(remaining).append('\n');
            if (!output.isEmpty()) sb.append("الناتج: ").append(output).append('\n');
            if (!backup.isEmpty()) sb.append("النسخة الاحتياطية: ").append(backup).append('\n');
            for (String w : warnings) sb.append("• ").append(w).append('\n');
            for (String e : errors) sb.append("✕ ").append(e).append('\n');
            return sb.toString();
        }
    }

    private static final String[] SUSPICIOUS = {
            "xposed", "lsposed", "lspatch", "lspd", "substrate", "frida",
            "zygisk", "riru", "magisk", "edxposed", "virtualapp", "sandhook",
            "pine", "yhx", "epic", "inlinehook", "hookframework"
    };
    private static final String[] FRAMEWORK_PREFIXES = {
            "Lde/robv/android/xposed/", "Lorg/lsposed/", "Lio/github/lsposed/",
            "Lcom/saurik/substrate/", "Lcom/elderdrivers/", "Ltop/niunaijun/",
            "Lcom/reveny/", "Lorg/luckypray/", "Lcom/highcapable/", "Lio/github/libxposed/"
    };
    private static final Pattern COMPONENT = Pattern.compile(
            "<(activity|activity-alias|service|receiver|provider)\\b([^>]*)>",
            Pattern.DOTALL);
    private static final Pattern META = Pattern.compile(
            "<meta-data\\b([^>]*)/?>", Pattern.DOTALL);
    private static final Pattern NAME_ATTR = Pattern.compile(
            "android:name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern APPLICATION = Pattern.compile(
            "<application\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern PERMISSION = Pattern.compile(
            "<(uses-permission|uses-permission-sdk-23|permission)\\b([^>]*)/?>", Pattern.DOTALL);

    private InjectionRemovalEngine() {
    }

    public static ScanReport scan(Context context, File apk, APKLogger logger) throws Exception {
        ScanReport report = new ScanReport();
        String manifest = readManifest(apk);
        Set<String> selectedClasses = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                report.entries++;
                String name = e.getName();
                if (name.startsWith("classes") && name.endsWith(".dex")) report.dexCount++;
                if (isSuspiciousFile(name)) {
                    Finding f = new Finding();
                    f.kind = Kind.FILE;
                    f.name = name;
                    f.location = "أصل/مورد داخل APK";
                    f.detail = readable(name);
                    f.id = "file:" + name;
                    f.entry = name;
                    report.findings.add(f);
                }
            }
        }
        if (manifest != null && !manifest.isEmpty()) {
            scanManifest(manifest, report);
        } else {
            report.warnings.add("تعذر فك AndroidManifest.xml؛ لم تُفحص مكونات الحقن في البيان.");
        }
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entry);
            if (dexEntry == null) continue;
            for (ClassDef classDef : dexEntry.getDexFile().getClasses()) {
                if (!isSuspiciousClass(classDef)) continue;
                selectedClasses.add(classDef.getType());
                Finding f = new Finding();
                f.kind = Kind.DEX_CLASS;
                f.name = FastDexPatch.descriptorToClassName(classDef.getType());
                f.location = entry;
                f.entry = entry;
                f.descriptor = classDef.getType();
                f.detail = "فئة تشير إلى إطار حقن/تحميل";
                f.id = "dex:" + entry + ":" + classDef.getType();
                report.findings.add(f);
            }
        }
        markDependencies(container, report, selectedClasses);
        if (report.findings.isEmpty()) report.warnings.add("لم تُعثر عناصر حقن معروفة في هذا الملف.");
        return report;
    }

    public static PatchReport remove(Context context, File apk, List<Finding> selected,
                                     boolean force, APKLogger logger) throws Exception {
        PatchReport report = new PatchReport();
        ScanReport scan = scan(context, apk, logger);
        report.before = scan.findings.size();
        Set<String> chosen = new LinkedHashSet<>();
        if (selected != null) for (Finding f : selected) if (f != null) chosen.add(f.id);
        if (chosen.isEmpty()) {
            report.errors.add("لم يتم تحديد أي عنصر.");
            return report;
        }
        List<Finding> chosenFindings = new ArrayList<>();
        for (Finding f : scan.findings) if (chosen.contains(f.id)) chosenFindings.add(f);
        if (chosenFindings.isEmpty()) {
            report.errors.add("العناصر المحددة لم تعد موجودة.");
            return report;
        }
        String manifestForDeps = readManifest(apk);
        for (Finding f : chosenFindings) {
            if (!f.removable && !force) {
                report.errors.add("العنصر " + f.name + " مرتبط بكود محتفظ به: " + f.reason);
            }
            if (f.kind == Kind.DEX_CLASS && manifestForDeps != null
                    && manifestForDeps.contains(f.name)) {
                boolean paired = false;
                for (Finding other : chosenFindings) {
                    if (other.kind == Kind.MANIFEST_COMPONENT && other.name.equals(f.name)) {
                        paired = true;
                        break;
                    }
                }
                if (!paired && !force) {
                    report.errors.add("الفئة " + f.name
                            + " ما زالت مُعلنة في AndroidManifest؛ اختر مكوّن البيان معها أو أبقِ الفئة.");
                }
            }
        }
        if (!report.errors.isEmpty()) return report;
        File work = tempDir(context, "injection_remove");
        try {
            Map<String, File> replacements = new LinkedHashMap<>();
            Set<String> skip = new LinkedHashSet<>();
            Map<String, Set<String>> removeClasses = new LinkedHashMap<>();
            Set<String> removeManifestNames = new LinkedHashSet<>();
            for (Finding f : chosenFindings) {
                if (f.kind == Kind.FILE) skip.add(f.entry);
                else if (f.kind == Kind.MANIFEST_COMPONENT) removeManifestNames.add(f.name);
                else if (f.kind == Kind.DEX_CLASS) {
                    removeClasses.computeIfAbsent(f.entry, k -> new LinkedHashSet<>()).add(f.descriptor);
                }
            }
            if (!removeClasses.isEmpty()) {
                MultiDexContainer<? extends DexBackedDexFile> container =
                        DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
                for (Map.Entry<String, Set<String>> e : removeClasses.entrySet()) {
                    MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(e.getKey());
                    if (dexEntry == null) continue;
                    DexPool pool = new DexPool(Opcodes.getDefault());
                    for (ClassDef c : dexEntry.getDexFile().getClasses()) {
                        if (!e.getValue().contains(c.getType())) pool.internClass(c);
                    }
                    MemoryDataStore store = new MemoryDataStore();
                    pool.writeTo(store);
                    File out = new File(work, safe(e.getKey()) + ".dex");
                    write(out, Arrays.copyOf(store.getData(), store.getSize()));
                    replacements.put(e.getKey(), out);
                }
            }
            if (!removeManifestNames.isEmpty()) {
                String manifest = readManifest(apk);
                if (manifest == null) throw new IOException("تعذر قراءة AndroidManifest.xml");
                String edited = removeManifestEntries(manifest, removeManifestNames);
                if (edited.equals(manifest)) {
                    report.warnings.add("لم يتغير البيان؛ قد تكون أسماء المكونات غير مباشرة.");
                } else {
                    File out = new File(work, "AndroidManifest.xml");
                    write(out, new aXMLEncoder().encodeString(edited, context));
                    replacements.put("AndroidManifest.xml", out);
                }
            }
            report.backup = new File(apk.getParentFile(),
                    stripExt(apk.getName()) + "_before_injection_removal.apk").getAbsolutePath();
            copy(apk, new File(report.backup));
            File out = unused(new File(apk.getParentFile(),
                    stripExt(apk.getName()) + "_injection_removed.apk"));
            ApkZipAlignUtil.rebuildApk(apk, out, replacements, null, null, skip);
            report.output = out.getAbsolutePath();
            ScanReport after = scan(context, out, logger);
            report.remaining = after.findings.size();
            report.removed = Math.max(0, report.before - report.remaining);
            if (report.remaining > 0) report.warnings.add("بقيت " + report.remaining
                    + " عناصر مشتبهة؛ قد تكون غير قابلة للحذف بأمان أو جزءًا من التطبيق.");
            if (!out.isFile() || out.length() == 0) report.errors.add("لم يُنتج ملف APK صالح.");
        } finally {
            delete(work);
        }
        return report;
    }

    private static void scanManifest(String manifest, ScanReport report) {
        Matcher cm = COMPONENT.matcher(manifest);
        while (cm.find()) {
            String tag = cm.group(1);
            String body = cm.group(2);
            Matcher nm = NAME_ATTR.matcher(body);
            if (!nm.find()) continue;
            String name = nm.group(1);
            if (!isSuspiciousName(name)) continue;
            Finding f = new Finding();
            f.kind = Kind.MANIFEST_COMPONENT;
            f.name = name;
            f.tag = tag;
            f.location = "AndroidManifest.xml · <" + tag + ">";
            f.detail = "مكون مرتبط بإطار حقن/تحميل";
            f.id = "manifest:" + tag + ":" + name;
            report.findings.add(f);
        }
        Matcher app = APPLICATION.matcher(manifest);
        if (app.find()) {
            Matcher nm = NAME_ATTR.matcher(app.group(1));
            if (nm.find() && isSuspiciousName(nm.group(1))) {
                Finding f = new Finding();
                f.kind = Kind.MANIFEST_COMPONENT;
                f.name = nm.group(1);
                f.tag = "application";
                f.location = "AndroidManifest.xml · <application>";
                f.detail = "فئة Application مرتبطة بإطار حقن";
                f.id = "manifest:application:" + nm.group(1);
                f.removable = false;
                f.reason = "حذف فئة Application تلقائيًا قد يمنع التطبيق من العمل.";
                report.findings.add(f);
            }
        }
        Matcher pm = PERMISSION.matcher(manifest);
        while (pm.find()) {
            Matcher nm = NAME_ATTR.matcher(pm.group(2));
            if (!nm.find() || !isSuspiciousName(nm.group(1))) continue;
            Finding f = new Finding();
            f.kind = Kind.MANIFEST_COMPONENT;
            f.name = nm.group(1);
            f.tag = pm.group(1);
            f.location = "AndroidManifest.xml · <" + pm.group(1) + ">";
            f.detail = "إذن مرتبط بأدوات حقن/روت";
            f.id = "manifest:permission:" + nm.group(1);
            report.findings.add(f);
        }
        Matcher mm = META.matcher(manifest);
        while (mm.find()) {
            String body = mm.group(1);
            Matcher nm = NAME_ATTR.matcher(body);
            if (!nm.find()) continue;
            String name = nm.group(1);
            if (!isSuspiciousName(name)) continue;
            Finding f = new Finding();
            f.kind = Kind.MANIFEST_COMPONENT;
            f.name = name;
            f.tag = "meta-data";
            f.location = "AndroidManifest.xml · <meta-data>";
            f.detail = "بيانات وصفية لإطار حقن";
            f.id = "manifest:meta:" + name;
            report.findings.add(f);
        }
    }

    private static void markDependencies(MultiDexContainer<? extends DexBackedDexFile> container,
                                         ScanReport report, Set<String> selected) throws IOException {
        for (Finding f : report.findings) {
            if (f.kind != Kind.DEX_CLASS || !selected.contains(f.descriptor)) continue;
            for (String entry : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entry);
                if (dexEntry == null) continue;
                for (ClassDef c : dexEntry.getDexFile().getClasses()) {
                    if (c.getType().equals(f.descriptor) || selected.contains(c.getType())) continue;
                    if (references(c, f.descriptor)) {
                        f.dependencies.add(FastDexPatch.descriptorToClassName(c.getType()));
                    }
                }
            }
            if (!f.dependencies.isEmpty()) {
                f.removable = false;
                f.reason = "مرتبط بفئات محتفظ بها: " + f.dependencies.get(0);
            }
        }
    }

    private static boolean references(ClassDef c, String descriptor) {
        for (Method m : c.getDirectMethods()) if (m.getImplementation() != null
                && references(m.getImplementation().getInstructions(), descriptor)) return true;
        for (Method m : c.getVirtualMethods()) if (m.getImplementation() != null
                && references(m.getImplementation().getInstructions(), descriptor)) return true;
        for (Field f : c.getStaticFields()) if (f.getType().equals(descriptor)) return true;
        for (Field f : c.getInstanceFields()) if (f.getType().equals(descriptor)) return true;
        return false;
    }

    private static boolean references(Iterable<? extends Instruction> instructions, String descriptor) {
        for (Instruction i : instructions) {
            if (!(i instanceof ReferenceInstruction ri)) continue;
            Reference r = ri.getReference();
            if (r instanceof TypeReference tr && descriptor.equals(tr.getType())) return true;
            if (r instanceof MethodReference mr && (descriptor.equals(mr.getDefiningClass())
                    || descriptor.equals(mr.getReturnType()))) return true;
            if (r instanceof FieldReference fr && (descriptor.equals(fr.getDefiningClass())
                    || descriptor.equals(fr.getType()))) return true;
        }
        return false;
    }

    private static boolean isSuspiciousClass(ClassDef c) {
        if (suspiciousText(c.getType())) return true;
        if (c.getSuperclass() != null && suspiciousText(c.getSuperclass())) return true;
        for (String iface : c.getInterfaces()) if (suspiciousText(iface)) return true;
        for (Field f : c.getStaticFields()) if (suspiciousText(f.getType())) return true;
        for (Field f : c.getInstanceFields()) if (suspiciousText(f.getType())) return true;
        for (Method m : c.getDirectMethods()) {
            if (suspiciousText(m.getName()) || suspiciousBody(m)) return true;
        }
        for (Method m : c.getVirtualMethods()) {
            if (suspiciousText(m.getName()) || suspiciousBody(m)) return true;
        }
        return false;
    }

    private static boolean suspiciousBody(Method m) {
        if (m.getImplementation() == null) return false;
        for (Instruction i : m.getImplementation().getInstructions()) {
            if (!(i instanceof ReferenceInstruction ri)) continue;
            Reference r = ri.getReference();
            if (r instanceof StringReference && suspiciousText(((StringReference) r).getString())) {
                return true;
            }
            if (r instanceof MethodReference) {
                MethodReference mr = (MethodReference) r;
                if (suspiciousText(mr.getDefiningClass()) || suspiciousText(mr.getName())) return true;
            }
            if (r instanceof TypeReference && suspiciousText(((TypeReference) r).getType())) return true;
        }
        return false;
    }

    private static boolean suspiciousText(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(java.util.Locale.US);
        for (String p : FRAMEWORK_PREFIXES) {
            if (lower.contains(p.toLowerCase(java.util.Locale.US))) return true;
        }
        for (String s : SUSPICIOUS) {
            if (lower.contains(s)) return true;
        }
        return false;
    }

    static boolean isSuspiciousNameForTest(String name) {
        return isSuspiciousName(name);
    }

    static boolean isSuspiciousFileForTest(String name) {
        return isSuspiciousFile(name);
    }

    static boolean isSuspiciousTextForTest(String value) {
        return suspiciousText(value);
    }

    static String removeManifestEntriesForTest(String manifest, Set<String> names) {
        return removeManifestEntries(manifest, names);
    }

    private static boolean isSuspiciousName(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        for (String s : SUSPICIOUS) if (n.contains(s)) return true;
        return false;
    }

    private static boolean isSuspiciousFile(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        if (!(n.startsWith("assets/") || n.startsWith("lib/"))) return false;
        for (String s : SUSPICIOUS) if (n.contains(s)) return true;
        return n.startsWith("assets/") && n.endsWith(".dex");
    }

    private static String readable(String name) {
        if (name.startsWith("lib/")) return "مكتبة أصلية مشتبهة";
        if (name.endsWith(".dex")) return "DEX محمّل من الأصول";
        return "ملف حقن/تحميل";
    }

    private static String removeManifestEntries(String manifest, Set<String> names) {
        String out = manifest;
        for (String name : names) {
            String q = Pattern.quote(name);
            out = out.replaceAll("<meta-data\\b[^>]*android:name\\s*=\\s*\"" + q + "\"[^>]*/?>", "");
            for (String tag : new String[]{"activity", "activity-alias", "service", "receiver",
                    "provider", "uses-permission", "uses-permission-sdk-23", "permission"}) {
                out = out.replaceAll("<" + tag + "\\b[^>]*android:name\\s*=\\s*\"" + q
                        + "\"[^>]*>.*?</" + tag + ">", "");
                out = out.replaceAll("<" + tag + "\\b[^>]*android:name\\s*=\\s*\"" + q
                        + "\"[^>]*/>", "");
            }
        }
        return out;
    }

    private static String readManifest(File apk) {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry e = zip.getEntry("AndroidManifest.xml");
            if (e == null) return null;
            try (InputStream is = zip.getInputStream(e)) {
                return new aXMLDecoder(is).decodeAsString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static File tempDir(Context context, String prefix) {
        File dir = new File(context.getCacheDir(), prefix + "_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    /** Restores a backup into a new APK beside the original, never overwriting it silently. */
    public static File restore(File backup, File original) throws IOException {
        if (backup == null || !backup.isFile()) throw new IOException("النسخة الاحتياطية غير موجودة.");
        File out = new File(original.getParentFile(),
                stripExt(original.getName()) + "_restored.apk");
        out = unused(out);
        copy(backup, out);
        return out;
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static File unused(File file) {
        if (!file.exists()) return file;
        String base = stripExt(file.getName());
        String ext = file.getName().substring(base.length());
        for (int i = 1; i < 1000; i++) {
            File c = new File(file.getParentFile(), base + "_" + i + ext);
            if (!c.exists()) return c;
        }
        return file;
    }

    private static void write(File file, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
    }

    private static void copy(File in, File out) throws IOException {
        try (InputStream is = new FileInputStream(in); OutputStream os = new FileOutputStream(out)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = is.read(b)) != -1) os.write(b, 0, n);
        }
    }

    private static void delete(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) delete(f); else f.delete();
        }
        dir.delete();
    }
}
