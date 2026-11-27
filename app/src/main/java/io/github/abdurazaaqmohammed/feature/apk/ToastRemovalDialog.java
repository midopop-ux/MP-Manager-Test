package io.github.abdurazaaqmohammed.feature.apk;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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

/** UI for scanning, selecting, and removing Toast calls from an APK. */
public final class ToastRemovalDialog {

    private ToastRemovalDialog() {
    }

    public static void show(Activity activity, File apk, Runnable onDone) {
        ProgressManager pm = new ProgressManager(activity, false).show();
        AppExecutors.io().execute(() -> {
            try {
                ToastRemovalEngine.ScanReport report = ToastRemovalEngine.scan(
                        activity, apk, pm.getLogger());
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    showReport(activity, apk, report, onDone);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("تعذر فحص Toast")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }

    private static void showReport(Activity activity, File apk,
                                   ToastRemovalEngine.ScanReport report, Runnable onDone) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView summary = new TextView(activity);
        summary.setText(report.summary());
        summary.setTextSize(13);
        root.addView(summary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (report.hits.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("فحص Toast")
                    .setView(root)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        List<MaterialCheckBox> boxes = new ArrayList<>();
        for (ToastRemovalEngine.Hit hit : report.hits) {
            MaterialCheckBox box = new MaterialCheckBox(activity);
            box.setText(hit.label());
            box.setTextSize(12);
            box.setTag(hit);
            list.addView(box);
            boxes.add(box);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (280 * activity.getResources().getDisplayMetrics().density)));

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
        MaterialButton keep = new MaterialButton(activity);
        keep.setText("الاحتفاظ بالمحدد");
        actions.addView(selected, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(all, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(keep, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("إزالة إشعارات Toast")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        Runnable startSelected = () -> {
            List<ToastRemovalEngine.Hit> chosen = checked(boxes);
            runPatch(activity, apk, chosen, ToastRemovalEngine.Mode.SELECTED,
                    sign.isChecked(), dialog, onDone);
        };
        selected.setOnClickListener(v -> startSelected.run());
        all.setOnClickListener(v -> runPatch(activity, apk, new ArrayList<>(),
                ToastRemovalEngine.Mode.ALL, sign.isChecked(), dialog, onDone));
        keep.setOnClickListener(v -> runPatch(activity, apk, checked(boxes),
                ToastRemovalEngine.Mode.KEEP_SELECTED, sign.isChecked(), dialog, onDone));
    }

    private static List<ToastRemovalEngine.Hit> checked(List<MaterialCheckBox> boxes) {
        List<ToastRemovalEngine.Hit> out = new ArrayList<>();
        for (MaterialCheckBox box : boxes) {
            if (box.isChecked() && box.getTag() instanceof ToastRemovalEngine.Hit hit) out.add(hit);
        }
        return out;
    }

    private static void runPatch(Activity activity, File apk, List<ToastRemovalEngine.Hit> chosen,
                                 ToastRemovalEngine.Mode mode, boolean sign,
                                 androidx.appcompat.app.AlertDialog dialog, Runnable onDone) {
        if (mode == ToastRemovalEngine.Mode.SELECTED && chosen.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setMessage("حدد رسالة واحدة على الأقل.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (sign && activity instanceof AppCompatActivity) {
            SignWrapper.requireAuth((AppCompatActivity) activity, wrapper ->
                    patch(activity, apk, chosen, mode, wrapper, dialog, onDone));
        } else {
            patch(activity, apk, chosen, mode, null, dialog, onDone);
        }
    }

    private static void patch(Activity activity, File apk, List<ToastRemovalEngine.Hit> chosen,
                              ToastRemovalEngine.Mode mode, SignWrapper signer,
                              androidx.appcompat.app.AlertDialog dialog, Runnable onDone) {
        ProgressManager pm = new ProgressManager(activity, false).show();
        AppExecutors.io().execute(() -> {
            try {
                ToastRemovalEngine.PatchReport result = ToastRemovalEngine.remove(
                        activity, apk, chosen, mode, pm.getLogger());
                if (signer != null && result.output != null && !result.output.isEmpty()) {
                    signer.signApk(new File(result.output));
                    result.signed = true;
                }
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    dialog.dismiss();
                    if (onDone != null) onDone.run();
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                            .setTitle(result.errors.isEmpty() ? "اكتملت إزالة Toast" : "تعذر الإزالة")
                            .setMessage(result.summary())
                            .setPositiveButton(android.R.string.ok, null);
                    if (result.backup != null && !result.backup.isEmpty()
                            && new File(result.backup).isFile()) {
                        builder.setNeutralButton("استعادة النسخة", (d, w) -> {
                            try {
                                File restored = ToastRemovalEngine.restore(new File(result.backup), apk);
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
                            .setTitle("خطأ في إزالة Toast")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }
}
