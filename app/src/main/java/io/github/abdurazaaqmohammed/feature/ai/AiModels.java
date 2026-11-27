package io.github.abdurazaaqmohammed.feature.ai;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class AiModels {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AiModels() {
    }

    static void fetch(AiChatEngine.Provider provider, AiChatEngine.ModelsListener listener) {
        if (provider == null) {
            MAIN.post(() -> listener.onError("لم يتم اختيار مزود."));
            return;
        }
        if ("anthropic".equals(provider.kind)) {
            MAIN.post(() -> listener.onModels(new ArrayList<>(provider.models)));
            return;
        }
        String base = AiChatEngine.normalizeBase(provider);
        String url;
        boolean auth = true;
        if ("ollama".equals(provider.kind)) {
            url = base + "/api/tags";
            auth = false;
        } else if ("gemini".equals(provider.kind)) {
            url = base + "/models?key=" + AiChatEngine.key(provider);
            auth = false;
        } else {
            url = base + "/models";
        }
        Request.Builder rb = new Request.Builder().url(url).get();
        if (auth) rb.addHeader("Authorization", "Bearer " + AiChatEngine.key(provider));
        Call call = AiChatEngine.client().newCall(rb.build());
        call.enqueue(new Callback() {
            @Override public void onFailure(Call c, java.io.IOException e) {
                MAIN.post(() -> listener.onError("تعذر جلب النماذج: " + e.getMessage()));
            }

            @Override public void onResponse(Call c, Response response) {
                try (ResponseBody body = response.body()) {
                    String raw = body == null ? "" : body.string();
                    if (!response.isSuccessful()) {
                        MAIN.post(() -> listener.onError(error(raw, "HTTP " + response.code())));
                        return;
                    }
                    List<String> models = parse(provider, raw);
                    if (models.isEmpty()) {
                        MAIN.post(() -> listener.onError("لم تُرجع الخدمة أي نماذج."));
                    } else {
                        MAIN.post(() -> listener.onModels(models));
                    }
                } catch (Exception e) {
                    MAIN.post(() -> listener.onError("تعذر قراءة النماذج: " + e.getMessage()));
                }
            }
        });
    }

    private static List<String> parse(AiChatEngine.Provider provider, String raw) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            JSONObject json = new JSONObject(raw);
            JSONArray array;
            if ("ollama".equals(provider.kind)) {
                array = json.optJSONArray("models");
            } else if ("gemini".equals(provider.kind)) {
                array = json.optJSONArray("models");
            } else {
                array = json.optJSONArray("data");
            }
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String id = "gemini".equals(provider.kind)
                            ? item.optString("name", "").replaceFirst("^models/", "")
                            : item.optString("id", item.optString("name", ""));
                    if (!TextUtils.isEmpty(id) && usable(id)) ids.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        List<String> out = new ArrayList<>(ids);
        Collections.sort(out);
        return out;
    }

    private static boolean usable(String id) {
        String s = id.toLowerCase(java.util.Locale.US);
        return !s.contains("embed") && !s.contains("whisper") && !s.contains("tts")
                && !s.contains("dall-e") && !s.contains("moderation")
                && !s.contains("rerank") && !s.contains("guard");
    }

    private static String error(String raw, String fallback) {
        if (TextUtils.isEmpty(raw)) return fallback;
        try {
            JSONObject json = new JSONObject(raw);
            JSONObject error = json.optJSONObject("error");
            if (error != null) return error.optString("message", fallback);
            if (json.has("message")) return json.optString("message", fallback);
        } catch (Exception ignored) {
        }
        return raw.length() > 500 ? raw.substring(0, 500) : raw;
    }
}
