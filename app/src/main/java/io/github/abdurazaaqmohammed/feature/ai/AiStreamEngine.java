package io.github.abdurazaaqmohammed.feature.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/** SSE/NDJSON streaming transport for chat providers. */
final class AiStreamEngine {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AiStreamEngine() {
    }

    static void send(Context context, AiChatEngine.Provider provider,
                     List<AiChatEngine.Message> history, AiChatEngine.StreamListener listener) {
        AiChatEngine.cancel();
        if (provider == null) {
            MAIN.post(() -> listener.onError("لم يتم اختيار مزود."));
            return;
        }
        if (TextUtils.isEmpty(provider.baseUrl)) {
            MAIN.post(() -> listener.onError("عنوان المزود فارغ."));
            return;
        }
        if (!"ollama".equals(provider.kind) && TextUtils.isEmpty(AiChatEngine.key(provider))) {
            MAIN.post(() -> listener.onError("أضف مفتاح API للمزود " + provider.name + "."));
            return;
        }
        if ("gemini".equals(provider.kind)) {
            AiChatEngine.send(context, provider, history, new AiChatEngine.Listener() {
                @Override public void onSuccess(String text) { listener.onSuccess(text); }
                @Override public void onError(String message) { listener.onError(message); }
            });
            return;
        }
        Request request;
        try {
            request = AiChatEngine.buildRequest(provider, history, true);
        } catch (Exception e) {
            MAIN.post(() -> listener.onError(e.getMessage()));
            return;
        }
        Call call = AiChatEngine.client().newCall(request);
        AiChatEngine.attach(call);
        call.enqueue(new Callback() {
            final AtomicBoolean done = new AtomicBoolean();
            final StringBuilder full = new StringBuilder();

            void fail(String message) {
                if (done.compareAndSet(false, true)) {
                    MAIN.post(() -> listener.onError(message));
                }
            }

            void finish() {
                if (!done.compareAndSet(false, true)) return;
                String text = full.toString();
                if (TextUtils.isEmpty(text)) {
                    MAIN.post(() -> listener.onError("استجابة فارغة."));
                } else {
                    MAIN.post(() -> listener.onSuccess(text));
                }
            }

            @Override public void onFailure(Call c, IOException e) {
                if (c.isCanceled()) return;
                fail("تعذر الاتصال: " + e.getMessage());
            }

            @Override public void onResponse(Call c, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        String raw = body == null ? "" : body.string();
                        fail(errorText(raw, "HTTP " + response.code()));
                        return;
                    }
                    if (body == null) {
                        fail("استجابة فارغة.");
                        return;
                    }
                    BufferedSource source = body.source();
                    String line;
                    while ((line = source.readUtf8Line()) != null) {
                        if (done.get()) break;
                        String error = consume(provider, line, full, listener);
                        if (error != null) {
                            fail(error);
                            return;
                        }
                    }
                    finish();
                } catch (Exception e) {
                    fail("تعذر قراءة الاستجابة: " + e.getMessage());
                }
            }
        });
    }

    private static String consume(AiChatEngine.Provider provider, String line,
                                  StringBuilder full, AiChatEngine.StreamListener listener) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty() || text.startsWith(":") || text.startsWith("event:")) return null;
        String data = text.startsWith("data:") ? text.substring(5).trim() : text;
        if (data.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(data);
            JSONObject err = json.optJSONObject("error");
            if (err != null) return err.optString("message", "Stream error");
            String delta = delta(provider, json);
            if (delta != null && !delta.isEmpty()) {
                full.append(delta);
                MAIN.post(() -> listener.onDelta(delta));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String delta(AiChatEngine.Provider provider, JSONObject json) {
        if ("anthropic".equals(provider.kind)) return anthropicDelta(json);
        if ("ollama".equals(provider.kind)) return ollamaDelta(json);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject first = choices.optJSONObject(0);
        if (first == null) return "";
        JSONObject d = first.optJSONObject("delta");
        if (d != null && !d.isNull("content")) return d.optString("content", "");
        JSONObject m = first.optJSONObject("message");
        return m == null ? "" : m.optString("content", "");
    }

    private static String anthropicDelta(JSONObject json) {
        if ("content_block_delta".equals(json.optString("type"))) {
            JSONObject d = json.optJSONObject("delta");
            return d == null ? "" : d.optString("text", "");
        }
        return "";
    }

    private static String ollamaDelta(JSONObject json) {
        if (json.optBoolean("done")) return "";
        JSONObject m = json.optJSONObject("message");
        return m == null ? json.optString("response", "") : m.optString("content", "");
    }

    private static String errorText(String raw, String fallback) {
        if (TextUtils.isEmpty(raw)) return fallback;
        try {
            JSONObject json = new JSONObject(raw);
            JSONObject error = json.optJSONObject("error");
            if (error != null) return error.optString("message", fallback);
            if (json.has("message")) return json.optString("message", fallback);
        } catch (Exception ignored) {
        }
        return raw.length() > 700 ? raw.substring(0, 700) : raw;
    }
}
