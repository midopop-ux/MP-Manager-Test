package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a dialog event against an in-memory spec. This is the same action
 * vocabulary used by code generation and smali injection, so the interactive
 * preview reflects real event behaviour instead of decorative buttons.
 */
public final class DsPreview {

    public static final class Result {
        public final List<String> log = new ArrayList<>();
        public final Map<String, String> variables = new LinkedHashMap<>();
        public boolean stopped;
    }

    private DsPreview() {
    }

    public static Map<String, String> initialVariables(DsSpec spec) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec == null || spec.variables == null) return out;
        for (DsSpec.Variable v : spec.variables) {
            if (v != null && !DsSpec.empty(v.name)) {
                out.put(v.name, v.value == null ? "" : v.value);
            }
        }
        return out;
    }

    public static Result run(DsSpec spec, DsSpec.Event event, Map<String, String> variables) {
        Result result = new Result();
        result.variables.putAll(initialVariables(spec));
        if (variables != null) result.variables.putAll(variables);
        if (spec == null || event == null) {
            result.log.add("لا يوجد حدث قابل للتنفيذ.");
            return result;
        }
        if (!event.enabled) {
            result.log.add("الحدث معطل: " + event.widgetId + " / " + event.event);
            return result;
        }
        if (!DsSpec.empty(event.condition) && !truthy(event.condition, result.variables)) {
            result.log.add("لم يتحقق شرط الحدث: " + event.condition);
            return result;
        }
        result.log.add("تشغيل " + event.event + " على " + event.widgetId);
        for (DsSpec.Action action : event.actions) {
            if (action == null) continue;
            if (result.stopped) break;
            if (!DsSpec.empty(action.condition)
                    && !truthy(action.condition, result.variables)) {
                result.log.add("تخطي إجراء: الشرط غير محقق (" + action.condition + ")");
                continue;
            }
            execute(spec, action, result);
        }
        return result;
    }

    public static Result runAll(DsSpec spec) {
        Result all = new Result();
        if (spec == null) return all;
        Map<String, String> vars = initialVariables(spec);
        for (DsSpec.Event event : spec.events) {
            if (event == null || !event.enabled) continue;
            Result one = run(spec, event, vars);
            all.log.addAll(one.log);
            vars.putAll(one.variables);
            if (one.stopped) all.stopped = true;
        }
        all.variables.putAll(vars);
        return all;
    }

    private static void execute(DsSpec spec, DsSpec.Action action, Result result) {
        String type = action.type == null ? "" : action.type;
        switch (type) {
            case DsEvents.A_SET_TEXT: {
                DsSpec.Widget w = spec.findWidget(action.target);
                if (w == null) {
                    result.log.add("SET_TEXT: العنصر غير موجود " + action.target);
                    break;
                }
                w.text = interpolate(action.value, result.variables);
                result.log.add("SET_TEXT " + w.id + " = " + w.text);
                break;
            }
            case DsEvents.A_SET_VAR: {
                String value = interpolate(action.value, result.variables);
                result.variables.put(action.target, value);
                DsSpec.Variable v = spec.findVariable(action.target);
                if (v != null) v.value = value;
                result.log.add("SET_VAR " + action.target + " = " + value);
                break;
            }
            case DsEvents.A_TOAST:
                result.log.add("TOAST: " + interpolate(action.value, result.variables));
                break;
            case DsEvents.A_URL:
                result.log.add("URL: " + (DsSpec.empty(action.url) ? action.value : action.url));
                break;
            case DsEvents.A_COPY:
                result.log.add("COPY: " + interpolate(action.value, result.variables));
                break;
            case DsEvents.A_CALL:
                result.log.add("CALL: " + action.methodClass + "#" + action.methodName
                        + (DsSpec.empty(action.methodArg) ? "" : "(" + action.methodArg + ")"));
                break;
            case DsEvents.A_SHOW_DIALOG:
                result.log.add("SHOW_DIALOG: " + action.target);
                break;
            case DsEvents.A_DELAY:
                result.log.add("DELAY: " + Math.max(0, action.delayMs) + " ms");
                break;
            case DsEvents.A_STOP:
                result.log.add("STOP: توقف تنفيذ بقية الإجراءات.");
                result.stopped = true;
                break;
            case DsEvents.A_DISMISS:
                result.log.add("DISMISS: إغلاق الدايلوك.");
                break;
            case DsEvents.A_IF_VAR:
            case DsEvents.A_IF_INPUT: {
                String expr = DsSpec.empty(action.condition) ? action.value : action.condition;
                result.log.add("IF " + expr + " → "
                        + (truthy(expr, result.variables) ? "صحيح" : "غير صحيح"));
                break;
            }
            default:
                result.log.add("إجراء غير معروف: " + type);
        }
    }

    private static String interpolate(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue())
                    .replace("${" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** Small, deterministic expression evaluator for preview conditions. */
    public static boolean truthy(String expression, Map<String, String> vars) {
        if (DsSpec.empty(expression)) return true;
        String expr = expression.trim();
        if (expr.startsWith("!")) return !truthy(expr.substring(1), vars);
        int or = expr.indexOf("||");
        if (or > 0) return truthy(expr.substring(0, or), vars) || truthy(expr.substring(or + 2), vars);
        int and = expr.indexOf("&&");
        if (and > 0) return truthy(expr.substring(0, and), vars) && truthy(expr.substring(and + 2), vars);
        String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
        for (String op : ops) {
            int i = expr.indexOf(op);
            if (i < 0) continue;
            String left = value(expr.substring(0, i).trim(), vars);
            String right = value(expr.substring(i + op.length()).trim(), vars);
            int cmp = compare(left, right);
            switch (op) {
                case "==": return eq(left, right);
                case "!=": return !eq(left, right);
                case ">": return cmp > 0;
                case "<": return cmp < 0;
                case ">=": return cmp >= 0;
                case "<=": return cmp <= 0;
                default: break;
            }
        }
        String v = value(expr, vars);
        return !v.isEmpty() && !"false".equalsIgnoreCase(v) && !"0".equals(v);
    }

    private static String value(String token, Map<String, String> vars) {
        String t = token.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        if (vars.containsKey(t)) return vars.get(t) == null ? "" : vars.get(t);
        return t;
    }

    private static int compare(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (Exception ignored) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
