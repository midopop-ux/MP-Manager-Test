package io.github.abdurazaaqmohammed.feature.apk;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** UI for scanning and removing injected components, assets and hook classes. */
public final class InjectionRemovalDialog {

    private InjectionRemovalDialog() {
    }

    public static void show(Activity activity, File apk, Runnable onDone) {
        ProgressManager pm = new ProgressManager(activity, false).show();
        AppExecutors.io().execute(() -> {
            try {
                InjectionRemovalEngine.ScanReport report = InjectionRemovalEngine.scan(
                        activity, apk, pm.getLogger());
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    showReport(activity, apk, report, onDone);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("تعذر فحص الحقن")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }

    private static void showReport(Activity activity, File apk,
                                   InjectionRemovalEngine.ScanReport report, Runnable onDone) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView summary = new TextView(activity);
        summary.setText(report.summary());
        summary.setTextSize(13);
        root.addView(summary);

        if (report.findings.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("فحص الحقن")
                    .setView(root)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        List<MaterialCheckBox> boxes = new ArrayList<>();
        for (InjectionRemovalEngine.Finding finding : report.findings) {
            MaterialCheckBox box = new MaterialCheckBox(activity);
            box.setText(finding.label());
            box.setTextSize(12);
            box.setTag(finding);
            box.setChecked(finding.removable);
            list.addView(box);
            boxes.add(box);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (300 * activity.getResources().getDisplayMetrics().density)));

        MaterialCheckBox force = new MaterialCheckBox(activity);
        force.setText("محاولة إزالة العناصر المرتبطة (متقدم)");
        root.addView(force);
        MaterialCheckBox sign = new MaterialCheckBox(activity);
        sign.setText("إعادة توقيع الناتج");
        sign.setChecked(true);
        root.addView(sign);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton selected = new MaterialButton(activity);
        selected.setText("إزالة المحدد");
        MaterialButton all = new MaterialButton(activity);
        all.setText("إزالة الكل");
        actions.addView(selected, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(all, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("إزالة الحقن")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        selected.setOnClickListener(v -> runPatch(activity, apk, checked(boxes),
                force.isChecked(), sign.isChecked(), dialog, onDone));
        all.setOnClickListener(v -> runPatch(activity, apk, new ArrayList<>(report.findings),
                force.isChecked(), sign.isChecked(), dialog, onDone));
    }

    private static List<InjectionRemovalEngine.Finding> checked(List<MaterialCheckBox> boxes) {
        List<InjectionRemovalEngine.Finding> out = new ArrayList<>();
        for (MaterialCheckBox box : boxes) {
            if (box.isChecked() && box.getTag() instanceof InjectionRemovalEngine.Finding f) out.add(f);
        }
        return out;
    }

    private static void runPatch(Activity activity, File apk,
                                 List<InjectionRemovalEngine.Finding> chosen, boolean force,
                                 boolean sign, androidx.appcompat.app.AlertDialog dialog,
                                 Runnable onDone) {
        if (chosen.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setMessage("حدد عنصرًا واحدًا على الأقل.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (sign && activity instanceof AppCompatActivity) {
            SignWrapper.requireAuth((AppCompatActivity) activity, wrapper ->
                    patch(activity, apk, chosen, force, wrapper, dialog, onDone));
        } else {
            patch(activity, apk, chosen, force, null, dialog, onDone);
        }
    }

    private static void patch(Activity activity, File apk,
                              List<InjectionRemovalEngine.Finding> chosen, boolean force,
                              SignWrapper signer, androidx.appcompat.app.AlertDialog dialog,
                              Runnable onDone) {
        ProgressManager pm = new ProgressManager(activity, false).show();
        AppExecutors.io().execute(() -> {
            try {
                InjectionRemovalEngine.PatchReport result = InjectionRemovalEngine.remove(
                        activity, apk, chosen, force, pm.getLogger());
                if (signer != null && result.output != null && !result.output.isEmpty()) {
                    signer.signApk(new File(result.output));
                }
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    dialog.dismiss();
                    if (onDone != null) onDone.run();
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                            .setTitle(result.errors.isEmpty() ? "اكتملت إزالة الحقن" : "تعذر الإزالة")
                            .setMessage(result.summary())
                            .setPositiveButton(android.R.string.ok, null);
                    if (result.backup != null && !result.backup.isEmpty()
                            && new File(result.backup).isFile()) {
                        builder.setNeutralButton("استعادة النسخة", (d, w) -> {
                            try {
                                File restored = InjectionRemovalEngine.restore(new File(result.backup), apk);
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle("استُعيدت النسخة")
                                        .setMessage(restored.getAbsolutePath())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            } catch (Exception ex) {
                                new MaterialAlertDialogBuilder(activity)
                                        .setMessage(String.valueOf(ex.getMessage()))
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            }
                        });
                    }
                    builder.show();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("خطأ في إزالة الحقن")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }
}
