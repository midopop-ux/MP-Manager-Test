package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;

import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import mt.modder.hub.apkCloner.util.ApkCloner;

/**
 * Real APK cloning workflow around {@link ApkCloner}: inspect first, keep a
 * backup, write versioned outputs, validate the archive and report every
 * limitation instead of claiming that every protected app can be cloned.
 */
public final class ApkCloneEngine {

    public interface Callback {
        void onMessage(String message);
        void onProgress(int progress, int total);
    }

    public static final class Report {
        public String packageName = "";
        public String versionName = "";
        public long versionCode;
        public int minSdk;
        public int targetSdk;
        public int permissions;
        public boolean debuggable;
        public final List<String> abis = new ArrayList<>();
        public long fileSize;
        public int entries;
        public int dexCount;
        public int nativeLibs;
        public int assets;
        public boolean hasResources;
        public boolean signed;
        public boolean split;
        public final List<String> signatureSchemes = new ArrayList<>();
        public String manifestPackage = "";
        public String label = "";
        public String newPackage = "";
        public String newLabel = "";
        public boolean iconReplaced;
        public boolean signedOutput;
        public long outputBytes;
        public String outputSha256 = "";
        public String sourcePath = "";
        public String sourceSha256 = "";
        public boolean labelPatched;
        public boolean sharedUserId;
        public int riskLevel;
        public final List<String> risks = new ArrayList<>();
        public final List<String> verification = new ArrayList<>();
        public long elapsedMs;
        public boolean cloneable = true;
        public String output = "";
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("الحزمة: ").append(packageName).append('\n');
            sb.append("الإصدار: ").append(versionName).append(" (").append(versionCode).append(")\n");
            sb.append("minSdk: ").append(minSdk > 0 ? minSdk : "غير معروف")
                    .append(" · targetSdk: ").append(targetSdk > 0 ? targetSdk : "غير معروف").append('\n');
            if (!abis.isEmpty()) sb.append("المعماريات: ").append(TextUtils.join(", ", abis)).append('\n');
            if (permissions > 0) sb.append("الأذونات: ").append(permissions).append('\n');
            sb.append("الحجم: ").append(String.format(java.util.Locale.US, "%.1f MB", fileSize / 1048576f)).append('\n');
            sb.append("الملفات: ").append(entries).append(" · DEX: ").append(dexCount)
                    .append(" · مكتبات: ").append(nativeLibs).append(" · أصول: ").append(assets).append('\n');
            if (!label.isEmpty()) sb.append("الاسم الظاهر: ").append(label).append('\n');
            if (!newPackage.isEmpty()) sb.append("الحزمة الجديدة: ").append(newPackage).append('\n');
            if (!newLabel.isEmpty()) sb.append("الاسم الجديد: ").append(newLabel)
                    .append(labelPatched ? " (تم تطبيقه)" : " (لم يُطبَّق)").append('\n');
            sb.append("resources.arsc: ").append(hasResources ? "موجود" : "غير موجود").append('\n');
            sb.append("التوقيع: ").append(signed ? "موقّع" : "غير موقّع");
            if (!signatureSchemes.isEmpty()) sb.append(" (").append(TextUtils.join(" + ", signatureSchemes)).append(')');
            sb.append('\n');
            if (!output.isEmpty()) sb.append("الناتج: ").append(output).append('\n');
            if (outputBytes > 0) sb.append("حجم الناتج: ")
                    .append(String.format(java.util.Locale.US, "%.1f MB", outputBytes / 1048576f)).append('\n');
            if (!sourceSha256.isEmpty()) sb.append("بصمة المصدر: ").append(sourceSha256).append('\n');
            if (!outputSha256.isEmpty()) sb.append("SHA-256: ").append(outputSha256).append('\n');
            if (iconReplaced) sb.append("الأيقونة: استُبدلت بنجاح\n");
            if (signedOutput) sb.append("توقيع الناتج: تم\n");
            if (elapsedMs > 0) sb.append("المدة: ").append(elapsedMs).append(" ms\n");
            if (!risks.isEmpty()) {
                sb.append("\nتقييم المخاطر: ").append(riskLabel()).append('\n');
                for (String risk : risks) sb.append("• ").append(risk).append('\n');
            }
            if (!verification.isEmpty()) {
                sb.append("\nالتحقق بعد الاستنساخ:\n");
                for (String check : verification) sb.append("• ").append(check).append('\n');
            }
            if (!warnings.isEmpty()) {
                sb.append("\nتحذيرات:\n");
                for (String w : warnings) sb.append("• ").append(w).append('\n');
            }
            if (!errors.isEmpty()) {
                sb.append("\nأخطاء:\n");
                for (String e : errors) sb.append("• ").append(e).append('\n');
            }
            sb.append('\n').append(cloneable ? "النتيجة: قابل للاستنساخ مع القيود المذكورة."
                    : "النتيجة: غير قابل للاستنساخ بأمان.");
            return sb.toString();
        }

        public String riskLabel() {
            if (riskLevel >= 2) return "مرتفع / مانع";
            if (riskLevel == 1) return "متوسط";
            return "منخفض";
        }
    }

    private ApkCloneEngine() {
    }

    public static Report inspect(Context context, File apk) {
        Report report = new Report();
        if (apk == null || !apk.isFile()) {
            report.cloneable = false;
            report.errors.add("ملف APK غير موجود.");
            return report;
        }
        report.fileSize = apk.length();
        report.sourceSha256 = sha256(apk);
        try {
            PackageInfo pi = ApkInfoUtil.getPackageInfo(context, apk);
            if (pi != null) {
                report.packageName = pi.packageName == null ? "" : pi.packageName;
                report.versionName = pi.versionName == null ? "" : pi.versionName;
                report.versionCode = ApkInfoUtil.getVersionCode(pi);
                report.minSdk = ApkInfoUtil.getMinSdk(pi);
                if (pi.applicationInfo != null) {
                    report.targetSdk = pi.applicationInfo.targetSdkVersion;
                    report.debuggable = (pi.applicationInfo.flags
                            & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                }
                report.permissions = pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length;
            } else {
                report.warnings.add("تعذر قراءة معلومات الحزمة من PackageManager.");
            }
            try (ZipFile zip = new ZipFile(apk)) {
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    report.entries++;
                    String name = entry.getName();
                    if (name.startsWith("classes") && name.endsWith(".dex")) report.dexCount++;
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        report.nativeLibs++;
                        String[] parts = name.split("/");
                        if (parts.length > 2 && !report.abis.contains(parts[1])) {
                            report.abis.add(parts[1]);
                        }
                    }
                    if (name.startsWith("assets/")) report.assets++;
                    if (name.startsWith("split") || name.contains("config.")) report.split = true;
                    if ("resources.arsc".equals(name)) report.hasResources = entry.getSize() > 50;
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA")
                            || name.endsWith(".DSA") || name.endsWith(".EC"))) report.signed = true;
                }
            }
        } catch (Exception e) {
            report.errors.add("تعذر فحص الملف: " + e.getMessage());
        }
        if (report.packageName.isEmpty()) {
            report.cloneable = false;
            report.errors.add("لم يُعثر على اسم حزمة صالح في AndroidManifest.xml.");
        }
        if (report.dexCount == 0) {
            report.warnings.add("لا توجد ملفات DEX قابلة للقراءة.");
            addRisk(report, 1, "لا توجد ملفات DEX؛ قد يكون التطبيق resource-only أو غير مكتمل.");
        }
        if (!report.hasResources) {
            report.warnings.add("لا يوجد resources.arsc؛ قد يعتمد التطبيق على موارد مشتركة.");
            addRisk(report, 1, "لا يوجد resources.arsc؛ قد تفشل الموارد بعد الاستنساخ.");
        }
        if (report.debuggable) {
            report.warnings.add("التطبيق قابل للتنقيح (debuggable)؛ قد تكون حماية التوقيع أضعف.");
            addRisk(report, 1, "التطبيق debuggable؛ الحماية والتوقيع قد يكونان أضعف.");
        }
        if (report.signed) report.warnings.add("سيُعاد توقيع النسخة المستنسخة؛ لن تعمل تحديثات المتجر الأصلية فوقها.");
        if (report.split) {
            report.warnings.add("يبدو أن الملف يحتوي مدخلات split/config؛ استنساخ الحزم المقسمة قد يحتاج دمجًا أولًا.");
            addRisk(report, 1, "الحزمة تحتوي Split APK؛ الاستنساخ الحالي يعتمد على base APK فقط.");
        }
        detectSignatureSchemes(apk, report);
        if (!report.signatureSchemes.isEmpty()) report.signed = true;
        report.manifestPackage = readManifestPackage(apk);
        report.label = readManifestLabel(apk);
        analyzeManifestRisks(readManifest(apk), report);
        if (!report.packageName.isEmpty() && ApkInfoUtil.isInstalled(context, report.packageName)) {
            report.warnings.add("الحزمة الأصلية مثبتة؛ استخدم معرفًا جديدًا لتجنب التعارض.");
            addRisk(report, 1, "الحزمة الأصلية مثبتة على الجهاز؛ قد يحدث تعارض في البيانات أو المكونات.");
        }
        if (report.signatureSchemes.size() == 1 && report.signatureSchemes.contains("v1")) {
            addRisk(report, 1, "التوقيع المصدر v1 فقط؛ بعض إصدارات Android الحديثة قد ترفض التثبيت.");
        }
        return report;
    }

    private static void addRisk(Report report, int level, String message) {
        if (report == null) return;
        report.riskLevel = Math.max(report.riskLevel, level);
        if (!report.risks.contains(message)) report.risks.add(message);
    }

    private static void analyzeManifestRisks(String manifest, Report report) {
        if (manifest == null || manifest.isEmpty()) return;
        if (manifest.matches("(?s).*android:sharedUserId\\s*=\\s*\"[^\"]+\".*")) {
            report.sharedUserId = true;
            addRisk(report, 2, "التطبيق يستخدم android:sharedUserId؛ الاستنساخ قد يفشل أو يشارك بيانات تطبيق آخر.");
        }
        if (manifest.matches("(?s).*android:extractNativeLibs\\s*=\\s*\"false\".*")
                && report.nativeLibs > 0) {
            addRisk(report, 1, "التطبيق يستخدم extractNativeLibs=false؛ يجب أن تبقى مكتبات .so غير مضغوطة ومحاذية.");
        }
        if (manifest.matches("(?s).*android:taskAffinity\\s*=\\s*\"" + Pattern.quote(report.packageName) + "[^\"]*\".*")) {
            addRisk(report, 1, "taskAffinity مرتبط بالحزمة الأصلية؛ قد تتداخل المهام مع النسخة الأصلية.");
        }
    }

    public static boolean isValidPackageName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String value = name.trim();
        if (value.length() < 3 || value.length() > 200) return false;
        String[] parts = value.split("\\.");
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.charAt(0) == '$'
                    || !Character.isJavaIdentifierStart(part.charAt(0))) return false;
            for (int i = 1; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!Character.isJavaIdentifierPart(c) || c == '$') return false;
            }
        }
        return true;
    }

    public static Report clone(Context context, File source, String oldPackage, String newPackage,
                               boolean backup, SignWrapper signer, Callback callback) throws Exception {
        return clone(context, source, oldPackage, newPackage, backup, signer, callback, "", null);
    }

    public static Report clone(Context context, File source, String oldPackage, String newPackage,
                               boolean backup, SignWrapper signer, Callback callback,
                               String newLabel) throws Exception {
        return clone(context, source, oldPackage, newPackage, backup, signer, callback, newLabel, null);
    }

    public static Report clone(Context context, File source, String oldPackage, String newPackage,
                               boolean backup, SignWrapper signer, Callback callback,
                               String newLabel, byte[] iconPng) throws Exception {
        return clone(context, source, oldPackage, newPackage, backup, signer, callback,
                newLabel, iconPng, null);
    }

    public static Report clone(Context context, File source, String oldPackage, String newPackage,
                               boolean backup, SignWrapper signer, Callback callback,
                               String newLabel, byte[] iconPng, File outputDir) throws Exception {
        long started = System.currentTimeMillis();
        Report report = inspect(context, source);
        report.sourcePath = source == null ? "" : source.getAbsolutePath();
        if (source == null || !source.isFile() || !source.canRead()) {
            report.cloneable = false;
            report.errors.add("ملف المصدر غير موجود أو غير قابل للقراءة.");
            return report;
        }
        if (TextUtils.isEmpty(oldPackage)) oldPackage = report.packageName;
        if (!isValidPackageName(newPackage)) {
            report.cloneable = false;
            report.errors.add("معرف الحزمة الجديد غير صالح. استخدم صيغة com.example.app.");
            return report;
        }
        if (newPackage.equals(oldPackage)) {
            report.cloneable = false;
            report.errors.add("معرف الحزمة الجديد مطابق للقديم.");
            return report;
        }
        if (isInstalled(context, newPackage)) {
            report.warnings.add("معرف الحزمة الجديد مثبت مسبقًا؛ قد يفشل التثبيت أو يتعارض مع نسخة أخرى.");
        }
        if (report.sharedUserId) {
            report.cloneable = false;
            report.errors.add("لا يمكن الاستنساخ بأمان: التطبيق يستخدم android:sharedUserId.");
            return report;
        }
        if (!report.cloneable) return report;
        File dir = outputDir;
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs()) || !dir.canWrite()) {
            File fallback = cloneOutputDir(context);
            if (fallback != null && fallback.canWrite()) {
                report.warnings.add("مجلد الإخراج المحدد غير قابل للكتابة؛ استُخدم مجلد الاستنساخ الافتراضي.");
                dir = fallback;
            }
        }
        if (dir == null || !dir.canWrite()) {
            report.cloneable = false;
            report.errors.add("لا يوجد مجلد إخراج قابل للكتابة.");
            return report;
        }
        long usable = dir.getUsableSpace();
        long needed = Math.max(source.length(), 8L * 1024 * 1024);
        if (usable > 0 && usable < needed) {
            report.cloneable = false;
            report.errors.add("المساحة الحرة غير كافية لإنشاء نسخة آمنة من APK.");
            return report;
        }
        if (backup) {
            if (callback != null) callback.onMessage("نسخة احتياطية...");
            File backupDir = source.getParentFile();
            if (backupDir == null || !backupDir.canWrite()) backupDir = cloneOutputDir(context);
            if (backupDir == null || !backupDir.canWrite()) {
                report.cloneable = false;
                report.errors.add("تعذر إنشاء نسخة احتياطية: لا يوجد مجلد قابل للكتابة.");
                return report;
            }
            String backupBase = stripExt(source.getName());
            if (!TextUtils.isEmpty(report.packageName)) {
                backupBase += "_" + report.packageName.replaceAll("[^A-Za-z0-9._-]", "_");
            }
            File backupFile = new File(backupDir, backupBase + "_original_backup.apk");
            try {
                if (!backupFile.isFile() || backupFile.length() == 0) {
                    File backupTemp = new File(backupDir,
                            "." + backupFile.getName() + ".part-" + System.nanoTime());
                    copy(source, backupTemp);
                    if (!backupTemp.renameTo(backupFile)) {
                        if (!moveFileSafely(backupTemp, backupFile)) {
                            throw new java.io.IOException("تعذر تثبيت النسخة الاحتياطية.");
                        }
                    }
                }
            } catch (Exception e) {
                report.cloneable = false;
                report.errors.add("تعذر إنشاء النسخة الاحتياطية: " + e.getMessage());
                return report;
            }
        }
        File finalOut = nextOutput(source, dir);
        File workOut = new File(dir, "." + stripExt(finalOut.getName())
                + ".part-" + System.nanoTime() + ".apk");
        if (callback != null) callback.onMessage("استنساخ " + report.packageName + " → " + newPackage);
        ApkCloner cloner = new ApkCloner(context, new ApkCloner.ApkClonerCallBack() {
            @Override public void onMessage(String msg) {
                if (callback != null) callback.onMessage(msg);
            }

            @Override public void onProgress(int progress, int total) {
                if (callback != null) callback.onProgress(progress, total);
            }
        });
        try {
            cloner.setPath(source.getAbsolutePath(), oldPackage, newPackage);
            cloner.setOutputFile(workOut.getAbsolutePath());
            cloner.processApk();
            if (!workOut.isFile() || workOut.length() == 0) {
                throw new java.io.IOException("لم يُنتج المحرك ملف APK صالحًا.");
            }
            report.newPackage = newPackage;
            if (newLabel != null && !newLabel.trim().isEmpty()) {
                if (callback != null) callback.onMessage("تغيير اسم التطبيق...");
                report.newLabel = newLabel.trim();
                patchLabel(context, workOut, newLabel.trim(), report);
            }
            if (iconPng != null && iconPng.length > 0) {
                if (callback != null) callback.onMessage("تغيير الأيقونة...");
                replaceIcon(context, workOut, iconPng, report);
            }
            if (callback != null) callback.onMessage("ضبط توافق التثبيت...");
            try {
                ApkZipAlignUtil.ensureInstallable(workOut);
            } catch (Exception alignError) {
                report.warnings.add("تعذر ضبط محاذاة APK: " + alignError.getMessage());
            }
            if (signer != null) {
                if (callback != null) callback.onMessage("إعادة التوقيع...");
                signer.signApk(workOut);
                report.signatureSchemes.clear();
                detectSignatureSchemes(workOut, report);
                report.signedOutput = !report.signatureSchemes.isEmpty();
                if (!report.signedOutput) {
                    report.warnings.add("أُعيد التوقيع لكن لم تُكتشف مخططات APK Signature v1/v2/v3 في الناتج.");
                }
            } else {
                report.warnings.add("لم يُعد توقيع الناتج؛ ثبّته بعد التوقيع أو فعّل التوقيع التلقائي.");
            }
            validate(workOut, newPackage, report);
            String installIssue = ApkZipAlignUtil.installIssue(workOut);
            if (installIssue != null) {
                report.cloneable = false;
                report.errors.add("الناتج غير قابل للتثبيت بأمان: " + installIssue);
            }
            if (report.cloneable) {
                if (!moveFileSafely(workOut, finalOut)) {
                    report.cloneable = false;
                    report.errors.add("تعذر تثبيت ملف الإخراج النهائي بأمان.");
                }
            }
        } catch (Exception e) {
            report.cloneable = false;
            report.errors.add("فشل الاستنساخ: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (workOut.exists()) workOut.delete();
        }
        if (report.cloneable && finalOut.isFile() && finalOut.length() > 0) {
            report.output = finalOut.getAbsolutePath();
            report.outputBytes = finalOut.length();
            report.outputSha256 = sha256(finalOut);
        } else {
            report.output = "";
            if (report.errors.isEmpty()) report.errors.add("لم يتم إنتاج ملف إخراج صالح.");
        }
        report.elapsedMs = Math.max(0, System.currentTimeMillis() - started);
        if (callback != null) callback.onProgress(1, 1);
        return report;
    }

    private static boolean moveFileSafely(File from, File to) {
        if (from == null || !from.isFile() || to == null) return false;
        if (to.exists() && !to.delete()) return false;
        if (from.renameTo(to)) return true;
        try {
            copy(from, to);
            if (to.isFile() && to.length() > 0) {
                from.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            if (to.exists()) to.delete();
            return false;
        }
    }

    private static void validate(File out, String expectedPackage, Report report) {
        try (ZipFile zip = new ZipFile(out)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.getSize() == 0) {
                report.cloneable = false;
                report.errors.add("النسخة الناتجة تفتقد AndroidManifest.xml صالحًا.");
            }
            if (report.hasResources && zip.getEntry("resources.arsc") == null) {
                report.cloneable = false;
                report.errors.add("النسخة الناتجة تفتقد resources.arsc الموجود في المصدر.");
            }
            int entries = 0;
            int outDex = 0;
            byte[] buffer = new byte[65536];
            java.util.Enumeration<? extends ZipEntry> e = zip.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                entries++;
                String name = entry.getName();
                if (name.startsWith("classes") && name.endsWith(".dex")) outDex++;
                if (entry.isDirectory()) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    while (in.read(buffer) > 0) {
                        // Read every entry so CRC/truncation errors surface before install.
                    }
                } catch (Exception entryError) {
                    report.cloneable = false;
                    report.errors.add("مدخل تالف في الناتج: " + name + " (" + entryError.getMessage() + ")");
                    break;
                }
            }
            report.entries = entries;
            if (entries < 3) {
                report.cloneable = false;
                report.errors.add("النسخة الناتجة شبه فارغة.");
            }
            if (report.dexCount > 0 && outDex == 0) {
                report.cloneable = false;
                report.errors.add("لا توجد ملفات DEX في الناتج رغم وجودها في المصدر.");
            } else if (report.dexCount > 0 && outDex < report.dexCount) {
                report.warnings.add("عدد ملفات DEX في الناتج (" + outDex
                        + ") أقل من المصدر (" + report.dexCount + ").");
            }
        } catch (Exception e) {
            report.cloneable = false;
            report.errors.add("النسخة الناتجة غير قابلة للقراءة: " + e.getMessage());
        }
        String actual = readManifestPackage(out);
        if (!actual.isEmpty() && !expectedPackage.equals(actual)) {
            report.cloneable = false;
            report.errors.add("اسم الحزمة في البيان الناتج هو " + actual
                    + " بدلًا من " + expectedPackage + "؛ لن يُعتمد كاستنساخ مستقل.");
        }
        report.manifestPackage = actual;
        report.label = readManifestLabel(out);
    }

    private static void replaceIcon(Context context, File apk, byte[] png, Report report) {
        File work = new File(context.getCacheDir(), "clone_icon_" + System.nanoTime());
        work.mkdirs();
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Map<String, File> replacements = new java.util.LinkedHashMap<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                String lower = name.toLowerCase(java.util.Locale.US);
                if (entry.isDirectory() || !lower.startsWith("res/")) continue;
                if (!lower.endsWith(".png") && !lower.endsWith(".webp")
                        && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) continue;
                if (!lower.contains("ic_launcher") && !lower.contains("app_icon")) continue;
                File target = new File(work, name.replace('/', '_'));
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(png);
                }
                replacements.put(name, target);
            }
            if (replacements.isEmpty()) {
                report.warnings.add("لم تُعثر أيقونة launcher قابلة للاستبدال داخل الملف.");
                return;
            }
            File temp = new File(apk.getParentFile(), apk.getName() + ".icon." + System.nanoTime() + ".tmp");
            ApkZipAlignUtil.rebuildApk(apk, temp, replacements, null, null, null);
            if (temp.isFile() && temp.length() > 0) {
                if (replaceFileSafely(apk, temp)) {
                    report.iconReplaced = true;
                } else {
                    report.warnings.add("تم إنشاء أيقونة جديدة لكن تعذر استبدال الملف الأصلي بأمان.");
                }
            }
        } catch (Exception e) {
            report.warnings.add("تعذر تغيير الأيقونة: " + e.getMessage());
        } finally {
            File[] files = work.listFiles();
            if (files != null) for (File f : files) f.delete();
            work.delete();
        }
    }

    private static void patchLabel(Context context, File apk, String label, Report report) {
        try {
            String manifest = readManifest(apk);
            if (manifest == null || manifest.isEmpty()) {
                report.warnings.add("تعذر قراءة البيان لتغيير الاسم.");
                return;
            }
            Matcher m = Pattern.compile("(<application\\b[^>]*?android:label=\")([^\"]*)(\")")
                    .matcher(manifest);
            String safe = label.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
            String edited;
            if (m.find()) {
                edited = manifest.substring(0, m.start(2)) + safe + manifest.substring(m.end(2));
            } else {
                edited = manifest.replaceFirst("<application\\b",
                        "<application android:label=\"" + safe + "\"");
            }
            File work = new File(context.getCacheDir(), "clone_label_" + System.nanoTime());
            work.mkdirs();
            File manifestFile = new File(work, "AndroidManifest.xml");
            try (FileOutputStream out = new FileOutputStream(manifestFile)) {
                out.write(new aXMLEncoder().encodeString(edited, context));
            }
            File temp = new File(apk.getParentFile(), apk.getName() + ".label." + System.nanoTime() + ".tmp");
            java.util.Map<String, File> replacements = new java.util.LinkedHashMap<>();
            replacements.put("AndroidManifest.xml", manifestFile);
            ApkZipAlignUtil.rebuildApk(apk, temp, replacements, null, null, null);
            if (temp.isFile() && temp.length() > 0) {
                if (replaceFileSafely(apk, temp)) {
                    report.labelPatched = true;
                } else {
                    report.warnings.add("تم إنشاء نسخة الاسم لكن تعذر استبدال الملف الأصلي بأمان.");
                }
            }
            manifestFile.delete();
            work.delete();
        } catch (Exception e) {
            report.warnings.add("تعذر تغيير اسم التطبيق: " + e.getMessage());
        }
    }

    private static String readManifest(File apk) {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry e = zip.getEntry("AndroidManifest.xml");
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return new aXMLDecoder(in).decodeAsString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readManifestPackage(File apk) {
        String manifest = readManifest(apk);
        if (manifest == null) return "";
        Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(manifest);
        return m.find() ? m.group(1) : "";
    }

    private static String readManifestLabel(File apk) {
        String manifest = readManifest(apk);
        if (manifest == null) return "";
        Matcher m = Pattern.compile("android:label\\s*=\\s*\"([^\"]+)\"").matcher(manifest);
        return m.find() ? m.group(1) : "";
    }

    /** Replaces an APK without ever deleting the original before the new file is ready. */
    private static boolean replaceFileSafely(File target, File replacement) {
        File backup = new File(target.getParentFile(),
                target.getName() + ".bak." + System.nanoTime());
        if (!target.renameTo(backup)) return false;
        if (replacement.renameTo(target)) {
            backup.delete();
            return true;
        }
        backup.renameTo(target);
        return false;
    }

    private static File nextOutput(File source, File dir) {
        if (dir == null) dir = source.getParentFile() == null
                ? new File(".") : source.getParentFile();
        String base = stripExt(source.getName());
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(dir, base + "_clone_v" + i + ".apk");
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, base + "_clone.apk");
    }

    private static void detectSignatureSchemes(File apk, Report report) {
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("META-INF/") && (name.endsWith(".RSA")
                        || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    if (!report.signatureSchemes.contains("v1")) report.signatureSchemes.add("v1");
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
            long len = raf.length();
            int window = (int) Math.min(len, 65536 + 22);
            byte[] tail = new byte[window];
            raf.seek(len - window);
            raf.readFully(tail);
            int eocd = -1;
            for (int i = window - 22; i >= 0; i--) {
                if ((tail[i] & 0xff) == 0x50 && (tail[i + 1] & 0xff) == 0x4b
                        && (tail[i + 2] & 0xff) == 0x05 && (tail[i + 3] & 0xff) == 0x06) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) return;
            ByteBuffer eocdBuf = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
            long cdOffset = eocdBuf.getInt(eocd + 16) & 0xFFFFFFFFL;
            if (cdOffset < 24 || cdOffset > len) return;
            raf.seek(cdOffset - 24);
            byte[] footer = new byte[24];
            raf.readFully(footer);
            if (!new String(footer, 8, 16, java.nio.charset.StandardCharsets.US_ASCII)
                    .equals("APK Sig Block 42")) return;
            long blockSize = ByteBuffer.wrap(footer, 0, 8)
                    .order(ByteOrder.LITTLE_ENDIAN).getLong();
            long start = cdOffset - blockSize - 8;
            if (start < 0 || start >= cdOffset) return;
            raf.seek(start + 8);
            long end = cdOffset - 24;
            while (raf.getFilePointer() + 12 <= end) {
                byte[] lenBytes = new byte[8];
                raf.readFully(lenBytes);
                long pairLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
                if (pairLen < 4 || pairLen > end - raf.getFilePointer()) break;
                byte[] idBytes = new byte[4];
                raf.readFully(idBytes);
                int id = ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                String scheme = id == 0x7109871a ? "v2" : id == 0xf05368c0 ? "v3"
                        : id == 0x1b93ad61 ? "v3.1" : "";
                if (!scheme.isEmpty() && !report.signatureSchemes.contains(scheme)) {
                    report.signatureSchemes.add(scheme);
                }
                raf.seek(raf.getFilePointer() + pairLen - 4);
            }
        } catch (Exception ignored) {
        }
    }

    private static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) md.update(buffer, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format(java.util.Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void copy(File in, File out) throws Exception {
        try (InputStream is = new FileInputStream(in);
             FileOutputStream os = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) > 0) os.write(buffer, 0, n);
        }
    }
    public static final class InstalledApp {
        public String packageName = "";
        public String label = "";
        public String versionName = "";
        public long versionCode;
        public String sourceDir = "";
        public boolean system;
        public boolean split;
    }

    public static List<InstalledApp> installedApps(Context context) {
        List<InstalledApp> out = new ArrayList<>();
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                if (pi == null || pi.applicationInfo == null) continue;
                String dir = pi.applicationInfo.sourceDir;
                if (TextUtils.isEmpty(dir)) continue;
                InstalledApp app = new InstalledApp();
                app.packageName = pi.packageName == null ? "" : pi.packageName;
                app.label = pm.getApplicationLabel(pi.applicationInfo).toString();
                app.versionName = pi.versionName == null ? "" : pi.versionName;
                app.versionCode = ApkInfoUtil.getVersionCode(pi);
                app.sourceDir = dir;
                app.system = (pi.applicationInfo.flags
                        & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                app.split = pi.applicationInfo.splitSourceDirs != null
                        && pi.applicationInfo.splitSourceDirs.length > 0;
                out.add(app);
            }
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static File cloneOutputDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "ClonedApps");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    public static String suggestPackageName(Context context, String base) {
        String clean = DsSafe.sanitize(base);
        if (clean.isEmpty()) clean = "app";
        String candidate = clean + ".clone";
        return uniquePackageName(context, candidate);
    }

    public static String uniquePackageName(Context context, String candidate) {
        String value = DsSafe.sanitize(candidate);
        if (value.isEmpty()) value = "app.clone";
        if (!isInstalled(context, value)) return value;
        for (int i = 2; i < 1000; i++) {
            String next = value + i;
            if (!isInstalled(context, next)) return next;
        }
        return value + "_" + System.currentTimeMillis();
    }

    public static boolean isInstalled(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Report verifyClone(Context context, File source, File output,
                                     String expectedPackage, String expectedLabel) {
        Report sourceReport = inspect(context, source);
        Report result = inspect(context, output);
        result.sourcePath = source == null ? "" : source.getAbsolutePath();
        result.sourceSha256 = sourceReport.sourceSha256;
        result.newPackage = expectedPackage == null ? "" : expectedPackage.trim();
        result.newLabel = expectedLabel == null ? "" : expectedLabel.trim();
        if (source == null || !source.isFile()) {
            result.cloneable = false;
            result.errors.add("ملف المصدر الأصلي غير متاح للمقارنة.");
        }
        if (output == null || !output.isFile()) {
            result.cloneable = false;
            result.errors.add("ملف الناتج غير موجود.");
            return result;
        }
        if (!sourceReport.packageName.isEmpty() && sourceReport.packageName.equals(result.packageName)) {
            result.cloneable = false;
            result.errors.add("اسم الحزمة في الناتج ما زال مطابقًا للمصدر.");
        } else if (!sourceReport.packageName.isEmpty()) {
            result.verification.add("تغيّر اسم الحزمة من " + sourceReport.packageName
                    + " إلى " + result.packageName + ".");
        }
        if (!result.newPackage.isEmpty() && !result.newPackage.equals(result.packageName)) {
            result.cloneable = false;
            result.errors.add("اسم الحزمة الناتج لا يطابق الاسم المطلوب.");
        }
        if (sourceReport.dexCount > 0) {
            if (result.dexCount <= 0) {
                result.cloneable = false;
                result.errors.add("الناتج لا يحتوي ملفات DEX.");
            } else if (result.dexCount < sourceReport.dexCount) {
                result.warnings.add("عدد ملفات DEX في الناتج أقل من المصدر ("
                        + result.dexCount + " مقابل " + sourceReport.dexCount + ").");
            } else {
                result.verification.add("عدد ملفات DEX سليم: " + result.dexCount + ".");
            }
        }
        if (!result.newLabel.isEmpty()) {
            if (result.newLabel.equals(result.label)) {
                result.verification.add("تم تطبيق الاسم الجديد: " + result.newLabel + ".");
            } else {
                result.warnings.add("الاسم الظاهر في الناتج هو " + result.label
                        + " بدلًا من " + result.newLabel + ".");
            }
        }
        if (sourceReport.signed && !result.signed) {
            result.warnings.add("المصدر موقّع لكن الناتج غير موقّع.");
        } else if (result.signed) {
            result.verification.add("الناتج موقّع: " + TextUtils.join(", ", result.signatureSchemes) + ".");
        }
        String installIssue = ApkZipAlignUtil.installIssue(output);
        if (installIssue != null) {
            result.cloneable = false;
            result.errors.add("الناتج غير قابل للتثبيت: " + installIssue);
        } else {
            result.verification.add("بنية ZIP ومحاذاة التثبيت سليمة.");
        }
        result.outputBytes = output.length();
        result.outputSha256 = sha256(output);
        return result;
    }

    public static Report cloneInstalled(Context context, String packageName, String newPackage,
                                        boolean backup, SignWrapper signer, Callback callback,
                                        String newLabel, byte[] iconPng, File outputDir) throws Exception {
        for (InstalledApp app : installedApps(context)) {
            if (app.packageName.equals(packageName)) {
                Report report = clone(context, new File(app.sourceDir), app.packageName,
                        newPackage, backup, signer, callback, newLabel, iconPng, outputDir);
                if (app.split) report.warnings.add("التطبيق يستخدم Split APK؛ تم استنساخ base.apk فقط. "
                        + "قد تحتاج حزم التقسيم الإضافية إلى دمج.");
                return report;
            }
        }
        Report report = new Report();
        report.cloneable = false;
        report.errors.add("التطبيق غير مثبت أو لا يمكن الوصول إلى ملف APK الخاص به.");
        return report;
    }

    private static final class DsSafe {
        private static String sanitize(String raw) {
            if (raw == null) return "";
            String s = raw.trim().toLowerCase(java.util.Locale.US);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.') out.append(c);
                else if (c == '_' || c == '-') out.append('_');
            }
            String value = out.toString().replaceAll("\\.+", ".");
            while (value.startsWith(".")) value = value.substring(1);
            while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
            if (value.isEmpty()) return "";
            if (!Character.isJavaIdentifierStart(value.charAt(0))) value = "app." + value;
            if (!value.contains(".")) value = value + ".clone";
            return value;
        }
    }

}
