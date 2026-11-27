package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Typed variables, placeholder resolution and small condition evaluation. */
public final class DsVars {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,47}");

    private DsVars() {
    }

    public static Map<String, String> baseValues(DsSpec spec) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec == null) return out;
        Date now = new Date();
        out.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now));
        out.put("time", new SimpleDateFormat("HH:mm", Locale.US).format(now));
        out.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now));
        out.put("app_package", spec.packageName == null ? "" : spec.packageName);
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || v.name == null || v.name.isEmpty()) continue;
            if ("date".equals(v.source)) {
                out.put(v.name, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now));
            } else if ("time".equals(v.source)) {
                out.put(v.name, new SimpleDateFormat("HH:mm", Locale.US).format(now));
            } else if ("app_version".equals(v.source)) {
                out.put(v.name, "");
            } else if ("static".equals(v.source) || v.source == null) {
                out.put(v.name, v.value == null ? "" : v.value);
            } else {
                out.put(v.name, "");
            }
        }
        return out;
    }

    public static String resolve(String text, DsSpec spec, Map<String, String> runtime) {
        if (text == null || text.isEmpty()) return "";
        Map<String, String> values = baseValues(spec);
        if (runtime != null) values.putAll(runtime);
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey() == null) continue;
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{{" + e.getKey() + "}}", v);
            out = out.replace("${" + e.getKey() + "}", v);
        }
        return out;
    }

    public static String resolve(String text, DsSpec spec) {
        return resolve(text, spec, null);
    }

    public static boolean hasPlaceholders(String text) {
        if (text == null) return false;
        return text.contains("{{") || text.contains("${");
    }

    /**
     * Returns a copy with design-time placeholders resolved. Runtime-only
     * placeholders stay in the text so the injected helper can resolve them.
     */
    public static DsSpec resolveStatic(DsSpec spec) {
        if (spec == null) return null;
        DsSpec out = spec.copy();
        Map<String, String> values = new LinkedHashMap<>();
        Date now = new Date();
        values.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now));
        values.put("time", new SimpleDateFormat("HH:mm", Locale.US).format(now));
        values.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now));
        values.put("app_package", spec.packageName == null ? "" : spec.packageName);
        for (DsSpec.Variable v : spec.variables) {
            if (v == null || v.name == null) continue;
            if ("static".equals(v.source)) values.put(v.name, v.value == null ? "" : v.value);
        }
        out.title = resolve(out.title, spec, values);
        out.message = resolve(out.message, spec, values);
        out.positive = resolve(out.positive, spec, values);
        out.negative = resolve(out.negative, spec, values);
        out.neutral = resolve(out.neutral, spec, values);
        out.positiveUrl = resolve(out.positiveUrl, spec, values);
        out.negativeUrl = resolve(out.negativeUrl, spec, values);
        out.neutralUrl = resolve(out.neutralUrl, spec, values);
        for (DsSpec.Widget w : out.widgets) {
            if (w == null) continue;
            w.text = resolve(w.text, spec, values);
            w.hint = resolve(w.hint, spec, values);
            w.url = resolve(w.url, spec, values);
            if (w.options != null) {
                List<String> opts = new ArrayList<>();
                for (String o : w.options) opts.add(resolve(o, spec, values));
                w.options = opts;
            }
        }
        for (DsSpec.Event e : out.events) {
            if (e == null) continue;
            for (DsSpec.Action a : e.actions) {
                if (a == null) continue;
                a.value = resolve(a.value, spec, values);
                a.value2 = resolve(a.value2, spec, values);
                a.url = resolve(a.url, spec, values);
                a.methodArg = resolve(a.methodArg, spec, values);
                a.conditionValue = resolve(a.conditionValue, spec, values);
            }
        }
        return out;
    }

    public static List<String> validate(DsSpec spec) {
        List<String> issues = new ArrayList<>();
        if (spec == null) {
            issues.add("لا توجد مواصفات دايلوك.");
            return issues;
        }
        List<String> seen = new ArrayList<>();
        for (DsSpec.Variable v : spec.variables) {
            if (v == null) continue;
            if (v.name == null || !NAME.matcher(v.name).matches()) {
                issues.add("اسم متغير غير صالح: " + v.name);
            } else if (seen.contains(v.name)) {
                issues.add("متغير مكرر: " + v.name);
            } else {
                seen.add(v.name);
            }
            if (isReserved(v.name)) issues.add("اسم متغير محجوز: " + v.name);
            String type = v.type == null ? "string" : v.type;
            if ("number".equals(type) && !DsSpec.empty(v.value) && !isNumber(v.value)) {
                issues.add("قيمة رقمية غير صالحة للمتغير " + v.name + ": " + v.value);
            }
            if ("boolean".equals(type) && !DsSpec.empty(v.value)
                    && !"true".equalsIgnoreCase(v.value) && !"false".equalsIgnoreCase(v.value)) {
                issues.add("قيمة منطقية غير صالحة للمتغير " + v.name + ": " + v.value);
            }
        }
        for (DsSpec.Widget w : spec.widgets) {
            if (w != null && "input".equals(w.kind) && !DsSpec.empty(w.variable)
                    && spec.findVariable(w.variable) == null) {
                issues.add("حقل الإدخال " + w.id + " مرتبط بمتغير غير موجود: " + w.variable);
            }
        }
        return issues;
    }

    public static boolean isReserved(String name) {
        if (name == null) return false;
        return "date".equals(name) || "time".equals(name) || "datetime".equals(name)
                || "app_package".equals(name) || "app_version".equals(name);
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Supports a deliberately small, predictable expression language:
     * var==x, var!=x, var empty, var notempty, input:id==x,
     * input:id empty/notempty, true/false, and && / || chains.
     */
    public static boolean evaluate(String expression, DsSpec spec, Map<String, String> runtime,
                                   Map<String, String> inputs) {
        if (DsSpec.empty(expression)) return true;
        String expr = expression.trim();
        int or = indexOfTop(expr, "||");
        if (or >= 0) {
            return evaluate(expr.substring(0, or), spec, runtime, inputs)
                    || evaluate(expr.substring(or + 2), spec, runtime, inputs);
        }
        int and = indexOfTop(expr, "&&");
        if (and >= 0) {
            return evaluate(expr.substring(0, and), spec, runtime, inputs)
                    && evaluate(expr.substring(and + 2), spec, runtime, inputs);
        }
        if ("true".equalsIgnoreCase(expr)) return true;
        if ("false".equalsIgnoreCase(expr)) return false;
        if (expr.startsWith("!")) {
            return !evaluate(expr.substring(1), spec, runtime, inputs);
        }
        String value;
        String rest;
        if (expr.startsWith("input:")) {
            String body = expr.substring(6).trim();
            int space = body.indexOf(' ');
            String id = space < 0 ? body : body.substring(0, space);
            value = inputs == null ? "" : inputs.get(id);
            rest = space < 0 ? "notempty" : body.substring(space + 1).trim();
        } else if (expr.startsWith("widget:")) {
            String body = expr.substring(7).trim();
            int space = body.indexOf(' ');
            String id = space < 0 ? body : body.substring(0, space);
            value = inputs == null ? "" : inputs.get(id);
            rest = space < 0 ? "visible" : body.substring(space + 1).trim();
        } else {
            int eq = expr.indexOf("==");
            int ne = expr.indexOf("!=");
            if (eq >= 0) {
                String name = expr.substring(0, eq).trim();
                String expected = expr.substring(eq + 2).trim();
                value = valueOf(name, spec, runtime);
                return value.equals(stripQuotes(expected));
            }
            if (ne >= 0) {
                String name = expr.substring(0, ne).trim();
                String expected = expr.substring(ne + 2).trim();
                value = valueOf(name, spec, runtime);
                return !value.equals(stripQuotes(expected));
            }
            int space = expr.indexOf(' ');
            if (space < 0) {
                return !DsSpec.empty(valueOf(expr, spec, runtime));
            }
            value = valueOf(expr.substring(0, space).trim(), spec, runtime);
            rest = expr.substring(space + 1).trim().toLowerCase(Locale.US);
        }
        if (value == null) value = "";
        if ("empty".equals(rest)) return value.trim().isEmpty();
        if ("notempty".equals(rest)) return !value.trim().isEmpty();
        if ("visible".equals(rest)) return !"false".equalsIgnoreCase(value);
        if ("number".equals(rest)) return isNumber(value);
        return false;
    }

    private static int indexOfTop(String s, String needle) {
        int depth = 0;
        for (int i = 0; i < s.length() - needle.length() + 1; i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.startsWith(needle, i)) return i;
        }
        return -1;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String valueOf(String name, DsSpec spec, Map<String, String> runtime) {
        if (runtime != null && runtime.containsKey(name)) {
            String v = runtime.get(name);
            return v == null ? "" : v;
        }
        DsSpec.Variable var = spec == null ? null : spec.findVariable(name);
        if (var == null) return "";
        return var.value == null ? "" : var.value;
    }
}
