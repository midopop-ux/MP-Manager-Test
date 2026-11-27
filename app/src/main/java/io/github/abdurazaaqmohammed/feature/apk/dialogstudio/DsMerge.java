package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Library operations: duplicate, merge without id collisions, call graph. */
public final class DsMerge {

    private DsMerge() {
    }

    public static DsSpec duplicate(DsSpec source, String newId) {
        if (source == null) return null;
        DsSpec copy = source.copy();
        copy.id = DsValidator.sanitizeId(newId);
        if (copy.id.isEmpty()) copy.id = source.id + "_copy";
        copy.name = source.name + " (نسخة)";
        return copy;
    }

    public static DsSpec merge(DsSpec a, DsSpec b) {
        if (a == null) return b == null ? null : b.copy();
        if (b == null) return a.copy();
        DsSpec out = a.copy();
        Map<String, String> idMap = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (DsSpec.Widget w : out.widgets) used.add(w.id);
        for (DsSpec.Widget w : b.widgets) {
            if (w == null) continue;
            String base = DsValidator.sanitizeId(w.id);
            if (base.isEmpty()) base = DsSpec.newWidgetId(w.kind, out.widgets.size());
            String id = base;
            int n = 2;
            while (used.contains(id)) id = base + "_" + (n++);
            used.add(id);
            idMap.put(w.id, id);
            DsSpec.Widget copy = DsSpec.copyWidget(w);
            copy.id = id;
            out.widgets.add(copy);
        }
        for (DsSpec.Variable v : b.variables) {
            if (v == null || DsSpec.empty(v.name)) continue;
            if (out.findVariable(v.name) == null) out.variables.add(DsSpec.copyVariable(v));
        }
        for (DsSpec.Event e : b.events) {
            if (e == null) continue;
            DsSpec.Event copy = DsSpec.copyEvent(e);
            if (idMap.containsKey(copy.widgetId)) copy.widgetId = idMap.get(copy.widgetId);
            for (DsSpec.Action action : copy.actions) {
                if (action != null && idMap.containsKey(action.target)) action.target = idMap.get(action.target);
            }
            if (out.findEvent(copy.widgetId, copy.event) == null) out.events.add(copy);
        }
        for (DsSpec.Automation rule : b.automations) {
            if (rule != null) out.automations.add(DsSpec.copyAutomation(rule));
        }
        for (DsSpec.Logic l : b.logic) {
            if (l == null) continue;
            DsSpec.Logic copy = DsSpec.copyLogic(l);
            if (idMap.containsKey(copy.fromWidget)) copy.fromWidget = idMap.get(copy.fromWidget);
            if (idMap.containsKey(copy.toWidget)) copy.toWidget = idMap.get(copy.toWidget);
            out.logic.add(copy);
        }
        for (String activity : b.activities) {
            if (!out.activities.contains(activity)) out.activities.add(activity);
        }
        return out;
    }

    public static DsSpec findById(List<DsSpec> library, String id) {
        if (library == null || id == null) return null;
        for (DsSpec spec : library) {
            if (spec != null && id.equals(spec.id)) return spec;
        }
        return null;
    }

    public static Map<String, List<String>> callGraph(List<DsSpec> library) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        if (library == null) return graph;
        for (DsSpec spec : library) {
            if (spec == null) continue;
            List<String> targets = new ArrayList<>();
            for (DsSpec.Event e : spec.events) {
                if (e == null) continue;
                for (DsSpec.Action a : e.actions) {
                    if (a != null && DsEvents.A_SHOW_DIALOG.equals(a.type)
                            && !DsSpec.empty(a.target) && !targets.contains(a.target)) {
                        targets.add(a.target);
                    }
                }
            }
            graph.put(spec.id, targets);
        }
        return graph;
    }

    public static List<String> findCycles(List<DsSpec> library) {
        Map<String, List<String>> graph = callGraph(library);
        List<String> cycles = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String node : graph.keySet()) {
            walk(node, graph, visiting, visited, cycles, new ArrayList<>());
        }
        return cycles;
    }

    private static void walk(String node, Map<String, List<String>> graph, Set<String> visiting,
                             Set<String> visited, List<String> cycles, List<String> path) {
        if (visited.contains(node)) return;
        if (visiting.contains(node)) {
            int at = path.indexOf(node);
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, at); i < path.size(); i++) {
                if (sb.length() > 0) sb.append(" ← ");
                sb.append(path.get(i));
            }
            if (sb.length() > 0) sb.append(" ← ").append(node);
            String cycle = sb.toString();
            if (!cycles.contains(cycle)) cycles.add(cycle);
            return;
        }
        visiting.add(node);
        path.add(node);
        List<String> targets = graph.get(node);
        if (targets != null) {
            for (String t : targets) walk(t, graph, visiting, visited, cycles, path);
        }
        path.remove(path.size() - 1);
        visiting.remove(node);
        visited.add(node);
    }

    public static String graphText(List<DsSpec> library) {
        Map<String, List<String>> graph = callGraph(library);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : graph.entrySet()) {
            sb.append(e.getKey());
            if (e.getValue().isEmpty()) {
                sb.append("  (لا يستدعي دايلوكات أخرى)\n");
            } else {
                sb.append("  →  ").append(String.join(", ", e.getValue())).append("\n");
            }
        }
        List<String> cycles = findCycles(library);
        if (!cycles.isEmpty()) {
            sb.append("\nاستدعاءات دائرية:\n");
            for (String c : cycles) sb.append("- ").append(c).append("\n");
        }
        return sb.toString();
    }

    public static String compare(DsSpec before, DsSpec after) {
        if (before == null || after == null) return "لا توجد بيانات كافية للمقارنة.";
        List<String> lines = new ArrayList<>();
        addDiff(lines, "العنوان", before.title, after.title);
        addDiff(lines, "الرسالة", before.message, after.message);
        addDiff(lines, "القالب", before.templateId, after.templateId);
        addDiff(lines, "التخطيط", before.layout, after.layout);
        addDiff(lines, "العرض %", String.valueOf(before.widthPercent), String.valueOf(after.widthPercent));
        addDiff(lines, "الزوايا", String.valueOf(before.cornerRadiusDp), String.valueOf(after.cornerRadiusDp));
        addDiff(lines, "الخلفية", String.valueOf(before.bgColor1), String.valueOf(after.bgColor1));
        Map<String, DsSpec.Widget> left = diffWidgets(before);
        Map<String, DsSpec.Widget> right = diffWidgets(after);
        for (String id : left.keySet()) {
            if (!right.containsKey(id)) lines.add("عنصر محذوف: " + id);
        }
        for (Map.Entry<String, DsSpec.Widget> e : right.entrySet()) {
            DsSpec.Widget a = left.get(e.getKey());
            if (a == null) {
                lines.add("عنصر جديد: " + e.getKey() + " (" + e.getValue().kind + ")");
                continue;
            }
            DsSpec.Widget b = e.getValue();
            addDiff(lines, "نص العنصر " + e.getKey(), a.text, b.text);
            addDiff(lines, "نوع العنصر " + e.getKey(), a.kind, b.kind);
            addDiff(lines, "تلميح العنصر " + e.getKey(), a.hint, b.hint);
            addDiff(lines, "شرط الظهور " + e.getKey(), a.visibleIf, b.visibleIf);
            addDiff(lines, "حجم النص " + e.getKey(), String.valueOf(a.textSizeSp), String.valueOf(b.textSizeSp));
            addDiff(lines, "لون النص " + e.getKey(), String.valueOf(a.textColor), String.valueOf(b.textColor));
            addDiff(lines, "الصفحة " + e.getKey(), String.valueOf(a.page), String.valueOf(b.page));
            addDiff(lines, "التفعيل " + e.getKey(), String.valueOf(a.enabled), String.valueOf(b.enabled));
            if (!diffOptions(a.options, b.options)) {
                lines.add("خيارات العنصر " + e.getKey() + ": " + (a.options == null ? 0 : a.options.size())
                        + " ← " + (b.options == null ? 0 : b.options.size()));
            }
        }
        Map<String, DsSpec.Variable> leftVars = diffVariables(before);
        Map<String, DsSpec.Variable> rightVars = diffVariables(after);
        for (String name : leftVars.keySet()) {
            if (!rightVars.containsKey(name)) lines.add("متغير محذوف: " + name);
        }
        for (Map.Entry<String, DsSpec.Variable> e : rightVars.entrySet()) {
            DsSpec.Variable a = leftVars.get(e.getKey());
            if (a == null) {
                lines.add("متغير جديد: " + e.getKey());
                continue;
            }
            DsSpec.Variable b = e.getValue();
            addDiff(lines, "قيمة المتغير " + e.getKey(), a.value, b.value);
            addDiff(lines, "نوع المتغير " + e.getKey(), a.type, b.type);
            addDiff(lines, "مصدر المتغير " + e.getKey(), a.source, b.source);
        }
        Map<String, DsSpec.Event> leftEvents = diffEvents(before);
        Map<String, DsSpec.Event> rightEvents = diffEvents(after);
        for (String key : leftEvents.keySet()) {
            if (!rightEvents.containsKey(key)) lines.add("حدث محذوف: " + key);
        }
        for (Map.Entry<String, DsSpec.Event> e : rightEvents.entrySet()) {
            DsSpec.Event a = leftEvents.get(e.getKey());
            if (a == null) {
                lines.add("حدث جديد: " + e.getKey() + " (" + size(e.getValue().actions) + " إجراء)");
                continue;
            }
            DsSpec.Event b = e.getValue();
            if (size(a.actions) != size(b.actions)) {
                lines.add("إجراءات " + e.getKey() + ": " + size(a.actions) + " ← " + size(b.actions));
            }
            if (!DsSpec.eq(a.condition, b.condition)) {
                lines.add("شرط الحدث " + e.getKey() + ": " + b.condition);
            }
        }
        addDiff(lines, "عدد الروابط", String.valueOf(size(before.logic)), String.valueOf(size(after.logic)));
        addDiff(lines, "عدد الأتمتة", String.valueOf(size(before.automations)), String.valueOf(size(after.automations)));
        if (lines.isEmpty()) return "لا توجد فروق هيكلية بين الدايلوكين.";
        StringBuilder sb = new StringBuilder("فروق الدايلوك:\n");
        int limit = Math.min(lines.size(), 160);
        for (int i = 0; i < limit; i++) sb.append("• ").append(lines.get(i)).append('\n');
        if (lines.size() > limit) sb.append("… و").append(lines.size() - limit).append(" فروق أخرى.\n");
        return sb.toString();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static void addDiff(List<String> lines, String label, String before, String after) {
        if (!DsSpec.eq(before, after)) {
            lines.add(label + ": " + (before == null ? "" : before) + " ← " + (after == null ? "" : after));
        }
    }

    private static boolean diffOptions(List<String> a, List<String> b) {
        if (a == null || b == null) return a == b;
        return a.equals(b);
    }

    private static Map<String, DsSpec.Widget> diffWidgets(DsSpec s) {
        Map<String, DsSpec.Widget> out = new LinkedHashMap<>();
        if (s.widgets == null) return out;
        for (DsSpec.Widget w : s.widgets) {
            if (w != null && !DsSpec.empty(w.id)) out.put(w.id, w);
        }
        return out;
    }

    private static Map<String, DsSpec.Variable> diffVariables(DsSpec s) {
        Map<String, DsSpec.Variable> out = new LinkedHashMap<>();
        if (s.variables == null) return out;
        for (DsSpec.Variable v : s.variables) {
            if (v != null && !DsSpec.empty(v.name)) out.put(v.name, v);
        }
        return out;
    }

    private static Map<String, DsSpec.Event> diffEvents(DsSpec s) {
        Map<String, DsSpec.Event> out = new LinkedHashMap<>();
        if (s.events == null) return out;
        for (DsSpec.Event e : s.events) {
            if (e != null) out.put(e.widgetId + "|" + e.event, e);
        }
        return out;
    }
}
