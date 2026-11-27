package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local catalogue of cloned outputs, with metadata and integrity hashes. */
final class CloneProjectStore {

    private static final String PREFS = "mp_clone_projects";
    private static final String KEY = "index";
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<Snapshot>>() { }.getType();

    static final class Snapshot {
        String id = "";
        String name = "";
        String createdAt = "";
        String sourcePath = "";
        String outputPath = "";
        String packageName = "";
        String newPackage = "";
        String label = "";
        String newLabel = "";
        String sha256 = "";
        String summary = "";
        long size;
        int dexCount;
        boolean cloneable;
        boolean signed;
        String signatureSchemes = "";
    }

    private CloneProjectStore() {
    }

    static Snapshot record(Context context, File source, ApkCloneEngine.Report report) {
        if (report == null || report.output == null || report.output.isEmpty()) return null;
        File output = new File(report.output);
        if (!output.isFile()) return null;
        Snapshot snapshot = new Snapshot();
        snapshot.id = UUID.randomUUID().toString();
        snapshot.name = (report.newLabel == null || report.newLabel.isEmpty()
                ? output.getName() : report.newLabel);
        snapshot.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        snapshot.sourcePath = report.sourcePath == null || report.sourcePath.isEmpty()
                ? (source == null ? "" : source.getAbsolutePath()) : report.sourcePath;
        snapshot.outputPath = output.getAbsolutePath();
        snapshot.packageName = report.packageName;
        snapshot.newPackage = report.newPackage;
        snapshot.label = report.label;
        snapshot.newLabel = report.newLabel;
        snapshot.sha256 = report.outputSha256;
        snapshot.summary = report.summary();
        snapshot.size = output.length();
        snapshot.dexCount = report.dexCount;
        snapshot.cloneable = report.cloneable;
        snapshot.signed = report.signedOutput;
        snapshot.signatureSchemes = android.text.TextUtils.join(", ", report.signatureSchemes);
        List<Snapshot> list = list(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (snapshot.outputPath.equals(list.get(i).outputPath)) list.remove(i);
        }
        list.add(0, snapshot);
        while (list.size() > 60) list.remove(list.size() - 1);
        write(context, list);
        return snapshot;
    }

    static List<Snapshot> list(Context context) {
        String raw = prefs(context).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        try {
            List<Snapshot> parsed = GSON.fromJson(raw, TYPE);
            if (parsed == null) return new ArrayList<>();
            Collections.sort(parsed, (a, b) -> b.createdAt.compareTo(a.createdAt));
            return parsed;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    static void delete(Context context, String id) {
        List<Snapshot> list = list(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) list.remove(i);
        }
        write(context, list);
    }

    static String compare(Snapshot first, Snapshot second) {
        if (first == null || second == null) return "اختر نسختين.";
        StringBuilder sb = new StringBuilder();
        sb.append("مقارنة النسخ\n\n");
        sb.append("A: ").append(first.name).append('\n');
        sb.append("B: ").append(second.name).append("\n\n");
        sb.append("الحزمة: ").append(first.newPackage).append(" ↔ ").append(second.newPackage)
                .append(first.newPackage.equals(second.newPackage) ? " (متطابقة)" : " (مختلفة)").append('\n');
        sb.append("الحجم: ").append(first.size).append(" ↔ ").append(second.size)
                .append(first.size == second.size ? " (متطابق)" : " (مختلف)").append('\n');
        sb.append("DEX: ").append(first.dexCount).append(" ↔ ").append(second.dexCount).append('\n');
        sb.append("التوقيع: ").append(first.signed ? "موقّع" : "غير موقّع")
                .append(" ↔ ").append(second.signed ? "موقّع" : "غير موقّع").append('\n');
        sb.append("SHA-256: ").append(first.sha256.equals(second.sha256)
                ? "متطابق" : "مختلف").append('\n');
        if (!first.sha256.equals(second.sha256)) {
            sb.append("  A: ").append(first.sha256).append('\n');
            sb.append("  B: ").append(second.sha256).append('\n');
        }
        return sb.toString();
    }

    private static void write(Context context, List<Snapshot> list) {
        prefs(context).edit().putString(KEY, GSON.toJson(list)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
