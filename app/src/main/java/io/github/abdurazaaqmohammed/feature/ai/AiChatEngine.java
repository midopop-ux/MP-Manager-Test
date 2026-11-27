package io.github.abdurazaaqmohammed.feature.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.abdurazaaqmohammed.util.hash.PasswordEncryptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/** Real multi-provider chat transport used by the AI workspace. */
public final class AiChatEngine {

    public interface Listener {
        void onSuccess(String text);
        void onError(String message);
    }

    public interface StreamListener extends Listener {
        void onDelta(String delta);
    }

    public interface ModelsListener {
        void onModels(List<String> models);
        void onError(String message);
    }

    public static final class Message {
        public String role;
        public String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static final class Provider {
        public String id = "";
        public String name = "";
        public String kind = "openai";
        public String baseUrl = "https://api.openai.com/v1";
        public String apiKey = "";
        public String model = "gpt-4o-mini";
        public List<String> models = new ArrayList<>();
        public String systemPrompt = "";
        public float temperature = 0.35f;
        public int maxTokens = 4096;
        public boolean builtIn = true;

        public Provider copy() {
            Provider p = new Provider();
            p.id = id;
            p.name = name;
            p.kind = kind;
            p.baseUrl = baseUrl;
            p.apiKey = apiKey;
            p.model = model;
            p.models = new ArrayList<>(models == null ? new ArrayList<>() : models);
            p.systemPrompt = systemPrompt;
            p.temperature = temperature;
            p.maxTokens = maxTokens;
            p.builtIn = builtIn;
            return p;
        }
    }

    private static final String PREFS = "mp_ai_chat";
    private static final String KEY_PROVIDERS = "providers_v1";
    private static final String KEY_SELECTED = "selected_provider_v1";
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final Gson GSON = new Gson();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    private static volatile Call currentCall;

    private AiChatEngine() {
    }

    public static String systemPrompt(Provider provider) {
        if (provider != null && !TextUtils.isEmpty(provider.systemPrompt)) {
            return provider.systemPrompt;
        }
        return systemPrompt();
    }

    public static String systemPrompt() {
        return "أنت مهندس تطبيقات Android وAPK محترف داخل أداة MP-Manager. "
                + "تخصصك: تحليل APK، DEX/Smali، AndroidManifest، الموارد، التوقيع، الاستنساخ، "
                + "Dialog Studio، واجهات Material 3، Java/Kotlin/XML، وإصلاح الأخطاء. "
                + "أجب بالعربية الواضحة. عند طلب تعديل قدّم تحليلًا مختصرًا ثم خطوات عملية وأكوادًا كاملة قابلة للتطبيق. "
                + "لا تدّع أنك نفذت شيئًا داخل جهاز المستخدم؛ أنت مستشار هندسي. "
                + "إذا كانت المعلومات ناقصة فاطلب الملف أو السجل المطلوب بدل التخمين. "
                + "عند اقتراح كود حافظ على توافق Android واذكر المخاطر والاختبارات المطلوبة.";
    }

    public static List<Provider> defaults() {
        List<Provider> list = new ArrayList<>();
        list.add(provider("openai", "OpenAI", "openai", "https://api.openai.com/v1",
                "gpt-4o-mini", "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1", "o3-mini"));
        list.add(provider("openrouter", "OpenRouter", "openai", "https://openrouter.ai/api/v1",
                "google/gemini-2.0-flash-001",
                "google/gemini-2.0-flash-001", "anthropic/claude-3.7-sonnet",
                "openai/gpt-4o-mini", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct"));
        list.add(provider("anthropic", "Anthropic", "anthropic", "https://api.anthropic.com/v1",
                "claude-3-5-sonnet-latest",
                "claude-3-5-sonnet-latest", "claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"));
        list.add(provider("gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta",
                "gemini-2.0-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"));
        list.add(provider("groq", "Groq", "openai", "https://api.groq.com/openai/v1",
                "llama-3.3-70b-versatile",
                "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"));
        list.add(provider("deepseek", "DeepSeek", "openai", "https://api.deepseek.com/v1",
                "deepseek-chat", "deepseek-chat", "deepseek-reasoner"));
        list.add(provider("mistral", "Mistral", "openai", "https://api.mistral.ai/v1",
                "mistral-large-latest", "mistral-large-latest", "mistral-small-latest", "codestral-latest"));
        list.add(provider("xai", "xAI Grok", "openai", "https://api.x.ai/v1",
                "grok-2-latest", "grok-2-latest", "grok-beta"));
        list.add(provider("together", "Together AI", "openai", "https://api.together.xyz/v1",
                "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-Coder-32B-Instruct"));
        list.add(provider("ollama", "Ollama محلي", "ollama", "http://127.0.0.1:11434",
                "qwen2.5-coder", "qwen2.5-coder", "llama3.2", "deepseek-coder-v2"));
        list.add(provider("custom", "مزود مخصص (OpenAI compatible)", "openai", "https://example.com/v1",
                "model-name", "model-name"));
        return list;
    }

    private static Provider provider(String id, String name, String kind, String baseUrl,
                                     String model, String... models) {
        Provider p = new Provider();
        p.id = id;
        p.name = name;
        p.kind = kind;
        p.baseUrl = baseUrl;
        p.model = model;
        p.models = new ArrayList<>(Arrays.asList(models));
        return p;
    }

    public static List<Provider> loadProviders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PROVIDERS, "");
        List<Provider> out = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                Type type = new TypeToken<List<Provider>>() {}.getType();
                List<Provider> saved = GSON.fromJson(raw, type);
                if (saved != null) out.addAll(saved);
            } catch (Exception ignored) {
            }
        }
        if (out.isEmpty()) out = defaults();
        for (Provider def : defaults()) {
            boolean found = false;
            for (Provider p : out) {
                if (def.id.equals(p.id)) {
                    found = true;
                    if (p.models == null || p.models.isEmpty()) p.models = new ArrayList<>(def.models);
                    if (TextUtils.isEmpty(p.baseUrl)) p.baseUrl = def.baseUrl;
                    if (TextUtils.isEmpty(p.kind)) p.kind = def.kind;
                    if (p.systemPrompt == null) p.systemPrompt = "";
                    if (p.temperature <= 0f || p.temperature > 2f) p.temperature = def.temperature;
                    if (p.maxTokens <= 0) p.maxTokens = def.maxTokens;
                    break;
                }
            }
            if (!found) out.add(def);
        }
        return out;
    }

    public static void saveProviders(Context context, List<Provider> providers) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROVIDERS, GSON.toJson(providers)).apply();
    }

    public static String selectedId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED, "openai");
    }

    public static void select(Context context, String id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SELECTED, id == null ? "openai" : id).apply();
    }

    public static Provider selected(Context context) {
        List<Provider> list = loadProviders(context);
        String id = selectedId(context);
        for (Provider p : list) if (id.equals(p.id)) return p;
        return list.isEmpty() ? defaults().get(0) : list.get(0);
    }

    public static void saveKey(Provider provider, String key) {
        provider.apiKey = PasswordEncryptor.encryptString(key == null ? "" : key);
    }

    public static String key(Provider provider) {
        return PasswordEncryptor.decryptString(provider == null ? "" : provider.apiKey);
    }

    public static void send(Context context, Provider provider, List<Message> history, Listener listener) {
        cancel();
        if (provider == null) {
            MAIN.post(() -> listener.onError("لم يتم اختيار مزود."));
            return;
        }
        if (TextUtils.isEmpty(provider.baseUrl)) {
            MAIN.post(() -> listener.onError("عنوان المزود فارغ."));
            return;
        }
        if (!"ollama".equals(provider.kind) && TextUtils.isEmpty(key(provider))) {
            MAIN.post(() -> listener.onError("أضف مفتاح API للمزود " + provider.name + "."));
            return;
        }
        Request request;
        try {
            request = buildRequest(provider, history);
        } catch (Exception e) {
            MAIN.post(() -> listener.onError(e.getMessage()));
            return;
        }
        currentCall = CLIENT.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                MAIN.post(() -> listener.onError("تعذر الاتصال: " + e.getMessage()));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String raw = response.body() == null ? "" : response.body().string();
                String text = null;
                String error = null;
                try {
                    JSONObject json = new JSONObject(raw);
                    if (!response.isSuccessful()) {
                        JSONObject err = json.optJSONObject("error");
                        error = err != null ? err.optString("message", raw) : raw;
                    } else if ("anthropic".equals(provider.kind)) {
                        JSONArray content = json.optJSONArray("content");
                        if (content != null && content.length() > 0) {
                            text = content.getJSONObject(0).optString("text");
                        }
                    } else if ("gemini".equals(provider.kind)) {
                        JSONArray candidates = json.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject c = candidates.getJSONObject(0).optJSONObject("content");
                            JSONArray parts = c == null ? null : c.optJSONArray("parts");
                            if (parts != null && parts.length() > 0) text = parts.getJSONObject(0).optString("text");
                        }
                    } else if ("ollama".equals(provider.kind)) {
                        JSONObject msg = json.optJSONObject("message");
                        text = msg == null ? json.optString("response") : msg.optString("content");
                    } else {
                        JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                            text = msg == null ? "" : msg.optString("content");
                        }
                    }
                    if (TextUtils.isEmpty(text) && error == null) error = raw;
                } catch (Exception e) {
                    error = raw.isEmpty() ? e.getMessage() : raw;
                }
                final String out = text;
                final String err = error;
                MAIN.post(() -> {
                    if (!TextUtils.isEmpty(out)) listener.onSuccess(out);
                    else listener.onError(TextUtils.isEmpty(err) ? "استجابة فارغة." : err);
                });
            }
        });
    }

    private static Request buildRequest(Provider provider, List<Message> history) throws Exception {
        return buildRequest(provider, history, false);
    }

    static Request buildRequest(Provider provider, List<Message> history,
                                        boolean stream) throws Exception {
        String base = normalizeBase(provider);
        String model = TextUtils.isEmpty(provider.model) ? "gpt-4o-mini" : provider.model.trim();
        if ("gemini".equals(provider.kind) && model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        String system = systemPrompt(provider);
        int maxTokens = provider.maxTokens <= 0 ? 4096 : Math.min(32000, provider.maxTokens);
        float temperature = provider.temperature <= 0f || provider.temperature > 2f
                ? 0.35f : provider.temperature;
        JSONObject body = new JSONObject();
        Request.Builder rb = new Request.Builder();
        if ("anthropic".equals(provider.kind)) {
            JSONArray messages = new JSONArray();
            for (Message m : history) {
                if ("system".equals(m.role)) continue;
                messages.put(new JSONObject().put("role", m.role).put("content", m.content));
            }
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("system", system);
            body.put("messages", messages);
            body.put("stream", stream);
            rb.url(base + "/messages")
                    .addHeader("x-api-key", key(provider))
                    .addHeader("anthropic-version", "2023-06-01");
        } else if ("gemini".equals(provider.kind)) {
            JSONArray contents = new JSONArray();
            for (Message m : history) {
                if ("system".equals(m.role)) continue;
                JSONObject part = new JSONObject().put("text", m.content);
                contents.put(new JSONObject()
                        .put("role", "assistant".equals(m.role) ? "model" : "user")
                        .put("parts", new JSONArray().put(part)));
            }
            body.put("contents", contents);
            body.put("systemInstruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", system))));
            body.put("generationConfig", new JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxTokens));
            rb.url(base + "/models/" + model + ":generateContent?key=" + key(provider));
        } else if ("ollama".equals(provider.kind)) {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", system));
            for (Message m : history) {
                messages.put(new JSONObject().put("role", m.role).put("content", m.content));
            }
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", stream);
            body.put("options", new JSONObject().put("temperature", temperature));
            rb.url(base + "/api/chat");
        } else {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt()));
            for (Message m : history) {
                messages.put(new JSONObject().put("role", m.role).put("content", m.content));
            }
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.35);
            rb.url(base + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + key(provider))
                    .addHeader("HTTP-Referer", "https://mp-manager.local")
                    .addHeader("X-Title", "MP Manager AI");
        }
        return rb.post(RequestBody.create(JSON, body.toString()))
                .addHeader("Content-Type", "application/json")
                .build();
    }

    static String normalizeBase(Provider provider) {
        String base = provider.baseUrl == null ? "" : provider.baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if ("ollama".equals(provider.kind) && base.endsWith("/v1")) {
            base = base.substring(0, base.length() - "/v1".length());
        }
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        } else if (base.endsWith("/messages")) {
            base = base.substring(0, base.length() - "/messages".length());
        } else if (base.endsWith("/generateContent")) {
            base = base.substring(0, base.length() - "/generateContent".length());
        }
        return base;
    }

    static void attach(Call call) {
        currentCall = call;
    }

    static OkHttpClient client() {
        return CLIENT;
    }

    public static void cancel() {
        Call call = currentCall;
        currentCall = null;
        if (call != null) call.cancel();
    }
}
