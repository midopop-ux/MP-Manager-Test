package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Finds dialogs in decompiled projects and APKs (decoded XML) and converts
 * them into editable specs. Parsing is intentionally conservative: it extracts
 * only structure it can prove and reports a confidence value.
 */
public final class DsRecovery {

    public static class Found {
        public String path = "";
        public String type = "xml";
        public String name = "";
        public String title = "";
        public String message = "";
        public String layoutRef = "";
        public String activity = "";
        public final List<String> buttons = new ArrayList<>();
        public final List<String> widgetIds = new ArrayList<>();
        public final List<String> handlers = new ArrayList<>();
        public final List<String> activities = new ArrayList<>();
        public final List<DsSpec.Widget> specWidgets = new ArrayList<>();
        public int confidence = 0;
        public String note = "";
        public DsSpec spec;

        public String key() {
            return type + "|" + path + "|" + name + "|" + title;
        }

        public String display() {
            String label = !DsSpec.empty(title) ? title : (!DsSpec.empty(name) ? name : path);
            return label + "  ·  " + type + "  ·  " + confidence + "%";
        }
    }

    private static final Pattern ID_REF = Pattern.compile("@\\+?id/([A-Za-z0-9_]+)");
    private static final Pattern ANDROID_ID = Pattern.compile("android:id\\s*=\\s*\"@\\+?id/([A-Za-z0-9_]+)\"");
    private static final Pattern ANDROID_TEXT = Pattern.compile("android:(?:text|hint)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ANDROID_ONCLICK = Pattern.compile("android:onClick\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern R_LAYOUT = Pattern.compile("R\\.layout\\.([A-Za-z0-9_]+)");
    private static final Pattern R_ID = Pattern.compile("R\\.id\\.([A-Za-z0-9_]+)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SET_TITLE = Pattern.compile("setTitle\\s*\\(([^)]*)\\)");
    private static final Pattern SET_MESSAGE = Pattern.compile("setMessage\\s*\\(([^)]*)\\)");
    private static final Pattern SET_BUTTON = Pattern.compile(
            "set(Positive|Negative|Neutral)Button\\s*\\(([^;]*?)\\)");
    private static final Pattern SET_VIEW = Pattern.compile("setView\\s*\\(([^)]*)\\)");
    private static final Pattern FIND_ID = Pattern.compile("findViewById\\s*\\(\\s*R\\.id\\.([A-Za-z0-9_]+)");
    private static final Pattern ON_CLICK = Pattern.compile(
            "setOnClickListener\\s*\\(\\s*(?:new\\s+[\\w.$]+\\s*\\([^)]*\\)\\s*\\{)?|->\\s*\\{");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(?m)^\\s*(?:(?:public|private|protected|static|final|override|open|suspend)\\s+)*"
                    + "(?:void|fun|boolean|Boolean|Unit)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern SMALI_CLASS = Pattern.compile("(?m)^\\.class\\s+[^\\n]*?(L[^;]+;)");
    private static final Pattern SMALI_METHOD = Pattern.compile("(?m)^\\.method\\s+[^\\n]*?([A-Za-z_<>][A-Za-z0-9_<>]*)\\([^\\n]*$");
    private static final Pattern SMALI_STRING = Pattern.compile("const-string(?:/jumbo)?\\s+[vp]\\d+\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SMALI_INVOKE = Pattern.compile("invoke-(?:virtual|static|direct|interface)\\s+\\{[^}]*}\\s*,\\s*([^\\s]+)->([A-Za-z0-9_<>$]+)\\(([^)]*)\\)");

    private DsRecovery() {
    }

    public static List<Found> scanText(String path, String content) {
        List<Found> out = new ArrayList<>();
        if (content == null || content.isEmpty()) return out;
        String lower = path == null ? "" : path.toLowerCase(Locale.US);
        if (lower.endsWith(".xml")) {
            Found f = scanXml(path, content);
            if (f != null) out.add(f);
        } else if (lower.endsWith(".java")) {
            out.addAll(scanJava(path, content, false));
        } else if (lower.endsWith(".kt")) {
            out.addAll(scanJava(path, content, true));
        } else if (lower.endsWith(".smali")) {
            out.addAll(scanSmali(path, content));
        }
        return out;
    }

    public static Found scanXml(String path, String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            if (root == null) return null;
            String rootTag = local(root);
            String file = baseName(path);
            boolean dialogFile = file.contains("dialog") || file.contains("alert")
                    || file.contains("popup") || file.contains("sheet") || file.contains("modal");
            int buttons = 0;
            int texts = 0;
            int inputs = 0;
            int signals = 0;
            List<Element> all = new ArrayList<>();
            collect(root, all);
            for (Element e : all) {
                String tag = local(e);
                if ("Button".equals(tag) || "MaterialButton".equals(tag)
                        || "AppCompatButton".equals(tag)) buttons++;
                if ("TextView".equals(tag) || "MaterialTextView".equals(tag)
                        || "AppCompatTextView".equals(tag)) texts++;
                if ("EditText".equals(tag) || "TextInputEditText".equals(tag)
                        || "MaterialAutoCompleteTextView".equals(tag)) inputs++;
                String id = attr(e, "id").toLowerCase(Locale.US);
                if (id.contains("title") || id.contains("message") || id.contains("body")
                        || id.contains("content") || id.contains("positive")
                        || id.contains("negative") || id.contains("neutral")
                        || id.contains("confirm") || id.contains("cancel")
                        || id.contains("ok") || id.contains("alert")
                        || id.contains("prompt") || id.contains("dialog")) {
                    signals++;
                }
            }
            boolean dialogRoot = rootTag.contains("Dialog") || rootTag.contains("Sheet")
                    || rootTag.contains("Popup") || rootTag.contains("Card");
            if (!dialogFile && !(dialogRoot && buttons > 0 && texts > 0)
                    && !(buttons > 0 && texts > 0 && signals >= 2)) return null;
            if (buttons == 0 && inputs == 0 && !dialogFile) return null;

            Found f = new Found();
            f.path = path;
            f.type = "xml";
            f.name = file;
            f.confidence = Math.min(95, 35 + (dialogFile ? 30 : 0) + (buttons > 0 ? 15 : 0)
                    + (texts > 0 ? 10 : 0) + (inputs > 0 ? 10 : 0));
            f.layoutRef = file;

            String bestTitle = "";
            String bestMessage = "";
            int titleScore = -1;
            for (Element e : all) {
                String tag = local(e);
                String id = attr(e, "id").replace("@+id/", "").replace("@id/", "");
                String text = firstString(e, "text");
                String hint = firstString(e, "hint");
                if (id.isEmpty()) continue;
                f.widgetIds.add(id);
                String lowerId = id.toLowerCase(Locale.US);
                if (("TextView".equals(tag) || "MaterialTextView".equals(tag) || "AppCompatTextView".equals(tag))
                        && !text.isEmpty()) {
                    int score = 0;
                    if (lowerId.contains("title")) score += 40;
                    if (lowerId.contains("message") || lowerId.contains("body")
                            || lowerId.contains("content")) score += 30;
                    if (score > titleScore) {
                        titleScore = score;
                        bestTitle = text;
                    }
                    if (lowerId.contains("message") || lowerId.contains("body")
                            || lowerId.contains("content")) bestMessage = text;
                    DsSpec.Widget w = new DsSpec.Widget();
                    w.id = id;
                    w.kind = "text";
                    w.text = text;
                    w.textSizeSp = 16f;
                    applyXmlStyle(e, w);
                    f.specWidgets.add(w);
                } else if ("Button".equals(tag) || "MaterialButton".equals(tag)
                        || "AppCompatButton".equals(tag)) {
                    String label = text.isEmpty() ? id : text;
                    f.buttons.add(label);
                    DsSpec.Widget w = new DsSpec.Widget();
                    w.id = id;
                    w.kind = "button";
                    w.text = label;
                    applyXmlStyle(e, w);
                    f.specWidgets.add(w);
                } else if ("EditText".equals(tag) || "TextInputEditText".equals(tag)) {
                    DsSpec.Widget w = new DsSpec.Widget();
                    w.id = id;
                    w.kind = "input";
                    w.hint = hint;
                    applyXmlStyle(e, w);
                    f.specWidgets.add(w);
                }
                String onClick = attr(e, "onClick");
                if (!onClick.isEmpty()) f.handlers.add(onClick);
            }
            f.title = bestTitle;
            f.message = bestMessage;
            if (f.title.isEmpty() && !f.buttons.isEmpty()) f.title = f.buttons.get(0);
            f.spec = toSpec(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyXmlStyle(Element e, DsSpec.Widget w) {
        if (e == null || w == null) return;
        String width = attr(e, "layout_width");
        if ("match_parent".equals(width) || "fill_parent".equals(width)) w.widthPercent = 100;
        else if ("wrap_content".equals(width)) w.widthPercent = 0;
        int height = dimension(attr(e, "layout_height"));
        if (height > 0) w.heightDp = height;
        float textSize = dimensionFloat(attr(e, "textSize"));
        if (textSize > 0) w.textSizeSp = textSize;
        int color = color(attr(e, "textColor"));
        if (color != 0) w.textColor = color;
        int bg = color(attr(e, "background"));
        if (bg != 0) w.btnBg = bg;
        int padding = dimension(attr(e, "padding"));
        if (padding <= 0) padding = dimension(attr(e, "paddingStart"));
        if (padding > 0) w.btnPaddingDp = padding;
        String input = attr(e, "inputType");
        if (!input.isEmpty()) w.inputType = input;
        String enabled = attr(e, "enabled");
        if ("false".equals(enabled)) w.enabled = false;
        String checked = attr(e, "checked");
        if ("true".equals(checked)) w.checked = true;
    }

    private static int dimension(String raw) {
        if (raw == null) return 0;
        String v = raw.trim().toLowerCase(Locale.US);
        if (v.endsWith("dp") || v.endsWith("dip")) v = v.substring(0, v.length() - 2);
        else if (v.endsWith("px")) v = v.substring(0, v.length() - 2);
        else if (v.endsWith("sp")) v = v.substring(0, v.length() - 2);
        try { return Math.round(Float.parseFloat(v)); } catch (Exception e) { return 0; }
    }

    private static float dimensionFloat(String raw) {
        if (raw == null) return 0;
        String v = raw.trim().toLowerCase(Locale.US);
        if (v.endsWith("sp") || v.endsWith("dp") || v.endsWith("dip") || v.endsWith("px")) {
            v = v.substring(0, v.length() - 2);
        }
        try { return Float.parseFloat(v); } catch (Exception e) { return 0; }
    }

    private static int color(String raw) {
        if (raw == null) return 0;
        String v = raw.trim();
        if (!v.startsWith("#")) return 0;
        v = v.substring(1);
        try {
            if (v.length() == 3) {
                int r = Integer.parseInt(v.substring(0, 1), 16);
                int g = Integer.parseInt(v.substring(1, 2), 16);
                int b = Integer.parseInt(v.substring(2, 3), 16);
                return 0xFF000000 | (r * 17 << 16) | (g * 17 << 8) | (b * 17);
            }
            if (v.length() == 4) {
                int a = Integer.parseInt(v.substring(0, 1), 16);
                int r = Integer.parseInt(v.substring(1, 2), 16);
                int g = Integer.parseInt(v.substring(2, 3), 16);
                int b = Integer.parseInt(v.substring(3, 4), 16);
                return (a * 17 << 24) | (r * 17 << 16) | (g * 17 << 8) | (b * 17);
            }
            if (v.length() == 6) return (int) (0xFF000000L | Long.parseLong(v, 16));
            if (v.length() == 8) return (int) Long.parseLong(v, 16);
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static List<Found> scanJava(String path, String content, boolean kotlin) {
        List<Found> out = new ArrayList<>();
        String[] chunks = content.split("(?=new\\s+(?:[A-Za-z0-9_.]+\\.)?(?:AlertDialog\\.Builder|MaterialAlertDialogBuilder))");
        if (chunks.length <= 1) {
            // Some projects wrap construction in helper methods; fall back to the whole file.
            chunks = new String[]{content};
        }
        int index = 0;
        for (String chunk : chunks) {
            if (!chunk.contains("AlertDialog") && !chunk.contains("MaterialAlertDialogBuilder")) continue;
            Found f = new Found();
            f.path = path;
            f.type = kotlin ? "kotlin" : "java";
            f.name = baseName(path) + (chunks.length > 1 ? "#" + (++index) : "");
            f.confidence = 55;
            Matcher title = SET_TITLE.matcher(chunk);
            if (title.find()) {
                f.title = valueOf(title.group(1));
                f.confidence += 10;
            }
            Matcher msg = SET_MESSAGE.matcher(chunk);
            if (msg.find()) {
                f.message = valueOf(msg.group(1));
                f.confidence += 10;
            }
            Matcher buttons = SET_BUTTON.matcher(chunk);
            while (buttons.find()) {
                String which = buttons.group(1);
                String label = valueOf(buttons.group(2));
                if (DsSpec.empty(label)) label = which;
                f.buttons.add(label);
                f.confidence += 5;
            }
            Matcher view = SET_VIEW.matcher(chunk);
            if (view.find()) {
                Matcher layout = R_LAYOUT.matcher(view.group(1));
                if (layout.find()) {
                    f.layoutRef = layout.group(1);
                    f.confidence += 10;
                }
            }
            Matcher ids = FIND_ID.matcher(chunk);
            while (ids.find()) {
                String id = ids.group(1);
                if (!f.widgetIds.contains(id)) f.widgetIds.add(id);
            }
            Matcher onClick = ON_CLICK.matcher(chunk);
            if (onClick.find()) f.handlers.add("setOnClickListener");
            Matcher methods = METHOD_DECL.matcher(content);
            while (methods.find()) {
                String name = methods.group(1);
                if (!f.handlers.contains(name) && f.handlers.size() < 12) f.handlers.add(name);
            }
            f.activity = guessActivity(path, content);
            if (!f.activity.isEmpty()) f.activities.add(f.activity);
            if (f.title.isEmpty() && f.message.isEmpty() && f.layoutRef.isEmpty()) continue;
            f.spec = toSpec(f);
            out.add(f);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private static List<Found> scanSmali(String path, String content) {
        List<Found> out = new ArrayList<>();
        String[] chunks = content.split("(?=new-instance\\s+[vp]\\d+,\\s*Landroid/app/AlertDialog\\$Builder;)");
        int index = 0;
        for (String chunk : chunks) {
            if (!chunk.contains("AlertDialog$Builder") && !chunk.contains("MaterialAlertDialogBuilder")) continue;
            Found f = new Found();
            f.path = path;
            f.type = "smali";
            f.name = baseName(path) + (chunks.length > 1 ? "#" + (++index) : "");
            f.confidence = 45;
            Matcher cls = SMALI_CLASS.matcher(content);
            if (cls.find()) {
                String owner = cls.group(1);
                f.activity = owner.startsWith("L") ? owner.substring(1, owner.length() - 1).replace('/', '.') : owner;
                f.activities.add(f.activity);
            }
            Matcher invoke = SMALI_INVOKE.matcher(chunk);
            while (invoke.find()) {
                String method = invoke.group(2);
                if ("setTitle".equals(method)) {
                    f.title = nearestString(chunk, invoke.start());
                    f.confidence += 10;
                } else if ("setMessage".equals(method)) {
                    f.message = nearestString(chunk, invoke.start());
                    f.confidence += 10;
                } else if (method.startsWith("set") && method.endsWith("Button")) {
                    String label = nearestString(chunk, invoke.start());
                    if (!label.isEmpty()) f.buttons.add(label);
                    f.confidence += 5;
                } else if ("setView".equals(method)) {
                    f.confidence += 5;
                } else if ("setOnClickListener".equals(method)) {
                    f.handlers.add("setOnClickListener");
                }
            }
            Matcher methods = SMALI_METHOD.matcher(content);
            while (methods.find()) {
                String name = methods.group(1);
                if (!"onCreate".equals(name) && f.handlers.size() < 12) f.handlers.add(name);
            }
            if (f.title.isEmpty() && f.message.isEmpty() && f.buttons.isEmpty()) continue;
            f.spec = toSpec(f);
            out.add(f);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private static String nearestString(String chunk, int position) {
        int start = Math.max(0, position - 500);
        String window = chunk.substring(start, Math.min(chunk.length(), position));
        Matcher m = SMALI_STRING.matcher(window);
        String last = "";
        while (m.find()) last = m.group(1);
        return unescape(last);
    }

    private static String guessActivity(String path, String content) {
        Matcher m = Pattern.compile("class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+extends\\s+[\\w.]+Activity").matcher(content);
        if (m.find()) return m.group(1);
        String file = baseName(path);
        return file.endsWith("Activity") ? file : "";
    }

    public static DsSpec toSpec(Found f) {
        if (f.spec != null) {
            f.spec.id = DsValidator.sanitizeId(DsSpec.empty(f.name) ? "recovered" : f.name);
            if (f.spec.id.isEmpty()) f.spec.id = "recovered_dialog";
            f.spec.name = f.name;
            f.spec.title = f.title;
            f.spec.message = f.message;
            f.spec.activities = new ArrayList<>(f.activities);
            return f.spec;
        }
        DsSpec spec = new DsSpec();
        spec.id = DsValidator.sanitizeId(DsSpec.empty(f.name) ? "recovered" : f.name);
        if (spec.id.isEmpty()) spec.id = "recovered_dialog";
        spec.name = f.name;
        spec.title = f.title;
        spec.message = f.message;
        spec.activities = new ArrayList<>(f.activities);
        if (!f.buttons.isEmpty()) spec.positive = f.buttons.get(0);
        if (f.buttons.size() > 1) spec.negative = f.buttons.get(1);
        for (DsSpec.Widget w : f.specWidgets) {
            if (w == null) continue;
            DsSpec.Widget copy = DsSpec.copyWidget(w);
            copy.id = DsValidator.sanitizeId(copy.id);
            if (copy.id.isEmpty()) copy.id = DsSpec.newWidgetId(copy.kind, spec.widgets.size());
            spec.widgets.add(copy);
        }
        if (spec.widgets.isEmpty()) {
            for (String id : f.widgetIds) {
                DsSpec.Widget w = new DsSpec.Widget();
                w.id = DsValidator.sanitizeId(id);
                w.kind = "text";
                w.text = id;
                w.textSizeSp = 15f;
                spec.widgets.add(w);
            }
        }
        if (spec.widgets.isEmpty()) {
            DsSpec.Widget w = new DsSpec.Widget();
            w.id = "mp_recovered_text";
            w.kind = "text";
            w.text = DsSpec.empty(f.message) ? f.title : f.message;
            spec.widgets.add(w);
        }
        return spec;
    }

    /** Adds recovered widgets/events into an existing spec without losing its design. */
    public static void mergeInto(DsSpec target, Found f) {
        if (target == null || f == null) return;
        DsSpec src = f.spec != null ? f.spec : toSpec(f);
        if (!DsSpec.empty(src.title)) target.title = src.title;
        if (!DsSpec.empty(src.message)) target.message = src.message;
        if (!DsSpec.empty(src.positive)) target.positive = src.positive;
        if (!DsSpec.empty(src.negative)) target.negative = src.negative;
        Set<String> ids = new LinkedHashSet<>();
        for (DsSpec.Widget w : target.widgets) ids.add(w.id);
        for (DsSpec.Widget w : src.widgets) {
            if (w == null) continue;
            String id = DsValidator.sanitizeId(w.id);
            String base = id;
            int n = 2;
            while (ids.contains(id)) id = base + "_" + (n++);
            DsSpec.Widget copy = DsSpec.copyWidget(w);
            copy.id = id;
            target.widgets.add(copy);
            ids.add(id);
        }
        for (String a : src.activities) {
            if (!target.activities.contains(a)) target.activities.add(a);
        }
    }

    public static List<Found> scanDirectory(File root, int maxFiles) {
        List<Found> out = new ArrayList<>();
        if (root == null || !root.exists()) return out;
        scanDir(root, out, maxFiles, 0);
        return out;
    }

    private static int scanDir(File dir, List<Found> out, int maxFiles, int depth) {
        if (dir == null || depth > 24 || out.size() >= maxFiles) return depth;
        File[] files = dir.listFiles();
        if (files == null) return depth;
        for (File file : files) {
            if (out.size() >= maxFiles) break;
            String name = file.getName();
            if (file.isDirectory()) {
                if (name.startsWith(".") || "build".equals(name) || "node_modules".equals(name)
                        || "intermediates".equals(name) || "generated".equals(name)) continue;
                scanDir(file, out, maxFiles, depth + 1);
                continue;
            }
            String lower = name.toLowerCase(Locale.US);
            if (!(lower.endsWith(".xml") || lower.endsWith(".java")
                    || lower.endsWith(".kt") || lower.endsWith(".smali"))) continue;
            if (file.length() <= 0 || file.length() > 3L * 1024L * 1024L) continue;
            String text = readFile(file);
            if (text.isEmpty()) continue;
            out.addAll(scanText(file.getAbsolutePath(), text));
        }
        return depth;
    }

    public static List<Found> scanEntries(Map<String, String> pathToContent) {
        List<Found> out = new ArrayList<>();
        if (pathToContent == null) return out;
        for (Map.Entry<String, String> e : pathToContent.entrySet()) {
            String path = e.getKey();
            String lower = path == null ? "" : path.toLowerCase(Locale.US);
            if (!(lower.endsWith(".xml") || lower.endsWith(".java")
                    || lower.endsWith(".kt") || lower.endsWith(".smali"))) continue;
            out.addAll(scanText(path, e.getValue()));
        }
        return out;
    }

    public static String readFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 4L * 1024L * 1024L)];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String valueOf(String expression) {
        if (expression == null) return "";
        Matcher m = STRING_LITERAL.matcher(expression);
        if (m.find()) return unescape(m.group(1));
        int dot = expression.lastIndexOf('.');
        if (dot >= 0) return expression.substring(dot + 1).trim();
        return expression.trim();
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static String baseName(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String local(Element e) {
        String name = e.getLocalName();
        if (name == null) name = e.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String attr(Element e, String localName) {
        for (int i = 0; i < e.getAttributes().getLength(); i++) {
            Node n = e.getAttributes().item(i);
            String name = n.getLocalName();
            if (name == null) name = n.getNodeName();
            if (name.equals(localName) || name.endsWith(":" + localName)) {
                String value = n.getNodeValue();
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private static String firstString(Element e, String localName) {
        String value = attr(e, localName);
        if (!value.isEmpty()) return value;
        return "";
    }

    private static void collect(Element e, List<Element> out) {
        out.add(e);
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) collect((Element) n, out);
        }
    }
}
