package io.github.abdurazaaqmohammed.feature.crypto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.feature.ai.AiChatActivity;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** Content-first decryption and file analysis workspace. */
public class FileCryptoActivity extends BaseActivity {

    public static final String EXTRA_PATH = "file_crypto_path";
    private static final int REQ_FILE = 7601;
    private static final int REQ_DIR = 7602;

    private TextView sourceInfo;
    private TextView statusText;
    private TextView detailText;
    private TextView keyStatus;
    private EditText passwordInput;
    private EditText keyInput;
    private EditText ivInput;
    private EditText outputInput;
    private CheckBox allBox;
    private ProgressBar progress;
    private ListView list;
    private ArrayAdapter<String> adapter;

    private final List<FileCryptoEngine.Finding> findings = new ArrayList<>();
    private File source;
    private FileCryptoEngine.Finding selected;
    private FileCryptoEngine.Result lastResult;
    private File lastOutput;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("file_crypto_ui", MODE_PRIVATE);
        buildUi();
        String requested = getIntent().getStringExtra(EXTRA_PATH);
        if (requested != null && !requested.isEmpty()) {
            File requestedFile = new File(requested);
            if (requestedFile.exists()) setSource(requestedFile, "");
        }
    }

    private int color(int attr) {
        View v = findViewById(android.R.id.content);
        if (v == null) v = getWindow().getDecorView();
        return MaterialColors.getColor(v, attr, 0xFF121212);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        setContentView(root);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("فك التشفير وتحليل الملفات");
        toolbar.setSubtitle("كشف الترميز والضغط والتشفير · معاينة · مفاتيح · دفعات");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, "نسخ التقرير");
        toolbar.getMenu().add(0, 2, 1, "شرح ذكي");
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                copyReport();
                return true;
            }
            if (item.getItemId() == 2) {
                askAi();
                return true;
            }
            return false;
        });
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MaterialCardView sourceCard = card(body);
        LinearLayout sourceBox = column(sourceCard);
        sourceBox.addView(title("المصدر"));
        sourceInfo = text("لم يتم اختيار ملف أو مجلد.");
        sourceInfo.setTextIsSelectable(true);
        sourceBox.addView(sourceInfo);
        LinearLayout sourceRow = row();
        MaterialButton file = button("اختيار ملف");
        file.setOnClickListener(v -> pickFile());
        MaterialButton dir = button("اختيار مجلد");
        dir.setOnClickListener(v -> pickDir());
        MaterialButton app = button("تطبيق مثبت");
        app.setOnClickListener(v -> pickInstalledApp());
        sourceRow.addView(file);
        sourceRow.addView(dir);
        sourceRow.addView(app);
        sourceBox.addView(sourceRow);

        MaterialCardView actionCard = card(body);
        LinearLayout actions = column(actionCard);
        actions.addView(title("العمليات"));
        allBox = new CheckBox(this);
        allBox.setText("عرض كل الملفات والمدخلات، وليس المشتبهة فقط");
        allBox.setChecked(prefs.getBoolean("show_all", false));
        allBox.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("show_all", checked).apply());
        actions.addView(allBox);
        LinearLayout actionRow = row();
        MaterialButton scan = button("فحص المصدر");
        scan.setOnClickListener(v -> scanSource());
        MaterialButton decrypt = button("فك المحدد");
        decrypt.setOnClickListener(v -> decryptSelected());
        MaterialButton batch = button("فك الكل");
        batch.setOnClickListener(v -> decryptAll());
        actionRow.addView(scan);
        actionRow.addView(decrypt);
        actionRow.addView(batch);
        actions.addView(actionRow);
        LinearLayout actionRow2 = row();
        MaterialButton preview = button("معاينة قبل");
        preview.setOnClickListener(v -> previewSelected());
        MaterialButton save = button("حفظ آخر نتيجة");
        save.setOnClickListener(v -> saveLastResult());
        MaterialButton extract = button("استخراج الأرشيف");
        extract.setOnClickListener(v -> extractSelectedArchive());
        actionRow2.addView(preview);
        actionRow2.addView(save);
        actionRow2.addView(extract);
        actions.addView(actionRow2);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        actions.addView(progress);
        statusText = text("اختر مصدرًا ثم افحصه.");
        statusText.setTextIsSelectable(true);
        actions.addView(statusText);

        MaterialCardView listCard = card(body);
        LinearLayout listBox = column(listCard);
        listBox.addView(title("النتائج"));
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(1));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < findings.size()) {
                selected = findings.get(position);
                showDetails(selected);
            }
        });
        listBox.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        listBox.addView(text("اضغط على أي نتيجة لعرض تفاصيلها ومعاينتها."));

        MaterialCardView keyCard = card(body);
        LinearLayout keys = column(keyCard);
        keys.addView(title("المفتاح / كلمة المرور"));
        passwordInput = field(keys, "كلمة مرور ZIP أو الملف", prefs.getString("password", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput = field(keys, "مفتاح التشفير (نص أو Hex أو Base64)", prefs.getString("key", ""),
                InputType.TYPE_CLASS_TEXT);
        ivInput = field(keys, "IV / Nonce (نص أو Hex أو Base64)", prefs.getString("iv", ""),
                InputType.TYPE_CLASS_TEXT);
        keyStatus = text("إن لم تتوفر معلومات التشفير فلا يمكن ضمان الفك.");
        keys.addView(keyStatus);
        LinearLayout keyRow = row();
        MaterialButton useKey = button("استخدام المفتاح في الفك");
        useKey.setOnClickListener(v -> decryptSelected());
        MaterialButton clearKey = button("مسح المفاتيح");
        clearKey.setOnClickListener(v -> {
            passwordInput.setText("");
            keyInput.setText("");
            ivInput.setText("");
            keyStatus.setText("تم مسح المفاتيح من الحقول.");
        });
        keyRow.addView(useKey);
        keyRow.addView(clearKey);
        keys.addView(keyRow);

        MaterialCardView detailCard = card(body);
        LinearLayout detailBox = column(detailCard);
        detailBox.addView(title("تفاصيل الملف المحدد"));
        detailText = text("لم يتم تحديد نتيجة.");
        detailText.setTextIsSelectable(true);
        detailBox.addView(detailText);

        MaterialCardView outputCard = card(body);
        LinearLayout outputBox = column(outputCard);
        outputBox.addView(title("مجلد حفظ النتائج"));
        outputInput = field(outputBox, "المسار", prefs.getString("output_dir",
                new File(getExternalFilesDir(null), "decrypted").getAbsolutePath()),
                InputType.TYPE_CLASS_TEXT);
        outputInput.setOnFocusChangeListener((v, focused) -> {
            if (!focused) prefs.edit()
                    .putString("output_dir", outputInput.getText().toString().trim()).apply();
        });
        outputBox.addView(text("تُحفظ النتائج الناجحة فقط، ولا يُعلن نجاح أي عملية لم تنتج محتوى صالحًا."));
    }

    private MaterialCardView card(LinearLayout parent) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(20));
        c.setCardElevation(dp(1));
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(color(com.google.android.material.R.attr.colorOutline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        parent.addView(c, lp);
        return c;
    }

    private LinearLayout column(MaterialCardView card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(box);
        return box;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView text(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(13f);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private MaterialButton button(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText field(LinearLayout parent, String hint, String value, int inputType) {
        TextView label = text(hint);
        label.setTextSize(12f);
        parent.addView(label);
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setInputType(inputType);
        parent.addView(e);
        return e;
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_FILE);
    }

    private void pickDir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(i, REQ_DIR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_FILE) {
            try {
                setSource(copyUriToCache(uri), "");
            } catch (Exception e) {
                toast("تعذر فتح الملف: " + e.getMessage());
            }
        } else if (requestCode == REQ_DIR) {
            File dir = treeUriToFile(uri);
            if (dir != null && dir.isDirectory()) {
                setSource(dir, "");
            } else {
                toast("تعذر تحويل المجلد إلى مسار قابل للقراءة.");
            }
        }
    }

    private File copyUriToCache(Uri uri) throws Exception {
        String name = displayName(uri);
        if (name == null || name.isEmpty()) name = "selected_" + System.currentTimeMillis() + ".bin";
        File dir = new File(getCacheDir(), "crypto_source");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء مجلد مؤقت.");
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("المصدر غير قابل للقراءة.");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return out;
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private File treeUriToFile(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            String[] parts = docId.split(":", 2);
            if (parts.length == 2) {
                if ("primary".equalsIgnoreCase(parts[0])) {
                    return new File(Environment.getExternalStorageDirectory(), parts[1]);
                }
                return new File("/storage/" + parts[0], parts[1]);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void pickInstalledApp() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<ApplicationInfo> usable = new ArrayList<>();
        for (ApplicationInfo info : apps) {
            if (info.sourceDir != null && new File(info.sourceDir).isFile()) usable.add(info);
        }
        Collections.sort(usable, (a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        String[] labels = new String[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            labels[i] = pm.getApplicationLabel(usable.get(i)) + "\n" + usable.get(i).packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("اختر تطبيقًا مثبتًا")
                .setItems(labels, (d, which) -> {
                    ApplicationInfo info = usable.get(which);
                    setSource(new File(info.sourceDir), info.packageName);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void setSource(File file, String label) {
        source = file;
        selected = null;
        lastResult = null;
        findings.clear();
        adapter.clear();
        adapter.notifyDataSetChanged();
        detailText.setText("لم يتم تحديد نتيجة.");
        sourceInfo.setText("المصدر: " + file.getName() + "\n" + file.getAbsolutePath()
                + (label == null || label.isEmpty() ? "" : "\n" + label));
        statusText.setText("جاهز للفحص.");
        scanSource();
    }

    private void scanSource() {
        if (source == null || !source.exists()) {
            toast("اختر ملفًا أو مجلدًا أولًا.");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        statusText.setText("جارٍ الفحص...");
        File target = source;
        boolean includeAll = allBox.isChecked();
        AppExecutors.io().execute(() -> {
            List<FileCryptoEngine.Finding> result = FileCryptoEngine.scan(target, includeAll,
                    (message, done, total) -> AppExecutors.runOnUiIfAlive(this,
                            () -> statusText.setText(message)));
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                findings.clear();
                findings.addAll(result);
                adapter.clear();
                for (FileCryptoEngine.Finding f : findings) {
                    adapter.add(f.shortLabel() + "\n" + FileCryptoEngine.formatSize(f.size)
                            + "  ·  " + f.path);
                }
                adapter.notifyDataSetChanged();
                statusText.setText("تم العثور على " + findings.size()
                        + " نتيجة. المشتبهة والمشفرة تظهر افتراضيًا.");
                if (!findings.isEmpty()) {
                    selected = findings.get(0);
                    showDetails(selected);
                } else {
                    selected = null;
                    detailText.setText("لا توجد نتائج مطابقة. جرّب تفعيل عرض كل الملفات أو فحص مصدر آخر.");
                }
            });
        });
    }

    private void showDetails(FileCryptoEngine.Finding f) {
        if (f == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("الاسم: ").append(f.name).append('\n');
        sb.append("المسار: ").append(f.path).append('\n');
        if (!f.container.isEmpty()) sb.append("الحاوية: ").append(f.container).append('\n');
        sb.append("الحجم: ").append(FileCryptoEngine.formatSize(f.size)).append('\n');
        sb.append("النوع المكتشف: ").append(f.type).append('\n');
        sb.append("التصنيف: ").append(kindLabel(f.kind)).append('\n');
        sb.append("الحالة: ").append(f.status).append('\n');
        if (!f.encoding.isEmpty()) sb.append("الترميز: ").append(f.encoding).append('\n');
        if (!f.cipher.isEmpty()) sb.append("التشفير المرجح: ").append(f.cipher).append('\n');
        sb.append("الإنتروبيا: ").append(String.format(java.util.Locale.US, "%.2f", f.entropy))
                .append("/8.0  ·  الثقة: ").append(f.confidence).append("%\n");
        if (!f.detail.isEmpty()) sb.append('\n').append(f.detail);
        if (f.needsKey || f.encrypted) {
            sb.append("\n\nملاحظة: التشفير الحقيقي لا يمكن فكه دون المفتاح أو كلمة المرور الصحيحة.");
        }
        detailText.setText(sb.toString());
        keyStatus.setText(f.zipEncrypted ? "هذا مدخل ZIP مشفر؛ كلمة المرور مطلوبة."
                : f.needsKey ? "هذا الملف يحتاج مفتاحًا أو معلومات إضافية."
                : "يمكن تجربة الفك التلقائي أو إدخال مفتاح.");
    }

    private String kindLabel(FileCryptoEngine.Kind kind) {
        if (kind == FileCryptoEngine.Kind.TEXT) return "نص";
        if (kind == FileCryptoEngine.Kind.ENCODING) return "ترميز/تحويل";
        if (kind == FileCryptoEngine.Kind.COMPRESSION) return "ضغط";
        if (kind == FileCryptoEngine.Kind.ENCRYPTION) return "تشفير";
        if (kind == FileCryptoEngine.Kind.ARCHIVE) return "أرشيف";
        if (kind == FileCryptoEngine.Kind.BINARY) return "بيانات ثنائية";
        return "غير معروف";
    }

    private void previewSelected() {
        if (selected == null) {
            toast("اختر نتيجة أولًا.");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        FileCryptoEngine.Finding f = selected;
        AppExecutors.io().execute(() -> {
            try {
                byte[] data = readFinding(f);
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    showPreview("قبل الفك: " + f.name, data);
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    toast("تعذر قراءة المحتوى: " + e.getMessage());
                });
            }
        });
    }

    private byte[] readFinding(FileCryptoEngine.Finding f) throws Exception {
        if (f.archivePath != null && !f.archivePath.isEmpty()
                && f.entryName != null && !f.entryName.isEmpty()) {
            net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(new File(f.archivePath));
            for (net.lingala.zip4j.model.FileHeader h : z.getFileHeaders()) {
                if (f.entryName.equals(h.getFileName())) {
                    try (InputStream in = z.getInputStream(h)) {
                        return FileCryptoEngine.readLimited(in, FileCryptoEngine.MAX_SCAN_BYTES);
                    }
                }
            }
            throw new Exception("المدخل غير موجود في الأرشيف.");
        }
        return FileCryptoEngine.readFile(new File(f.path));
    }

    private void decryptSelected() {
        if (selected == null) {
            toast("اختر نتيجة أولًا.");
            return;
        }
        FileCryptoEngine.Finding f = selected;
        String password = passwordInput.getText().toString();
        String key = keyInput.getText().toString();
        String iv = ivInput.getText().toString();
        prefs.edit().putString("password", password).putString("key", key)
                .putString("iv", iv).apply();
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        statusText.setText("جارٍ فك " + f.name + "...");
        AppExecutors.io().execute(() -> {
            try {
                byte[] data = f.zipEncrypted ? null : readFinding(f);
                FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, password, key, iv);
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    if (r.success) {
                        lastResult = r;
                        statusText.setText("الطريقة: " + r.method + (r.uncertain ? " (نتيجة تخمينية)" : ""));
                        showPreview("بعد الفك: " + f.name, r.data);
                    } else {
                        statusText.setText(r.needsKey ? "يحتاج مفتاحًا/كلمة مرور."
                                : "لم ينتج فك صالح.");
                        new AlertDialog.Builder(this)
                                .setTitle("نتيجة الفك")
                                .setMessage(r.message + "\n\nلم يتم اعتبار العملية ناجحة.")
                                .setPositiveButton("حسنًا", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    statusText.setText("تعذر الفك: " + e.getMessage());
                });
            }
        });
    }

    private void decryptAll() {
        if (findings.isEmpty()) {
            toast("لا توجد نتائج. افحص المصدر أولًا.");
            return;
        }
        String password = passwordInput.getText().toString();
        String key = keyInput.getText().toString();
        String iv = ivInput.getText().toString();
        File outDir = new File(outputInput.getText().toString().trim());
        if (!outDir.exists() && !outDir.mkdirs()) {
            toast("تعذر إنشاء مجلد الإخراج.");
            return;
        }
        List<FileCryptoEngine.Finding> targets = new ArrayList<>(findings);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setMax(targets.size());
        progress.setProgress(0);
        AppExecutors.io().execute(() -> {
            int ok = 0;
            int failed = 0;
            int needKey = 0;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                FileCryptoEngine.Finding f = targets.get(i);
                try {
                    byte[] data = f.zipEncrypted ? null : readFinding(f);
                    FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, password, key, iv);
                    if (r.success && r.data != null) {
                        File saved = writeResult(f, r, outDir);
                        if (saved != null) {
                            ok++;
                            lines.add("✔ " + f.name + " → " + r.method + " → " + saved.getName());
                        } else {
                            failed++;
                            lines.add("✘ " + f.name + " → تعذر الحفظ");
                        }
                    } else {
                        failed++;
                        if (r.needsKey) needKey++;
                        lines.add("✘ " + f.name + " → " + r.message);
                    }
                } catch (Exception e) {
                    failed++;
                    lines.add("✘ " + f.name + " → " + e.getMessage());
                }
                final int done = i + 1;
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setProgress(done);
                    statusText.setText("فك الدفعة: " + done + "/" + targets.size());
                });
            }
            final int fOk = ok;
            final int fFail = failed;
            final int fKey = needKey;
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                statusText.setText("اكتملت الدفعة: نجح " + fOk + " · فشل " + fFail
                        + " · يحتاج مفتاحًا " + fKey + "\nالمجلد: " + outDir.getAbsolutePath());
                StringBuilder sb = new StringBuilder();
                for (String line : lines) sb.append(line).append('\n');
                new AlertDialog.Builder(this)
                        .setTitle("نتائج فك الدفعة")
                        .setMessage(sb.length() == 0 ? "لا توجد نتائج." : sb.toString())
                        .setPositiveButton("حسنًا", null)
                        .show();
            });
        });
    }

    private File writeResult(FileCryptoEngine.Finding f, FileCryptoEngine.Result r, File outDir) {
        try {
            String base = f.name == null || f.name.isEmpty() ? "result" : f.name;
            base = base.replaceAll("[^A-Za-z0-9._-]", "_");
            String ext = extensionFor(r.outputType, r.data);
            File out = new File(outDir, base + "." + r.method.replaceAll("[^A-Za-z0-9_-]", "_") + ext);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(r.data);
            }
            lastOutput = out;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String extensionFor(String type, byte[] data) {
        if (type.contains("PNG")) return ".png";
        if (type.contains("JPEG")) return ".jpg";
        if (type.contains("GIF")) return ".gif";
        if (type.contains("WEBP")) return ".webp";
        if (type.contains("PDF")) return ".pdf";
        if (type.contains("ZIP")) return ".zip";
        if (type.contains("GZIP")) return ".gz";
        if (type.contains("DEX")) return ".dex";
        if (type.contains("ELF")) return ".so";
        if (type.contains("SQLite")) return ".db";
        if (FileCryptoEngine.isPlainText(data)) return ".txt";
        return ".bin";
    }

    private void showPreview(String title, byte[] data) {
        if (data == null) {
            toast("لا توجد بيانات للمعاينة.");
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bmp != null) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setImageBitmap(bmp);
            box.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        }
        TextView info = text("النوع: " + FileCryptoEngine.detectType(data, "")
                + " · الحجم: " + FileCryptoEngine.formatSize(data.length)
                + " · SHA-256: " + FileCryptoEngine.sha256(data));
        info.setTextIsSelectable(true);
        box.addView(info);
        TextView body = text(FileCryptoEngine.previewText(data, 20000));
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(11f);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("إغلاق", null)
                .setNeutralButton("نسخ النص", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crypto-preview",
                            FileCryptoEngine.previewText(data, 20000)));
                    toast("نُسخ المحتوى.");
                })
                .create();
        dialog.show();
    }

    private void saveLastResult() {
        if (lastResult == null || lastResult.data == null) {
            toast("لا توجد نتيجة ناجحة لحفظها.");
            return;
        }
        File dir = new File(outputInput.getText().toString().trim());
        if (!dir.exists() && !dir.mkdirs()) {
            toast("تعذر إنشاء مجلد الحفظ.");
            return;
        }
        File saved = writeResult(selected, lastResult, dir);
        if (saved == null) {
            toast("تعذر حفظ النتيجة.");
            return;
        }
        statusText.setText("تم الحفظ: " + saved.getAbsolutePath());
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", saved);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/octet-stream");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "مشاركة النتيجة"));
        } catch (Exception ignored) {
        }
    }

    private void extractSelectedArchive() {
        if (selected == null) {
            toast("اختر نتيجة أولًا.");
            return;
        }
        File archive = selected.archivePath != null && !selected.archivePath.isEmpty()
                ? new File(selected.archivePath) : new File(selected.path);
        if (!archive.isFile() || !FileCryptoEngine.isZip(archive)) {
            toast("النتيجة المحددة ليست أرشيف ZIP/APK.");
            return;
        }
        File outDir = new File(outputInput.getText().toString().trim(),
                FileCryptoEngine.baseName(archive.getName()) + "_extracted");
        if (!outDir.exists() && !outDir.mkdirs()) {
            toast("تعذر إنشاء مجلد الاستخراج.");
            return;
        }
        String password = passwordInput.getText().toString();
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        AppExecutors.io().execute(() -> {
            int count = 0;
            String error = "";
            try {
                net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(archive);
                if (password != null && !password.isEmpty()) z.setPassword(password.toCharArray());
                for (net.lingala.zip4j.model.FileHeader h : z.getFileHeaders()) {
                    if (h.isDirectory()) continue;
                    String safe = h.getFileName().replace('\\', '/');
                    while (safe.startsWith("/") || safe.startsWith("../")) safe = safe.substring(1);
                    File target = new File(outDir, safe);
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (InputStream in = z.getInputStream(h);
                         FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    count++;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final int extracted = count;
            final String err = error;
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                statusText.setText(err.isEmpty()
                        ? "تم استخراج " + extracted + " مدخل إلى " + outDir.getAbsolutePath()
                        : "فشل الاستخراج: " + err);
                new AlertDialog.Builder(this)
                        .setTitle("استخراج الأرشيف")
                        .setMessage(err.isEmpty()
                                ? "تم استخراج " + extracted + " مدخل إلى:\n" + outDir.getAbsolutePath()
                                : err)
                        .setPositiveButton("حسنًا", null)
                        .show();
            });
        });
    }

    private void askAi() {
        if (selected == null) {
            toast("اختر نتيجة أولًا.");
            return;
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("حلّل ملفًا مشتبهًا من ناحية التشفير والترميز دون اختلاق نتائج.\n");
        prompt.append("الاسم: ").append(selected.name).append('\n');
        prompt.append("المسار: ").append(selected.path).append('\n');
        prompt.append("النوع: ").append(selected.type).append('\n');
        prompt.append("الحالة: ").append(selected.status).append('\n');
        if (!selected.encoding.isEmpty()) prompt.append("الترميز: ").append(selected.encoding).append('\n');
        if (!selected.cipher.isEmpty()) prompt.append("التشفير المرجح: ").append(selected.cipher).append('\n');
        prompt.append("الإنتروبيا: ").append(String.format(java.util.Locale.US, "%.2f", selected.entropy)).append('\n');
        if (!selected.detail.isEmpty()) prompt.append("التفاصيل: ").append(selected.detail).append('\n');
        prompt.append("اقترح خطوات فك واقعية وما إذا كان يحتاج مفتاحًا، ولا تدّعِ نجاحًا غير مؤكد.");
        Intent intent = new Intent(this, AiChatActivity.class);
        intent.putExtra(AiChatActivity.EXTRA_PROMPT, prompt.toString());
        startActivity(intent);
    }

    private void copyReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("تقرير فك التشفير وتحليل الملفات\n");
        if (source != null) sb.append("المصدر: ").append(source.getAbsolutePath()).append('\n');
        sb.append("عدد النتائج: ").append(findings.size()).append("\n\n");
        for (FileCryptoEngine.Finding f : findings) {
            sb.append(f.shortLabel()).append('\n');
            sb.append("  ").append(f.path).append('\n');
            if (!f.cipher.isEmpty()) sb.append("  التشفير: ").append(f.cipher).append('\n');
            if (!f.encoding.isEmpty()) sb.append("  الترميز: ").append(f.encoding).append('\n');
            sb.append("  الإنتروبيا: ").append(String.format(java.util.Locale.US, "%.2f", f.entropy))
                    .append(" · الثقة: ").append(f.confidence).append("%\n");
            if (!f.detail.isEmpty()) sb.append("  ").append(f.detail.replace("\n", "\n  ")).append('\n');
            sb.append('\n');
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("crypto-report", sb.toString()));
        toast("نُسخ التقرير.");
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
