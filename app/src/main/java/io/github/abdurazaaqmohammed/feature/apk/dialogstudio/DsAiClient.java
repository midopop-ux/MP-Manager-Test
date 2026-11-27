package io.github.abdurazaaqmohammed.feature.apk.dialogstudio;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.feature.ai.AiChatEngine;
import modder.hub.dexeditor.fragment.SettingsFragment;

/** Multi-provider AI adapter used by the Dialog Studio AI actions. */
public final class DsAiClient {

    public interface Result {
        void onResult(String text, String error);
    }

    private DsAiClient() {
    }

    public static void request(Context context, String prompt, Result result) {
        AiChatEngine.Provider provider = AiChatEngine.selected(context);
        if (TextUtils.isEmpty(AiChatEngine.key(provider))) {
            String legacyKey = SettingsFragment.getChatGptApiKey(context);
            if (!TextUtils.isEmpty(legacyKey)) {
                provider = new AiChatEngine.Provider();
                provider.id = "gemini_legacy";
                provider.name = "Gemini (إعدادات التطبيق)";
                provider.kind = "gemini";
                provider.baseUrl = "https://generativelanguage.googleapis.com/v1beta";
                provider.model = SettingsFragment.getGeminiModel(context);
                if (TextUtils.isEmpty(provider.model)) provider.model = "gemini-1.5-flash";
                AiChatEngine.saveKey(provider, legacyKey.trim());
            }
        }
        List<AiChatEngine.Message> history = new ArrayList<>();
        history.add(new AiChatEngine.Message("user", prompt));
        AiChatEngine.send(context, provider, history, new AiChatEngine.Listener() {
            @Override public void onSuccess(String text) {
                result.onResult(text, null);
            }

            @Override public void onError(String message) {
                result.onResult(null, message);
            }
        });
    }

}
