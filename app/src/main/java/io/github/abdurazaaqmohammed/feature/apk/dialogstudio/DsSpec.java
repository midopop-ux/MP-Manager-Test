package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable, serializable description of one dialog. It is the shared document
 * between the visual studio, the code generators and the APK injector.
 */
public class DsSpec {
    public static final String SCHEMA = "mp.dialog.studio.v1";

    public String schema = SCHEMA;
    public String id = "";
    public String name = "";
    public String packageName = "";
    public String title = "";
    public String message = "";
    public String positive = "OK";
    public String negative = "";
    public String neutral = "";
    public String positiveUrl = "";
    public String negativeUrl = "";
    public String neutralUrl = "";
    public String templateId = "ios_alert";
    /** Layout archetype: classic, ios_alert, ios_sheet, bottom_sheet, action_sheet,
     * floating, notification, media, form, wizard, dashboard, fullscreen, tabs. */
    public String layout = "classic";
    public String templateCategory = "modern";
    public int titleAlign = 0;
    public boolean verticalButtons = false;
    public boolean showIcon = false;
    public String iconB64 = "";
    public int accentColor = 0;
    public int shadowDp = 0;
    public int maxWidthDp = 0;
    public String enterAnimation = "fade_scale";
    public String exitAnimation = "fade";
    public String previewDevice = "iphone15pro";
    public String previewWallpaper = "aurora";
    public boolean previewDark = false;

    public boolean cancelable = true;
    public boolean draggable = false;
    public boolean showOnce = false;
    public boolean dontShowAgain = false;
    public boolean preventDoubleOpen = false;
    public boolean saveInputState = false;
    public boolean blockDismissWhileBusy = false;
    public int delayMs = 0;

    public String font = "";
    public String fontPath = "";
    public int bgColor1 = 0xFFFDFBFF;
    public int bgColor2 = 0x00FFFFFF;
    public boolean gradient = false;
    public int orientation = 0;
    public float cornerRadiusDp = 28f;
    public float borderWidthDp = 0f;
    public int borderColor = 0x33FFFFFF;
    public boolean animBorder = false;
    public int animColorA = 0xFFFFFFFF;
    public int animColorB = 0xFF000000;
    public boolean rainbowAnim = false;
    public int animSpeedMs = 2500;
    public int titleColor = 0;
    public int msgColor = 0;
    public int btnColor = 0;
    public float dimAmount = 0.45f;
    public float widthPercent = 92f;
    public int paddingDp = 20;
    public float titleSizeSp = 20f;
    public float msgSizeSp = 15f;
    public float buttonSizeSp = 15f;

    public List<Widget> widgets = new ArrayList<>();
    public List<Variable> variables = new ArrayList<>();
    public List<Event> events = new ArrayList<>();
    public List<Automation> automations = new ArrayList<>();
    public List<Logic> logic = new ArrayList<>();
    public List<Link> links = new ArrayList<>();
    public List<String> activities = new ArrayList<>();
    public String updatedAt = "";
    public String aiNotes = "";

    public static class Widget {
        public String id = "";
        public String kind = "text";
        public String text = "";
        public String hint = "";
        public float textSizeSp = 16f;
        public int textColor = 0;
        public int fontStyle = 0;
        public String font = "";
        public String fontPath = "";
        public String imageB64 = "";
        public String btnAction = "Dismiss";
        public String url = "";
        public int btnBg = 0;
        public int btnBg2 = 0;
        public float btnCornerRadiusDp = 0f;
        public float btnBorderWidthDp = 0f;
        public int btnBorderColor = -1;
        public int btnPaddingDp = 0;
        public boolean btnAnim = false;
        public List<Integer> btnAnimColors;
        public int btnAnimSpeedMs = 2500;
        public boolean btnAnimRainbow = false;
        public int leftDp = 0;
        public int topDp = 0;
        public List<String> options = new ArrayList<>();
        public boolean checked = false;
        public String variable = "";
        public String visibleIf = "";
        public String description = "";
        public int widthPercent = 100;
        public int heightDp = 0;
        public int gravity = 0;
        public boolean enabled = true;
        public int page = 0;
        public String iconB64 = "";
        public boolean searchable = false;
        public boolean multiple = false;
        public String inputType = "";
        public String linkMode = "browser";
    }

    /** A link embedded in the dialog content or attached to an element. */
    public static class Link {
        public String text = "";
        public String url = "";
        public String mode = "browser";
        public String target = "";
        public String description = "";
    }

    public static class Variable {
        public String name = "";
        public String type = "string";
        public String value = "";
        public String source = "static";
        public boolean persistent = false;
        public boolean secret = false;
        public String description = "";
    }

    public static class Action {
        public String type = "";
        public String target = "";
        public String value = "";
        public String value2 = "";
        public String url = "";
        public String condition = "";
        public String conditionValue = "";
        public String methodClass = "";
        public String methodName = "";
        public String methodSignature = "";
        public String methodArg = "";
        public boolean toastLong = false;
        public boolean continueOnError = true;
        public int delayMs = 0;
        public String description = "";
    }

    public static class Event {
        public String widgetId = "@dialog";
        public String event = "CLICK";
        public List<Action> actions = new ArrayList<>();
        public boolean enabled = true;
        public int timeoutMs = 0;
        public int priority = 0;
        public String condition = "";
    }

    public static class Automation {
        public String trigger = "ACTIVITY_OPEN";
        public String activity = "";
        public String condition = "";
        public int delayMs = 0;
        public int intervalMs = 0;
        public boolean enabled = true;
        public String description = "";
    }

    public static class Logic {
        public String fromWidget = "";
        public String toWidget = "";
        public String fromVar = "";
        public String operation = "SET_TEXT";
        public String template = "";
        public String condition = "";
    }

    public Widget findWidget(String widgetId) {
        if (widgetId == null) return null;
        for (Widget w : widgets) {
            if (w != null && widgetId.equals(w.id)) return w;
        }
        return null;
    }

    public Variable findVariable(String varName) {
        if (varName == null) return null;
        for (Variable v : variables) {
            if (v != null && varName.equals(v.name)) return v;
        }
        return null;
    }

    public Event findEvent(String widgetId, String eventName) {
        for (Event e : events) {
            if (e != null && e.enabled && eq(widgetId, e.widgetId) && eq(eventName, e.event)) return e;
        }
        return null;
    }

    public static String newWidgetId(String kind, int index) {
        String base = kind == null || kind.isEmpty() ? "view" : kind;
        return "mp_" + base + "_" + index;
    }

    public DsSpec copy() {
        DsSpec out = new DsSpec();
        out.schema = schema;
        out.id = id;
        out.name = name;
        out.packageName = packageName;
        out.title = title;
        out.message = message;
        out.positive = positive;
        out.negative = negative;
        out.neutral = neutral;
        out.positiveUrl = positiveUrl;
        out.negativeUrl = negativeUrl;
        out.neutralUrl = neutralUrl;
        out.templateId = templateId;
        out.layout = layout;
        out.templateCategory = templateCategory;
        out.titleAlign = titleAlign;
        out.verticalButtons = verticalButtons;
        out.showIcon = showIcon;
        out.iconB64 = iconB64;
        out.accentColor = accentColor;
        out.shadowDp = shadowDp;
        out.maxWidthDp = maxWidthDp;
        out.enterAnimation = enterAnimation;
        out.exitAnimation = exitAnimation;
        out.previewDevice = previewDevice;
        out.previewWallpaper = previewWallpaper;
        out.previewDark = previewDark;
        out.cancelable = cancelable;
        out.draggable = draggable;
        out.showOnce = showOnce;
        out.dontShowAgain = dontShowAgain;
        out.preventDoubleOpen = preventDoubleOpen;
        out.saveInputState = saveInputState;
        out.blockDismissWhileBusy = blockDismissWhileBusy;
        out.delayMs = delayMs;
        out.font = font;
        out.fontPath = fontPath;
        out.bgColor1 = bgColor1;
        out.bgColor2 = bgColor2;
        out.gradient = gradient;
        out.orientation = orientation;
        out.cornerRadiusDp = cornerRadiusDp;
        out.borderWidthDp = borderWidthDp;
        out.borderColor = borderColor;
        out.animBorder = animBorder;
        out.animColorA = animColorA;
        out.animColorB = animColorB;
        out.rainbowAnim = rainbowAnim;
        out.animSpeedMs = animSpeedMs;
        out.titleColor = titleColor;
        out.msgColor = msgColor;
        out.btnColor = btnColor;
        out.dimAmount = dimAmount;
        out.widthPercent = widthPercent;
        out.paddingDp = paddingDp;
        out.titleSizeSp = titleSizeSp;
        out.msgSizeSp = msgSizeSp;
        out.buttonSizeSp = buttonSizeSp;
        out.updatedAt = updatedAt;
        out.aiNotes = aiNotes;
        out.activities = new ArrayList<>(activities);
        for (Widget w : widgets) out.widgets.add(copyWidget(w));
        for (Variable v : variables) out.variables.add(copyVariable(v));
        for (Event e : events) out.events.add(copyEvent(e));
        for (Automation a : automations) out.automations.add(copyAutomation(a));
        for (Logic l : logic) out.logic.add(copyLogic(l));
        for (Link l : links) out.links.add(copyLink(l));
        return out;
    }

    public static Widget copyWidget(Widget w) {
        if (w == null) return null;
        Widget c = new Widget();
        c.id = w.id;
        c.kind = w.kind;
        c.text = w.text;
        c.hint = w.hint;
        c.textSizeSp = w.textSizeSp;
        c.textColor = w.textColor;
        c.fontStyle = w.fontStyle;
        c.font = w.font;
        c.fontPath = w.fontPath;
        c.imageB64 = w.imageB64;
        c.btnAction = w.btnAction;
        c.url = w.url;
        c.btnBg = w.btnBg;
        c.btnBg2 = w.btnBg2;
        c.btnCornerRadiusDp = w.btnCornerRadiusDp;
        c.btnBorderWidthDp = w.btnBorderWidthDp;
        c.btnBorderColor = w.btnBorderColor;
        c.btnPaddingDp = w.btnPaddingDp;
        c.btnAnim = w.btnAnim;
        c.btnAnimColors = w.btnAnimColors == null ? null : new ArrayList<>(w.btnAnimColors);
        c.btnAnimSpeedMs = w.btnAnimSpeedMs;
        c.btnAnimRainbow = w.btnAnimRainbow;
        c.leftDp = w.leftDp;
        c.topDp = w.topDp;
        c.options = w.options == null ? new ArrayList<>() : new ArrayList<>(w.options);
        c.checked = w.checked;
        c.variable = w.variable;
        c.visibleIf = w.visibleIf;
        c.description = w.description;
        c.widthPercent = w.widthPercent;
        c.heightDp = w.heightDp;
        c.gravity = w.gravity;
        c.enabled = w.enabled;
        c.page = w.page;
        c.iconB64 = w.iconB64;
        c.searchable = w.searchable;
        c.multiple = w.multiple;
        c.inputType = w.inputType;
        c.linkMode = w.linkMode;
        return c;
    }

    public static Variable copyVariable(Variable v) {
        if (v == null) return null;
        Variable c = new Variable();
        c.name = v.name;
        c.type = v.type;
        c.value = v.value;
        c.source = v.source;
        c.persistent = v.persistent;
        c.secret = v.secret;
        c.description = v.description;
        return c;
    }

    public static Action copyAction(Action a) {
        if (a == null) return null;
        Action c = new Action();
        c.type = a.type;
        c.target = a.target;
        c.value = a.value;
        c.value2 = a.value2;
        c.url = a.url;
        c.condition = a.condition;
        c.conditionValue = a.conditionValue;
        c.methodClass = a.methodClass;
        c.methodName = a.methodName;
        c.methodSignature = a.methodSignature;
        c.methodArg = a.methodArg;
        c.toastLong = a.toastLong;
        c.continueOnError = a.continueOnError;
        c.delayMs = a.delayMs;
        c.description = a.description;
        return c;
    }

    public static Event copyEvent(Event e) {
        if (e == null) return null;
        Event c = new Event();
        c.widgetId = e.widgetId;
        c.event = e.event;
        c.enabled = e.enabled;
        c.timeoutMs = e.timeoutMs;
        c.priority = e.priority;
        c.condition = e.condition;
        for (Action a : e.actions) c.actions.add(copyAction(a));
        return c;
    }

    public static Automation copyAutomation(Automation a) {
        if (a == null) return null;
        Automation c = new Automation();
        c.trigger = a.trigger;
        c.activity = a.activity;
        c.condition = a.condition;
        c.delayMs = a.delayMs;
        c.intervalMs = a.intervalMs;
        c.enabled = a.enabled;
        c.description = a.description;
        return c;
    }

    public static Logic copyLogic(Logic l) {
        if (l == null) return null;
        Logic c = new Logic();
        c.fromWidget = l.fromWidget;
        c.toWidget = l.toWidget;
        c.fromVar = l.fromVar;
        c.operation = l.operation;
        c.template = l.template;
        c.condition = l.condition;
        return c;
    }

    public static Link copyLink(Link l) {
        if (l == null) return null;
        Link c = new Link();
        c.text = l.text;
        c.url = l.url;
        c.mode = l.mode;
        c.target = l.target;
        c.description = l.description;
        return c;
    }

    public static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
