package io.github.abdurazaaqmohammed.feature.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.feature.apk.ApkCloneEngine;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** Conversational AI workspace specialised in Android/APK engineering. */
public class AiChatActivity extends BaseActivity {

    public static final String EXTRA_PROMPT = "ai_prompt";
    private static final int REQ_ATTACH = 7301;
    private static final int REQ_APK = 7302;

    private final List<AiChatEngine.Message> history = new ArrayList<>();
    private final List<ChatItem> items = new ArrayList<>();
    private final ChatAdapter adapter = new ChatAdapter();
    private final StringBuilder streamBuffer = new StringBuilder();
    private final List<AiChatEngine.Provider> providers = new ArrayList<>();

    private MaterialToolbar toolbar;
    private MaterialButton providerButton;
    private MaterialButton modelButton;
    private TextView status;
    private TextView attachment;
    private EditText input;
    private MaterialButton sendButton;
    private RecyclerView list;
    private AiChatEngine.Provider provider;
    private String attachedName = "";
    private String attachedText = "";
    private boolean busy;
    private int streamIndex = -1;
    private String sessionId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionId = getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE)
                .getString("current_session", "");
        providers.clear();
        providers.addAll(AiChatEngine.loadProviders(this));
        provider = AiChatEngine.selected(this);
        buildUi();
        refreshHeader();
        String requestedPrompt = getIntent().getStringExtra(EXTRA_PROMPT);
        if (requestedPrompt != null && !requestedPrompt.isEmpty() && input != null) {
            input.setText(requestedPrompt);
            input.setSelection(input.getText().length());
        }
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString("chat_history");
            if (saved != null && !saved.isEmpty()) {
                try {
                    java.lang.reflect.Type type = new TypeToken<List<AiChatEngine.Message>>() {}.getType();
                    List<AiChatEngine.Message> restored = new Gson().fromJson(saved, type);
                    if (restored != null) {
                        for (AiChatEngine.Message m : restored) {
                            if (m == null || m.content == null) continue;
                            history.add(m);
                            addItem(m.role, m.content, false);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (history.isEmpty()) restoreSavedChat();
        if (history.isEmpty()) addWelcome();
    }

    private void restoreSavedChat() {
        SharedPreferences prefs = getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE);
        sessionId = prefs.getString("current_session", "");
        String raw = prefs.getString("current_history", "");
        if (raw == null || raw.isEmpty()) return;
        try {
            java.lang.reflect.Type type = new TypeToken<List<AiChatEngine.Message>>() {}.getType();
            List<AiChatEngine.Message> restored = new Gson().fromJson(raw, type);
            if (restored == null) return;
            for (AiChatEngine.Message m : restored) {
                if (m == null || m.content == null || m.content.isEmpty()) continue;
                history.add(m);
                addItem(m.role, m.content, false);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveChat() {
        if (history.isEmpty()) return;
        if (sessionId == null || sessionId.isEmpty()) sessionId = AiSessionStore.newId();
        SharedPreferences prefs = getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE);
        prefs.edit()
                .putString("current_history", new Gson().toJson(history))
                .putString("current_session", sessionId)
                .apply();
        AiSessionStore.Session session = new AiSessionStore.Session();
        session.id = sessionId;
        session.title = AiSessionStore.titleOf(history);
        session.messages = new ArrayList<>(history);
        AiSessionStore.save(this, session);
    }

    private int color(int attr) {
        View v = findViewById(android.R.id.content);
        if (v == null) v = getWindow().getDecorView();
        return MaterialColors.getColor(v, attr, 0xFF121212);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        setContentView(root);

        toolbar = new MaterialToolbar(this);
        toolbar.setTitle("مساعد التطبيقات");
        toolbar.setSubtitle("تحليل APK · Dialog Studio · Java/Kotlin/XML");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, "محادثة جديدة");
        toolbar.getMenu().add(0, 2, 1, "إعدادات المزود");
        toolbar.getMenu().add(0, 3, 2, "إعادة آخر رد");
        toolbar.getMenu().add(0, 4, 3, "تصدير المحادثة");
        toolbar.getMenu().add(0, 5, 4, "تحليل APK");
        toolbar.getMenu().add(0, 6, 5, "المحادثات");
        toolbar.setOnMenuItemClickListener(this::onMenu);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(6), dp(12), dp(6));
        providerButton = chip("المزود");
        providerButton.setOnClickListener(v -> showProviderPicker());
        modelButton = chip("النموذج");
        modelButton.setOnClickListener(v -> showModelPicker());
        MaterialButton manage = chip("إدارة المزودين");
        manage.setOnClickListener(v -> showSettingsDialog());
        bar.addView(providerButton);
        bar.addView(modelButton);
        bar.addView(manage);
        hs.addView(bar);
        root.addView(hs);

        HorizontalScrollView prompts = new HorizontalScrollView(this);
        prompts.setHorizontalScrollBarEnabled(false);
        LinearLayout promptBar = new LinearLayout(this);
        promptBar.setOrientation(LinearLayout.HORIZONTAL);
        promptBar.setPadding(dp(12), 0, dp(12), dp(4));
        String[][] presets = {
                {"تحليل APK", "حلّل بنية APK هذه واذكر المخاطر وخطوات الاختبار."},
                {"إصلاح خطأ", "ساعدني في تشخيص هذا الخطأ وإصلاحه دون كسر الوظائف الحالية."},
                {"تصميم دايلوك", "صمّم دايلوك Material 3 متكاملًا مع XML وJava والأحداث."},
                {"مراجعة كود", "راجع هذا الكود واكتشف الأخطاء والتحسينات مع أمثلة."},
                {"شرح وظيفة", "اشرح وظيفة هذا العنصر أو الكود خطوة بخطوة."}
        };
        for (String[] preset : presets) {
            MaterialButton chip = chip(preset[0]);
            chip.setOnClickListener(v -> {
                input.setText(preset[1]);
                input.setSelection(input.getText().length());
                input.requestFocus();
            });
            promptBar.addView(chip);
        }
        prompts.addView(promptBar);
        root.addView(prompts);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setPadding(dp(8), dp(6), dp(8), dp(12));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextSize(12f);
        status.setPadding(dp(16), dp(2), dp(16), dp(4));
        status.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        root.addView(status);

        MaterialCardView inputCard = new MaterialCardView(this);
        inputCard.setRadius(dp(24));
        inputCard.setCardElevation(dp(2));
        inputCard.setStrokeWidth(dp(1));
        inputCard.setStrokeColor(color(com.google.android.material.R.attr.colorOutline));
        LinearLayout inputBox = new LinearLayout(this);
        inputBox.setOrientation(LinearLayout.VERTICAL);
        inputBox.setPadding(dp(10), dp(6), dp(10), dp(8));

        attachment = new TextView(this);
        attachment.setTextSize(12f);
        attachment.setPadding(dp(8), dp(2), dp(8), dp(4));
        attachment.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
        attachment.setVisibility(View.GONE);
        inputBox.addView(attachment);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        MaterialButton attach = new MaterialButton(this);
        attach.setText("إرفاق");
        attach.setAllCaps(false);
        attach.setMinWidth(0);
        attach.setMinimumWidth(0);
        attach.setInsetTop(0);
        attach.setInsetBottom(0);
        attach.setOnClickListener(v -> pickAttachment());
        row.addView(attach, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setHint("اسأل عن APK أو Dialog Studio أو اكتب كودًا...");
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        input.setHintTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(input, inputLp);

        sendButton = new MaterialButton(this);
        sendButton.setText("إرسال");
        sendButton.setAllCaps(false);
        sendButton.setOnClickListener(v -> sendOrStop());
        row.addView(sendButton);
        inputBox.addView(row);
        inputCard.addView(inputBox);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(10), 0, dp(10), dp(12));
        root.addView(inputCard, cardLp);
    }

    private MaterialButton chip(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setCornerRadius(dp(20));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshHeader() {
        if (provider == null) provider = AiChatEngine.selected(this);
        providerButton.setText("المزود: " + provider.name);
        modelButton.setText("النموذج: " + provider.model);
        boolean hasKey = "ollama".equals(provider.kind) || !TextUtils.isEmpty(AiChatEngine.key(provider));
        status.setText(hasKey ? "جاهز · " + provider.name + " / " + provider.model
                : "أضف مفتاح API من إعدادات المزود · " + provider.name);
        status.setTextColor(hasKey
                ? color(com.google.android.material.R.attr.colorOnSurfaceVariant)
                : color(com.google.android.material.R.attr.colorError));
    }

    private void addWelcome() {
        addItem("assistant", "مرحبًا. أنا مساعد هندسي متخصص في Android وAPK وDialog Studio.\n\n"
                + "أستطيع مساعدتك في:\n"
                + "• تحليل بنية APK وDEX وAndroidManifest\n"
                + "• تشخيص أخطاء البناء والتوقيع والاستنساخ\n"
                + "• تصميم دايلوكات Material 3 وتوليد XML/Java\n"
                + "• تحسين الأداء وإصلاح الأعطال\n\n"
                + "اختر مزودًا ونموذجًا، وأضف مفتاح API، ثم ابدأ. يمكنك إرفاق ملف نصي أو كود للتحليل.", false);
    }

    private void addItem(String role, String text, boolean error) {
        items.add(new ChatItem(role, text, error));
        adapter.notifyItemInserted(items.size() - 1);
        list.post(() -> list.scrollToPosition(items.size() - 1));
    }

    private void addLoading() {
        items.add(new ChatItem("assistant", "يفكر…", false));
        items.get(items.size() - 1).loading = true;
        adapter.notifyItemInserted(items.size() - 1);
        list.post(() -> list.scrollToPosition(items.size() - 1));
    }

    private void removeLoading() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).loading) {
                items.remove(i);
                adapter.notifyItemRemoved(i);
                return;
            }
        }
    }

    private void sendOrStop() {
        if (busy) {
            AiChatEngine.cancel();
            busy = false;
            sendButton.setText("إرسال");
            status.setText("تم إيقاف الطلب.");
            removeLoading();
            streamIndex = -1;
            saveChat();
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty() && TextUtils.isEmpty(attachedText)) return;
        if (provider == null) {
            toast("اختر مزودًا أولاً.");
            return;
        }
        String prompt = text;
        if (!TextUtils.isEmpty(attachedText)) {
            prompt += "\n\n[ملف مرفق: " + attachedName + "]\n```\n" + attachedText + "\n```";
            attachedText = "";
            attachedName = "";
            attachment.setVisibility(View.GONE);
        }
        input.setText("");
        history.add(new AiChatEngine.Message("user", prompt));
        addItem("user", text.isEmpty() ? "تحليل الملف المرفق." : text, false);
        addLoading();
        busy = true;
        sendButton.setText("إيقاف");
        status.setText("جارٍ الاتصال بـ " + provider.name + "…");
        saveChat();
        streamBuffer.setLength(0);
        streamIndex = items.size() - 1;
        AiStreamEngine.send(this, provider, new ArrayList<>(history), new AiChatEngine.StreamListener() {
            @Override public void onDelta(String delta) {
                if (streamIndex < 0 || streamIndex >= items.size()) return;
                ChatItem item = items.get(streamIndex);
                if (item.loading) item.loading = false;
                streamBuffer.append(delta);
                item.text = streamBuffer.toString();
                adapter.notifyItemChanged(streamIndex);
                status.setText("يتلقى الرد… " + streamBuffer.length() + " حرف");
                list.post(() -> list.scrollToPosition(streamIndex));
            }

            @Override public void onSuccess(String reply) {
                busy = false;
                sendButton.setText("إرسال");
                if (streamIndex >= 0 && streamIndex < items.size()
                        && !TextUtils.isEmpty(streamBuffer.toString())) {
                    items.get(streamIndex).text = reply;
                    items.get(streamIndex).loading = false;
                    adapter.notifyItemChanged(streamIndex);
                } else {
                    removeLoading();
                    addItem("assistant", reply, false);
                }
                streamIndex = -1;
                history.add(new AiChatEngine.Message("assistant", reply));
                saveChat();
                refreshHeader();
            }

            @Override public void onError(String message) {
                busy = false;
                sendButton.setText("إرسال");
                if (streamIndex >= 0 && streamIndex < items.size()
                        && TextUtils.isEmpty(items.get(streamIndex).text)) {
                    removeLoading();
                }
                streamIndex = -1;
                addItem("assistant", "تعذر إكمال الطلب:\n" + message, true);
                status.setText("حدث خطأ · تحقق من المفتاح أو الشبكة.");
                status.setTextColor(color(com.google.android.material.R.attr.colorError));
                saveChat();
            }
        });
    }

    private boolean onMenu(MenuItem item) {
        if (item.getItemId() == 1) {
            saveChat();
            history.clear();
            items.clear();
            adapter.notifyDataSetChanged();
            sessionId = AiSessionStore.newId();
            getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE).edit()
                    .remove("current_history").remove("current_session").apply();
            addWelcome();
            return true;
        }
        if (item.getItemId() == 2) {
            showSettingsDialog();
            return true;
        }
        if (item.getItemId() == 3) {
            regenerate();
            return true;
        }
        if (item.getItemId() == 4) {
            exportChat();
            return true;
        }
        if (item.getItemId() == 5) {
            pickApk();
            return true;
        }
        if (item.getItemId() == 6) {
            showSessions();
            return true;
        }
        return false;
    }

    private void regenerate() {
        if (busy) {
            toast("أوقف الطلب الحالي أولاً.");
            return;
        }
        int lastUser = -1;
        for (int i = items.size() - 1; i >= 0; i--) {
            if ("user".equals(items.get(i).role)) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            toast("لا يوجد سؤال سابق.");
            return;
        }
        String prompt = items.get(lastUser).text;
        while (items.size() > lastUser) {
            items.remove(items.size() - 1);
        }
        while (!history.isEmpty() && !"user".equals(history.get(history.size() - 1).role)) {
            history.remove(history.size() - 1);
        }
        if (!history.isEmpty()) history.remove(history.size() - 1);
        adapter.notifyDataSetChanged();
        saveChat();
        input.setText(prompt);
        sendOrStop();
    }

    private void showProviderPicker() {
        String[] names = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) names[i] = providers.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("اختر مزود الذكاء الاصطناعي")
                .setItems(names, (d, which) -> {
                    provider = providers.get(which);
                    AiChatEngine.select(this, provider.id);
                    refreshHeader();
                })
                .setNeutralButton("إدارة المزودين", (d, w) -> showSettingsDialog())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showModelPicker() {
        if (provider == null) return;
        List<String> models = provider.models == null ? new ArrayList<>() : new ArrayList<>(provider.models);
        if (!models.contains(provider.model)) models.add(0, provider.model);
        String[] names = models.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("اختر النموذج · " + provider.name)
                .setItems(names, (d, which) -> {
                    provider.model = models.get(which);
                    AiChatEngine.saveProviders(this, providers);
                    refreshHeader();
                })
                .setPositiveButton("نموذج مخصص", (d, w) -> customModel())
                .setNeutralButton("تحديث القائمة", (d, w) -> refreshModels())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void refreshModels() {
        if (provider == null) return;
        status.setText("جارٍ جلب النماذج من " + provider.name + "…");
        AiModels.fetch(provider, new AiChatEngine.ModelsListener() {
            @Override public void onModels(List<String> models) {
                provider.models = new ArrayList<>(models);
                if (!provider.models.contains(provider.model)) provider.models.add(0, provider.model);
                AiChatEngine.saveProviders(AiChatActivity.this, providers);
                refreshHeader();
                toast("تم تحديث " + models.size() + " نموذجًا.");
                showModelPicker();
            }

            @Override public void onError(String message) {
                status.setText("تعذر جلب النماذج.");
                toast(message);
            }
        });
    }

    private void customModel() {
        EditText edit = new EditText(this);
        edit.setText(provider.model);
        edit.setHint("معرف النموذج");
        new AlertDialog.Builder(this)
                .setTitle("نموذج مخصص")
                .setView(edit)
                .setPositiveButton("استخدام", (d, w) -> {
                    String value = edit.getText().toString().trim();
                    if (value.isEmpty()) return;
                    provider.model = value;
                    if (!provider.models.contains(value)) provider.models.add(0, value);
                    AiChatEngine.saveProviders(this, providers);
                    refreshHeader();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showSessions() {
        List<AiSessionStore.Session> sessions = AiSessionStore.list(this);
        ListView list = new ListView(this);
        List<String> labels = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (AiSessionStore.Session session : sessions) {
            labels.add(session.title + "\n" + fmt.format(new Date(session.updated)));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("سجل المحادثات")
                .setView(list)
                .setPositiveButton("محادثة جديدة", (d, w) -> {
                    saveChat();
                    history.clear();
                    items.clear();
                    adapter.notifyDataSetChanged();
                    sessionId = AiSessionStore.newId();
                    getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE).edit()
                            .remove("current_history").remove("current_session").apply();
                    addWelcome();
                })
                .setNegativeButton("إغلاق", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            loadSession(sessions.get(position));
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            AiSessionStore.Session session = sessions.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("حذف المحادثة")
                    .setMessage(session.title)
                    .setPositiveButton("حذف", (d, w) -> {
                        AiSessionStore.delete(this, session.id);
                        if (session.id.equals(sessionId)) sessionId = AiSessionStore.newId();
                        dialog.dismiss();
                        showSessions();
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
            return true;
        });
        dialog.show();
    }

    private void loadSession(AiSessionStore.Session session) {
        if (session == null) return;
        saveChat();
        history.clear();
        items.clear();
        adapter.notifyDataSetChanged();
        if (session.messages != null) {
            for (AiChatEngine.Message message : session.messages) {
                if (message == null || message.content == null || message.content.isEmpty()) continue;
                history.add(message);
                addItem(message.role, message.content, false);
            }
        }
        sessionId = session.id;
        getSharedPreferences("mp_ai_chat_ui", MODE_PRIVATE).edit()
                .putString("current_history", new Gson().toJson(history))
                .putString("current_session", sessionId)
                .apply();
        if (items.isEmpty()) addWelcome();
        toast("فُتحت المحادثة.");
    }

    private void showSettingsDialog() {
        if (provider == null) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        AutoCompleteTextView providerPick = new AutoCompleteTextView(this);
        String[] names = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) names[i] = providers.get(i).name;
        providerPick.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names));
        providerPick.setText(provider.name);
        providerPick.setHint("المزود");
        box.addView(label("المزود"));
        box.addView(providerPick);

        EditText name = field(box, "اسم المزود", provider.name);
        EditText base = field(box, "عنوان API الأساسي", provider.baseUrl);
        EditText model = field(box, "النموذج", provider.model);
        EditText key = field(box, "مفتاح API", AiChatEngine.key(provider));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText system = multilineField(box, "تعليمات النظام (اختياري)", provider.systemPrompt);
        EditText maxTokens = field(box, "الحد الأقصى للرد (tokens)", String.valueOf(provider.maxTokens));
        maxTokens.setInputType(InputType.TYPE_CLASS_NUMBER);
        providerPick.setOnItemClickListener((parent, view, position, id) -> {
            provider = providers.get(position);
            AiChatEngine.select(this, provider.id);
            name.setText(provider.name);
            base.setText(provider.baseUrl);
            model.setText(provider.model);
            key.setText(AiChatEngine.key(provider));
            system.setText(provider.systemPrompt);
            maxTokens.setText(String.valueOf(provider.maxTokens));
        });

        MaterialButton test = new MaterialButton(this);
        test.setText("اختبار الاتصال وجلب النماذج");
        test.setAllCaps(false);
        test.setOnClickListener(v -> {
            provider.baseUrl = base.getText().toString().trim();
            provider.model = model.getText().toString().trim();
            AiChatEngine.saveKey(provider, key.getText().toString().trim());
            AiChatEngine.saveProviders(this, providers);
            toast("جارٍ اختبار " + provider.name + "…");
            AiModels.fetch(provider, new AiChatEngine.ModelsListener() {
                @Override public void onModels(List<String> models) {
                    toast("الاتصال ناجح · " + models.size() + " نموذجًا.");
                }
                @Override public void onError(String message) {
                    toast(message);
                }
            });
        });
        box.addView(test);

        TextView note = new TextView(this);
        note.setText("المفاتيح تُشفّر محليًا عبر Android Keystore. لا تُرسل إلا إلى عنوان المزود المحدد.");
        note.setTextSize(12f);
        note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعدادات مزود الذكاء الاصطناعي")
                .setView(scroll)
                .setPositiveButton("حفظ", null)
                .setNeutralButton("مزود جديد", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                provider.name = name.getText().toString().trim().isEmpty()
                        ? provider.name : name.getText().toString().trim();
                provider.baseUrl = base.getText().toString().trim();
                provider.model = model.getText().toString().trim();
                provider.systemPrompt = system.getText().toString().trim();
                try {
                    provider.maxTokens = Integer.parseInt(maxTokens.getText().toString().trim());
                } catch (Exception ignored) {
                    provider.maxTokens = 4096;
                }
                if (provider.maxTokens < 256) provider.maxTokens = 256;
                AiChatEngine.saveKey(provider, key.getText().toString().trim());
                AiChatEngine.saveProviders(this, providers);
                refreshHeader();
                dialog.dismiss();
                toast("حُفظت إعدادات " + provider.name + ".");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                addProviderDialog();
            });
        });
        dialog.show();
    }

    private void addProviderDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText name = field(box, "اسم المزود", "مزود جديد");
        EditText base = field(box, "عنوان OpenAI-compatible", "https://example.com/v1");
        EditText model = field(box, "النموذج", "model-name");
        EditText key = field(box, "مفتاح API", "");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("إضافة مزود مخصص")
                .setView(scroll)
                .setPositiveButton("إضافة", (d, w) -> {
                    AiChatEngine.Provider p = new AiChatEngine.Provider();
                    p.id = "custom_" + System.currentTimeMillis();
                    p.name = name.getText().toString().trim();
                    p.baseUrl = base.getText().toString().trim();
                    p.model = model.getText().toString().trim();
                    p.models = new ArrayList<>();
                    p.models.add(p.model);
                    p.builtIn = false;
                    AiChatEngine.saveKey(p, key.getText().toString().trim());
                    providers.add(p);
                    provider = p;
                    AiChatEngine.select(this, p.id);
                    AiChatEngine.saveProviders(this, providers);
                    refreshHeader();
                    toast("أُضيف المزود المخصص.");
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setPadding(0, dp(10), 0, dp(2));
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        return t;
    }

    private EditText field(LinearLayout box, String hint, String value) {
        box.addView(label(hint));
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        box.addView(e);
        return e;
    }

    private EditText multilineField(LinearLayout box, String hint, String value) {
        box.addView(label(hint));
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setMinLines(2);
        e.setMaxLines(5);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(e);
        return e;
    }

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/octet-stream"});
        startActivityForResult(intent, REQ_APK);
    }

    private void pickAttachment() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/*", "application/json", "application/xml", "application/java-archive",
                "application/octet-stream"});
        startActivityForResult(intent, REQ_ATTACH);
    }

    @Override
    protected void onDestroy() {
        saveChat();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("chat_history", new Gson().toJson(history));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_APK) {
            analyzeApk(uri);
            return;
        }
        if (requestCode != REQ_ATTACH) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذر فتح الملف.");
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0 && total < 240000) {
                sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                total += n;
            }
            attachedText = sb.toString();
            attachedName = uri.getLastPathSegment() == null ? "ملف" : uri.getLastPathSegment();
            attachment.setText("مرفق: " + attachedName + " · " + attachedText.length() + " حرف");
            attachment.setVisibility(View.VISIBLE);
            toast("أُرفق الملف النصي.");
        } catch (Exception e) {
            toast("تعذر قراءة الملف: " + e.getMessage());
        }
    }

    private void analyzeApk(Uri uri) {
        toast("جارٍ تحليل APK…");
        AppExecutors.io().execute(() -> {
            try {
                File dir = new File(getCacheDir(), "ai_apk");
                dir.mkdirs();
                File out = new File(dir, "analyze_" + System.currentTimeMillis() + ".apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) throw new Exception("تعذر فتح APK.");
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                ApkCloneEngine.Report report = ApkCloneEngine.inspect(this, out);
                String text = report.summary();
                AppExecutors.runOnUiIfAlive(this, () -> {
                    attachedName = out.getName();
                    attachedText = "تقرير فحص APK:\n" + text;
                    attachment.setText("تحليل APK جاهز · " + report.packageName
                            + " · " + report.entries + " ملف");
                    attachment.setVisibility(View.VISIBLE);
                    input.setText("حلّل هذا التطبيق واقترح تحسينات مستقرة، مع تحديد المخاطر وخطوات الاختبار.");
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> toast("تعذر تحليل APK: " + e.getMessage()));
            }
        });
    }

    private void exportChat() {
        if (items.isEmpty()) {
            toast("لا توجد محادثة للتصدير.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MP Manager · محادثة الذكاء الاصطناعي\n");
        sb.append("المزود: ").append(provider == null ? "" : provider.name).append('\n');
        sb.append("النموذج: ").append(provider == null ? "" : provider.model).append('\n');
        sb.append("التاريخ: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date())).append("\n\n");
        for (ChatItem it : items) {
            if (it.loading) continue;
            sb.append("[").append("user".equals(it.role) ? "المستخدم" : "المساعد").append("]\n");
            sb.append(it.text).append("\n\n");
        }
        try {
            File dir = getExternalFilesDir("DialogStudio/ai_chats");
            if (dir == null) dir = new File(getFilesDir(), "ai_chats");
            dir.mkdirs();
            File out = new File(dir, "chat_" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            toast("صُدرت المحادثة إلى " + out.getAbsolutePath());
        } catch (Exception e) {
            toast("فشل التصدير: " + e.getMessage());
        }
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ChatItem {
        final String role;
        String text;
        final boolean error;
        boolean loading;

        ChatItem(String role, String text, boolean error) {
            this.role = role;
            this.text = text;
            this.error = error;
        }
    }

    private final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout outer = new LinearLayout(AiChatActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(dp(4), dp(4), dp(4), dp(4));
            TextView bubble = new TextView(AiChatActivity.this);
            bubble.setTextSize(15f);
            bubble.setLineSpacing(dp(2), 1f);
            bubble.setTextIsSelectable(true);
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            outer.addView(bubble);
            LinearLayout actions = new LinearLayout(AiChatActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setVisibility(View.GONE);
            MaterialButton copyCode = chip("نسخ الكود");
            MaterialButton editCode = chip("تعديل الكود");
            actions.addView(copyCode);
            actions.addView(editCode);
            outer.addView(actions);
            return new VH(outer, bubble, actions, copyCode, editCode);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ChatItem it = items.get(position);
            boolean user = "user".equals(it.role);
            h.outer.setGravity(user ? Gravity.END : Gravity.START);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            int textColor;
            if (it.error) {
                bg.setColor(color(com.google.android.material.R.attr.colorErrorContainer));
                textColor = color(com.google.android.material.R.attr.colorOnErrorContainer);
            } else if (user) {
                bg.setColor(color(com.google.android.material.R.attr.colorPrimaryContainer));
                textColor = color(com.google.android.material.R.attr.colorOnPrimaryContainer);
            } else {
                bg.setColor(color(com.google.android.material.R.attr.colorSurfaceVariant));
                textColor = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
            }
            h.bubble.setBackground(bg);
            h.bubble.setTextColor(textColor);
            h.bubble.setTypeface(it.loading ? Typeface.DEFAULT : Typeface.DEFAULT);
            h.bubble.setText(it.loading ? it.text : AiMarkdown.render(it.text));
            h.bubble.setOnLongClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("AI", it.text));
                toast("نُسخ النص.");
                return true;
            });
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.bubble.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = user ? Gravity.END : Gravity.START;
            h.bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
            boolean hasCode = !user && !it.loading && AiMarkdown.hasCodeBlock(it.text);
            h.actions.setVisibility(hasCode ? View.VISIBLE : View.GONE);
            if (hasCode) {
                h.copyCode.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("AI code", AiMarkdown.firstCodeBlock(it.text)));
                    toast("نُسخ الكود.");
                });
                h.editCode.setOnClickListener(v -> {
                    input.setText(AiMarkdown.firstCodeBlock(it.text));
                    input.setSelection(input.getText().length());
                    input.requestFocus();
                    toast("الكود جاهز للتعديل.");
                });
            }
        }

        @Override public int getItemCount() {
            return items.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final LinearLayout outer;
            final TextView bubble;
            final LinearLayout actions;
            final MaterialButton copyCode;
            final MaterialButton editCode;

            VH(LinearLayout outer, TextView bubble, LinearLayout actions,
               MaterialButton copyCode, MaterialButton editCode) {
                super(outer);
                this.outer = outer;
                this.bubble = bubble;
                this.actions = actions;
                this.copyCode = copyCode;
                this.editCode = editCode;
            }
        }
    }
}
