package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Structural analysis, reporting and safe automatic repair. */
public final class DsValidator {

    public static final String ERROR = "ERROR";
    public static final String WARN = "WARN";
    public static final String INFO = "INFO";

    private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static class Issue {
        public String severity = WARN;
        public String code = "";
        public String message = "";
        public String target = "";
        public boolean fixable = false;

        public Issue(String severity, String code, String message, String target, boolean fixable) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.target = target;
            this.fixable = fixable;
        }
    }

    public static class Report {
        public final List<Issue> issues = new ArrayList<>();

        public void add(String severity, String code, String message, String target, boolean fixable) {
            issues.add(new Issue(severity, code, message, target, fixable));
        }

        public int errors() {
            int n = 0;
            for (Issue i : issues) if (ERROR.equals(i.severity)) n++;
            return n;
        }

        public int warnings() {
            int n = 0;
            for (Issue i : issues) if (WARN.equals(i.severity)) n++;
            return n;
        }

        public int fixable() {
            int n = 0;
            for (Issue i : issues) if (i.fixable) n++;
            return n;
        }

        public boolean ok() {
            return errors() == 0;
        }

        public String summaryAr() {
            return "أخطاء: " + errors() + " · تحذيرات: " + warnings()
                    + " · قابلة للإصلاح: " + fixable();
        }
    }

    private DsValidator() {
    }

    public static Report validate(DsSpec spec, DsNativeIndex index) {
        Report r = new Report();
        if (spec == null) {
            r.add(ERROR, "spec_missing", "لا توجد مواصفات دايلوك.", "", false);
            return r;
        }
        if (DsSpec.empty(spec.id)) {
            r.add(WARN, "id_missing", "الدايلوك بلا معرّف؛ سيُولَّد تلقائيًا.", "", true);
        } else if (!ID.matcher(spec.id).matches()) {
            r.add(ERROR, "id_invalid", "معرّف الدايلوك غير صالح: " + spec.id, spec.id, true);
        }
        if (DsSpec.empty(spec.title) && DsSpec.empty(spec.message)) {
            r.add(WARN, "content_empty", "العنوان والرسالة فارغان.", "", true);
        }
        if (!DsTemplates.has(spec.templateId)) {
            r.add(WARN, "template_unknown", "قالب غير معروف: " + spec.templateId, "", true);
        }
        if (spec.cornerRadiusDp < 0 || spec.cornerRadiusDp > 64) {
            r.add(WARN, "corner_range", "نصف قطر الزوايا خارج المدى 0..64.", "", true);
        }
        if (spec.borderWidthDp < 0 || spec.borderWidthDp > 16) {
            r.add(WARN, "border_range", "سماكة الحدّ خارج المدى 0..16.", "", true);
        }
        if (spec.delayMs < 0 || spec.delayMs > 600000) {
            r.add(WARN, "delay_range", "التأخير خارج المدى 0..600000ms.", "", true);
        }
        if (spec.widthPercent < 40 || spec.widthPercent > 100) {
            r.add(WARN, "width_range", "نسبة العرض خارج المدى 40..100.", "", true);
        }
        if (spec.dimAmount < 0 || spec.dimAmount > 1) {
            r.add(WARN, "dim_range", "تعتيم الخلفية خارج المدى 0..1.", "", true);
        }
        if (spec.widgets == null || spec.widgets.isEmpty()) {
            r.add(ERROR, "widgets_empty", "لا توجد عناصر في الدايلوك.", "", false);
        } else {
            Set<String> ids = new LinkedHashSet<>();
            int i = 0;
            for (DsSpec.Widget w : spec.widgets) {
                if (w == null) {
                    r.add(ERROR, "widget_null", "عنصر فارغ في القائمة.", "", true);
                    continue;
                }
                if (DsSpec.empty(w.id)) {
                    r.add(WARN, "widget_id_missing", "عنصر " + w.kind + " بلا معرّف.", "", true);
                } else if (!ID.matcher(w.id).matches()) {
                    r.add(ERROR, "widget_id_invalid", "معرّف عنصر غير صالح: " + w.id, w.id, true);
                } else if (!ids.add(w.id)) {
                    r.add(ERROR, "widget_id_duplicate", "معرّف عنصر مكرر: " + w.id, w.id, true);
                }
                if (("text".equals(w.kind) || "button".equals(w.kind)) && DsSpec.empty(w.text)) {
                    r.add(WARN, "widget_text_empty", "العنصر " + w.id + " بلا نص.", w.id, true);
                }
                if ("input".equals(w.kind) && DsSpec.empty(w.hint) && DsSpec.empty(w.text)) {
                    r.add(WARN, "input_hint_empty", "حقل الإدخال " + w.id + " بلا تلميح.", w.id, true);
                }
                if ("spinner".equals(w.kind) && (w.options == null || w.options.isEmpty())) {
                    r.add(WARN, "spinner_options_empty", "القائمة " + w.id + " بلا خيارات.", w.id, true);
                }
                if (w.textSizeSp <= 0 || w.textSizeSp > 96) {
                    r.add(WARN, "text_size_range", "حجم نص غير صالح في " + w.id + ".", w.id, true);
                }
                i++;
            }
            if (i == 0) r.add(ERROR, "widgets_empty", "لا توجد عناصر صالحة.", "", false);
        }
        if (spec.events != null) {
            for (String s : DsEvents.validate(spec, index)) r.add(ERROR, "event", s, "", true);
            Set<String> pairs = new LinkedHashSet<>();
            for (DsSpec.Event e : spec.events) {
                if (e == null) continue;
                String key = e.widgetId + "|" + e.event;
                if (!pairs.add(key)) {
                    r.add(WARN, "event_duplicate", "حدث مكرر لنفس العنصر: " + key, key, true);
                }
                if (DsEvents.TIMEOUT.equals(e.event) && (e.timeoutMs <= 0 || e.timeoutMs > 600000)) {
                    r.add(WARN, "timeout_range", "مهلة غير صالحة في " + key + ".", key, true);
                }
            }
        }
        if (spec.variables != null) {
            Set<String> varNames = new LinkedHashSet<>();
            for (DsSpec.Variable v : spec.variables) {
                if (v == null || DsSpec.empty(v.name)) continue;
                if (!varNames.add(v.name)) {
                    r.add(WARN, "variable_duplicate", "متغير مكرر: " + v.name, v.name, true);
                }
                if (!"string".equals(v.type) && !"number".equals(v.type)
                        && !"boolean".equals(v.type) && !"list".equals(v.type)) {
                    r.add(WARN, "variable_type", "نوع متغير غير معروف: " + v.name, v.name, true);
                }
            }
        }
        for (String s : DsVars.validate(spec)) r.add(WARN, "variable", s, "", true);
        if (spec.automations != null) {
            for (DsSpec.Automation a : spec.automations) {
                if (a == null || !a.enabled) continue;
                if ("ACTIVITY_OPEN".equals(a.trigger) && DsSpec.empty(a.activity)) {
                    r.add(WARN, "automation_activity", "قاعدة الأتمتة تحتاج شاشة محددة.", "", true);
                }
                if (a.delayMs < 0 || a.delayMs > 600000) {
                    r.add(WARN, "automation_delay", "تأخير أتمتة غير صالح.", "", true);
                }
                if ("TIMER".equals(a.trigger) && (a.intervalMs <= 0 || a.intervalMs > 3600000)) {
                    r.add(WARN, "automation_interval", "فاصل مؤقت غير صالح.", "", true);
                }
            }
        }
        if (spec.logic != null) {
            for (DsSpec.Logic l : spec.logic) {
                if (l == null) continue;
                if (!DsSpec.empty(l.fromWidget) && spec.findWidget(l.fromWidget) == null) {
                    r.add(WARN, "logic_from", "مصدر منطقي غير موجود: " + l.fromWidget, l.fromWidget, true);
                }
                if (!DsSpec.empty(l.toWidget) && spec.findWidget(l.toWidget) == null) {
                    r.add(WARN, "logic_to", "هدف منطقي غير موجود: " + l.toWidget, l.toWidget, true);
                }
            }
        }
        if (spec.events != null) {
            for (DsSpec.Event e : spec.events) {
                if (e == null) continue;
                for (DsSpec.Action a : e.actions) {
                    if (a == null) continue;
                    if (DsEvents.A_SHOW_DIALOG.equals(a.type) && DsSpec.eq(a.target, spec.id)) {
                        r.add(ERROR, "circular_dialog", "الدايلوك يستدعي نفسه: " + spec.id, spec.id, true);
                    }
                    if (DsEvents.A_SET_TEXT.equals(a.type) && spec.findWidget(a.target) == null) {
                        r.add(WARN, "action_target", "هدف SET_TEXT غير موجود: " + a.target, a.target, true);
                    }
                    if (DsEvents.A_SET_VAR.equals(a.type) && spec.findVariable(a.target) == null) {
                        r.add(WARN, "action_variable", "متغير SET_VAR غير موجود: " + a.target, a.target, true);
                    }
                    if (DsEvents.A_SHOW_DIALOG.equals(a.type) && DsSpec.empty(a.target)) {
                        r.add(WARN, "action_dialog", "SHOW_DIALOG بلا هدف.", "", true);
                    }
                    if (DsEvents.A_CALL.equals(a.type) && index != null && index.validate(a) != null) {
                        r.add(WARN, "action_call", "استدعاء غير صالح: " + index.validate(a), a.target, true);
                    }
                }
            }
        }
        return r;
    }

    /** Repairs everything that can be repaired without guessing user intent. */
    public static Report fix(DsSpec spec, DsNativeIndex index) {
        if (spec == null) return validate(null, index);
        if (DsSpec.empty(spec.id) || !ID.matcher(spec.id).matches()) {
            spec.id = "mp_dialog_" + Math.abs((spec.name + spec.title).hashCode() % 100000);
        }
        if (!DsTemplates.has(spec.templateId)) {
            spec.templateId = "ios_alert";
        }
        spec.cornerRadiusDp = clamp(spec.cornerRadiusDp, 0f, 64f);
        spec.borderWidthDp = clamp(spec.borderWidthDp, 0f, 16f);
        spec.delayMs = clampInt(spec.delayMs, 0, 600000);
        spec.widthPercent = clamp(spec.widthPercent, 40f, 100f);
        spec.dimAmount = clamp(spec.dimAmount, 0f, 1f);
        if (DsSpec.empty(spec.title) && DsSpec.empty(spec.message)) spec.title = "تنبيه";
        if (DsSpec.empty(spec.positive) && DsSpec.empty(spec.negative)
                && DsSpec.empty(spec.neutral)) spec.positive = "OK";
        if (spec.widgets == null) spec.widgets = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int i = 0;
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            if (DsSpec.empty(w.id) || !ID.matcher(w.id).matches()) {
                w.id = DsSpec.newWidgetId(w.kind, i);
            }
            String base = w.id;
            int n = 2;
            while (!ids.add(w.id)) {
                w.id = base + "_" + (n++);
            }
            if (("text".equals(w.kind) || "button".equals(w.kind)) && DsSpec.empty(w.text)) {
                w.text = "text".equals(w.kind) ? "نص" : "زر";
            }
            if ("input".equals(w.kind) && DsSpec.empty(w.hint) && DsSpec.empty(w.text)) {
                w.hint = "أدخل قيمة";
            }
            if (w.textSizeSp <= 0 || w.textSizeSp > 96) w.textSizeSp = 16f;
            i++;
        }
        spec.widgets.removeIf(w -> w == null);
        if (spec.variables == null) spec.variables = new ArrayList<>();
        Set<String> vars = new LinkedHashSet<>();
        List<DsSpec.Variable> cleanVars = new ArrayList<>();
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            if (!v.name.matches("[A-Za-z_][A-Za-z0-9_]{0,47}")) continue;
            if (!vars.add(v.name)) continue;
            if (v.type == null) v.type = "string";
            if (v.source == null) v.source = "static";
            if ("number".equals(v.type) && !DsSpec.empty(v.value)) {
                try {
                    Double.parseDouble(v.value);
                } catch (Exception e) {
                    v.value = "0";
                }
            }
            if ("boolean".equals(v.type) && !DsSpec.empty(v.value)
                    && !"true".equalsIgnoreCase(v.value) && !"false".equalsIgnoreCase(v.value)) {
                v.value = "false";
            }
            cleanVars.add(v);
        }
        spec.variables = cleanVars;
        if (spec.events == null) spec.events = new ArrayList<>();
        List<DsSpec.Event> cleanEvents = new ArrayList<>();
        Set<String> pairs = new LinkedHashSet<>();
        for (DsSpec.Event e : spec.events) {
            if (e == null) continue;
            if (!e.enabled) {
                cleanEvents.add(e);
                continue;
            }
            if (!"@dialog".equals(e.widgetId) && spec.findWidget(e.widgetId) == null) continue;
            if (!DsEvents.eventNames().contains(e.event)) continue;
            if (!pairs.add(e.widgetId + "|" + e.event)) continue;
            if (e.actions == null) e.actions = new ArrayList<>();
            List<DsSpec.Action> cleanActions = new ArrayList<>();
            for (DsSpec.Action a : e.actions) {
                if (a == null || DsSpec.empty(a.type)) continue;
                if (DsEvents.A_URL.equals(a.type) && DsSpec.empty(a.url)) continue;
                if (DsEvents.A_TOAST.equals(a.type) && DsSpec.empty(a.value)) continue;
                if (DsEvents.A_SET_TEXT.equals(a.type)
                        && (DsSpec.empty(a.target) || spec.findWidget(a.target) == null)) continue;
                if (DsEvents.A_SET_VAR.equals(a.type)
                        && (DsSpec.empty(a.target) || spec.findVariable(a.target) == null)) continue;
                if (DsEvents.A_SHOW_DIALOG.equals(a.type) && DsSpec.empty(a.target)) continue;
                if (DsEvents.A_CALL.equals(a.type) && index != null && index.size() > 0
                        && index.validate(a) != null) continue;
                if (DsEvents.A_SHOW_DIALOG.equals(a.type) && DsSpec.eq(a.target, spec.id)) continue;
                cleanActions.add(a);
            }
            e.actions = cleanActions;
            if (!e.actions.isEmpty()) {
                if (DsEvents.TIMEOUT.equals(e.event) && (e.timeoutMs <= 0 || e.timeoutMs > 600000)) {
                    e.timeoutMs = 5000;
                }
                cleanEvents.add(e);
            }
        }
        spec.events = cleanEvents;
        if (spec.automations == null) spec.automations = new ArrayList<>();
        List<DsSpec.Automation> cleanAuto = new ArrayList<>();
        for (DsSpec.Automation a : spec.automations) {
            if (a == null) continue;
            if (!a.enabled) {
                cleanAuto.add(a);
                continue;
            }
            if ("ACTIVITY_OPEN".equals(a.trigger) && DsSpec.empty(a.activity)) continue;
            a.delayMs = clampInt(a.delayMs, 0, 600000);
            if ("TIMER".equals(a.trigger)) {
                a.intervalMs = clampInt(a.intervalMs, 1000, 3600000);
            }
            cleanAuto.add(a);
        }
        spec.automations = cleanAuto;
        if (spec.logic == null) spec.logic = new ArrayList<>();
        List<DsSpec.Logic> cleanLogic = new ArrayList<>();
        for (DsSpec.Logic l : spec.logic) {
            if (l == null) continue;
            if (!DsSpec.empty(l.fromWidget) && spec.findWidget(l.fromWidget) == null) continue;
            if (!DsSpec.empty(l.toWidget) && spec.findWidget(l.toWidget) == null) continue;
            cleanLogic.add(l);
        }
        spec.logic = cleanLogic;
        return validate(spec, index);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static String reportText(Report report) {
        if (report == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(report.summaryAr()).append("\n\n");
        if (report.issues.isEmpty()) {
            sb.append("لا توجد مشاكل. البنية سليمة وجاهزة للتنفيذ.");
            return sb.toString();
        }
        String lastSeverity = "";
        for (Issue i : report.issues) {
            String label = ERROR.equals(i.severity) ? "خطأ" : WARN.equals(i.severity) ? "تحذير" : "معلومة";
            if (!label.equals(lastSeverity)) {
                sb.append("• ").append(label).append(":\n");
                lastSeverity = label;
            }
            sb.append("   - ").append(i.message);
            if (i.fixable) sb.append(" (قابلة للإصلاح)");
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String severityLabel(String severity) {
        if (ERROR.equals(severity)) return "خطأ";
        if (WARN.equals(severity)) return "تحذير";
        return "معلومة";
    }

    public static String sanitizeId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9_]", "_");
        if (s.isEmpty()) return "";
        if (Character.isDigit(s.charAt(0))) s = "mp_" + s;
        return s;
    }
}
