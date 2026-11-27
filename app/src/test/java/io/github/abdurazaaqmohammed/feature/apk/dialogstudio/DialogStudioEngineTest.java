package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DialogStudioEngineTest {

    private static DsSpec.Widget widget(String id, String kind, String text) {
        DsSpec.Widget w = new DsSpec.Widget();
        w.id = id;
        w.kind = kind;
        w.text = text;
        w.textSizeSp = 15f;
        return w;
    }

    private static DsSpec sample() {
        DsSpec s = new DsSpec();
        s.id = "welcome";
        s.name = "Welcome";
        s.packageName = "com.example.app";
        s.title = "مرحبا";
        s.message = "اختر";
        s.widgets.add(widget("mp_text_0", "text", "مرحبا {{user}}"));
        DsSpec.Widget input = widget("mp_input_1", "input", "");
        input.hint = "الاسم";
        input.variable = "user";
        s.widgets.add(input);
        s.widgets.add(widget("mp_button_2", "button", "متابعة"));
        DsSpec.Variable v = new DsSpec.Variable();
        v.name = "user";
        v.value = "أحمد";
        s.variables.add(v);
        DsSpec.Event e = new DsSpec.Event();
        e.widgetId = "mp_button_2";
        e.event = DsEvents.CLICK;
        DsSpec.Action toast = new DsSpec.Action();
        toast.type = DsEvents.A_TOAST;
        toast.value = "تم {{user}}";
        e.actions.add(toast);
        s.events.add(e);
        return s;
    }

    @Test
    public void templateLibraryHasAtLeastTwentyDistinctRealPresets() {
        List<DsTemplates.Template> all = DsTemplates.all();
        assertTrue(all.size() >= 70);
        List<String> ids = new ArrayList<>();
        List<String> layouts = new ArrayList<>();
        for (DsTemplates.Template t : all) {
            assertFalse(t.id.isEmpty());
            assertFalse(ids.contains(t.id));
            assertFalse(t.layout == null || t.layout.isEmpty());
            assertFalse(t.category == null || t.category.isEmpty());
            ids.add(t.id);
            if (!layouts.contains(t.layout)) layouts.add(t.layout);
        }
        assertTrue("templates must be layout-distinct", layouts.size() >= 12);
        DsSpec s = sample();
        DsTemplates.apply(s, DsTemplates.get("cyberpunk"));
        assertEquals("cyberpunk", s.templateId);
        assertTrue(s.rainbowAnim);
        assertTrue(s.cornerRadiusDp > 0);
        assertEquals("متابعة", s.widgets.get(2).text);
        DsTemplates.apply(s, DsTemplates.get("ios_sheet"));
        assertEquals("ios_sheet", s.layout);
        assertTrue(s.verticalButtons);
        DsTemplates.apply(s, DsTemplates.get("notification_banner"));
        assertEquals(1, s.titleAlign);
    }

    @Test
    public void expandedPremiumTemplatesMapToRealLayouts() {
        String[][] expected = {
                {"clay_soft", "classic"},
                {"neo_brutal", "compact"},
                {"lavender_mist", "frosted_glass"},
                {"desert_sand", "classic"},
                {"retro_terminal", "terminal"},
                {"sunset_glass", "liquid_glass"},
                {"bamboo", "minimal"},
                {"silver_metal", "floating"},
                {"coral_reef", "media"},
                {"midnight_neon", "animated"}
        };
        DsSpec s = new DsSpec();
        for (String[] pair : expected) {
            DsTemplates.Template template = DsTemplates.get(pair[0]);
            assertNotNull(pair[0], template);
            assertEquals(pair[0], pair[1], template.layout);
            DsTemplates.apply(s, template);
            assertEquals(pair[0], pair[0], s.templateId);
            assertTrue(pair[0] + " must produce real widgets", s.widgets.size() >= 2);
        }
    }

    @Test
    public void applyingTemplateToEmptySpecSeedsRealPreviewWidgets() {
        DsSpec s = new DsSpec();
        DsTemplates.apply(s, DsTemplates.get("ios_alert"));
        assertTrue(s.widgets.size() >= 2);
        assertFalse(s.title.isEmpty());
        assertFalse(s.message.isEmpty());
        assertTrue(s.widgets.get(0).id.startsWith("mp_btn_"));
        assertEquals("button", s.widgets.get(0).kind);
    }

    @Test
    public void customTemplateRoundTripAndSpecCopyKeepExtendedFields() {
        DsSpec s = sample();
        s.layout = "form";
        s.verticalButtons = true;
        s.previewDevice = "iphonese";
        s.previewWallpaper = "ocean";
        s.previewDark = true;
        DsSpec.Link link = new DsSpec.Link();
        link.text = "فتح";
        link.url = "https://example.com";
        link.mode = "browser";
        s.links.add(link);
        DsSpec copy = s.copy();
        assertEquals("form", copy.layout);
        assertTrue(copy.verticalButtons);
        assertEquals("iphonese", copy.previewDevice);
        assertEquals("ocean", copy.previewWallpaper);
        assertTrue(copy.previewDark);
        assertEquals(1, copy.links.size());
        assertEquals("https://example.com", copy.links.get(0).url);

        DsTemplates.Template t = DsTemplates.fromSpec(copy, "custom_test", "قالب اختبار");
        assertNotNull(t);
        assertTrue(t.custom);
        assertEquals("form", t.layout);
        DsTemplates.register(t);
        assertEquals("form", DsTemplates.get("custom_test").layout);
        DsTemplates.unregister("custom_test");
        assertFalse(DsTemplates.has("custom_test"));
    }

    @Test
    public void clonePackageValidationRejectsUnsafeNames() {
        assertTrue(io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine.isValidPackageName("com.example.clone"));
        assertFalse(io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine.isValidPackageName("single"));
        assertFalse(io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine.isValidPackageName("com..bad"));
        assertFalse(io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine.isValidPackageName("com.example.1bad"));
        assertFalse(io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine.isValidPackageName(""));
    }

    @Test
    public void variablesResolvePlaceholdersAndConditions() {
        DsSpec s = sample();
        assertEquals("مرحبا أحمد", DsVars.resolve("مرحبا {{user}}", s));
        assertEquals("مرحبا أحمد", DsVars.resolve("مرحبا ${user}", s));
        Map<String, String> runtime = new LinkedHashMap<>();
        runtime.put("user", "سارة");
        assertEquals("مرحبا سارة", DsVars.resolve("مرحبا {{user}}", s, runtime));
        assertTrue(DsVars.evaluate("user==أحمد", s, null, null));
        assertFalse(DsVars.evaluate("user==سارة", s, null, null));
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("mp_input_1", "نص");
        assertTrue(DsVars.evaluate("input:mp_input_1 notempty", s, null, inputs));
        assertTrue(DsVars.evaluate("user==أحمد && input:mp_input_1 notempty", s, null, inputs));
        assertTrue(DsVars.validate(s).isEmpty());
        s.variables.add(s.variables.get(0));
        assertFalse(DsVars.validate(s).isEmpty());
    }

    @Test
    public void nativeIndexRejectsUnsafeCallsAndAcceptsSafeStaticOnes() {
        DsNativeIndex index = new DsNativeIndex();
        index.setPackagePrefix("com.example.app");
        index.indexJava("package com.example.app;\n"
                + "public class Util {\n"
                + "  public static void save(String value) {}\n"
                + "  public static String read() { return \"\"; }\n"
                + "  public void instance(String value) {}\n"
                + "  public static void run() { Runtime.getRuntime().exec(\"x\"); }\n"
                + "}\n", "com.example.app.Util");
        assertTrue(index.size() >= 3);
        List<DsNativeIndex.Method> found = index.search("save", 10);
        assertEquals("save", found.get(0).name);
        assertTrue(found.get(0).supported);
        assertTrue(index.find("com.example.app.Util", "instance", null) == null
                || !index.find("com.example.app.Util", "instance", null).supported);
        DsSpec.Action action = new DsSpec.Action();
        action.type = DsEvents.A_CALL;
        action.methodClass = "com.example.app.Util";
        action.methodName = "save";
        action.methodArg = "ok";
        assertNull(index.validate(action));
        String invocation = index.smaliInvocation(action, "v1", "v2", "v3", "v4", "v5");
        assertNotNull(invocation);
        assertTrue(invocation.contains("Lcom/example/app/Util;->save(Ljava/lang/String;)V"));
        action.methodName = "missing";
        assertNotNull(index.validate(action));
    }

    @Test
    public void recoveryFindsXmlJavaAndSmaliDialogs() {
        String xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                + "<TextView android:id=\"@+id/title\" android:text=\"عنوان\"/>"
                + "<TextView android:id=\"@+id/message\" android:text=\"رسالة\"/>"
                + "<Button android:id=\"@+id/ok\" android:text=\"موافق\"/>"
                + "<Button android:id=\"@+id/cancel\" android:text=\"إلغاء\"/>"
                + "</LinearLayout>";
        DsRecovery.Found f = DsRecovery.scanXml("res/layout/dialog_welcome.xml", xml);
        assertNotNull(f);
        assertEquals("عنوان", f.title);
        assertEquals("رسالة", f.message);
        assertEquals(2, f.buttons.size());
        assertNotNull(f.spec);
        assertTrue(f.spec.widgets.size() >= 4);

        String java = "class MainActivity extends Activity {\n"
                + " void x() { new AlertDialog.Builder(this).setTitle(\"تأكيد\")\n"
                + "  .setMessage(\"هل تريد المتابعة؟\")\n"
                + "  .setPositiveButton(\"نعم\", null).setNegativeButton(\"لا\", null)\n"
                + "  .setView(R.layout.dialog_confirm).show(); }\n"
                + "}";
        List<DsRecovery.Found> javaFound = DsRecovery.scanText("src/MainActivity.java", java);
        assertFalse(javaFound.isEmpty());
        assertEquals("تأكيد", javaFound.get(0).title);
        assertEquals("dialog_confirm", javaFound.get(0).layoutRef);

        String smali = ".class public Lcom/example/Main;\n"
                + ".super Landroid/app/Activity;\n"
                + ".method public show()V\n"
                + "  new-instance v0, Landroid/app/AlertDialog$Builder;\n"
                + "  const-string v1, \"عنوان سملي\"\n"
                + "  invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n"
                + "  const-string v1, \"رسالة\"\n"
                + "  invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setMessage(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n"
                + "  invoke-virtual {v0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;\n"
                + ".end method\n";
        List<DsRecovery.Found> smaliFound = DsRecovery.scanText("smali/com/example/Main.smali", smali);
        assertFalse(smaliFound.isEmpty());
        assertEquals("عنوان سملي", smaliFound.get(0).title);
    }

    @Test
    public void validatorFindsAndRepairsDuplicatesAndInvalidActions() {
        DsSpec s = sample();
        s.widgets.add(widget("mp_text_0", "text", "مكرر"));
        DsSpec.Action badUrl = new DsSpec.Action();
        badUrl.type = DsEvents.A_URL;
        s.events.get(0).actions.add(badUrl);
        s.cornerRadiusDp = 500f;
        DsValidator.Report before = DsValidator.validate(s, null);
        assertFalse(before.ok());
        assertTrue(before.fixable() > 0);
        DsValidator.Report after = DsValidator.fix(s, null);
        assertEquals(0, after.errors());
        assertTrue(s.cornerRadiusDp <= 64f);
        assertEquals(4, s.widgets.size());
        assertFalse(s.widgets.get(2).id.equals(s.widgets.get(3).id));
        assertEquals(1, s.events.get(0).actions.size());
    }

    @Test
    public void codeGeneratorEmitsLayoutDrawableAndCompilableJavaShape() {
        DsSpec s = sample();
        DsTemplates.apply(s, DsTemplates.get("liquid_glass"));
        String xml = DsCodeGen.layoutXml(s);
        assertTrue(xml.contains("@+id/mp_button_2"));
        assertTrue(xml.contains("@drawable/mp_bg_welcome"));
        String drawable = DsCodeGen.drawableXml(s);
        assertTrue(drawable.contains("<corners"));
        String java = DsCodeGen.javaSource(s);
        assertTrue(java.contains("class MpDialog_Welcome"));
        assertTrue(java.contains("setOnClickListener"));
        assertTrue(java.contains("render(\"تم أحمد\")"));
        assertTrue(java.contains("public static void show(Context context)"));
        assertTrue(java.contains("updateLogic(\"mp_input_1\")"));
    }


    @Test
    public void studioSmaliKeepsEachEventInItsOwnMethod() {
        DsSpec s = sample();
        DsSpec.Event longClick = new DsSpec.Event();
        longClick.widgetId = "mp_button_2";
        longClick.event = DsEvents.LONG_CLICK;
        DsSpec.Action copy = new DsSpec.Action();
        copy.type = DsEvents.A_COPY;
        copy.value = "x";
        longClick.actions.add(copy);
        s.events.add(longClick);
        DsSpec.Event open = new DsSpec.Event();
        open.widgetId = "@dialog";
        open.event = DsEvents.OPEN;
        DsSpec.Action openToast = new DsSpec.Action();
        openToast.type = DsEvents.A_TOAST;
        openToast.value = "open";
        open.actions.add(openToast);
        s.events.add(open);

        SmaliStudioInjector.Plan plan = SmaliStudioInjector.build("Lcom/example/MainActivity;", s);
        assertEquals("run0", plan.method("mp_button_2", DsEvents.CLICK));
        assertEquals("run1", plan.method("mp_button_2", DsEvents.LONG_CLICK));
        assertEquals("run2", plan.method("@dialog", DsEvents.OPEN));
        assertEquals("run2", plan.openMethod);

        String actions = null;
        for (String cls : plan.classes) {
            if (cls.contains(".class public final " + plan.actionsType)) actions = cls;
        }
        assertNotNull(actions);
        assertTrue(actions.contains(".method public static run0("));
        assertTrue(actions.contains(".method public static run1("));
        assertTrue(actions.contains(".method public static run2("));
        assertFalse(actions.contains("->run("));

        boolean clickCallsRun0 = false;
        for (String cls : plan.classes) {
            if (cls.contains("Lcom/example/MainActivity$mpSEv0;")) {
                clickCallsRun0 = cls.contains("->run0(Landroid/content/Context;");
            }
        }
        assertTrue(clickCallsRun0);
    }

    @Test
    public void studioSmaliConditionsUseRealVariables() {
        DsSpec s = sample();
        DsSpec.Action ifVar = new DsSpec.Action();
        ifVar.type = DsEvents.A_IF_VAR;
        ifVar.condition = "user == أحمد";
        s.events.get(0).actions.add(ifVar);
        SmaliStudioInjector.Plan plan = SmaliStudioInjector.build("Lcom/example/MainActivity;", s);
        String actions = null;
        for (String cls : plan.classes) {
            if (cls.contains(".class public final " + plan.actionsType)) actions = cls;
        }
        assertNotNull(actions);
        assertTrue(actions.contains("mpSVars;->get(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(actions.contains("String;->equals(Ljava/lang/Object;)Z"));
        assertFalse(actions.contains("mpSVars;->get(\"user == أحمد\")"));
    }

    @Test
    public void aiPatchParsesAndAppliesWithValidation() {
        String response = "```json\n{\n"
                + "\"name\":\"نسخة ذكية\",\n"
                + "\"title\":\"عنوان جديد\",\n"
                + "\"message\":\"رسالة جديدة\",\n"
                + "\"templateId\":\"material_you\",\n"
                + "\"replaceWidgets\":true,\n"
                + "\"widgets\":[{\"id\":\"mp_text_0\",\"kind\":\"text\",\"text\":\"نص\"},"
                + "{\"id\":\"mp_button_1\",\"kind\":\"button\",\"text\":\"حسنًا\"}],\n"
                + "\"events\":[{\"widgetId\":\"mp_button_1\",\"event\":\"CLICK\",\"actions\":[{\"type\":\"DISMISS\"}]}],\n"
                + "\"notes\":\"تم\"\n}\n```";
        DsAi.Patch patch = DsAi.parse(response);
        assertTrue(patch.warnings.isEmpty());
        assertEquals("عنوان جديد", patch.title);
        DsSpec out = DsAi.apply(sample(), patch, true);
        assertEquals("عنوان جديد", out.title);
        assertEquals(2, out.widgets.size());
        assertEquals("material_you", out.templateId);
        assertFalse(DsAi.diffSummary(sample(), out).isEmpty());
    }

    @Test
    public void mergeRemapsIdsAndDetectsCircularCalls() {
        DsSpec a = sample();
        DsSpec b = sample();
        b.id = "second";
        b.widgets.add(widget("mp_text_0", "text", "ثانٍ"));
        DsSpec merged = DsMerge.merge(a, b);
        assertEquals(7, merged.widgets.size());
        assertTrue(merged.widgets.get(3).id.startsWith("mp_text_0_"));
        List<DsSpec> library = new ArrayList<>();
        DsSpec one = sample();
        one.id = "one";
        DsSpec two = sample();
        two.id = "two";
        DsSpec.Action call = new DsSpec.Action();
        call.type = DsEvents.A_SHOW_DIALOG;
        call.target = "two";
        DsSpec.Event e1 = new DsSpec.Event();
        e1.widgetId = "mp_button_2";
        e1.event = DsEvents.CLICK;
        e1.actions.add(call);
        one.events.add(e1);
        DsSpec.Action back = new DsSpec.Action();
        back.type = DsEvents.A_SHOW_DIALOG;
        back.target = "one";
        DsSpec.Event e2 = new DsSpec.Event();
        e2.widgetId = "mp_button_2";
        e2.event = DsEvents.CLICK;
        e2.actions.add(back);
        two.events.add(e2);
        library.add(one);
        library.add(two);
        assertFalse(DsMerge.findCycles(library).isEmpty());
    }

    @Test
    public void injectionSupportIsHonestAboutUnsupportedActions() {
        DsSpec.Action toast = new DsSpec.Action();
        toast.type = DsEvents.A_TOAST;
        toast.value = "x";
        assertTrue(DsEvents.injectionSupported(DsEvents.CLICK, toast));
        DsSpec.Action nested = new DsSpec.Action();
        nested.type = DsEvents.A_SHOW_DIALOG;
        nested.target = "other";
        assertFalse(DsEvents.injectionSupported(DsEvents.CLICK, nested));
        DsSpec.Action delay = new DsSpec.Action();
        delay.type = DsEvents.A_DELAY;
        assertFalse(DsEvents.injectionSupported(DsEvents.CLICK, delay));
    }

    @Test
    public void recoveryKeepsXmlSizesColorsAndPadding() {
        String xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                + "<TextView android:id=\"@+id/title\" android:text=\"عنوان\""
                + " android:textSize=\"22sp\" android:textColor=\"#FF336699\""
                + " android:layout_width=\"match_parent\"/>"
                + "<Button android:id=\"@+id/ok\" android:text=\"موافق\""
                + " android:background=\"#FF112233\" android:padding=\"14dp\""
                + " android:layout_height=\"48dp\"/>"
                + "</LinearLayout>";
        DsRecovery.Found f = DsRecovery.scanXml("res/layout/dialog_styled.xml", xml);
        assertNotNull(f);
        DsSpec.Widget title = null;
        DsSpec.Widget ok = null;
        for (DsSpec.Widget w : f.specWidgets) {
            if ("title".equals(w.id)) title = w;
            if ("ok".equals(w.id)) ok = w;
        }
        assertNotNull(title);
        assertNotNull(ok);
        assertEquals(22f, title.textSizeSp, 0.01f);
        assertEquals(0xFF336699, title.textColor);
        assertEquals(100, title.widthPercent);
        assertEquals(48, ok.heightDp);
        assertEquals(14, ok.btnPaddingDp);
        assertEquals(0xFF112233, ok.btnBg);
    }

    @Test
    public void nativeIndexNormalizesDescriptorsForDexAndJava() {
        assertEquals("String", DsNativeIndex.normalizeType("Ljava/lang/String;"));
        assertEquals("int", DsNativeIndex.normalizeType("I"));
        assertEquals("boolean", DsNativeIndex.normalizeType("Z"));
        assertEquals("void", DsNativeIndex.normalizeType("V"));
        assertEquals("Ljava/lang/String;", DsNativeIndex.typeDescriptor("String"));
        assertEquals("I", DsNativeIndex.typeDescriptor("int"));
        assertEquals("[I", DsNativeIndex.typeDescriptor("int[]"));
        DsNativeIndex index = new DsNativeIndex();
        index.add("com.example.app.Tools", "ping", "String",
                new ArrayList<>(java.util.Collections.singletonList("String")), true, "dex");
        DsSpec.Action a = new DsSpec.Action();
        a.type = DsEvents.A_CALL;
        a.methodClass = "com.example.app.Tools";
        a.methodName = "ping";
        a.methodArg = "hello";
        assertNull(index.validate(a));
        assertTrue(index.javaInvocation(a).contains("ping(\"hello\")"));
    }

    @Test
    public void codegenImplementsDontShowAgainAsARealCheckboxAndGuardsBadCalls() {
        DsSpec s = sample();
        s.dontShowAgain = true;
        DsSpec.Action badCall = new DsSpec.Action();
        badCall.type = DsEvents.A_CALL;
        s.events.get(0).actions.add(badCall);
        String java = DsCodeGen.javaSource(s);
        assertTrue(java.contains("new CheckBox(context)"));
        assertTrue(java.contains("dontShow.isChecked()"));
        assertTrue(java.contains("CALL بلا دالة محددة"));
        assertFalse(java.contains("putBoolean(\"welcome.dont_show\", true).apply();\n        dialog.show()"));
    }

    @Test
    public void recoveryAcceptsCustomNamedDialogLayoutWithDialogSignals() {
        String xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                + "<TextView android:id=\"@+id/title\" android:text=\"عنوان\"/>"
                + "<TextView android:id=\"@+id/message\" android:text=\"رسالة\"/>"
                + "<Button android:id=\"@+id/confirm\" android:text=\"تأكيد\"/>"
                + "</LinearLayout>";
        DsRecovery.Found f = DsRecovery.scanXml("res/layout/custom_prompt.xml", xml);
        assertNotNull(f);
        assertEquals("عنوان", f.title);
        assertEquals(1, f.buttons.size());
    }

    @Test
    public void codegenGuardsAnyActionWithItsOwnCondition() {
        DsSpec s = sample();
        s.events.get(0).actions.get(0).condition = "user==أحمد";
        String java = DsCodeGen.javaSource(s);
        assertTrue(java.contains("if (valueOf(\"user\").equals(\"أحمد\"))"));
        assertTrue(java.contains("Toast.makeText"));
    }

    @Test
    public void autoFixDoesNotDeleteCallsWhenNativeIndexIsEmpty() {
        DsSpec s = sample();
        DsSpec.Action call = new DsSpec.Action();
        call.type = DsEvents.A_CALL;
        call.methodClass = "com.example.Native";
        call.methodName = "save";
        s.events.get(0).actions.add(call);
        DsValidator.fix(s, new DsNativeIndex());
        boolean kept = false;
        for (DsSpec.Action a : s.events.get(0).actions) {
            if (DsEvents.A_CALL.equals(a.type)) kept = true;
        }
        assertTrue("auto-fix must not delete a configured call just because no index exists", kept);
    }

    @Test
    public void autoFixPreservesDisabledEventsAndAutomations() {
        DsSpec s = sample();
        s.events.get(0).enabled = false;
        DsSpec.Automation auto = new DsSpec.Automation();
        auto.trigger = DsAutomation.AFTER_DELAY;
        auto.delayMs = 1000;
        auto.enabled = false;
        s.automations.add(auto);
        DsValidator.fix(s, new DsNativeIndex());
        assertEquals(1, s.events.size());
        assertFalse(s.events.get(0).enabled);
        assertEquals(1, s.automations.size());
        assertFalse(s.automations.get(0).enabled);
    }

    @Test
    public void compareReportsRealWidgetAndVariableDifferences() {
        DsSpec before = sample();
        DsSpec after = before.copy();
        after.widgets.get(0).text = "نص جديد";
        after.widgets.add(widget("mp_extra", "button", "إضافة"));
        after.variables.get(0).value = "سارة";
        String diff = DsMerge.compare(before, after);
        assertTrue(diff.contains("نص العنصر mp_text_0"));
        assertTrue(diff.contains("عنصر جديد: mp_extra"));
        assertTrue(diff.contains("قيمة المتغير user"));
        assertTrue(diff.contains("←"));
    }

    @Test
    public void compareReturnsNoDiffForIdenticalSpecs() {
        DsSpec before = sample();
        assertTrue(DsMerge.compare(before, before.copy()).contains("لا توجد فروق"));
    }

    @Test
    public void previewExecutesRealActionsAndInterpolatesVariables() {
        DsSpec s = sample();
        DsSpec.Event e = new DsSpec.Event();
        e.widgetId = "mp_button_2";
        e.event = DsEvents.CLICK;
        DsSpec.Action setVar = new DsSpec.Action();
        setVar.type = DsEvents.A_SET_VAR;
        setVar.target = "user";
        setVar.value = "ليلى";
        e.actions.add(setVar);
        DsSpec.Action setText = new DsSpec.Action();
        setText.type = DsEvents.A_SET_TEXT;
        setText.target = "mp_text_0";
        setText.value = "أهلاً {{user}}";
        e.actions.add(setText);
        DsSpec.Action toast = new DsSpec.Action();
        toast.type = DsEvents.A_TOAST;
        toast.value = "تم {{user}}";
        e.actions.add(toast);
        s.events.add(e);
        DsPreview.Result r = DsPreview.run(s, e, new HashMap<>());
        assertEquals("ليلى", r.variables.get("user"));
        assertEquals("أهلاً ليلى", s.findWidget("mp_text_0").text);
        assertTrue(r.log.stream().anyMatch(line -> line.contains("TOAST: تم ليلى")));
    }

    @Test
    public void previewHonoursConditionsAndStopAction() {
        DsSpec s = sample();
        DsSpec.Event e = new DsSpec.Event();
        e.widgetId = "mp_button_2";
        e.event = DsEvents.CLICK;
        e.condition = "user == أحمد";
        DsSpec.Action setText = new DsSpec.Action();
        setText.type = DsEvents.A_SET_TEXT;
        setText.target = "mp_text_0";
        setText.value = "مرحبا";
        e.actions.add(setText);
        s.events.add(e);
        DsPreview.Result ok = DsPreview.run(s, e, new HashMap<>());
        assertEquals("مرحبا", s.findWidget("mp_text_0").text);
        e.condition = "user == ليلى";
        s.findWidget("mp_text_0").text = "قديم";
        DsPreview.Result skipped = DsPreview.run(s, e, new HashMap<>());
        assertEquals("قديم", s.findWidget("mp_text_0").text);
        assertTrue(skipped.log.stream().anyMatch(line -> line.contains("لم يتحقق")));

        DsSpec.Event stop = new DsSpec.Event();
        stop.widgetId = "mp_button_2";
        stop.event = DsEvents.LONG_CLICK;
        DsSpec.Action first = new DsSpec.Action();
        first.type = DsEvents.A_TOAST;
        first.value = "قبل";
        DsSpec.Action stopper = new DsSpec.Action();
        stopper.type = DsEvents.A_STOP;
        DsSpec.Action after = new DsSpec.Action();
        after.type = DsEvents.A_TOAST;
        after.value = "بعد";
        stop.actions.add(first);
        stop.actions.add(stopper);
        stop.actions.add(after);
        DsPreview.Result stopped = DsPreview.run(s, stop, new HashMap<>());
        assertTrue(stopped.stopped);
        assertTrue(stopped.log.stream().anyMatch(line -> line.contains("قبل")));
        assertFalse(stopped.log.stream().anyMatch(line -> line.contains("TOAST: بعد")));
    }

    @Test
    public void previewExpressionEvaluatorSupportsComparisons() {
        Map<String, String> vars = new HashMap<>();
        vars.put("count", "5");
        vars.put("name", "أحمد");
        assertTrue(DsPreview.truthy("count > 3", vars));
        assertFalse(DsPreview.truthy("count <= 3", vars));
        assertTrue(DsPreview.truthy("name == أحمد", vars));
        assertTrue(DsPreview.truthy("count > 3 && name == أحمد", vars));
        assertFalse(DsPreview.truthy("count > 9 || name == ليلى", vars));
    }

    @Test
    public void validatorRejectsDanglingVariablesAndDuplicateDeclarations() {
        DsSpec s = sample();
        DsSpec.Variable first = new DsSpec.Variable();
        first.name = "count";
        first.type = "number";
        first.value = "1";
        DsSpec.Variable duplicate = new DsSpec.Variable();
        duplicate.name = "count";
        duplicate.type = "string";
        duplicate.value = "x";
        s.variables.add(first);
        s.variables.add(duplicate);
        DsSpec.Action missing = new DsSpec.Action();
        missing.type = DsEvents.A_SET_VAR;
        missing.target = "missing_var";
        missing.value = "1";
        s.events.get(0).actions.add(missing);
        DsValidator.Report report = DsValidator.validate(s, null);
        assertTrue(report.issues.stream().anyMatch(i -> "variable_duplicate".equals(i.code)));
        assertTrue(report.issues.stream().anyMatch(i -> "action_variable".equals(i.code)));
        DsValidator.fix(s, null);
        long countVars = s.variables.stream().filter(v -> "count".equals(v.name)).count();
        assertEquals(1, countVars);
        assertFalse(s.events.get(0).actions.stream()
                .anyMatch(a -> DsEvents.A_SET_VAR.equals(a.type) && "missing_var".equals(a.target)));
    }
}
