package com.questionbubble.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny Markdown renderer for chat replies: headings, bullets, **bold**, *italic*, `code`. */
final class Md {

    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\w)");

    static CharSequence render(Context c, String src) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = src.replace("\r", "").split("\n", -1);
        boolean inCode = false;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            int start = out.length();
            if (inCode) {
                out.append(line);
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(0x1FFFFFFF), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                String t = line;
                int heading = 0;
                while (heading < t.length() && heading < 4 && t.charAt(heading) == '#') heading++;
                if (heading > 0 && t.length() > heading && t.charAt(heading) == ' ') {
                    t = t.substring(heading + 1);
                } else {
                    heading = 0;
                }
                String trimmed = t.replaceFirst("^\\s+", "");
                int indent = t.length() - trimmed.length();
                boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ");
                if (bullet) {
                    out.append("•  ");
                    inline(c, out, trimmed.substring(2));
                    int margin = Theme.dp(c, 4 + indent * 6);
                    out.setSpan(new LeadingMarginSpan.Standard(margin, margin + Theme.dp(c, 14)),
                            start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    inline(c, out, t);
                }
                if (heading > 0) {
                    out.setSpan(new TypefaceSpan(Fonts.weight(c, 600)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(heading == 1 ? 1.2f : 1.1f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (li < lines.length - 1) out.append('\n');
        }
        return out;
    }

    private static void inline(Context c, SpannableStringBuilder out, String text) {
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            int s = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                out.setSpan(new TypefaceSpan(Fonts.weight(c, 600)), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null) {
                out.append(m.group(2));
                out.setSpan(new TypefaceSpan("monospace"), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(0x24FFFFFF), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(m.group(3));
                out.setSpan(new StyleSpan(Typeface.ITALIC), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(text, last, text.length());
    }

    private Md() {}
}
