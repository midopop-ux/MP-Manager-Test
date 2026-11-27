package io.github.abdurazaaqmohammed.feature.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiSessionStoreTitleTest {

    @Test
    public void titleUsesFirstUserMessageAndTruncates() {
        List<AiChatEngine.Message> messages = new ArrayList<>();
        messages.add(new AiChatEngine.Message("assistant", "مرحبًا"));
        messages.add(new AiChatEngine.Message("user", "اشرح لي كيف يعمل محرك الدايلوك المتقدم في هذا التطبيق بالتفصيل"));
        String title = AiSessionStore.titleOf(messages);
        assertTrue(title.startsWith("اشرح لي"));
        assertTrue(title.endsWith("…"));
        assertTrue(title.length() <= 53);
    }

    @Test
    public void titleFallsBackWhenNoUserMessage() {
        List<AiChatEngine.Message> messages = new ArrayList<>();
        messages.add(new AiChatEngine.Message("assistant", "مرحبًا"));
        assertEquals("محادثة", AiSessionStore.titleOf(messages));
        assertEquals("محادثة", AiSessionStore.titleOf(null));
    }

    @Test
    public void shortTitleIsNotTruncated() {
        List<AiChatEngine.Message> messages = new ArrayList<>();
        messages.add(new AiChatEngine.Message("user", "مرحبًا"));
        assertEquals("مرحبًا", AiSessionStore.titleOf(messages));
    }
}
