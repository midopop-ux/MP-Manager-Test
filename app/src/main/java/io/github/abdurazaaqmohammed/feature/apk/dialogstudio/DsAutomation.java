package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Dialog automation rules and their generated code. */
public final class DsAutomation {

    public static final String ACTIVITY_OPEN = "ACTIVITY_OPEN";
    public static final String AFTER_DELAY = "AFTER_DELAY";
    public static final String CONDITION = "CONDITION";
    public static final String CLICK = "CLICK";
    public static final String DATA_UPDATE = "DATA_UPDATE";
    public static final String TIMER = "TIMER";

    private static final List<String> TRIGGERS = Arrays.asList(
            ACTIVITY_OPEN, AFTER_DELAY, CONDITION, CLICK, DATA_UPDATE, TIMER);

    private DsAutomation() {
    }

    public static List<String> triggers() {
        return new ArrayList<>(TRIGGERS);
    }

    public static String describe(String trigger) {
        if (trigger == null) return "";
        switch (trigger) {
            case ACTIVITY_OPEN:
                return "عند فتح شاشة";
            case AFTER_DELAY:
                return "بعد انتهاء عملية / تأخير";
            case CONDITION:
                return "عند تحقق شرط";
            case CLICK:
                return "عند الضغط على عنصر";
            case DATA_UPDATE:
                return "عند وصول بيانات جديدة";
            case TIMER:
                return "مؤقت مجدول";
            default:
                return trigger;
        }
    }

    public static boolean injectable(String trigger) {
        return ACTIVITY_OPEN.equals(trigger) || AFTER_DELAY.equals(trigger);
    }

    public static List<String> validate(DsSpec spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;
        for (DsSpec.Automation a : spec.automations) {
            if (a == null || !a.enabled) continue;
            if (!TRIGGERS.contains(a.trigger)) {
                out.add("مشغّل أتمتة غير معروف: " + a.trigger);
            }
            if (ACTIVITY_OPEN.equals(a.trigger) && DsSpec.empty(a.activity)) {
                out.add("أتمتة فتح الشاشة تحتاج اسم الشاشة.");
            }
            if (TIMER.equals(a.trigger) && a.intervalMs <= 0) {
                out.add("المؤقت يحتاج فاصلًا موجبًا.");
            }
        }
        return out;
    }

    public static String javaSnippet(DsSpec spec, DsSpec.Automation rule, String className) {
        if (spec == null || rule == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("// أتمتة: ").append(describe(rule.trigger)).append("\n");
        if (ACTIVITY_OPEN.equals(rule.trigger)) {
            sb.append("if (\"").append(rule.activity == null ? "" : rule.activity)
                    .append("\".equals(activityName)) { ").append(className).append(".show(context); }\n");
        } else if (AFTER_DELAY.equals(rule.trigger)) {
            sb.append(className).append(".showDelayed(context, ").append(Math.max(0, rule.delayMs)).append("L);\n");
        } else if (CONDITION.equals(rule.trigger)) {
            sb.append("if (").append(rule.condition == null ? "true" : rule.condition)
                    .append(") { ").append(className).append(".show(context); }\n");
        } else if (CLICK.equals(rule.trigger)) {
            sb.append("// اربط هذا الاستدعاء بحدث النقر للعنصر المحدد.\n");
        } else if (DATA_UPDATE.equals(rule.trigger)) {
            sb.append("// استدعِ ").append(className).append(".show(context) بعد تحديث البيانات.\n");
        } else if (TIMER.equals(rule.trigger)) {
            sb.append("// مؤقت: كل ").append(Math.max(1000, rule.intervalMs)).append("ms\n");
        }
        return sb.toString();
    }

    public static String summary(DsSpec spec) {
        if (spec == null || spec.automations.isEmpty()) return "لا توجد قواعد أتمتة.";
        StringBuilder sb = new StringBuilder();
        for (DsSpec.Automation a : spec.automations) {
            if (a == null || !a.enabled) continue;
            sb.append("• ").append(describe(a.trigger));
            if (!DsSpec.empty(a.activity)) sb.append(" (").append(a.activity).append(")");
            if (a.delayMs > 0) sb.append(" بعد ").append(a.delayMs).append("ms");
            if (a.intervalMs > 0) sb.append(" كل ").append(a.intervalMs).append("ms");
            sb.append("\n");
        }
        return sb.length() == 0 ? "لا توجد قواعد أتمتة مفعّلة." : sb.toString();
    }
}
