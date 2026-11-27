package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates the smali support classes used by the APK injector for studio
 * features: a view registry, a variable store, action sequences, event
 * listeners, lifecycle hooks and the guarded/delayed entry point.
 */
public final class SmaliStudioInjector {

    public static class Plan {
        public String registryType = "";
        public String varsType = "";
        public String actionsType = "";
        public String guardType = "";
        public String delayType = "";
        public String closeType = "";
        public String timeoutType = "";
        public String confirmType = "";
        public String cancelType = "";
        public final Map<String, String> viewListeners = new LinkedHashMap<>();
        public final Map<String, String> inputWatchers = new LinkedHashMap<>();
        public final Map<String, String> eventMethods = new LinkedHashMap<>();
        public String openMethod = "";
        public final List<String> classes = new ArrayList<>();
        public boolean hasActions;
        public boolean hasGuard;
        public int delayMs;
        public boolean preventDoubleOpen;

        public String listener(String widgetId, String event) {
            return viewListeners.get(widgetId + "|" + event);
        }

        public String watcher(String widgetId) {
            return inputWatchers.get(widgetId);
        }

        public String method(String widgetId, String event) {
            return eventMethods.get(widgetId + "|" + event);
        }
    }

    private static final String RUN = "run(Landroid/content/Context;Landroid/content/DialogInterface;)V";

    private SmaliStudioInjector() {
    }

    public static Plan build(String base, DsSpec spec) {
        Plan p = new Plan();
        if (spec == null) return p;
        p.delayMs = Math.max(0, spec.delayMs);
        p.preventDoubleOpen = spec.preventDoubleOpen;
        String b = base.endsWith(";") ? base.substring(0, base.length() - 1) : base;
        p.registryType = b + "$mpSReg;";
        p.varsType = b + "$mpSVars;";
        p.actionsType = b + "$mpSAct;";
        p.guardType = b + "$mpSGuard;";
        p.delayType = b + "$mpSDelay;";
        p.closeType = b + "$mpSClose;";
        p.timeoutType = b + "$mpSTimeout;";
        p.confirmType = b + "$mpSConf;";
        p.cancelType = b + "$mpSCanc;";

        int methodIndex = 0;
        for (DsSpec.Event e : spec.events) {
            if (e == null || !e.enabled || e.actions == null || e.actions.isEmpty()) continue;
            String m = "run" + (methodIndex++);
            p.eventMethods.put(e.widgetId + "|" + e.event, m);
            if (DsEvents.OPEN.equals(e.event) && "@dialog".equals(e.widgetId)) p.openMethod = m;
        }
        boolean actionsNeeded = false;
        boolean closeActions = false;
        boolean timeoutActions = false;
        boolean confirmActions = false;
        boolean cancelActions = false;
        boolean watcherNeeded = false;
        int viewIndex = 0;
        int watcherIndex = 0;

        for (DsSpec.Event e : spec.events) {
            if (e == null || !e.enabled || e.actions == null || e.actions.isEmpty()) continue;
            actionsNeeded = true;
            String event = e.event == null ? "" : e.event;
            if (DsEvents.isWidgetEvent(event)) {
                String type = b + "$mpSEv" + (viewIndex++) + ";";
                p.viewListeners.put(e.widgetId + "|" + event, type);
                p.classes.add(viewListenerSmali(type, e.widgetId, event, p));
            } else if (DsEvents.INPUT_CHANGE.equals(event)) {
                DsSpec.Widget w = spec.findWidget(e.widgetId);
                if (w != null) {
                    String type = b + "$mpSTw" + (watcherIndex++) + ";";
                    p.inputWatchers.put(e.widgetId, type);
                    p.classes.add(inputWatcherSmali(type, w, spec, p));
                    watcherNeeded = true;
                }
            } else if (DsEvents.CLOSE.equals(event)) {
                closeActions = true;
            } else if (DsEvents.TIMEOUT.equals(event)) {
                timeoutActions = true;
            } else if (DsEvents.CONFIRM.equals(event)) {
                confirmActions = true;
            } else if (DsEvents.CANCEL.equals(event)) {
                cancelActions = true;
            }
        }
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null || !"input".equals(w.kind)) continue;
            boolean linked = !DsSpec.empty(w.variable);
            for (DsSpec.Logic l : spec.logic) {
                if (l != null && DsSpec.eq(l.fromWidget, w.id)) { linked = true; break; }
            }
            if (linked && !p.inputWatchers.containsKey(w.id)) {
                String type = b + "$mpSTw" + (watcherIndex++) + ";";
                p.inputWatchers.put(w.id, type);
                p.classes.add(inputWatcherSmali(type, w, spec, p));
                actionsNeeded = true;
            }
        }
        p.hasActions = actionsNeeded;
        p.hasGuard = p.preventDoubleOpen;
        if (actionsNeeded) {
            p.classes.add(actionsSmali(p, spec));
            p.classes.add(varsSmali(p.varsType, spec));
        } else {
            p.classes.add(varsSmali(p.varsType, spec));
        }
        p.classes.add(registrySmali(p.registryType));
        if (closeActions || p.preventDoubleOpen) {
            p.classes.add(closeSmali(p.closeType, p, closeActions));
        }
        if (timeoutActions) {
            DsSpec.Event timeout = spec.findEvent("@dialog", DsEvents.TIMEOUT);
            p.classes.add(timeoutSmali(p.timeoutType, p, timeout == null ? 5000 : timeout.timeoutMs));
        }
        if (confirmActions) p.classes.add(dialogListenerSmali(p.confirmType, p, DsEvents.CONFIRM));
        if (cancelActions) p.classes.add(dialogListenerSmali(p.cancelType, p, DsEvents.CANCEL));
        if (p.hasGuard) p.classes.add(guardSmali(p.guardType));
        if (p.delayMs > 0) p.classes.add(delaySmali(p.delayType, base, p));
        return p;
    }

    public static String trampolineSmali(String activityType, Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(".method public mpShowStudioDialog()V\n");
        sb.append("    .locals 2\n");
        if (plan.hasGuard) {
            sb.append("    sget-boolean v0, ").append(plan.guardType).append("->showing:Z\n");
            sb.append("    if-eqz v0, :mp_guard_go\n");
            sb.append("    return-void\n");
            sb.append("    :mp_guard_go\n");
            sb.append("    const/4 v0, 0x1\n");
            sb.append("    sput-boolean v0, ").append(plan.guardType).append("->showing:Z\n");
        }
        sb.append("    invoke-direct {p0}, ").append(activityType).append("->showMPManagerDialog()V\n");
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    public static String wrapperCall(Plan plan, String activityType) {
        if (plan == null) return "";
        StringBuilder sb = new StringBuilder();
        if (plan.delayMs > 0) {
            sb.append("    new-instance v0, Landroid/os/Handler;\n");
            sb.append("    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V\n");
            sb.append("    new-instance v1, ").append(plan.delayType).append("\n");
            sb.append("    invoke-direct {v1, p0}, ").append(plan.delayType).append("-><init>(")
                    .append(activityType).append(")V\n");
            sb.append("    const-wide v2, 0x").append(Long.toHexString(plan.delayMs)).append("L\n");
            sb.append("    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n");
            sb.append("    move-result v0\n");
        }
        if (plan.hasGuard || plan.delayMs > 0) {
            sb.append("    invoke-virtual {p0}, ").append(activityType).append("->mpShowStudioDialog()V\n");
        }
        return sb.toString();
    }

    private static String registrySmali(String type) {
        return ".class public final " + type + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpRegistry.java\"\n\n"
                + ".field private static final m:Ljava/util/HashMap;\n\n"
                + ".method static constructor <clinit>()V\n"
                + "    .locals 1\n"
                + "    new-instance v0, Ljava/util/HashMap;\n"
                + "    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V\n"
                + "    sput-object v0, " + type + "->m:Ljava/util/HashMap;\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public static put(Ljava/lang/String;Landroid/view/View;)V\n"
                + "    .locals 1\n"
                + "    sget-object v0, " + type + "->m:Ljava/util/HashMap;\n"
                + "    invoke-virtual {v0, p0, p1}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public static get(Ljava/lang/String;)Landroid/view/View;\n"
                + "    .locals 1\n"
                + "    sget-object v0, " + type + "->m:Ljava/util/HashMap;\n"
                + "    invoke-virtual {v0, p0}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;\n"
                + "    move-result-object v0\n"
                + "    check-cast v0, Landroid/view/View;\n"
                + "    return-object v0\n"
                + ".end method\n";
    }

    private static String varsSmali(String type, DsSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(".class public final ").append(type).append("\n");
        sb.append(".super Ljava/lang/Object;\n");
        sb.append(".source \"mpVars.java\"\n\n");
        sb.append(".field private static final m:Ljava/util/HashMap;\n\n");
        sb.append(".method static constructor <clinit>()V\n");
        sb.append("    .locals 2\n");
        sb.append("    new-instance v0, Ljava/util/HashMap;\n");
        sb.append("    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V\n");
        sb.append("    sput-object v0, ").append(type).append("->m:Ljava/util/HashMap;\n");
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            if (!"static".equals(v.source) && v.source != null) continue;
            sb.append("    const-string v1, \"").append(esc(v.name)).append("\"\n");
            sb.append("    const-string v2, \"").append(esc(v.value)).append("\"\n");
            sb.append("    invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n");
        }
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public static get(Ljava/lang/String;)Ljava/lang/String;\n");
        sb.append("    .locals 1\n");
        sb.append("    sget-object v0, ").append(type).append("->m:Ljava/util/HashMap;\n");
        sb.append("    invoke-virtual {v0, p0}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    if-eqz v0, :mp_vars_null\n");
        sb.append("    check-cast v0, Ljava/lang/String;\n");
        sb.append("    return-object v0\n");
        sb.append("    :mp_vars_null\n");
        sb.append("    const-string v0, \"\"\n");
        sb.append("    return-object v0\n");
        sb.append(".end method\n\n");
        sb.append(".method public static put(Ljava/lang/String;Ljava/lang/String;)V\n");
        sb.append("    .locals 1\n");
        sb.append("    sget-object v0, ").append(type).append("->m:Ljava/util/HashMap;\n");
        sb.append("    invoke-virtual {v0, p0, p1}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public static render(Ljava/lang/String;)Ljava/lang/String;\n");
        sb.append("    .locals 3\n");
        sb.append("    move-object v0, p0\n");
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            appendReplace(sb, type, "{{" + v.name + "}}");
            appendReplace(sb, type, "${" + v.name + "}");
        }
        sb.append("    return-object v0\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    private static void appendReplace(StringBuilder sb, String type, String token) {
        sb.append("    const-string v1, \"").append(esc(token)).append("\"\n");
        sb.append("    invoke-static {v1}, ").append(type).append("->get(Ljava/lang/String;)Ljava/lang/String;\n");
        sb.append("    move-result-object v2\n");
        sb.append("    invoke-virtual {v0, v1, v2}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;\n");
        sb.append("    move-result-object v0\n");
    }

    private static String actionsSmali(Plan p, DsSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(".class public final ").append(p.actionsType).append("\n");
        sb.append(".super Ljava/lang/Object;\n");
        sb.append(".source \"mpActions.java\"\n");
        sb.append(".field public static dlg:Landroid/content/DialogInterface;\n\n");
        for (DsSpec.Event e : spec.events) {
            if (e == null || !e.enabled || e.actions == null || e.actions.isEmpty()) continue;
            String method = p.method(e.widgetId, e.event);
            if (method == null) continue;
            sb.append(".method public static ").append(method)
                    .append("(Landroid/content/Context;Landroid/content/DialogInterface;)V\n");
            sb.append("    .locals 8\n");
            int label = 0;
            for (DsSpec.Action a : e.actions) {
                if (a == null) continue;
                label = emitAction(sb, p, a, label);
            }
            sb.append("    return-void\n");
            sb.append(".end method\n\n");
        }
        sb.append(".method public static safeStart(Landroid/content/Context;Landroid/content/Intent;)V\n");
        sb.append("    .locals 1\n");
        sb.append("    :mp_try_start\n");
        sb.append("    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\n");
        sb.append("    :mp_try_end\n");
        sb.append("    .catch Ljava/lang/Exception; {:mp_try_start .. :mp_try_end} :mp_catch_start\n");
        sb.append("    return-void\n");
        sb.append("    :mp_catch_start\n");
        sb.append("    move-exception v0\n");
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    private static int emitAction(StringBuilder sb, Plan p, DsSpec.Action a, int label) {
        String type = a.type == null ? "" : a.type;
        int l = label;
        switch (type) {
            case DsEvents.A_DISMISS:
                sb.append("    if-eqz p1, :mp_dismiss_").append(l).append("\n");
                sb.append("    invoke-interface {p1}, Landroid/content/DialogInterface;->dismiss()V\n");
                sb.append("    :mp_dismiss_").append(l).append("\n");
                return l + 1;
            case DsEvents.A_URL:
                sb.append("    const-string v0, \"").append(esc(a.url)).append("\"\n");
                sb.append("    invoke-static {v0}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    invoke-static {v0}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    new-instance v1, Landroid/content/Intent;\n");
                sb.append("    const-string v2, \"android.intent.action.VIEW\"\n");
                sb.append("    invoke-direct {v1, v2, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V\n");
                sb.append("    invoke-static {p0, v1}, ").append(p.actionsType).append("->safeStart(Landroid/content/Context;Landroid/content/Intent;)V\n");
                return l + 1;
            case DsEvents.A_TOAST:
                sb.append("    const-string v0, \"").append(esc(a.value)).append("\"\n");
                sb.append("    invoke-static {v0}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    const/4 v1, 0x").append(a.toastLong ? "1" : "0").append("\n");
                sb.append("    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    invoke-virtual {v0}, Landroid/widget/Toast;->show()V\n");
                return l + 1;
            case DsEvents.A_COPY:
                sb.append("    const-string v0, \"").append(esc(a.value)).append("\"\n");
                sb.append("    invoke-static {v0}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    const-string v1, \"clipboard\"\n");
                sb.append("    invoke-virtual {p0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;\n");
                sb.append("    move-result-object v1\n");
                sb.append("    check-cast v1, Landroid/content/ClipboardManager;\n");
                sb.append("    const-string v2, \"mp\"\n");
                sb.append("    invoke-static {v2, v0}, Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    invoke-virtual {v1, v0}, Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V\n");
                return l + 1;
            case DsEvents.A_SET_TEXT:
                sb.append("    const-string v0, \"").append(esc(a.target)).append("\"\n");
                sb.append("    invoke-static {v0}, ").append(p.registryType).append("->get(Ljava/lang/String;)Landroid/view/View;\n");
                sb.append("    move-result-object v0\n");
                sb.append("    if-eqz v0, :mp_settext_").append(l).append("\n");
                sb.append("    check-cast v0, Landroid/widget/TextView;\n");
                sb.append("    const-string v1, \"").append(esc(a.value)).append("\"\n");
                sb.append("    invoke-static {v1}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
                sb.append("    move-result-object v1\n");
                sb.append("    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n");
                sb.append("    :mp_settext_").append(l).append("\n");
                return l + 1;
            case DsEvents.A_SET_VAR:
                sb.append("    const-string v0, \"").append(esc(a.value)).append("\"\n");
                sb.append("    const-string v1, \"").append(esc(a.value2)).append("\"\n");
                sb.append("    invoke-static {v1}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
                sb.append("    move-result-object v1\n");
                sb.append("    invoke-static {v0, v1}, ").append(p.varsType).append("->put(Ljava/lang/String;Ljava/lang/String;)V\n");
                return l + 1;
            case DsEvents.A_CALL:
                return emitCall(sb, p, a, l);
            case DsEvents.A_IF_VAR:
                return emitIfVar(sb, p, a, l);
            case DsEvents.A_IF_INPUT:
                return emitIfInput(sb, p, a, l);
            case DsEvents.A_STOP:
                sb.append("    return-void\n");
                return l + 1;
            default:
                return l;
        }
    }


    private static int emitIfVar(StringBuilder sb, Plan p, DsSpec.Action a, int label) {
        String expr = a.condition == null ? "" : a.condition.trim();
        String var;
        String expected;
        boolean equals = true;
        if (expr.endsWith(" notempty")) {
            var = expr.substring(0, expr.length() - 9).trim();
            expected = "";
            equals = false;
        } else if (expr.endsWith(" empty")) {
            var = expr.substring(0, expr.length() - 6).trim();
            expected = "";
        } else {
            int ne = expr.indexOf("!=");
            int eq = expr.indexOf("==");
            if (ne > 0) {
                var = expr.substring(0, ne).trim();
                expected = expr.substring(ne + 2).trim();
                equals = false;
            } else if (eq > 0) {
                var = expr.substring(0, eq).trim();
                expected = expr.substring(eq + 2).trim();
            } else {
                var = expr;
                expected = a.conditionValue == null ? "" : a.conditionValue;
            }
        }
        if (var.isEmpty()) return label;
        expected = expected.replaceAll("^[\"']|[\"']$", "");
        sb.append("    const-string v0, \"").append(esc(var)).append("\"\n");
        sb.append("    invoke-static {v0}, ").append(p.varsType).append("->get(Ljava/lang/String;)Ljava/lang/String;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    const-string v1, \"").append(esc(expected)).append("\"\n");
        sb.append("    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n");
        sb.append("    move-result v0\n");
        sb.append("    ").append(equals ? "if-eqz" : "if-nez").append(" v0, :mp_ifvar_ret_").append(label).append("\n");
        sb.append("    goto :mp_ifvar_cont_").append(label).append("\n");
        sb.append("    :mp_ifvar_ret_").append(label).append("\n");
        sb.append("    return-void\n");
        sb.append("    :mp_ifvar_cont_").append(label).append("\n");
        return label + 1;
    }

    private static int emitIfInput(StringBuilder sb, Plan p, DsSpec.Action a, int label) {
        String expr = a.condition == null ? "" : a.condition.trim();
        String expected = "";
        String op = "notempty";
        if (expr.startsWith("==")) {
            op = "==";
            expected = expr.substring(2).trim();
        } else if (expr.startsWith("!=")) {
            op = "!=";
            expected = expr.substring(2).trim();
        } else if (expr.startsWith("contains ")) {
            op = "contains";
            expected = expr.substring(9).trim();
        } else if (!expr.isEmpty()) {
            op = expr;
        }
        expected = expected.replaceAll("^[\"']|[\"']$", "");
        sb.append("    const-string v0, \"").append(esc(a.target)).append("\"\n");
        sb.append("    invoke-static {v0}, ").append(p.registryType).append("->get(Ljava/lang/String;)Landroid/view/View;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    if-eqz v0, :mp_ifinput_ret_").append(label).append("\n");
        sb.append("    check-cast v0, Landroid/widget/EditText;\n");
        sb.append("    invoke-virtual {v0}, Landroid/widget/EditText;->getText()Landroid/text/Editable;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;\n");
        sb.append("    move-result-object v0\n");
        if ("empty".equals(op) || "notempty".equals(op)) {
            sb.append("    invoke-virtual {v0}, Ljava/lang/String;->isEmpty()Z\n");
            sb.append("    move-result v0\n");
            sb.append("    ").append("empty".equals(op) ? "if-eqz" : "if-nez")
                    .append(" v0, :mp_ifinput_ret_").append(label).append("\n");
        } else {
            sb.append("    const-string v1, \"").append(esc(expected)).append("\"\n");
            sb.append("    invoke-virtual {v0, v1}, Ljava/lang/String;->")
                    .append("contains".equals(op) ? "contains" : "equals")
                    .append("(Ljava/lang/CharSequence;)Z\n");
            sb.append("    move-result v0\n");
            sb.append("    ").append("!=".equals(op) ? "if-nez" : "if-eqz")
                    .append(" v0, :mp_ifinput_ret_").append(label).append("\n");
        }
        sb.append("    goto :mp_ifinput_cont_").append(label).append("\n");
        sb.append("    :mp_ifinput_ret_").append(label).append("\n");
        sb.append("    return-void\n");
        sb.append("    :mp_ifinput_cont_").append(label).append("\n");
        return label + 1;
    }

    private static int emitCall(StringBuilder sb, Plan p, DsSpec.Action a, int label) {
        String type = paramType(a);
        String descriptor = callDescriptor(a, type);
        if (descriptor == null) return label;
        int l = label;
        sb.append("    :mp_try_").append(l).append("\n");
        if (type.isEmpty()) {
            sb.append("    invoke-static {}, ").append(descriptor).append("\n");
        } else if ("String".equals(type)) {
            sb.append("    const-string v0, \"").append(esc(a.methodArg)).append("\"\n");
            sb.append("    invoke-static {v0}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
            sb.append("    move-result-object v0\n");
            sb.append("    invoke-static {v0}, ").append(descriptor).append("\n");
        } else if ("int".equals(type)) {
            sb.append("    const-string v0, \"").append(esc(a.methodArg)).append("\"\n");
            sb.append("    invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I\n");
            sb.append("    move-result v0\n");
            sb.append("    invoke-static {v0}, ").append(descriptor).append("\n");
        } else if ("boolean".equals(type)) {
            sb.append("    const/4 v0, 0x").append("true".equalsIgnoreCase(a.methodArg) ? "1" : "0").append("\n");
            sb.append("    invoke-static {v0}, ").append(descriptor).append("\n");
        } else if ("long".equals(type)) {
            sb.append("    const-string v0, \"").append(esc(a.methodArg)).append("\"\n");
            sb.append("    invoke-static {v0}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J\n");
            sb.append("    move-result-wide v0\n");
            sb.append("    invoke-static {v0, v1}, ").append(descriptor).append("\n");
        } else if ("float".equals(type)) {
            sb.append("    const-string v0, \"").append(esc(a.methodArg)).append("\"\n");
            sb.append("    invoke-static {v0}, Ljava/lang/Float;->parseFloat(Ljava/lang/String;)F\n");
            sb.append("    move-result v0\n");
            sb.append("    invoke-static {v0}, ").append(descriptor).append("\n");
        } else {
            return label;
        }
        sb.append("    :mp_try_end_").append(l).append("\n");
        sb.append("    .catch Ljava/lang/Throwable; {:mp_try_").append(l)
                .append(" .. :mp_try_end_").append(l).append("} :mp_catch_").append(l).append("\n");
        sb.append("    goto :mp_call_done_").append(l).append("\n");
        sb.append("    :mp_catch_").append(l).append("\n");
        sb.append("    move-exception v2\n");
        sb.append("    :mp_call_done_").append(l).append("\n");
        return l + 1;
    }

    private static String paramType(DsSpec.Action a) {
        String sig = a.methodSignature == null ? "" : a.methodSignature.trim();
        int open = sig.indexOf('(');
        int close = sig.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
            String p = sig.substring(open + 1, close).trim();
            if (p.isEmpty()) return "";
            int comma = p.indexOf(',');
            if (comma >= 0) p = p.substring(0, comma).trim();
            return DsNativeIndex.normalizeType(p);
        }
        return DsSpec.empty(a.methodArg) ? "" : "String";
    }

    private static String callDescriptor(DsSpec.Action a, String type) {
        if (DsSpec.empty(a.methodClass) || DsSpec.empty(a.methodName)) return null;
        String owner = "L" + a.methodClass.replace('.', '/') + ";";
        String params = type.isEmpty() ? "" : DsNativeIndex.typeDescriptor(type);
        String ret = "V";
        String sig = a.methodSignature == null ? "" : a.methodSignature;
        int close = sig.indexOf(')');
        if (close >= 0 && close + 1 < sig.length()) {
            String r = sig.substring(close + 1).trim();
            if (!r.isEmpty()) ret = DsNativeIndex.typeDescriptor(r);
        }
        return owner + "->" + a.methodName + "(" + params + ")" + ret;
    }

    private static String viewListenerSmali(String type, String widgetId, String event, Plan p) {
        boolean longClick = DsEvents.LONG_CLICK.equals(event);
        boolean doubleClick = DsEvents.DOUBLE_CLICK.equals(event);
        String method = p.method(widgetId, event);
        StringBuilder sb = new StringBuilder();
        sb.append(".class public final ").append(type).append("\n");
        sb.append(".super Ljava/lang/Object;\n");
        sb.append(".source \"mpEvent.java\"\n");
        sb.append(".implements ").append(longClick
                ? "Landroid/view/View$OnLongClickListener;" : "Landroid/view/View$OnClickListener;").append("\n\n");
        sb.append(".field private final c:Landroid/content/Context;\n");
        if (doubleClick) sb.append(".field private last:J\n");
        sb.append("\n.method public constructor <init>(Landroid/content/Context;)V\n");
        sb.append("    .locals 1\n");
        sb.append("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n");
        sb.append("    iput-object p1, p0, ").append(type).append("->c:Landroid/content/Context;\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        if (longClick) {
            sb.append(".method public onLongClick(Landroid/view/View;)Z\n");
            sb.append("    .locals 0\n");
            if (method != null) {
                sb.append("    iget-object v0, p0, ").append(type).append("->c:Landroid/content/Context;\n");
                sb.append("    sget-object v1, ").append(p.actionsType).append("->dlg:Landroid/content/DialogInterface;\n");
                sb.append("    invoke-static {v0, v1}, ").append(p.actionsType).append("->").append(method).append("(Landroid/content/Context;Landroid/content/DialogInterface;)V\n");
            }
            sb.append("    const/4 v0, 0x1\n");
            sb.append("    return v0\n");
            sb.append(".end method\n");
        } else {
            sb.append(".method public onClick(Landroid/view/View;)V\n");
            sb.append("    .locals 6\n");
            if (doubleClick) {
                sb.append("    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J\n");
                sb.append("    move-result-wide v2\n");
                sb.append("    iget-wide v4, p0, ").append(type).append("->last:J\n");
                sb.append("    iput-wide v2, p0, ").append(type).append("->last:J\n");
                sb.append("    sub-long v4, v2, v4\n");
                sb.append("    const-wide v2, 0x12cL\n");
                sb.append("    cmp-long v0, v4, v2\n");
                sb.append("    if-gez v0, :mp_double_run\n");
                sb.append("    return-void\n");
                sb.append("    :mp_double_run\n");
            }
            if (method != null) {
                sb.append("    iget-object v0, p0, ").append(type).append("->c:Landroid/content/Context;\n");
                sb.append("    sget-object v1, ").append(p.actionsType).append("->dlg:Landroid/content/DialogInterface;\n");
                sb.append("    invoke-static {v0, v1}, ").append(p.actionsType).append("->").append(method).append("(Landroid/content/Context;Landroid/content/DialogInterface;)V\n");
            }
            sb.append("    return-void\n");
            sb.append(".end method\n");
        }
        return sb.toString();
    }

    private static String inputWatcherSmali(String type, DsSpec.Widget w, DsSpec spec, Plan p) {
        DsSpec.Logic link = null;
        for (DsSpec.Logic l : spec.logic) {
            if (l != null && DsSpec.eq(l.fromWidget, w.id)) {
                link = l;
                break;
            }
        }
        String var = w.variable == null ? "" : w.variable;
        String target = link == null ? "" : link.toWidget;
        String template = link == null ? "" : link.template;
        String label = "mp_input_target_" + Math.abs(type.hashCode());
        StringBuilder sb = new StringBuilder();
        sb.append(".class public final ").append(type).append("\n");
        sb.append(".super Ljava/lang/Object;\n");
        sb.append(".source \"mpInput.java\"\n");
        sb.append(".implements Landroid/text/TextWatcher;\n\n");
        sb.append(".field private final c:Landroid/content/Context;\n");
        sb.append("\n.method public constructor <init>(Landroid/content/Context;)V\n");
        sb.append("    .locals 1\n");
        sb.append("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n");
        sb.append("    iput-object p1, p0, ").append(type).append("->c:Landroid/content/Context;\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public beforeTextChanged(Ljava/lang/CharSequence;III)V\n");
        sb.append("    .locals 0\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public onTextChanged(Ljava/lang/CharSequence;III)V\n");
        sb.append("    .locals 0\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public afterTextChanged(Landroid/text/Editable;)V\n");
        sb.append("    .locals 4\n");
        if (!var.isEmpty()) {
            sb.append("    const-string v0, \"").append(esc(var)).append("\"\n");
            sb.append("    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    invoke-static {v0, v1}, ").append(p.varsType).append("->put(Ljava/lang/String;Ljava/lang/String;)V\n");
        }
        if (!target.isEmpty()) {
            sb.append("    const-string v0, \"").append(esc(target)).append("\"\n");
            sb.append("    invoke-static {v0}, ").append(p.registryType).append("->get(Ljava/lang/String;)Landroid/view/View;\n");
            sb.append("    move-result-object v0\n");
            sb.append("    if-eqz v0, :").append(label).append("\n");
            sb.append("    check-cast v0, Landroid/widget/TextView;\n");
            sb.append("    const-string v1, \"").append(esc(template)).append("\"\n");
            sb.append("    invoke-static {v1}, ").append(p.varsType).append("->render(Ljava/lang/String;)Ljava/lang/String;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n");
            sb.append("    :").append(label).append("\n");
        }
        String inputMethod = p.method(w.id, DsEvents.INPUT_CHANGE);
        if (inputMethod != null) {
            sb.append("    iget-object v0, p0, ").append(type).append("->c:Landroid/content/Context;\n");
            sb.append("    sget-object v1, ").append(p.actionsType).append("->dlg:Landroid/content/DialogInterface;\n");
            sb.append("    invoke-static {v0, v1}, ").append(p.actionsType).append("->").append(inputMethod).append("(Landroid/content/Context;Landroid/content/DialogInterface;)V\n");
        }
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    private static String closeSmali(String type, Plan p, boolean hasActions) {
        StringBuilder sb = new StringBuilder();
        sb.append(".class public final ").append(type).append("\n");
        sb.append(".super Ljava/lang/Object;\n");
        sb.append(".source \"mpClose.java\"\n");
        sb.append(".implements Landroid/content/DialogInterface$OnDismissListener;\n\n");
        sb.append(".field private final c:Landroid/content/Context;\n\n");
        sb.append(".method public constructor <init>(Landroid/content/Context;)V\n");
        sb.append("    .locals 1\n");
        sb.append("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n");
        sb.append("    iput-object p1, p0, ").append(type).append("->c:Landroid/content/Context;\n");
        sb.append("    return-void\n");
        sb.append(".end method\n\n");
        sb.append(".method public onDismiss(Landroid/content/DialogInterface;)V\n");
        sb.append("    .locals 1\n");
        if (p.hasGuard) {
            sb.append("    const/4 v0, 0x0\n");
            sb.append("    sput-boolean v0, ").append(p.guardType).append("->showing:Z\n");
        }
        String closeMethod = p.method("@dialog", DsEvents.CLOSE);
        if (hasActions && closeMethod != null) {
            sb.append("    iget-object v0, p0, ").append(type).append("->c:Landroid/content/Context;\n");
            sb.append("    invoke-static {v0, p1}, ").append(p.actionsType).append("->").append(closeMethod).append("(Landroid/content/Context;Landroid/content/DialogInterface;)V\n");
        }
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    private static String timeoutSmali(String type, Plan p, int timeoutMs) {
        String timeoutMethod = p.method("@dialog", DsEvents.TIMEOUT);
        return ".class public final " + type + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpTimeout.java\"\n"
                + ".implements Ljava/lang/Runnable;\n\n"
                + ".field private final c:Landroid/content/Context;\n\n"
                + ".method public constructor <init>(Landroid/content/Context;)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + type + "->c:Landroid/content/Context;\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public run()V\n"
                + "    .locals 2\n"
                + "    iget-object v0, p0, " + type + "->c:Landroid/content/Context;\n"
                + "    sget-object v1, " + p.actionsType + "->dlg:Landroid/content/DialogInterface;\n"
                + "    invoke-static {v0, v1}, " + p.actionsType + "->" + (timeoutMethod == null ? "noop" : timeoutMethod) + "(Landroid/content/Context;Landroid/content/DialogInterface;)V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    private static String dialogListenerSmali(String type, Plan p, String event) {
        String method = p.method("@dialog", event);
        return ".class public final " + type + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpDialogEvent.java\"\n"
                + ".implements Landroid/content/DialogInterface$OnClickListener;\n\n"
                + ".field private final c:Landroid/content/Context;\n\n"
                + ".method public constructor <init>(Landroid/content/Context;)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + type + "->c:Landroid/content/Context;\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public onClick(Landroid/content/DialogInterface;I)V\n"
                + "    .locals 1\n"
                + "    iget-object v0, p0, " + type + "->c:Landroid/content/Context;\n"
                + (method == null ? "" : "    invoke-static {v0, p1}, " + p.actionsType + "->" + method + "(Landroid/content/Context;Landroid/content/DialogInterface;)V\n")
                + "    return-void\n"
                + ".end method\n";
    }

    private static String guardSmali(String type) {
        return ".class public final " + type + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpGuard.java\"\n\n"
                + ".field public static showing:Z\n\n"
                + ".method public constructor <init>()V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    private static String delaySmali(String type, String activityBase, Plan p) {
        String activity = activityBase.endsWith(";") ? activityBase : activityBase + ";";
        return ".class public final " + type + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpDelay.java\"\n"
                + ".implements Ljava/lang/Runnable;\n\n"
                + ".field private final a:" + activity + "\n\n"
                + ".method public constructor <init>(" + activity + ")V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + type + "->a:" + activity + "\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public run()V\n"
                + "    .locals 1\n"
                + "    iget-object v0, p0, " + type + "->a:" + activity + "\n"
                + "    invoke-virtual {v0}, " + activity + "->mpShowStudioDialog()V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String describePlan(Plan p) {
        if (p == null) return "لا توجد خطة حقن.";
        return "متغيرات: " + (DsSpec.empty(p.varsType) ? "لا" : "نعم")
                + " · سجل عناصر: " + (DsSpec.empty(p.registryType) ? "لا" : "نعم")
                + " · إجراءات: " + p.hasActions
                + " · مستمعات: " + p.viewListeners.size()
                + " · حراسة: " + p.hasGuard
                + " · تأخير: " + p.delayMs + "ms";
    }
}
