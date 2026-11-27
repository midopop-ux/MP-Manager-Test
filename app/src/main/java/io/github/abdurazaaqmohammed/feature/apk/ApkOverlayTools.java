package io.github.abdurazaaqmohammed.feature.apk;

import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.abdurazaaqmohammed.feature.apk.OverlayInjectorUtil;
import io.github.abdurazaaqmohammed.feature.apk.OverlayProfiles;
import io.github.abdurazaaqmohammed.feature.apk.SignWrapper;
import io.github.abdurazaaqmohammed.util.misc.ToastInjectorUtil;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.ui.main.ColorWheelView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/**
 * Toast/dialog overlay injection UI extracted from ApkToolsHandler.
 * Same constructor dependencies; only the three entry points are public.
 */
public class ApkOverlayTools {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;

    public ApkOverlayTools(MainActivity context,
                           DialogUtil dialogUtil,
                           UIHelper uiHelper, boolean pane1) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private ImageView overlayImagePreviewView;
    private String overlayImageBase64;
    private String overlayVideoPath;
    private OverlayForm activeOverlayForm;
    private Runnable activeAdvRender;
    private DialogStudioController dialogStudio;
    private AlertDialog activeOverlayDialog;
    private static OverlayInjectorUtil.AdvWidget styleClipboard;


    private static void pasteStyle(OverlayInjectorUtil.AdvWidget dst,
                                   OverlayInjectorUtil.AdvWidget src) {
        if (dst == null || src == null) return;
        dst.text = src.text;
        dst.textSizeSp = src.textSizeSp;
        dst.textColor = src.textColor;
        dst.fontStyle = src.fontStyle;
        dst.font = src.font;
        dst.fontPath = src.fontPath;
        dst.imageB64 = src.imageB64;
        dst.btnAction = src.btnAction;
        dst.url = src.url;
        dst.btnBg = src.btnBg;
        dst.btnBg2 = src.btnBg2;
        dst.btnCornerRadiusDp = src.btnCornerRadiusDp;
        dst.btnBorderWidthDp = src.btnBorderWidthDp;
        dst.btnBorderColor = src.btnBorderColor;
        dst.btnPaddingDp = src.btnPaddingDp;
        dst.btnAnim = src.btnAnim;
        dst.btnAnimColors = src.btnAnimColors == null ? null
                : new ArrayList<>(src.btnAnimColors);
        dst.btnAnimSpeedMs = src.btnAnimSpeedMs;
        dst.btnAnimRainbow = src.btnAnimRainbow;
    }

    private static boolean isBaseChromeWidget(OverlayInjectorUtil.AdvWidget w) {
        return w != null && ("@title".equals(w.id) || "@message".equals(w.id));
    }

    private static boolean isInsideView(View child, View parent) {
        View v = child;
        while (v != null) {
            if (v == parent) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    private void refreshLayers(MaterialAutoCompleteTextView tv,
                               List<OverlayInjectorUtil.AdvWidget> widgets,
                               OverlayInjectorUtil.AdvWidget selected) {
        if (tv == null) return;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < widgets.size(); i++) {
            labels.add(layerLabel(widgets.get(i), i));
        }
        tv.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, labels));
        int index = selected == null ? -1 : widgets.indexOf(selected);
        tv.setText(index >= 0 ? labels.get(index) : "", false);
    }

    private String layerLabel(OverlayInjectorUtil.AdvWidget w, int index) {
        if (w == null) return (index + 1) + ". عنصر";
        String kind = w.kind == null ? "text" : w.kind;
        String name;
        switch (kind) {
            case "title": name = "عنوان"; break;
            case "message": name = "رسالة"; break;
            case "button": name = "زر"; break;
            case "input": name = "إدخال"; break;
            case "checkbox": name = "اختيار"; break;
            case "toggle": name = "تبديل"; break;
            case "spinner": name = "قائمة"; break;
            case "progress": name = "تقدم"; break;
            case "divider": name = "فاصل"; break;
            case "card": name = "بطاقة"; break;
            case "image": name = "صورة"; break;
            case "icon": name = "أيقونة"; break;
            default: name = "نص"; break;
        }
        String text = w.text == null ? "" : w.text.trim();
        if (text.length() > 18) text = text.substring(0, 18) + "…";
        return (index + 1) + ". " + name + (text.isEmpty() ? "" : ": " + text);
    }

    private void applyDialogTheme(OverlayForm f, int index,
                                  List<OverlayInjectorUtil.AdvWidget> widgets) {
        if (f == null) return;
        int bg1;
        int bg2;
        int border;
        int title;
        int radius;
        int borderWidth;
        boolean bgEnabled = true;
        int okBg;
        int okBg2;
        int okText;
        int cancelBg;
        int cancelBg2;
        int cancelText;
        switch (index) {
            case 1:
                bg1 = 0xF7FFFFFF; bg2 = 0xF7EEF2FF; border = 0x22000000; title = 0xFF1B1B1F;
                radius = 24; borderWidth = 1;
                okBg = 0xFF3B6CF6; okBg2 = 0xFF2F5BEA; okText = 0xFFFFFFFF;
                cancelBg = 0xFFE9EAF2; cancelBg2 = 0xFFE1E3EE; cancelText = 0xFF1B1B1F;
                break;
            case 2:
                bg1 = 0xF21A1A1E; bg2 = 0xF22A2A32; border = 0x33FFFFFF; title = 0xFFFFFFFF;
                radius = 22; borderWidth = 1;
                okBg = 0xFF6C8CFF; okBg2 = 0xFF4D6EF5; okText = 0xFFFFFFFF;
                cancelBg = 0x33FFFFFF; cancelBg2 = 0x22FFFFFF; cancelText = 0xFFFFFFFF;
                break;
            case 3:
                bg1 = 0xF20B1020; bg2 = 0xF21A0B2E; border = 0xFF00E5FF; title = 0xFF00E5FF;
                radius = 20; borderWidth = 2;
                okBg = 0xFF00E5FF; okBg2 = 0xFF00B8D4; okText = 0xFF001018;
                cancelBg = 0x2200E5FF; cancelBg2 = 0x1100E5FF; cancelText = 0xFF00E5FF;
                break;
            case 4:
                bg1 = 0; bg2 = 0; border = 0; title = 0;
                radius = 0; borderWidth = 0; bgEnabled = false;
                okBg = 0xFF3B6CF6; okBg2 = 0xFF2F5BEA; okText = 0xFFFFFFFF;
                cancelBg = 0xFFF1F2F7; cancelBg2 = 0xFFE9EAF2; cancelText = 0xFF1B1B1F;
                break;
            default:
                bg1 = 0xCC0B1220; bg2 = 0xCC1B2A4A; border = 0x66FFFFFF; title = 0xFFFFFFFF;
                radius = 26; borderWidth = 1;
                okBg = 0xFF3B6CF6; okBg2 = 0xFF2F5BEA; okText = 0xFFFFFFFF;
                cancelBg = 0x33FFFFFF; cancelBg2 = 0x22FFFFFF; cancelText = 0xFFFFFFFF;
                break;
        }
        f.bgSwitch.setChecked(bgEnabled);
        f.bgC1 = bg1;
        f.bgC2 = bg2;
        f.bgC2set = bgEnabled && bg2 != 0;
        f.borderC = border;
        f.titleC = title;
        f.radiusDp = radius;
        f.borderDp = borderWidth;
        setText(f.radiusInput, String.valueOf(radius));
        setText(f.borderWidthInput, String.valueOf(borderWidth));
        if (f.orientTv != null) f.orientTv.setText("Top to bottom", false);
        if (f.titleColorHex != null) {
            f.titleColorHex.setText(title == 0 ? "" : String.format("#%08X", title));
        }
        if (widgets == null) widgets = new ArrayList<>();
        for (OverlayInjectorUtil.AdvWidget w : widgets) {
            if (w == null) continue;
            if ("title".equals(w.kind) || "message".equals(w.kind)) {
                w.textColor = title;
            } else if ("button".equals(w.kind) && w.id != null) {
                if ("@btn_ok".equals(w.id)) {
                    w.btnBg = okBg;
                    w.btnBg2 = okBg2;
                    w.textColor = okText;
                    w.btnCornerRadiusDp = radius;
                } else if ("@btn_cancel".equals(w.id)) {
                    w.btnBg = cancelBg;
                    w.btnBg2 = cancelBg2;
                    w.textColor = cancelText;
                    w.btnCornerRadiusDp = radius;
                }
            }
        }
        refreshStyleLabels(f);
    }
    private BitmapDrawable buildGridDrawable() {
        int step = Math.max(4, dp(8));
        Bitmap bmp = Bitmap.createBitmap(step, step, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x223B6CF6);
        paint.setStrokeWidth(1f);
        canvas.drawLine(0, 0, step, 0, paint);
        canvas.drawLine(0, 0, 0, step, paint);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bmp);
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return drawable;
    }

    private void ensureChromeWidgets(List<OverlayInjectorUtil.AdvWidget> widgets, OverlayForm form) {
        boolean wasEmpty = widgets.isEmpty();
        OverlayInjectorUtil.AdvWidget title = null;
        OverlayInjectorUtil.AdvWidget message = null;
        for (OverlayInjectorUtil.AdvWidget w : widgets) {
            if (w == null) continue;
            if (title == null && "@title".equals(w.id)) title = w;
            else if (message == null && "@message".equals(w.id)) message = w;
        }
        String titleText = textOf(form == null ? null : form.titleInput);
        String messageText = textOf(form == null ? null : form.msgInput);
        String dialogFont = form == null || form.dlgFont == null ? "" : form.dlgFont;
        String dialogFontPath = form == null || form.dlgFontPath == null ? "" : form.dlgFontPath;
        if (title == null) {
            title = new OverlayInjectorUtil.AdvWidget();
            title.id = "@title";
            title.kind = "title";
            title.leftDp = 4;
            title.topDp = 4;
            title.widthPercent = 100;
            title.textSizeSp = 20f;
            title.fontStyle = 1;
            title.font = dialogFont;
            title.fontPath = dialogFontPath;
            widgets.add(title);
        }
        if (message == null) {
            message = new OverlayInjectorUtil.AdvWidget();
            message.id = "@message";
            message.kind = "message";
            message.leftDp = 4;
            message.topDp = 40;
            message.widthPercent = 100;
            message.textSizeSp = 15f;
            message.font = dialogFont;
            message.fontPath = dialogFontPath;
            widgets.add(message);
        }
        if (form != null && form.titleC != 0) title.textColor = form.titleC;
        title.text = titleText.isEmpty() ? "عنوان الحوار" : titleText;
        message.text = messageText.isEmpty() ? "نص الرسالة" : messageText;
        if (title.font == null || title.font.isEmpty()) title.font = dialogFont;
        if (message.font == null || message.font.isEmpty()) message.font = dialogFont;
        if (title.fontPath == null || title.fontPath.isEmpty()) title.fontPath = dialogFontPath;
        if (message.fontPath == null || message.fontPath.isEmpty()) message.fontPath = dialogFontPath;
        if (!wasEmpty) return;
        OverlayInjectorUtil.AdvWidget ok = new OverlayInjectorUtil.AdvWidget();
        ok.id = "@btn_ok";
        ok.kind = "button";
        ok.text = "موافق";
        ok.textSizeSp = 14f;
        ok.textColor = 0xFFFFFFFF;
        ok.btnBg = 0xFF3B6CF6;
        ok.btnBg2 = 0xFF2F5BEA;
        ok.btnCornerRadiusDp = 22f;
        ok.btnPaddingDp = 12;
        ok.btnAction = "Dismiss";
        ok.leftDp = 8;
        ok.topDp = 150;
        ok.widthDp = 120;
        ok.heightDp = 44;
        ok.font = dialogFont;
        ok.fontPath = dialogFontPath;
        widgets.add(ok);
        OverlayInjectorUtil.AdvWidget cancel = new OverlayInjectorUtil.AdvWidget();
        cancel.id = "@btn_cancel";
        cancel.kind = "button";
        cancel.text = "حسنًا";
        cancel.textSizeSp = 14f;
        cancel.textColor = 0xFF1B1B1F;
        cancel.btnBg = 0xFFF1F2F7;
        cancel.btnCornerRadiusDp = 22f;
        cancel.btnPaddingDp = 12;
        cancel.btnAction = "Dismiss";
        cancel.leftDp = 140;
        cancel.topDp = 150;
        cancel.widthDp = 120;
        cancel.heightDp = 44;
        cancel.font = dialogFont;
        cancel.fontPath = dialogFontPath;
        widgets.add(cancel);
    }

    private void renderAdvSafe() {
        if (activeAdvRender != null) activeAdvRender.run();
    }

    private View previewWidget(OverlayInjectorUtil.AdvWidget w, boolean richText, OverlayForm form) {
        String kind = w.sourceKind == null || w.sourceKind.isEmpty()
                ? (w.kind == null ? "text" : w.kind) : w.sourceKind;
        switch (kind) {
            case "title":
            case "message": {
                TextView tv = new TextView(context);
                boolean isTitle = "title".equals(kind);
                tv.setText(displayText(w.text == null ? "" : w.text, richText));
                tv.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : (isTitle ? 20f : 15f));
                int themeText = MaterialColors.getColor(context, isTitle
                        ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOnSurfaceVariant,
                        isTitle ? 0xFF1B1B1F : 0xFF44464F);
                tv.setTextColor(w.textColor != 0 ? w.textColor : themeText);
                if (isTitle) tv.setTypeface(null,
                        (w.fontStyle & 1) != 0 ? Typeface.BOLD : Typeface.NORMAL);
                tv.setLineSpacing(0f, 1.12f);
                applyWidgetTypeface(tv, w, form);
                return tv;
            }
            case "icon": {
                TextView icon = new TextView(context);
                icon.setText(w.text == null || w.text.isEmpty() ? "★" : w.text);
                icon.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 28f);
                icon.setTextColor(w.textColor == 0 ? 0xFF6750A4 : w.textColor);
                icon.setGravity(Gravity.CENTER);
                icon.setPadding(dp(6), dp(4), dp(6), dp(4));
                return icon;
            }
            case "image": {
                ImageView iv = new ImageView(context);
                iv.setAdjustViewBounds(true);
                if (w.imageB64 != null && !w.imageB64.isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(w.imageB64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bitmap != null) iv.setImageBitmap(bitmap);
                    } catch (Exception ignored) {
                    }
                } else {
                    iv.setBackgroundColor(0xFF9E9E9E);
                }
                return iv;
            }
            case "button":
            case "link": {
                MaterialButton btn = new MaterialButton(context);
                String label = DsEmpty(w.text) ? ("link".equals(kind) ? "Open link" : context.rss.getString(R.string.button)) : w.text;
                btn.setText(displayText(label, richText));
                btn.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                if (w.textColor != 0) btn.setTextColor(w.textColor);
                applyWidgetTypeface(btn, w, form);
                btn.setBackground(buildButtonDrawable(w));
                return btn;
            }
            case "input":
            case "textarea":
            case "number": {
                EditText input = new EditText(context);
                input.setText(displayText(w.text == null ? "" : w.text, false));
                input.setHint(w.hint == null ? "" : w.hint);
                input.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                if (w.textColor != 0) input.setTextColor(w.textColor);
                input.setInputType("number".equals(kind)
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        : "textarea".equals(kind)
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        : InputType.TYPE_CLASS_TEXT);
                input.setFocusable(false);
                input.setClickable(false);
                input.setLongClickable(false);
                input.setCursorVisible(false);
                input.setPadding(dp(8), dp(6), dp(8), dp(6));
                applyWidgetTypeface(input, w, form);
                return input;
            }
            case "checkbox": {
                CheckBox check = new CheckBox(context);
                check.setText(displayText(w.text == null ? "" : w.text, false));
                check.setChecked(w.checked);
                check.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                if (w.textColor != 0) check.setTextColor(w.textColor);
                check.setFocusable(false);
                check.setClickable(false);
                check.setLongClickable(false);
                applyWidgetTypeface(check, w, form);
                return check;
            }
            case "toggle": {
                MaterialSwitch toggle = new MaterialSwitch(context);
                toggle.setText(displayText(w.text == null ? "" : w.text, false));
                toggle.setChecked(w.checked);
                toggle.setFocusable(false);
                toggle.setClickable(false);
                toggle.setLongClickable(false);
                toggle.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                if (w.textColor != 0) toggle.setTextColor(w.textColor);
                applyWidgetTypeface(toggle, w, form);
                return toggle;
            }
            case "spinner":
            case "list":
            case "radio":
            case "multiselect": {
                Spinner spinner = new Spinner(context);
                List<String> options = w.options == null || w.options.isEmpty()
                        ? Arrays.asList("Option 1", "Option 2", "Option 3") : w.options;
                spinner.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_dropdown_item, options));
                if (w.checked && !options.isEmpty()) spinner.setSelection(0);
                spinner.setFocusable(false);
                spinner.setClickable(false);
                spinner.setLongClickable(false);
                return spinner;
            }
            case "search": {
                AutoCompleteTextView search = new AutoCompleteTextView(context);
                search.setHint(w.hint == null ? "Search" : w.hint);
                List<String> options = w.options == null ? new ArrayList<>() : w.options;
                search.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_dropdown_item_1line, options));
                search.setFocusable(false);
                search.setClickable(false);
                search.setLongClickable(false);
                search.setCursorVisible(false);
                return search;
            }
            case "rating": {
                RatingBar rating = new RatingBar(context);
                rating.setNumStars(5);
                rating.setStepSize(1);
                rating.setRating(4);
                rating.setFocusable(false);
                rating.setClickable(false);
                return rating;
            }
            case "progress": {
                ProgressBar progress = new ProgressBar(context);
                progress.setIndeterminate(false);
                progress.setMax(100);
                progress.setProgress(64);
                return progress;
            }
            case "divider": {
                View line = new View(context);
                line.setBackgroundColor(0x33000000);
                line.setMinimumHeight(dp(1));
                return line;
            }
            case "card": {
                MaterialCardView card = new MaterialCardView(context);
                card.setCardElevation(dp(2));
                card.setRadius(dp(18));
                card.setCardBackgroundColor(0x1F6750A4);
                card.setStrokeWidth(dp(1));
                card.setStrokeColor(0x336750A4);
                TextView content = new TextView(context);
                content.setText(displayText(w.text == null ? "Card" : w.text, richText));
                content.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                if (w.textColor != 0) content.setTextColor(w.textColor);
                content.setPadding(dp(12), dp(10), dp(12), dp(10));
                applyWidgetTypeface(content, w, form);
                card.addView(content);
                return card;
            }
            case "tabs":
            case "toolbar": {
                LinearLayout bar = new LinearLayout(context);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setPadding(dp(8), dp(4), dp(8), dp(4));
                List<String> labels = w.options == null || w.options.isEmpty()
                        ? Arrays.asList("Tab 1", "Tab 2", "Tab 3") : w.options;
                for (int i = 0; i < Math.min(3, labels.size()); i++) {
                    TextView tab = new TextView(context);
                    tab.setText(labels.get(i));
                    tab.setTextSize(12);
                    tab.setPadding(dp(8), dp(4), dp(8), dp(4));
                    if (i == 0) tab.setTypeface(null, Typeface.BOLD);
                    bar.addView(tab);
                }
                return bar;
            }
            case "pager": {
                TextView pager = new TextView(context);
                pager.setText("‹   1 / 3   ›");
                pager.setGravity(Gravity.CENTER);
                pager.setTextSize(13);
                pager.setPadding(dp(8), dp(6), dp(8), dp(6));
                return pager;
            }
            default: {
                TextView tv = new TextView(context);
                tv.setText(displayText(w.text == null || w.text.isEmpty() ? "Text" : w.text, richText));
                tv.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 16f);
                if (w.textColor != 0) tv.setTextColor(w.textColor);
                applyWidgetTypeface(tv, w, form);
                return tv;
            }
        }
    }

    private static boolean DsEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private interface ImagePicked {
        void onImage(Bitmap thumb, String base64);
    }

    private ImagePicked pendingImagePick;

    private void pickImage(ImagePicked cb) {
        pendingImagePick = cb;
        try {
            MainActivity.overlayImageCallback = this::loadOverlayImageV2;
            context.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 9021);
        } catch (Exception e) {
            pendingImagePick = null;
            Extensions.showMessage(context, context.getString(R.string.image_picker_unavailable));
        }
    }

    private void loadOverlayImageV2(Uri uri) {
        ImagePicked cb = pendingImagePick;
        pendingImagePick = null;
        decodePickedImage(uri, (thumb, b64) -> {
            if (cb != null) cb.onImage(thumb, b64);
        });
    }

    private void decodePickedImage(Uri uri, ImagePicked cb) {
        AppExecutors.io().execute(() -> {
            try {
                Bitmap bitmap = decodeSampledBitmap(uri, 512);
                if (bitmap == null) throw new IOException("Cannot decode image");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] bytes = baos.toByteArray();
                if (bytes.length > 200 * 1024) {
                    baos.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    bytes = baos.toByteArray();
                }
                final String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                final Bitmap thumb = bitmap;
                final int kb = bytes.length / 1024;
                context.handler.post(() -> {
                    cb.onImage(thumb, b64);
                    Extensions.showMessage(context, "Image attached (" + kb + " KB)");
                });
            } catch (Exception e) {
                context.handler.post(() -> Extensions.showMessage(context, "Image failed: " + e.getMessage()));
            }
        });
    }
    private void loadOverlayImage(Uri uri) {
        decodePickedImage(uri, (thumb, b64) -> {
            overlayImageBase64 = b64;
            if (overlayImagePreviewView != null) {
                overlayImagePreviewView.setImageBitmap(thumb);
                overlayImagePreviewView.setVisibility(View.VISIBLE);
            }
            if (activeOverlayForm != null) updatePreviewImage(activeOverlayForm);
        });
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxSize) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    public void showAddToastDialog(File file, String filePath) {
        List<ActivityInfo> activities = ToastInjectorUtil.getActivities(context, filePath);
        if (activities.isEmpty()) {
            Extensions.showMessage(context, "No activities found in this APK");
            return;
        }
        PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(filePath, 0);
        String packageName = packageInfo != null ? packageInfo.packageName : "";
        String mainActivity = ToastInjectorUtil.findMainLauncherActivity(context, filePath, packageName);

        CharSequence[] displayItems = new CharSequence[activities.size()];
        boolean[] checked = new boolean[activities.size()];
        final Set<Integer> selectedIndices = new HashSet<>();
        if(!TextUtils.isEmpty(mainActivity)) for (int i = 0; i < activities.size(); i++) {
            String name = activities.get(i).name;
            boolean isMain = name.equals(mainActivity);
            displayItems[i] = isMain ? name + " (Main)" : name;
            checked[i] = isMain;
            if (isMain) selectedIndices.add(i);
        }

        dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.select_activity))
                .setMultiChoiceItems(displayItems, checked, (dialog, which, isChecked) -> {
                    if (isChecked) selectedIndices.add(which);
                    else selectedIndices.remove(which);
                })
                .setPositiveButton(context.getString(R.string.next_button), (dialog, which) -> {
                    if (selectedIndices.isEmpty()) return;
                    List<String> selectedActivities = new ArrayList<>();
                    for (int idx : selectedIndices) selectedActivities.add(activities.get(idx).name);
                    showToastMessageDialog(file, selectedActivities);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class OverlayForm {
        MaterialAutoCompleteTextView modeTv;
        MaterialAutoCompleteTextView durationTv;
        MaterialAutoCompleteTextView gravityTv;
        MaterialAutoCompleteTextView posAction;
        MaterialAutoCompleteTextView negAction;
        MaterialAutoCompleteTextView neuAction;
        MaterialAutoCompleteTextView profileTv;
        TextInputEditText messageInput;
        TextInputEditText xInput;
        TextInputEditText yInput;
        TextInputEditText titleInput;
        TextInputEditText msgInput;
        TextInputEditText posInput;
        TextInputEditText posUrl;
        TextInputEditText negInput;
        TextInputEditText negUrl;
        TextInputEditText neuInput;
        TextInputEditText neuUrl;
        MaterialSwitch toastHtml;
        MaterialSwitch toastB64;
        MaterialSwitch cancelSwitch;
        MaterialAutoCompleteTextView showModeTv;
        MaterialSwitch movableSwitch;
        MaterialAutoCompleteTextView mediaTv;
        LinearLayout mediaBox;
        MaterialButton pickMediaBtn;
        TextView videoSummaryTv;
        TextView videoWarnTv;
        MaterialSwitch dlgHtml;
        MaterialSwitch dlgB64;
        LinearLayout toastSection;
        LinearLayout dialogSection;
        LinearLayout posBox;
        LinearLayout negBox;
        LinearLayout neuBox;
        TextView toastPreview;
        TextView dlgPreviewTitle;
        TextView dlgPreviewMsg;
        ImageView dlgPreviewImage;
        TextView dlgPreviewVideoTv;
        LinearLayout dlgPreviewBox;
        MaterialCardView phoneCard;
        MaterialSwitch bgSwitch;
        MaterialSwitch animSwitch;
        MaterialButton bg1Btn;
        MaterialButton bg2Btn;
        MaterialButton borderColorBtn;
        LinearLayout animColorsRow;
        final ArrayList<Integer> animColors = new ArrayList<>(
                Arrays.asList(-16776961, -65536));
        Runnable renderAnimChips;
        MaterialButton titleColorBtn;
        TextInputEditText titleColorHex;
        MaterialSwitch rainbowSwitch;
        View animBox;
        MaterialButton dlgFontBtn;
        MaterialButton advFontBtn;
        MaterialButton advFontApplyAllBtn;
        MaterialSwitch advSnapSwitch;
        String dlgFont = "";
        String dlgFontPath = "";
        String advFont = "";
        String advFontPath = "";
        MaterialAutoCompleteTextView orientTv;
        TextInputEditText radiusInput;
        TextInputEditText borderWidthInput;
        TextInputEditText speedInput;
        int bgC1 = -1;
        int bgC2;
        boolean bgC2set;
        int orient;
        float radiusDp = 16f;
        float borderDp;
        int borderC = -1;
        boolean animOn;
        int animMs = 500;
        int titleC;
    }

    private OverlayForm fillForm(View view) {
        OverlayForm f = new OverlayForm();
        f.modeTv = view.findViewById(R.id.overlayModeTv);
        f.durationTv = view.findViewById(R.id.overlayToastDuration);
        f.gravityTv = view.findViewById(R.id.overlayToastGravity);
        f.posAction = view.findViewById(R.id.overlayDlgPosAction);
        f.negAction = view.findViewById(R.id.overlayDlgNegAction);
        f.neuAction = view.findViewById(R.id.overlayDlgNeuAction);
        f.profileTv = view.findViewById(R.id.overlayProfileTv);
        f.messageInput = view.findViewById(R.id.overlayToastMessage);
        f.xInput = view.findViewById(R.id.overlayToastX);
        f.yInput = view.findViewById(R.id.overlayToastY);
        f.titleInput = view.findViewById(R.id.overlayDlgTitle);
        f.msgInput = view.findViewById(R.id.overlayDlgMessage);
        f.posInput = view.findViewById(R.id.overlayDlgPos);
        f.posUrl = view.findViewById(R.id.overlayDlgPosUrl);
        f.negInput = view.findViewById(R.id.overlayDlgNeg);
        f.negUrl = view.findViewById(R.id.overlayDlgNegUrl);
        f.neuInput = view.findViewById(R.id.overlayDlgNeu);
        f.neuUrl = view.findViewById(R.id.overlayDlgNeuUrl);
        f.toastHtml = view.findViewById(R.id.overlayToastHtml);
        f.toastB64 = view.findViewById(R.id.overlayToastB64);
        f.cancelSwitch = view.findViewById(R.id.overlayDlgCancelable);
        f.showModeTv = view.findViewById(R.id.overlayDlgShowModeTv);
        f.movableSwitch = view.findViewById(R.id.overlayDlgMovable);
        f.mediaTv = view.findViewById(R.id.overlayDlgMediaTv);
        f.mediaBox = view.findViewById(R.id.overlayDlgMediaBox);
        f.pickMediaBtn = view.findViewById(R.id.overlayDlgPickMediaBtn);
        f.videoSummaryTv = view.findViewById(R.id.overlayDlgVideoSummary);
        f.videoWarnTv = view.findViewById(R.id.overlayDlgVideoWarn);
        f.dlgHtml = view.findViewById(R.id.overlayDlgHtml);
        f.dlgB64 = view.findViewById(R.id.overlayDlgB64);
        f.toastSection = view.findViewById(R.id.overlayToastSection);
        f.dialogSection = view.findViewById(R.id.overlayDialogSection);
        f.posBox = view.findViewById(R.id.overlayPosBox);
        f.negBox = view.findViewById(R.id.overlayNegBox);
        f.neuBox = view.findViewById(R.id.overlayNeuBox);
        f.toastPreview = view.findViewById(R.id.overlayToastPreview);
        f.dlgPreviewTitle = view.findViewById(R.id.overlayDlgPreviewTitle);
        f.dlgPreviewMsg = view.findViewById(R.id.overlayDlgPreviewMsg);
        f.dlgPreviewImage = view.findViewById(R.id.overlayDlgPreviewImage);
        f.dlgPreviewVideoTv = view.findViewById(R.id.overlayDlgPreviewVideoTv);
        f.dlgPreviewBox = view.findViewById(R.id.overlayDlgPreviewBox);
        f.phoneCard = view.findViewById(R.id.overlayPhoneDialogCard);
        f.bgSwitch = view.findViewById(R.id.overlayBgSwitch);
        f.animSwitch = view.findViewById(R.id.overlayAnimSwitch);
        f.bg1Btn = view.findViewById(R.id.overlayBg1Btn);
        f.bg2Btn = view.findViewById(R.id.overlayBg2Btn);
        f.borderColorBtn = view.findViewById(R.id.overlayBorderColorBtn);
        f.animColorsRow = view.findViewById(R.id.overlayAnimColorsRow);
        f.titleColorBtn = view.findViewById(R.id.overlayTitleColorBtn);
        f.titleColorHex = view.findViewById(R.id.overlayTitleColorHex);
        f.rainbowSwitch = view.findViewById(R.id.overlayRainbowSwitch);
        f.animBox = view.findViewById(R.id.overlayAnimBox);
        f.dlgFontBtn = view.findViewById(R.id.overlayDlgFontBtn);
        f.advFontBtn = view.findViewById(R.id.advFontBtn);
        f.advFontApplyAllBtn = view.findViewById(R.id.advFontApplyAllBtn);
        f.advSnapSwitch = view.findViewById(R.id.advSnapSwitch);
        f.orientTv = view.findViewById(R.id.overlayOrientTv);
        f.radiusInput = view.findViewById(R.id.overlayRadiusInput);
        f.borderWidthInput = view.findViewById(R.id.overlayBorderWidthInput);
        f.speedInput = view.findViewById(R.id.overlaySpeedInput);
        return f;
    }

    private static String textOf(EditText e) {
        return e != null && e.getText() != null ? e.getText().toString() : "";
    }

    private OverlayInjectorUtil.ToastOptions collectToast(OverlayForm f) {
        OverlayInjectorUtil.ToastOptions toast = new OverlayInjectorUtil.ToastOptions();
        toast.message = textOf(f.messageInput);
        toast.longDuration = f.durationTv.getText() == null || f.durationTv.getText().toString().equals("Long");
        String gravity = f.gravityTv.getText() == null ? "Default" : f.gravityTv.getText().toString();
        switch (gravity) {
            case "Bottom" -> toast.gravity = 80;
            case "Center" -> toast.gravity = 17;
            case "Top" -> toast.gravity = 48;
        }
        toast.xOffset = clampOffset(textOf(f.xInput));
        toast.yOffset = clampOffset(textOf(f.yInput));
        toast.html = f.toastHtml.isChecked();
        toast.base64 = f.toastB64.isChecked();
        if (toast.message.isEmpty()) {
            Extensions.showMessage(context, "Enter a message");
            return null;
        }
        return toast;
    }

    private void applyBehaviorToOptions(OverlayForm f, OverlayInjectorUtil.DialogOptions dlg) {
        String showMode = textOf(f.showModeTv);
        String showOnceLabel = context.rss.getString(R.string.show_once);
        String dontShowLabel = context.rss.getString(R.string.don_t_show_again_button);
        dlg.showOnce = showOnceLabel.equals(showMode);
        dlg.dontShowAgain = dontShowLabel.equals(showMode);
        dlg.draggable = f.movableSwitch != null && f.movableSwitch.isChecked();
    }

    private OverlayInjectorUtil.DialogOptions collectDialog(OverlayForm f) {
        OverlayInjectorUtil.DialogOptions dlg = new OverlayInjectorUtil.DialogOptions();
        dlg.title = textOf(f.titleInput);
        dlg.message = textOf(f.msgInput);
        dlg.positive = f.posBox.getVisibility() == View.VISIBLE ? textOf(f.posInput).trim() : "";
        dlg.negative = f.negBox.getVisibility() == View.VISIBLE ? textOf(f.negInput).trim() : "";
        dlg.neutral = f.neuBox.getVisibility() == View.VISIBLE ? textOf(f.neuInput).trim() : "";
        boolean[] badUrl = new boolean[1];
        dlg.positiveUrl = f.posBox.getVisibility() == View.VISIBLE ? actionUrl(f.posAction, f.posUrl, badUrl) : null;
        dlg.negativeUrl = f.negBox.getVisibility() == View.VISIBLE ? actionUrl(f.negAction, f.negUrl, badUrl) : null;
        dlg.neutralUrl = f.neuBox.getVisibility() == View.VISIBLE ? actionUrl(f.neuAction, f.neuUrl, badUrl) : null;
        if (badUrl[0]) return null;
        dlg.cancelable = f.cancelSwitch.isChecked();
        applyBehaviorToOptions(f, dlg);
        String mediaMode = textOf(f.mediaTv);
        String videoLabel = context.rss.getString(R.string.media_video);
        String picLabel = context.rss.getString(R.string.media_picture);
        if (videoLabel.equals(mediaMode)) {
            if (overlayVideoPath == null || overlayVideoPath.isEmpty()) {
                Extensions.showMessage(context, R.string.choose_video_required);
                return null;
            }
            dlg.videoPath = overlayVideoPath;
            dlg.imageBase64 = null;
        } else if (picLabel.equals(mediaMode)) {
            dlg.imageBase64 = overlayImageBase64;
            dlg.videoPath = null;
        } else {
            dlg.imageBase64 = null;
            dlg.videoPath = null;
        }
        dlg.html = f.dlgHtml.isChecked();
        dlg.base64 = f.dlgB64.isChecked();
        collectStyle(f, dlg);
        dlg.titleColor = f.titleC;
        dlg.dlgFont = f.dlgFont;
        dlg.dlgFontPath = f.dlgFontPath;
        boolean hasMedia = (dlg.imageBase64 != null && !dlg.imageBase64.isEmpty())
                || (dlg.videoPath != null && !dlg.videoPath.isEmpty());
        if (dlg.message.isEmpty() && dlg.title.isEmpty() && !hasMedia) {
            Extensions.showMessage(context, "Enter a title or message");
            return null;
        }
        if (dlg.positive.isEmpty() && dlg.negative.isEmpty() && dlg.neutral.isEmpty()) {
            dlg.positive = "OK";
        }
        return dlg;
    }

    private OverlayInjectorUtil.DialogOptions collectAdvanced(OverlayForm f,
                                                              List<OverlayInjectorUtil.AdvWidget> widgets) {
        if (widgets == null) widgets = new ArrayList<>();
        ensureChromeWidgets(widgets, f);
        if (widgets.isEmpty()) {
            Extensions.showMessage(context, "Add at least one widget");
            return null;
        }
        OverlayInjectorUtil.DialogOptions dlg = new OverlayInjectorUtil.DialogOptions();
        dlg.advanced = true;
        dlg.widgets = new ArrayList<>();
        for (OverlayInjectorUtil.AdvWidget w : widgets) {
            OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
            if (copy != null) dlg.widgets.add(copy);
        }
        if (dlg.widgets.isEmpty()) {
            Extensions.showMessage(context, "Add at least one widget");
            return null;
        }
        collectStyle(f, dlg);
        dlg.dlgFont = f.advFont;
        dlg.dlgFontPath = f.advFontPath;
        dlg.cancelable = f.cancelSwitch.isChecked();
        applyBehaviorToOptions(f, dlg);
        dlg.html = f.dlgHtml.isChecked();
        dlg.base64 = f.dlgB64.isChecked();
        if (dialogStudio != null) {
            dialogStudio.applyTo(dlg);
            dlg.widgets.clear();
            for (OverlayInjectorUtil.AdvWidget w : widgets) {
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
                if (copy != null) dlg.widgets.add(copy);
            }
            if (dlg.widgets.isEmpty()) {
                Extensions.showMessage(context, "Add at least one widget");
                return null;
            }
        }
        boolean hasButton = false;
        for (OverlayInjectorUtil.AdvWidget w : dlg.widgets) {
            if (w != null && "button".equals(w.kind)) {
                hasButton = true;
                break;
            }
        }
        if (!hasButton) dlg.cancelable = true;
        return dlg;
    }

    private void collectStyle(OverlayForm f, OverlayInjectorUtil.DialogOptions dlg) {
        dlg.bgEnabled = f.bgSwitch.isChecked();
        dlg.bgColor1 = f.bgC1;
        dlg.bgColor2 = f.bgC2;
        dlg.bgGradient = f.bgC2set;
        String orientText = f.orientTv.getText() == null ? "" : f.orientTv.getText().toString();
        if (orientText.startsWith("Left")) dlg.bgOrientation = 1;
        else if (orientText.startsWith("Top-left")) dlg.bgOrientation = 2;
        else if (orientText.startsWith("Bottom")) dlg.bgOrientation = 3;
        else dlg.bgOrientation = 0;
        dlg.cornerRadiusDp = parseFloatSafe(textOf(f.radiusInput), 16f);
        dlg.borderWidthDp = parseFloatSafe(textOf(f.borderWidthInput), 0f);
        dlg.borderColor = f.borderC;
        dlg.animBorder = f.animSwitch.isChecked();
        while (f.animColors.size() < 2) f.animColors.add(f.animColors.isEmpty() ? -16776961 : -65536);
        dlg.animColorA = f.animColors.get(0);
        dlg.animColorB = f.animColors.get(1);
        if (f.animColors.size() > 2) {
            dlg.animExtraColors = new ArrayList<>(f.animColors.subList(2, f.animColors.size()));
        } else {
            dlg.animExtraColors = null;
        }
        dlg.rainbowAnim = f.rainbowSwitch != null && f.rainbowSwitch.isChecked();
        dlg.animSpeedMs = Math.max(100, parseIntSafe(textOf(f.speedInput), 2500));
    }

    private void applyAdvanced(OverlayForm f, List<OverlayInjectorUtil.AdvWidget> widgets,
                               OverlayInjectorUtil.AdvWidget[] selected,
                               OverlayInjectorUtil.DialogOptions o) {
        applyDialog(f, o);
        widgets.clear();
        if (o.widgets != null) {
            for (OverlayInjectorUtil.AdvWidget w : o.widgets) {
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
                if (copy != null) widgets.add(copy);
            }
        }
        f.advFont = o.dlgFont == null ? "" : o.dlgFont;
        f.advFontPath = o.dlgFontPath == null ? "" : o.dlgFontPath;
        if (dialogStudio != null) dialogStudio.loadFrom(o);
        refreshAdvFontLabels(f);
        selected[0] = null;
    }

    private static void setText(EditText e, String s) {
        if (e != null) e.setText(s == null ? "" : s);
    }

    private void applyToast(OverlayForm f, OverlayInjectorUtil.ToastOptions o) {
        setText(f.messageInput, o.message);
        f.durationTv.setText(o.longDuration ? "Long" : "Short", false);
        f.gravityTv.setText(o.gravity == 80 ? "Bottom" : o.gravity == 17 ? "Center" : o.gravity == 48 ? "Top" : "Default", false);
        setText(f.xInput, String.valueOf(o.xOffset));
        setText(f.yInput, String.valueOf(o.yOffset));
        f.toastHtml.setChecked(o.html);
        f.toastB64.setChecked(o.base64);
    }

    private void applyDialog(OverlayForm f, OverlayInjectorUtil.DialogOptions o) {
        setText(f.titleInput, o.title);
        setText(f.msgInput, o.message);
        setupButtonBox(f.posBox, R.id.overlayPosAddBtn, f.posInput, o.positive, f.posAction, f.posUrl, o.positiveUrl);
        setupButtonBox(f.negBox, R.id.overlayNegAddBtn, f.negInput, o.negative, f.negAction, f.negUrl, o.negativeUrl);
        setupButtonBox(f.neuBox, R.id.overlayNeuAddBtn, f.neuInput, o.neutral, f.neuAction, f.neuUrl, o.neutralUrl);
        f.cancelSwitch.setChecked(o.cancelable);
        String modeLabel = o.showOnce ? context.rss.getString(R.string.show_once)
                : o.dontShowAgain ? context.rss.getString(R.string.don_t_show_again_button)
                : context.rss.getString(R.string.always_show);
        if (f.showModeTv != null) f.showModeTv.setText(modeLabel, false);
        if (f.movableSwitch != null) f.movableSwitch.setChecked(o.draggable);
        f.dlgHtml.setChecked(o.html);
        f.dlgB64.setChecked(o.base64);
        f.bgSwitch.setChecked(o.bgEnabled);
        f.bgC1 = o.bgColor1;
        f.bgC2 = o.bgColor2;
        f.bgC2set = o.bgGradient;
        String[] orients = {"Top to bottom", "Left to right", "Top-left to bottom-right", "Bottom-left to top-right"};
        f.orientTv.setText(orients[Math.max(0, Math.min(3, o.bgOrientation))], false);
        setText(f.radiusInput, String.valueOf(o.cornerRadiusDp));
        setText(f.borderWidthInput, String.valueOf(o.borderWidthDp));
        f.borderC = o.borderColor;
        f.animSwitch.setChecked(o.animBorder);
        f.animOn = o.animBorder;
        if (f.rainbowSwitch != null) f.rainbowSwitch.setChecked(o.rainbowAnim);
        if (f.animBox != null) f.animBox.setVisibility(o.animBorder ? View.VISIBLE : View.GONE);
        f.animColors.clear();
        f.animColors.add(o.animColorA);
        f.animColors.add(o.animColorB);
        if (o.animExtraColors != null) f.animColors.addAll(o.animExtraColors);
        if (f.renderAnimChips != null) f.renderAnimChips.run();
        setText(f.speedInput, String.valueOf(o.animSpeedMs));
        f.animMs = o.animSpeedMs;
        f.titleC = o.titleColor;
        if (f.titleColorHex != null) {
            f.titleColorHex.setText(o.titleColor == 0 ? "" : String.format("#%08X", o.titleColor));
        }
        f.dlgFont = o.dlgFont == null ? "" : o.dlgFont;
        f.dlgFontPath = o.dlgFontPath == null ? "" : o.dlgFontPath;
        f.radiusDp = o.cornerRadiusDp;
        f.borderDp = o.borderWidthDp;
        f.orient = o.bgOrientation;
        overlayImageBase64 = o.imageBase64;
        overlayVideoPath = o.videoPath;
        String mediaLabel = (o.videoPath != null && !o.videoPath.isEmpty())
                ? context.rss.getString(R.string.media_video)
                : (o.imageBase64 != null && !o.imageBase64.isEmpty())
                        ? context.rss.getString(R.string.media_picture)
                        : context.rss.getString(R.string.media_none);
        if (f.mediaTv != null) f.mediaTv.setText(mediaLabel, false);
        refreshStyleLabels(f);
        refreshFontLabels(f);
        syncMediaUi(f);
        updatePreviewImage(f);
        syncUrlBoxes(f);
    }

    private void setupButtonBox(LinearLayout box, int addBtnId, TextInputEditText textInput,
                                String text, MaterialAutoCompleteTextView actionTv,
                                TextInputEditText urlInput, String url) {
        View root = (View) box.getParent();
        View addBtn = root.findViewById(addBtnId);
        boolean show = text != null && !text.isEmpty();
        box.setVisibility(show ? View.VISIBLE : View.GONE);
        if (addBtn != null) addBtn.setVisibility(show ? View.GONE : View.VISIBLE);
        setText(textInput, text);
        if (actionTv != null) actionTv.setText(url != null && !url.isEmpty() ? "Open URL" : "Dismiss", false);
        setText(urlInput, url);
    }

    private void refreshProfiles(OverlayForm f, boolean isToast) {
        try {
            List<String> names = isToast ? OverlayProfiles.toastNames(context) : OverlayProfiles.dialogNames(context);
            f.profileTv.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, names));
        } catch (Exception ignored) {
        }
    }

    private void attachFormatMenu(EditText target) {
        if (target == null) return;
        target.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, 101, 0, R.string.bold);
                menu.add(0, 102, 1, R.string.italic);
                menu.add(0, 103, 2, R.string.underline);
                menu.add(0, 104, 3, R.string.color);
                menu.add(0, 105, 4, android.R.string.copy);
                menu.add(0, 106, 5, android.R.string.cut);
                menu.add(0, 107, 6, android.R.string.paste);
                menu.add(0, 108, 7, R.string.share_text);
                menu.add(0, 109, 8, android.R.string.selectAll);
                return true;
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = target.getSelectionStart();
                int end = target.getSelectionEnd();
                if (start < 0 || end < 0) return false;
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                switch (item.getItemId()) {
                    case 101:
                        wrapRange(target, start, end, "<b>", "</b>");
                        break;
                    case 102:
                        wrapRange(target, start, end, "<i>", "</i>");
                        break;
                    case 103:
                        wrapRange(target, start, end, "<u>", "</u>");
                        break;
                    case 104:
                        mode.finish();
                        showColorWheelDialog(target, start, end);
                        return true;
                    case 105:
                        copyRange(start, end);
                        break;
                    case 106:
                        copyRange(start, end);
                        replaceRange(start, end, "");
                        break;
                    case 107:
                        pasteAt(start, end);
                        break;
                    case 108: {
                        String text = start == end ? textOf(target)
                                : target.getText().toString().substring(start, end);
                        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text);
                        context.startActivity(Intent.createChooser(share, "Share"));
                        break;
                    }
                    case 109:
                        target.selectAll();
                        return true;
                    default:
                        return false;
                }
                mode.finish();
                return true;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            private void copyRange(int start, int end) {
                try {
                    String text = target.getText().toString().substring(start, end);
                    ClipboardManager cm = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("text", text));
                    Extensions.showMessage(context, context.getString(R.string.copied_style));
                } catch (Exception ignored) {
                }
            }

            private void replaceRange(int start, int end, String replacement) {
                try {
                    Editable text = target.getText();
                    if (text != null) text.replace(start, end, replacement);
                } catch (Exception ignored) {
                }
            }

            private void pasteAt(int start, int end) {
                try {
                    ClipboardManager cm = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return;
                    CharSequence paste = cm.getPrimaryClip().getItemAt(0).coerceToText(context);
                    if (paste != null) replaceRange(start, end, paste.toString());
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void wrapRange(EditText target, int start, int end, String open, String close) {
        if (target == null || target.getText() == null) return;
        try {
            Editable text = target.getText();
            int len = text.length();
            start = Math.max(0, Math.min(start, len));
            end = Math.max(0, Math.min(end, len));
            if (start == end) {
                text.insert(start, open + close);
                target.setSelection(start + open.length());
            } else {
                text.insert(end, close);
                text.insert(start, open);
                target.setSelection(end + open.length() + close.length());
            }
        } catch (Exception ignored) {
        }
    }

    private String formatVideoSummary(String path) {
        if (path == null || path.isEmpty()) return "";
        File f = new File(path);
        String size = Formatter.formatFileSize(context, f.isFile() ? f.length() : 0L);
        return context.rss.getString(R.string.video_selected_format, f.getName(), size);
    }

    private void syncMediaUi(OverlayForm f) {
        if (f == null || f.mediaTv == null) return;
        String mode = textOf(f.mediaTv);
        String picLabel = context.rss.getString(R.string.media_picture);
        String vidLabel = context.rss.getString(R.string.media_video);
        boolean isPic = picLabel.equals(mode);
        boolean isVid = vidLabel.equals(mode);
        if (f.mediaBox != null) f.mediaBox.setVisibility((isPic || isVid) ? View.VISIBLE : View.GONE);
        if (f.pickMediaBtn != null) {
            f.pickMediaBtn.setText(isVid ? R.string.choose_video : R.string.dialog_image);
        }
        if (f.videoWarnTv != null) f.videoWarnTv.setVisibility(isVid ? View.VISIBLE : View.GONE);
        if (f.videoSummaryTv != null) {
            String summary = isVid ? formatVideoSummary(overlayVideoPath) : "";
            f.videoSummaryTv.setText(summary);
            f.videoSummaryTv.setVisibility(!summary.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (overlayImagePreviewView != null && !isPic) {
            overlayImagePreviewView.setImageBitmap(null);
            overlayImagePreviewView.setVisibility(View.GONE);
        }
    }

    private void updatePreviewImage(OverlayForm f) {
        String mode = f.mediaTv != null ? textOf(f.mediaTv) : "";
        boolean isVid = context.rss.getString(R.string.media_video).equals(mode);
        if (f.dlgPreviewVideoTv != null) {
            String summary = isVid ? formatVideoSummary(overlayVideoPath) : "";
            f.dlgPreviewVideoTv.setText(summary);
            f.dlgPreviewVideoTv.setVisibility(!summary.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (isVid) {
            f.dlgPreviewImage.setVisibility(View.GONE);
            return;
        }
        try {
            if (overlayImageBase64 != null && !overlayImageBase64.isEmpty()
                    && context.rss.getString(R.string.media_picture).equals(mode)) {
                byte[] bytes = Base64.decode(overlayImageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    f.dlgPreviewImage.setImageBitmap(bitmap);
                    f.dlgPreviewImage.setVisibility(View.VISIBLE);
                    if (overlayImagePreviewView != null) {
                        overlayImagePreviewView.setImageBitmap(bitmap);
                        overlayImagePreviewView.setVisibility(View.VISIBLE);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        f.dlgPreviewImage.setVisibility(View.GONE);
    }

    private void setupStyleControls(View view, OverlayForm f, Runnable updatePreview) {
        View styleBox = view.findViewById(R.id.overlayStyleBox);
        view.findViewById(R.id.overlayStyleToggle).setOnClickListener(v -> {
            boolean show = styleBox.getVisibility() != View.VISIBLE;
            styleBox.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        String[] orients = {"Top to bottom", "Left to right", "Top-left to bottom-right", "Bottom-left to top-right"};
        f.orientTv.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, orients));
        f.orientTv.setText(orients[0], false);
        f.bg1Btn.setOnClickListener(v -> showColorWheel(f.bgC1, (argb, hex) -> {
            f.bgC1 = argb;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.bg2Btn.setOnClickListener(v -> showColorWheel(f.bgC2set ? f.bgC2 : -16776961, (argb, hex) -> {
            f.bgC2 = argb;
            f.bgC2set = true;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.bg2Btn.setOnLongClickListener(v -> {
            f.bgC2set = false;
            refreshStyleLabels(f);
            updatePreview.run();
            Extensions.showMessage(context, "Gradient cleared (long-press clears)");
            return true;
        });
        f.borderColorBtn.setOnClickListener(v -> showColorWheel(f.borderC, (argb, hex) -> {
            f.borderC = argb;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.renderAnimChips = () -> {
            if (f.animColorsRow == null) return;
            f.animColorsRow.removeAllViews();
            if (f.animColors.size() < 2) {
                while (f.animColors.size() < 2) f.animColors.add(f.animColors.isEmpty() ? -16776961 : -65536);
            }
            for (int i = 0; i < f.animColors.size(); i++) {
                final int idx = i;
                int color = f.animColors.get(idx);
                MaterialButton chip = new MaterialButton(context);
                chip.setText("");
                chip.setCornerRadius(dp(24));
                try {
                    chip.setBackgroundTintList(ColorStateList.valueOf(color));
                } catch (Exception ignored) {
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                int m = dp(4);
                lp.setMargins(m, 0, m, 0);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(x -> showColorWheel(color, (argb, hex) -> {
                    f.animColors.set(idx, argb);
                    f.renderAnimChips.run();
                    updatePreview.run();
                }));
                chip.setOnLongClickListener(x -> {
                    if (f.animColors.size() > 2) {
                        f.animColors.remove(idx);
                        f.renderAnimChips.run();
                        updatePreview.run();
                    } else {
                        Extensions.showMessage(context, "Need at least 2 colors");
                    }
                    return true;
                });
                f.animColorsRow.addView(chip);
            }
            MaterialButton add = new MaterialButton(context);
            add.setBackgroundResource(R.drawable.add_24px);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
            int am = dp(4);
            alp.setMargins(am, 0, am, 0);
            add.setLayoutParams(alp);
            add.setOnClickListener(x -> showColorWheel(-65536, (argb, hex) -> {
                f.animColors.add(argb);
                f.renderAnimChips.run();
                updatePreview.run();
            }));
            f.animColorsRow.addView(add);
        };
        f.renderAnimChips.run();
        f.titleColorBtn.setOnClickListener(v -> showColorWheel(f.titleC == 0 ? -16777216 : f.titleC, (argb, hex) -> {
            f.titleC = argb;
            if (f.titleColorHex != null) f.titleColorHex.setText(hex);
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.titleColorBtn.setOnLongClickListener(v -> {
            f.titleC = 0;
            if (f.titleColorHex != null) f.titleColorHex.setText("");
            refreshStyleLabels(f);
            updatePreview.run();
            return true;
        });
        if (f.titleColorHex != null) {
            f.titleColorHex.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    int parsed = parseHexColor(s.toString(), 0);
                    if (parsed != 0 || s.toString().trim().isEmpty()) {
                        f.titleC = parsed;
                        refreshStyleLabels(f);
                        updatePreview.run();
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
        }
        f.bgSwitch.setOnCheckedChangeListener((b, c) -> updatePreview.run());
        f.animSwitch.setOnCheckedChangeListener((b, c) -> {
            f.animOn = c;
            if (f.animBox != null) f.animBox.setVisibility(c ? View.VISIBLE : View.GONE);
            updatePreview.run();
        });
        if (f.animBox != null) {
            f.animBox.setVisibility(f.animSwitch.isChecked() ? View.VISIBLE : View.GONE);
        }
        TextWatcher styleWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updatePreview.run();
            }

            public void afterTextChanged(Editable s) {
            }
        };
        f.radiusInput.addTextChangedListener(styleWatcher);
        f.borderWidthInput.addTextChangedListener(styleWatcher);
        f.speedInput.addTextChangedListener(styleWatcher);
        refreshStyleLabels(f);
    }

    private static String shortHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private void refreshStyleLabels(OverlayForm f) {
        f.bg1Btn.setText(context.getString(R.string.bg_format, shortHex(f.bgC1)));
        f.bg2Btn.setText(f.bgC2set ? context.getString(R.string.grad_format, shortHex(f.bgC2)) : context.getString(R.string.gradient_none));
        f.borderColorBtn.setText(context.getString(R.string.border_format, shortHex(f.borderC)));
        if (f.titleColorBtn != null) {
            f.titleColorBtn.setText("");
            try {
                if (f.titleC == 0) {
                    f.titleColorBtn.setBackgroundTintList(null);
                } else {
                    f.titleColorBtn.setBackgroundTintList(
                            ColorStateList.valueOf(f.titleC));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static int parseHexColor(String text, int def) {
        try {
            String hex = text == null ? "" : text.trim();
            if (hex.isEmpty()) return 0;
            if (!hex.startsWith("#")) hex = "#" + hex;
            if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return def;
            return (int) Long.parseLong(hex.substring(1), 16)
                    | (hex.length() == 7 ? 0xFF000000 : 0);
        } catch (Exception e) {
            return def;
        }
    }

    private static float parseFloatSafe(String text, float def) {
        try {
            return Float.parseFloat(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseIntSafe(String text, int def) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void updateStylePreview(OverlayForm f) {
        try {
            float density = context.getResources().getDisplayMetrics().density;
            f.radiusDp = parseFloatSafe(textOf(f.radiusInput), 16f);
            f.borderDp = parseFloatSafe(textOf(f.borderWidthInput), 0f);
            f.animMs = Math.max(100, parseIntSafe(textOf(f.speedInput), 2500));
            String orientText = f.orientTv.getText() == null ? "" : f.orientTv.getText().toString();
            if (orientText.startsWith("Left")) f.orient = 1;
            else if (orientText.startsWith("Top-left")) f.orient = 2;
            else if (orientText.startsWith("Bottom")) f.orient = 3;
            else f.orient = 0;
            f.animOn = f.animSwitch.isChecked();
            int onSurface = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
            if (f.titleC != 0) f.dlgPreviewTitle.setTextColor(f.titleC);
            else f.dlgPreviewTitle.setTextColor(onSurface);
            f.dlgPreviewMsg.setTextColor(onSurface);
            boolean styled = f.bgSwitch.isChecked() || f.borderDp > 0;
            if (!styled) {
                f.dlgPreviewBox.setBackground(null);
                if (f.phoneCard != null) {
                    int base = MaterialColors.getColor(context,
                            com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
                    int glassA = (base & 0x00FFFFFF) | 0xE8000000;
                    int glassB = (base & 0x00FFFFFF) | 0xD0000000;
                    GradientDrawable glass = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{glassA, glassB});
                    glass.setCornerRadius(f.radiusDp * density);
                    glass.setStroke(Math.max(1, (int) (density + 0.5f)), 0x66FFFFFF);
                    f.phoneCard.setBackground(glass);
                    f.phoneCard.setRadius(f.radiusDp * density);
                }
                return;
            }
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(f.radiusDp * density);
            if (f.bgSwitch.isChecked()) {
                if (f.bgC2set) {
                    gd.setColors(new int[]{f.bgC1, f.bgC2});
                    GradientDrawable.Orientation[] values = {
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            GradientDrawable.Orientation.TL_BR,
                            GradientDrawable.Orientation.BL_TR};
                    gd.setOrientation(values[Math.max(0, Math.min(3, f.orient))]);
                } else {
                    gd.setColor(f.bgC1);
                }
            }
            if (f.borderDp > 0) {
                gd.setStroke((int) (f.borderDp * density + 0.5f), f.borderC);
            }
            f.dlgPreviewBox.setBackground(gd);
            if (f.phoneCard != null) {
                f.phoneCard.setBackground(gd);
                f.phoneCard.setRadius(f.radiusDp * density);
            }
        } catch (Exception ignored) {
        }
    }

    private static CharSequence displayText(String raw, boolean richText) {
        if (raw == null) raw = "";
        if (!richText) return raw;
        try {
            return HtmlCompat.fromHtml(raw,
                    HtmlCompat.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) {
            return raw;
        }
    }

    private GradientDrawable buildButtonDrawable(
            OverlayInjectorUtil.AdvWidget w) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable gd =
                new GradientDrawable();
        if (w.btnBg != 0 && w.btnBg2 != 0) {
            gd.setColors(new int[]{w.btnBg, w.btnBg2});
        } else if (w.btnBg != 0) {
            gd.setColor(w.btnBg);
        } else {
            return gd;
        }
        if (w.btnCornerRadiusDp > 0) gd.setCornerRadius(w.btnCornerRadiusDp * density);
        float borderDp = w.btnBorderWidthDp > 0 ? w.btnBorderWidthDp : (w.btnAnim ? 2f : 0f);
        if (borderDp > 0) {
            int bc = w.btnBorderColor != -1 ? w.btnBorderColor : w.btnBg;
            gd.setStroke(Math.max(1, (int) (borderDp * density + 0.5f)), bc);
        }
        return gd;
    }

    private static int[] snapWidget(OverlayInjectorUtil.AdvWidget self, View dragged,
                                    int lPx, int tPx, FrameLayout canvas, float density) {
        int thresh = (int) (8 * density + 0.5f);

        int nl = lPx;
        int nt = tPx;
        int vw = dragged.getWidth() > 0 ? dragged.getWidth() : (int) (80 * density);
        int vh = dragged.getHeight() > 0 ? dragged.getHeight() : (int) (48 * density);
        int[] xs = new int[]{0, Math.max(0, (canvas.getWidth() - vw) / 2)};
        int[] ys = new int[]{0};
        if (canvas.getChildCount() > 0) {
            int[] extra = new int[canvas.getChildCount() * 3 + 2];
            System.arraycopy(xs, 0, extra, 0, xs.length);
            int n = xs.length;
            for (int i = 0; i < canvas.getChildCount(); i++) {
                View sib = canvas.getChildAt(i);
                if (sib == dragged) continue;
                extra[n++] = sib.getLeft();
                extra[n++] = sib.getRight();
                extra[n++] = sib.getLeft() + sib.getWidth() / 2;
            }
            xs = Arrays.copyOf(extra, n);
            int[] yextra = new int[canvas.getChildCount() * 3 + 1];
            yextra[0] = 0;
            int m = 1;
            for (int i = 0; i < canvas.getChildCount(); i++) {
                View sib = canvas.getChildAt(i);
                if (sib == dragged) continue;
                yextra[m++] = sib.getTop();
                yextra[m++] = sib.getBottom();
                yextra[m++] = sib.getTop() + sib.getHeight() / 2;
            }
            ys = Arrays.copyOf(yextra, m);
        }
        int[] xEdges = new int[]{nl, nl + vw, nl + vw / 2};
        int bestDx = 0;
        boolean foundX = false;
        for (int xEdge : xEdges) {
            for (int gx : xs) {
                int d = gx - xEdge;
                if (Math.abs(d) <= thresh && (!foundX || Math.abs(d) < Math.abs(bestDx))) {
                    bestDx = d;
                    foundX = true;
                }
            }
        }
        if (foundX) nl += bestDx;
        int[] yEdges = new int[]{nt, nt + vh, nt + vh / 2};
        int bestDy = 0;
        boolean foundY = false;
        for (int yEdge : yEdges) {
            for (int gy : ys) {
                int d = gy - yEdge;
                if (Math.abs(d) <= thresh && (!foundY || Math.abs(d) < Math.abs(bestDy))) {
                    bestDy = d;
                    foundY = true;
                }
            }
        }
        if (foundY) nt += bestDy;
        return new int[]{Math.max(0, nl), Math.max(0, nt)};
    }

    private static String fontLabel(String family) {
        if (family == null || family.isEmpty()) return "Font: Default";
        return switch (family) {
            case "serif" -> "Font: Serif";
            case "monospace" -> "Font: Mono";
            case "sans-serif" -> "Font: Sans";
            case "sans-serif-light" -> "Font: Light";
            case "sans-serif-medium" -> "Font: Medium";
            case "sans-serif-black" -> "Font: Black";
            case "sans-serif-condensed" -> "Font: Condensed";
            default -> "Font: Default";
        };
    }

    private static final String[] FONT_LABELS = {"Default", "Sans", "Serif", "Mono", "Light", "Medium", "Black", "Condensed"};
    private static final String[] FONT_VALUES = {"", "sans-serif", "serif", "monospace", "sans-serif-light",
            "sans-serif-medium", "sans-serif-black", "sans-serif-condensed"};

    private interface FontPicked {
        void onFont(String path);
    }

    private interface FontApplier {
        void apply(String font, String fontPath);
    }

    private interface VideoPicked {
        void onVideo(String path);
    }

    private void pickVideoFile(VideoPicked cb) {
        try {
            FilePickerDialog.Properties props = new FilePickerDialog.Properties();
            props.selection_mode = FilePickerDialog.SINGLE_MODE;
            props.selection_type = FilePickerDialog.FILE_SELECT;
            props.extensions = new String[]{"mp4", "webm", "3gp", "mkv", "mov"};
            props.preferenceKey = "overlay_video";
            FilePickerDialog dlg = new FilePickerDialog(context, props);
            dlg.setTitle(context.getString(R.string.choose_video));
            dlg.setDialogSelectionListener(files -> {
                if (files == null || files.length == 0 || files[0] == null) return;
                File src = new File(files[0]);
                if (!src.isFile() || src.length() <= 0) {
                    Extensions.showMessage(context, R.string.choose_video_required);
                    return;
                }
                if (src.length() > OverlayInjectorUtil.MAX_VIDEO_BYTES) {
                    Extensions.showMessage(context, R.string.video_too_large);
                    return;
                }
                if (cb != null) cb.onVideo(src.getAbsolutePath());
            });
            dlg.show();
        } catch (Exception e) {
            Extensions.showMessage(context, R.string.choose_video_required);
        }
    }

    private void pickFontFile(FontPicked cb) {
        try {
            FilePickerDialog.Properties props = new FilePickerDialog.Properties();
            props.selection_mode = FilePickerDialog.SINGLE_MODE;
            props.selection_type = FilePickerDialog.FILE_SELECT;
            props.extensions = new String[]{"ttf", "otf"};
            props.preferenceKey = "overlay_font";
            FilePickerDialog dlg = new FilePickerDialog(context, props);
            dlg.setTitle(context.getString(R.string.choose_font));
            dlg.setDialogSelectionListener(files -> {
                if (files == null || files.length == 0 || files[0] == null) return;
                onFontFilePicked(new File(files[0]), cb);
            });
            dlg.show();
        } catch (Exception e) {
            Extensions.showMessage(context, "Font picker unavailable");
        }
    }

    private void onFontFilePicked(File src, FontPicked cb) {
        AppExecutors.io().execute(() -> {
            try {
                if (src == null || !src.isFile()) throw new IOException("Not a file");
                String lower = src.getName().toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
                    context.handler.post(() -> Extensions.showMessage(context, "Please pick a .ttf or .otf file"));
                    return;
                }
                File dir = context.getExternalFilesDir("Fonts");
                if (dir == null) dir = context.getFilesDir();
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, "mpfont_" + System.currentTimeMillis()
                        + (lower.endsWith(".otf") ? ".otf" : ".ttf"));
                try (InputStream is = new FileInputStream(src);
                     FileOutputStream os = new FileOutputStream(dst)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
                }
                if (!dst.exists() || dst.length() == 0) {
                    context.handler.post(() -> Extensions.showMessage(context, "Font copy failed"));
                    return;
                }
                String path = dst.getAbsolutePath();
                context.handler.post(() -> {
                    if (cb != null) cb.onFont(path);
                    Extensions.showMessage(context, "Font loaded: " + dst.getName());
                });
            } catch (Exception e) {
                context.handler.post(() -> Extensions.showMessage(context, "Font failed: " + e.getMessage()));
            }
        });
    }

    private static String baseName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        String n = i >= 0 ? path.substring(i + 1) : path;
        return n.length() > 24 ? "…" + n.substring(n.length() - 23) : n;
    }

    private static Typeface loadTypeface(String font, String fontPath) {
        try {
            if (fontPath != null && !fontPath.isEmpty()) {
                Typeface tf = Typeface.createFromFile(fontPath);
                if (tf != null) return tf;
            }
            if (font != null && !font.isEmpty()) {
                return Typeface.create(font, Typeface.NORMAL);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void refreshFontLabels(OverlayForm f) {
        if (f == null || f.dlgFontBtn == null) return;
        if (f.dlgFontPath != null && !f.dlgFontPath.isEmpty()) {
            f.dlgFontBtn.setText(context.rss.getString(R.string.font_x, baseName(f.dlgFontPath)));
        } else {
            f.dlgFontBtn.setText(fontLabel(f.dlgFont));
        }
    }

    private void refreshAdvFontLabels(OverlayForm f) {
        if (f == null || f.advFontBtn == null) return;
        if (f.advFontPath != null && !f.advFontPath.isEmpty()) {
            f.advFontBtn.setText(context.rss.getString(R.string.dialog_font_x, baseName(f.advFontPath)));
        } else {
            String label = fontLabel(f.advFont).replace(context.rss.getString(R.string.font_x), context.rss.getString(R.string.dialog_font_x));
            f.advFontBtn.setText(label);
        }
    }

    private String widgetFontLabel(OverlayInjectorUtil.AdvWidget w) {
        if (w == null) return context.rss.getString(R.string.font_default);
        if (!TextUtils.isEmpty(w.fontPath)) return context.rss.getString(R.string.dialog_font_x, baseName(w.fontPath));
        if (TextUtils.isEmpty(w.font)) return context.rss.getString(R.string.font_dialog);
        return fontLabel(w.font);
    }

    private static String effectivePreviewFont(OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (w != null && !TextUtils.isEmpty(w.font)) return w.font;
        if (f != null && !TextUtils.isEmpty(f.advFont)) return f.advFont;
        return "";
    }

    private static String effectivePreviewFontPath(OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (w != null && !TextUtils.isEmpty(w.fontPath)) return w.fontPath;
        if (f != null && !TextUtils.isEmpty(f.advFontPath)) return f.advFontPath;
        return "";
    }

    private static void applyWidgetTypeface(TextView tv, OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (tv == null || w == null) return;
        try {
            Typeface tf = loadTypeface(
                    effectivePreviewFont(w, f), effectivePreviewFontPath(w, f));
            if (tf != null) tv.setTypeface(tf, w.fontStyle);
            else if (w.fontStyle != 0) tv.setTypeface(null, w.fontStyle);
        } catch (Exception ignored) {
        }
    }

    private void showFontPickerDialog(FontApplier applier, String currentFont, String currentFontPath,
                                      MaterialButton labelBtn) {
        String[] labels = new String[FONT_LABELS.length + 2];
        System.arraycopy(FONT_LABELS, 0, labels, 0, FONT_LABELS.length);
        labels[FONT_LABELS.length] = "Choose font file (ttf/otf)…";
        labels[FONT_LABELS.length + 1] = "Clear";
        int checked = -1;
        if (currentFontPath == null || currentFontPath.isEmpty()) {
            for (int i = 0; i < FONT_VALUES.length; i++) {
                if (FONT_VALUES[i].equals(currentFont)) {
                    checked = i;
                    break;
                }
            }
        }
        dialogUtil.getDialogBuilder()
                .setTitle(R.string.font)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (which < FONT_LABELS.length) {
                        applier.apply(FONT_VALUES[which], "");
                        if (labelBtn != null) labelBtn.setText(fontLabel(FONT_VALUES[which]));
                    } else if (which == FONT_LABELS.length) {
                        pickFontFile(path -> {
                            applier.apply("", path);
                             if (labelBtn != null) labelBtn.setText(context.getString(R.string.font_label_format, baseName(path)));
                        });
                    } else {
                        applier.apply("", "");
                         if (labelBtn != null) labelBtn.setText(context.getString(R.string.font_default_label));
                    }
                }).show();
    }

    private void showFontPicker(OverlayInjectorUtil.AdvWidget w, MaterialButton fontBtn) {
        showFontPickerDialog((font, path) -> {
            w.font = font;
            w.fontPath = path;
            renderAdvSafe();
        }, w.font, w.fontPath, fontBtn);
    }

    private void showToastMessageDialog(File file, List<String> selectedActivities) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        overlayImageBase64 = null;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_overlay_editor, null);

        MaterialAutoCompleteTextView modeTv = view.findViewById(R.id.overlayModeTv);
        String toast = context.rss.getString(R.string.toast);
        String dialog = context.rss.getString(R.string.dialog);
        String adv = context.rss.getString(R.string.advanced);
        String[] modes = {toast, dialog, adv};
        modeTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, modes));
        modeTv.setText(modes[0], false);

        LinearLayout advancedSection = view.findViewById(R.id.overlayAdvancedSection);
        FrameLayout advCanvas = view.findViewById(R.id.overlayCanvas);
        View phoneFrame = view.findViewById(R.id.overlayPhoneFrame);
        if (phoneFrame != null) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int available = Math.max(dp(220), screenWidth - dp(32));
            int phoneWidth = Math.min(dp(360), available);
            ViewGroup.LayoutParams lp = phoneFrame.getLayoutParams();
            lp.width = phoneWidth;
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
            }
            phoneFrame.setLayoutParams(lp);
        }
        LinearLayout advProps = view.findViewById(R.id.overlayPropsBox);
        final List<OverlayInjectorUtil.AdvWidget> advWidgets = new ArrayList<>();
        final OverlayInjectorUtil.AdvWidget[] advSelected = new OverlayInjectorUtil.AdvWidget[1];
        final float advDensity = context.getResources().getDisplayMetrics().density;
        final Runnable[] renderAdv = new Runnable[1];
        final Runnable[] showAdvProps = new Runnable[1];
        final int[] advRenderToken = new int[1];
        final Deque<List<OverlayInjectorUtil.AdvWidget>> advUndo = new ArrayDeque<>();
        final Deque<List<OverlayInjectorUtil.AdvWidget>> advRedo = new ArrayDeque<>();
        final Supplier<List<OverlayInjectorUtil.AdvWidget>> advSnapshot = () -> {
            List<OverlayInjectorUtil.AdvWidget> snapshot = new ArrayList<>();
            for (OverlayInjectorUtil.AdvWidget widget : advWidgets) {
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(widget);
                if (copy != null) snapshot.add(copy);
            }
            return snapshot;
        };
        final Consumer<List<OverlayInjectorUtil.AdvWidget>> advRestore = snapshot -> {
            advWidgets.clear();
            for (OverlayInjectorUtil.AdvWidget widget : snapshot) {
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(widget);
                if (copy != null) advWidgets.add(copy);
            }
            advSelected[0] = null;
            renderAdv[0].run();
            showAdvProps[0].run();
        };
        final Runnable pushAdvHistory = () -> {
            advUndo.push(advSnapshot.get());
            while (advUndo.size() > 40) advUndo.removeLast();
            advRedo.clear();
        };
        final OverlayForm form = fillForm(view);

        showAdvProps[0] = () -> {
            advProps.removeAllViews();
            OverlayInjectorUtil.AdvWidget w = advSelected[0];
            if (w == null) {
                TextView hint = new TextView(context);
                hint.setText(context.getString(R.string.tap_widget_hint));
                hint.setTextSize(13);
                advProps.addView(hint);
                return;
            }
            TextView header = new TextView(context);
            header.setText(context.getString(R.string.selected_format, w.kind));
            header.setTypeface(null, Typeface.BOLD);
            header.setTextSize(15);
            advProps.addView(header);
            LinearLayout xyRow = new LinearLayout(context);
            xyRow.setOrientation(LinearLayout.HORIZONTAL);
            EditText xInput = new EditText(context);
            xInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            xInput.setText(String.valueOf(w.leftDp));
            EditText yInput = new EditText(context);
            yInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            yInput.setText(String.valueOf(w.topDp));
            xyRow.addView(UiFields.wrap(context, xInput, "X dp", 0),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            xyRow.addView(UiFields.wrap(context, yInput, "Y dp", 0),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(xyRow);
            xInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.leftDp = Math.max(0, Integer.parseInt(s.toString().trim()));
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            yInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.topDp = Math.max(0, Integer.parseInt(s.toString().trim()));
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            LinearLayout whRow = new LinearLayout(context);
            whRow.setOrientation(LinearLayout.HORIZONTAL);
            EditText wInput = new EditText(context);
            wInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            wInput.setText(w.widthDp > 0 ? String.valueOf(w.widthDp) : "");
            wInput.setHint("Auto");
            EditText hInput = new EditText(context);
            hInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            hInput.setText(w.heightDp > 0 ? String.valueOf(w.heightDp) : "");
            hInput.setHint("Auto");
            whRow.addView(UiFields.wrap(context, wInput, "W dp", 0),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            whRow.addView(UiFields.wrap(context, hInput, "H dp", 0),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(whRow);
            wInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.widthDp = s.length() == 0 ? 0 : Math.max(0, Integer.parseInt(s.toString().trim()));
                        w.widthPercent = 0;
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            hInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.heightDp = s.length() == 0 ? 0 : Math.max(0, Integer.parseInt(s.toString().trim()));
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            LinearLayout sizeButtons = new LinearLayout(context);
            sizeButtons.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton fullBtn = new MaterialButton(context);
            fullBtn.setText("ملء العرض");
            fullBtn.setOnClickListener(x -> {
                w.leftDp = 0;
                w.widthDp = 0;
                w.widthPercent = 100;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
            MaterialButton autoBtn = new MaterialButton(context);
            autoBtn.setText("حجم تلقائي");
            autoBtn.setOnClickListener(x -> {
                w.widthDp = 0;
                w.heightDp = 0;
                w.widthPercent = 0;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
            sizeButtons.addView(fullBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            sizeButtons.addView(autoBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(sizeButtons);
            LinearLayout styleRow2 = new LinearLayout(context);
            styleRow2.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton copyBtn = new MaterialButton(context);
            copyBtn.setText(context.getString(R.string.copy_style));
            copyBtn.setOnClickListener(x -> {
                styleClipboard = OverlayInjectorUtil.copyWidget(w);
                Extensions.showMessage(context, context.getString(R.string.style_copied_format, w.kind));
                showAdvProps[0].run();
            });
            styleRow2.addView(copyBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton pasteBtn = new MaterialButton(context);
            pasteBtn.setText(context.getString(R.string.paste_style));
            boolean canPaste = styleClipboard != null && w.kind.equals(styleClipboard.kind);
            pasteBtn.setEnabled(canPaste);
            pasteBtn.setAlpha(canPaste ? 1f : 0.5f);
            pasteBtn.setOnClickListener(x -> {
                if (styleClipboard == null || !w.kind.equals(styleClipboard.kind)) return;
                pasteStyle(w, styleClipboard);
                renderAdv[0].run();
                showAdvProps[0].run();
                Extensions.showMessage(context, context.getString(R.string.style_pasted));
            });
            styleRow2.addView(pasteBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(styleRow2);
            LinearLayout editActions = new LinearLayout(context);
            editActions.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton duplicateBtn = new MaterialButton(context);
            duplicateBtn.setText("تكرار");
            duplicateBtn.setOnClickListener(x -> {
                pushAdvHistory.run();
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
                if (copy == null) return;
                copy.id = null;
                copy.sourceKind = copy.kind;
                copy.leftDp = w.leftDp + 12;
                copy.topDp = w.topDp + 12;
                advWidgets.add(copy);
                advSelected[0] = copy;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
            editActions.addView(duplicateBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!isBaseChromeWidget(w)) {
                MaterialButton delBtn = new MaterialButton(context);
                delBtn.setText(context.getString(R.string.delete_widget));
                delBtn.setOnClickListener(x -> {
                    pushAdvHistory.run();
                    advWidgets.remove(w);
                    advSelected[0] = null;
                    renderAdv[0].run();
                    showAdvProps[0].run();
                });
                editActions.addView(delBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            advProps.addView(editActions);
            LinearLayout orderRow = new LinearLayout(context);
            orderRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton frontBtn = new MaterialButton(context);
            frontBtn.setText("للأمام");
            frontBtn.setOnClickListener(x -> {
                pushAdvHistory.run();
                advWidgets.remove(w);
                advWidgets.add(w);
                renderAdv[0].run();
            });
            MaterialButton backBtn = new MaterialButton(context);
            backBtn.setText("للخلف");
            backBtn.setOnClickListener(x -> {
                pushAdvHistory.run();
                advWidgets.remove(w);
                advWidgets.add(0, w);
                renderAdv[0].run();
            });
            orderRow.addView(frontBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            orderRow.addView(backBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(orderRow);
            if (w.kind.equals("image")) {
                ImageView thumb = new ImageView(context);
                thumb.setAdjustViewBounds(true);
                if (w.imageB64 != null && !w.imageB64.isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(w.imageB64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bitmap != null) thumb.setImageBitmap(bitmap);
                    } catch (Exception ignored) {
                    }
                }
                advProps.addView(thumb, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
                MaterialButton pickBtn = new MaterialButton(context);
                pickBtn.setText(context.getString(R.string.choose_image));
                pickBtn.setOnClickListener(x -> pickImage((bmp, b64) -> {
                    w.imageB64 = b64;
                    renderAdv[0].run();
                    showAdvProps[0].run();
                }));
                advProps.addView(pickBtn);
                return;
            }
            EditText textInput = new EditText(context);
            textInput.setText(w.text == null ? "" : w.text);
            if (w.kind.equals("button")) textInput.setHint(R.string.button_text);
            else textInput.setHint(R.string.text_html_allowed);
            advProps.addView(UiFields.wrap(context, textInput, null, 0));
            textInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    w.text = s.toString();
                    if ("@title".equals(w.id) && form.titleInput != null) {
                        form.titleInput.setText(s.toString());
                    } else if ("@message".equals(w.id) && form.msgInput != null) {
                        form.msgInput.setText(s.toString());
                    }
                    renderAdv[0].run();
                }

                public void afterTextChanged(Editable s) {
                }
            });
            EditText sizeInput = new EditText(context);
            sizeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            sizeInput.setText(String.valueOf(w.textSizeSp));
            advProps.addView(UiFields.wrap(context, sizeInput, "Text size sp", 0));
            sizeInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.textSizeSp = Float.parseFloat(s.toString());
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            if (w.kind.equals("input") || w.kind.equals("textarea")
                    || w.kind.equals("number") || w.kind.equals("search")) {
                EditText hintInput = new EditText(context);
                hintInput.setText(w.hint == null ? "" : w.hint);
                advProps.addView(UiFields.wrap(context, hintInput, "نص التلميح / Hint", 0));
                hintInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        w.hint = s.toString();
                        renderAdv[0].run();
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
            }
            if (w.kind.equals("spinner") || w.kind.equals("list")
                    || w.kind.equals("radio") || w.kind.equals("multiselect")) {
                EditText optionsInput = new EditText(context);
                optionsInput.setText(w.options == null ? "" : TextUtils.join("، ", w.options));
                advProps.addView(UiFields.wrap(context, optionsInput,
                        "الخيارات مفصولة بفاصلة", 0));
                optionsInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        String[] parts = s.toString().split("[,،\\n]");
                        List<String> options = new ArrayList<>();
                        for (String part : parts) {
                            String value = part.trim();
                            if (!value.isEmpty()) options.add(value);
                        }
                        w.options = options;
                        renderAdv[0].run();
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
            }
            if (w.kind.equals("checkbox") || w.kind.equals("toggle")) {
                MaterialSwitch checkedSwitch = new MaterialSwitch(context);
                checkedSwitch.setText("محدد / مفعل");
                checkedSwitch.setChecked(w.checked);
                checkedSwitch.setOnCheckedChangeListener((b, checked) -> {
                    w.checked = checked;
                    renderAdv[0].run();
                });
                advProps.addView(checkedSwitch);
            }
            LinearLayout fmtRow = new LinearLayout(context);
            fmtRow.setOrientation(LinearLayout.HORIZONTAL);
            advProps.addView(fmtRow);
            addFormatRow(fmtRow, textInput);
            attachFormatMenu(textInput);
            LinearLayout colorRow = new LinearLayout(context);
            colorRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton textColorBtn = new MaterialButton(context);
            textColorBtn.setText(w.textColor == 0 ? "لون النص" : String.format("النص #%08X", w.textColor));
            textColorBtn.setOnClickListener(x -> showColorWheel(
                    w.textColor == 0 ? 0xFF1B1B1F : w.textColor, (argb, hex) -> {
                        w.textColor = argb;
                        textColorBtn.setText("النص " + hex);
                        renderAdv[0].run();
                    }));
            textColorBtn.setOnLongClickListener(x -> {
                w.textColor = 0;
                textColorBtn.setText("لون النص");
                renderAdv[0].run();
                return true;
            });
            colorRow.addView(textColorBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton fontBtn = new MaterialButton(context);
            fontBtn.setText(widgetFontLabel(w));
            fontBtn.setOnClickListener(x -> showFontPicker(w, fontBtn));
            colorRow.addView(fontBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(colorRow);
            LinearLayout styleRow = new LinearLayout(context);
            styleRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton boldBtn = new MaterialButton(context);
            boldBtn.setText("B");
            final boolean[] isBold = {(w.fontStyle & 1) != 0};
            final boolean[] isItalic = {(w.fontStyle & 2) != 0};
            boldBtn.setAlpha(isBold[0] ? 1f : 0.55f);
            MaterialButton italicBtn = new MaterialButton(context);
            italicBtn.setText("I");
            italicBtn.setAlpha(isItalic[0] ? 1f : 0.55f);
            boldBtn.setOnClickListener(x -> {
                isBold[0] = !isBold[0];
                w.fontStyle = (isBold[0] ? 1 : 0) | (isItalic[0] ? 2 : 0);
                boldBtn.setAlpha(isBold[0] ? 1f : 0.55f);
                renderAdv[0].run();
            });
            italicBtn.setOnClickListener(x -> {
                isItalic[0] = !isItalic[0];
                w.fontStyle = (isBold[0] ? 1 : 0) | (isItalic[0] ? 2 : 0);
                italicBtn.setAlpha(isItalic[0] ? 1f : 0.55f);
                renderAdv[0].run();
            });
            styleRow.addView(boldBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            styleRow.addView(italicBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton inheritBtn = new MaterialButton(context);
            inheritBtn.setText(context.getString(R.string.dialog_font));
            inheritBtn.setOnClickListener(x -> {
                w.font = "";
                w.fontPath = "";
                fontBtn.setText(widgetFontLabel(w));
                renderAdv[0].run();
                Extensions.showMessage(context, "Using dialog font");
            });
            styleRow.addView(inheritBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
            advProps.addView(styleRow);
            if (w.kind.equals("button")) {
                LinearLayout btnBgRow = new LinearLayout(context);
                btnBgRow.setOrientation(LinearLayout.HORIZONTAL);
                MaterialButton bgBtn = new MaterialButton(context);
                bgBtn.setText(w.btnBg == 0 ? context.getString(R.string.bg_color_label) : context.getString(R.string.bg_format, shortHex(w.btnBg)));
                bgBtn.setOnClickListener(x -> showColorWheel(w.btnBg == 0 ? -7829368 : w.btnBg, (argb, hex) -> {
                    w.btnBg = argb;
                    bgBtn.setText(context.getString(R.string.bg_format, hex));
                    renderAdv[0].run();
                }));
                bgBtn.setOnLongClickListener(x -> {
                    w.btnBg = 0;
                    bgBtn.setText(context.getString(R.string.bg_color_label));
                    renderAdv[0].run();
                    return true;
                });
                btnBgRow.addView(bgBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton gradBtn = new MaterialButton(context);
                gradBtn.setText(w.btnBg2 == 0 ? context.getString(R.string.gradient_label) : context.getString(R.string.grad_format, shortHex(w.btnBg2)));
                gradBtn.setOnClickListener(x -> showColorWheel(w.btnBg2 == 0 ? -16776961 : w.btnBg2, (argb, hex) -> {
                    w.btnBg2 = argb;
                    gradBtn.setText(context.getString(R.string.grad_format, hex));
                    renderAdv[0].run();
                }));
                gradBtn.setOnLongClickListener(x -> {
                    w.btnBg2 = 0;
                    gradBtn.setText(context.getString(R.string.gradient_label));
                    renderAdv[0].run();
                    Extensions.showMessage(context, "Gradient cleared (long-press clears)");
                    return true;
                });
                btnBgRow.addView(gradBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                advProps.addView(btnBgRow);
                LinearLayout btnShapeRow = new LinearLayout(context);
                btnShapeRow.setOrientation(LinearLayout.HORIZONTAL);
                EditText cornerInput = new EditText(context);
                cornerInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                cornerInput.setText(w.btnCornerRadiusDp > 0 ? String.valueOf(w.btnCornerRadiusDp) : "");
                cornerInput.setHint("Corner dp");
                btnShapeRow.addView(UiFields.wrap(context, cornerInput, "Corner dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                cornerInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnCornerRadiusDp = s.length() == 0 ? 0 : Float.parseFloat(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                EditText padInput = new EditText(context);
                padInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                padInput.setText(w.btnPaddingDp > 0 ? String.valueOf(w.btnPaddingDp) : "");
                padInput.setHint("Pad dp");
                btnShapeRow.addView(UiFields.wrap(context, padInput, "Padding dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                padInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnPaddingDp = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                advProps.addView(btnShapeRow);
                LinearLayout btnBorderRow = new LinearLayout(context);
                btnBorderRow.setOrientation(LinearLayout.HORIZONTAL);
                EditText borderInput = new EditText(context);
                borderInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                borderInput.setText(w.btnBorderWidthDp > 0 ? String.valueOf(w.btnBorderWidthDp) : "");
                borderInput.setHint("Border dp");
                btnBorderRow.addView(UiFields.wrap(context, borderInput, "Border dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                borderInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnBorderWidthDp = s.length() == 0 ? 0 : Float.parseFloat(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                MaterialButton borderBtn = new MaterialButton(context);
                borderBtn.setText(w.btnBorderColor == -1 ? context.getString(R.string.border_color) : context.getString(R.string.border_format, shortHex(w.btnBorderColor)));
                borderBtn.setOnClickListener(x -> showColorWheel(
                        w.btnBorderColor == -1 ? -1 : w.btnBorderColor, (argb, hex) -> {
                            w.btnBorderColor = argb;
                            borderBtn.setText(context.getString(R.string.border_format, hex));
                            renderAdv[0].run();
                        }));
                borderBtn.setOnLongClickListener(x -> {
                    w.btnBorderColor = -1;
                    borderBtn.setText(context.getString(R.string.border_color));
                    renderAdv[0].run();
                    return true;
                });
                btnBorderRow.addView(borderBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                advProps.addView(btnBorderRow);
                MaterialSwitch animBtnSwitch = new MaterialSwitch(context);
                animBtnSwitch.setText(context.getString(R.string.animated_border));
                animBtnSwitch.setChecked(w.btnAnim);
                advProps.addView(animBtnSwitch);
                LinearLayout animBtnBox = new LinearLayout(context);
                animBtnBox.setOrientation(LinearLayout.VERTICAL);
                advProps.addView(animBtnBox);
                final Runnable[] renderBtnAnim = new Runnable[1];
                renderBtnAnim[0] = () -> {
                    animBtnBox.removeAllViews();
                    if (!w.btnAnim) return;
                    if (w.btnAnimColors == null) w.btnAnimColors = new ArrayList<>();
                    if (w.btnAnimColors.size() < 2) {
                        while (w.btnAnimColors.size() < 2) {
                            w.btnAnimColors.add(w.btnAnimColors.isEmpty() ? -16776961 : -65536);
                        }
                    }
                    LinearLayout chipRow = new LinearLayout(context);
                    chipRow.setOrientation(LinearLayout.HORIZONTAL);
                    animBtnBox.addView(chipRow);
                    for (int i = 0; i < w.btnAnimColors.size(); i++) {
                        final int idx = i;
                        int color = w.btnAnimColors.get(idx);
                        MaterialButton chip = new MaterialButton(context);
                        chip.setText("");
                        chip.setCornerRadius(dp(24));
                        try {
                            chip.setBackgroundTintList(ColorStateList.valueOf(color));
                        } catch (Exception ignored) {
                        }
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                        int m = dp(4);
                        lp.setMargins(m, 0, m, 0);
                        chip.setLayoutParams(lp);
                        chip.setOnClickListener(x -> showColorWheel(color, (argb, hex) -> {
                            w.btnAnimColors.set(idx, argb);
                            renderBtnAnim[0].run();
                            renderAdv[0].run();
                        }));
                        chip.setOnLongClickListener(x -> {
                            if (w.btnAnimColors.size() > 2) {
                                w.btnAnimColors.remove(idx);
                                renderBtnAnim[0].run();
                                renderAdv[0].run();
                            } else {
                                Extensions.showMessage(context, "Need at least 2 colors");
                            }
                            return true;
                        });
                        chipRow.addView(chip);
                    }
                    MaterialButton add = new MaterialButton(context);
                    add.setBackgroundResource(R.drawable.add_24px);
                    LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
                    int am = dp(4);
                    alp.setMargins(am, 0, am, 0);
                    add.setLayoutParams(alp);
                    add.setOnClickListener(x -> showColorWheel(-65536, (argb, hex) -> {
                        w.btnAnimColors.add(argb);
                        renderBtnAnim[0].run();
                        renderAdv[0].run();
                    }));
                    chipRow.addView(add);
                    EditText speedInput = new EditText(context);
                    speedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    if (w.btnAnimSpeedMs > 0) speedInput.setText(String.valueOf(w.btnAnimSpeedMs));
                    speedInput.setHint("Speed ms");
                    speedInput.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                        }

                        public void onTextChanged(CharSequence s, int a, int b, int c) {
                            try {
                                w.btnAnimSpeedMs = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                            } catch (Exception ignored) {
                            }
                        }

                        public void afterTextChanged(Editable s) {
                        }
                    });
                    animBtnBox.addView(UiFields.wrap(context, speedInput, "Animation speed ms", 0));
                    MaterialSwitch rainbowBtnSwitch = new MaterialSwitch(context);
                    rainbowBtnSwitch.setText(context.getString(R.string.rainbow_rgb_mode));
                    rainbowBtnSwitch.setChecked(w.btnAnimRainbow);
                    rainbowBtnSwitch.setOnCheckedChangeListener((b, c) -> w.btnAnimRainbow = c);
                    animBtnBox.addView(rainbowBtnSwitch);
                };
                animBtnSwitch.setOnCheckedChangeListener((b, c) -> {
                    w.btnAnim = c;
                    if (c && (w.btnAnimColors == null || w.btnAnimColors.isEmpty())) {
                        w.btnAnimColors = new ArrayList<>(Arrays.asList(-16776961, -65536));
                    }
                    renderBtnAnim[0].run();
                    renderAdv[0].run();
                });
                renderBtnAnim[0].run();
                MaterialAutoCompleteTextView actionTv = new MaterialAutoCompleteTextView(context);
                String dismiss = context.rss.getString(R.string.dismiss);
                String openUrl = context.rss.getString(R.string.open_url);
                actionTv.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_dropdown_item_1line, new String[]{dismiss, openUrl}));
                actionTv.setText(w.btnAction == null ? dismiss : w.btnAction, false);
                actionTv.setInputType(InputType.TYPE_NULL);
                actionTv.setCursorVisible(false);
                actionTv.setOnClickListener(vv -> actionTv.showDropDown());
                TextInputLayout actionBox = UiFields.box(context, context.rss.getString(R.string.tap_action));
                actionBox.addView(actionTv);
                advProps.addView(actionBox);
                EditText urlInput = new EditText(context);
                urlInput.setText(w.url == null ? "" : w.url);
                View urlBox = UiFields.wrap(context, urlInput, context.rss.getString(R.string.url_for_open_url), 0);
                advProps.addView(urlBox);
                Runnable syncUrlBox = () -> urlBox.setVisibility(
                        actionTv.getText() != null && actionTv.getText().toString().equals(openUrl)
                                ? View.VISIBLE : View.GONE);
                actionTv.setOnItemClickListener((p, vv, pos, id) -> {
                    w.btnAction = pos == 1 ? openUrl : dismiss;
                    syncUrlBox.run();
                });
                syncUrlBox.run();
                urlInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        w.url = s.toString();
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
            }
        };

        final MaterialButton advUndoBtn = view.findViewById(R.id.advUndoBtn);
        final MaterialButton advRedoBtn = view.findViewById(R.id.advRedoBtn);
        final MaterialAutoCompleteTextView advLayers = view.findViewById(R.id.advLayers);
        final MaterialAutoCompleteTextView advAlign = view.findViewById(R.id.advAlign);
        final MaterialAutoCompleteTextView advTheme = view.findViewById(R.id.advTheme);
        final MaterialSwitch advGridSwitch = view.findViewById(R.id.advGridSwitch);
        renderAdv[0] = () -> {
            try {
            final int renderToken = ++advRenderToken[0];
            advCanvas.removeAllViews();
            ensureChromeWidgets(advWidgets, form);
            boolean richText = form.dlgHtml != null && form.dlgHtml.isChecked();
            for (OverlayInjectorUtil.AdvWidget w : new ArrayList<>(advWidgets)) {
                View vw = previewWidget(w, richText, form);
                if (w == advSelected[0]) {
                    vw.setAlpha(0.9f);
                    vw.setElevation(dp(6));
                    GradientDrawable outline = new GradientDrawable();
                    outline.setCornerRadius(dp(12));
                    outline.setStroke(dp(2), 0xFF3B6CF6);
                    vw.setForeground(outline);
                } else {
                    vw.setForeground(null);
                }
                vw.setTag(w);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = (int) (w.leftDp * advDensity);
                lp.topMargin = (int) (w.topDp * advDensity);
                if (w.widthDp > 0) lp.width = (int) (w.widthDp * advDensity);
                else if (w.widthPercent > 0 && w.widthPercent < 100) {
                    int cw = advCanvas.getWidth();
                    if (cw > 0) lp.width = Math.max(1, (int) (cw * w.widthPercent / 100f));
                } else if (w.widthPercent >= 100) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                if (w.heightDp > 0) lp.height = (int) (w.heightDp * advDensity);
                vw.setLayoutParams(lp);
                vw.setOnTouchListener(new View.OnTouchListener() {
                    float lastX;
                    float lastY;
                    boolean moved;
                    boolean dragging;
                    int snapLockX = Integer.MIN_VALUE;
                    int snapLockY = Integer.MIN_VALUE;

                    public boolean onTouch(View v, MotionEvent event) {
                        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                pushAdvHistory.run();
                                lastX = event.getRawX();
                                lastY = event.getRawY();
                                moved = false;
                                dragging = true;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                return true;
                            case MotionEvent.ACTION_MOVE: {
                                if (!dragging) return false;
                                float dx = event.getRawX() - lastX;
                                float dy = event.getRawY() - lastY;
                                if (!moved && Math.hypot(dx, dy) < 6) return true;
                                moved = true;
                                int rawL = Math.max(0, (int) (p.leftMargin + dx));
                                int rawT = Math.max(0, (int) (p.topMargin + dy));
                                int nl = rawL;
                                int nt = rawT;
                                if (form.advSnapSwitch != null && form.advSnapSwitch.isChecked()) {
                                    int threshPx = (int) (8 * advDensity + 0.5f);
                                    int slackPx = (int) (4 * advDensity + 0.5f);
                                    int[] cand = snapWidget(w, v, rawL, rawT, advCanvas, advDensity);
                                    if (snapLockX != Integer.MIN_VALUE
                                            && Math.abs(rawL - snapLockX) <= threshPx + slackPx) {
                                        nl = snapLockX;
                                    } else {
                                        nl = cand[0];
                                        snapLockX = (cand[0] != rawL) ? cand[0] : Integer.MIN_VALUE;
                                    }
                                    if (snapLockY != Integer.MIN_VALUE
                                            && Math.abs(rawT - snapLockY) <= threshPx + slackPx) {
                                        nt = snapLockY;
                                    } else {
                                        nt = cand[1];
                                        snapLockY = (cand[1] != rawT) ? cand[1] : Integer.MIN_VALUE;
                                    }
                                } else {
                                    snapLockX = Integer.MIN_VALUE;
                                    snapLockY = Integer.MIN_VALUE;
                                }
                                p.leftMargin = nl;
                                p.topMargin = nt;
                                v.setLayoutParams(p);
                                w.leftDp = Math.round(nl / advDensity);
                                w.topDp = Math.round(nt / advDensity);
                                lastX = event.getRawX();
                                lastY = event.getRawY();
                                return true;
                            }
                            case MotionEvent.ACTION_UP:
                                dragging = false;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                                if (!moved) {
                                    advSelected[0] = w;
                                    String action = w.btnAction == null ? "" : w.btnAction;
                                    if (dialogStudio != null) {
                                        dialogStudio.previewAction(w, action);
                                    }
                                    renderAdv[0].run();
                                    showAdvProps[0].run();
                                }
                                if (!moved) {
                                    advSelected[0] = w;
                                    renderAdv[0].run();
                                    showAdvProps[0].run();
                                }
                                return true;
                            case MotionEvent.ACTION_CANCEL:
                                dragging = false;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                                return true;
                        }
                        return false;
                    }
                });
                advCanvas.addView(vw);
                if (w == advSelected[0]) {
                    advCanvas.post(() -> {
                        if (renderToken != advRenderToken[0] || advSelected[0] != w) return;
                        final View handle = new View(context);
                        GradientDrawable hb = new GradientDrawable();
                        hb.setShape(GradientDrawable.OVAL);
                        hb.setColor(0xFF3B6CF6);
                        hb.setStroke(dp(2), 0xFFFFFFFF);
                        handle.setBackground(hb);
                        handle.setElevation(dp(8));
                        final int baseLeft = vw.getLeft() - advCanvas.getPaddingLeft();
                        final int baseTop = vw.getTop() - advCanvas.getPaddingTop();
                        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(22), dp(22));
                        hp.leftMargin = baseLeft + vw.getWidth() - dp(11);
                        hp.topMargin = baseTop + vw.getHeight() - dp(11);
                        handle.setLayoutParams(hp);
                        handle.setOnTouchListener((hv, event) -> {
                            FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) vw.getLayoutParams();
                            switch (event.getActionMasked()) {
                                case MotionEvent.ACTION_DOWN:
                                    pushAdvHistory.run();
                                    hv.setTag(new float[]{event.getRawX(), event.getRawY(),
                                            vp.width > 0 ? vp.width : vw.getWidth(),
                                            vp.height > 0 ? vp.height : vw.getHeight()});
                                    if (hv.getParent() != null) {
                                        hv.getParent().requestDisallowInterceptTouchEvent(true);
                                    }
                                    return true;
                                case MotionEvent.ACTION_MOVE: {
                                    float[] start = (float[]) hv.getTag();
                                    if (start == null) return true;
                                    int nw = Math.max(dp(28),
                                            (int) (start[2] + (event.getRawX() - start[0])));
                                    int nh = Math.max(dp(20),
                                            (int) (start[3] + (event.getRawY() - start[1])));
                                    vp.width = nw;
                                    vp.height = nh;
                                    vw.setLayoutParams(vp);
                                    w.widthPercent = 0;
                                    w.widthDp = Math.max(1, Math.round(nw / advDensity));
                                    w.heightDp = Math.max(1, Math.round(nh / advDensity));
                                    FrameLayout.LayoutParams hlp =
                                            (FrameLayout.LayoutParams) handle.getLayoutParams();
                                    hlp.leftMargin = baseLeft + nw - dp(11);
                                    hlp.topMargin = baseTop + nh - dp(11);
                                    handle.setLayoutParams(hlp);
                                    return true;
                                }
                                case MotionEvent.ACTION_UP:
                                case MotionEvent.ACTION_CANCEL:
                                    if (hv.getParent() != null) {
                                        hv.getParent().requestDisallowInterceptTouchEvent(false);
                                    }
                                    renderAdv[0].run();
                                    showAdvProps[0].run();
                                    return true;
                            }
                            return false;
                        });
                        advCanvas.addView(handle);
                    });
                }
            }
            refreshLayers(advLayers, advWidgets, advSelected[0]);
            advUndoBtn.setEnabled(!advUndo.isEmpty());
            if (advRedoBtn != null) advRedoBtn.setEnabled(!advRedo.isEmpty());
            if (advCanvas.getWidth() == 0) {
                advCanvas.post(() -> {
                    if (renderToken == advRenderToken[0] && advCanvas.getWidth() > 0) {
                        renderAdv[0].run();
                    }
                });
            }
            } catch (Exception e) {
                advCanvas.removeAllViews();
                TextView error = new TextView(context);
                error.setText("تعذر عرض المعاينة: " + e.getMessage());
                error.setTextSize(13);
                error.setPadding(dp(12), dp(12), dp(12), dp(12));
                advCanvas.addView(error);
                advUndoBtn.setEnabled(!advUndo.isEmpty());
                if (advRedoBtn != null) advRedoBtn.setEnabled(!advRedo.isEmpty());
            }
        };
        if (advProps != null) {
            advProps.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
                if (newFocus != null && isInsideView(newFocus, advProps)) pushAdvHistory.run();
            });
        }
        advUndoBtn.setOnClickListener(v -> {
            if (advUndo.isEmpty()) return;
            advRedo.push(advSnapshot.get());
            while (advRedo.size() > 40) advRedo.removeLast();
            advRestore.accept(advUndo.pop());
        });
        if (advRedoBtn != null) {
            advRedoBtn.setOnClickListener(v -> {
                if (advRedo.isEmpty()) return;
                advUndo.push(advSnapshot.get());
                while (advUndo.size() > 40) advUndo.removeLast();
                advRestore.accept(advRedo.pop());
            });
        }

        if (advGridSwitch != null) {
            advGridSwitch.setOnCheckedChangeListener((button, checked) ->
                    advCanvas.setBackground(checked ? buildGridDrawable() : null));
        }
        if (advAlign != null) {
            String[] alignLabels = {"يسار", "وسط أفقي", "يمين", "أعلى", "وسط رأسي", "أسفل"};
            advAlign.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, alignLabels));
            advAlign.setOnItemClickListener((parent, v, position, id) -> {
                OverlayInjectorUtil.AdvWidget w = advSelected[0];
                if (w == null) {
                    Extensions.showMessage(context, "اختر عنصرًا أولًا");
                    advAlign.setText("", false);
                    return;
                }
                pushAdvHistory.run();
                View widgetView = advCanvas.findViewWithTag(w);
                int cw = Math.max(1, advCanvas.getWidth()
                        - advCanvas.getPaddingLeft() - advCanvas.getPaddingRight());
                int ch = Math.max(1, advCanvas.getHeight()
                        - advCanvas.getPaddingTop() - advCanvas.getPaddingBottom());
                int ww = widgetView != null && widgetView.getWidth() > 0
                        ? widgetView.getWidth()
                        : Math.round((w.widthPercent >= 100
                        ? cw : (w.widthDp > 0 ? w.widthDp : 120) * advDensity));
                int hh = widgetView != null && widgetView.getHeight() > 0
                        ? widgetView.getHeight()
                        : Math.round((w.heightDp > 0 ? w.heightDp : 44) * advDensity);
                switch (position) {
                    case 0: w.leftDp = 0; break;
                    case 1: w.leftDp = Math.max(0, Math.round((cw - ww) / 2f / advDensity)); break;
                    case 2: w.leftDp = Math.max(0, Math.round((cw - ww) / advDensity)); break;
                    case 3: w.topDp = 0; break;
                    case 4: w.topDp = Math.max(0, Math.round((ch - hh) / 2f / advDensity)); break;
                    case 5: w.topDp = Math.max(0, Math.round((ch - hh) / advDensity)); break;
                }
                renderAdv[0].run();
                showAdvProps[0].run();
                advAlign.setText("", false);
            });
        }
        if (advLayers != null) {
            advLayers.setOnItemClickListener((parent, v, position, id) -> {
                if (position < 0 || position >= advWidgets.size()) return;
                advSelected[0] = advWidgets.get(position);
                renderAdv[0].run();
                showAdvProps[0].run();
            });
        }
        view.findViewById(R.id.advAddText).setOnClickListener(v -> {
            pushAdvHistory.run();
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "text";
            w.text = context.rss.getString(R.string.text);
            w.textSizeSp = 16f;
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
        });
        view.findViewById(R.id.advAddImage).setOnClickListener(v -> {
            pushAdvHistory.run();
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "image";
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
            pickImage((thumb, b64) -> {
                w.imageB64 = b64;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
        });
        view.findViewById(R.id.advAddButton).setOnClickListener(v -> {
            pushAdvHistory.run();
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "button";
            w.text = "Button";
            w.textSizeSp = 14f;
            w.btnAction = "Dismiss";
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
        });
        MaterialAutoCompleteTextView addKind = view.findViewById(R.id.advAddKind);
        if (addKind != null) {
            String[] addLabels = {"نص", "عنوان", "وصف", "زر", "صورة", "أيقونة",
                    "حقل إدخال", "مربع اختيار", "مفتاح تبديل", "قائمة منسدلة",
                    "شريط تقدم", "فاصل", "بطاقة"};
            String[] addKinds = {"text", "title", "message", "button", "image", "icon",
                    "input", "checkbox", "toggle", "spinner", "progress", "divider", "card"};
            addKind.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, addLabels));
            addKind.setText(addLabels[0], false);
            View addComponent = view.findViewById(R.id.advAddComponent);
            if (addComponent != null) addComponent.setOnClickListener(v -> {
                String label = addKind.getText() == null ? "" : addKind.getText().toString();
                int kindIndex = 0;
                for (int i = 0; i < addLabels.length; i++) {
                    if (addLabels[i].equals(label)) {
                        kindIndex = i;
                        break;
                    }
                }
                pushAdvHistory.run();
                OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
                w.kind = addKinds[kindIndex];
                w.sourceKind = w.kind;
                w.leftDp = 12;
                w.topDp = 118 + 26 * Math.max(0, advWidgets.size() - 4);
                w.widthPercent = 100;
                w.textSizeSp = 15f;
                switch (w.kind) {
                    case "title":
                        w.text = "عنوان جديد";
                        w.textSizeSp = 20f;
                        w.fontStyle = 1;
                        break;
                    case "message":
                        w.text = "وصف جديد";
                        break;
                    case "button":
                        w.text = "زر جديد";
                        w.textSizeSp = 14f;
                        w.widthDp = 120;
                        w.heightDp = 44;
                        w.btnBg = 0xFF3B6CF6;
                        w.btnBg2 = 0xFF2F5BEA;
                        w.btnCornerRadiusDp = 22f;
                        w.btnPaddingDp = 12;
                        w.textColor = 0xFFFFFFFF;
                        w.btnAction = "Dismiss";
                        break;
                    case "image":
                        w.widthDp = 180;
                        w.heightDp = 90;
                        break;
                    case "icon":
                        w.text = "★";
                        w.textSizeSp = 28f;
                        w.widthDp = 48;
                        w.heightDp = 48;
                        break;
                    case "input":
                        w.hint = "أدخل النص";
                        w.heightDp = 44;
                        break;
                    case "checkbox":
                        w.text = "خيار";
                        w.checked = true;
                        break;
                    case "toggle":
                        w.text = "تفعيل";
                        w.checked = true;
                        break;
                    case "spinner":
                        w.options = new ArrayList<>(Arrays.asList("الخيار 1", "الخيار 2", "الخيار 3"));
                        break;
                    case "progress":
                        w.heightDp = 18;
                        break;
                    case "divider":
                        w.heightDp = 1;
                        break;
                    case "card":
                        w.text = "بطاقة جديدة";
                        w.heightDp = 64;
                        break;
                    default:
                        w.text = "نص جديد";
                        break;
                }
                advWidgets.add(w);
                advSelected[0] = w;
                renderAdv[0].run();
                showAdvProps[0].run();
                if ("image".equals(w.kind)) {
                    pickImage((thumb, b64) -> {
                        w.imageB64 = b64;
                        renderAdv[0].run();
                        showAdvProps[0].run();
                    });
                }
            });
        }
        advCanvas.setOnClickListener(v -> {
            advSelected[0] = null;
            renderAdv[0].run();
            showAdvProps[0].run();
        });
        renderAdv[0].run();
        showAdvProps[0].run();

        LinearLayout toastSection = view.findViewById(R.id.overlayToastSection);
        TextInputEditText messageInput = view.findViewById(R.id.overlayToastMessage);
        MaterialAutoCompleteTextView durationTv = view.findViewById(R.id.overlayToastDuration);
        durationTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                new String[]{context.rss.getString(R.string.longs), context.rss.getString(R.string.shorts)}));
        MaterialAutoCompleteTextView gravityTv = view.findViewById(R.id.overlayToastGravity);
        gravityTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                new String[]{context.rss.getString(R.string.def), context.rss.getString(R.string.bottom), context.rss.getString(R.string.center), context.rss.getString(R.string.top)}));
        TextInputEditText xInput = view.findViewById(R.id.overlayToastX);
        TextInputEditText yInput = view.findViewById(R.id.overlayToastY);
        MaterialSwitch toastHtmlSwitch = view.findViewById(R.id.overlayToastHtml);
        MaterialSwitch toastB64Switch = view.findViewById(R.id.overlayToastB64);
        TextView toastPreview = view.findViewById(R.id.overlayToastPreview);
        addFormatRow(view.findViewById(R.id.toastFormatRow), messageInput);

        LinearLayout dialogSection = view.findViewById(R.id.overlayDialogSection);
        final LinearLayout dialogSection2 = view.findViewById(R.id.overlayDialogSection2);
        final LinearLayout styleSection = view.findViewById(R.id.overlayStyleSection);
        TextInputEditText dlgTitleInput = view.findViewById(R.id.overlayDlgTitle);
        TextInputEditText dlgMsgInput = view.findViewById(R.id.overlayDlgMessage);
        TextInputEditText dlgPosInput = view.findViewById(R.id.overlayDlgPos);
        MaterialAutoCompleteTextView dlgPosAction = view.findViewById(R.id.overlayDlgPosAction);
        TextInputEditText dlgPosUrl = view.findViewById(R.id.overlayDlgPosUrl);
        TextInputEditText dlgNegInput = view.findViewById(R.id.overlayDlgNeg);
        MaterialAutoCompleteTextView dlgNegAction = view.findViewById(R.id.overlayDlgNegAction);
        TextInputEditText dlgNegUrl = view.findViewById(R.id.overlayDlgNegUrl);
        TextInputEditText dlgNeuInput = view.findViewById(R.id.overlayDlgNeu);
        MaterialAutoCompleteTextView dlgNeuAction = view.findViewById(R.id.overlayDlgNeuAction);
        TextInputEditText dlgNeuUrl = view.findViewById(R.id.overlayDlgNeuUrl);
        MaterialSwitch cancelSwitch = view.findViewById(R.id.overlayDlgCancelable);
        MaterialSwitch dlgHtmlSwitch = view.findViewById(R.id.overlayDlgHtml);
        MaterialSwitch dlgB64Switch = view.findViewById(R.id.overlayDlgB64);
        TextView dlgPreviewTitle = view.findViewById(R.id.overlayDlgPreviewTitle);
        TextView dlgPreviewMsg = view.findViewById(R.id.overlayDlgPreviewMsg);
        String[] buttonActions = {context.rss.getString(R.string.dismiss), context.rss.getString(R.string.open_url)};
        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, buttonActions);
        dlgPosAction.setAdapter(actionAdapter);
        dlgNegAction.setAdapter(actionAdapter);
        dlgNeuAction.setAdapter(actionAdapter);
        addFormatRow(view.findViewById(R.id.dlgFormatRow), dlgMsgInput);
        attachFormatMenu(messageInput);
        attachFormatMenu(dlgMsgInput);
        attachFormatMenu(dlgTitleInput);

        Runnable updatePreview = () -> {
            try {
                if (toastSection.getVisibility() == View.VISIBLE) {
                    String text = messageInput.getText() == null ? "" : messageInput.getText().toString();
                    if (toastHtmlSwitch.isChecked()) {
                        toastPreview.setText(HtmlCompat.fromHtml(text,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                    } else {
                        toastPreview.setText(text);
                    }
                } else {
                    String title = dlgTitleInput.getText() == null ? "" : dlgTitleInput.getText().toString();
                    String msg = dlgMsgInput.getText() == null ? "" : dlgMsgInput.getText().toString();
                    if (dlgHtmlSwitch.isChecked()) {
                        dlgPreviewTitle.setText(HtmlCompat.fromHtml(title,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                        dlgPreviewMsg.setText(HtmlCompat.fromHtml(msg,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                    } else {
                        dlgPreviewTitle.setText(title);
                        dlgPreviewMsg.setText(msg);
                    }
                    Typeface dlgTf = loadTypeface(form.dlgFont, form.dlgFontPath);
                    try {
                        if (dlgTf != null) {
                            dlgPreviewTitle.setTypeface(dlgTf, Typeface.BOLD);
                            dlgPreviewMsg.setTypeface(dlgTf);
                        } else {
                            dlgPreviewTitle.setTypeface(null, Typeface.BOLD);
                            dlgPreviewMsg.setTypeface(null);
                        }
                    } catch (Exception ignored) {
                    }
                    updateStylePreview(form);
                    updatePreviewImage(form);
                    if (advancedSection.getVisibility() == View.VISIBLE) {
                        renderAdv[0].run();
                    }
                }
            } catch (Exception ignored) {
            }
        };
        if (advTheme != null) {
            String[] themeLabels = {"زجاج أزرق", "فاتح ناعم", "داكن", "نيون", "بلا خلفية"};
            advTheme.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, themeLabels));
            advTheme.setOnItemClickListener((parent, v, position, id) -> {
                pushAdvHistory.run();
                applyDialogTheme(form, position, advWidgets);
                updatePreview.run();
                renderAdv[0].run();
                showAdvProps[0].run();
                advTheme.setText("", false);
            });
        }
        TextWatcher previewWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updatePreview.run();
            }

            public void afterTextChanged(Editable s) {
            }
        };
        messageInput.addTextChangedListener(previewWatcher);
        dlgTitleInput.addTextChangedListener(previewWatcher);
        dlgMsgInput.addTextChangedListener(previewWatcher);
        toastHtmlSwitch.setOnCheckedChangeListener((b, c) -> updatePreview.run());
        dlgHtmlSwitch.setOnCheckedChangeListener((b, c) -> {
            updatePreview.run();
            renderAdv[0].run();
        });
        MaterialAutoCompleteTextView profileTv = view.findViewById(R.id.overlayProfileTv);
        refreshProfiles(form, true);
        Runnable syncModeSections = () -> {
            String mode = modeTv.getText() == null ? toast : modeTv.getText().toString();
            boolean isToast = !dialog.equals(mode) && !adv.equals(mode);
            boolean isAdvanced = adv.equals(mode);
            boolean isDialog = dialog.equals(mode);
            toastSection.setVisibility(isToast ? View.VISIBLE : View.GONE);
            dialogSection.setVisibility(isDialog ? View.VISIBLE : View.GONE);
            if (dialogSection2 != null) dialogSection2.setVisibility(isDialog ? View.VISIBLE : View.GONE);
            if (styleSection != null) styleSection.setVisibility(isToast ? View.GONE : View.VISIBLE);
            advancedSection.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            View studioPanel = view.findViewById(R.id.dialogStudioPanel);
            if (studioPanel != null) studioPanel.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
        };
        modeTv.setOnItemClickListener((p, v, pos, id) -> {
            profileTv.setText("", false);
            refreshProfiles(form, pos == 0);
            syncModeSections.run();
            updatePreview.run();
        });
        syncModeSections.run();
        setupStyleControls(view, form, updatePreview);
        activeAdvRender = renderAdv[0];
        bindUrlVisibility(form);
        refreshFontLabels(form);
        refreshAdvFontLabels(form);
        if (form.dlgFontBtn != null) {
            form.dlgFontBtn.setOnClickListener(v -> showFontPickerDialog((font, path) -> {
                form.dlgFont = font;
                form.dlgFontPath = path;
                refreshFontLabels(form);
                updatePreview.run();
            }, form.dlgFont, form.dlgFontPath, form.dlgFontBtn));
        }
        if (form.advFontBtn != null) {
            form.advFontBtn.setOnClickListener(v -> showFontPickerDialog((font, path) -> {
                form.advFont = font;
                form.advFontPath = path;
                refreshAdvFontLabels(form);
                renderAdv[0].run();
            }, form.advFont, form.advFontPath, form.advFontBtn));
        }
        if (form.advFontApplyAllBtn != null) {
            form.advFontApplyAllBtn.setOnClickListener(v -> {
                for (OverlayInjectorUtil.AdvWidget w : advWidgets) {
                    if (w == null || w.kind.equals("image")) continue;
                    w.font = form.advFont == null ? "" : form.advFont;
                    w.fontPath = form.advFontPath == null ? "" : form.advFontPath;
                }
                renderAdv[0].run();
                showAdvProps[0].run();
                Extensions.showMessage(context, R.string.dialog_font_applied_to_all_widgets);
            });
        }
        updatePreview.run();
        profileTv.setOnItemClickListener((p, v, pos, id) -> {
            String name = profileTv.getText() == null ? "" : profileTv.getText().toString();
            if (name.isEmpty()) return;
            if (toastSection.getVisibility() == View.VISIBLE) {
                OverlayInjectorUtil.ToastOptions opts = OverlayProfiles.getToast(context, name);
                if (opts != null) {
                    applyToast(form, opts);
                    updatePreview.run();
                }
            } else {
                OverlayInjectorUtil.DialogOptions opts = OverlayProfiles.getDialog(context, name);
                if (opts != null) {
                    if (advancedSection.getVisibility() == View.VISIBLE) {
                        applyAdvanced(form, advWidgets, advSelected, opts);
                        renderAdv[0].run();
                        showAdvProps[0].run();
                    } else {
                        applyDialog(form, opts);
                    }
                    updatePreview.run();
                }
            }
        });
        view.findViewById(R.id.overlayProfileSave).setOnClickListener(v -> {
            boolean isToast = toastSection.getVisibility() == View.VISIBLE;
            boolean isAdvanced = advancedSection.getVisibility() == View.VISIBLE;
            EditText nameInput = new EditText(context);
            nameInput.setSingleLine(true);
            String current = profileTv.getText() == null ? "" : profileTv.getText().toString();
            nameInput.setText(current);
            dialogUtil.getDialogBuilder()
                    .setTitle(R.string.save_profile)
                    .setView(UiFields.wrap(context, nameInput, context.rss.getString(R.string.profile_name), 16))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String profileName = nameInput.getText() == null ? ""
                                : nameInput.getText().toString().trim();
                        if (profileName.isEmpty()) {
                            Extensions.showMessage(context, R.string.enter_a_name);
                            return;
                        }
                        if (isToast) {
                            OverlayInjectorUtil.ToastOptions opts = collectToast(form);
                            if (opts == null) return;
                            OverlayProfiles.putToast(context, profileName, opts);
                        } else if (isAdvanced) {
                            OverlayInjectorUtil.DialogOptions opts = collectAdvanced(form, advWidgets);
                            if (opts == null) return;
                            OverlayProfiles.putDialog(context, profileName, opts);
                        } else {
                            OverlayInjectorUtil.DialogOptions opts = collectDialog(form);
                            if (opts == null) return;
                            OverlayProfiles.putDialog(context, profileName, opts);
                        }
                        refreshProfiles(form, isToast);
                        profileTv.setText(profileName, false);
                        Extensions.showMessage(context, R.string.profile_saved);
                    }).show();
        });
        view.findViewById(R.id.overlayProfileDelete).setOnClickListener(v -> {
            String name = profileTv.getText() == null ? "" : profileTv.getText().toString();
            if (name.isEmpty()) return;
            boolean isToast = toastSection.getVisibility() == View.VISIBLE;
            if (isToast) OverlayProfiles.removeToast(context, name);
            else OverlayProfiles.removeDialog(context, name);
            profileTv.setText("", false);
            refreshProfiles(form, isToast);
        });

        overlayImageBase64 = null;
        overlayVideoPath = null;
        overlayImagePreviewView = view.findViewById(R.id.overlayDlgImagePreview);
        overlayImagePreviewView.setVisibility(View.GONE);
        overlayImagePreviewView.setImageBitmap(null);
        String alwaysShowLabel = context.rss.getString(R.string.always_show);
        String[] showModes = {
                alwaysShowLabel,
                context.rss.getString(R.string.show_once),
                context.rss.getString(R.string.don_t_show_again_button)
        };
        if (form.showModeTv != null) {
            form.showModeTv.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, showModes));
            form.showModeTv.setText(alwaysShowLabel, false);
        }
        String mediaNoneLabel = context.rss.getString(R.string.media_none);
        String mediaPicLabel = context.rss.getString(R.string.media_picture);
        String mediaVidLabel = context.rss.getString(R.string.media_video);
        String[] mediaModes = {mediaNoneLabel, mediaPicLabel, mediaVidLabel};
        if (form.mediaTv != null) {
            form.mediaTv.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, mediaModes));
            form.mediaTv.setText(mediaNoneLabel, false);
            form.mediaTv.setOnItemClickListener((p, v, pos, id) -> {
                if (pos == 0) {
                    overlayImageBase64 = null;
                    overlayVideoPath = null;
                } else if (pos == 1) {
                    overlayVideoPath = null;
                } else {
                    overlayImageBase64 = null;
                }
                syncMediaUi(form);
                updatePreview.run();
            });
        }
        syncMediaUi(form);
        if (form.pickMediaBtn != null) {
            form.pickMediaBtn.setOnClickListener(v -> {
                String sel = textOf(form.mediaTv);
                if (mediaVidLabel.equals(sel)) {
                    pickVideoFile(path -> {
                        overlayVideoPath = path;
                        overlayImageBase64 = null;
                        syncMediaUi(form);
                        updatePreview.run();
                    });
                } else {
                    pickImage((thumb, b64) -> {
                        overlayImageBase64 = b64;
                        overlayVideoPath = null;
                        if (overlayImagePreviewView != null) {
                            overlayImagePreviewView.setImageBitmap(thumb);
                            overlayImagePreviewView.setVisibility(View.VISIBLE);
                        }
                        syncMediaUi(form);
                        updatePreview.run();
                    });
                }
            });
        }

        LinearLayout posBox = view.findViewById(R.id.overlayPosBox);
        LinearLayout negBox = view.findViewById(R.id.overlayNegBox);
        LinearLayout neuBox = view.findViewById(R.id.overlayNeuBox);
        view.findViewById(R.id.overlayPosAddBtn).setOnClickListener(v -> {
            posBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayNegAddBtn).setOnClickListener(v -> {
            negBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayNeuAddBtn).setOnClickListener(v -> {
            neuBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayPosRemoveBtn).setOnClickListener(v -> {
            posBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayPosAddBtn).setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.overlayNegRemoveBtn).setOnClickListener(v -> {
            negBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayNegAddBtn).setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.overlayNeuRemoveBtn).setOnClickListener(v -> {
            neuBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayNeuAddBtn).setVisibility(View.VISIBLE);
        });

        MaterialCheckBox autosign = view.findViewById(R.id.overlayAutosign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        view.findViewById(R.id.overlaySignSettings).setOnClickListener(uiHelper.showSignSettingsDialog());
        dialogStudio = new DialogStudioController(context, new DialogStudioController.Host() {
            @Override public List<OverlayInjectorUtil.AdvWidget> widgets() { return advWidgets; }
            @Override public void refreshCanvas() { if (renderAdv[0] != null) renderAdv[0].run(); }
            @Override public void status(String message) { }
            @Override public void inject() {
                if (activeOverlayDialog != null) {
                    activeOverlayDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                }
            }
        });
        dialogStudio.attach(view, null);
        syncModeSections.run();
        activeOverlayForm = form;

        AlertDialog overlayDialog = dialogUtil.getDialogBuilder()
                .setTitle(R.string.toast_dialog)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    OverlayInjectorUtil.ToastOptions to = null;
                    OverlayInjectorUtil.DialogOptions dlg = null;
                    if (advancedSection.getVisibility() == View.VISIBLE) {
                        dlg = collectAdvanced(form, advWidgets);
                        if (dlg == null) return;
                    } else if (toastSection.getVisibility() == View.VISIBLE) {
                        to = collectToast(form);
                        if (to == null) return;
                    } else {
                        dlg = collectDialog(form);
                        if (dlg == null) return;
                    }
                    runAddOverlay(file, selectedActivities, to, dlg, sign[0]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        activeOverlayDialog = overlayDialog;
        overlayDialog.setOnDismissListener(d -> {
            activeOverlayDialog = null;
            activeOverlayForm = null;
            activeAdvRender = null;
            overlayImageBase64 = null;
            overlayVideoPath = null;
        });
        overlayDialog.show();
    }

    private void syncUrlBox(MaterialAutoCompleteTextView actionTv, TextInputEditText urlInput) {
        if (actionTv == null || urlInput == null || !(urlInput.getParent() instanceof View)) return;
        boolean open = actionTv.getText() != null && actionTv.getText().toString().equals(context.rss.getString(R.string.open_url));
        ((View) urlInput.getParent()).setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private void syncUrlBoxes(OverlayForm f) {
        if (f == null) return;
        syncUrlBox(f.posAction, f.posUrl);
        syncUrlBox(f.negAction, f.negUrl);
        syncUrlBox(f.neuAction, f.neuUrl);
    }

    private void bindUrlVisibility(OverlayForm f) {
        MaterialAutoCompleteTextView[] actions = {f.posAction, f.negAction, f.neuAction};
        for (MaterialAutoCompleteTextView actionTv : actions) {
            if (actionTv == null) continue;
            actionTv.setOnItemClickListener((p, v, pos, id) -> syncUrlBoxes(f));
        }
        syncUrlBoxes(f);
    }

    private String actionUrl(MaterialAutoCompleteTextView actionTv, TextInputEditText urlInput, boolean[] badUrl) {
        try {
            if (actionTv.getText() != null && actionTv.getText().toString().equals(context.rss.getString(R.string.open_url))) {
                String url = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
                if (url.isEmpty() || url.equals("https://") || url.equals("http://")) {
                    Extensions.showMessage(context, R.string.enter_a_url_or_set_the_button_to_dismiss);
                    badUrl[0] = true;
                    return null;
                }
                return url;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void addFormatRow(LinearLayout row, EditText target) {
        String[][] buttons = {{"B", "<b>", "</b>"}, {"I", "<i>", "</i>"}, {"U", "<u>", "</u>"}};
        for (String[] spec : buttons) {
            MaterialButton btn = new MaterialButton(context);
            btn.setText(spec[0]);
            btn.setFocusable(false);
            btn.setFocusableInTouchMode(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int margin = dp(4);
            params.setMargins(margin, 0, margin, 0);
            btn.setOnClickListener(v -> wrapSelection(target, spec[1], spec[2]));
            row.addView(btn, params);
        }
        MaterialButton colorBtn = new MaterialButton(context);
        colorBtn.setText(R.string.color);
        colorBtn.setFocusable(false);
        colorBtn.setFocusableInTouchMode(false);
        colorBtn.setOnClickListener(v -> showColorWheelDialog(target));
        row.addView(colorBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
    }

    private static void wrapSelection(EditText target, String open, String close) {
        if (target == null) return;
        try {
            int start = target.getSelectionStart();
            int end = target.getSelectionEnd();
            if (start < 0 || end < 0) {
                target.append(open + close);
                target.setSelection(target.getText().length() - close.length());
                return;
            }
            if (start > end) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            Editable text = target.getText();
            if (text == null) return;
            if (start == end) {
                text.insert(start, open + close);
                target.setSelection(start + open.length());
            } else {
                text.insert(end, close);
                text.insert(start, open);
                target.setSelection(end + open.length() + close.length());
            }
        } catch (Exception ignored) {
        }
    }

    private void showColorWheelDialog(EditText target) {
        int start = -1;
        int end = -1;
        try {
            start = target.getSelectionStart();
            end = target.getSelectionEnd();
        } catch (Exception ignored) {
        }
        showColorWheelDialog(target, start, end);
    }

    private interface ColorPicked {
        void onColor(int argb, String hex);
    }

    private void showColorWheel(int initialArgb, ColorPicked onApply) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);
        ColorWheelView wheel = new ColorWheelView(context);
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        root.addView(wheel, wheelParams);
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        View previewSwatch = new View(context);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        swatchParams.rightMargin = dp(8);
        previewRow.addView(previewSwatch, swatchParams);
        EditText hexInput = new EditText(context);
        hexInput.setHint("#RRGGBB");
        hexInput.setSingleLine(true);
        previewRow.addView(UiFields.wrap(context, hexInput, null, 0),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(previewRow);
        SeekBar alphaBar = new SeekBar(context);
        alphaBar.setMax(255);
        alphaBar.setProgress(255);
        root.addView(alphaBar);
        LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] presets = {0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4,
                0xFF4CAF50, 0xFFFFEB3B, 0xFFFF9800, 0xFFFFC107, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000};
        for (int color : presets) {
            TextView swatch = new TextView(context);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
            int margin = dp(2);
            params.setMargins(margin, margin, margin, margin);
            final int presetColor = color;
            swatch.setOnClickListener(v -> wheel.setColor(presetColor));
            presetRow.addView(swatch, params);
        }
        root.addView(presetRow);

        final boolean[] syncing = new boolean[1];
        Runnable syncFromWheel = () -> {
            if (syncing[0]) return;
            syncing[0] = true;
            try {
                int color = wheel.getColor(alphaBar.getProgress());
                previewSwatch.setBackgroundColor(color);
                String hex = String.format(alphaBar.getProgress() == 255 ? "#%06X" : "#%08X",
                        alphaBar.getProgress() == 255 ? (color & 0xFFFFFF) : color);
                String cur = hexInput.getText() == null ? "" : hexInput.getText().toString();
                if (!hex.equalsIgnoreCase(cur)) hexInput.setText(hex);
            } finally {
                syncing[0] = false;
            }
        };
        wheel.setListener(syncFromWheel);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                syncFromWheel.run();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        hexInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (syncing[0]) return;
                syncing[0] = true;
                try {
                    String hex = s.toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    if (hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        try {
                            int parsed = (int) Long.parseLong(hex.substring(1), 16)
                                    | (hex.length() == 7 ? 0xFF000000 : 0);
                            if ((parsed & 0xFFFFFF) != (wheel.getColor(255) & 0xFFFFFF)) {
                                wheel.setColor(parsed);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    syncing[0] = false;
                }
            }

            public void afterTextChanged(Editable s) {
            }
        });
        syncFromWheel.run();
        wheel.setColor(initialArgb);
        dialogUtil.getDialogBuilder()
                .setTitle(R.string.pick_a_color)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String hex = hexInput.getText() == null ? "" : hexInput.getText().toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        Extensions.showMessage(context, R.string.invalid_hex_color);
                        return;
                    }
                    hex = hex.toUpperCase();
                    int argb;
                    try {
                        argb = (int) Long.parseLong(hex.substring(1), 16)
                                | (hex.length() == 7 ? 0xFF000000 : 0);
                    } catch (Exception e) {
                        Extensions.showMessage(context, R.string.invalid_hex_color);
                        return;
                    }
                    onApply.onColor(argb, hex);
                })
                .show();
    }

    private void showColorWheelDialog(EditText target, int snapshotStart, int snapshotEnd) {
        final int[] snapshot = new int[]{snapshotStart, snapshotEnd};
        showColorWheel(0xFFFF0000, (argb, hex) -> {
            String tag = "<font color=\"" + hex + "\">";
            if (snapshot[0] >= 0 && snapshot[1] >= 0 && target.getText() != null) {
                int len = target.getText().length();
                int start = Math.max(0, Math.min(snapshot[0], len));
                int end = Math.max(0, Math.min(snapshot[1], len));
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                try {
                    target.setSelection(start, end);
                } catch (Exception ignored) {
                }
            }
            wrapSelection(target, tag, "</font>");
        });
    }


    private static int clampOffset(String text) {
        try {
            int v = Integer.parseInt(text.trim());
            if (v > 9999) return 9999;
            return Math.max(v, -9999);
        } catch (Exception e) {
            return 0;
        }
    }

    private void runAddToast(File file, List<String> selectedActivities, String message, boolean sign) {
        OverlayInjectorUtil.ToastOptions toast = new OverlayInjectorUtil.ToastOptions();
        toast.message = message;
        runAddOverlay(file, selectedActivities, toast, null, sign);
    }

    private void runAddOverlay(File file, List<String> selectedActivities,
                               OverlayInjectorUtil.ToastOptions toast,
                               OverlayInjectorUtil.DialogOptions dialog, boolean sign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doAdd = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            AppExecutors.io().execute(() -> {
                try {
                    File result = OverlayInjectorUtil.addOverlayToActivities(context, file, selectedActivities, toast, dialog, logger);
                    if (sign) wrapper[0].signApk(result);
                    pm.dismiss();
                    context.handler.post(context::refreshAllPanes);
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            });
        };
        if (sign) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doAdd.run();
        }); else doAdd.run();
    }

    public void showRemoveAllToastsDialog(File file) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        CheckBox autosign = new CheckBox(context);
        autosign.setText(R.string.auto_sign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        layout.addView(autosign);

        MaterialButton signSettings = new MaterialButton(context);
        signSettings.setText(R.string.sign_set);
        signSettings.setOnClickListener(uiHelper.showSignSettingsDialog());
        layout.addView(signSettings);

        dialogUtil.getDialogBuilder()
                .setTitle(R.string.remove_all_toasts)
                .setMessage(R.string.remove_toasts_info)
                .setView(layout)
                .setPositiveButton(R.string.remove, (dialog, which) -> runRemoveAllToasts(file, sign[0]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runRemoveAllToasts(File file, boolean sign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doRemove = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            AppExecutors.io().execute(() -> {
                try {
                    File result = ToastInjectorUtil.removeAllToasts(context, file, logger);
                    if (sign) wrapper[0].signApk(result);
                    pm.dismiss();
                    context.handler.post(context::refreshAllPanes);
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            });
        };
        if (sign) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doRemove.run();
        }); else doRemove.run();
    }

    public String getPackageNameFromApk(String filePath) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
            if (pi != null && pi.applicationInfo != null) return pi.applicationInfo.packageName;
        } catch (Exception ignored) {}
        return "";
    }
}
