package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;

import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.reandroid.apk.APKLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

/**
 * Real Toast discovery and selective removal over APK DEX files. Only classes
 * that reference Toast are disassembled, every makeText/show block is
 * reported, and the APK is rebuilt with only the selected blocks removed.
 */
public final class ToastRemovalEngine {

    public enum Mode { SELECTED, ALL, KEEP_SELECTED }

    public static final class Hit {
        public String id = "";
        public String entry = "";
        public String classDescriptor = "";
        public String className = "";
        public String method = "";
        public String message = "";
        public int line;
        public int blockIndex;
        public String snippet = "";

        public String label() {
            return className + "\n" + method + " · سطر " + line
                    + (message == null || message.isEmpty() ? "" : "\n" + message);
        }
    }

    public static final class ScanReport {
        public final List<Hit> hits = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int dexCount;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("DEX: ").append(dexCount).append(" · استدعاءات Toast: ")
                    .append(hits.size()).append('\n');
            for (String w : warnings) sb.append("• ").append(w).append('\n');
            for (String e : errors) sb.append("✕ ").append(e).append('\n');
            if (hits.isEmpty() && errors.isEmpty()) sb.append("لا توجد استدعاءات Toast قابلة للمعالجة.");
            return sb.toString();
        }
    }

    public static final class PatchReport {
        public String output = "";
        public String backup = "";
        public int before;
        public int removed;
        public int remaining;
        public boolean signed;
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("قبل: ").append(before).append(" · أُزيل: ").append(removed)
                    .append(" · بقي: ").append(remaining).append('\n');
            if (!output.isEmpty()) sb.append("الناتج: ").append(output).append('\n');
            if (!backup.isEmpty()) sb.append("النسخة الاحتياطية: ").append(backup).append('\n');
            for (String w : warnings) sb.append("• ").append(w).append('\n');
            for (String e : errors) sb.append("✕ ").append(e).append('\n');
            return sb.toString();
        }
    }

    private static final Pattern CONST_STRING = Pattern.compile(
            "const-string(?:/jumbo)?\\s+[vp]\\d+\\s*,\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern METHOD = Pattern.compile("^\\.method\\s+(.+)$");

    private ToastRemovalEngine() {
    }

    public static ScanReport scan(Context context, File apk, APKLogger logger) throws Exception {
        ScanReport report = new ScanReport();
        File work = tempDir(context, "toast_scan");
        try {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
            report.dexCount = container.getDexEntryNames().size();
            BaksmaliOptions options = FastDexPatch.defaultBaksmaliOptions();
            Set<String> toastMethods = new LinkedHashSet<>(Arrays.asList("makeText", "show"));
            for (String entry : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entry);
                if (dexEntry == null) continue;
                DexBackedDexFile dex = dexEntry.getDexFile();
                Set<String> classes = FastDexPatch.findClassesWithMethodCalls(
                        dex, "Landroid/widget/Toast;", toastMethods);
                if (classes.isEmpty()) continue;
                File dir = new File(work, safe(entry));
                Map<String, File> files = FastDexPatch.disassembleClasses(
                        context, dex, classes, dir, options, logger);
                for (Map.Entry<String, File> file : files.entrySet()) {
                    String content = read(file.getValue());
                    for (Block block : parse(content, entry, file.getKey())) {
                        report.hits.add(block.hit);
                    }
                }
            }
            if (report.hits.isEmpty()) report.warnings.add("لم تُعثر استدعاءات Toast في الفئات المفحوصة.");
        } finally {
            delete(work);
        }
        return report;
    }

    public static PatchReport remove(Context context, File apk, List<Hit> selected,
                                     Mode mode, APKLogger logger) throws Exception {
        PatchReport report = new PatchReport();
        ScanReport before = scan(context, apk, logger);
        report.before = before.hits.size();
        Set<String> chosen = new LinkedHashSet<>();
        if (selected != null) for (Hit h : selected) if (h != null) chosen.add(h.id);
        if (mode == Mode.SELECTED && chosen.isEmpty()) {
            report.errors.add("لم يتم تحديد أي استدعاء Toast.");
            return report;
        }
        if (mode == Mode.ALL) {
            for (Hit h : before.hits) chosen.add(h.id);
        } else if (mode == Mode.KEEP_SELECTED) {
            for (Hit h : before.hits) if (!chosen.contains(h.id)) chosen.add(h.id);
        }
        if (chosen.isEmpty()) {
            report.errors.add("لا توجد عناصر قابلة للإزالة وفق الوضع المختار.");
            return report;
        }
        File work = tempDir(context, "toast_patch");
        try {
            Map<String, File> replacements = new LinkedHashMap<>();
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
            BaksmaliOptions options = FastDexPatch.defaultBaksmaliOptions();
            Set<String> toastMethods = new LinkedHashSet<>(Arrays.asList("makeText", "show"));
            for (String entry : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entry);
                if (dexEntry == null) continue;
                DexBackedDexFile dex = dexEntry.getDexFile();
                Set<String> classes = FastDexPatch.findClassesWithMethodCalls(
                        dex, "Landroid/widget/Toast;", toastMethods);
                if (classes.isEmpty()) continue;
                File dir = new File(work, safe(entry));
                Map<String, File> files = FastDexPatch.disassembleClasses(
                        context, dex, classes, dir, options, logger);
                boolean changed = false;
                for (Map.Entry<String, File> file : files.entrySet()) {
                    String content = read(file.getValue());
                    List<Block> blocks = parse(content, entry, file.getKey());
                    Set<Integer> removeLines = new LinkedHashSet<>();
                    for (Block b : blocks) {
                        if (chosen.contains(b.hit.id)) {
                            for (int i = b.start; i <= b.end; i++) removeLines.add(i);
                        }
                    }
                    if (removeLines.isEmpty()) {
                        file.getValue().delete();
                        continue;
                    }
                    write(file.getValue(), withoutLines(content, removeLines));
                    changed = true;
                    if (logger != null) logger.logMessage("Toast: " + file.getKey()
                            + " · أزيل " + removeLines.size() + " سطرًا");
                }
                if (!changed) continue;
                int api = FastDexPatch.detectDexApi(apk, entry);
                File mini = FastDexPatch.assembleMiniDex(context, dir, api, logger);
                byte[] merged = FastDexPatch.mergeDex(dex, mini, api);
                File mergedFile = new File(work, safe(entry) + ".merged");
                write(mergedFile, merged);
                replacements.put(entry, mergedFile);
            }
            if (replacements.isEmpty()) {
                report.errors.add("لم يتغير أي DEX؛ لم تُطابق العناصر المحددة.");
                return report;
            }
            report.backup = new File(apk.getParentFile(),
                    stripExt(apk.getName()) + "_before_toast_removal.apk").getAbsolutePath();
            copy(apk, new File(report.backup));
            File out = unused(new File(apk.getParentFile(),
                    stripExt(apk.getName()) + "_no_toast_selected.apk"));
            ApkZipAlignUtil.rebuildApk(apk, out, replacements, null, null, null);
            report.output = out.getAbsolutePath();
            ScanReport after = scan(context, out, logger);
            report.remaining = after.hits.size();
            report.removed = Math.max(0, report.before - report.remaining);
            if (report.removed == 0) report.errors.add("لم تُزل أي استدعاءات؛ تحقق من السجل.");
            if (report.remaining > 0) report.warnings.add("بقيت " + report.remaining
                    + " استدعاءات (متوقعة في وضع الاحتفاظ أو بسبب أنماط غير مباشرة).");
            if (!new File(report.output).isFile() || new File(report.output).length() == 0) {
                report.errors.add("لم يُنتج ملف APK صالح.");
            }
        } finally {
            delete(work);
        }
        return report;
    }

    static List<Hit> parseSmaliForTest(String content) {
        List<Hit> out = new ArrayList<>();
        for (Block b : parse(content, "classes.dex", "Lcom/example/Main;")) out.add(b.hit);
        return out;
    }

    private static List<Block> parse(String content, String entry, String descriptor) {
        List<Block> out = new ArrayList<>();
        String[] lines = content.split("\\n", -1);
        String method = "";
        int blockIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            Matcher mm = METHOD.matcher(t);
            if (mm.find()) {
                method = mm.group(1).trim();
                blockIndex = 0;
                continue;
            }
            if (!t.startsWith("invoke-") || !t.contains("Landroid/widget/Toast;->")) continue;
            boolean make = t.contains("->makeText");
            boolean show = t.contains("->show");
            if (!make && !show) continue;
            int start = i;
            int end = i;
            if (make) {
                int j = i + 1;
                while (j < lines.length && nonInstruction(lines[j])) j++;
                if (j < lines.length && lines[j].trim().startsWith("move-result")) {
                    end = j;
                    do j++;
                    while (j < lines.length && nonInstruction(lines[j]));
                }
                if (j < lines.length && lines[j].trim().startsWith("invoke-")
                        && lines[j].trim().contains("Landroid/widget/Toast;->show")) {
                    end = j;
                } else {
                    end = Math.max(end, j - 1);
                }
            }
            String message = "";
            for (int k = start - 1; k >= Math.max(0, start - 8); k--) {
                Matcher sm = CONST_STRING.matcher(lines[k].trim());
                if (sm.find()) {
                    message = unescape(sm.group(1));
                    break;
                }
            }
            if (message.isEmpty()) message = make ? "رسالة من مورد/متغير" : "Toast.show()";
            Hit hit = new Hit();
            hit.entry = entry;
            hit.classDescriptor = descriptor;
            hit.className = FastDexPatch.descriptorToClassName(descriptor);
            hit.method = method;
            hit.message = message;
            hit.line = start + 1;
            hit.blockIndex = blockIndex++;
            hit.id = entry + "|" + descriptor + "|" + method + "|" + hit.blockIndex;
            hit.snippet = lines[start].trim() + (end > start ? "\n" + lines[end].trim() : "");
            out.add(new Block(hit, start, end));
            if (end > start) i = end;
        }
        return out;
    }

    private static String withoutLines(String content, Set<Integer> remove) {
        String[] lines = content.split("\\n", -1);
        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < lines.length; i++) {
            if (!remove.contains(i)) sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    private static boolean nonInstruction(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith(".") || t.startsWith("#");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n")
                .replace("\\t", "\t").replace("\\\\", "\\");
    }

    private static String read(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            char[] b = new char[8192];
            int n;
            while ((n = r.read(b)) != -1) sb.append(b, 0, n);
        }
        return sb.toString();
    }

    private static void write(File file, String text) throws IOException {
        try (FileWriter w = new FileWriter(file)) { w.write(text); }
    }

    private static void write(File file, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
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
            File candidate = new File(file.getParentFile(), base + "_" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return file;
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

    private static final class Block {
        final Hit hit;
        final int start;
        final int end;

        Block(Hit hit, int start, int end) {
            this.hit = hit;
            this.start = start;
            this.end = end;
        }
    }
}
