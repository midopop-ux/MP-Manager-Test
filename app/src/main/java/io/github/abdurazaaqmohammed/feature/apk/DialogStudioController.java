package io.github.abdurazaaqmohammed.feature.apk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.ai.AiChatActivity;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsAi;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsAiClient;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsAutomation;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsCodeGen;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsEvents;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsMerge;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsNativeIndex;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsPreview;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsRecovery;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsSpec;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsTemplates;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsValidator;
import io.github.abdurazaaqmohammed.feature.apk.dialogstudio.DsVars;

/**
 * Wires the Dialog Studio panel to the real engine classes and to the live
 * advanced-dialog editor. Every control in this controller performs a real
 * operation: recovery, editing, validation, preview, code generation or APK
 * injection. The panel is deliberately built from the same Material 3 widgets
 * used by the rest of the overlay editor.
 */
public final class DialogStudioController {

    public interface Host {
        List<OverlayInjectorUtil.AdvWidget> widgets();

        void refreshCanvas();

        void status(String message);

        void inject();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Activity activity;
    private final Context context;
    private final Host host;
    private final DsNativeIndex index = new DsNativeIndex();
    private final List<DsRecovery.Found> found = new ArrayList<>();
    private final List<DsSpec> library = new ArrayList<>();
    private final List<DsSpec> visibleLibrary = new ArrayList<>();
    private final List<DsNativeIndex.Method> methods = new ArrayList<>();
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private boolean restoringHistory;

    private DsSpec spec = new DsSpec();
    private DsSpec beforeAi;
    private DsSpec previewBefore;
    private final Map<String, String> previewVars = new HashMap<>();
    private String previewLog = "";
    private DsAi.Patch aiPatch;
    private LinearLayout groups;
    private TextView status;
    private View studioRoot;
    private MaterialAutoCompleteTextView previewDeviceSelect;

    private EditText pathInput;
    private MaterialAutoCompleteTextView foundSelect;
    private MaterialAutoCompleteTextView librarySelect;
    private MaterialAutoCompleteTextView templateSelect;
    private MaterialAutoCompleteTextView widgetSelect;
    private MaterialAutoCompleteTextView eventSelect;
    private MaterialAutoCompleteTextView actionSelect;
    private MaterialAutoCompleteTextView actionTypeSelect;
    private MaterialAutoCompleteTextView methodSelect;
    private MaterialAutoCompleteTextView logicFrom;
    private MaterialAutoCompleteTextView logicTo;
    private MaterialAutoCompleteTextView autoTrigger;
    private MaterialAutoCompleteTextView aiMode;
    private EditText widgetText;
    private EditText widgetHint;
    private EditText widgetOptions;
    private EditText widgetWidth;
    private EditText widgetHeight;
    private EditText widgetX;
    private EditText widgetY;
    private EditText actionTarget;
    private EditText actionValue;
    private EditText actionCondition;
    private EditText methodSearch;
    private EditText varName;
    private EditText varValue;
    private EditText logicTemplate;
    private EditText autoActivity;
    private EditText autoDelay;
    private EditText autoInterval;
    private EditText aiPrompt;
    private EditText delayInput;
    private EditText widthInput;
    private EditText dimInput;
    private EditText cornerInput;
    private EditText borderInput;
    private EditText styleBg1;
    private EditText styleBg2;
    private EditText styleBorder;
    private EditText styleTitle;
    private EditText styleMsg;
    private EditText styleBtn;
    private EditText styleFont;
    private EditText styleFontPath;
    private EditText styleTitleSize;
    private EditText styleMsgSize;
    private EditText styleBtnSize;
    private EditText stylePadding;
    private MaterialSwitch styleGradient;
    private EditText templateSearch;
    private MaterialAutoCompleteTextView linkMode;
    private EditText linkTarget;
    private int foundIndex = -1;
    private int libraryIndex = -1;
    private EditText librarySearch;
    private int actionIndex = -1;
    private int methodIndex = -1;

    private static final String[] EVENT_LABELS = {
            "ضغطة", "ضغطة طويلة", "ضغطتان", "عند الفتح", "عند الإغلاق",
            "تأكيد", "إلغاء", "تغير الإدخال", "انتهاء المهلة", "نجاح", "فشل"
    };
    private static final String[] ACTION_LABELS = {
            "إغلاق الدايلوك", "فتح رابط", "رسالة Toast", "نسخ نص",
            "تحديث نص عنصر", "تعيين متغير", "استدعاء دالة", "شرط متغير",
            "شرط إدخال", "إيقاف التنفيذ", "فتح دايلوك آخر", "تأخير"
    };

    public DialogStudioController(Activity activity, Host host) {
        this.activity = activity;
        this.context = activity;
        this.host = host;
    }

    public DsSpec spec() {
        return spec;
    }

    /** Simulates a widget action inside the live preview without touching the target APK. */
    public void previewAction(OverlayInjectorUtil.AdvWidget widget, String action) {
        if (widget == null) return;
        if (previewBefore == null) previewBefore = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        DsSpec.Event event = spec.findEvent(widget.id, "CLICK");
        if (event == null) event = spec.findEvent(widget.id, "CONFIRM");
        if (event == null) {
            event = spec.findEvent(widget.id, "INPUT_CHANGE");
        }
        if (event != null && !event.actions.isEmpty()) {
            DsPreview.Result result = DsPreview.run(spec, event, previewVars);
            previewVars.putAll(result.variables);
            previewLog = joinLog(result.log);
            applySpecToWidgets();
            host.refreshCanvas();
            String message = "معاينة " + widget.id + " · " + result.log.size() + " إجراء";
            host.status(message);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = DsSpec.empty(widget.id) ? widget.kind : widget.id;
        String kind = DsSpec.empty(widget.sourceKind) ? widget.kind : widget.sourceKind;
        String detail = DsSpec.empty(action) ? "ضغطة" : action;
        if ("input".equals(kind) || "textarea".equals(kind) || "number".equals(kind)) {
            detail = "إدخال: " + (widget.hint == null ? "" : widget.hint);
        } else if ("link".equals(kind)) {
            detail = "فتح رابط: " + (widget.url == null ? "" : widget.url);
        } else if ("rating".equals(kind) || "spinner".equals(kind) || "list".equals(kind)) {
            detail = "تحديد قيمة";
        }
        String message = "معاينة " + name + " · " + detail;
        host.status(message);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private String joinLog(List<String> log) {
        StringBuilder sb = new StringBuilder();
        for (String line : log) sb.append("• ").append(line).append('\n');
        return sb.toString();
    }

    private void runPreviewScenario() {
        if (spec.events.isEmpty()) {
            toast("لا توجد أحداث. أضف حدثًا أولاً.");
            return;
        }
        if (previewBefore == null) previewBefore = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        DsSpec.Event event = spec.events.get(0);
        for (DsSpec.Event e : spec.events) {
            if (e != null && e.enabled) {
                event = e;
                break;
            }
        }
        DsPreview.Result result = DsPreview.run(spec, event, previewVars);
        previewVars.putAll(result.variables);
        previewLog = joinLog(result.log);
        applySpecToWidgets();
        host.refreshCanvas();
        showCode("سجل سيناريو المعاينة", previewLog);
        setStatus("شُغّل " + event.event + " · " + result.log.size() + " خطوة.");
    }

    private void runAllPreviewEvents() {
        if (spec.events.isEmpty()) {
            toast("لا توجد أحداث.");
            return;
        }
        if (previewBefore == null) previewBefore = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        DsPreview.Result result = DsPreview.runAll(spec);
        previewVars.putAll(result.variables);
        previewLog = joinLog(result.log);
        applySpecToWidgets();
        host.refreshCanvas();
        showCode("سجل جميع الأحداث", previewLog);
        setStatus("اكتمل اختبار " + spec.events.size() + " حدث.");
    }

    private void fillPreviewData() {
        if (previewBefore == null) previewBefore = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        int n = 0;
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            if ("input".equals(w.kind) || "textarea".equals(w.kind)
                    || "number".equals(w.kind) || "search".equals(w.kind)) {
                w.text = "بيانات تجريبية " + (++n);
            }
        }
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            if ("number".equals(v.type) || "int".equals(v.type)) v.value = "42";
            else if ("boolean".equals(v.type)) v.value = "true";
            else if (DsSpec.empty(v.value)) v.value = "قيمة " + v.name;
            previewVars.put(v.name, v.value);
        }
        applySpecToWidgets();
        host.refreshCanvas();
        setStatus("مُلئت المعاينة ببيانات تجريبية.");
    }

    private void resetPreview() {
        if (previewBefore != null) {
            spec = GSON.fromJson(GSON.toJson(previewBefore), DsSpec.class);
            previewBefore = null;
        }
        previewVars.clear();
        previewLog = "";
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        setStatus("أُعيدت المعاينة إلى حالتها الأصلية.");
    }

    private void showPreviewLog() {
        showCode("سجل تنفيذ المعاينة", previewLog.isEmpty() ? "لا يوجد تنفيذ بعد." : previewLog);
    }

    private void previewOpenClose() {
        if (previewBefore == null) previewBefore = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        StringBuilder log = new StringBuilder();
        for (String name : new String[]{"OPEN", "CLOSE"}) {
            DsSpec.Event event = spec.findEvent("@dialog", name);
            if (event == null) {
                log.append("لا يوجد حدث ").append(name).append("\n");
                continue;
            }
            DsPreview.Result result = DsPreview.run(spec, event, previewVars);
            previewVars.putAll(result.variables);
            log.append(joinLog(result.log));
        }
        previewLog = log.toString();
        applySpecToWidgets();
        host.refreshCanvas();
        showCode("دورة حياة المعاينة", previewLog);
    }

    public void attach(View root, OverlayInjectorUtil.DialogOptions dialog) {
        View panel = root.findViewById(R.id.dialogStudioPanel);
        if (panel == null) return;
        studioRoot = root;
        groups = (LinearLayout) panel.findViewById(R.id.dialogStudioGroups);
        status = (TextView) panel.findViewById(R.id.dialogStudioStatus);
        loadFrom(dialog);
        loadCustomTemplates();
        buildGroups();
        setStatus("جاهز · " + spec.widgets.size() + " عنصر · "
                + spec.events.size() + " حدث");
    }

    public void loadFrom(OverlayInjectorUtil.DialogOptions dialog) {
        if (dialog != null && dialog.studio != null) {
            spec = GSON.fromJson(GSON.toJson(dialog.studio), DsSpec.class);
        } else {
            spec = new DsSpec();
        }
        if (spec == null) spec = new DsSpec();
        if (DsSpec.empty(spec.id)) spec.id = "mp_dialog_" + System.currentTimeMillis();
        if (!spec.widgets.isEmpty() && host.widgets().isEmpty()) {
            applySpecToWidgets();
        } else {
            syncFromWidgets();
        }
    }

    public void applyTo(OverlayInjectorUtil.DialogOptions dialog) {
        if (dialog == null) return;
        if (spec != null && (!spec.widgets.isEmpty() || !spec.title.isEmpty() || !spec.message.isEmpty())) {
            saveVersion(spec);
        }
        syncFromWidgets();
        applySpecToWidgets();
        dialog.studio = spec;
        dialog.advanced = true;
        dialog.delayMs = Math.max(0, spec.delayMs);
        dialog.preventDoubleOpen = spec.preventDoubleOpen;
        dialog.showOnce = spec.showOnce;
        dialog.dontShowAgain = spec.dontShowAgain;
        dialog.cancelable = spec.cancelable;
        dialog.draggable = spec.draggable;
        dialog.bgEnabled = true;
        dialog.bgColor1 = spec.bgColor1;
        dialog.bgColor2 = spec.bgColor2;
        dialog.bgGradient = spec.gradient;
        dialog.bgOrientation = spec.orientation;
        dialog.cornerRadiusDp = spec.cornerRadiusDp;
        dialog.borderWidthDp = spec.borderWidthDp;
        dialog.borderColor = spec.borderColor;
        dialog.animBorder = spec.animBorder;
        dialog.animColorA = spec.animColorA;
        dialog.animColorB = spec.animColorB;
        dialog.rainbowAnim = spec.rainbowAnim;
        dialog.animSpeedMs = Math.max(100, spec.animSpeedMs);
        dialog.titleColor = spec.titleColor;
        dialog.msgColor = spec.msgColor;
        dialog.btnColor = spec.btnColor;
        dialog.widthPercent = spec.widthPercent;
        dialog.dimAmount = spec.dimAmount;
        dialog.dlgFont = spec.font == null ? "" : spec.font;
        dialog.dlgFontPath = spec.fontPath == null ? "" : spec.fontPath;
        Map<String, String> runtime = new HashMap<>();
        for (DsSpec.Variable v : spec.variables) {
            if (v != null && !DsSpec.empty(v.name) && !"input".equals(v.source)) {
                runtime.put(v.name, v.value == null ? "" : v.value);
            }
        }
        for (OverlayInjectorUtil.AdvWidget w : host.widgets()) {
            if (w == null || "input".equals(w.kind)) continue;
            if (!DsSpec.empty(w.text) && (w.text.contains("{{") || w.text.contains("${"))) {
                w.text = DsVars.resolve(w.text, spec, runtime);
            }
            if (!DsSpec.empty(w.hint) && (w.hint.contains("{{") || w.hint.contains("${"))) {
                w.hint = DsVars.resolve(w.hint, spec, runtime);
            }
        }
    }

    // ---------------------------------------------------------------- UI

    private void buildGroups() {
        groups.removeAllViews();
        buildRecoveryGroup();
        buildDesignGroup();
        buildStyleGroup();
        buildWidgetsGroup();
        buildEventsGroup();
        buildFunctionsGroup();
        buildVariablesGroup();
        buildLinksGroup();
        buildAutomationGroup();
        buildTestGroup();
        buildAiGroup();
    }

    private void buildRecoveryGroup() {
        LinearLayout c = group("الاستعادة والتحويل", true);
        pathInput = field(c, "مسار مشروع مفكوك أو ملف APK", defaultProjectPath());
        row(c,
                button("فحص المشروع", v -> scanProject(false)),
                button("فحص APK", v -> scanProject(true)));
        foundSelect = select(c, "الدايلوكات المكتشفة", new String[]{"— لم يتم الفحص بعد —"}, 0);
        foundSelect.setOnItemClickListener((p, v, pos, id) -> foundIndex = pos);
        row(c,
                button("فتح في المحرر", v -> openFound(false)),
                button("دمج مع الحالي", v -> openFound(true)));
        row(c,
                button("إضافة للمكتبة", v -> addFoundToLibrary()),
                button("عرض العلاقات", v -> showGraph()));
        librarySelect = select(c, "مكتبة الدايلوكات", new String[]{"— المكتبة فارغة —"}, 0);
        librarySelect.setOnItemClickListener((p, v, pos, id) -> libraryIndex = pos);
        librarySearch = field(c, "بحث في المكتبة", "");
        librarySearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refreshLibrary(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        row(c,
                button("فتح", v -> openLibrary(false)),
                button("نسخة", v -> openLibrary(true)));
        row(c,
                button("تعديل الاسم", v -> renameLibraryItem()),
                button("حذف", v -> deleteLibraryItem()),
                button("مقارنة", v -> compareLibraryItem()));
        row(c,
                button("تصدير JSON", v -> exportLibraryItem()),
                button("استيراد JSON", v -> importLibraryJson()));
        row(c,
                button("حفظ المكتبة", v -> saveLibrary()),
                button("تحميل المكتبة", v -> loadLibrary()));
        row(c,
                button("تراجع", v -> undo()),
                button("إعادة", v -> redo()));
        row(c,
                button("الإصدارات", v -> showVersions()),
                button("نسخة احتياطية", v -> {
                    saveVersion(spec);
                    toast("حُفظت نسخة احتياطية.");
                }));
        hint(c, "الفحص يقرأ XML وJava وKotlin وSmali، ويستعيد AlertDialog.Builder وأحداثه.");
    }

    private void buildDesignGroup() {
        LinearLayout c = group("التصميم والقوالب", false);
        templateSelect = select(c, "القالب", DsTemplates.namesAr(),
                Math.max(0, DsTemplates.indexOf(spec.templateId)));
        templateSelect.setOnItemClickListener((p, v, pos, id) -> applyTemplate());
        row(c, button("تطبيق القالب", v -> applyTemplate()),
                button("تحديث المعاينة", v -> preview()),
                button("مكتبة القوالب", v -> showTemplateLibrary()));
        previewDeviceSelect = select(c, "حجم شاشة المعاينة",
                new String[]{"iPhone SE · 320dp", "iPhone 15 · 393dp", "Pixel · 412dp", "Tablet · 600dp"}, 1);
        previewDeviceSelect.setOnItemClickListener((p, v, pos, id) -> applyPreviewDevice(pos));
        row(c,
                button("تكبير +", v -> scalePreview(1.15f)),
                button("تصغير −", v -> scalePreview(0.87f)),
                button("إعادة الضبط", v -> resetPreviewView()),
                button("توسيط", v -> centerPreview()));
        row(c, button("حفظ كقالب", v -> saveCustomTemplate()),
                button("تصدير القوالب", v -> exportTemplates()),
                button("استيراد القوالب", v -> importTemplates()));
        widthInput = field(c, "العرض %", String.valueOf(spec.widthPercent));
        dimInput = field(c, "تعتيم الخلفية 0-1", String.valueOf(spec.dimAmount));
        cornerInput = field(c, "استدارة الزوايا dp", String.valueOf(spec.cornerRadiusDp));
        borderInput = field(c, "سماكة الإطار dp", String.valueOf(spec.borderWidthDp));
        delayInput = field(c, "تأخير الظهور ms", String.valueOf(spec.delayMs));
        row(c, button("تطبيق القياسات", v -> {
                    remember();
                    applyMeasurements();
                }),
                button("تحديث المحرر", v -> {
                    remember();
                    applyMeasurements();
                    applySpecToWidgets();
                    host.refreshCanvas();
                }));
        row(c,
                button("توليد XML", v -> showCode("Layout + Drawable",
                        DsCodeGen.layoutXml(spec) + "\n\n<!-- drawable -->\n" + DsCodeGen.drawableXml(spec))),
                button("توليد Java", v -> showCode("Java", DsCodeGen.javaSource(spec, libraryMap()))));
        row(c,
                button("محرر المصدر", v -> editSource()),
                button("تحرير Java", v -> editSourceText("Java", DsCodeGen.javaSource(spec, libraryMap()), "java")));
        MaterialSwitch guard = toggle(c, "منع الفتح المتكرر", spec.preventDoubleOpen);
        guard.setOnCheckedChangeListener((b, checked) -> {
            remember();
            spec.preventDoubleOpen = checked;
            setStatus("منع الفتح المتكرر: " + (checked ? "مفعل" : "معطل"));
        });
        MaterialSwitch once = toggle(c, "عرض مرة واحدة", spec.showOnce);
        once.setOnCheckedChangeListener((b, checked) -> {
            remember();
            spec.showOnce = checked;
        });
        MaterialSwitch dont = toggle(c, "لا تظهر مجددًا", spec.dontShowAgain);
        dont.setOnCheckedChangeListener((b, checked) -> {
            remember();
            spec.dontShowAgain = checked;
        });
    }

    private void buildStyleGroup() {
        LinearLayout c = group("الألوان والخطوط", false);
        styleBg1 = field(c, "خلفية 1 (#AARRGGBB)", hex(spec.bgColor1));
        styleBg2 = field(c, "خلفية 2 للتدرّج", hex(spec.bgColor2));
        styleBorder = field(c, "لون الإطار", hex(spec.borderColor));
        styleTitle = field(c, "لون العنوان", hex(spec.titleColor == 0 ? 0xFF1B1B1F : spec.titleColor));
        styleMsg = field(c, "لون النص", hex(spec.msgColor == 0 ? 0xFF44464F : spec.msgColor));
        styleBtn = field(c, "لون الزر", hex(spec.btnColor == 0 ? 0xFF6750A4 : spec.btnColor));
        EditText bg1 = styleBg1;
        EditText bg2 = styleBg2;
        EditText border = styleBorder;
        EditText title = styleTitle;
        EditText msg = styleMsg;
        EditText btn = styleBtn;
        row(c,
                button("فاتح", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFFFFFFFF, 0x00FFFFFF, 0xFF1B1B1F, 0xFF44464F, 0xFF6750A4, 0x22000000)),
                button("داكن", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFF1C1C1E, 0x00FFFFFF, 0xFFFFFFFF, 0xFFC7C7CC, 0xFF64D2FF, 0x33FFFFFF)),
                button("بنفسجي", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFFF5F3FF, 0xFFEDE9FE, 0xFF2E1065, 0xFF5B21B6, 0xFF7C3AED, 0x22A78BFA)),
                button("أزرق", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFFEFF6FF, 0xFFDBEAFE, 0xFF0F172A, 0xFF334155, 0xFF2563EB, 0x223B82F6)),
                button("أخضر", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFFF0FDF4, 0xFFDCFCE7, 0xFF052E16, 0xFF166534, 0xFF16A34A, 0x2222C55E)),
                button("وردي", v -> setPalette(bg1, bg2, title, msg, btn, border,
                        0xFFFFF1F2, 0xFFFFE4E6, 0xFF4C0519, 0xFF9F1239, 0xFFE11D48, 0x22F43F5E)));
        styleGradient = toggle(c, "تدرّج الخلفية", spec.gradient);
        styleGradient.setOnCheckedChangeListener((b, checked) -> onGradientChanged(checked));
        String[] fonts = {"sans-serif", "serif", "monospace", "sans-serif-condensed", "sans-serif-medium"};
        MaterialAutoCompleteTextView fontPick = select(c, "الخط", fonts,
                Math.max(0, java.util.Arrays.asList(fonts).indexOf(spec.font)));
        styleFont = field(c, "اسم الخط المخصص", spec.font);
        styleFontPath = field(c, "مسار ملف الخط TTF/OTF (اختياري)", spec.fontPath);
        EditText font = styleFont;
        EditText fontPath = styleFontPath;
        fontPick.setOnItemClickListener((p, v, pos, id) -> font.setText(fonts[pos]));
        styleTitleSize = field(c, "حجم العنوان sp", String.valueOf(spec.titleSizeSp));
        styleMsgSize = field(c, "حجم النص sp", String.valueOf(spec.msgSizeSp));
        styleBtnSize = field(c, "حجم زر sp", String.valueOf(spec.buttonSizeSp));
        stylePadding = field(c, "الحشو الداخلي dp", String.valueOf(spec.paddingDp));
        EditText titleSize = styleTitleSize;
        EditText msgSize = styleMsgSize;
        EditText btnSize = styleBtnSize;
        EditText padding = stylePadding;
        row(c, button("تطبيق الألوان والخطوط", v -> {
            remember();
            spec.bgColor1 = parseColor(bg1.getText().toString(), spec.bgColor1);
            spec.bgColor2 = parseColor(bg2.getText().toString(), spec.bgColor2);
            spec.borderColor = parseColor(border.getText().toString(), spec.borderColor);
            spec.titleColor = parseColor(title.getText().toString(), spec.titleColor);
            spec.msgColor = parseColor(msg.getText().toString(), spec.msgColor);
            spec.btnColor = parseColor(btn.getText().toString(), spec.btnColor);
            spec.font = font.getText().toString().trim();
            spec.fontPath = fontPath.getText().toString().trim();
            spec.titleSizeSp = parseFloat(titleSize.getText().toString(), spec.titleSizeSp);
            spec.msgSizeSp = parseFloat(msgSize.getText().toString(), spec.msgSizeSp);
            spec.buttonSizeSp = parseFloat(btnSize.getText().toString(), spec.buttonSizeSp);
            spec.paddingDp = (int) parseFloat(padding.getText().toString(), spec.paddingDp);
            applySpecToWidgets();
            host.refreshCanvas();
            setStatus("طُبقت الألوان والخطوط على الدايلوك.");
        }));
        hint(c, "الألوان بصيغة #AARRGGBB. الخط المخصص يعمل عند وجود ملف TTF/OTF في المشروع.");
    }

    private void onGradientChanged(boolean checked) {
        remember();
        spec.gradient = checked;
        applySpecToWidgets();
        host.refreshCanvas();
        setStatus("التدرّج: " + (checked ? "مفعل" : "معطل"));
    }

    private void syncStyleFields() {
        if (styleBg1 == null) return;
        styleBg1.setText(hex(spec.bgColor1));
        styleBg2.setText(hex(spec.bgColor2));
        styleBorder.setText(hex(spec.borderColor));
        styleTitle.setText(hex(spec.titleColor == 0 ? 0xFF1B1B1F : spec.titleColor));
        styleMsg.setText(hex(spec.msgColor == 0 ? 0xFF44464F : spec.msgColor));
        styleBtn.setText(hex(spec.btnColor == 0 ? 0xFF6750A4 : spec.btnColor));
        styleFont.setText(spec.font);
        styleFontPath.setText(spec.fontPath);
        styleTitleSize.setText(String.valueOf(spec.titleSizeSp));
        styleMsgSize.setText(String.valueOf(spec.msgSizeSp));
        styleBtnSize.setText(String.valueOf(spec.buttonSizeSp));
        stylePadding.setText(String.valueOf(spec.paddingDp));
        if (styleGradient != null) {
            styleGradient.setOnCheckedChangeListener(null);
            styleGradient.setChecked(spec.gradient);
            styleGradient.setOnCheckedChangeListener((b, checked) -> onGradientChanged(checked));
        }
    }

    private void setPalette(EditText bg1, EditText bg2, EditText title, EditText msg,
                            EditText btn, EditText border, int bg1Color, int bg2Color,
                            int titleColor, int msgColor, int btnColor, int borderColor) {
        bg1.setText(hex(bg1Color));
        bg2.setText(hex(bg2Color));
        title.setText(hex(titleColor));
        msg.setText(hex(msgColor));
        btn.setText(hex(btnColor));
        border.setText(hex(borderColor));
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) return fallback;
        String text = raw.trim();
        if (text.isEmpty()) return fallback;
        try {
            return Color.parseColor(text.startsWith("#") ? text : "#" + text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void buildWidgetsGroup() {
        LinearLayout c = group("العناصر والواجهات", false);
        row(c,
                button("نص", v -> addWidget("text")),
                button("زر", v -> addWidget("button")),
                button("إدخال", v -> addWidget("input")));
        row(c,
                button("اختيار", v -> addWidget("checkbox")),
                button("قائمة", v -> addWidget("spinner")),
                button("فاصل", v -> addWidget("divider")));
        row(c,
                button("بطاقة", v -> addWidget("card")),
                button("مؤشر تحميل", v -> addWidget("progress")),
                button("مفتاح", v -> addWidget("toggle")));
        row(c,
                button("نص طويل", v -> addWidget("textarea")),
                button("رقم", v -> addWidget("number")),
                button("رابط", v -> addWidget("link")));
        row(c,
                button("بحث", v -> addWidget("search")),
                button("تعدد", v -> addWidget("multiselect")),
                button("راديو", v -> addWidget("radio")));
        row(c,
                button("تقييم", v -> addWidget("rating")),
                button("تبويبات", v -> addWidget("tabs")),
                button("صفحات", v -> addWidget("pager")));
        row(c,
                button("شريط أدوات", v -> addWidget("toolbar")),
                button("قائمة", v -> addWidget("list")),
                button("صورة", v -> addWidget("image")),
                button("أيقونة", v -> addWidget("icon")));
        widgetSelect = select(c, "العنصر الحالي", widgetLabels(), 0);
        widgetSelect.setOnItemClickListener((p, v, pos, id) -> loadWidgetFields(pos));
        widgetText = field(c, "النص", "");
        widgetHint = field(c, "التلميح", "");
        widgetOptions = field(c, "خيارات القائمة (سطر لكل خيار)", "");
        widgetWidth = field(c, "عرض العنصر % (0 = تلقائي)", "100");
        widgetHeight = field(c, "ارتفاع العنصر dp (0 = تلقائي)", "0");
        widgetX = field(c, "الموضع X dp", "0");
        widgetY = field(c, "الموضع Y dp", "0");
        row(c,
                button("تحديث", v -> updateWidget()),
                button("نسخ العنصر", v -> duplicateWidget()),
                button("حذف", v -> deleteWidget()),
                button("أعلى", v -> moveWidget(-1)),
                button("أسفل", v -> moveWidget(1)));
        hint(c, "المعرفات تُنشأ تلقائيًا وتُستخدم في الأحداث والربط. يمكن تعديل الحجم والموضع بدقة.");
    }

    private void buildEventsGroup() {
        LinearLayout c = group("الأحداث والإجراءات", false);
        widgetSelectForEvents = select(c, "العنصر", eventWidgetLabels(), 0);
        eventSelect = select(c, "الحدث", EVENT_LABELS, 0);
        eventSelect.setOnItemClickListener((p, v, pos, id) -> refreshActions());
        actionTypeSelect = select(c, "نوع الإجراء", ACTION_LABELS, 0);
        actionTarget = field(c, "الهدف / المتغير / العنصر", "");
        actionValue = field(c, "القيمة / النص / معامل الدالة", "");
        actionCondition = field(c, "الشرط (مثال: user==نعم أو input:field notempty)", "");
        row(c,
                button("إضافة إجراء", v -> addAction()),
                button("تحديث المحدد", v -> updateAction()),
                button("حذف المحدد", v -> deleteAction()));
        actionSelect = select(c, "الإجراءات الحالية", new String[]{"— لا إجراءات —"}, 0);
        actionSelect.setOnItemClickListener((p, v, pos, id) -> loadActionFields(pos));
        row(c,
                button("معاينة الحدث", v -> preview()),
                button("فحص الدعم", v -> checkInjectionSupport()));
    }

    private MaterialAutoCompleteTextView widgetSelectForEvents;

    private void buildFunctionsGroup() {
        LinearLayout c = group("الربط بالوظائف الأصلية", false);
        methodSearch = field(c, "بحث بالاسم أو الفئة", "");
        row(c,
                button("بحث", v -> searchMethods()),
                button("فهرس المشروع", v -> indexProject()));
        methodSelect = select(c, "الدوال المتاحة", new String[]{"— لا نتائج —"}, 0);
        methodSelect.setOnItemClickListener((p, v, pos, id) -> methodIndex = pos);
        row(c,
                button("ربط بالحدث", v -> bindMethod()),
                button("عرض التفاصيل", v -> showMethod()));
        hint(c, "يُسمح بالدوال static ذات المعاملات البسيطة فقط. الدوال الخطرة محجوبة.");
    }

    private void buildVariablesGroup() {
        LinearLayout c = group("المتغيرات والمنطق", false);
        varName = field(c, "اسم المتغير", "");
        varValue = field(c, "القيمة", "");
        row(c,
                button("إضافة متغير", v -> addVariable()),
                button("حذف متغير", v -> removeVariable()));
        row(c,
                button("عرض المتغيرات", v -> showVariables()),
                button("تحديث النصوص", v -> refreshVariableText()));
        logicFrom = select(c, "من عنصر", eventWidgetLabels(), 0);
        logicTo = select(c, "إلى عنصر", eventWidgetLabels(), 0);
        logicTemplate = field(c, "القالب (استخدم {{var}})", "");
        row(c,
                button("إضافة ربط", v -> addLogic()),
                button("عرض الروابط", v -> showLogic()));
    }

    private void buildLinksGroup() {
        LinearLayout c = group("الروابط والوظائف الداخلية", false);
        linkMode = select(c, "طريقة الفتح", new String[]{
                "متصفح", "رابط داخلي", "تطبيق خارجي", "نسخ الرابط"
        }, 0);
        EditText text = field(c, "نص الرابط", "");
        EditText url = field(c, "الرابط أو المسار", "");
        linkTarget = field(c, "هدف داخلي (معرف دايلوك)", "");
        row(c,
                button("إضافة رابط", v -> {
                    DsSpec.Link link = new DsSpec.Link();
                    link.text = text(text);
                    link.url = text(url);
                    link.target = text(linkTarget);
                    link.mode = linkModeValue();
                    if (DsSpec.empty(link.text) || DsSpec.empty(link.url)) {
                        toast("أدخل نص الرابط والرابط.");
                        return;
                    }
                    spec.links.add(link);
                    setStatus("أُضيف رابط: " + link.text);
                }),
                button("عرض الروابط", v -> showLinks()),
                button("حذف الكل", v -> {
                    spec.links.clear();
                    setStatus("حُذفت روابط الدايلوك.");
                }));
        hint(c, "الروابط تُعرض داخل الدايلوك وتُفتح فعليًا في المعاينة والكود المولد.");
    }

    private String linkModeValue() {
        int i = selectedIndex(linkMode);
        switch (i) {
            case 1: return "internal";
            case 2: return "app";
            case 3: return "copy";
            default: return "browser";
        }
    }

    private void showLinks() {
        if (spec.links.isEmpty()) {
            toast("لا توجد روابط.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Link link : spec.links) {
            if (link == null) continue;
            sb.append("• ").append(link.text).append(" → ").append(link.url)
                    .append(" [").append(link.mode).append("]")
                    .append(DsSpec.empty(link.target) ? "" : " ⇒ " + link.target).append('\n');
        }
        showCode("روابط الدايلوك", sb.toString());
    }

    private void buildAutomationGroup() {
        LinearLayout c = group("أتمتة الدايلوكات", false);
        autoTrigger = select(c, "المشغّل", DsAutomation.triggers().toArray(new String[0]), 0);
        autoActivity = field(c, "الشاشة / الفئة", "");
        autoDelay = field(c, "التأخير ms", "0");
        autoInterval = field(c, "الفاصل ms", "0");
        row(c,
                button("إضافة قاعدة", v -> addAutomation()),
                button("عرض القواعد", v -> showAutomations()),
                button("حذف الكل", v -> {
                    spec.automations.clear();
                    setStatus("تم حذف قواعد الأتمتة.");
                }));
        hint(c, "القواعد المدعومة للـAPK: ACTIVITY_OPEN وAFTER_DELAY وCONDITION وCLICK وTIMER.");
    }

    private void buildTestGroup() {
        LinearLayout c = group("التحليل والاختبار والتنفيذ", false);
        row(c,
                button("فحص", v -> validate()),
                button("إصلاح تلقائي", v -> fix()));
        row(c,
                button("معاينة حقيقية", v -> preview()),
                button("توليد الكود", v -> showCode("الكود المولد",
                        DsCodeGen.layoutXml(spec) + "\n\n" + DsCodeGen.javaSource(spec, libraryMap()))));
        row(c,
                button("تطبيق على المحرر", v -> {
                    applySpecToWidgets();
                    host.refreshCanvas();
                    setStatus("تم تطبيق الدايلوك على المحرر.");
                }),
                button("حقن في APK", v -> host.inject()));
        row(c,
                button("تصدير المشروع", v -> exportProject()),
                button("ملخص", v -> showSummary()));
        row(c,
                button("تشغيل سيناريو", v -> runPreviewScenario()),
                button("اختبار كل الأحداث", v -> runAllPreviewEvents()),
                button("تعبئة تجريبية", v -> fillPreviewData()));
        row(c,
                button("دورة فتح/إغلاق", v -> previewOpenClose()),
                button("إعادة تعيين", v -> resetPreview()),
                button("سجل المعاينة", v -> showPreviewLog()));
    }

    private void buildAiGroup() {
        LinearLayout c = group("الذكاء الاصطناعي الهندسي", false);
        String[] modes = {DsAi.MODE_CREATE, DsAi.MODE_REDESIGN, DsAi.MODE_EVENTS,
                DsAi.MODE_CONVERT, DsAi.MODE_FIX, DsAi.MODE_EXPLAIN,
                DsAi.MODE_SCENARIO, DsAi.MODE_FUNCTIONS};
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = DsAi.modeLabel(modes[i]);
        aiMode = select(c, "الوضع", labels, 0);
        aiPrompt = field(c, "وصف المطلوب", "");
        row(c,
                button("تشغيل", v -> runAi()),
                button("اعتماد التعديل", v -> applyAi()),
                button("عرض الفرق", v -> showAiDiff()));
        row(c,
                button("محادثة AI كاملة", v -> context.startActivity(
                        new Intent(context, AiChatActivity.class))),
                button("شرح المحدد", v -> {
                    aiMode.setText(DsAi.modeLabel(DsAi.MODE_EXPLAIN), false);
                    runAi();
                }));
        hint(c, "يعرض التعديل المقترح قبل اعتماده، ولا يغيّر الدايلوك تلقائيًا.");
    }

    // -------------------------------------------------------------- helpers

    private LinearLayout group(String title, boolean open) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(2), dp(5), dp(2), dp(5));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView header = new TextView(context);
        header.setText((open ? "▾  " : "▸  ") + title);
        header.setTextSize(15);
        header.setTypeface(header.getTypeface(), Typeface.BOLD);
        header.setTextColor(MaterialColors.getColor(header,
                com.google.android.material.R.attr.colorOnSurface));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(12));
        content.setVisibility(open ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText((show ? "▾  " : "▸  ") + title);
        });
        wrap.addView(header);
        wrap.addView(content);
        card.addView(wrap);
        groups.addView(card);
        return content;
    }

    private MaterialButton button(String text, View.OnClickListener listener) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMaxLines(2);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42));
        b.setMinimumHeight(dp(42));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private void row(LinearLayout parent, View... views) {
        if (views.length == 0) return;
        if (views.length == 1) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(views[0]);
            parent.addView(row);
            return;
        }
        for (int start = 0; start < views.length; start += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(views[start]);
            if (start + 1 < views.length) {
                row.addView(views[start + 1]);
            } else {
                View spacer = new View(context);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, 1, 1f));
                row.addView(spacer);
            }
            parent.addView(row);
        }
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(context)
                .inflate(R.layout.item_ds_field, parent, false);
        layout.setHint(hint);
        TextInputEditText edit = layout.findViewById(R.id.dsFieldInput);
        edit.setText(value == null ? "" : value);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        parent.addView(layout);
        return edit;
    }

    private MaterialAutoCompleteTextView select(LinearLayout parent, String hint,
                                                String[] values, int selected) {
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(context)
                .inflate(R.layout.item_ds_select, parent, false);
        layout.setHint(hint);
        MaterialAutoCompleteTextView tv = layout.findViewById(R.id.dsSelectInput);
        tv.setInputType(InputType.TYPE_NULL);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, values);
        tv.setAdapter(adapter);
        tv.setOnClickListener(v -> tv.showDropDown());
        if (values.length > 0) {
            tv.setText(values[Math.max(0, Math.min(values.length - 1, selected))]);
        }
        parent.addView(layout);
        return tv;
    }

    private MaterialSwitch toggle(LinearLayout parent, String text, boolean checked) {
        MaterialSwitch s = new MaterialSwitch(context);
        s.setText(text);
        s.setChecked(checked);
        parent.addView(s);
        return s;
    }

    private void hint(LinearLayout parent, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(MaterialColors.getColor(t,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setPadding(dp(2), dp(5), dp(2), dp(2));
        parent.addView(t);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
        if (host != null) host.status(message);
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        setStatus(message);
    }

    // ------------------------------------------------------------ recovery

    private String defaultProjectPath() {
        File dir = context.getExternalFilesDir("DialogStudio");
        return dir == null ? "" : dir.getAbsolutePath();
    }

    private void remember() {
        if (restoringHistory) return;
        undoStack.push(GSON.toJson(spec));
        while (undoStack.size() > 40) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            toast("لا توجد خطوة للتراجع.");
            return;
        }
        redoStack.push(GSON.toJson(spec));
        restoreHistory(GSON.fromJson(undoStack.pop(), DsSpec.class));
        setStatus("تم التراجع.");
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            toast("لا توجد خطوة للإعادة.");
            return;
        }
        undoStack.push(GSON.toJson(spec));
        restoreHistory(GSON.fromJson(redoStack.pop(), DsSpec.class));
        setStatus("تمت الإعادة.");
    }

    private void restoreHistory(DsSpec restored) {
        if (restored == null) return;
        restoringHistory = true;
        spec = restored;
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        restoringHistory = false;
    }

    private void scanProject(boolean apk) {
        String path = text(pathInput);
        if (TextUtils.isEmpty(path)) {
            toast("حدد مسار المشروع أو ملف APK أولاً.");
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            toast("المسار غير موجود: " + path);
            return;
        }
        setStatus(apk ? "جارٍ فحص APK..." : "جارٍ فحص المشروع...");
        new Thread(() -> {
            List<DsRecovery.Found> result = new ArrayList<>();
            String error = null;
            try {
                if (apk || file.getName().toLowerCase().endsWith(".apk")) {
                    result.addAll(OverlayInjectorUtil.scanApkForDialogs(context, file, null));
                } else {
                    result.addAll(DsRecovery.scanDirectory(file, 600));
                }
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final List<DsRecovery.Found> out = result;
            final String err = error;
            activity.runOnUiThread(() -> {
                found.clear();
                found.addAll(out);
                foundIndex = out.isEmpty() ? -1 : 0;
                String[] labels = new String[Math.max(1, out.size())];
                if (out.isEmpty()) labels[0] = "— لم يُعثر على دايلوكات —";
                for (int i = 0; i < out.size(); i++) labels[i] = out.get(i).display();
                setSelect(foundSelect, labels, 0);
                setStatus(err == null
                        ? "تم العثور على " + out.size() + " دايلوك."
                        : "فشل الفحص: " + err);
            });
        }).start();
    }

    private void openFound(boolean merge) {
        if (foundIndex < 0 || foundIndex >= found.size()) {
            toast("اختر دايلوكًا مكتشفًا أولاً.");
            return;
        }
        DsRecovery.Found f = found.get(foundIndex);
        DsSpec target = f.spec != null ? f.spec : DsRecovery.toSpec(f);
        if (target == null) {
            toast("تعذر استعادة الدايلوك.");
            return;
        }
        if (merge) {
            spec = DsMerge.merge(spec, target);
            toast("تم دمج الدايلوك دون تعارض في المعرفات.");
        } else {
            spec = GSON.fromJson(GSON.toJson(target), DsSpec.class);
            toast("تم فتح الدايلوك في المحرر.");
        }
        if (DsSpec.empty(spec.id)) spec.id = "mp_recovered_" + System.currentTimeMillis();
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
    }

    private void addFoundToLibrary() {
        if (foundIndex < 0 || foundIndex >= found.size()) {
            toast("اختر دايلوكًا مكتشفًا أولاً.");
            return;
        }
        DsRecovery.Found f = found.get(foundIndex);
        DsSpec s = f.spec != null ? f.spec : DsRecovery.toSpec(f);
        if (s == null) return;
        library.add(GSON.fromJson(GSON.toJson(s), DsSpec.class));
        refreshLibrary();
        toast("أضيف إلى المكتبة (" + library.size() + ").");
    }

    private void openLibrary(boolean duplicate) {
        if (libraryIndex < 0 || libraryIndex >= visibleLibrary.size()) {
            toast("اختر دايلوكًا من المكتبة أولاً.");
            return;
        }
        DsSpec s = visibleLibrary.get(libraryIndex);
        spec = duplicate ? DsMerge.duplicate(s, s.id + "_copy_" + System.currentTimeMillis())
                : GSON.fromJson(GSON.toJson(s), DsSpec.class);
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        toast(duplicate ? "تم إنشاء نسخة." : "تم فتح الدايلوك.");
    }

    private DsSpec selectedLibrarySpec() {
        if (libraryIndex < 0 || libraryIndex >= visibleLibrary.size()) {
            toast("اختر دايلوكًا من المكتبة أولاً.");
            return null;
        }
        return visibleLibrary.get(libraryIndex);
    }

    private void renameLibraryItem() {
        DsSpec s = selectedLibrarySpec();
        if (s == null) return;
        EditText input = new EditText(context);
        input.setText(s.name == null ? "" : s.name);
        input.setHint("اسم الدايلوك");
        new AlertDialog.Builder(context)
                .setTitle("تعديل اسم الدايلوك")
                .setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("الاسم لا يمكن أن يكون فارغًا.");
                        return;
                    }
                    s.name = name;
                    refreshLibrary();
                    saveLibrary();
                    setStatus("تم تعديل اسم الدايلوك.");
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void deleteLibraryItem() {
        DsSpec s = selectedLibrarySpec();
        if (s == null) return;
        new AlertDialog.Builder(context)
                .setTitle("حذف من المكتبة")
                .setMessage("حذف \"" + (s.name == null ? s.id : s.name) + "\" من المكتبة؟")
                .setPositiveButton("حذف", (d, w) -> {
                    library.remove(s);
                    refreshLibrary();
                    saveLibrary();
                    setStatus("حُذف الدايلوك من المكتبة.");
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void compareLibraryItem() {
        DsSpec s = selectedLibrarySpec();
        if (s == null) return;
        showCode("مقارنة الدايلوك الحالي مع المكتبة", DsMerge.compare(s, spec));
    }

    private void exportLibraryItem() {
        DsSpec s = selectedLibrarySpec();
        if (s == null) return;
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) {
                toast("التخزين غير متاح.");
                return;
            }
            dir.mkdirs();
            File out = new File(dir, "library_" + DsValidator.sanitizeId(s.id) + ".json");
            write(out, GSON.toJson(s));
            showCode("تصدير الدايلوك", out.getAbsolutePath() + "\n\n" + GSON.toJson(s));
            setStatus("صُدر الدايلوك إلى JSON.");
        } catch (Exception e) {
            toast("فشل التصدير: " + e.getMessage());
        }
    }

    private void importLibraryJson() {
        File dir = context.getExternalFilesDir("DialogStudio");
        if (dir == null) {
            toast("التخزين غير متاح.");
            return;
        }
        List<File> files = new ArrayList<>();
        collectJsonFiles(dir, files, 0);
        if (files.isEmpty()) {
            toast("لا توجد ملفات JSON في " + dir.getAbsolutePath());
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) labels[i] = files.get(i).getName();
        new AlertDialog.Builder(context)
                .setTitle("استيراد دايلوك JSON")
                .setItems(labels, (d, pos) -> {
                    try {
                        String json = read(files.get(pos));
                        int added = 0;
                        if (json.trim().startsWith("[")) {
                            DsSpec[] arr = GSON.fromJson(json, DsSpec[].class);
                            if (arr != null) {
                                for (DsSpec item : arr) {
                                    if (addImportedSpec(item)) added++;
                                }
                            }
                        } else if (addImportedSpec(GSON.fromJson(json, DsSpec.class))) {
                            added = 1;
                        }
                        if (added == 0) {
                            toast("ملف JSON لا يحتوي دايلوكات صالحة.");
                            return;
                        }
                        refreshLibrary();
                        saveLibrary();
                        setStatus("استُورد " + added + " دايلوك.");
                    } catch (Exception e) {
                        toast("فشل الاستيراد: " + e.getMessage());
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private boolean addImportedSpec(DsSpec imported) {
        if (imported == null) return false;
        if (DsSpec.empty(imported.id)) imported.id = "imported_" + System.currentTimeMillis();
        if (DsMerge.findById(library, imported.id) != null) {
            imported.id = imported.id + "_" + System.currentTimeMillis();
        }
        if (DsSpec.empty(imported.name)) imported.name = imported.id;
        library.add(imported);
        return true;
    }

    private void collectJsonFiles(File dir, List<File> out, int depth) {
        if (dir == null || depth > 2) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectJsonFiles(f, out, depth + 1);
            else if (f.getName().toLowerCase(Locale.US).endsWith(".json")) out.add(f);
        }
    }

    private void showGraph() {
        List<DsSpec> all = new ArrayList<>(library);
        if (!all.contains(spec)) all.add(spec);
        showCode("العلاقات والاستدعاءات", DsMerge.graphText(all)
                + "\n\nدورات: " + DsMerge.findCycles(all));
    }

    private void saveLibrary() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir != null && !dir.exists()) dir.mkdirs();
            File out = new File(dir, "dialog_library.json");
            FileWriter writer = new FileWriter(out);
            writer.write(GSON.toJson(library));
            writer.close();
            toast("حُفظت المكتبة: " + out.getName());
        } catch (Exception e) {
            toast("فشل الحفظ: " + e.getMessage());
        }
    }

    private void loadLibrary() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            File in = new File(dir, "dialog_library.json");
            if (!in.exists()) {
                toast("لا توجد مكتبة محفوظة.");
                return;
            }
            DsSpec[] arr = GSON.fromJson(new FileReader(in), DsSpec[].class);
            library.clear();
            if (arr != null) library.addAll(Arrays.asList(arr));
            refreshLibrary();
            toast("تم تحميل " + library.size() + " دايلوك.");
        } catch (Exception e) {
            toast("فشل التحميل: " + e.getMessage());
        }
    }


    private File versionsDir() {
        File dir = new File(context.getFilesDir(), "dialog_studio_versions");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void saveVersion(DsSpec s) {
        if (s == null) return;
        try {
            File dir = versionsDir();
            String json = GSON.toJson(s);
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                StringBuilder last = new StringBuilder();
                FileReader reader = new FileReader(files[0]);
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) last.append(buf, 0, n);
                reader.close();
                if (json.equals(last.toString())) return;
            }
            String id = DsValidator.sanitizeId(s.id);
            if (id.isEmpty()) id = "dialog";
            File out = new File(dir, System.currentTimeMillis() + "_" + id + ".json");
            FileWriter writer = new FileWriter(out);
            writer.write(json);
            writer.close();
            if (files != null && files.length >= 25) {
                Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i <= files.length - 25; i++) files[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void showVersions() {
        File[] files = versionsDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            toast("لا توجد إصدارات محفوظة.");
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] labels = new String[files.length];
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (int i = 0; i < files.length; i++) {
            labels[i] = fmt.format(new Date(files[i].lastModified())) + "  —  " + files[i].getName();
        }
        new AlertDialog.Builder(activity)
                .setTitle("الإصدارات السابقة")
                .setItems(labels, (d, which) -> restoreVersion(files[which]))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void restoreVersion(File file) {
        try {
            DsSpec restored = GSON.fromJson(new FileReader(file), DsSpec.class);
            if (restored == null) {
                toast("ملف الإصدار غير صالح.");
                return;
            }
            spec = restored;
            applySpecToWidgets();
            host.refreshCanvas();
            refreshAll();
            setStatus("استُعيد الإصدار: " + file.getName());
        } catch (Exception e) {
            toast("فشل استعادة الإصدار: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- design

    private void loadCustomTemplates() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) return;
            File in = new File(dir, "templates.json");
            if (!in.exists()) return;
            DsTemplates.Template[] arr = GSON.fromJson(read(in), DsTemplates.Template[].class);
            if (arr == null) return;
            for (DsTemplates.Template t : arr) {
                if (t == null || t.id == null) continue;
                t.custom = true;
                DsTemplates.register(t);
            }
        } catch (Exception ignored) {
        }
    }

    private void showTemplateLibrary() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(4));
        templateSearch = new EditText(context);
        templateSearch.setHint("بحث في القوالب بالاسم أو النوع");
        box.addView(templateSearch);
        Spinner category = new Spinner(context);
        List<String> cats = new ArrayList<>();
        cats.add("الكل");
        cats.addAll(DsTemplates.categories());
        category.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, cats));
        box.addView(category);
        ListView list = new ListView(context);
        final List<List<DsTemplates.Template>> shown = new ArrayList<>();
        shown.add(DsTemplates.all());
        Runnable refresh = () -> {
            String q = templateSearch.getText() == null ? "" : templateSearch.getText().toString();
            String cat = category.getSelectedItemPosition() <= 0 ? "" : String.valueOf(category.getSelectedItem());
            List<DsTemplates.Template> result = new ArrayList<>();
            for (DsTemplates.Template t : DsTemplates.search(q)) {
                if (cat.isEmpty() || cat.equals(t.category)) result.add(t);
            }
            shown.set(0, result);
            String[] labels = new String[result.size()];
            for (int i = 0; i < result.size(); i++) {
                DsTemplates.Template t = result.get(i);
                labels[i] = t.nameAr + " · " + DsTemplates.layoutAr(t.layout)
                        + " · " + t.category + (t.custom ? " · مخصص" : "");
            }
            list.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_1, labels));
        };
        refresh.run();
        templateSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refresh.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refresh.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < shown.get(0).size()) showTemplateDetails(shown.get(0).get(pos));
        });
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        new AlertDialog.Builder(context)
                .setTitle("مكتبة القوالب · " + DsTemplates.all().size())
                .setView(box)
                .setPositiveButton("حفظ القالب الحالي", (d, w) -> saveCustomTemplate())
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showTemplateDetails(DsTemplates.Template t) {
        if (t == null) return;
        String details = t.descriptionAr + "\n\n"
                + "التخطيط: " + DsTemplates.layoutAr(t.layout)
                + "\nالفئة: " + t.category
                + "\nالأزرار: " + ("vertical".equals(t.buttonLayout) ? "عمودية" : "أفقية")
                + "\nالزوايا: " + ((int) t.corner) + "dp";
        new AlertDialog.Builder(context)
                .setTitle(t.nameAr)
                .setMessage(details)
                .setPositiveButton("تطبيق", (d, w) -> {
                    DsTemplates.apply(spec, t);
                    applySpecToWidgets();
                    host.refreshCanvas();
                    refreshAll();
                    setStatus("طُبق القالب: " + t.nameAr);
                })
                .setNegativeButton(t.custom ? "حذف" : "إغلاق", (d, w) -> {
                    if (t.custom) {
                        DsTemplates.unregister(t.id);
                        saveCustomTemplateFile();
                        setStatus("حُذف القالب المخصص: " + t.nameAr);
                    }
                })
                .show();
    }

    private void saveCustomTemplate() {
        String id = "custom_" + DsValidator.sanitizeId(spec.id) + "_" + System.currentTimeMillis();
        DsTemplates.Template t = DsTemplates.fromSpec(spec, id, "قالب " + spec.name);
        if (t == null) return;
        DsTemplates.register(t);
        saveCustomTemplateFile();
        setStatus("حُفظ القالب المخصص: " + t.nameAr);
    }

    private void saveCustomTemplateFile() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) return;
            dir.mkdirs();
            List<DsTemplates.Template> custom = new ArrayList<>();
            for (DsTemplates.Template t : DsTemplates.all()) if (t.custom) custom.add(t);
            write(new File(dir, "templates.json"), GSON.toJson(custom));
        } catch (Exception e) {
            toast("فشل حفظ القوالب: " + e.getMessage());
        }
    }

    private void exportTemplates() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) {
                toast("التخزين غير متاح.");
                return;
            }
            dir.mkdirs();
            File out = new File(dir, "templates.json");
            List<DsTemplates.Template> custom = new ArrayList<>();
            for (DsTemplates.Template t : DsTemplates.all()) if (t.custom) custom.add(t);
            write(out, GSON.toJson(custom));
            showCode("تصدير القوالب", out.getAbsolutePath() + "\n\n" + GSON.toJson(custom));
        } catch (Exception e) {
            toast("فشل التصدير: " + e.getMessage());
        }
    }

    private void importTemplates() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) {
                toast("التخزين غير متاح.");
                return;
            }
            File in = new File(dir, "templates.json");
            if (!in.exists()) {
                toast("لا يوجد ملف قوالب في " + in.getAbsolutePath());
                return;
            }
            String json = read(in);
            DsTemplates.Template[] arr = GSON.fromJson(json, DsTemplates.Template[].class);
            int n = 0;
            if (arr != null) {
                for (DsTemplates.Template t : arr) {
                    if (t == null || t.id == null) continue;
                    t.custom = true;
                    DsTemplates.register(t);
                    n++;
                }
            }
            setStatus("استُوردت " + n + " قالبًا.");
        } catch (Exception e) {
            toast("فشل الاستيراد: " + e.getMessage());
        }
    }

    private void applyTemplate() {
        remember();
        int idx = DsTemplates.indexOf(DsTemplates.idAt(selectedIndex(templateSelect)));
        if (idx < 0) idx = 0;
        DsTemplates.Template t = DsTemplates.get(DsTemplates.idAt(idx));
        if (t == null) return;
        DsTemplates.apply(spec, t);
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        setStatus("طُبق القالب: " + t.nameAr);
    }

    private void applyPreviewDevice(int index) {
        View frame = studioRoot == null ? null : studioRoot.findViewById(R.id.overlayPhoneFrame);
        if (frame == null) return;
        int widthDp = index == 0 ? 320 : index == 2 ? 412 : index == 3 ? 600 : 393;
        int heightDp = index == 3 ? 760 : index == 0 ? 520 : 620;
        ViewGroup.LayoutParams lp = frame.getLayoutParams();
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min((int) (widthDp * context.getResources().getDisplayMetrics().density),
                Math.max((int) (220 * context.getResources().getDisplayMetrics().density), screen - 32));
        lp.width = width;
        lp.height = (int) (heightDp * context.getResources().getDisplayMetrics().density);
        frame.setLayoutParams(lp);
        View shell = studioRoot.findViewById(R.id.overlayPhoneShell);
        if (shell != null) {
            shell.setScaleX(1f);
            shell.setScaleY(1f);
            shell.setTranslationX(0f);
            shell.setTranslationY(0f);
        }
        setStatus("حجم المعاينة: " + widthDp + "dp");
    }

    private void scalePreview(float factor) {
        View shell = studioRoot == null ? null : studioRoot.findViewById(R.id.overlayPhoneShell);
        if (shell == null) return;
        float scale = Math.max(0.55f, Math.min(1.8f, shell.getScaleX() * factor));
        shell.setPivotX(shell.getWidth() / 2f);
        shell.setPivotY(shell.getHeight() / 2f);
        shell.setScaleX(scale);
        shell.setScaleY(scale);
        setStatus("تكبير المعاينة: " + Math.round(scale * 100) + "%");
    }

    private void resetPreviewView() {
        View shell = studioRoot == null ? null : studioRoot.findViewById(R.id.overlayPhoneShell);
        if (shell == null) return;
        shell.setScaleX(1f);
        shell.setScaleY(1f);
        shell.setTranslationX(0f);
        shell.setTranslationY(0f);
        setStatus("أُعيد ضبط المعاينة.");
    }

    private void centerPreview() {
        View shell = studioRoot == null ? null : studioRoot.findViewById(R.id.overlayPhoneShell);
        if (shell == null) return;
        shell.setTranslationX(0f);
        shell.setTranslationY(0f);
        setStatus("وُسّطت المعاينة.");
    }

    private void applyMeasurements() {
        spec.widthPercent = parseFloat(text(widthInput), spec.widthPercent);
        spec.dimAmount = parseFloat(text(dimInput), spec.dimAmount);
        spec.cornerRadiusDp = parseFloat(text(cornerInput), spec.cornerRadiusDp);
        spec.borderWidthDp = parseFloat(text(borderInput), spec.borderWidthDp);
        spec.delayMs = parseInt(text(delayInput), spec.delayMs);
        setStatus("القياسات: عرض " + spec.widthPercent + "% · تعتيم "
                + spec.dimAmount + " · استدارة " + spec.cornerRadiusDp + "dp");
    }

    // ------------------------------------------------------------- widgets

    private void addWidget(String kind) {
        remember();
        DsSpec.Widget w = new DsSpec.Widget();
        w.id = uniqueWidgetId(kind);
        w.kind = kind;
        w.textSizeSp = "button".equals(kind) ? 15 : 16;
        if ("text".equals(kind)) w.text = "نص جديد";
        if ("button".equals(kind)) {
            w.text = "زر";
            w.btnBg = 0xFF6750A4;
            w.textColor = Color.WHITE;
            w.btnCornerRadiusDp = 16;
        }
        if ("input".equals(kind)) {
            w.hint = "أدخل النص";
            w.variable = "input_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.source = "input";
            spec.variables.add(v);
        }
        if ("checkbox".equals(kind) || "toggle".equals(kind)) {
            w.text = "خيار";
            w.variable = "check_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.type = "boolean";
            v.source = "input";
            spec.variables.add(v);
        }
        if ("spinner".equals(kind)) {
            w.options = new ArrayList<>(Arrays.asList("الخيار الأول", "الخيار الثاني"));
            w.variable = "choice_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.source = "input";
            spec.variables.add(v);
        }
        if ("card".equals(kind)) {
            w.text = "بطاقة";
            w.description = "وصف البطاقة";
            w.btnBg = 0x11000000;
        }
        if ("progress".equals(kind)) w.text = "جارٍ التحميل...";
        if ("image".equals(kind)) {
            w.text = "";
            w.heightDp = 96;
            w.widthPercent = 100;
        }
        if ("icon".equals(kind)) {
            w.text = "★";
            w.textSizeSp = 28;
            w.widthPercent = 0;
        }
        if ("textarea".equals(kind) || "number".equals(kind)) {
            w.hint = "textarea".equals(kind) ? "نص متعدد الأسطر" : "أدخل رقمًا";
            w.variable = "input_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.source = "input";
            spec.variables.add(v);
        }
        if ("link".equals(kind)) {
            w.text = "رابط";
            w.url = "https://";
        }
        if ("search".equals(kind) || "multiselect".equals(kind)
                || "radio".equals(kind) || "tabs".equals(kind)
                || "toolbar".equals(kind) || "pager".equals(kind)
                || "list".equals(kind)) {
            w.options = new ArrayList<>(Arrays.asList("الخيار الأول", "الخيار الثاني", "الخيار الثالث"));
            w.variable = "choice_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.source = "input";
            spec.variables.add(v);
        }
        if ("rating".equals(kind)) {
            w.variable = "rating_" + (spec.variables.size() + 1);
            DsSpec.Variable v = new DsSpec.Variable();
            v.name = w.variable;
            v.type = "number";
            v.source = "input";
            spec.variables.add(v);
        }
        w.leftDp = 16;
        w.topDp = 16 + Math.min(220, spec.widgets.size() * 56);
        spec.widgets.add(w);
        addAdvWidget(w);
        refreshWidgets(spec.widgets.size() - 1);
        loadWidgetFields(spec.widgets.size() - 1);
        setStatus("أضيف عنصر " + kind + " بالمعرف " + w.id);
    }

    private void addAdvWidget(DsSpec.Widget w) {
        OverlayInjectorUtil.AdvWidget aw = new OverlayInjectorUtil.AdvWidget();
        copyToAdv(w, aw);
        host.widgets().add(aw);
        host.refreshCanvas();
    }

    private void copyToAdv(DsSpec.Widget w, OverlayInjectorUtil.AdvWidget aw) {
        aw.id = w.id;
        aw.sourceKind = w.kind == null ? "text" : w.kind;
        aw.kind = injectableKind(w.kind);
        aw.text = w.text;
        aw.hint = w.hint;
        aw.options = w.options == null ? null : new ArrayList<>(w.options);
        aw.checked = w.checked;
        aw.textSizeSp = w.textSizeSp;
        aw.textColor = w.textColor;
        aw.fontStyle = w.fontStyle;
        aw.font = w.font;
        aw.fontPath = w.fontPath;
        aw.imageB64 = w.imageB64;
        aw.btnAction = w.btnAction;
        aw.url = w.url;
        aw.btnBg = w.btnBg;
        aw.btnBg2 = w.btnBg2;
        aw.btnCornerRadiusDp = w.btnCornerRadiusDp;
        aw.btnBorderWidthDp = w.btnBorderWidthDp;
        aw.btnBorderColor = w.btnBorderColor;
        aw.btnPaddingDp = w.btnPaddingDp;
        aw.btnAnim = w.btnAnim;
        aw.btnAnimColors = w.btnAnimColors;
        aw.btnAnimSpeedMs = w.btnAnimSpeedMs;
        aw.btnAnimRainbow = w.btnAnimRainbow;
        aw.leftDp = w.leftDp;
        aw.topDp = w.topDp;
        aw.widthPercent = w.widthPercent;
        aw.heightDp = w.heightDp;
        aw.gravity = w.gravity;
    }

    private String injectableKind(String kind) {
        if (kind == null) return "text";
        switch (kind) {
            case "textarea":
            case "number":
                return "input";
            case "link":
                return "button";
            case "search":
            case "list":
            case "radio":
            case "rating":
                return "spinner";
            case "multiselect":
                return "checkbox";
            case "pager":
            case "tabs":
            case "toolbar":
            case "icon":
                return "text";
            default:
                return kind;
        }
    }

    private void updateWidget() {
        remember();
        DsSpec.Widget w = selectedWidget();
        if (w == null) {
            toast("اختر عنصرًا.");
            return;
        }
        w.text = text(widgetText);
        w.hint = text(widgetHint);
        w.widthPercent = parseInt(text(widgetWidth), w.widthPercent);
        w.heightDp = parseInt(text(widgetHeight), w.heightDp);
        w.leftDp = parseInt(text(widgetX), w.leftDp);
        w.topDp = parseInt(text(widgetY), w.topDp);
        String raw = text(widgetOptions);
        if (!raw.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (String line : raw.split("\\n")) {
                if (!line.trim().isEmpty()) options.add(line.trim());
            }
            w.options = options;
        }
        for (OverlayInjectorUtil.AdvWidget aw : host.widgets()) {
            if (aw != null && w.id.equals(aw.id)) {
                copyToAdv(w, aw);
                break;
            }
        }
        host.refreshCanvas();
        setStatus("حُدث العنصر " + w.id);
    }

    private void deleteWidget() {
        remember();
        DsSpec.Widget w = selectedWidget();
        if (w == null) return;
        spec.widgets.remove(w);
        for (int i = host.widgets().size() - 1; i >= 0; i--) {
            OverlayInjectorUtil.AdvWidget aw = host.widgets().get(i);
            if (aw != null && w.id.equals(aw.id)) host.widgets().remove(i);
        }
        host.refreshCanvas();
        refreshWidgets();
        setStatus("حُذف العنصر " + w.id);
    }

    private void moveWidget(int delta) {
        remember();
        DsSpec.Widget w = selectedWidget();
        if (w == null) return;
        for (OverlayInjectorUtil.AdvWidget aw : host.widgets()) {
            if (aw != null && w.id.equals(aw.id)) {
                aw.topDp = Math.max(0, aw.topDp + delta * 16);
                w.topDp = aw.topDp;
                break;
            }
        }
        host.refreshCanvas();
    }

    private void loadWidgetFields(int pos) {
        DsSpec.Widget w = widgetAt(pos);
        if (w == null) return;
        if (widgetText != null) widgetText.setText(w.text);
        if (widgetHint != null) widgetHint.setText(w.hint);
        if (widgetOptions != null) {
            StringBuilder sb = new StringBuilder();
            if (w.options != null) {
                for (String o : w.options) sb.append(o).append('\n');
            }
            widgetOptions.setText(sb.toString().trim());
        }
        if (widgetWidth != null) widgetWidth.setText(String.valueOf(w.widthPercent));
        if (widgetHeight != null) widgetHeight.setText(String.valueOf(w.heightDp));
        if (widgetX != null) widgetX.setText(String.valueOf(w.leftDp));
        if (widgetY != null) widgetY.setText(String.valueOf(w.topDp));
    }

    private void duplicateWidget() {
        remember();
        DsSpec.Widget w = selectedWidget();
        if (w == null) {
            toast("اختر عنصرًا.");
            return;
        }
        DsSpec.Widget copy = GSON.fromJson(GSON.toJson(w), DsSpec.Widget.class);
        if (copy == null) return;
        copy.id = uniqueWidgetId(w.kind);
        copy.leftDp = w.leftDp + 12;
        copy.topDp = w.topDp + 12;
        spec.widgets.add(copy);
        addAdvWidget(copy);
        refreshWidgets(spec.widgets.size() - 1);
        loadWidgetFields(spec.widgets.size() - 1);
        setStatus("نُسخ العنصر " + w.id + " → " + copy.id);
    }

    private DsSpec.Widget selectedWidget() {
        int pos = selectedIndex(widgetSelect);
        return widgetAt(pos);
    }

    private DsSpec.Widget widgetAt(int pos) {
        if (pos < 0 || pos >= spec.widgets.size()) return null;
        return spec.widgets.get(pos);
    }

    private String uniqueWidgetId(String kind) {
        int n = 0;
        while (true) {
            String id = "mp_" + kind + "_" + n;
            boolean used = false;
            for (DsSpec.Widget w : spec.widgets) if (w != null && id.equals(w.id)) used = true;
            if (!used) return id;
            n++;
        }
    }

    // -------------------------------------------------------------- events

    private void addAction() {
        remember();
        DsSpec.Event event = ensureEvent();
        if (event == null) return;
        if (DsEvents.A_CALL.equals(actionType(selectedIndex(actionTypeSelect)))
                && methodAt(methodIndex) == null) {
            toast("ابحث عن دالة واخترها قبل إضافة إجراء الاستدعاء.");
            return;
        }
        DsSpec.Action a = actionFromFields();
        event.actions.add(a);
        actionIndex = event.actions.size() - 1;
        refreshActions();
        setStatus("أضيف إجراء " + a.type + " إلى " + event.event);
    }

    private void updateAction() {
        remember();
        DsSpec.Event event = DsEvents.find(spec, eventWidgetId(), eventName());
        if (event == null || actionIndex < 0 || actionIndex >= event.actions.size()) {
            toast("اختر إجراءً موجودًا أولاً.");
            return;
        }
        DsSpec.Action updated = actionFromFields();
        DsSpec.Action old = event.actions.get(actionIndex);
        if (DsEvents.A_CALL.equals(updated.type)
                && DsSpec.empty(updated.methodName) && old != null) {
            updated.methodClass = old.methodClass;
            updated.methodName = old.methodName;
            updated.methodSignature = old.methodSignature;
            updated.methodArg = DsSpec.empty(updated.methodArg) ? old.methodArg : updated.methodArg;
            updated.description = DsEvents.describeAction(updated);
        }
        event.actions.set(actionIndex, updated);
        refreshActions();
        setStatus("حُدث الإجراء.");
    }

    private void deleteAction() {
        remember();
        DsSpec.Event event = DsEvents.find(spec, eventWidgetId(), eventName());
        if (event == null || actionIndex < 0 || actionIndex >= event.actions.size()) return;
        event.actions.remove(actionIndex);
        actionIndex = -1;
        refreshActions();
        setStatus("حُذف الإجراء.");
    }

    private DsSpec.Event ensureEvent() {
        String widget = eventWidgetId();
        String event = eventName();
        if (event == null) {
            toast("اختر حدثًا.");
            return null;
        }
        DsSpec.Event e = DsEvents.find(spec, widget, event);
        if (e == null) {
            e = new DsSpec.Event();
            e.widgetId = widget;
            e.event = event;
            spec.events.add(e);
        }
        return e;
    }

    private DsSpec.Action actionFromFields() {
        DsSpec.Action a = new DsSpec.Action();
        int idx = selectedIndex(actionTypeSelect);
        a.type = actionType(idx);
        a.target = text(actionTarget);
        a.value = text(actionValue);
        a.value2 = text(actionValue);
        a.url = text(actionValue);
        String rawCondition = text(actionCondition);
        if (!rawCondition.isEmpty()
                && rawCondition.matches("^(==|!=|>=|<=|>|<|empty|notempty|contains).*")
                && !text(actionTarget).isEmpty()) {
            rawCondition = text(actionTarget) + " " + rawCondition;
        }
        a.condition = rawCondition;
        a.conditionValue = rawCondition;
        if (DsEvents.A_SET_VAR.equals(a.type)) {
            a.value = text(actionTarget);
            a.value2 = text(actionValue);
        }
        if (DsEvents.A_CALL.equals(a.type)) {
            DsNativeIndex.Method m = methodAt(methodIndex);
            if (m != null) {
                a.methodClass = m.owner;
                a.methodName = m.name;
                a.methodSignature = "(" + joinParams(m.params) + ")" + m.returnType;
                a.methodArg = text(actionValue);
            }
        }
        if (DsEvents.A_IF_VAR.equals(a.type)) {
            String cond = text(actionCondition);
            if (cond.isEmpty()) cond = "true";
            if (!cond.matches(".*(==|!=|>=|<=|>|<| empty| notempty).*")) {
                cond = text(actionTarget) + " " + cond;
            }
            a.condition = cond;
        }
        if (DsEvents.A_IF_INPUT.equals(a.type)) {
            a.target = text(actionTarget);
            String cond = text(actionCondition);
            a.condition = cond.isEmpty() ? "notempty" : cond;
        }
        if (DsEvents.A_SHOW_DIALOG.equals(a.type)) {
            a.target = text(actionTarget);
        }
        if (DsEvents.A_DELAY.equals(a.type)) {
            a.delayMs = parseInt(text(actionValue), 500);
        }
        a.description = DsEvents.describeAction(a);
        return a;
    }

    private void loadActionFields(int pos) {
        DsSpec.Event event = ensureEvent();
        if (event == null || pos < 0 || pos >= event.actions.size()) return;
        actionIndex = pos;
        DsSpec.Action a = event.actions.get(pos);
        int type = actionTypeIndex(a.type);
        if (actionTypeSelect != null) actionTypeSelect.setText(ACTION_LABELS[type]);
        if (actionTarget != null) {
            actionTarget.setText(DsEvents.A_SET_VAR.equals(a.type) ? a.value
                    : (DsEvents.A_CALL.equals(a.type) ? a.methodArg : a.target));
        }
        if (actionValue != null) {
            if (DsEvents.A_SET_VAR.equals(a.type)) actionValue.setText(a.value2);
            else if (DsEvents.A_URL.equals(a.type)) actionValue.setText(a.url);
            else if (DsEvents.A_CALL.equals(a.type)) actionValue.setText(a.methodArg);
            else if (DsEvents.A_DELAY.equals(a.type)) actionValue.setText(String.valueOf(a.delayMs));
            else actionValue.setText(a.value);
        }
        if (actionCondition != null) actionCondition.setText(a.condition);
    }

    private void refreshActions() {
        if (actionSelect == null) return;
        DsSpec.Event event = DsEvents.find(spec, eventWidgetId(), eventName());
        String[] labels;
        if (event == null || event.actions.isEmpty()) {
            labels = new String[]{"— لا إجراءات —"};
            actionIndex = -1;
        } else {
            labels = new String[event.actions.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = (i + 1) + ". " + DsEvents.describeAction(event.actions.get(i));
            }
        }
        int target = actionIndex >= 0 && actionIndex < labels.length ? actionIndex : 0;
        setSelect(actionSelect, labels, target);
        if (event != null && !event.actions.isEmpty()) loadActionFields(target);
    }

    private void checkInjectionSupport() {
        DsSpec.Event event = ensureEvent();
        if (event == null) return;
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Action a : event.actions) {
            sb.append(DsEvents.injectionSupported(event.event, a) ? "مدعوم" : "غير مدعوم")
                    .append(" · ").append(DsEvents.describeAction(a)).append('\n');
        }
        showCode("دعم الحقن", sb.length() == 0 ? "لا إجراءات." : sb.toString());
    }

    private String eventWidgetId() {
        int pos = selectedIndex(widgetSelectForEvents);
        if (pos <= 0) return "@dialog";
        int idx = pos - 1;
        return idx < spec.widgets.size() ? spec.widgets.get(idx).id : "@dialog";
    }

    private String eventName() {
        int idx = selectedIndex(eventSelect);
        String[] names = DsEvents.eventNames().toArray(new String[0]);
        return idx >= 0 && idx < names.length ? names[idx] : null;
    }

    private String actionType(int idx) {
        List<String> types = DsEvents.actionNames();
        return idx >= 0 && idx < types.size() ? types.get(idx) : DsEvents.A_TOAST;
    }

    private int actionTypeIndex(String type) {
        List<String> types = DsEvents.actionNames();
        int idx = types.indexOf(type);
        return idx < 0 ? 0 : idx;
    }

    // ------------------------------------------------------------ functions

    private void indexProject() {
        String path = text(pathInput);
        if (TextUtils.isEmpty(path)) {
            toast("حدد مسار المشروع أولاً.");
            return;
        }
        setStatus("جارٍ فهرسة الدوال...");
        new Thread(() -> {
            DsNativeIndex fresh = new DsNativeIndex();
            try {
                File root = new File(path);
                int n = 0;
                if (root.isDirectory()) {
                    n = indexDirectory(fresh, root, 0);
                } else if (root.isFile()) {
                    n = fresh.indexDex(root);
                }
                if (n > 0) {
                    synchronized (index) {
                        index.clear();
                        for (DsNativeIndex.Method m : fresh.methods()) index.add(m);
                    }
                    int found = n;
                    activity.runOnUiThread(() -> {
                        searchMethods();
                        setStatus("فُهرست " + found + " دالة من " + root.getName() + ".");
                    });
                } else {
                    activity.runOnUiThread(() -> toast("لم تُعثر دوال قابلة للفهرسة في المسار."));
                }
            } catch (Throwable t) {
                activity.runOnUiThread(() -> toast("فشل الفهرس: " + t.getMessage()));
            }
        }).start();
    }

    private int indexDirectory(DsNativeIndex target, File dir, int count) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return count;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().startsWith(".") || f.getName().equals("build")) continue;
                count = indexDirectory(target, f, count);
            } else {
                String name = f.getName();
                if (!(name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".smali"))) continue;
                String src = DsRecovery.readFile(f);
                if (src.isEmpty()) continue;
                String owner = ownerForSource(src, name);
                if (name.endsWith(".java")) target.indexJava(src, owner);
                else if (name.endsWith(".kt")) target.indexKotlin(src, owner);
                else target.indexSmali(src, owner);
                count++;
                if (count > 4000) return count;
            }
        }
        return count;
    }

    private String ownerForSource(String src, String fileName) {
        String simple = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        java.util.regex.Matcher pkg = java.util.regex.Pattern
                .compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)").matcher(src);
        String prefix = pkg.find() ? pkg.group(1) + "." : "";
        java.util.regex.Matcher type = java.util.regex.Pattern
                .compile("(?m)^\\s*(?:public\\s+|internal\\s+|open\\s+|final\\s+|abstract\\s+)*"
                        + "(?:class|object|interface|enum)\\s+([A-Za-z_][\\w]*)").matcher(src);
        if (type.find()) return prefix + type.group(1);
        return prefix + simple;
    }

    private void searchMethods() {
        methods.clear();
        methods.addAll(index.search(text(methodSearch), 60));
        String[] labels = new String[Math.max(1, methods.size())];
        if (methods.isEmpty()) labels[0] = "— لا نتائج —";
        for (int i = 0; i < methods.size(); i++) labels[i] = methods.get(i).display();
        setSelect(methodSelect, labels, 0);
        methodIndex = methods.isEmpty() ? -1 : 0;
        setStatus("نتائج الدوال: " + methods.size());
    }

    private void bindMethod() {
        DsNativeIndex.Method m = methodAt(methodIndex);
        if (m == null) {
            toast("اختر دالة أولاً.");
            return;
        }
        if (!m.supported || m.unsafeBody) {
            toast("الدالة غير مدعومة: " + (m.blockedReason.isEmpty() ? "معاملات أو جسم غير آمن" : m.blockedReason));
            return;
        }
        DsSpec.Event event = ensureEvent();
        if (event == null) return;
        DsSpec.Action a = new DsSpec.Action();
        a.type = DsEvents.A_CALL;
        a.methodClass = m.owner;
        a.methodName = m.name;
        a.methodSignature = "(" + joinParams(m.params) + ")" + m.returnType;
        a.methodArg = "";
        a.description = DsEvents.describeAction(a);
        event.actions.add(a);
        refreshActions();
        setStatus("رُبطت الدالة " + m.name + " بالحدث " + event.event);
    }

    private void showMethod() {
        DsNativeIndex.Method m = methodAt(methodIndex);
        if (m == null) return;
        showCode("تفاصيل الدالة", m.signature()
                + "\n\nالوصف: " + m.descriptor()
                + "\nالمصدر: " + m.source
                + "\nstatic: " + m.isStatic
                + "\nمدعومة: " + m.supported
                + (m.unsafeBody ? "\nسبب الحجب: " + m.blockedReason : ""));
    }

    private DsNativeIndex.Method methodAt(int pos) {
        return pos >= 0 && pos < methods.size() ? methods.get(pos) : null;
    }

    private String joinParams(List<String> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(params.get(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ variables

    private void addVariable() {
        remember();
        String name = text(varName).trim();
        if (name.isEmpty()) {
            toast("اكتب اسم المتغير.");
            return;
        }
        for (DsSpec.Variable v : spec.variables) {
            if (v != null && name.equals(v.name)) {
                v.value = text(varValue);
                setStatus("حُدث المتغير " + name);
                return;
            }
        }
        DsSpec.Variable v = new DsSpec.Variable();
        v.name = name;
        v.value = text(varValue);
        spec.variables.add(v);
        setStatus("أضيف المتغير " + name);
    }

    private void removeVariable() {
        remember();
        String name = text(varName).trim();
        for (int i = spec.variables.size() - 1; i >= 0; i--) {
            DsSpec.Variable v = spec.variables.get(i);
            if (v != null && name.equals(v.name)) {
                spec.variables.remove(i);
                setStatus("حُذف المتغير " + name);
                return;
            }
        }
        toast("لم يُعثر على المتغير.");
    }

    private void showVariables() {
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Variable v : spec.variables) {
            if (v == null) continue;
            sb.append(v.name).append(" = ").append(v.value)
                    .append(" (").append(v.type).append(", ").append(v.source).append(")\n");
        }
        showCode("المتغيرات", sb.length() == 0 ? "لا متغيرات." : sb.toString());
    }

    private void refreshVariableText() {
        Map<String, String> runtime = new HashMap<>();
        for (DsSpec.Variable v : spec.variables) {
            if (v != null && !DsSpec.empty(v.name)) runtime.put(v.name, v.value == null ? "" : v.value);
        }
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null || "input".equals(w.kind)) continue;
            w.text = DsVars.resolve(w.text, spec, runtime);
        }
        applySpecToWidgets();
        host.refreshCanvas();
        setStatus("حُدثت النصوص من المتغيرات.");
    }

    private void addLogic() {
        remember();
        DsSpec.Logic l = new DsSpec.Logic();
        l.fromWidget = widgetIdAt(selectedIndex(logicFrom));
        l.toWidget = widgetIdAt(selectedIndex(logicTo));
        l.operation = "SET_TEXT";
        l.template = text(logicTemplate);
        spec.logic.add(l);
        setStatus("أضيف ربط " + l.fromWidget + " ← " + l.toWidget);
    }

    private void showLogic() {
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Logic l : spec.logic) {
            if (l == null) continue;
            sb.append(l.fromWidget).append(" ← ").append(l.toWidget)
                    .append(" · ").append(l.operation)
                    .append(" · ").append(l.template).append('\n');
        }
        showCode("الروابط المنطقية", sb.length() == 0 ? "لا روابط." : sb.toString());
    }

    private String widgetIdAt(int pos) {
        if (pos <= 0) return "@dialog";
        int idx = pos - 1;
        return idx < spec.widgets.size() ? spec.widgets.get(idx).id : "@dialog";
    }

    // ------------------------------------------------------------ automation

    private void addAutomation() {
        remember();
        DsSpec.Automation a = new DsSpec.Automation();
        List<String> triggers = DsAutomation.triggers();
        int idx = selectedIndex(autoTrigger);
        a.trigger = idx >= 0 && idx < triggers.size() ? triggers.get(idx) : DsAutomation.ACTIVITY_OPEN;
        a.activity = text(autoActivity);
        a.delayMs = parseInt(text(autoDelay), 0);
        a.intervalMs = parseInt(text(autoInterval), 0);
        spec.automations.add(a);
        setStatus("أضيفت قاعدة أتمتة: " + DsAutomation.describe(a.trigger));
    }

    private void showAutomations() {
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Automation a : spec.automations) {
            if (a == null) continue;
            sb.append(DsAutomation.describe(a.trigger))
                    .append(" · شاشة ").append(a.activity)
                    .append(" · تأخير ").append(a.delayMs)
                    .append(" · فاصل ").append(a.intervalMs).append('\n');
        }
        List<String> issues = DsAutomation.validate(spec);
        if (!issues.isEmpty()) sb.append("\nملاحظات:\n- ").append(String.join("\n- ", issues));
        showCode("قواعد الأتمتة", sb.length() == 0 ? "لا قواعد." : sb.toString());
    }

    // --------------------------------------------------------------- testing

    private void validate() {
        DsValidator.Report report = DsValidator.validate(spec, index);
        showCode("تقرير الفحص", DsValidator.reportText(report)
                + "\n\n" + report.summaryAr());
        setStatus(report.summaryAr());
    }

    private void fix() {
        remember();
        DsValidator.Report report = DsValidator.fix(spec, index);
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        showCode("إصلاح تلقائي", DsValidator.reportText(report)
                + "\n\n" + report.summaryAr());
        setStatus("تم الإصلاح: " + report.fixable() + " عنصر.");
    }

    private void preview() {
        applyMeasurements();
        applySpecToWidgets();
        host.refreshCanvas();
        setStatus("حُدثت المعاينة الأصلية داخل إطار iPhone.");
    }

    private void exportProject() {
        try {
            File dir = context.getExternalFilesDir("DialogStudio");
            if (dir == null) {
                toast("التخزين غير متاح.");
                return;
            }
            File out = new File(dir, "export_" + spec.id);
            out.mkdirs();
            String pkg = DsSpec.empty(spec.packageName) ? "mp.dialog.generated" : spec.packageName;
            File srcDir = new File(out, "src/" + pkg.replace('.', '/'));
            File layoutDir = new File(out, "res/layout");
            File drawableDir = new File(out, "res/drawable");
            srcDir.mkdirs();
            layoutDir.mkdirs();
            drawableDir.mkdirs();
            write(new File(layoutDir, "dialog_" + spec.id + ".xml"), DsCodeGen.layoutXml(spec));
            write(new File(drawableDir, "bg_" + spec.id + ".xml"), DsCodeGen.drawableXml(spec));
            write(new File(srcDir, DsCodeGen.className(spec) + ".java"),
                    DsCodeGen.javaSource(spec, libraryMap()));
            write(new File(out, "dialog_" + spec.id + ".json"), GSON.toJson(spec));
            write(new File(out, "README.txt"),
                    "MP Manager Dialog Studio export\n"
                            + "Package: " + pkg + "\n"
                            + "Class: " + DsCodeGen.className(spec) + "\n"
                            + "Layout: res/layout/dialog_" + spec.id + ".xml\n"
                            + "Drawable: res/drawable/bg_" + spec.id + ".xml\n"
                            + "Spec: dialog_" + spec.id + ".json\n");
            toast("صُدر المشروع الكامل إلى " + out.getAbsolutePath());
        } catch (Exception e) {
            toast("فشل التصدير: " + e.getMessage());
        }
    }

    private void showSummary() {
        showCode("ملخص الدايلوك", "المعرف: " + spec.id
                + "\nالقالب: " + spec.templateId
                + "\nالعناصر: " + spec.widgets.size()
                + "\nالمتغيرات: " + spec.variables.size()
                + "\nالأحداث: " + spec.events.size()
                + "\nالروابط: " + spec.logic.size()
                + "\nالأتمتة: " + spec.automations.size()
                + "\n\n" + DsTemplates.summary(spec));
    }

    // ------------------------------------------------------------------- AI

    private void runAi() {
        String prompt = text(aiPrompt);
        if (prompt.trim().isEmpty()) {
            toast("اكتب وصفًا للمطلوب.");
            return;
        }
        int idx = selectedIndex(aiMode);
        String[] modes = {DsAi.MODE_CREATE, DsAi.MODE_REDESIGN, DsAi.MODE_EVENTS,
                DsAi.MODE_CONVERT, DsAi.MODE_FIX, DsAi.MODE_EXPLAIN,
                DsAi.MODE_SCENARIO, DsAi.MODE_FUNCTIONS};
        String mode = idx >= 0 && idx < modes.length ? modes[idx] : DsAi.MODE_REDESIGN;
        beforeAi = GSON.fromJson(GSON.toJson(spec), DsSpec.class);
        setStatus("جارٍ إرسال الطلب إلى الذكاء الاصطناعي...");
        String full = DsAi.buildPrompt(spec, mode, prompt, index.methods());
        DsAiClient.request(context, full, (text, error) -> {
            if (error != null) {
                toast("فشل الذكاء الاصطناعي: " + error);
                return;
            }
            aiPatch = DsAi.parse(text);
            if (aiPatch == null) {
                toast("تعذر قراءة رد الذكاء الاصطناعي.");
                return;
            }
            DsSpec after = DsAi.apply(beforeAi, aiPatch, aiPatch.replaceWidgets);
            showCode("التعديل المقترح", DsAi.diffSummary(beforeAi, after)
                    + "\n\n" + (aiPatch.notes == null ? "" : aiPatch.notes)
                    + "\n\nاعتمد التعديل من زر (اعتماد التعديل).");
            setStatus("وصل الاقتراح. راجع الفرق ثم اعتمده.");
        });
    }

    private void applyAi() {
        remember();
        if (aiPatch == null || beforeAi == null) {
            toast("شغّل الذكاء الاصطناعي أولاً.");
            return;
        }
        spec = DsAi.apply(beforeAi, aiPatch, aiPatch.replaceWidgets);
        applySpecToWidgets();
        host.refreshCanvas();
        refreshAll();
        setStatus("اعتُمد تعديل الذكاء الاصطناعي.");
    }

    private void showAiDiff() {
        if (aiPatch == null || beforeAi == null) {
            toast("لا يوجد اقتراح بعد.");
            return;
        }
        showCode("فرق التعديل", DsAi.diffSummary(beforeAi,
                DsAi.apply(beforeAi, aiPatch, aiPatch.replaceWidgets)));
    }

    // --------------------------------------------------------------- shared

    private void applySpecToWidgets() {
        List<OverlayInjectorUtil.AdvWidget> widgets = host.widgets();
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            OverlayInjectorUtil.AdvWidget found = null;
            for (OverlayInjectorUtil.AdvWidget aw : widgets) {
                if (aw != null && w.id.equals(aw.id)) {
                    found = aw;
                    break;
                }
            }
            if (found == null) {
                found = new OverlayInjectorUtil.AdvWidget();
                widgets.add(found);
            }
            copyToAdv(w, found);
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            OverlayInjectorUtil.AdvWidget aw = widgets.get(i);
            if (aw == null || aw.id == null || !aw.id.startsWith("mp_")) continue;
            boolean exists = false;
            for (DsSpec.Widget w : spec.widgets) {
                if (w != null && aw.id.equals(w.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) widgets.remove(i);
        }
    }

    private void syncFromWidgets() {
        List<OverlayInjectorUtil.AdvWidget> widgets = host.widgets();
        int counter = 0;
        for (OverlayInjectorUtil.AdvWidget aw : widgets) {
            if (aw == null) continue;
            if ("title".equals(aw.kind) || "message".equals(aw.kind)) continue;
            if (DsSpec.empty(aw.id)) aw.id = uniqueWidgetId(aw.kind == null ? "text" : aw.kind);
            DsSpec.Widget w = null;
            for (DsSpec.Widget candidate : spec.widgets) {
                if (candidate != null && aw.id.equals(candidate.id)) {
                    w = candidate;
                    break;
                }
            }
            if (w == null) {
                w = new DsSpec.Widget();
                w.id = aw.id;
                spec.widgets.add(w);
            }
            w.kind = DsSpec.empty(aw.sourceKind) ? (aw.kind == null ? "text" : aw.kind) : aw.sourceKind;
            w.text = aw.text == null ? "" : aw.text;
            w.hint = aw.hint == null ? "" : aw.hint;
            w.options = aw.options == null ? null : new ArrayList<>(aw.options);
            w.checked = aw.checked;
            w.textSizeSp = aw.textSizeSp;
            w.textColor = aw.textColor;
            w.font = aw.font == null ? "" : aw.font;
            w.fontPath = aw.fontPath == null ? "" : aw.fontPath;
            w.imageB64 = aw.imageB64 == null ? "" : aw.imageB64;
            w.btnAction = aw.btnAction == null ? "" : aw.btnAction;
            w.url = aw.url == null ? "" : aw.url;
            w.btnBg = aw.btnBg;
            w.btnBg2 = aw.btnBg2;
            w.btnCornerRadiusDp = aw.btnCornerRadiusDp;
            w.btnBorderWidthDp = aw.btnBorderWidthDp;
            w.btnBorderColor = aw.btnBorderColor;
            w.btnPaddingDp = aw.btnPaddingDp;
            w.btnAnim = aw.btnAnim;
            w.btnAnimColors = aw.btnAnimColors;
            w.btnAnimSpeedMs = aw.btnAnimSpeedMs;
            w.btnAnimRainbow = aw.btnAnimRainbow;
            w.leftDp = aw.leftDp;
            w.topDp = aw.topDp;
            w.widthPercent = aw.widthPercent;
            w.heightDp = aw.heightDp;
            w.gravity = aw.gravity;
            counter++;
        }
        if (!widgets.isEmpty()) {
            for (int i = spec.widgets.size() - 1; i >= 0; i--) {
                DsSpec.Widget w = spec.widgets.get(i);
                if (w == null || w.id == null || !w.id.startsWith("mp_")) continue;
                boolean exists = false;
                for (OverlayInjectorUtil.AdvWidget aw : widgets) {
                    if (aw != null && w.id.equals(aw.id)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) spec.widgets.remove(i);
            }
        }
    }

    private void refreshAll() {
        refreshWidgets();
        refreshLibrary();
        refreshActions();
        if (templateSelect != null) {
            templateSelect.setText(DsTemplates.namesAr()[Math.max(0,
                    DsTemplates.indexOf(spec.templateId))]);
        }
        if (widthInput != null) widthInput.setText(String.valueOf(spec.widthPercent));
        if (dimInput != null) dimInput.setText(String.valueOf(spec.dimAmount));
        if (cornerInput != null) cornerInput.setText(String.valueOf(spec.cornerRadiusDp));
        if (borderInput != null) borderInput.setText(String.valueOf(spec.borderWidthDp));
        if (delayInput != null) delayInput.setText(String.valueOf(spec.delayMs));
        syncStyleFields();
        setStatus("جاهز · " + spec.widgets.size() + " عنصر · "
                + spec.events.size() + " حدث");
    }

    private void refreshWidgets() {
        refreshWidgets(-1);
    }

    private void refreshWidgets(int preferredIndex) {
        int current = selectedIndex(widgetSelect);
        int target = preferredIndex >= 0 ? preferredIndex : current;
        if (target < 0 || target >= spec.widgets.size()) target = 0;
        setSelect(widgetSelect, widgetLabels(), target);
        int eventCurrent = selectedIndex(widgetSelectForEvents);
        if (eventCurrent < 0 || eventCurrent > spec.widgets.size()) eventCurrent = 0;
        setSelect(widgetSelectForEvents, eventWidgetLabels(), eventCurrent);
        int fromCurrent = selectedIndex(logicFrom);
        if (fromCurrent < 0 || fromCurrent > spec.widgets.size()) fromCurrent = 0;
        setSelect(logicFrom, eventWidgetLabels(), fromCurrent);
        int toCurrent = selectedIndex(logicTo);
        if (toCurrent < 0 || toCurrent > spec.widgets.size()) toCurrent = 0;
        setSelect(logicTo, eventWidgetLabels(), toCurrent);
    }

    private void refreshLibrary() {
        String query = librarySearch == null ? "" : text(librarySearch).trim().toLowerCase(Locale.US);
        visibleLibrary.clear();
        for (DsSpec s : library) {
            if (s == null) continue;
            String haystack = (s.id + " " + s.name + " " + s.title + " " + s.message)
                    .toLowerCase(Locale.US);
            if (query.isEmpty() || haystack.contains(query)) visibleLibrary.add(s);
        }
        String[] labels = new String[Math.max(1, visibleLibrary.size())];
        if (visibleLibrary.isEmpty()) labels[0] = "— لا نتائج —";
        for (int i = 0; i < visibleLibrary.size(); i++) {
            DsSpec s = visibleLibrary.get(i);
            labels[i] = (s.name == null || s.name.isEmpty() ? s.id : s.name)
                    + " · " + (s.widgets == null ? 0 : s.widgets.size()) + " عنصر";
        }
        libraryIndex = -1;
        setSelect(librarySelect, labels, 0);
    }

    private String[] widgetLabels() {
        String[] labels = new String[Math.max(1, spec.widgets.size())];
        if (spec.widgets.isEmpty()) labels[0] = "— لا عناصر —";
        for (int i = 0; i < spec.widgets.size(); i++) {
            DsSpec.Widget w = spec.widgets.get(i);
            labels[i] = (i + 1) + ". " + w.kind + " · " + w.id
                    + (DsSpec.empty(w.text) ? "" : " · " + shortText(w.text));
        }
        return labels;
    }

    private String[] eventWidgetLabels() {
        String[] labels = new String[spec.widgets.size() + 1];
        labels[0] = "@dialog (الدايلوك نفسه)";
        for (int i = 0; i < spec.widgets.size(); i++) {
            DsSpec.Widget w = spec.widgets.get(i);
            labels[i + 1] = w.id + (DsSpec.empty(w.text) ? "" : " · " + shortText(w.text));
        }
        return labels;
    }

    private String shortText(String text) {
        if (text == null) return "";
        return text.length() <= 24 ? text : text.substring(0, 24) + "…";
    }

    private void setSelect(MaterialAutoCompleteTextView view, String[] values, int selected) {
        if (view == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, values);
        view.setAdapter(adapter);
        if (values.length > 0) {
            view.setText(values[Math.max(0, Math.min(values.length - 1, selected))]);
        }
    }

    private int selectedIndex(MaterialAutoCompleteTextView view) {
        if (view == null) return -1;
        String value = view.getText() == null ? "" : view.getText().toString();
        android.widget.ListAdapter adapter = view.getAdapter();
        if (adapter == null) return -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.equals(String.valueOf(adapter.getItem(i)))) return i;
        }
        return -1;
    }

    private String text(EditText edit) {
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, DsSpec> libraryMap() {
        Map<String, DsSpec> map = new HashMap<>();
        for (DsSpec s : library) if (s != null && !DsSpec.empty(s.id)) map.put(s.id, s);
        if (!DsSpec.empty(spec.id)) map.put(spec.id, spec);
        return map;
    }

    private void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
    }

    private String read(File file) throws Exception {
        FileReader reader = new FileReader(file);
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) > 0) sb.append(buffer, 0, n);
        reader.close();
        return sb.toString();
    }


    private void editSource() {
        editSourceText("محرر XML", DsCodeGen.layoutXml(spec), "xml");
    }

    private void editSourceText(String title, String source, String ext) {
        EditText input = new EditText(activity);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(12);
        input.setMinLines(12);
        input.setText(source);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(input);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("تطبيق", (d, w) -> {
                    String text = input.getText().toString();
                    List<DsRecovery.Found> parsed = DsRecovery.scanText("dialog_editor." + ext, text);
                    if (parsed.isEmpty()) {
                        toast("لم يتعرف المحرك على دايلوك في المصدر.");
                        return;
                    }
                    DsSpec restored = DsRecovery.toSpec(parsed.get(0));
                    if (restored == null) {
                        toast("تعذر تحويل المصدر إلى نموذج.");
                        return;
                    }
                    spec = restored;
                    applySpecToWidgets();
                    host.refreshCanvas();
                    refreshAll();
                    setStatus("طُبق المصدر على المحرر.");
                })
                .setNeutralButton("نسخ", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dialog", input.getText()));
                    toast("نُسخ المصدر.");
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showCode(String title, String content) {
        TextView text = new TextView(context);
        text.setText(content == null ? "" : content);
        text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setPadding(dp(12), dp(12), dp(12), dp(12));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(text);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("نسخ", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText(title, content == null ? "" : content));
                    toast("نُسخ إلى الحافظة.");
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }
}
