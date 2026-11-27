package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt construction and safe application of AI proposals. The model never
 * writes directly into the dialog: its JSON is parsed, validated and only then
 * merged into a copy that the user can review.
 */
public final class DsAi {

    public static final String MODE_CREATE = "CREATE";
    public static final String MODE_REDESIGN = "REDESIGN";
    public static final String MODE_EVENTS = "EVENTS";
    public static final String MODE_CONVERT = "CONVERT";
    public static final String MODE_FIX = "FIX";
    public static final String MODE_EXPLAIN = "EXPLAIN";
    public static final String MODE_SCENARIO = "SCENARIO";
    public static final String MODE_FUNCTIONS = "FUNCTIONS";

    public static class Patch {
        public String name = "";
        public String title = "";
        public String message = "";
        public String templateId = "";
        public String notes = "";
        public String javaCode = "";
        public boolean replaceWidgets = false;
        public List<DsSpec.Widget> widgets = new ArrayList<>();
        public List<DsSpec.Variable> variables = new ArrayList<>();
        public List<DsSpec.Event> events = new ArrayList<>();
        public List<DsSpec.Automation> automations = new ArrayList<>();
        public List<DsSpec.Logic> logic = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public String raw = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DsAi() {
    }

    public static String buildPrompt(DsSpec spec, String mode, String userText,
                                     List<DsNativeIndex.Method> methods) {
        StringBuilder sb = new StringBuilder();
        sb.append("أنت مهندس دايلوكات Android داخل أداة MP Manager. مهمتك إنتاج تعديل آمن وقابل للتنفيذ.\n");
        sb.append("أعد JSON فقط داخل كتلة ```json ... ```، دون أي شرح خارجها.\n\n");
        sb.append("المخطط المطلوب:\n");
        sb.append("{\n");
        sb.append("  \"name\":\"...\", \"title\":\"...\", \"message\":\"...\",\n");
        sb.append("  \"templateId\":\"...\", \"replaceWidgets\":true,\n");
        sb.append("  \"widgets\":[{\"id\":\"mp_text_0\",\"kind\":\"text|button|input|checkbox|spinner|divider|image\",\"text\":\"...\",\"hint\":\"...\",\"textSizeSp\":16,\"options\":[]}],\n");
        sb.append("  \"variables\":[{\"name\":\"user\",\"type\":\"string|number|boolean|list\",\"value\":\"...\",\"source\":\"static|input|date|time\",\"persistent\":false}],\n");
        sb.append("  \"events\":[{\"widgetId\":\"mp_button_0|@dialog\",\"event\":\"CLICK|LONG_CLICK|DOUBLE_CLICK|OPEN|CLOSE|CONFIRM|CANCEL|INPUT_CHANGE|TIMEOUT\",\"timeoutMs\":5000,\"actions\":[{\"type\":\"DISMISS|URL|TOAST|COPY|SET_TEXT|SET_VAR|CALL|IF_VAR|IF_INPUT|STOP|SHOW_DIALOG|DELAY\",\"target\":\"...\",\"value\":\"...\",\"value2\":\"...\",\"url\":\"...\",\"methodClass\":\"...\",\"methodName\":\"...\",\"methodSignature\":\"(String)\",\"methodArg\":\"...\"}]}],\n");
        sb.append("  \"automations\":[{\"trigger\":\"ACTIVITY_OPEN|AFTER_DELAY|CONDITION|CLICK|DATA_UPDATE|TIMER\",\"activity\":\"...\",\"delayMs\":0,\"intervalMs\":0}],\n");
        sb.append("  \"logic\":[{\"fromWidget\":\"mp_input_0\",\"toWidget\":\"mp_text_0\",\"fromVar\":\"\",\"operation\":\"SET_TEXT\",\"template\":\"...\"}],\n");
        sb.append("  \"notes\":\"شرح مختصر\", \"javaCode\":\"\"\n");
        sb.append("}\n\n");
        sb.append("قواعد إلزامية:\n");
        sb.append("- استخدم معرّفات [A-Za-z_][A-Za-z0-9_]* وفريدة لكل عنصر.\n");
        sb.append("- لا تستخدم إلا قوالب هذه القائمة: ").append(templateIds()).append(".\n");
        sb.append("- استخدم CALL فقط مع دوال static من قائمة الدوال المتاحة، وبمعامل واحد كحد أقصى.\n");
        sb.append("- لا تقترح SHOW_DIALOG إلى دايلوك غير موجود في المكتبة.\n");
        sb.append("- حافظ على النصوص العربية واتجاه RTL.\n");
        sb.append("- لا تكتب تعليمات تنفيذ أو أوامر نظام.\n\n");
        sb.append("النمط: ").append(mode).append("\n");
        if (!DsSpec.empty(userText)) sb.append("طلب المستخدم: ").append(userText).append("\n\n");
        sb.append("المواصفات الحالية:\n").append(GSON.toJson(spec)).append("\n\n");
        if (methods != null && !methods.isEmpty()) {
            sb.append("الدوال المتاحة للربط (static فقط):\n");
            int n = 0;
            for (DsNativeIndex.Method m : methods) {
                if (!m.supported) continue;
                sb.append("- ").append(m.owner).append("#").append(m.name)
                        .append("(").append(join(m.params)).append(") -> ").append(m.returnType).append("\n");
                if (++n >= 80) break;
            }
        }
        return sb.toString();
    }

    private static String templateIds() {
        StringBuilder sb = new StringBuilder();
        for (DsTemplates.Template t : DsTemplates.all()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.id);
        }
        return sb.toString();
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public static Patch parse(String response) {
        Patch patch = new Patch();
        patch.raw = response == null ? "" : response;
        if (DsSpec.empty(response)) {
            patch.warnings.add("استجابة فارغة من الذكاء الاصطناعي.");
            return patch;
        }
        String json = response.trim();
        int fence = json.indexOf("```");
        if (fence >= 0) {
            int start = json.indexOf('\n', fence);
            int end = json.indexOf("```", start < 0 ? fence + 3 : start);
            if (start >= 0 && end > start) json = json.substring(start + 1, end).trim();
        }
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first < 0 || last <= first) {
            patch.warnings.add("لم يتم العثور على JSON في الاستجابة.");
            return patch;
        }
        json = json.substring(first, last + 1);
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            patch.name = str(o, "name");
            patch.title = str(o, "title");
            patch.message = str(o, "message");
            patch.templateId = str(o, "templateId");
            patch.notes = str(o, "notes");
            patch.javaCode = str(o, "javaCode");
            patch.replaceWidgets = bool(o, "replaceWidgets");
            if (o.has("widgets") && o.get("widgets").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("widgets")) {
                    if (!e.isJsonObject()) continue;
                    DsSpec.Widget w = GSON.fromJson(e, DsSpec.Widget.class);
                    if (w != null) patch.widgets.add(w);
                }
            }
            if (o.has("variables") && o.get("variables").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("variables")) {
                    if (!e.isJsonObject()) continue;
                    DsSpec.Variable v = GSON.fromJson(e, DsSpec.Variable.class);
                    if (v != null) patch.variables.add(v);
                }
            }
            if (o.has("events") && o.get("events").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("events")) {
                    if (!e.isJsonObject()) continue;
                    DsSpec.Event ev = GSON.fromJson(e, DsSpec.Event.class);
                    if (ev != null) patch.events.add(ev);
                }
            }
            if (o.has("automations") && o.get("automations").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("automations")) {
                    if (!e.isJsonObject()) continue;
                    DsSpec.Automation a = GSON.fromJson(e, DsSpec.Automation.class);
                    if (a != null) patch.automations.add(a);
                }
            }
            if (o.has("logic") && o.get("logic").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("logic")) {
                    if (!e.isJsonObject()) continue;
                    DsSpec.Logic l = GSON.fromJson(e, DsSpec.Logic.class);
                    if (l != null) patch.logic.add(l);
                }
            }
        } catch (Exception e) {
            patch.warnings.add("تعذر تحليل JSON: " + e.getMessage());
        }
        return patch;
    }

    public static DsSpec apply(DsSpec base, Patch patch, boolean replaceWidgets) {
        if (base == null) return null;
        DsSpec out = base.copy();
        if (patch == null) return out;
        if (!DsSpec.empty(patch.name)) out.name = patch.name;
        if (!DsSpec.empty(patch.title)) out.title = patch.title;
        if (!DsSpec.empty(patch.message)) out.message = patch.message;
        if (!DsSpec.empty(patch.templateId) && DsTemplates.has(patch.templateId)) {
            out.templateId = patch.templateId;
            DsTemplates.apply(out, DsTemplates.get(patch.templateId));
        }
        if ((replaceWidgets || patch.replaceWidgets) && !patch.widgets.isEmpty()) {
            out.widgets.clear();
            for (DsSpec.Widget w : patch.widgets) {
                if (w == null) continue;
                DsSpec.Widget c = DsSpec.copyWidget(w);
                if (DsSpec.empty(c.id)) c.id = DsSpec.newWidgetId(c.kind, out.widgets.size());
                out.widgets.add(c);
            }
        } else {
            for (DsSpec.Widget w : patch.widgets) {
                if (w == null) continue;
                DsSpec.Widget existing = out.findWidget(w.id);
                if (existing != null) {
                    DsSpec.Widget c = DsSpec.copyWidget(w);
                    out.widgets.set(out.widgets.indexOf(existing), c);
                } else {
                    out.widgets.add(DsSpec.copyWidget(w));
                }
            }
        }
        for (DsSpec.Variable v : patch.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            DsSpec.Variable existing = out.findVariable(v.name);
            if (existing != null) {
                int at = out.variables.indexOf(existing);
                out.variables.set(at, DsSpec.copyVariable(v));
            } else {
                out.variables.add(DsSpec.copyVariable(v));
            }
        }
        for (DsSpec.Event e : patch.events) {
            if (e == null) continue;
            DsSpec.Event existing = out.findEvent(e.widgetId, e.event);
            if (existing != null) out.events.set(out.events.indexOf(existing), DsSpec.copyEvent(e));
            else out.events.add(DsSpec.copyEvent(e));
        }
        if (!patch.automations.isEmpty()) {
            out.automations.clear();
            for (DsSpec.Automation a : patch.automations) {
                if (a != null) out.automations.add(DsSpec.copyAutomation(a));
            }
        }
        for (DsSpec.Logic l : patch.logic) {
            if (l != null) out.logic.add(DsSpec.copyLogic(l));
        }
        if (!DsSpec.empty(patch.notes)) out.aiNotes = patch.notes;
        DsValidator.fix(out, null);
        return out;
    }

    public static String diffSummary(DsSpec before, DsSpec after) {
        if (before == null || after == null) return "";
        List<String> lines = new ArrayList<>();
        if (!DsSpec.eq(before.title, after.title)) lines.add("العنوان: " + after.title);
        if (!DsSpec.eq(before.message, after.message)) lines.add("الرسالة: " + after.message);
        if (!DsSpec.eq(before.templateId, after.templateId)) lines.add("القالب: " + after.templateId);
        if (before.widgets.size() != after.widgets.size()) {
            lines.add("العناصر: " + before.widgets.size() + " ← " + after.widgets.size());
        }
        if (before.variables.size() != after.variables.size()) {
            lines.add("المتغيرات: " + before.variables.size() + " ← " + after.variables.size());
        }
        if (before.events.size() != after.events.size()) {
            lines.add("الأحداث: " + before.events.size() + " ← " + after.events.size());
        }
        if (!lines.isEmpty()) return "التغييرات المقترحة:\n- " + String.join("\n- ", lines);
        return "لا توجد تغييرات هيكلية واضحة.";
    }

    private static String str(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean bool(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public static String modeLabel(String mode) {
        if (MODE_CREATE.equals(mode)) return "إنشاء دايلوك من وصف";
        if (MODE_REDESIGN.equals(mode)) return "إعادة تصميم احترافية";
        if (MODE_EVENTS.equals(mode)) return "إضافة أحداث وإجراءات";
        if (MODE_CONVERT.equals(mode)) return "تحويل تصميم تقليدي";
        if (MODE_FIX.equals(mode)) return "إصلاح الأخطاء";
        if (MODE_EXPLAIN.equals(mode)) return "شرح العناصر والوظائف";
        if (MODE_SCENARIO.equals(mode)) return "إنشاء سيناريو تفاعلي";
        if (MODE_FUNCTIONS.equals(mode)) return "اقتراح دوال التطبيق";
        return mode;
    }
}
