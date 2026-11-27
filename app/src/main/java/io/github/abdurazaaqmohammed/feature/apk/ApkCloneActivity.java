package io.github.abdurazaaqmohammed.feature.apk;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.feature.dex.DexBrowserActivity;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.main.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** Standalone APK clone workspace: installed apps, APK files, labels, icons and reports. */
public class ApkCloneActivity extends BaseActivity {

    private static final int REQ_APK = 7401;
    private static final int REQ_ICON = 7402;

    private TextView sourceInfo;
    private TextView riskView;
    private EditText packageInput;
    private EditText labelInput;
    private EditText outputInput;
    private TextView iconStatus;
    private CheckBox backupBox;
    private CheckBox signBox;
    private ProgressBar progress;
    private TextView report;
    private MaterialButton cloneButton;

    private File sourceFile;
    private String installedPackage = "";
    private byte[] iconPng;
    private ApkCloneEngine.Report current;
    private File lastOutput;
    private MaterialButton installButton;
    private MaterialButton shareButton;
    private MaterialButton verifyButton;
    private MaterialToolbar toolbar;
    private android.content.SharedPreferences clonePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clonePrefs = getSharedPreferences("apk_clone_ui", MODE_PRIVATE);
        buildUi();
        restoreLastOutput();
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

        toolbar = new MaterialToolbar(this);
        toolbar.setTitle("استوديو استنساخ التطبيقات");
        toolbar.setSubtitle("APK · تطبيقات مثبتة · أيقونة · اسم · توقيع");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, "النسخ المحفوظة");
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showProjects();
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
        sourceInfo = text("لم يتم اختيار مصدر.");
        sourceBox.addView(sourceInfo);
        riskView = text("تقييم المخاطر: غير معروف");
        sourceBox.addView(riskView);
        LinearLayout sourceRow = row();
        MaterialButton apk = button("اختيار APK");
        apk.setOnClickListener(v -> pickApk());
        MaterialButton installed = button("تطبيق مثبت");
        installed.setOnClickListener(v -> showInstalledPicker());
        sourceRow.addView(apk);
        sourceRow.addView(installed);
        sourceBox.addView(sourceRow);

        MaterialCardView settingsCard = card(body);
        LinearLayout settings = column(settingsCard);
        settings.addView(title("بيانات النسخة"));
        packageInput = field(settings, "اسم الحزمة الجديد", "", InputType.TYPE_CLASS_TEXT);
        MaterialButton uniquePkg = button("توليد معرف فريد");
        uniquePkg.setOnClickListener(v -> {
            String base = packageInput.getText().toString().trim();
            if (base.isEmpty() && current != null) base = current.packageName + ".clone";
            if (base.isEmpty()) base = "app.clone";
            packageInput.setText(ApkCloneEngine.uniquePackageName(this, base));
            toast("تم توليد معرف غير مثبت.");
        });
        settings.addView(uniquePkg);
        labelInput = field(settings, "اسم التطبيق الجديد", "", InputType.TYPE_CLASS_TEXT);
        outputInput = field(settings, "مجلد الإخراج", ApkCloneEngine.cloneOutputDir(this).getAbsolutePath(),
                InputType.TYPE_CLASS_TEXT);
        LinearLayout iconRow = row();
        MaterialButton icon = button("اختيار أيقونة");
        icon.setOnClickListener(v -> pickIcon());
        iconRow.addView(icon);
        iconStatus = text("لم تُحدد أيقونة جديدة.");
        iconRow.addView(iconStatus);
        settings.addView(iconRow);
        backupBox = new CheckBox(this);
        backupBox.setText("الاحتفاظ بنسخة احتياطية من APK الأصلي");
        backupBox.setChecked(clonePrefs.getBoolean("backup", true));
        backupBox.setOnCheckedChangeListener((b, checked) ->
                clonePrefs.edit().putBoolean("backup", checked).apply());
        settings.addView(backupBox);
        signBox = new CheckBox(this);
        signBox.setText("إعادة توقيع النسخة الناتجة");
        signBox.setChecked(clonePrefs.getBoolean("sign", true));
        signBox.setOnCheckedChangeListener((b, checked) ->
                clonePrefs.edit().putBoolean("sign", checked).apply());
        settings.addView(signBox);
        String savedOutput = clonePrefs.getString("output_dir", "");
        if (savedOutput != null && !savedOutput.isEmpty()) outputInput.setText(savedOutput);
        outputInput.setOnFocusChangeListener((v, focused) -> {
            if (!focused) clonePrefs.edit()
                    .putString("output_dir", outputInput.getText().toString().trim()).apply();
        });
        MaterialButton signSettings = button("إعدادات التوقيع");
        signSettings.setOnClickListener(v -> SignatureKeyDialog.show(this));
        settings.addView(signSettings);

        MaterialCardView actions = card(body);
        LinearLayout actionBox = column(actions);
        LinearLayout actionRow = row();
        MaterialButton inspect = button("فحص المصدر");
        inspect.setOnClickListener(v -> inspect());
        cloneButton = button("استنساخ الآن");
        cloneButton.setOnClickListener(v -> cloneNow());
        MaterialButton batch = button("استنساخ عدة تطبيقات");
        batch.setOnClickListener(v -> showBatchPicker());
        actionRow.addView(inspect);
        actionRow.addView(cloneButton);
        actionRow.addView(batch);
        actionBox.addView(actionRow);
        LinearLayout advancedRow = row();
        MaterialButton dex = button("مكونات DEX");
        dex.setOnClickListener(v -> openDexBrowser());
        MaterialButton components = button("مكونات الحزمة");
        components.setOnClickListener(v -> showComponents());
        advancedRow.addView(dex);
        advancedRow.addView(components);
        MaterialButton copyReport = button("نسخ التقرير");
        copyReport.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("clone-report", report.getText().toString()));
            toast("نُسخ التقرير.");
        });
        advancedRow.addView(copyReport);
        actionBox.addView(advancedRow);
        LinearLayout outputRow = row();
        installButton = button("تثبيت الناتج");
        installButton.setEnabled(false);
        installButton.setOnClickListener(v -> installOutput());
        shareButton = button("مشاركة الناتج");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareOutput());
        verifyButton = button("تحقق من الناتج");
        verifyButton.setEnabled(false);
        verifyButton.setOnClickListener(v -> verifyOutput());
        outputRow.addView(installButton);
        outputRow.addView(shareButton);
        outputRow.addView(verifyButton);
        actionBox.addView(outputRow);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        actionBox.addView(progress);
        report = text("اختر مصدرًا ثم افحصه لعرض التفاصيل.");
        report.setTextIsSelectable(true);
        actionBox.addView(report);
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

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/octet-stream"});
        startActivityForResult(i, REQ_APK);
    }

    private void pickIcon() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_ICON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_APK) {
            try {
                File dir = new File(getCacheDir(), "clone_source");
                dir.mkdirs();
                File out = new File(dir, "source_" + System.currentTimeMillis() + ".apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) throw new Exception("تعذر فتح الملف.");
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                setSource(out, "");
            } catch (Exception e) {
                toast("تعذر نسخ APK: " + e.getMessage());
            }
        } else if (requestCode == REQ_ICON) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp == null) throw new Exception("صورة غير صالحة.");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                iconPng = out.toByteArray();
                iconStatus.setText("أيقونة محددة · " + bmp.getWidth() + "×" + bmp.getHeight());
            } catch (Exception e) {
                iconStatus.setText("تعذر قراءة الأيقونة: " + e.getMessage());
            }
        }
    }

    private void setSource(File file, String installed) {
        sourceFile = file;
        installedPackage = installed == null ? "" : installed;
        iconPng = null;
        if (iconStatus != null) iconStatus.setText("لم تُحدد أيقونة جديدة.");
        if (riskView != null) riskView.setText("تقييم المخاطر: جارٍ الفحص...");
        sourceInfo.setText("المصدر: " + file.getName() + "\n" + file.getAbsolutePath());
        AppExecutors.io().execute(() -> {
            ApkCloneEngine.Report r = ApkCloneEngine.inspect(this, file);
            AppExecutors.runOnUiIfAlive(this, () -> {
                current = r;
                report.setText(r.summary());
                if (riskView != null) {
                    riskView.setText("تقييم المخاطر: " + r.riskLabel()
                            + (r.risks.isEmpty() ? " · لا توجد تنبيهات جوهرية"
                            : " · " + r.risks.size() + " تنبيه"));
                }
                packageInput.setText(ApkCloneEngine.suggestPackageName(this, r.packageName));
                labelInput.setText(r.label == null || r.label.isEmpty()
                        ? (r.packageName + " Clone") : (r.label + " Clone"));
            });
        });
    }

    private void showComponents() {
        File target = lastOutput != null && lastOutput.isFile() ? lastOutput : sourceFile;
        if (target == null || !target.isFile()) {
            toast("اختر APK أولاً.");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        AppExecutors.io().execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                com.apk.axml.APKParser parser = new com.apk.axml.APKParser();
                parser.parse(target.getAbsolutePath(), this);
                sb.append("الحزمة: ").append(parser.getPackageName()).append('\n');
                sb.append("الاسم: ").append(parser.getAppName()).append('\n');
                sb.append("الإصدار: ").append(parser.getVersionName())
                        .append(" (").append(parser.getVersionCode()).append(")\n");
                sb.append("minSdk: ").append(parser.getMinSDKVersion())
                        .append(" · targetSdk: ").append(parser.getTargetSDKVersion()).append("\n\n");
                appendComponents(sb, "Activities", parser.getActivities());
                appendComponents(sb, "Services", parser.getServices());
                appendComponents(sb, "Receivers", parser.getReceivers());
                appendComponents(sb, "Providers", parser.getProviders());
                java.util.List<String> permissions = parser.getPermissions();
                sb.append("Permissions (").append(permissions == null ? 0 : permissions.size()).append("):\n");
                if (permissions != null) {
                    for (String permission : permissions) sb.append("  • ").append(permission).append('\n');
                }
            } catch (Exception e) {
                sb.append("تعذر تحليل المكونات: ").append(e.getMessage());
            }
            String text = sb.toString();
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                TextView body = new TextView(this);
                body.setTextSize(13f);
                body.setTextIsSelectable(true);
                body.setPadding(dp(18), dp(8), dp(18), dp(8));
                body.setText(text);
                ScrollView scroll = new ScrollView(this);
                scroll.addView(body);
                new AlertDialog.Builder(this)
                        .setTitle("مكونات الحزمة")
                        .setView(scroll)
                        .setPositiveButton("إغلاق", null)
                        .show();
            });
        });
    }

    private void appendComponents(StringBuilder sb, String label, java.util.List<?> items) {
        sb.append(label).append(" (").append(items == null ? 0 : items.size()).append("):\n");
        if (items != null) {
            for (Object item : items) {
                String name = item == null ? "" : item.toString();
                if (item instanceof android.content.pm.ComponentInfo) {
                    android.content.pm.ComponentInfo info = (android.content.pm.ComponentInfo) item;
                    name = info.name + (info.exported ? " · exported" : "");
                }
                sb.append("  • ").append(name).append('\n');
            }
        }
        sb.append('\n');
    }

    private void openDexBrowser() {
        File target = lastOutput != null && lastOutput.isFile() ? lastOutput : sourceFile;
        if (target == null || !target.isFile()) {
            toast("اختر APK أولاً.");
            return;
        }
        Intent intent = new Intent(this, DexBrowserActivity.class);
        intent.putExtra(DexBrowserActivity.EXTRA_APK, target.getAbsolutePath());
        startActivity(intent);
    }

    private void inspect() {
        File file = sourceFile;
        if (file == null) {
            toast("اختر APK أو تطبيقًا مثبتًا أولاً.");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        AppExecutors.io().execute(() -> {
            ApkCloneEngine.Report r = ApkCloneEngine.inspect(this, file);
            AppExecutors.runOnUiIfAlive(this, () -> {
                current = r;
                progress.setVisibility(View.GONE);
                report.setText(r.summary());
                if (riskView != null) {
                    riskView.setText("تقييم المخاطر: " + r.riskLabel()
                            + (r.risks.isEmpty() ? " · لا توجد تنبيهات جوهرية"
                            : " · " + r.risks.size() + " تنبيه"));
                }
                if (packageInput.getText().toString().trim().isEmpty()) {
                    packageInput.setText(ApkCloneEngine.suggestPackageName(this, r.packageName));
                }
            });
        });
    }

    private void showInstalledPicker() {
        List<ApkCloneEngine.InstalledApp> apps = ApkCloneEngine.installedApps(this);
        if (apps.isEmpty()) {
            toast("لا توجد تطبيقات مثبتة قابلة للقراءة.");
            return;
        }
        EditText search = new EditText(this);
        search.setHint("بحث بالاسم أو الحزمة");
        ListView list = new ListView(this);
        List<String> labels = new ArrayList<>();
        List<ApkCloneEngine.InstalledApp> shown = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String q = search.getText().toString().trim().toLowerCase();
            labels.clear();
            shown.clear();
            for (ApkCloneEngine.InstalledApp app : apps) {
                if (q.isEmpty() || app.label.toLowerCase().contains(q)
                        || app.packageName.toLowerCase().contains(q)) {
                    shown.add(app);
                    labels.add(app.label + "\n" + app.packageName + " · v" + app.versionName
                            + (app.system ? " · نظام" : ""));
                }
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        filter.run();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(4));
        box.addView(search);
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("اختر تطبيقًا مثبتًا")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            ApkCloneEngine.InstalledApp app = shown.get(position);
            dialog.dismiss();
            File file = new File(app.sourceDir);
            sourceFile = file;
            installedPackage = app.packageName;
            iconPng = null;
            if (iconStatus != null) iconStatus.setText("لم تُحدد أيقونة جديدة.");
            if (riskView != null) riskView.setText("تقييم المخاطر: جارٍ الفحص...");
            sourceInfo.setText("تطبيق مثبت: " + app.label + "\n" + app.packageName
                    + "\n" + app.sourceDir + (app.split ? "\n⚠ Split APK" : ""));
            packageInput.setText(ApkCloneEngine.suggestPackageName(this, app.packageName));
            labelInput.setText(app.label + " Clone");
            inspect();
        });
        dialog.show();
    }

    private void cloneNow() {
        if (sourceFile == null) {
            toast("اختر مصدرًا أولاً.");
            return;
        }
        String newPkg = packageInput.getText().toString().trim();
        if (!ApkCloneEngine.isValidPackageName(newPkg)) {
            toast("اسم الحزمة غير صالح؛ استخدم com.example.clone");
            return;
        }
        String label = labelInput.getText().toString().trim();
        String outputPath = outputInput.getText().toString().trim();
        File outDir = outputPath.isEmpty()
                ? ApkCloneEngine.cloneOutputDir(this) : new File(outputPath);
        StringBuilder confirm = new StringBuilder();
        confirm.append("المصدر: ").append(sourceFile.getName()).append('\n');
        confirm.append("الحزمة الجديدة: ").append(newPkg).append('\n');
        if (!label.isEmpty()) confirm.append("الاسم الجديد: ").append(label).append('\n');
        confirm.append("الإخراج: ").append(outDir.getAbsolutePath()).append('\n');
        confirm.append(backupBox.isChecked() ? "نسخة احتياطية: نعم\n" : "نسخة احتياطية: لا\n");
        confirm.append(signBox.isChecked() ? "إعادة التوقيع: نعم" : "إعادة التوقيع: لا");
        if (current != null && !current.warnings.isEmpty()) {
            confirm.append("\n\nتنبيهات:\n• ").append(current.warnings.get(0));
            if (current.warnings.size() > 1) {
                confirm.append("\n• ").append(current.warnings.get(1));
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("تأكيد الاستنساخ")
                .setMessage(confirm.toString())
                .setPositiveButton("بدء", (d, w) -> startClone(newPkg, label, outDir))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startClone(String newPkg, String label, File outDir) {
        if (signBox.isChecked()) {
            SignWrapper.requireAuth(this, wrapper -> runClone(wrapper, newPkg, label, outDir),
                    () -> toast("أُلغي التوقيع."));
        } else {
            runClone(null, newPkg, label, outDir);
        }
    }

    private void runClone(SignWrapper signer, String newPkg, String label, File outDir) {
        cloneButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(0);
        report.setText("جارٍ الاستنساخ…");
        AppExecutors.io().execute(() -> {
            try {
                ApkCloneEngine.Report r;
                if (!installedPackage.isEmpty()) {
                    r = ApkCloneEngine.cloneInstalled(this, installedPackage, newPkg,
                            backupBox.isChecked(), signer, new ApkCloneEngine.Callback() {
                                @Override public void onMessage(String message) {
                                    AppExecutors.runOnUiIfAlive(ApkCloneActivity.this,
                                            () -> report.setText(message));
                                }
                                @Override public void onProgress(int p, int total) {
                                    AppExecutors.runOnUiIfAlive(ApkCloneActivity.this, () -> {
                                        progress.setIndeterminate(false);
                                        progress.setMax(Math.max(1, total));
                                        progress.setProgress(p);
                                    });
                                }
                            }, label, iconPng, outDir);
                } else {
                    r = ApkCloneEngine.clone(this, sourceFile,
                            current == null || current.packageName.isEmpty()
                                    ? installedPackage : current.packageName,
                            newPkg, backupBox.isChecked(), signer,
                            new ApkCloneEngine.Callback() {
                                @Override public void onMessage(String message) {
                                    AppExecutors.runOnUiIfAlive(ApkCloneActivity.this,
                                            () -> report.setText(message));
                                }
                                @Override public void onProgress(int p, int total) {
                                    AppExecutors.runOnUiIfAlive(ApkCloneActivity.this, () -> {
                                        progress.setIndeterminate(false);
                                        progress.setMax(Math.max(1, total));
                                        progress.setProgress(p);
                                    });
                                }
                            }, label, iconPng, outDir);
                }
                AppExecutors.runOnUiIfAlive(this, () -> {
                    current = r;
                    progress.setVisibility(View.GONE);
                    cloneButton.setEnabled(true);
                    report.setText(r.summary());
                    if (r.cloneable && !r.output.isEmpty() && new File(r.output).isFile()) {
                        setLastOutput(new File(r.output));
                        CloneProjectStore.record(this, sourceFile, r);
                    }
                    toast(r.cloneable ? "تم الاستنساخ بنجاح." : "تعذر إكمال الاستنساخ.");
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    cloneButton.setEnabled(true);
                    report.setText("خطأ: " + e.getMessage());
                    toast("فشل الاستنساخ.");
                });
            }
        });
    }

    private void showBatchPicker() {
        List<ApkCloneEngine.InstalledApp> apps = ApkCloneEngine.installedApps(this);
        if (apps.isEmpty()) {
            toast("لا توجد تطبيقات مثبتة.");
            return;
        }
        List<ApkCloneEngine.InstalledApp> shown = new ArrayList<>();
        java.util.Map<String, Boolean> selected = new java.util.HashMap<>();
        EditText search = new EditText(this);
        search.setHint("بحث بالاسم أو الحزمة");
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        List<String> labels = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, labels);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String q = search.getText().toString().trim().toLowerCase();
            shown.clear();
            labels.clear();
            for (ApkCloneEngine.InstalledApp app : apps) {
                if (q.isEmpty() || app.label.toLowerCase().contains(q)
                        || app.packageName.toLowerCase().contains(q)) {
                    shown.add(app);
                    labels.add(app.label + "\n" + app.packageName
                            + (app.split ? " · Split" : ""));
                }
            }
            adapter.notifyDataSetChanged();
            for (int i = 0; i < shown.size(); i++) {
                list.setItemChecked(i, Boolean.TRUE.equals(
                        selected.get(shown.get(i).packageName)));
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            ApkCloneEngine.InstalledApp app = shown.get(position);
            boolean now = !Boolean.TRUE.equals(selected.get(app.packageName));
            selected.put(app.packageName, now);
            list.setItemChecked(position, now);
        });
        filter.run();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(4));
        box.addView(search);
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        AlertDialog batchDialog = new AlertDialog.Builder(this)
                .setTitle("اختر تطبيقات للاستنساخ الدفعي")
                .setView(box)
                .setPositiveButton("استنساخ المحدد", (d, w) -> {
                    List<ApkCloneEngine.InstalledApp> chosen = new ArrayList<>();
                    for (ApkCloneEngine.InstalledApp app : apps) {
                        if (Boolean.TRUE.equals(selected.get(app.packageName))) chosen.add(app);
                    }
                    if (chosen.isEmpty()) {
                        toast("لم تحدد تطبيقات.");
                        return;
                    }
                    String outputPath = outputInput.getText().toString().trim();
                    File batchOut = outputPath.isEmpty()
                            ? ApkCloneEngine.cloneOutputDir(this) : new File(outputPath);
                    if (signBox.isChecked()) {
                        SignWrapper.requireAuth(this,
                                wrapper -> runBatch(chosen, wrapper, batchOut),
                                () -> toast("أُلغي توقيع الدفعة."));
                    } else {
                        runBatch(chosen, null, batchOut);
                    }
                })
                .setNeutralButton("تحديد الكل", null)
                .setNegativeButton("إلغاء", null)
                .create();
        batchDialog.setOnShowListener(d -> {
            batchDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                boolean selectAll = false;
                for (ApkCloneEngine.InstalledApp app : apps) {
                    if (!Boolean.TRUE.equals(selected.get(app.packageName))) {
                        selectAll = true;
                        break;
                    }
                }
                for (ApkCloneEngine.InstalledApp app : apps) {
                    selected.put(app.packageName, selectAll);
                }
                filter.run();
                batchDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setText(selectAll ? "إلغاء الكل" : "تحديد الكل");
            });
        });
        batchDialog.show();
    }

    private void runBatch(List<ApkCloneEngine.InstalledApp> selected, SignWrapper signer,
                          File outDir) {
        cloneButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setMax(selected.size());
        progress.setProgress(0);
        AppExecutors.io().execute(() -> {
            int done = 0;
            int ok = 0;
            final File[] latest = new File[1];
            StringBuilder log = new StringBuilder();
            for (ApkCloneEngine.InstalledApp app : selected) {
                try {
                    String pkg = ApkCloneEngine.suggestPackageName(this, app.packageName);
                    ApkCloneEngine.Report r = ApkCloneEngine.cloneInstalled(this, app.packageName,
                            pkg, backupBox.isChecked(), signer, null,
                            app.label + " Clone", null, outDir);
                    if (r.cloneable && !r.output.isEmpty()) {
                        ok++;
                        latest[0] = new File(r.output);
                        CloneProjectStore.record(this, null, r);
                    }
                    log.append(r.cloneable ? "✓ " : "✗ ").append(app.label)
                            .append(" → ").append(pkg).append('\n');
                    if (!r.errors.isEmpty()) log.append("   ").append(r.errors.get(0)).append('\n');
                } catch (Exception e) {
                    log.append("✗ ").append(app.label).append(": ").append(e.getMessage()).append('\n');
                }
                done++;
                final int p = done;
                AppExecutors.runOnUiIfAlive(this, () -> progress.setProgress(p));
            }
            final int success = ok;
            AppExecutors.runOnUiIfAlive(this, () -> {
                cloneButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                report.setText("استنساخ دفعي: " + success + "/" + selected.size() + " ناجح\n\n" + log);
                if (latest[0] != null && latest[0].isFile()) setLastOutput(latest[0]);
                toast("اكتمل الاستنساخ الدفعي.");
            });
        });
    }

    private void showProjects() {
        List<CloneProjectStore.Snapshot> snapshots = CloneProjectStore.list(this);
        if (snapshots.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("النسخ المحفوظة")
                    .setMessage("لا توجد نسخ محفوظة بعد. أنشئ نسخة ليُسجَّل مسارها وبصمتها هنا.")
                    .setPositiveButton("إغلاق", null)
                    .show();
            return;
        }
        ListView list = new ListView(this);
        List<String> labels = new ArrayList<>();
        for (CloneProjectStore.Snapshot snapshot : snapshots) {
            labels.add(snapshot.name + "\n" + snapshot.newPackage + " · "
                    + (snapshot.signed ? "موقّع" : "غير موقّع") + " · "
                    + snapshot.createdAt
                    + (new File(snapshot.outputPath).isFile() ? "" : " · الملف مفقود"));
        }
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("النسخ المحفوظة")
                .setView(list)
                .setNegativeButton("إغلاق", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            showProjectActions(snapshots.get(position));
        });
        dialog.show();
    }

    private void showProjectActions(CloneProjectStore.Snapshot snapshot) {
        File output = new File(snapshot.outputPath);
        String[] actions = {"استعادة كآخر ناتج", "عرض التقرير", "مقارنة مع الناتج الحالي",
                "نسخ المسار", "حذف ملف APK", "حذف من السجل"};
        new AlertDialog.Builder(this)
                .setTitle(snapshot.name)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        if (!output.isFile()) {
                            toast("ملف النسخة مفقود.");
                            return;
                        }
                        setLastOutput(output);
                        toast("استُعيدت النسخة.");
                    } else if (which == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle(snapshot.name)
                                .setMessage(snapshot.summary)
                                .setPositiveButton("إغلاق", null)
                                .show();
                    } else if (which == 2) {
                        if (lastOutput == null || !lastOutput.isFile()) {
                            toast("لا يوجد ناتج حالي للمقارنة.");
                            return;
                        }
                        File currentFile = lastOutput;
                        progress.setVisibility(View.VISIBLE);
                        progress.setIndeterminate(true);
                        AppExecutors.io().execute(() -> {
                            ApkCloneEngine.Report currentReport = ApkCloneEngine.inspect(this, currentFile);
                            CloneProjectStore.Snapshot current = new CloneProjectStore.Snapshot();
                            current.name = currentFile.getName();
                            current.newPackage = currentReport.newPackage;
                            current.size = currentFile.length();
                            current.dexCount = currentReport.dexCount;
                            current.signed = currentReport.signedOutput;
                            current.signatureSchemes = android.text.TextUtils.join(
                                    ", ", currentReport.signatureSchemes);
                            current.sha256 = currentReport.outputSha256;
                            String comparison = CloneProjectStore.compare(snapshot, current);
                            AppExecutors.runOnUiIfAlive(this, () -> {
                                progress.setVisibility(View.GONE);
                                new AlertDialog.Builder(this)
                                        .setTitle("مقارنة")
                                        .setMessage(comparison)
                                        .setPositiveButton("إغلاق", null)
                                        .show();
                            });
                        });
                    } else if (which == 3) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("APK", snapshot.outputPath));
                        toast("نُسخ المسار.");
                    } else if (which == 4) {
                        new AlertDialog.Builder(this)
                                .setTitle("حذف ملف APK")
                                .setMessage("سيُحذف ملف النسخة من التخزين وسجلها. لا يمكن التراجع.")
                                .setPositiveButton("حذف", (dd, ww) -> {
                                    if (output.isFile() && !output.delete()) {
                                        toast("تعذر حذف الملف.");
                                        return;
                                    }
                                    if (lastOutput != null && lastOutput.equals(output)) setLastOutput(null);
                                    CloneProjectStore.delete(this, snapshot.id);
                                    showProjects();
                                })
                                .setNegativeButton("إلغاء", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("حذف من السجل")
                                .setMessage("لن يُحذف ملف APK نفسه، فقط سجل النسخة.")
                                .setPositiveButton("حذف", (dd, ww) -> {
                                    CloneProjectStore.delete(this, snapshot.id);
                                    showProjects();
                                })
                                .setNegativeButton("إلغاء", null)
                                .show();
                    }
                })
                .show();
    }

    private void restoreLastOutput() {
        String path = clonePrefs.getString("last_output", "");
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        if (file.isFile()) setLastOutput(file);
    }

    private void setLastOutput(File file) {
        lastOutput = file;
        clonePrefs.edit()
                .putString("last_output", file == null ? "" : file.getAbsolutePath()).apply();
        boolean ready = file != null && file.isFile();
        if (installButton != null) installButton.setEnabled(ready);
        if (shareButton != null) shareButton.setEnabled(ready);
        if (verifyButton != null) verifyButton.setEnabled(ready);
    }

    private void installOutput() {
        if (lastOutput == null || !lastOutput.isFile()) {
            toast("لا يوجد ناتج صالح للتثبيت.");
            return;
        }
        InstallUtil.installApkWithDialog(this, lastOutput);
    }

    private void shareOutput() {
        if (lastOutput == null || !lastOutput.isFile()) {
            toast("لا يوجد ناتج صالح للمشاركة.");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", lastOutput);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "مشاركة APK"));
        } catch (Exception e) {
            toast("تعذر المشاركة: " + e.getMessage());
        }
    }

    private void verifyOutput() {
        if (lastOutput == null || !lastOutput.isFile()) {
            toast("لا يوجد ناتج للتحقق.");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        AppExecutors.io().execute(() -> {
            File source = sourceFile;
            if ((source == null || !source.isFile()) && current != null
                    && current.sourcePath != null && !current.sourcePath.isEmpty()) {
                source = new File(current.sourcePath);
            }
            ApkCloneEngine.Report r = source != null && source.isFile()
                    ? ApkCloneEngine.verifyClone(this, source, lastOutput,
                            current == null ? "" : current.newPackage,
                            current == null ? "" : current.newLabel)
                    : ApkCloneEngine.inspect(this, lastOutput);
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                report.setText("تحقق من الناتج:\n\n" + r.summary());
                toast(r.cloneable ? "الناتج سليم وقابل للتثبيت." : "الناتج يحتاج مراجعة.");
            });
        });
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
