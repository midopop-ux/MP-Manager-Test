package io.github.abdurazaaqmohammed.feature.ai;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

final class AiMarkdown {
    private AiMarkdown() {
    }

    static CharSequence render(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        boolean code = false;
        for (String line : raw.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                code = !code;
                continue;
            }
            int start = out.length();
            if (code) {
                out.append(line).append('\n');
                styleCode(out, start, out.length());
                continue;
            }
            String clean = line;
            int heading = 0;
            while (heading < clean.length() && clean.charAt(heading) == '#') heading++;
            if (heading > 0 && heading <= 4 && heading < clean.length()
                    && clean.charAt(heading) == ' ') {
                clean = clean.substring(heading + 1);
                start = out.length();
                out.append(clean).append('\n');
                appendInline(out, start, out.length());
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.08f), start, out.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            out.append(clean).append('\n');
            appendInline(out, start, out.length());
        }
        if (out.length() > 0) out.delete(out.length() - 1, out.length());
        return out;
    }

    static boolean hasCodeBlock(String raw) {
        return raw != null && raw.contains("```");
    }

    static String firstCodeBlock(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf("```");
        if (start < 0) return "";
        int lineEnd = raw.indexOf('\n', start + 3);
        if (lineEnd < 0) return "";
        int end = raw.indexOf("```", lineEnd + 1);
        if (end < 0) return "";
        return raw.substring(lineEnd + 1, end).trim();
    }

    private static void appendInline(SpannableStringBuilder out, int start, int end) {
        String text = out.subSequence(start, end).toString();
        SpannableStringBuilder styled = new SpannableStringBuilder();
        boolean bold = false;
        boolean code = false;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("**", i)) {
                bold = !bold;
                i += 2;
                continue;
            }
            char c = text.charAt(i);
            if (c == '`') {
                code = !code;
                i++;
                continue;
            }
            int from = i;
            while (i < text.length() && !text.startsWith("**", i) && text.charAt(i) != '`') i++;
            int spanStart = styled.length();
            styled.append(text, from, i);
            if (code) styleCode(styled, spanStart, styled.length());
            if (bold) styled.setSpan(new StyleSpan(Typeface.BOLD), spanStart, styled.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        out.replace(start, end, styled);
    }

    private static void styleCode(SpannableStringBuilder text, int start, int end) {
        if (start >= end) return;
        text.setSpan(new TypefaceSpan("monospace"), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new BackgroundColorSpan(0x22000000), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.92f), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
