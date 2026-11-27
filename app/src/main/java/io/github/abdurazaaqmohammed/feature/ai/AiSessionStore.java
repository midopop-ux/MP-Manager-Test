package io.github.abdurazaaqmohammed.feature.ai;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent chat sessions stored locally, independent from provider settings. */
final class AiSessionStore {

    private static final String PREFS = "mp_ai_sessions";
    private static final String KEY_INDEX = "index";
    private static final String KEY_PREFIX = "session_";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Session>>() { }.getType();

    static final class Session {
        String id = "";
        String title = "";
        long updated;
        List<AiChatEngine.Message> messages = new ArrayList<>();
    }

    private AiSessionStore() {
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static List<Session> list(Context context) {
        String raw = prefs(context).getString(KEY_INDEX, "");
        List<Session> sessions = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            try {
                List<Session> parsed = GSON.fromJson(raw, LIST_TYPE);
                if (parsed != null) sessions.addAll(parsed);
            } catch (Exception ignored) {
            }
        }
        Collections.sort(sessions, (a, b) -> Long.compare(b.updated, a.updated));
        return sessions;
    }

    static Session load(Context context, String id) {
        if (id == null || id.isEmpty()) return null;
        String raw = prefs(context).getString(KEY_PREFIX + id, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return GSON.fromJson(raw, Session.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    static void save(Context context, Session session) {
        if (session == null || session.id == null || session.id.isEmpty()) return;
        SharedPreferences prefs = prefs(context);
        session.updated = System.currentTimeMillis();
        if (session.title == null || session.title.trim().isEmpty()) {
            session.title = titleOf(session.messages);
        }
        prefs.edit().putString(KEY_PREFIX + session.id, GSON.toJson(session)).apply();
        List<Session> index = list(context);
        Session meta = new Session();
        meta.id = session.id;
        meta.title = session.title;
        meta.updated = session.updated;
        boolean replaced = false;
        for (int i = 0; i < index.size(); i++) {
            if (index.get(i).id.equals(session.id)) {
                index.set(i, meta);
                replaced = true;
                break;
            }
        }
        if (!replaced) index.add(meta);
        prefs.edit().putString(KEY_INDEX, GSON.toJson(index)).apply();
    }

    static void delete(Context context, String id) {
        if (id == null || id.isEmpty()) return;
        SharedPreferences prefs = prefs(context);
        prefs.edit().remove(KEY_PREFIX + id).apply();
        List<Session> index = list(context);
        for (int i = index.size() - 1; i >= 0; i--) {
            if (index.get(i).id.equals(id)) index.remove(i);
        }
        prefs.edit().putString(KEY_INDEX, GSON.toJson(index)).apply();
    }

    static void clear(Context context) {
        SharedPreferences prefs = prefs(context);
        for (Session session : list(context)) {
            prefs.edit().remove(KEY_PREFIX + session.id).apply();
        }
        prefs.edit().remove(KEY_INDEX).apply();
    }

    static String titleOf(List<AiChatEngine.Message> messages) {
        if (messages == null) return "محادثة";
        for (AiChatEngine.Message message : messages) {
            if (message != null && "user".equals(message.role) && message.content != null) {
                String text = message.content.replace('\n', ' ').trim();
                if (text.length() > 52) text = text.substring(0, 52) + "…";
                if (!text.isEmpty()) return text;
            }
        }
        return "محادثة";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
