package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Index of callable methods discovered in the target project. The studio only
 * offers calls that are safe to inject: static, application-owned and with at
 * most one primitive/String argument.
 */
public final class DsNativeIndex {

    public static class Method {
        public String owner = "";
        public String name = "";
        public String returnType = "void";
        public List<String> params = new ArrayList<>();
        public boolean isStatic = false;
        public String source = "";
        public boolean supported = false;
        public boolean unsafeBody = false;
        public String blockedReason = "";

        public String signature() {
            return owner + "#" + name + "(" + join(params) + ")";
        }

        public String descriptor() {
            StringBuilder sb = new StringBuilder();
            sb.append("L").append(owner.replace('.', '/')).append(";->").append(name).append("(");
            for (String p : params) sb.append(typeDescriptor(p));
            sb.append(")").append(typeDescriptor(returnType));
            return sb.toString();
        }

        public String display() {
            return (isStatic ? "static " : "") + returnType + " " + name + "(" + join(params) + ")";
        }

        private static String join(List<String> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(list.get(i));
            }
            return sb.toString();
        }
    }

    private static final Set<String> ALLOWED_PARAMS = new LinkedHashSet<>();
    private static final Set<String> ALLOWED_RETURNS = new LinkedHashSet<>();
    private static final String[] BLOCKED = {
            "exec", "exit", "killprocess", "loadlibrary", "loadclass", "forname",
            "setaccessible", "invoke", "getruntime", "processbuilder", "reflection",
            "defineclass", "finalize", "halt", "abort"
    };

    static {
        for (String s : new String[]{"String", "int", "boolean", "long", "float", "double"}) {
            ALLOWED_PARAMS.add(s);
        }
        for (String s : new String[]{"void", "String", "int", "boolean", "long", "float", "double"}) {
            ALLOWED_RETURNS.add(s);
        }
    }

    private final List<Method> methods = new ArrayList<>();
    private final Set<String> seen = new LinkedHashSet<>();
    private String packagePrefix = "";

    public void setPackagePrefix(String prefix) {
        packagePrefix = prefix == null ? "" : prefix.trim();
    }

    public void clear() {
        methods.clear();
        seen.clear();
    }

    public List<Method> methods() {
        return new ArrayList<>(methods);
    }

    public int size() {
        return methods.size();
    }

    public void add(Method m) {
        if (m == null || DsSpec.empty(m.owner) || DsSpec.empty(m.name)) return;
        m.owner = m.owner.replace('/', '.');
        String sig = m.signature();
        if (!seen.add(sig)) return;
        evaluateSupport(m);
        methods.add(m);
    }

    public void add(String owner, String name, String returnType, List<String> params, boolean isStatic, String source) {
        Method m = new Method();
        m.owner = owner;
        m.name = name;
        m.returnType = returnType == null || returnType.isEmpty() ? "void" : normalizeType(returnType);
        m.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
        m.isStatic = isStatic;
        m.source = source == null ? "" : source;
        add(m);
    }

    private void evaluateSupport(Method m) {
        m.supported = true;
        m.blockedReason = "";
        if (m.unsafeBody) {
            m.supported = false;
            m.blockedReason = "جسم الدالة يحتوي على عملية حساسة (تنفيذ أو تحميل كود).";
            return;
        }
        String lowerName = m.name == null ? "" : m.name.toLowerCase(Locale.US);
        for (String b : BLOCKED) {
            if (lowerName.contains(b)) {
                m.supported = false;
                m.blockedReason = "دالة حساسة أو غير آمنة للحقن: " + m.name;
                return;
            }
        }
        if (!m.isStatic) {
            m.supported = false;
            m.blockedReason = "الدالة غير ثابتة (instance)؛ الحقن يدعم الدوال static فقط.";
            return;
        }
        if (!packagePrefix.isEmpty() && !m.owner.startsWith(packagePrefix)) {
            m.supported = false;
            m.blockedReason = "الدالة خارج حزمة التطبيق المستهدف.";
            return;
        }
        if (m.owner.startsWith("java.") || m.owner.startsWith("android.")
                || m.owner.startsWith("androidx.") || m.owner.startsWith("kotlin.")
                || m.owner.startsWith("dalvik.") || m.owner.startsWith("com.android.")) {
            m.supported = false;
            m.blockedReason = "مكتبة نظام غير مسموح باستدعائها من الدايلوك.";
            return;
        }
        if (m.params.size() > 1) {
            m.supported = false;
            m.blockedReason = "عدد المعاملات أكبر من واحد؛ غير مدعوم في الحقن.";
            return;
        }
        for (String p : m.params) {
            if (!ALLOWED_PARAMS.contains(normalizeType(p))) {
                m.supported = false;
                m.blockedReason = "نوع معامل غير مدعوم: " + p;
                return;
            }
        }
        if (!ALLOWED_RETURNS.contains(normalizeType(m.returnType))) {
            m.supported = false;
            m.blockedReason = "نوع إرجاع غير مدعوم: " + m.returnType;
        }
    }

    public List<Method> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<Method> out = new ArrayList<>();
        for (Method m : methods) {
            String hay = (m.owner + " " + m.name + " " + m.display()).toLowerCase(Locale.US);
            if (q.isEmpty() || hay.contains(q)) out.add(m);
            if (out.size() >= limit) break;
        }
        out.sort((a, b) -> {
            if (a.supported != b.supported) return a.supported ? -1 : 1;
            int c = a.owner.compareToIgnoreCase(b.owner);
            return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    public Method find(String owner, String name, List<String> params) {
        for (Method m : methods) {
            if (m.owner.equals(owner) && m.name.equals(name)
                    && (params == null || m.params.equals(params))) return m;
        }
        return null;
    }

    public String validate(DsSpec.Action action) {
        if (action == null || !"CALL".equals(action.type)) return "ليست عملية استدعاء.";
        if (DsSpec.empty(action.methodClass) || DsSpec.empty(action.methodName)) {
            return "لم يتم اختيار دالة للاستدعاء.";
        }
        Method m = null;
        for (Method candidate : methods) {
            if (candidate.owner.equals(action.methodClass) && candidate.name.equals(action.methodName)) {
                m = candidate;
                break;
            }
        }
        if (m == null) {
            return "الدالة غير موجودة في فهرس المشروع: "
                    + action.methodClass + "#" + action.methodName;
        }
        if (!m.supported) return m.blockedReason;
        if (!m.params.isEmpty() && DsSpec.empty(action.methodArg)) {
            return "الدالة تتطلب معاملًا من نوع " + m.params.get(0) + ".";
        }
        String p = m.params.isEmpty() ? "" : normalizeType(m.params.get(0));
        if ("int".equals(p) || "long".equals(p) || "float".equals(p) || "double".equals(p)) {
            try {
                Double.parseDouble(action.methodArg.trim());
            } catch (Exception e) {
                return "قيمة المعامل ليست رقمًا صالحًا: " + action.methodArg;
            }
        } else if ("boolean".equals(p)) {
            if (!"true".equalsIgnoreCase(action.methodArg) && !"false".equalsIgnoreCase(action.methodArg)) {
                return "قيمة المعامل يجب أن تكون true أو false.";
            }
        }
        return null;
    }

    public String smaliInvocation(DsSpec.Action action, String stringReg, String intReg,
                                  String longReg, String floatReg, String boolReg) {
        if (action == null) return null;
        Method m = null;
        for (Method candidate : methods) {
            if (candidate.owner.equals(action.methodClass) && candidate.name.equals(action.methodName)) {
                m = candidate;
                break;
            }
        }
        if (m == null || !m.supported) return null;
        String descriptor = m.descriptor();
        if (m.params.isEmpty()) return "invoke-static {}, " + descriptor;
        String type = normalizeType(m.params.get(0));
        String reg;
        if ("String".equals(type)) reg = stringReg;
        else if ("int".equals(type)) reg = intReg;
        else if ("long".equals(type)) reg = longReg;
        else if ("float".equals(type)) reg = floatReg;
        else if ("boolean".equals(type)) reg = boolReg;
        else return null;
        return "invoke-static {" + reg + "}, " + descriptor;
    }

    public String javaInvocation(DsSpec.Action action) {
        if (action == null) return "";
        String arg = action.methodArg == null ? "" : action.methodArg;
        String type = "";
        for (Method m : methods) {
            if (m.owner.equals(action.methodClass) && m.name.equals(action.methodName)) {
                type = m.params.isEmpty() ? "" : normalizeType(m.params.get(0));
                break;
            }
        }
        String argCode;
        if (type.isEmpty()) argCode = "";
        else if ("String".equals(type)) argCode = quote(arg);
        else if ("boolean".equals(type)) argCode = "true".equalsIgnoreCase(arg) ? "true" : "false";
        else if ("long".equals(type)) argCode = arg + "L";
        else if ("float".equals(type)) argCode = arg + "f";
        else argCode = arg;
        return action.methodClass + "." + action.methodName + "(" + argCode + ")";
    }

    public static String normalizeType(String type) {
        if (type == null) return "void";
        String t = type.trim();
        if (t.startsWith("L") && t.endsWith(";")) {
            String body = t.substring(1, t.length() - 1);
            int slash = body.lastIndexOf('/');
            return slash >= 0 ? body.substring(slash + 1) : body;
        }
        if ("I".equals(t)) return "int";
        if ("Z".equals(t)) return "boolean";
        if ("J".equals(t)) return "long";
        if ("F".equals(t)) return "float";
        if ("D".equals(t)) return "double";
        if ("V".equals(t)) return "void";
        if ("[C".equals(t)) return "char[]";
        if (t.endsWith("[]")) return t;
        int space = t.lastIndexOf(' ');
        if (space >= 0) t = t.substring(space + 1);
        if (t.contains("<")) t = t.substring(0, t.indexOf('<'));
        return t;
    }

    public static String typeDescriptor(String type) {
        String t = normalizeType(type);
        if ("void".equals(t)) return "V";
        if ("int".equals(t)) return "I";
        if ("boolean".equals(t)) return "Z";
        if ("long".equals(t)) return "J";
        if ("float".equals(t)) return "F";
        if ("double".equals(t)) return "D";
        if ("String".equals(t)) return "Ljava/lang/String;";
        if (t.endsWith("[]")) return "[" + typeDescriptor(t.substring(0, t.length() - 2));
        return "L" + t.replace('.', '/') + ";";
    }

    private static String quote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*"
                    + "((?:public|private|protected|static|final|synchronized|native|abstract|default|strictfp)\\s+)+"
                    + "([\\w.$<>\\[\\], ?]+?)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)");

    private static final Pattern KOTLIN_METHOD = Pattern.compile(
            "(?m)^\\s*(?:(?:public|private|protected|internal|open|override|suspend|inline|operator|tailrec)\\s+)*"
                    + "fun\\s+(?:<[^>]+>\\s*)?([A-Za-z_][\\w]*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([\\w.<>?]+))?");

    private static final Pattern SMALI_METHOD = Pattern.compile(
            "(?m)^\\s*\\.method\\s+([^\\n]+)$");

    public void indexJava(String source, String owner) {
        if (source == null) return;
        Matcher m = JAVA_METHOD.matcher(source);
        while (m.find()) {
            String modifiers = m.group(1);
            String ret = m.group(2).trim();
            String name = m.group(3);
            if ("class".equals(name) || "interface".equals(name) || "enum".equals(name)) continue;
            if (ret.contains(" new ") || ret.equals("new")) continue;
            List<String> params = parseJavaParams(m.group(4));
            Method method = new Method();
            method.owner = owner;
            method.name = name;
            method.returnType = normalizeType(ret);
            method.params = params;
            method.isStatic = modifiers.contains("static");
            method.source = "java";
            method.unsafeBody = bodyUnsafe(javaBody(source, m.end()));
            add(method);
        }
    }

    public void indexKotlin(String source, String owner) {
        if (source == null) return;
        Matcher m = KOTLIN_METHOD.matcher(source);
        while (m.find()) {
            String name = m.group(1);
            List<String> params = parseKotlinParams(m.group(2));
            String ret = m.group(3) == null ? "void" : m.group(3).trim();
            String line = m.group();
            boolean isStatic = line.contains(" @JvmStatic") || line.contains("companion object");
            Method method = new Method();
            method.owner = owner;
            method.name = name;
            method.returnType = normalizeType(ret);
            method.params = params;
            method.isStatic = isStatic;
            method.source = "kotlin";
            method.unsafeBody = bodyUnsafe(javaBody(source, m.end()));
            add(method);
        }
    }

    /**
     * Indexes a real .dex file or every classes*.dex inside an APK/JAR. This is
     * what makes the native bridge usable for released APKs, not only for
     * decompiled source trees.
     */
    public int indexDex(File file) throws IOException {
        if (file == null || !file.isFile()) return 0;
        String lower = file.getName().toLowerCase(Locale.US);
        if (lower.endsWith(".apk") || lower.endsWith(".jar")
                || lower.endsWith(".zip") || lower.endsWith(".apks")) {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
            int count = 0;
            for (String entry : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry =
                        container.getEntry(entry);
                if (dexEntry != null) count += indexDexFile(dexEntry.getDexFile());
            }
            return count;
        }
        return indexDexFile(DexFileFactory.loadDexFile(file, Opcodes.getDefault()));
    }

    private static final int MAX_DEX_METHODS = 12000;

    private int indexDexFile(DexBackedDexFile dex) {
        if (dex == null) return 0;
        int count = 0;
        for (ClassDef classDef : dex.getClasses()) {
            if (methods.size() >= MAX_DEX_METHODS) break;
            if (classDef == null) continue;
            String owner = normalizeType(classDef.getType());
            for (com.android.tools.smali.dexlib2.iface.Method dm : classDef.getMethods()) {
                if (dm == null) continue;
                String name = dm.getName();
                if ("<init>".equals(name) || "<clinit>".equals(name)) continue;
                Method method = new Method();
                method.owner = owner;
                method.name = name;
                method.returnType = normalizeType(dm.getReturnType());
                method.params = new ArrayList<>();
                for (CharSequence param : dm.getParameterTypes()) {
                    method.params.add(normalizeType(param == null ? "void" : param.toString()));
                }
                method.isStatic = (dm.getAccessFlags()
                        & com.android.tools.smali.dexlib2.AccessFlags.STATIC.getValue()) != 0;
                method.source = "dex";
                method.unsafeBody = dexBodyUnsafe(dm.getImplementation());
                add(method);
                count++;
                if (methods.size() >= MAX_DEX_METHODS) break;
            }
        }
        return count;
    }

    private static boolean dexBodyUnsafe(MethodImplementation implementation) {
        if (implementation == null) return false;
        for (Instruction instruction : implementation.getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (reference instanceof MethodReference) {
                String name = ((MethodReference) reference).getName().toLowerCase(Locale.US);
                if (name.contains("exec") || name.contains("loadclass")
                        || name.contains("loadlibrary") || name.contains("forname")
                        || name.contains("setaccessible") || name.contains("killprocess")
                        || name.contains("exit") || name.contains("halt")
                        || name.contains("abort")) {
                    return true;
                }
            } else if (reference instanceof StringReference) {
                String value = String.valueOf(((StringReference) reference).getString()).toLowerCase(Locale.US);
                if (value.contains("exec(") || value.contains("processbuilder")
                        || value.contains("dexclassloader") || value.contains("pathclassloader")
                        || value.contains("system.load")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void indexSmali(String source, String owner) {
        if (source == null) return;
        Matcher m = SMALI_METHOD.matcher(source);
        while (m.find()) {
            String header = m.group(1).trim();
            int paren = header.indexOf('(');
            if (paren < 0) continue;
            String before = header.substring(0, paren).trim();
            String after = header.substring(paren);
            int space = before.lastIndexOf(' ');
            if (space < 0) continue;
            String name = before.substring(space + 1);
            if ("<init>".equals(name) || "<clinit>".equals(name)) continue;
            String modifiers = before.substring(0, space);
            int close = after.indexOf(')');
            if (close < 0) continue;
            String paramsDesc = after.substring(1, close);
            String retDesc = after.substring(close + 1).trim();
            List<String> params = parseDescriptorParams(paramsDesc);
            add(owner, name, normalizeType(retDesc), params,
                    modifiers.contains("static"), "smali");
        }
    }

    private static String javaBody(String source, int from) {
        int brace = source.indexOf('{', from);
        if (brace < 0) return "";
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        return source.substring(brace);
    }

    private static String smaliBody(String source, int from) {
        int end = source.indexOf(".end method", from);
        return end < 0 ? source.substring(from) : source.substring(from, end);
    }

    private static boolean bodyUnsafe(String body) {
        if (body == null || body.isEmpty()) return false;
        String lower = body.toLowerCase(Locale.US);
        String[] blocked = {"exec(", "processbuilder", "system.exit", "system.load",
                "runtime.", "killprocess", "class.forname", "setaccessible",
                "dexclassloader", "pathclassloader", "runtime.getruntime"};
        for (String b : blocked) {
            if (lower.contains(b)) return true;
        }
        return false;
    }

    private static List<String> parseJavaParams(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            p = p.replaceAll("@[A-Za-z_][A-Za-z0-9_.]*(?:\\([^)]*\\))?", "").trim();
            p = p.replace("final ", "").trim();
            int space = p.lastIndexOf(' ');
            if (space > 0) p = p.substring(0, space).trim();
            out.add(normalizeType(p));
        }
        return out;
    }

    private static List<String> parseKotlinParams(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int colon = p.indexOf(':');
            String type = colon >= 0 ? p.substring(colon + 1).trim() : "String";
            int eq = type.indexOf('=');
            if (eq >= 0) type = type.substring(0, eq).trim();
            type = type.replace("?", "");
            out.add(normalizeType(type));
        }
        return out;
    }

    private static List<String> parseDescriptorParams(String desc) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            if (c == '[') {
                int start = i;
                i++;
                while (i < desc.length() && desc.charAt(i) == '[') i++;
                if (i < desc.length() && desc.charAt(i) == 'L') {
                    int semi = desc.indexOf(';', i);
                    i = semi < 0 ? desc.length() : semi + 1;
                } else {
                    i++;
                }
                out.add(desc.substring(start, Math.min(i, desc.length())));
            } else if (c == 'L') {
                int semi = desc.indexOf(';', i);
                if (semi < 0) break;
                out.add(normalizeType(desc.substring(i, semi + 1)));
                i = semi + 1;
            } else {
                out.add(normalizeType(String.valueOf(c)));
                i++;
            }
        }
        return out;
    }
}
