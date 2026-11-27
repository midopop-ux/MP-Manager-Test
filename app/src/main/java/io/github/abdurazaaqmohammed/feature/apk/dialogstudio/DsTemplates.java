package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Design presets. Every preset maps to concrete dialog fields that the injector
 * already understands (background, gradient, stroke, corner radius, text and
 * button colors, animation), so choosing a preset changes the real output.
 */
public final class DsTemplates {

    public static class Template {
        public String id = "";
        public String nameAr = "";
        public String nameEn = "";
        public String category = "modern";
        public String descriptionAr = "";
        public int bg1 = 0xFFFFFFFF;
        public int bg2 = 0x00FFFFFF;
        public boolean gradient = false;
        public int orientation = 0;
        public int borderColor = 0x22000000;
        public float borderWidth = 0f;
        public float corner = 20f;
        public int titleColor = 0xFF1B1B1F;
        public int msgColor = 0xFF44464F;
        public int btnColor = 0xFF0A57D0;
        public int btnBg = 0;
        public int btnBg2 = 0;
        public int btnBorderColor = -1;
        public float btnCorner = 14f;
        public float btnBorderWidth = 0f;
        public boolean lightText = false;
        public boolean animBorder = false;
        public boolean rainbow = false;
        public float widthPercent = 92f;
        public float dim = 0.45f;
        public int padding = 20;
        public float titleSize = 20f;
        public float msgSize = 15f;
        public float btnSize = 15f;
        public boolean glass = false;
        public String layout = "classic";
        public String buttonLayout = "auto";
        public boolean custom = false;
    }

    private static final Map<String, Template> BY_ID = new LinkedHashMap<>();
    private static final List<Template> ALL = new ArrayList<>();
    private static final Map<String, String> LAYOUTS = new LinkedHashMap<>();
    private static final Map<String, String> BUTTONS = new LinkedHashMap<>();

    static {
        add(t("ios_alert", "تنبيه iOS الأصلي", "Native iOS Alert", "apple",
                "خلفية فاتحة وزرّان أزرقان بزوايا كبيرة.", 0xFFF2F2F7, 0x00FFFFFF, false,
                0, 0x22000000, 0f, 28f, 0xFF1C1C1E, 0xFF3C3C43, 0xFF007AFF,
                0x00000000, 0x00000000, -1, 28f, 0f, false, false, false, 86f, 0.42f, 20, 20f, 15f, 16f));
        add(t("liquid_glass", "زجاج سائل", "Liquid Glass", "glass",
                "تدرّج شفاف مع حدّ ضوئي رقيق.", 0xCC1E2433, 0x99DCE9FF, true,
                2, 0x66FFFFFF, 1.5f, 26f, 0xFFFFFFFF, 0xE6EAF2FF, 0xFF8AB4FF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 24f, 1f, true, false, false, 90f, 0.30f, 22, 22f, 15f, 15f));
        add(t("frosted_glass", "زجاج مصنفر", "Frosted Glass", "glass",
                "طبقة بيضاء ضبابية وحدود ناعمة.", 0xE6FFFFFF, 0xB3EAF2FF, true,
                0, 0x66FFFFFF, 1f, 26f, 0xFF17181C, 0xFF3A3F4B, 0xFF1A73E8,
                0xFFFFFFFF, 0xFFEAF1FF, 0x33000000, 22f, 1f, false, false, false, 90f, 0.38f, 22, 21f, 15f, 15f));
        add(t("floating_panel", "لوحة عائمة", "Floating Panel", "modern",
                "لوحة بيضاء مرتفعة بحدّ خفيف.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x14000000, 1f, 20f, 0xFF111318, 0xFF4A4D55, 0xFF3867D6,
                0xFFF2F5FF, 0xFFE4EBFF, 0x22000000, 18f, 1f, false, false, false, 90f, 0.40f, 20, 20f, 15f, 15f));
        add(t("bottom_sheet", "لوحة سفلية", "Bottom Sheet", "sheet",
                "عرض كامل وزوايا علوية كبيرة.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 26f, 0xFF17181C, 0xFF43474E, 0xFF0B57D0,
                0xFFF7F9FF, 0xFFE8EEFF, 0x22000000, 22f, 1f, false, false, false, 100f, 0.50f, 22, 21f, 15f, 15f));
        add(t("action_sheet", "قائمة إجراءات", "Action Sheet", "sheet",
                "قائمة إجراءات فاتحة بأزرار زرقاء.", 0xFFF7F7F9, 0x00FFFFFF, false,
                0, 0x22000000, 0.5f, 18f, 0xFF1C1C1E, 0xFF48484A, 0xFF007AFF,
                0xFFFFFFFF, 0xFFF0F0F5, 0x11000000, 16f, 1f, false, false, false, 94f, 0.40f, 16, 18f, 15f, 15f));
        add(t("interactive_card", "بطاقة تفاعلية", "Interactive Card", "card",
                "بطاقة أنيقة مع زرّ متدرّج.", 0xFFFFFBFE, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 24f, 0xFF1D1B20, 0xFF49454F, 0xFFFFFFFF,
                0xFF6750A4, 0xFF8F7BFF, 0x00000000, 20f, 0f, false, false, false, 92f, 0.45f, 22, 21f, 15f, 15f));
        add(t("animated_dialog", "حوار متحرك", "Animated Dialog", "animated",
                "حدّ متحرك بألوان متعاقبة.", 0xFF16121F, 0x00FFFFFF, false,
                0, 0x66A78BFA, 2f, 24f, 0xFFFFFFFF, 0xFFD9D2FF, 0xFFC4B5FD,
                0x33FFFFFF, 0x22FFFFFF, 0x66A78BFA, 20f, 1f, true, false, false, 90f, 0.50f, 22, 21f, 15f, 15f));
        add(t("premium_notification", "إشعار فاخر", "Premium Notification", "premium",
                "حدّ ذهبي وعنوان فاخر.", 0xF7FFFFFF, 0x00FFFFFF, false,
                0, 0xFFFFC857, 1.5f, 20f, 0xFF6B4E00, 0xFF4A4A4A, 0xFF8A6D1D,
                0xFFFFF7DF, 0xFFFFEFC2, 0x66FFC857, 18f, 1f, false, false, false, 92f, 0.42f, 20, 20f, 15f, 15f));
        add(t("neon_glass", "نيون زجاجي", "Neon Glass", "neon",
                "زجاج داكن مع نيون سماوي.", 0xCC0A0F1E, 0xCC101B33, true,
                2, 0xFF00E5FF, 1.5f, 22f, 0xFF7CF7FF, 0xFFC9F7FF, 0xFF00E5FF,
                0x2200E5FF, 0x1100E5FF, 0xFF00E5FF, 18f, 1f, true, false, false, 90f, 0.55f, 20, 21f, 15f, 15f));
        add(t("midnight_glass", "زجاج منتصف الليل", "Midnight Glass", "glass",
                "أزرق داكن شفاف وحدود باردة.", 0xE6101420, 0xCC1B2438, true,
                0, 0x44A0C4FF, 1f, 24f, 0xFFEAF1FF, 0xFFB9C7E6, 0xFF9CC4FF,
                0x33A0C4FF, 0x11A0C4FF, 0x44A0C4FF, 20f, 1f, false, false, false, 90f, 0.50f, 22, 21f, 15f, 15f));
        add(t("aurora", "الشفق", "Aurora", "gradient",
                "تدرّج أخضر مزرق هادئ.", 0xFF0F2027, 0xFF2C5364, true,
                2, 0x5535E0B0, 1f, 24f, 0xFFE8FFF8, 0xFFC8E8E2, 0xFF5EEAD4,
                0x2235E0B0, 0x1135E0B0, 0x5535E0B0, 20f, 1f, false, false, false, 90f, 0.50f, 22, 21f, 15f, 15f));
        add(t("minimal_light", "بسيط فاتح", "Minimal Light", "minimal",
                "تصميم بسيط بزوايا صغيرة.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x1F000000, 1f, 12f, 0xFF111111, 0xFF555555, 0xFF111111,
                0x00000000, 0x00000000, -1, 10f, 0f, false, false, false, 90f, 0.35f, 18, 19f, 15f, 15f));
        add(t("minimal_dark", "بسيط داكن", "Minimal Dark", "minimal",
                "تصميم داكن بسيط وعالي التباين.", 0xFF1C1C1E, 0x00FFFFFF, false,
                0, 0x33FFFFFF, 1f, 12f, 0xFFFFFFFF, 0xFFC7C7CC, 0xFF64D2FF,
                0x22FFFFFF, 0x11FFFFFF, 0x33FFFFFF, 10f, 1f, false, false, false, 90f, 0.50f, 18, 19f, 15f, 15f));
        add(t("material_you", "Material You", "Material You", "material",
                "ألوان Material 3 الافتراضية.", 0xFFFEF7FF, 0x00FFFFFF, false,
                0, 0x22000000, 0f, 28f, 0xFF1D1B20, 0xFF49454F, 0xFFFFFFFF,
                0xFF6750A4, 0xFF7F67BE, 0x00000000, 24f, 0f, false, false, false, 90f, 0.45f, 24, 21f, 15f, 15f));
        add(t("rounded_pill", "حبّة مستديرة", "Rounded Pill", "modern",
                "زوايا شديدة الاستدارة وأزرار حبّية.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 40f, 0xFF141414, 0xFF5A5A5A, 0xFFFFFFFF,
                0xFF111827, 0xFF374151, 0x00000000, 40f, 0f, false, false, false, 88f, 0.40f, 20, 20f, 15f, 15f));
        add(t("sharp_modern", "حديث حاد", "Sharp Modern", "modern",
                "زوايا حادة وحدّ أحمر.", 0xFF111111, 0x00FFFFFF, false,
                0, 0xFFFF3B30, 1f, 4f, 0xFFFFFFFF, 0xFFCFCFCF, 0xFFFF453A,
                0x22FF3B30, 0x11FF3B30, 0xFFFF3B30, 4f, 1f, false, false, false, 90f, 0.55f, 18, 20f, 15f, 15f));
        add(t("gradient_sunset", "غروب متدرّج", "Gradient Sunset", "gradient",
                "تدرّج برتقالي ووردي.", 0xFFFF512F, 0xFFDD2476, true,
                2, 0x44FFFFFF, 1f, 24f, 0xFFFFFFFF, 0xFFFFE8F0, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 20f, 1f, false, false, false, 90f, 0.45f, 22, 21f, 15f, 15f));
        add(t("ocean_breeze", "نسيم المحيط", "Ocean Breeze", "gradient",
                "تدرّج أزرق منعش.", 0xFF2193B0, 0xFF6DD5ED, true,
                2, 0x44FFFFFF, 1f, 22f, 0xFFFFFFFF, 0xFFEAF9FF, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 18f, 1f, false, false, false, 90f, 0.42f, 20, 21f, 15f, 15f));
        add(t("forest_glow", "توهّج الغابة", "Forest Glow", "gradient",
                "تدرّج أخضر عميق.", 0xFF134E5E, 0xFF71B280, true,
                2, 0x44D9FFE8, 1f, 22f, 0xFFF2FFF7, 0xFFD7F5E3, 0xFFB9FFD9,
                0x22D9FFE8, 0x11D9FFE8, 0x55D9FFE8, 18f, 1f, false, false, false, 90f, 0.42f, 20, 21f, 15f, 15f));
        add(t("monochrome_pro", "أحادي احترافي", "Monochrome Pro", "minimal",
                "أسود ورمادي بحدّ واضح.", 0xFF0D0D0D, 0x00FFFFFF, false,
                0, 0xFF444444, 1f, 8f, 0xFFF5F5F5, 0xFFBDBDBD, 0xFFFFFFFF,
                0xFF2A2A2A, 0xFF3A3A3A, 0xFF666666, 8f, 1f, false, false, false, 90f, 0.55f, 18, 20f, 15f, 15f));
        add(t("translucent_veil", "حجاب شفاف", "Translucent Veil", "glass",
                "طبقة داكنة شفافة وحدود بيضاء.", 0x99000000, 0x66000000, true,
                0, 0x33FFFFFF, 1f, 18f, 0xFFFFFFFF, 0xFFE0E0E0, 0xFFFFFFFF,
                0x22FFFFFF, 0x11FFFFFF, 0x44FFFFFF, 16f, 1f, false, false, false, 90f, 0.30f, 20, 21f, 15f, 15f));
        add(t("cyberpunk", "سايبر بانك", "Cyberpunk", "neon",
                "بنفسجي ووردي نيون مع حدّ متحرك.", 0xFF120024, 0xFF1F0038, true,
                2, 0xFFFF00A0, 2f, 6f, 0xFFFFD6F2, 0xFFE9B8FF, 0xFFFF4FD8,
                0x33FF00A0, 0x22FF00A0, 0xFFFF00A0, 4f, 1f, true, true, true, 90f, 0.55f, 20, 21f, 15f, 15f));
        add(t("paper_card", "بطاقة ورقية", "Paper Card", "card",
                "ورق دافئ وحدود بنية خفيفة.", 0xFFFFF8E7, 0x00FFFFFF, false,
                0, 0x332E1B00, 1f, 10f, 0xFF3E2723, 0xFF5D4037, 0xFF6D4C41,
                0xFFFFF3D6, 0xFFFFE8B8, 0x332E1B00, 8f, 1f, false, false, false, 92f, 0.40f, 20, 20f, 15f, 15f));
        add(t("royal_gold", "ذهبي ملكي", "Royal Gold", "premium",
                "أسود ملكي مع ذهبي.", 0xFF14120B, 0x00FFFFFF, false,
                0, 0xFFFFD166, 1.5f, 14f, 0xFFFFE9A8, 0xFFE8D9A8, 0xFFFFD166,
                0x22FFD166, 0x11FFD166, 0x66FFD166, 10f, 1f, false, false, false, 90f, 0.55f, 20, 21f, 15f, 15f));
        add(t("ios_sheet", "ورقة iOS", "iOS Sheet", "apple",
                "ورقة سفلية بعرض كامل وزرّين عموديين.", 0xFFF2F2F7, 0x00FFFFFF, false,
                0, 0x33000000, 0.5f, 28f, 0xFF1C1C1E, 0xFF3C3C43, 0xFF007AFF,
                0xFFFFFFFF, 0xFFE5E5EA, 0x22000000, 22f, 1f, false, false, false, 100f, 0.50f, 20, 20f, 15f, 16f));
        add(t("ios_action_sheet", "قائمة إجراءات iOS", "iOS Action Sheet", "apple",
                "قائمة خيارات عمودية بأسلوب iOS.", 0xFFF7F7F9, 0x00FFFFFF, false,
                0, 0x22000000, 0.5f, 18f, 0xFF1C1C1E, 0xFF48484A, 0xFF007AFF,
                0xFFFFFFFF, 0xFFF0F0F5, 0x11000000, 16f, 1f, false, false, false, 96f, 0.40f, 14, 18f, 15f, 15f));
        add(t("confirm_warning", "تأكيد تحذيري", "Warning Confirm", "confirm",
                "أيقونة تحذير وزرّان عموديان واضحان.", 0xFFFFFBF2, 0x00FFFFFF, false,
                0, 0xFFFFB300, 1f, 18f, 0xFF3E2A00, 0xFF6B5200, 0xFFFFFFFF,
                0xFFB26A00, 0xFF8A5200, 0x00000000, 14f, 0f, false, false, false, 92f, 0.45f, 22, 21f, 15f, 15f));
        add(t("destructive_alert", "تنبيه حذف", "Destructive Alert", "warning",
                "تنبيه أحمر لإجراء لا يمكن التراجع عنه.", 0xFF2B0B0B, 0x00FFFFFF, false,
                0, 0xFFFF453A, 1.5f, 18f, 0xFFFFE5E2, 0xFFFFB4AB, 0xFFFF453A,
                0x33FF453A, 0x22FF453A, 0xFFFF453A, 14f, 1f, true, false, false, 90f, 0.55f, 22, 21f, 15f, 15f));
        add(t("input_form", "نموذج إدخال", "Input Form", "form",
                "نموذج بحقول متعددة وزر إرسال عريض.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 20f, 0xFF111318, 0xFF5A5F6A, 0xFFFFFFFF,
                0xFF0B57D0, 0xFF0842A0, 0x00000000, 14f, 0f, false, false, false, 94f, 0.45f, 22, 20f, 15f, 15f));
        add(t("multi_select", "اختيار متعدد", "Multi Select", "form",
                "قائمة اختيارات متعددة مع زر تأكيد.", 0xFFFEF7FF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 22f, 0xFF1D1B20, 0xFF49454F, 0xFFFFFFFF,
                0xFF6750A4, 0xFF7F67BE, 0x00000000, 18f, 0f, false, false, false, 92f, 0.45f, 20, 20f, 15f, 15f));
        add(t("search_picker", "بحث واختيار", "Search Picker", "form",
                "حقل بحث وقائمة نتائج قابلة للتصفية.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 24f, 0xFF1B1B1F, 0xFF5A5F6A, 0xFF1A73E8,
                0xFFF1F5FF, 0xFFE3ECFF, 0x22000000, 20f, 1f, false, false, false, 94f, 0.40f, 20, 20f, 15f, 15f));
        add(t("media_card", "بطاقة وسائط", "Media Card", "media",
                "صورة أو أيقونة كبيرة فوق العنوان والأزرار.", 0xFF10131A, 0x00FFFFFF, false,
                0, 0x44FFFFFF, 1f, 26f, 0xFFFFFFFF, 0xFFD8DEE9, 0xFFFFFFFF,
                0xFF2D6CDF, 0xFF1E4FAF, 0x00000000, 22f, 0f, true, false, false, 92f, 0.55f, 20, 22f, 15f, 15f));
        add(t("fullscreen_onboarding", "تهيئة ملء الشاشة", "Fullscreen Onboarding", "fullscreen",
                "صفحات متعددة تملأ الشاشة مع تنقل.", 0xFF07101F, 0xFF123B66, true,
                2, 0x66FFFFFF, 1f, 0f, 0xFFFFFFFF, 0xFFD8E7FF, 0xFFFFFFFF,
                0x334C9AFF, 0x224C9AFF, 0x664C9AFF, 18f, 1f, true, false, false, 100f, 0.65f, 24, 26f, 16f, 16f));
        add(t("dashboard", "لوحة معلومات", "Dashboard", "dashboard",
                "بطاقات إحصاءات وشبكة عناصر.", 0xFFF7F9FC, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 24f, 0xFF0F172A, 0xFF475569, 0xFFFFFFFF,
                0xFF0F766E, 0xFF115E59, 0x00000000, 16f, 0f, false, false, false, 96f, 0.40f, 20, 20f, 15f, 15f));
        add(t("tabs_dialog", "دايلوك بتبويبات", "Tabs Dialog", "tabs",
                "تبويبات علوية وصفحات محتوى.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 22f, 0xFF111827, 0xFF4B5563, 0xFFFFFFFF,
                0xFF2563EB, 0xFF1D4ED8, 0x00000000, 18f, 0f, false, false, false, 94f, 0.45f, 20, 20f, 15f, 15f));
        add(t("notification_banner", "شريط إشعار", "Notification Banner", "notification",
                "شريط علوي عائم بأيقونة ونص.", 0xFF1C1C1E, 0x00FFFFFF, false,
                0, 0x33FFFFFF, 1f, 20f, 0xFFFFFFFF, 0xFFC7C7CC, 0xFF64D2FF,
                0x22FFFFFF, 0x11FFFFFF, 0x33FFFFFF, 16f, 1f, true, false, false, 96f, 0.35f, 16, 17f, 14f, 14f));
        add(t("compact_prompt", "مطالبة مدمجة", "Compact Prompt", "compact",
                "نافذة صغيرة جدًا لسؤال سريع.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 16f, 0xFF111111, 0xFF666666, 0xFFFFFFFF,
                0xFF111827, 0xFF374151, 0x00000000, 14f, 0f, false, false, false, 78f, 0.35f, 16, 18f, 14f, 14f));
        add(t("terminal", "طرفية", "Terminal", "retro",
                "خلفية سوداء وخط أخضر ثابت.", 0xFF000000, 0x00FFFFFF, false,
                0, 0xFF00FF66, 1f, 4f, 0xFF00FF66, 0xFF7CFFB2, 0xFF00FF66,
                0x2200FF66, 0x1100FF66, 0xFF00FF66, 2f, 1f, true, false, false, 92f, 0.60f, 16, 19f, 14f, 14f));
        add(t("magazine", "مجلة", "Magazine", "editorial",
                "عنوان كبير وتخطيط تحريري فاخر.", 0xFFFFFBF5, 0x00FFFFFF, false,
                0, 0x332A2118, 1f, 6f, 0xFF1F1A14, 0xFF6B5B4B, 0xFFB45309,
                0xFFFEF3C7, 0xFFFDE68A, 0x332A2118, 6f, 1f, false, false, false, 94f, 0.35f, 24, 26f, 15f, 15f));
        add(t("permission_rationale", "شرح صلاحية", "Permission Rationale", "system",
                "أيقونة درع وشرح صلاحية وزرّان.", 0xFFF8FAFC, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 22f, 0xFF0F172A, 0xFF475569, 0xFFFFFFFF,
                0xFF1D4ED8, 0xFF1E40AF, 0x00000000, 16f, 0f, false, false, false, 92f, 0.42f, 22, 21f, 15f, 15f));
        add(t("success_check", "نجاح", "Success Check", "system",
                "علامة نجاح خضراء ورسالة تأكيد.", 0xFFF0FDF4, 0x00FFFFFF, false,
                0, 0xFF22C55E, 1f, 24f, 0xFF052E16, 0xFF166534, 0xFFFFFFFF,
                0xFF16A34A, 0xFF15803D, 0x00000000, 18f, 0f, false, false, false, 86f, 0.40f, 20, 21f, 15f, 15f));
        add(t("error_alert", "خطأ", "Error Alert", "system",
                "تنبيه خطأ أحمر مع إجراء إغلاق.", 0xFFFEF2F2, 0x00FFFFFF, false,
                0, 0xFFEF4444, 1f, 22f, 0xFF450A0A, 0xFF7F1D1D, 0xFFFFFFFF,
                0xFFDC2626, 0xFFB91C1C, 0x00000000, 16f, 0f, false, false, false, 88f, 0.45f, 20, 21f, 15f, 15f));

        add(t("glass_tinted", "زجاج ملوّن", "Tinted Glass", "glass",
                "زجاج ملوّن ناعم مع زر متدرّج.", 0xCC14213D, 0x994B6BB8, true,
                2, 0x66A8C7FF, 1.5f, 26f, 0xFFEAF2FF, 0xFFB9C9E8, 0xFFFFFFFF,
                0xFF2563EB, 0xFF7C3AED, 0x00000000, 22f, 1f, false, false, false, 92f, 0.45f, 22, 21f, 15f, 15f));
        add(t("neo_brutal", "نيو بروتالي", "Neo Brutal", "bold",
                "حدود سوداء سميكة وظل حاد.", 0xFFFFFBF0, 0x00FFFFFF, false,
                0, 0xFF000000, 3f, 4f, 0xFF111111, 0xFF333333, 0xFF000000,
                0xFFFFE066, 0xFFFFC933, 0xFF000000, 4f, 1f, false, false, false, 90f, 0.40f, 20, 22f, 15f, 15f));
        add(t("soft_pastel", "باستيل ناعم", "Soft Pastel", "modern",
                "ألوان باستيل وزر وردي.", 0xFFFFF7FB, 0x00FFFFFF, false,
                0, 0x33F472B6, 1f, 28f, 0xFF3B2433, 0xFF6B4A5D, 0xFFFFFFFF,
                0xFFDB2777, 0xFF9D174D, 0x00000000, 24f, 1f, false, false, false, 90f, 0.35f, 22, 21f, 15f, 15f));
        add(t("dark_luxury", "فخامة داكنة", "Dark Luxury", "premium",
                "أسود عميق وذهبي هادئ.", 0xFF0B0B0D, 0x00FFFFFF, false,
                0, 0xFFFFD700, 1f, 20f, 0xFFF5E6B8, 0xFFC9B47A, 0xFFFFD700,
                0xFF1C1C1F, 0xFF2A2A2E, 0x66FFD700, 18f, 1f, false, false, false, 90f, 0.55f, 22, 21f, 15f, 15f));
        add(t("cyber_red", "سايبر أحمر", "Cyber Red", "neon",
                "أسود ونيون أحمر حاد.", 0xFF120306, 0xFF2B0710, true,
                2, 0xFFFF1744, 2f, 8f, 0xFFFFE4E8, 0xFFFFB3BE, 0xFFFF1744,
                0x33FF1744, 0x22FF1744, 0xFFFF1744, 6f, 1f, true, true, false, 90f, 0.55f, 20, 21f, 15f, 15f));
        add(t("minimal_blue", "أزرق بسيط", "Minimal Blue", "minimal",
                "أبيض نظيف ولمسة أزرق.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 16f, 0xFF0F172A, 0xFF64748B, 0xFFFFFFFF,
                0xFF2563EB, 0xFF1D4ED8, 0x00000000, 12f, 1f, false, false, false, 90f, 0.35f, 20, 20f, 15f, 15f));
        add(t("card_stack", "بطاقات متراكبة", "Card Stack", "card",
                "بطاقة رئيسية وطبقات خلفية.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x22000000, 1f, 22f, 0xFF1E293B, 0xFF64748B, 0xFFFFFFFF,
                0xFF0EA5E9, 0xFF0284C7, 0x00000000, 18f, 1f, false, false, false, 92f, 0.45f, 22, 21f, 15f, 15f));
        add(t("glass_emerald", "زجاج زمردي", "Emerald Glass", "glass",
                "زجاج داكن بلمسة زمردية.", 0xCC06281F, 0x9934D399, true,
                2, 0x6634D399, 1f, 24f, 0xFFECFDF5, 0xFFA7F3D0, 0xFF10B981,
                0x2234D399, 0x1134D399, 0x6634D399, 20f, 1f, false, false, false, 90f, 0.50f, 22, 21f, 15f, 15f));

        add(t("ai_console", "وحدة تحكم AI", "AI Console", "neon",
                "داكن مع نيون سماوي وحدود متحركة.", 0xFF07111F, 0xFF10233D, true,
                2, 0xFF22D3EE, 1.5f, 18f, 0xFFE0FBFF, 0xFFA5F3FC, 0xFF22D3EE,
                0x3322D3EE, 0x1122D3EE, 0xFF22D3EE, 14f, 1f, true, true, false, 94f, 0.55f, 20, 21f, 15f, 15f));
        add(t("glass_pill", "حبّة زجاجية", "Glass Pill", "glass",
                "زجاج فاتح وحبّة شديدة الاستدارة.", 0xE6FFFFFF, 0xB3EAF2FF, true,
                0, 0x66FFFFFF, 1f, 36f, 0xFF0F172A, 0xFF475569, 0xFF2563EB,
                0xFFEFF6FF, 0xFFDBEAFE, 0x332563EB, 34f, 1f, false, false, false, 88f, 0.35f, 18, 19f, 15f, 15f));
        add(t("material_tonal", "Material Tonal", "Material Tonal", "material",
                "ألوان tonal هادئة من Material 3.", 0xFFF3EDF7, 0x00FFFFFF, false,
                0, 0x226750A4, 0f, 24f, 0xFF1D1B20, 0xFF49454F, 0xFFFFFFFF,
                0xFF6750A4, 0xFF7F67BE, 0x00000000, 20f, 0f, false, false, false, 92f, 0.40f, 22, 20f, 15f, 15f));
        add(t("ios_glass_sheet", "ورقة iOS زجاجية", "iOS Glass Sheet", "apple",
                "ورقة سفلية شفافة بزوايا iOS.", 0xE6F2F2F7, 0xB3FFFFFF, true,
                0, 0x44FFFFFF, 0.5f, 30f, 0xFF1C1C1E, 0xFF3C3C43, 0xFF007AFF,
                0xFFFFFFFF, 0xFFE5E5EA, 0x22000000, 28f, 1f, false, false, false, 100f, 0.45f, 22, 21f, 15f, 16f));
        add(t("neon_lime", "نيون ليموني", "Neon Lime", "neon",
                "أسود وليموني عالي التباين.", 0xFF0A0F0A, 0xFF152015, true,
                2, 0xFFA3E635, 1.5f, 14f, 0xFFF7FEE7, 0xFFD9F99D, 0xFFA3E635,
                0x22A3E635, 0x11A3E635, 0xFFA3E635, 10f, 1f, true, false, false, 90f, 0.50f, 20, 21f, 15f, 15f));
        add(t("solar_flare", "وهج شمسي", "Solar Flare", "gradient",
                "تدرّج برتقالي ذهبي دافئ.", 0xFFF59E0B, 0xFFEF4444, true,
                2, 0x66FFF7ED, 1f, 26f, 0xFFFFFFFF, 0xFFFFEDD5, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 22f, 1f, false, false, false, 90f, 0.42f, 22, 21f, 15f, 15f));
        add(t("midnight_blue", "أزرق منتصف الليل", "Midnight Blue", "premium",
                "كحلي عميق وزر سماوي.", 0xFF0B1220, 0x00FFFFFF, false,
                0, 0x3322D3EE, 1f, 20f, 0xFFE2E8F0, 0xFF94A3B8, 0xFF22D3EE,
                0xFF1E3A5F, 0xFF27496D, 0x3322D3EE, 14f, 1f, false, false, false, 90f, 0.55f, 22, 21f, 15f, 15f));
        add(t("soft_neu", "نيومورفي ناعم", "Soft Neumorphic", "modern",
                "ظلال ناعمة وحواف فاتحة.", 0xFFEEF2F7, 0x00FFFFFF, false,
                0, 0x22FFFFFF, 1f, 26f, 0xFF334155, 0xFF64748B, 0xFFFFFFFF,
                0xFFE2E8F0, 0xFFCBD5E1, 0x22FFFFFF, 22f, 1f, false, false, false, 90f, 0.35f, 22, 20f, 15f, 15f));
        add(t("holographic", "هولوغرام", "Holographic", "gradient",
                "تدرّج وردي وسماوي لامع.", 0xFF7C3AED, 0xFF06B6D4, true,
                2, 0x66FFFFFF, 1.5f, 24f, 0xFFFFFFFF, 0xFFEDE9FE, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 20f, 1f, true, true, true, 90f, 0.45f, 22, 22f, 15f, 15f));
        add(t("minimal_sage", "مريمي بسيط", "Minimal Sage", "minimal",
                "أبيض هادئ وأخضر مريمي.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x2234D399, 1f, 18f, 0xFF134E4A, 0xFF64748B, 0xFFFFFFFF,
                0xFF059669, 0xFF047857, 0x00000000, 14f, 1f, false, false, false, 90f, 0.35f, 20, 20f, 15f, 15f));

        add(t("clay_soft", "طيني ناعم", "Clay Soft", "modern",
                "أسطح طينية وردية وظلال ناعمة.", 0xFFFDF2F8, 0x00FFFFFF, false,
                0, 0x33F9A8D4, 1f, 24f, 0xFF500724, 0xFF831843, 0xFFFFFFFF,
                0xFFEC4899, 0xFFDB2777, 0x00000000, 18f, 1f, false, false, false, 90f, 0.35f, 22, 21f, 15f, 15f));
        add(t("lavender_mist", "ضباب اللافندر", "Lavender Mist", "glass",
                "بنفسجي هادئ وحدود بيضاء.", 0xE6F5F3FF, 0xCCEDE9FE, true,
                0, 0x66FFFFFF, 1.2f, 26f, 0xFF2E1065, 0xFF5B21B6, 0xFF7C3AED,
                0xFFFFFFFF, 0xFFF3E8FF, 0x22FFFFFF, 22f, 1f, false, false, false, 90f, 0.32f, 22, 21f, 15f, 15f));
        add(t("desert_sand", "رمال الصحراء", "Desert Sand", "premium",
                "رملي دافئ وذهبي مطفي.", 0xFFFAF5EB, 0x00FFFFFF, false,
                0, 0x33C2A36B, 1f, 20f, 0xFF3F2E1E, 0xFF6B5B45, 0xFF8B5E34,
                0xFFEADBC8, 0xFFDCC5A8, 0x33C2A36B, 16f, 1f, false, false, false, 90f, 0.40f, 22, 21f, 15f, 15f));
        add(t("retro_terminal", "طرفية قديمة", "Retro Terminal", "neon",
                "أسود وأخضر طرفية بخط حاد.", 0xFF050805, 0x00FFFFFF, false,
                0, 0xFF22C55E, 1.5f, 4f, 0xFF86EFAC, 0xFF4ADE80, 0xFF22C55E,
                0xFF052E16, 0xFF064E3B, 0xFF22C55E, 4f, 1f, true, false, false, 92f, 0.60f, 18, 19f, 15f, 15f));
        add(t("sunset_glass", "زجاج الغروب", "Sunset Glass", "gradient",
                "برتقالي ووردي شفاف.", 0xD9FF7E5F, 0xD9FEB47B, true,
                2, 0x66FFFFFF, 1.2f, 24f, 0xFFFFFFFF, 0xFFFFF7ED, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 20f, 1f, true, false, false, 90f, 0.42f, 22, 21f, 15f, 15f));
        add(t("bamboo", "خيزران", "Bamboo", "minimal",
                "أخضر طبيعي وخطوط بسيطة.", 0xFFFFFFFF, 0x00FFFFFF, false,
                0, 0x334D7C0F, 1f, 16f, 0xFF1A2E05, 0xFF4D7C0F, 0xFFFFFFFF,
                0xFF65A30D, 0xFF4D7C0F, 0x00000000, 12f, 1f, false, false, false, 90f, 0.35f, 20, 20f, 15f, 15f));
        add(t("silver_metal", "معدن فضي", "Silver Metal", "premium",
                "فضي متدرّج وحدود لامعة.", 0xFFE5E7EB, 0xFF9CA3AF, true,
                1, 0x99FFFFFF, 1.5f, 18f, 0xFF111827, 0xFF374151, 0xFF111827,
                0xFFF3F4F6, 0xFFD1D5DB, 0x99FFFFFF, 14f, 1f, false, false, false, 90f, 0.45f, 20, 21f, 15f, 15f));
        add(t("coral_reef", "شعاب مرجانية", "Coral Reef", "gradient",
                "مرجاني وفيروزي متدرّج.", 0xFFF43F5E, 0xFF06B6D4, true,
                3, 0x66FFFFFF, 1.5f, 26f, 0xFFFFFFFF, 0xFFFFE4E6, 0xFFFFFFFF,
                0x33FFFFFF, 0x22FFFFFF, 0x66FFFFFF, 22f, 1f, true, false, false, 90f, 0.45f, 22, 21f, 15f, 15f));
        add(t("midnight_neon", "نيون منتصف الليل", "Midnight Neon", "neon",
                "كحلي داكن ونيون بنفسجي.", 0xFF0B0618, 0x00FFFFFF, false,
                0, 0xFFA855F7, 2f, 22f, 0xFFF5D0FE, 0xFFD8B4FE, 0xFFC084FC,
                0xFF1E1033, 0xFF2E1065, 0xFFA855F7, 16f, 1f, true, true, false, 90f, 0.55f, 22, 21f, 15f, 15f));

        LAYOUTS.put("ios_alert", "ios_alert");
        LAYOUTS.put("liquid_glass", "liquid_glass");
        LAYOUTS.put("frosted_glass", "frosted_glass");
        LAYOUTS.put("floating_panel", "floating");
        LAYOUTS.put("bottom_sheet", "bottom_sheet");
        LAYOUTS.put("action_sheet", "action_sheet");
        LAYOUTS.put("interactive_card", "media");
        LAYOUTS.put("animated_dialog", "animated");
        LAYOUTS.put("premium_notification", "notification");
        LAYOUTS.put("neon_glass", "liquid_glass");
        LAYOUTS.put("midnight_glass", "frosted_glass");
        LAYOUTS.put("aurora", "media");
        LAYOUTS.put("minimal_light", "minimal");
        LAYOUTS.put("minimal_dark", "minimal");
        LAYOUTS.put("material_you", "classic");
        LAYOUTS.put("rounded_pill", "compact");
        LAYOUTS.put("sharp_modern", "classic");
        LAYOUTS.put("gradient_sunset", "media");
        LAYOUTS.put("ocean_breeze", "media");
        LAYOUTS.put("forest_glow", "media");
        LAYOUTS.put("monochrome_pro", "minimal");
        LAYOUTS.put("translucent_veil", "floating");
        LAYOUTS.put("cyberpunk", "animated");
        LAYOUTS.put("paper_card", "classic");
        LAYOUTS.put("royal_gold", "classic");
        LAYOUTS.put("ios_sheet", "ios_sheet");
        LAYOUTS.put("ios_action_sheet", "action_sheet");
        LAYOUTS.put("confirm_warning", "confirm");
        LAYOUTS.put("destructive_alert", "warning");
        LAYOUTS.put("input_form", "form");
        LAYOUTS.put("multi_select", "multiselect");
        LAYOUTS.put("search_picker", "search");
        LAYOUTS.put("media_card", "media");
        LAYOUTS.put("fullscreen_onboarding", "fullscreen");
        LAYOUTS.put("dashboard", "dashboard");
        LAYOUTS.put("tabs_dialog", "tabs");
        LAYOUTS.put("notification_banner", "notification");
        LAYOUTS.put("compact_prompt", "compact");
        LAYOUTS.put("terminal", "terminal");
        LAYOUTS.put("magazine", "magazine");
        LAYOUTS.put("permission_rationale", "permission");
        LAYOUTS.put("success_check", "success");
        LAYOUTS.put("error_alert", "error");
        LAYOUTS.put("ai_console", "dashboard");
        LAYOUTS.put("glass_pill", "compact");
        LAYOUTS.put("material_tonal", "classic");
        LAYOUTS.put("ios_glass_sheet", "ios_sheet");
        LAYOUTS.put("neon_lime", "animated");
        LAYOUTS.put("solar_flare", "media");
        LAYOUTS.put("midnight_blue", "classic");
        LAYOUTS.put("soft_neu", "floating");
        LAYOUTS.put("holographic", "animated");
        LAYOUTS.put("minimal_sage", "minimal");
        LAYOUTS.put("clay_soft", "classic");
        LAYOUTS.put("neo_brutal", "compact");
        LAYOUTS.put("lavender_mist", "frosted_glass");
        LAYOUTS.put("desert_sand", "classic");
        LAYOUTS.put("retro_terminal", "terminal");
        LAYOUTS.put("sunset_glass", "liquid_glass");
        LAYOUTS.put("bamboo", "minimal");
        LAYOUTS.put("silver_metal", "floating");
        LAYOUTS.put("coral_reef", "media");
        LAYOUTS.put("midnight_neon", "animated");
        BUTTONS.put("ios_alert", "horizontal");
        BUTTONS.put("ios_sheet", "vertical");
        BUTTONS.put("ios_action_sheet", "vertical");
        BUTTONS.put("action_sheet", "vertical");
        BUTTONS.put("confirm_warning", "vertical");
        BUTTONS.put("destructive_alert", "vertical");
        BUTTONS.put("error_alert", "vertical");
        BUTTONS.put("compact_prompt", "vertical");
        BUTTONS.put("ios_glass_sheet", "vertical");
        BUTTONS.put("glass_pill", "horizontal");
        for (Template x : ALL) {
            x.layout = LAYOUTS.containsKey(x.id) ? LAYOUTS.get(x.id) : "classic";
            x.buttonLayout = BUTTONS.containsKey(x.id) ? BUTTONS.get(x.id) : "auto";
        }
    }

    private DsTemplates() {
    }

    private static Template t(String id, String ar, String en, String category, String desc,
                              int bg1, int bg2, boolean gradient, int orientation,
                              int borderColor, float borderWidth, float corner,
                              int titleColor, int msgColor, int btnColor,
                              int btnBg, int btnBg2, int btnBorderColor,
                              float btnCorner, float btnBorderWidth, boolean lightText,
                              boolean animBorder, boolean rainbow, float widthPercent,
                              float dim, int padding, float titleSize, float msgSize, float btnSize) {
        Template x = new Template();
        x.id = id;
        x.nameAr = ar;
        x.nameEn = en;
        x.category = category;
        x.descriptionAr = desc;
        x.bg1 = bg1;
        x.bg2 = bg2;
        x.gradient = gradient;
        x.orientation = orientation;
        x.borderColor = borderColor;
        x.borderWidth = borderWidth;
        x.corner = corner;
        x.titleColor = titleColor;
        x.msgColor = msgColor;
        x.btnColor = btnColor;
        x.btnBg = btnBg;
        x.btnBg2 = btnBg2;
        x.btnBorderColor = btnBorderColor;
        x.btnCorner = btnCorner;
        x.btnBorderWidth = btnBorderWidth;
        x.lightText = lightText;
        x.animBorder = animBorder;
        x.rainbow = rainbow;
        x.widthPercent = widthPercent;
        x.dim = dim;
        x.padding = padding;
        x.titleSize = titleSize;
        x.msgSize = msgSize;
        x.btnSize = btnSize;
        x.glass = "glass".equals(category);
        x.layout = LAYOUTS.containsKey(id) ? LAYOUTS.get(id) : "classic";
        x.buttonLayout = BUTTONS.containsKey(id) ? BUTTONS.get(id) : "auto";
        return x;
    }

    private static void add(Template t) {
        ALL.add(t);
        BY_ID.put(t.id, t);
    }

    public static List<Template> all() {
        return new ArrayList<>(ALL);
    }

    public static boolean has(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    public static Template get(String id) {
        Template t = BY_ID.get(id);
        return t == null ? BY_ID.get("ios_alert") : t;
    }

    public static String[] namesAr() {
        String[] out = new String[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) out[i] = ALL.get(i).nameAr;
        return out;
    }

    public static String idAt(int index) {
        if (index < 0 || index >= ALL.size()) return ALL.get(0).id;
        return ALL.get(index).id;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id.equals(id)) return i;
        }
        return 0;
    }

    /** Applies a preset to a spec while keeping all user content untouched. */
    public static void apply(DsSpec spec, Template t) {
        if (spec == null || t == null) return;
        spec.templateId = t.id;
        spec.templateCategory = t.category;
        spec.layout = DsSpec.empty(t.layout) ? "classic" : t.layout;
        spec.verticalButtons = "vertical".equals(t.buttonLayout);
        spec.titleAlign = "ios_alert".equals(t.layout) || "notification".equals(t.layout)
                || "compact".equals(t.layout) ? 1 : 0;
        spec.bgColor1 = t.bg1;
        spec.bgColor2 = t.bg2;
        spec.gradient = t.gradient;
        spec.orientation = t.orientation;
        spec.borderColor = t.borderColor;
        spec.borderWidthDp = t.borderWidth;
        spec.cornerRadiusDp = t.corner;
        spec.titleColor = t.titleColor;
        spec.msgColor = t.msgColor;
        spec.btnColor = t.btnColor;
        spec.animBorder = t.animBorder;
        spec.rainbowAnim = t.rainbow;
        spec.widthPercent = t.widthPercent;
        spec.dimAmount = t.dim;
        spec.paddingDp = t.padding;
        spec.titleSizeSp = t.titleSize;
        spec.msgSizeSp = t.msgSize;
        spec.buttonSizeSp = t.btnSize;
        seedWidgets(spec, t);
        for (DsSpec.Widget w : spec.widgets) {
            if (w == null) continue;
            if ("button".equals(w.kind)) {
                w.btnBg = t.btnBg;
                w.btnBg2 = t.btnBg2;
                w.btnBorderColor = t.btnBorderColor;
                w.btnCornerRadiusDp = t.btnCorner;
                w.btnBorderWidthDp = t.btnBorderWidth;
                w.btnAnim = t.animBorder;
                w.btnAnimRainbow = t.rainbow;
                w.textColor = t.btnColor;
                w.textSizeSp = t.btnSize;
            } else if (!"image".equals(w.kind)) {
                w.textColor = t.lightText ? 0xFFFFFFFF : t.titleColor;
                if ("text".equals(w.kind)) w.textSizeSp = t.msgSize;
            }
        }
    }


    private static void seedWidgets(DsSpec spec, Template t) {
        if (spec == null || t == null || !spec.widgets.isEmpty()) return;
        if (DsSpec.empty(spec.title)) spec.title = t.nameAr;
        if (DsSpec.empty(spec.message)) spec.message = t.descriptionAr;
        String layout = t.layout == null ? "" : t.layout;
        if ("form".equals(layout) || "input_form".equals(t.id)) {
            DsSpec.Widget input = new DsSpec.Widget();
            input.id = "mp_seed_input";
            input.kind = "input";
            input.hint = "أدخل النص";
            input.leftDp = 12;
            input.topDp = 8;
            input.widthPercent = 92;
            input.textColor = t.titleColor;
            spec.widgets.add(input);
            addSeedButton(spec, t, "mp_btn_cancel", "إلغاء", 64);
            addSeedButton(spec, t, "mp_btn_ok", "إرسال", 116);
        } else if ("action_sheet".equals(layout) || "ios_action_sheet".equals(t.id)) {
            addSeedButton(spec, t, "mp_btn_1", "خيار أول", 8);
            addSeedButton(spec, t, "mp_btn_2", "خيار ثانٍ", 60);
            addSeedButton(spec, t, "mp_btn_cancel", "إلغاء", 112);
        } else if ("dashboard".equals(layout)) {
            DsSpec.Widget progress = new DsSpec.Widget();
            progress.id = "mp_seed_progress";
            progress.kind = "progress";
            progress.text = "62%";
            progress.leftDp = 12;
            progress.topDp = 8;
            progress.widthPercent = 92;
            spec.widgets.add(progress);
            addSeedButton(spec, t, "mp_btn_ok", "متابعة", 64);
        } else if ("wizard".equals(layout) || "fullscreen".equals(layout)) {
            addSeedButton(spec, t, "mp_btn_cancel", "السابق", 8);
            addSeedButton(spec, t, "mp_btn_ok", "التالي", 60);
        } else {
            addSeedButton(spec, t, "mp_btn_cancel", "إلغاء", 8);
            addSeedButton(spec, t, "mp_btn_ok", "موافق", 60);
        }
    }

    private static void addSeedButton(DsSpec spec, Template t, String id, String label, int topDp) {
        DsSpec.Widget w = new DsSpec.Widget();
        w.id = id;
        w.kind = "button";
        w.text = label;
        w.textColor = t.btnColor;
        w.textSizeSp = t.btnSize;
        w.leftDp = 12;
        w.topDp = topDp;
        w.btnAction = "Dismiss";
        w.btnBg = t.btnBg;
        w.btnBg2 = t.btnBg2;
        w.btnBorderColor = t.btnBorderColor;
        w.btnCornerRadiusDp = t.btnCorner;
        w.btnBorderWidthDp = t.btnBorderWidth;
        w.btnAnim = t.animBorder;
        w.btnAnimRainbow = t.rainbow;
        spec.widgets.add(w);
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Template t : ALL) {
            if (!out.contains(t.category)) out.add(t.category);
        }
        return out;
    }

    public static List<Template> byCategory(String category) {
        List<Template> out = new ArrayList<>();
        for (Template t : ALL) {
            if (category == null || category.isEmpty() || category.equals(t.category)) out.add(t);
        }
        return out;
    }

    public static List<Template> search(String query) {
        List<Template> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.US);
        for (Template t : ALL) {
            if (q.isEmpty() || t.id.toLowerCase(java.util.Locale.US).contains(q)
                    || t.nameAr.contains(q) || t.nameEn.toLowerCase(java.util.Locale.US).contains(q)
                    || t.descriptionAr.contains(q) || t.category.contains(q)) {
                out.add(t);
            }
        }
        return out;
    }

    public static void register(Template t) {
        if (t == null || t.id == null || t.id.trim().isEmpty()) return;
        Template old = BY_ID.get(t.id);
        if (old != null) ALL.remove(old);
        BY_ID.put(t.id, t);
        ALL.add(t);
    }

    public static void unregister(String id) {
        Template old = BY_ID.remove(id);
        if (old != null) ALL.remove(old);
    }

    public static Template fromSpec(DsSpec spec, String id, String nameAr) {
        if (spec == null) return null;
        Template t = new Template();
        t.id = id;
        t.nameAr = nameAr;
        t.nameEn = nameAr;
        t.category = "custom";
        t.descriptionAr = "قالب مخصص محفوظ من الاستوديو.";
        t.custom = true;
        t.layout = spec.layout == null ? "classic" : spec.layout;
        t.buttonLayout = spec.verticalButtons ? "vertical" : "horizontal";
        t.bg1 = spec.bgColor1;
        t.bg2 = spec.bgColor2;
        t.gradient = spec.gradient;
        t.orientation = spec.orientation;
        t.borderColor = spec.borderColor;
        t.borderWidth = spec.borderWidthDp;
        t.corner = spec.cornerRadiusDp;
        t.titleColor = spec.titleColor;
        t.msgColor = spec.msgColor;
        t.btnColor = spec.btnColor;
        t.animBorder = spec.animBorder;
        t.rainbow = spec.rainbowAnim;
        t.widthPercent = spec.widthPercent;
        t.dim = spec.dimAmount;
        t.padding = spec.paddingDp;
        t.titleSize = spec.titleSizeSp;
        t.msgSize = spec.msgSizeSp;
        t.btnSize = spec.buttonSizeSp;
        for (DsSpec.Widget w : spec.widgets) {
            if (w != null && "button".equals(w.kind)) {
                t.btnBg = w.btnBg;
                t.btnBg2 = w.btnBg2;
                t.btnBorderColor = w.btnBorderColor;
                t.btnCorner = w.btnCornerRadiusDp;
                t.btnBorderWidth = w.btnBorderWidthDp;
                break;
            }
        }
        return t;
    }

    public static String layoutAr(String layout) {
        if (layout == null) return "كلاسيكي";
        switch (layout) {
            case "ios_alert": return "تنبيه iOS";
            case "ios_sheet": return "ورقة iOS";
            case "bottom_sheet": return "لوحة سفلية";
            case "action_sheet": return "قائمة إجراءات";
            case "floating": return "لوحة عائمة";
            case "notification": return "إشعار";
            case "media": return "وسائط";
            case "form": return "نموذج";
            case "multiselect": return "اختيار متعدد";
            case "search": return "بحث";
            case "fullscreen": return "ملء الشاشة";
            case "dashboard": return "لوحة معلومات";
            case "tabs": return "تبويبات";
            case "compact": return "مدمج";
            case "terminal": return "طرفية";
            case "magazine": return "مجلة";
            case "permission": return "صلاحية";
            case "success": return "نجاح";
            case "error": return "خطأ";
            case "warning": return "تحذير";
            case "confirm": return "تأكيد";
            case "liquid_glass": return "زجاج سائل";
            case "frosted_glass": return "زجاج مصنفر";
            case "animated": return "متحرك";
            case "minimal": return "بسيط";
            default: return "كلاسيكي";
        }
    }

    /** A compact, human readable summary used by the studio preview. */
    public static String summary(DsSpec spec) {
        Template t = get(spec.templateId);
        return t.nameAr + " · " + String.format(java.util.Locale.US,
                "corner %.0fdp · border %.1fdp · %d widgets",
                spec.cornerRadiusDp, spec.borderWidthDp, spec.widgets.size());
    }
}
