package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emits real Android artefacts for a spec: a layout XML file, a shape
 * drawable and a complete Java dialog class with events, variables, logic,
 * lifecycle and automation. The same spec also drives the APK injector.
 */
public final class DsCodeGen {

    private DsCodeGen() {
    }

    public static String className(DsSpec spec) {
        String id = spec == null ? "dialog" : DsValidator.sanitizeId(spec.id);
        if (id.isEmpty()) id = "dialog";
        StringBuilder sb = new StringBuilder("MpDialog_");
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String layoutXml(DsSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n");
        sb.append("    android:layout_width=\"match_parent\"\n");
        sb.append("    android:layout_height=\"wrap_content\"\n");
        sb.append("    android:orientation=\"vertical\"\n");
        sb.append("    android:padding=\"").append(spec.paddingDp).append("dp\"\n");
        sb.append("    android:background=\"@drawable/mp_bg_").append(safeId(spec)).append("\">\n\n");
        if (!DsSpec.empty(spec.title)) {
            sb.append("    <TextView\n");
            sb.append("        android:id=\"@+id/mp_title\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:text=\"").append(xml(spec.title)).append("\"\n");
            sb.append("        android:textColor=\"#").append(hex(spec.titleColor == 0 ? 0xFF1B1B1F : spec.titleColor)).append("\"\n");
            sb.append("        android:textSize=\"").append(num(spec.titleSizeSp)).append("sp\"\n");
            sb.append("        android:textStyle=\"bold\" />\n\n");
        }
        if (!DsSpec.empty(spec.message)) {
            sb.append("    <TextView\n");
            sb.append("        android:id=\"@+id/mp_message\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"6dp\"\n");
            sb.append("        android:text=\"").append(xml(spec.message)).append("\"\n");
            sb.append("        android:textColor=\"#").append(hex(spec.msgColor == 0 ? 0xFF44464F : spec.msgColor)).append("\"\n");
            sb.append("        android:textSize=\"").append(num(spec.msgSizeSp)).append("sp\" />\n\n");
        }
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            sb.append(widgetXml(w, spec));
        }
        sb.append("</LinearLayout>\n");
        return sb.toString();
    }

    private static String widgetXml(DsSpec.Widget w, DsSpec spec) {
        String id = safeId(w.id);
        String textColor = "#" + hex(w.textColor == 0
                ? (isLight(spec) ? 0xFFFFFFFF : 0xFF1B1B1F) : w.textColor);
        StringBuilder sb = new StringBuilder();
        if ("button".equals(w.kind)) {
            sb.append("    <com.google.android.material.button.MaterialButton\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"10dp\"\n");
            sb.append("        android:text=\"").append(xml(w.text)).append("\"\n");
            sb.append("        android:textColor=\"").append(textColor).append("\"\n");
            sb.append("        android:textSize=\"").append(num(w.textSizeSp)).append("sp\"\n");
            if (w.btnCornerRadiusDp > 0) {
                sb.append("        app:cornerRadius=\"").append(num(w.btnCornerRadiusDp)).append("dp\"\n");
            }
            sb.append("        />\n\n");
        } else if ("image".equals(w.kind)) {
            sb.append("    <ImageView\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:adjustViewBounds=\"true\"\n");
            sb.append("        android:contentDescription=\"@null\" />\n\n");
        } else if ("input".equals(w.kind)) {
            sb.append("    <com.google.android.material.textfield.TextInputLayout\n");
            sb.append("        android:id=\"@+id/").append(id).append("_box\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:hint=\"").append(xml(DsSpec.empty(w.hint) ? "..." : w.hint)).append("\">\n\n");
            sb.append("        <com.google.android.material.textfield.TextInputEditText\n");
            sb.append("            android:id=\"@+id/").append(id).append("\"\n");
            sb.append("            android:layout_width=\"match_parent\"\n");
            sb.append("            android:layout_height=\"wrap_content\"\n");
            sb.append("            android:text=\"").append(xml(w.text)).append("\"\n");
            sb.append("            android:textSize=\"").append(num(w.textSizeSp)).append("sp\" />\n");
            sb.append("    </com.google.android.material.textfield.TextInputLayout>\n\n");
        } else if ("textarea".equals(w.kind) || "number".equals(w.kind)) {
            sb.append("    <com.google.android.material.textfield.TextInputLayout\n");
            sb.append("        android:id=\"@+id/").append(id).append("_box\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:hint=\"").append(xml(DsSpec.empty(w.hint) ? "..." : w.hint)).append("\">\n\n");
            sb.append("        <com.google.android.material.textfield.TextInputEditText\n");
            sb.append("            android:id=\"@+id/").append(id).append("\"\n");
            sb.append("            android:layout_width=\"match_parent\"\n");
            sb.append("            android:layout_height=\"wrap_content\"\n");
            sb.append("            android:inputType=\"").append("number".equals(w.kind) ? "numberDecimal" : "textMultiLine").append("\"\n");
            sb.append("            android:textSize=\"").append(num(w.textSizeSp)).append("sp\" />\n");
            sb.append("    </com.google.android.material.textfield.TextInputLayout>\n\n");
        } else if ("link".equals(w.kind)) {
            sb.append("    <com.google.android.material.button.MaterialButton\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:text=\"").append(xml(w.text)).append("\"\n");
            sb.append("        android:textSize=\"").append(num(w.textSizeSp)).append("sp\" />\n\n");
        } else if ("search".equals(w.kind)) {
            sb.append("    <AutoCompleteTextView\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:hint=\"").append(xml(DsSpec.empty(w.hint) ? "..." : w.hint)).append("\" />\n\n");
        } else if ("rating".equals(w.kind)) {
            sb.append("    <RatingBar\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"wrap_content\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:numStars=\"5\"\n");
            sb.append("        android:stepSize=\"1\" />\n\n");
        } else if ("checkbox".equals(w.kind)) {
            sb.append("    <CheckBox\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:checked=\"").append(w.checked).append("\"\n");
            sb.append("        android:text=\"").append(xml(w.text)).append("\"\n");
            sb.append("        android:textColor=\"").append(textColor).append("\" />\n\n");
        } else if ("spinner".equals(w.kind) || "list".equals(w.kind)) {
            sb.append("    <Spinner\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\" />\n\n");
        } else if ("divider".equals(w.kind)) {
            sb.append("    <View\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"1dp\"\n");
            sb.append("        android:layout_marginTop=\"10dp\"\n");
            sb.append("        android:background=\"").append(textColor).append("\" />\n\n");
        } else {
            sb.append("    <TextView\n");
            sb.append("        android:id=\"@+id/").append(id).append("\"\n");
            sb.append("        android:layout_width=\"match_parent\"\n");
            sb.append("        android:layout_height=\"wrap_content\"\n");
            sb.append("        android:layout_marginTop=\"8dp\"\n");
            sb.append("        android:text=\"").append(xml(w.text)).append("\"\n");
            sb.append("        android:textColor=\"").append(textColor).append("\"\n");
            sb.append("        android:textSize=\"").append(num(w.textSizeSp)).append("sp\" />\n\n");
        }
        return sb.toString();
    }

    public static String drawableXml(DsSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:shape=\"rectangle\">\n");
        if (spec.gradient) {
            sb.append("    <gradient\n");
            sb.append("        android:startColor=\"#").append(hex(spec.bgColor1)).append("\"\n");
            sb.append("        android:endColor=\"#").append(hex(spec.bgColor2)).append("\"\n");
            sb.append("        android:angle=\"").append(gradientAngle(spec.orientation)).append("\" />\n");
        } else {
            sb.append("    <solid android:color=\"#").append(hex(spec.bgColor1)).append("\" />\n");
        }
        sb.append("    <corners android:radius=\"").append(num(spec.cornerRadiusDp)).append("dp\" />\n");
        if (spec.borderWidthDp > 0) {
            sb.append("    <stroke android:width=\"").append(num(spec.borderWidthDp)).append("dp\"\n");
            sb.append("        android:color=\"#").append(hex(spec.borderColor)).append("\" />\n");
        }
        sb.append("</shape>\n");
        return sb.toString();
    }

    public static String javaSource(DsSpec spec) {
        return javaSource(spec, new LinkedHashMap<>());
    }

    public static String javaSource(DsSpec spec, Map<String, DsSpec> library) {
        DsSpec s = DsVars.resolveStatic(spec);
        String cls = className(s);
        String pkg = DsSpec.empty(s.packageName) ? "mp.dialog.generated" : s.packageName;
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import android.app.Activity;\n");
        sb.append("import android.app.AlertDialog;\n");
        sb.append("import android.content.ClipData;\n");
        sb.append("import android.content.ClipboardManager;\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.content.DialogInterface;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.content.SharedPreferences;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.graphics.drawable.GradientDrawable;\n");
        sb.append("import android.net.Uri;\n");
        sb.append("import android.os.Handler;\n");
        sb.append("import android.os.Looper;\n");
        sb.append("import android.text.Editable;\n");
        sb.append("import android.text.TextWatcher;\n");
        sb.append("import android.util.Base64;\n");
        sb.append("import android.view.View;\n");
        sb.append("import android.view.ViewGroup;\n");
        sb.append("import android.view.Window;\n");
        sb.append("import android.view.WindowManager;\n");
        sb.append("import android.widget.ArrayAdapter;\n");
        sb.append("import android.widget.Button;\n");
        sb.append("import android.widget.CheckBox;\n");
        sb.append("import android.widget.EditText;\n");
        sb.append("import android.widget.ImageView;\n");
        sb.append("import android.widget.LinearLayout;\n");
        sb.append("import android.widget.Spinner;\n");
        sb.append("import android.widget.TextView;\n");
        sb.append("import android.widget.Toast;\n");
        sb.append("import android.graphics.Bitmap;\n");
        sb.append("import android.graphics.BitmapFactory;\n");
        sb.append("import java.text.SimpleDateFormat;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.Date;\n");
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Locale;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("/** Generated by MP Manager Dialog Studio. */\n");
        sb.append("public final class ").append(cls).append(" {\n\n");
        sb.append("    private static final String PREFS = \"mp_dialog_studio\";\n");
        sb.append("    private static boolean showing;\n");
        sb.append("    private final Context context;\n");
        sb.append("    private final Map<String, String> vars = new HashMap<>();\n");
        sb.append("    private final Map<String, View> views = new HashMap<>();\n");
        sb.append("    private final Handler handler = new Handler(Looper.getMainLooper());\n");
        sb.append("    private AlertDialog dialog;\n");
        sb.append("    private boolean inputChanged;\n\n");
        sb.append("    public ").append(cls).append("(Context context) {\n");
        sb.append("        this.context = context;\n");
        sb.append("        initVars();\n");
        sb.append("    }\n\n");
        sb.append("    public static void show(Context context) {\n");
        sb.append("        new ").append(cls).append("(context).show();\n");
        sb.append("    }\n\n");
        sb.append("    public static void showDelayed(final Context context, long delayMs) {\n");
        sb.append("        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {\n");
        sb.append("            public void run() { show(context); }\n");
        sb.append("        }, Math.max(0L, delayMs));\n");
        sb.append("    }\n\n");
        if (!s.activities.isEmpty() || hasAutomation(s, "ACTIVITY_OPEN")) {
            sb.append("    public static boolean shouldShowFor(String activity) {\n");
            sb.append("        if (activity == null) return false;\n");
            for (String a : s.activities) {
                sb.append("        if (activity.endsWith(\"").append(java(a)).append("\")) return true;\n");
            }
            for (DsSpec.Automation a : s.automations) {
                if (a != null && a.enabled && "ACTIVITY_OPEN".equals(a.trigger) && !DsSpec.empty(a.activity)) {
                    sb.append("        if (activity.endsWith(\"").append(java(a.activity)).append("\")) return true;\n");
                }
            }
            sb.append("        return false;\n");
            sb.append("    }\n\n");
        }
        sb.append("    private void initVars() {\n");
        sb.append("        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);\n");
        sb.append("        vars.put(\"date\", new SimpleDateFormat(\"yyyy-MM-dd\", Locale.US).format(new Date()));\n");
        sb.append("        vars.put(\"time\", new SimpleDateFormat(\"HH:mm\", Locale.US).format(new Date()));\n");
        sb.append("        vars.put(\"datetime\", new SimpleDateFormat(\"yyyy-MM-dd HH:mm\", Locale.US).format(new Date()));\n");
        sb.append("        vars.put(\"app_package\", context.getPackageName());\n");
        for (DsSpec.Variable v : s.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            String init = v.value == null ? "" : v.value;
            if (v.persistent) {
                sb.append("        vars.put(\"").append(java(v.name)).append("\", prefs.getString(\"")
                        .append(java(s.id + "." + v.name)).append("\", \"").append(java(init)).append("\"));\n");
            } else if ("date".equals(v.source)) {
                sb.append("        vars.put(\"").append(java(v.name))
                        .append("\", new SimpleDateFormat(\"yyyy-MM-dd\", Locale.US).format(new Date()));\n");
            } else if ("time".equals(v.source)) {
                sb.append("        vars.put(\"").append(java(v.name))
                        .append("\", new SimpleDateFormat(\"HH:mm\", Locale.US).format(new Date()));\n");
            } else if (!"input".equals(v.source)) {
                sb.append("        vars.put(\"").append(java(v.name)).append("\", \"")
                        .append(java(init)).append("\");\n");
            }
        }
        sb.append("    }\n\n");
        sb.append("    private String render(String text) {\n");
        sb.append("        if (text == null) return \"\";\n");
        sb.append("        String out = text;\n");
        sb.append("        for (Map.Entry<String, String> e : vars.entrySet()) {\n");
        sb.append("            String value = e.getValue() == null ? \"\" : e.getValue();\n");
        sb.append("            out = out.replace(\"{{\" + e.getKey() + \"}}\", value);\n");
        sb.append("            out = out.replace(\"${\" + e.getKey() + \"}\", value);\n");
        sb.append("        }\n");
        sb.append("        return out;\n");
        sb.append("    }\n\n");
        sb.append("    public void show() {\n");
        if (s.preventDoubleOpen) {
            sb.append("        if (showing) return;\n");
            sb.append("        showing = true;\n");
        }
        sb.append("        final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);\n");
        if (s.showOnce) {
            sb.append("        if (prefs.getBoolean(\"").append(java(s.id)).append(".shown_once\", false)) { showing = false; return; }\n");
        }
        if (s.dontShowAgain) {
            sb.append("        if (prefs.getBoolean(\"").append(java(s.id)).append(".dont_show\", false)) { showing = false; return; }\n");
        }
        sb.append("        LinearLayout root = new LinearLayout(context);\n");
        sb.append("        root.setOrientation(LinearLayout.VERTICAL);\n");
        sb.append("        root.setPadding(dp(").append(numF(s.paddingDp)).append("), dp(").append(numF(s.paddingDp))
                .append("), dp(").append(numF(s.paddingDp)).append("), dp(").append(numF(s.paddingDp)).append("));\n");
        sb.append("        GradientDrawable bg = new GradientDrawable();\n");
        if (s.gradient) {
            sb.append("        bg.setColors(new int[]{0x").append(hex(s.bgColor1)).append(", 0x")
                    .append(hex(s.bgColor2)).append("});\n");
            sb.append("        bg.setOrientation(GradientDrawable.Orientation.")
                    .append(orientationName(s.orientation)).append(");\n");
        } else {
            sb.append("        bg.setColor(0x").append(hex(s.bgColor1)).append(");\n");
        }
        sb.append("        bg.setCornerRadius(dp(").append(numF(s.cornerRadiusDp)).append("));\n");
        if (s.borderWidthDp > 0) {
            sb.append("        bg.setStroke(dp(").append(numF(s.borderWidthDp)).append("), 0x")
                    .append(hex(s.borderColor)).append(");\n");
        }
        sb.append("        root.setBackground(bg);\n");
        if (s.shadowDp > 0) {
            sb.append("        root.setElevation(dp(").append(num(s.shadowDp)).append("));\n");
        }
        if (!DsSpec.empty(s.title)) {
            sb.append("        TextView title = new TextView(context);\n");
            sb.append("        title.setText(render(\"").append(java(s.title)).append("\"));\n");
            sb.append("        title.setTextSize(").append(num(s.titleSizeSp)).append("f);\n");
            sb.append("        title.setTextColor(0x").append(hex(s.titleColor == 0 ? 0xFF1B1B1F : s.titleColor)).append(");\n");
            sb.append("        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);\n");
            sb.append("        root.addView(title);\n");
        }
        if (!DsSpec.empty(s.message)) {
            sb.append("        TextView message = new TextView(context);\n");
            sb.append("        message.setText(render(\"").append(java(s.message)).append("\"));\n");
            sb.append("        message.setTextSize(").append(num(s.msgSizeSp)).append("f);\n");
            sb.append("        message.setTextColor(0x").append(hex(s.msgColor == 0 ? 0xFF44464F : s.msgColor)).append(");\n");
            sb.append("        message.setPadding(0, dp(6f), 0, 0);\n");
            sb.append("        root.addView(message);\n");
        }
        for (DsSpec.Widget w : s.widgets) {
            if (w == null) continue;
            sb.append(widgetJava(w, s, library));
        }
        boolean hasButtonWidget = false;
        for (DsSpec.Widget cw : s.widgets) {
            if (cw != null && "button".equals(cw.kind)) { hasButtonWidget = true; break; }
        }
        if (s.dontShowAgain) {
            sb.append("        final CheckBox dontShow = new CheckBox(context);\n");
            sb.append("        dontShow.setText(\"لا تظهر مرة أخرى\");\n");
            sb.append("        root.addView(dontShow);\n");
        }
        sb.append("        AlertDialog.Builder b = new AlertDialog.Builder(context);\n");
        sb.append("        b.setView(root);\n");
        sb.append("        if (!").append(s.cancelable).append(") b.setCancelable(false);\n");
        if (!hasButtonWidget && !DsSpec.empty(s.positive)) {
            sb.append("        b.setPositiveButton(render(\"").append(java(s.positive))
                    .append("\"), new DialogInterface.OnClickListener() {\n");
            sb.append("            public void onClick(DialogInterface d, int which) { d.dismiss(); ");
            sb.append(hasEvent(s, "@dialog", DsEvents.CONFIRM) ? "onDialogConfirm();" : "");
            sb.append(" }\n        });\n");
        }
        if (!hasButtonWidget && !DsSpec.empty(s.negative)) {
            sb.append("        b.setNegativeButton(render(\"").append(java(s.negative))
                    .append("\"), new DialogInterface.OnClickListener() {\n");
            sb.append("            public void onClick(DialogInterface d, int which) { d.dismiss(); ");
            sb.append(hasEvent(s, "@dialog", DsEvents.CANCEL) ? "onDialogCancel();" : "");
            sb.append(" }\n        });\n");
        }
        if (!hasButtonWidget && !DsSpec.empty(s.neutral)) {
            sb.append("        b.setNeutralButton(render(\"").append(java(s.neutral))
                    .append("\"), null);\n");
        }
        sb.append("        dialog = b.create();\n");
        sb.append("        dialog.setOnShowListener(new DialogInterface.OnShowListener() {\n");
        sb.append("            public void onShow(DialogInterface d) {\n");
        sb.append("                Window win = dialog.getWindow();\n");
        sb.append("                if (win != null) {\n");
        sb.append("                    WindowManager.LayoutParams lp = win.getAttributes();\n");
        sb.append("                    lp.width = (int) (context.getResources().getDisplayMetrics().widthPixels * ")
                .append(num(s.widthPercent / 100f)).append("f);\n");
        if (s.maxWidthDp > 0) {
            sb.append("                    lp.width = Math.min(lp.width, dp(")
                    .append(num(s.maxWidthDp)).append("));\n");
        }
        sb.append("                    lp.height = WindowManager.LayoutParams.WRAP_CONTENT;\n");
        sb.append("                    lp.dimAmount = ").append(num(s.dimAmount)).append("f;\n");
        sb.append("                    win.setAttributes(lp);\n");
        sb.append("                    win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);\n");
        sb.append("                    win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));\n");
        if ("fade_scale".equals(s.enterAnimation)) {
            sb.append("                    win.getDecorView().setAlpha(0f);\n");
            sb.append("                    win.getDecorView().setScaleX(0.94f);\n");
            sb.append("                    win.getDecorView().setScaleY(0.94f);\n");
            sb.append("                    win.getDecorView().animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();\n");
        } else if ("slide_up".equals(s.enterAnimation)) {
            sb.append("                    win.getDecorView().setTranslationY(dp(32));\n");
            sb.append("                    win.getDecorView().setAlpha(0f);\n");
            sb.append("                    win.getDecorView().animate().translationY(0f).alpha(1f).setDuration(200).start();\n");
        } else if ("fade".equals(s.enterAnimation)) {
            sb.append("                    win.getDecorView().setAlpha(0f);\n");
            sb.append("                    win.getDecorView().animate().alpha(1f).setDuration(160).start();\n");
        }
        sb.append("                }\n");
        sb.append("                applyVisibility();\n");
        if (hasEvent(s, "@dialog", DsEvents.OPEN)) sb.append("                onDialogOpen();\n");
        sb.append("            }\n        });\n");
        sb.append("        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {\n");
        sb.append("            public void onDismiss(DialogInterface d) {\n");
        if (s.preventDoubleOpen) sb.append("                showing = false;\n");
        if (s.saveInputState) sb.append("                saveInputs(prefs);\n");
        if (s.dontShowAgain) sb.append("                if (dontShow.isChecked()) prefs.edit().putBoolean(\"")
                .append(java(s.id)).append(".dont_show\", true).apply();\n");
        if (hasEvent(s, "@dialog", DsEvents.CLOSE)) sb.append("                onDialogClose();\n");
        sb.append("            }\n        });\n");
        if (s.showOnce) {
            sb.append("        prefs.edit().putBoolean(\"").append(java(s.id)).append(".shown_once\", true).apply();\n");
        }
        sb.append("        dialog.show();\n");
        if (hasEvent(s, "@dialog", DsEvents.TIMEOUT)) {
            DsSpec.Event timeout = s.findEvent("@dialog", DsEvents.TIMEOUT);
            int ms = timeout == null || timeout.timeoutMs <= 0 ? 5000 : timeout.timeoutMs;
            sb.append("        handler.postDelayed(new Runnable() { public void run() { if (dialog != null && dialog.isShowing()) onDialogTimeout(); } }, ")
                    .append(ms).append("L);\n");
        }
        sb.append("    }\n\n");
        sb.append("    private String valueOf(String name) {\n");
        sb.append("        if (name != null && name.startsWith(\"@\")) return inputValue(name.substring(1));\n");
        sb.append("        String value = vars.get(name);\n");
        sb.append("        return value == null ? \"\" : value;\n");
        sb.append("    }\n\n");
        sb.append("    private String inputValue(String id) {\n");
        sb.append("        View view = views.get(id);\n");
        sb.append("        if (view instanceof EditText) return ((EditText) view).getText().toString();\n");
        sb.append("        if (view instanceof CheckBox) return String.valueOf(((CheckBox) view).isChecked());\n");
        sb.append("        if (view instanceof Spinner) { Object item = ((Spinner) view).getSelectedItem(); return item == null ? \"\" : item.toString(); }\n");
        sb.append("        return \"\";\n");
        sb.append("    }\n\n");
        sb.append("    private int dp(float value) {\n");
        sb.append("        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);\n");
        sb.append("    }\n\n");
        sb.append("    private void saveInputs(SharedPreferences prefs) {\n");
        sb.append("        SharedPreferences.Editor e = prefs.edit();\n");
        for (DsSpec.Widget w : s.widgets) {
            if (w == null || !"input".equals(w.kind)) continue;
            sb.append("        { View v = views.get(\"").append(java(w.id)).append("\"); if (v instanceof EditText) e.putString(\"")
                    .append(java(s.id + "." + w.id)).append("\", ((EditText) v).getText().toString()); }\n");
        }
        sb.append("        e.apply();\n");
        sb.append("    }\n\n");
        sb.append("    private void applyVisibility() {\n");
        for (DsSpec.Widget w : s.widgets) {
            if (w == null || DsSpec.empty(w.visibleIf)) continue;
            sb.append("        { View v = views.get(\"").append(java(w.id)).append("\"); if (v != null) v.setVisibility(")
                    .append(javaCondition(w.visibleIf, "v")).append(" ? View.VISIBLE : View.GONE); }\n");
        }
        sb.append("    }\n\n");
        for (DsSpec.Event e : s.events) {
            if (e == null || !e.enabled || e.actions == null || e.actions.isEmpty()) continue;
            sb.append(eventMethod(s, e, library));
        }
        sb.append("    private void updateLogic(String sourceId) {\n");
        for (DsSpec.Logic l : s.logic) {
            if (l == null) continue;
            String from = !DsSpec.empty(l.fromWidget) ? l.fromWidget : l.fromVar;
            if (DsSpec.empty(from)) continue;
            sb.append("        if (\"").append(java(from)).append("\".equals(sourceId) || sourceId == null) {\n");
            sb.append("            View target = views.get(\"").append(java(l.toWidget)).append("\");\n");
            sb.append("            if (target instanceof TextView) ((TextView) target).setText(render(\"")
                    .append(java(l.template)).append("\"));\n");
            sb.append("        }\n");
        }
        sb.append("    }\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String widgetJava(DsSpec.Widget w, DsSpec s, Map<String, DsSpec> library) {
        String id = java(w.id);
        StringBuilder sb = new StringBuilder();
        if ("button".equals(w.kind) || "link".equals(w.kind)) {
            sb.append("        Button btn_").append(safeId(w.id)).append(" = new Button(context);\n");
            sb.append("        btn_").append(safeId(w.id)).append(".setText(render(\"").append(java(w.text)).append("\"));\n");
            sb.append("        btn_").append(safeId(w.id)).append(".setTextSize(").append(num(w.textSizeSp)).append("f);\n");
            if (w.textColor != 0) {
                sb.append("        btn_").append(safeId(w.id)).append(".setTextColor(0x").append(hex(w.textColor)).append(");\n");
            }
            if (w.btnBg != 0 || w.btnCornerRadiusDp > 0 || w.btnBorderWidthDp > 0) {
                sb.append("        { GradientDrawable bgb = new GradientDrawable();\n");
                if (w.btnBg2 != 0) {
                    sb.append("          bgb.setColors(new int[]{0x").append(hex(w.btnBg)).append(", 0x").append(hex(w.btnBg2)).append("});\n");
                } else if (w.btnBg != 0) {
                    sb.append("          bgb.setColor(0x").append(hex(w.btnBg)).append(");\n");
                }
                sb.append("          bgb.setCornerRadius(dp(").append(numF(w.btnCornerRadiusDp)).append("));\n");
                if (w.btnBorderWidthDp > 0) {
                    sb.append("          bgb.setStroke(dp(").append(numF(w.btnBorderWidthDp)).append("), 0x")
                            .append(hex(w.btnBorderColor == -1 ? 0xFFFFFFFF : w.btnBorderColor)).append(");\n");
                }
                sb.append("          btn_").append(safeId(w.id)).append(".setBackground(bgb); }\n");
            }
            if ("link".equals(w.kind) && !DsSpec.empty(w.url)) {
                sb.append("        btn_").append(safeId(w.id))
                        .append(".setOnClickListener(new View.OnClickListener() { public void onClick(View v) { try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(render(\"")
                        .append(java(w.url)).append("\")))); } catch (Exception ignored) {} } });\n");
            }
            sb.append("        views.put(\"").append(id).append("\", btn_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(btn_").append(safeId(w.id)).append(", new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n");
            String click = eventMethodName(w.id, DsEvents.CLICK);
            if (hasEvent(s, w.id, DsEvents.CLICK)) {
                sb.append("        btn_").append(safeId(w.id)).append(".setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ")
                        .append(click).append("(); } });\n");
            }
            if (hasEvent(s, w.id, DsEvents.LONG_CLICK)) {
                sb.append("        btn_").append(safeId(w.id)).append(".setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { ")
                        .append(eventMethodName(w.id, DsEvents.LONG_CLICK)).append("(); return true; } });\n");
            }
            if (hasEvent(s, w.id, DsEvents.DOUBLE_CLICK)) {
                sb.append("        btn_").append(safeId(w.id)).append(".setOnClickListener(new View.OnClickListener() {\n");
                sb.append("            private long last;\n");
                sb.append("            public void onClick(View v) { long now = android.os.SystemClock.elapsedRealtime(); if (now - last < 300L) { last = 0L; ")
                        .append(eventMethodName(w.id, DsEvents.DOUBLE_CLICK)).append("(); } else { last = now; } }\n");
                sb.append("        });\n");
            }
        } else if ("image".equals(w.kind)) {
            sb.append("        ImageView img_").append(safeId(w.id)).append(" = new ImageView(context);\n");
            sb.append("        img_").append(safeId(w.id)).append(".setAdjustViewBounds(true);\n");
            sb.append("        try { byte[] data = Base64.decode(\"").append(java(w.imageB64))
                    .append("\", Base64.DEFAULT); Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length); img_")
                    .append(safeId(w.id)).append(".setImageBitmap(bmp); } catch (Exception ignored) {}\n");
            sb.append("        views.put(\"").append(id).append("\", img_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(img_").append(safeId(w.id)).append(", new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n");
        } else if ("input".equals(w.kind) || "textarea".equals(w.kind) || "number".equals(w.kind)) {
            sb.append("        EditText input_").append(safeId(w.id)).append(" = new EditText(context);\n");
            sb.append("        input_").append(safeId(w.id)).append(".setHint(render(\"").append(java(w.hint)).append("\"));\n");
            sb.append("        input_").append(safeId(w.id)).append(".setText(render(\"").append(java(w.text)).append("\"));\n");
            sb.append("        input_").append(safeId(w.id)).append(".setTextSize(").append(num(w.textSizeSp)).append("f);\n");
            if (w.textColor != 0) {
                sb.append("        input_").append(safeId(w.id)).append(".setTextColor(0x").append(hex(w.textColor)).append(");\n");
            }
            if (s.saveInputState) {
                sb.append("        input_").append(safeId(w.id)).append(".setText(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(\"")
                        .append(java(s.id + "." + w.id)).append("\", render(\"").append(java(w.text)).append("\")));\n");
            }
            sb.append("        views.put(\"").append(id).append("\", input_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(input_").append(safeId(w.id)).append(", new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n");
            if (!DsSpec.empty(w.variable) || hasEvent(s, w.id, DsEvents.INPUT_CHANGE)) {
                sb.append("        input_").append(safeId(w.id)).append(".addTextChangedListener(new TextWatcher() {\n");
                sb.append("            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}\n");
                sb.append("            public void onTextChanged(CharSequence s, int a, int b, int c) {}\n");
                sb.append("            public void afterTextChanged(Editable e) { inputChanged = true;");
                if (!DsSpec.empty(w.variable)) {
                    sb.append(" vars.put(\"").append(java(w.variable)).append("\", e.toString());");
                }
                sb.append(" updateLogic(\"").append(id).append("\"); applyVisibility();");
                if (hasEvent(s, w.id, DsEvents.INPUT_CHANGE)) {
                    sb.append(" ").append(eventMethodName(w.id, DsEvents.INPUT_CHANGE)).append("();");
                }
                sb.append(" }\n        });\n");
            }
        } else if ("checkbox".equals(w.kind)) {
            sb.append("        CheckBox check_").append(safeId(w.id)).append(" = new CheckBox(context);\n");
            sb.append("        check_").append(safeId(w.id)).append(".setText(render(\"").append(java(w.text)).append("\"));\n");
            sb.append("        check_").append(safeId(w.id)).append(".setChecked(").append(w.checked).append(");\n");
            sb.append("        views.put(\"").append(id).append("\", check_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(check_").append(safeId(w.id)).append(");\n");
            if (!DsSpec.empty(w.variable)) {
                sb.append("        check_").append(safeId(w.id)).append(".setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {\n");
                sb.append("            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) { vars.put(\"")
                        .append(java(w.variable)).append("\", String.valueOf(checked)); applyVisibility(); }\n        });\n");
            }
        } else if ("spinner".equals(w.kind) || "list".equals(w.kind)
                || "search".equals(w.kind) || "radio".equals(w.kind)
                || "multiselect".equals(w.kind) || "rating".equals(w.kind)) {
            sb.append("        Spinner spin_").append(safeId(w.id)).append(" = new Spinner(context);\n");
            sb.append("        ArrayAdapter<String> ad_").append(safeId(w.id)).append(" = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, new String[]{");
            if (w.options != null) {
                for (int i = 0; i < w.options.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("\"").append(java(w.options.get(i))).append("\"");
                }
            }
            sb.append("});\n");
            sb.append("        spin_").append(safeId(w.id)).append(".setAdapter(ad_").append(safeId(w.id)).append(");\n");
            sb.append("        views.put(\"").append(id).append("\", spin_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(spin_").append(safeId(w.id)).append(");\n");
        } else if ("divider".equals(w.kind)) {
            sb.append("        View div_").append(safeId(w.id)).append(" = new View(context);\n");
            sb.append("        div_").append(safeId(w.id)).append(".setBackgroundColor(0x").append(hex(w.textColor == 0 ? 0x33000000 : w.textColor)).append(");\n");
            sb.append("        root.addView(div_").append(safeId(w.id)).append(", new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)));\n");
        } else {
            sb.append("        TextView txt_").append(safeId(w.id)).append(" = new TextView(context);\n");
            sb.append("        txt_").append(safeId(w.id)).append(".setText(render(\"").append(java(w.text)).append("\"));\n");
            sb.append("        txt_").append(safeId(w.id)).append(".setTextSize(").append(num(w.textSizeSp)).append("f);\n");
            if (w.textColor != 0) {
                sb.append("        txt_").append(safeId(w.id)).append(".setTextColor(0x").append(hex(w.textColor)).append(");\n");
            }
            sb.append("        views.put(\"").append(id).append("\", txt_").append(safeId(w.id)).append(");\n");
            sb.append("        root.addView(txt_").append(safeId(w.id)).append(");\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String eventMethod(DsSpec s, DsSpec.Event e, Map<String, DsSpec> library) {
        String name = eventMethodName(e.widgetId, e.event);
        StringBuilder sb = new StringBuilder();
        sb.append("    private void ").append(name).append("() {\n");
        if (!DsSpec.empty(e.condition)) {
            sb.append("        if (!(").append(javaCondition(e.condition, null)).append(")) return;\n");
        }
        sb.append(actionsJava(e.actions, 0, "        ", library));
        sb.append("    }\n\n");
        return sb.toString();
    }

    private static String actionsJava(List<DsSpec.Action> actions, int from, String indent,
                                      Map<String, DsSpec> library) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < actions.size(); i++) {
            DsSpec.Action a = actions.get(i);
            if (a == null) continue;
            String type = a.type == null ? "" : a.type;
            String baseIndent = indent;
            boolean guarded = !DsSpec.empty(a.condition)
                    && !DsEvents.A_IF_VAR.equals(type)
                    && !DsEvents.A_IF_INPUT.equals(type)
                    && !DsEvents.A_STOP.equals(type)
                    && !DsEvents.A_DELAY.equals(type);
            if (guarded) {
                sb.append(indent).append("if (").append(javaCondition(a.condition, null)).append(") {\n");
                indent += "    ";
            }
            switch (type) {
                case DsEvents.A_DISMISS:
                    sb.append(indent).append("if (dialog != null) dialog.dismiss();\n");
                    break;
                case DsEvents.A_URL:
                    sb.append(indent).append("try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(render(\"")
                            .append(java(a.url)).append("\")))); } catch (Exception ignored) {}\n");
                    break;
                case DsEvents.A_TOAST:
                    sb.append(indent).append("Toast.makeText(context, render(\"").append(java(a.value))
                            .append("\"), Toast.").append(a.toastLong ? "LENGTH_LONG" : "LENGTH_SHORT").append(").show();\n");
                    break;
                case DsEvents.A_COPY:
                    sb.append(indent).append("try { ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); ")
                            .append("cm.setPrimaryClip(ClipData.newPlainText(\"mp\", render(\"").append(java(a.value))
                            .append("\"))); } catch (Exception ignored) {}\n");
                    break;
                case DsEvents.A_SET_TEXT:
                    if (DsSpec.empty(a.target)) {
                        sb.append(indent).append("// SET_TEXT بلا عنصر هدف\n");
                        break;
                    }
                    sb.append(indent).append("{ View target = views.get(\"").append(java(a.target))
                            .append("\"); if (target instanceof TextView) ((TextView) target).setText(render(\"")
                            .append(java(a.value)).append("\")); }\n");
                    break;
                case DsEvents.A_SET_VAR:
                    sb.append(indent).append("vars.put(\"").append(java(a.value)).append("\", render(\"")
                            .append(java(a.value2)).append("\")); applyVisibility();\n");
                    break;
                case DsEvents.A_CALL:
                    if (DsSpec.empty(a.methodClass) || DsSpec.empty(a.methodName)) {
                        sb.append(indent).append("// CALL بلا دالة محددة\n");
                        break;
                    }
                    sb.append(indent).append("try { ").append(a.methodClass).append(".").append(a.methodName)
                            .append("(").append(callArg(a)).append("); } catch (Exception ignored) {}\n");
                    break;
                case DsEvents.A_IF_VAR:
                    sb.append(indent).append("if (!(").append(javaCondition(a.condition, null)).append(")) return;\n");
                    break;
                case DsEvents.A_IF_INPUT:
                    sb.append(indent).append("if (!(").append(javaCondition("input:" + a.target + " " + a.condition, null))
                            .append(")) return;\n");
                    break;
                case DsEvents.A_STOP:
                    sb.append(indent).append("return;\n");
                    break;
                case DsEvents.A_SHOW_DIALOG: {
                    DsSpec target = library == null ? null : library.get(a.target);
                    if (target != null) {
                        sb.append(indent).append("new ").append(className(target)).append("(context).show();\n");
                    } else {
                        sb.append(indent).append("// SHOW_DIALOG: ").append(java(a.target))
                                .append(" (أضف الدايلوك إلى المكتبة لتوليد الاستدعاء)\n");
                    }
                    break;
                }
                case DsEvents.A_DELAY:
                    sb.append(indent).append("handler.postDelayed(new Runnable() { public void run() {\n");
                    sb.append(actionsJava(actions, i + 1, indent + "    ", library));
                    sb.append(indent).append("} }, ").append(Math.max(0, a.delayMs)).append("L);\n");
                    sb.append(indent).append("return;\n");
                    return sb.toString();
                default:
                    sb.append(indent).append("// إجراء غير معروف: ").append(java(type)).append("\n");
            }
            if (guarded) {
                indent = baseIndent;
                sb.append(indent).append("}\n");
            }
        }
        return sb.toString();
    }

    private static String callArg(DsSpec.Action a) {
        String type = "";
        int dot = a.methodSignature == null ? -1 : a.methodSignature.indexOf('(');
        if (dot >= 0) {
            int end = a.methodSignature.indexOf(')', dot);
            if (end > dot + 1) type = a.methodSignature.substring(dot + 1, end).trim();
        }
        String arg = a.methodArg == null ? "" : a.methodArg;
        if (type.isEmpty()) return arg.isEmpty() ? "" : "\"" + java(arg) + "\"";
        if ("boolean".equals(type) || "Boolean".equals(type)) {
            return "true".equalsIgnoreCase(arg) ? "true" : "false";
        }
        if ("int".equals(type) || "Integer".equals(type)) return String.valueOf(safeInt(arg));
        if ("long".equals(type) || "Long".equals(type)) return safeLong(arg) + "L";
        if ("float".equals(type) || "Float".equals(type)) return safeFloat(arg) + "f";
        return "\"" + java(arg) + "\"";
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static long safeLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static float safeFloat(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return 0f;
        }
    }

    private static String eventMethodName(String widgetId, String event) {
        if ("@dialog".equals(widgetId)) {
            String e = DsValidator.sanitizeId(event).toLowerCase(Locale.US);
            return "onDialog" + (e.isEmpty() ? "Event" : Character.toUpperCase(e.charAt(0)) + e.substring(1));
        }
        String w = DsValidator.sanitizeId(widgetId);
        if (w.isEmpty()) w = "dialog";
        return "on_" + w + "_" + DsValidator.sanitizeId(event);
    }

    private static boolean hasEvent(DsSpec s, String widgetId, String event) {
        return s.findEvent(widgetId, event) != null;
    }

    private static boolean hasAutomation(DsSpec s, String trigger) {
        for (DsSpec.Automation a : s.automations) {
            if (a != null && a.enabled && trigger.equals(a.trigger)) return true;
        }
        return false;
    }

    private static String javaCondition(String expression, String viewVar) {
        if (DsSpec.empty(expression)) return "true";
        String expr = expression.trim();
        int or = expr.indexOf("||");
        if (or >= 0) {
            return "(" + javaCondition(expr.substring(0, or), viewVar) + " || "
                    + javaCondition(expr.substring(or + 2), viewVar) + ")";
        }
        int and = expr.indexOf("&&");
        if (and >= 0) {
            return "(" + javaCondition(expr.substring(0, and), viewVar) + " && "
                    + javaCondition(expr.substring(and + 2), viewVar) + ")";
        }
        if ("true".equalsIgnoreCase(expr)) return "true";
        if ("false".equalsIgnoreCase(expr)) return "false";
        if (expr.startsWith("!")) return "!(" + javaCondition(expr.substring(1), viewVar) + ")";
        String lower = expr.toLowerCase(Locale.US);
        int eq = expr.indexOf("==");
        int ne = expr.indexOf("!=");
        if ((eq >= 0 || ne >= 0) && !lower.startsWith("input:") && !lower.startsWith("widget:")) {
            boolean equals = eq >= 0;
            int at = equals ? eq : ne;
            String name = expr.substring(0, at).trim();
            String expected = expr.substring(at + 2).trim();
            expected = expected.replaceAll("^[\"']|[\"']$", "");
            return (equals ? "" : "!") + "valueOf(\"" + java(name) + "\").equals(\""
                    + java(expected) + "\")";
        }
        if (lower.endsWith(" notempty") || lower.endsWith(" visible")) {
            String name = expr.substring(0, expr.lastIndexOf(' ')).trim();
            return "!valueOf(\"" + java(name) + "\").isEmpty()";
        }
        if (lower.endsWith(" empty")) {
            String name = expr.substring(0, expr.lastIndexOf(' ')).trim();
            return "valueOf(\"" + java(name) + "\").isEmpty()";
        }
        if (lower.startsWith("input:") || lower.startsWith("widget:")) {
            String body = expr.substring(expr.indexOf(':') + 1).trim();
            int space = body.indexOf(' ');
            String id = space < 0 ? body : body.substring(0, space);
            String op = space < 0 ? "notempty" : body.substring(space + 1).trim();
            String value = "valueOf(\"@" + java(id) + "\")";
            if (op.startsWith("==")) {
                return value + ".equals(\"" + java(op.substring(2).trim()) + "\")";
            }
            if (op.startsWith("!=")) {
                return "!" + value + ".equals(\"" + java(op.substring(2).trim()) + "\")";
            }
            if (op.startsWith("contains ")) {
                return value + ".contains(\"" + java(op.substring(9).trim()) + "\")";
            }
            if ("empty".equals(op)) return value + ".isEmpty()";
            if ("notempty".equals(op) || "visible".equals(op)) return "!" + value + ".isEmpty()";
            return "!" + value + ".isEmpty()";
        }
        return "!valueOf(\"" + java(expr) + "\").isEmpty()";
    }

    private static String valueOfCall(String name) {
        if (name.startsWith("@")) return "inputValue(\"" + java(name.substring(1)) + "\")";
        return "vars.get(\"" + java(name) + "\")";
    }

    private static String orientationName(int orientation) {
        if (orientation == 1) return "LEFT_RIGHT";
        if (orientation == 2) return "TL_BR";
        if (orientation == 3) return "BL_TR";
        return "TOP_BOTTOM";
    }

    private static int gradientAngle(int orientation) {
        if (orientation == 1) return 0;
        if (orientation == 2) return 45;
        if (orientation == 3) return 135;
        return 270;
    }

    private static boolean isLight(DsSpec s) {
        int r = (s.bgColor1 >> 16) & 0xFF;
        int g = (s.bgColor1 >> 8) & 0xFF;
        int b = s.bgColor1 & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 < 128;
    }

    private static String safeId(DsSpec spec) {
        String id = DsValidator.sanitizeId(spec.id);
        return id.isEmpty() ? "dialog" : id;
    }

    private static String safeId(String id) {
        String s = DsValidator.sanitizeId(id);
        return s.isEmpty() ? "view" : s;
    }

    private static String hex(int color) {
        return String.format(Locale.US, "%08X", color);
    }

    private static String num(float value) {
        if (value == Math.round(value)) return String.valueOf((int) value);
        return String.format(Locale.US, "%.2f", value);
    }

    private static String numF(float value) {
        if (value == Math.round(value)) return String.valueOf((int) value) + "f";
        return String.format(Locale.US, "%.2f", value) + "f";
    }

    private static String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String java(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
