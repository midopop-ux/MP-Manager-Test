package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Event names, action catalog and injectability rules. */
public final class DsEvents {

    public static final String CLICK = "CLICK";
    public static final String LONG_CLICK = "LONG_CLICK";
    public static final String DOUBLE_CLICK = "DOUBLE_CLICK";
    public static final String OPEN = "OPEN";
    public static final String CLOSE = "CLOSE";
    public static final String CONFIRM = "CONFIRM";
    public static final String CANCEL = "CANCEL";
    public static final String INPUT_CHANGE = "INPUT_CHANGE";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    public static final String A_DISMISS = "DISMISS";
    public static final String A_URL = "URL";
    public static final String A_TOAST = "TOAST";
    public static final String A_COPY = "COPY";
    public static final String A_SET_TEXT = "SET_TEXT";
    public static final String A_SET_VAR = "SET_VAR";
    public static final String A_CALL = "CALL";
    public static final String A_IF_VAR = "IF_VAR";
    public static final String A_IF_INPUT = "IF_INPUT";
    public static final String A_STOP = "STOP";
    public static final String A_SHOW_DIALOG = "SHOW_DIALOG";
    public static final String A_DELAY = "DELAY";

    private static final List<String> EVENT_NAMES = Arrays.asList(
            CLICK, LONG_CLICK, DOUBLE_CLICK, OPEN, CLOSE, CONFIRM, CANCEL,
            INPUT_CHANGE, TIMEOUT, SUCCESS, FAILURE);

    private static final List<String> ACTION_NAMES = Arrays.asList(
            A_DISMISS, A_URL, A_TOAST, A_COPY, A_SET_TEXT, A_SET_VAR, A_CALL,
            A_IF_VAR, A_IF_INPUT, A_STOP, A_SHOW_DIALOG, A_DELAY);

    private DsEvents() {
    }

    public static List<String> eventNames() {
        return new ArrayList<>(EVENT_NAMES);
    }

    public static List<String> actionNames() {
        return new ArrayList<>(ACTION_NAMES);
    }

    public static boolean isWidgetEvent(String event) {
        return CLICK.equals(event) || LONG_CLICK.equals(event) || DOUBLE_CLICK.equals(event);
    }

    public static List<DsSpec.Action> actionsFor(DsSpec spec, String widgetId, String event) {
        List<DsSpec.Action> out = new ArrayList<>();
        if (spec == null) return out;
        for (DsSpec.Event e : spec.events) {
            if (e != null && e.enabled && DsSpec.eq(widgetId, e.widgetId) && DsSpec.eq(event, e.event)) {
                for (DsSpec.Action a : e.actions) if (a != null) out.add(a);
            }
        }
        return out;
    }

    public static DsSpec.Event find(DsSpec spec, String widgetId, String event) {
        if (spec == null) return null;
        for (DsSpec.Event e : spec.events) {
            if (e != null && e.enabled && DsSpec.eq(widgetId, e.widgetId) && DsSpec.eq(event, e.event)) {
                return e;
            }
        }
        return null;
    }

    public static boolean hasActions(DsSpec spec, String widgetId, String event) {
        return !actionsFor(spec, widgetId, event).isEmpty();
    }

    /** True when the APK injector can reproduce this action natively. */
    public static boolean injectionSupported(String event, DsSpec.Action action) {
        if (event == null || action == null) return false;
        if (SUCCESS.equals(event) || FAILURE.equals(event)) return false;
        String t = action.type;
        if (A_DISMISS.equals(t) || A_URL.equals(t) || A_TOAST.equals(t)
                || A_COPY.equals(t) || A_SET_TEXT.equals(t) || A_SET_VAR.equals(t)
                || A_CALL.equals(t) || A_IF_VAR.equals(t) || A_IF_INPUT.equals(t)
                || A_STOP.equals(t)) {
            return true;
        }
        return false;
    }

    public static String describeAction(DsSpec.Action a) {
        if (a == null) return "";
        String t = a.type == null ? "" : a.type;
        switch (t) {
            case A_DISMISS:
                return "إغلاق الدايلوك";
            case A_URL:
                return "فتح رابط: " + a.url;
            case A_TOAST:
                return "إظهار رسالة: " + a.value;
            case A_COPY:
                return "نسخ النص: " + a.value;
            case A_SET_TEXT:
                return "تحديث النص " + a.target + " ← " + a.value;
            case A_SET_VAR:
                return "تعيين المتغير " + a.value + " = " + a.value2;
            case A_CALL:
                return "استدعاء " + a.methodClass + "#" + a.methodName + "(" + a.methodArg + ")";
            case A_IF_VAR:
                return "شرط متغير: " + a.condition + " " + a.conditionValue;
            case A_IF_INPUT:
                return "شرط إدخال: " + a.target + " " + a.condition;
            case A_STOP:
                return "إيقاف التنفيذ";
            case A_SHOW_DIALOG:
                return "فتح دايلوك آخر: " + a.target;
            case A_DELAY:
                return "تأخير " + a.delayMs + "ms";
            default:
                return t;
        }
    }

    public static String describeEvent(String event) {
        if (event == null) return "";
        switch (event) {
            case CLICK:
                return "نقر";
            case LONG_CLICK:
                return "نقر مطوّل";
            case DOUBLE_CLICK:
                return "نقر مزدوج";
            case OPEN:
                return "عند الفتح";
            case CLOSE:
                return "عند الإغلاق";
            case CONFIRM:
                return "عند التأكيد";
            case CANCEL:
                return "عند الإلغاء";
            case INPUT_CHANGE:
                return "عند تغيير الإدخال";
            case TIMEOUT:
                return "عند انتهاء المهلة";
            case SUCCESS:
                return "عند النجاح";
            case FAILURE:
                return "عند الفشل";
            default:
                return event;
        }
    }

    public static List<String> validate(DsSpec spec, DsNativeIndex index) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;
        for (DsSpec.Event e : spec.events) {
            if (e == null || !e.enabled) continue;
            if (!EVENT_NAMES.contains(e.event)) {
                out.add("حدث غير معروف: " + e.event);
                continue;
            }
            if (!"@dialog".equals(e.widgetId) && spec.findWidget(e.widgetId) == null) {
                out.add("الحدث " + e.event + " مرتبط بعنصر غير موجود: " + e.widgetId);
            }
            if (isWidgetEvent(e.event) && "@dialog".equals(e.widgetId)) {
                out.add("حدث " + e.event + " يجب ربطه بعنصر واجهة.");
            }
            if ((OPEN.equals(e.event) || CLOSE.equals(e.event) || TIMEOUT.equals(e.event)
                    || CONFIRM.equals(e.event) || CANCEL.equals(e.event)) && !"@dialog".equals(e.widgetId)) {
                out.add("حدث " + e.event + " يعمل على مستوى الدايلوك وليس العنصر.");
            }
            if (e.actions == null || e.actions.isEmpty()) {
                out.add("الحدث " + e.event + " بلا إجراءات.");
            }
            for (DsSpec.Action a : e.actions) {
                if (a == null || DsSpec.empty(a.type)) {
                    out.add("إجراء فارغ في الحدث " + e.event);
                    continue;
                }
                if (A_URL.equals(a.type) && DsSpec.empty(a.url)) {
                    out.add("إجراء فتح رابط بلا رابط في الحدث " + e.event);
                }
                if (A_TOAST.equals(a.type) && DsSpec.empty(a.value)) {
                    out.add("إجراء رسالة بلا نص في الحدث " + e.event);
                }
                if (A_SET_TEXT.equals(a.type)) {
                    if (DsSpec.empty(a.target)) out.add("إجراء تحديث نص بلا عنصر هدف.");
                    else if (spec.findWidget(a.target) == null) {
                        out.add("عنصر الهدف غير موجود: " + a.target);
                    }
                }
                if (A_SET_VAR.equals(a.type) && DsSpec.empty(a.value)) {
                    out.add("إجراء تعيين متغير بلا اسم.");
                }
                if (A_CALL.equals(a.type) && index != null) {
                    String err = index.validate(a);
                    if (err != null) out.add(err);
                }
            }
        }
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            if (isWidgetEvent(CLICK) && !DsSpec.empty(w.id)
                    && spec.findEvent(w.id, DOUBLE_CLICK) != null
                    && spec.findEvent(w.id, CLICK) != null) {
                out.add("العنصر " + w.id + " يستخدم النقر والنقر المزدوج معًا؛ قد يتنفيذ النقر العادي أولًا.");
            }
        }
        return out;
    }
}
