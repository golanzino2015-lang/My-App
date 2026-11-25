package com.questionbubble.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns text into words the AI understands, and its words back into readable text. */
final class Words {

    static final String[] SPECIAL = {"<pad>", "<unk>", "<user>", "<ai>", "<end>"};
    static final int UNK = 1, USER = 2, AI = 3, END = 4;
    static final String UNK_MARK = "zzunk";

    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        String[] pairs = {
                "whats", "what is", "what's", "what is", "whos", "who is", "who's", "who is", "wheres", "where is",
                "where's", "where is", "hows", "how is", "how's", "how is", "it's", "it is", "i'm", "i am", "im", "i am",
                "you're", "you are", "youre", "you are", "ur", "your", "u", "you", "r", "are", "don't", "do not",
                "dont", "do not", "doesn't", "does not", "doesnt", "does not", "can't", "cannot", "cant", "cannot",
                "won't", "will not", "isn't", "is not", "isnt", "is not", "aren't", "are not", "i've", "i have",
                "i'll", "i will", "that's", "that is", "thats", "that is", "there's", "there is", "let's", "let us",
                "wanna", "want to", "gonna", "going to", "gotta", "got to", "thx", "thanks", "ty", "thank you",
                "pls", "please", "plz", "please", "wat", "what", "wut", "what", "sup", "what is up",
                "wassup", "what is up", "yeah", "yes", "yea", "yes", "yep", "yes", "yup", "yes", "nope", "no",
                "nah", "no", "cuz", "because", "idk", "i do not know", "doin", "doing", "goin", "going",
                "didn't", "did not", "didnt", "did not", "wasn't", "was not", "haven't", "have not",
                "couldn't", "could not", "what're", "what are", "how're", "how are", "you've", "you have",
                "we're", "we are", "they're", "they are", "he's", "he is", "she's", "she is", "ok", "okay",
                "k", "okay", "kk", "okay"};
        for (int i = 0; i < pairs.length; i += 2) SHORT.put(pairs[i], pairs[i + 1]);
    }

    private static final Pattern HI = Pattern.compile("hi+"), HEY = Pattern.compile("hey+"),
            HAHA = Pattern.compile("(ha){2,}h?|(he){2,}"), HELLO = Pattern.compile("hel+o+"), NO = Pattern.compile("no+"),
            YES = Pattern.compile("yes+"), LOL = Pattern.compile("(lol)+"), OKAY = Pattern.compile("okay+"),
            QUOTES = Pattern.compile("^'+|'+$"), WORD = Pattern.compile("\\b[a-zA-Z]+\\b");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9']+|[.,!?]");
    private static final Pattern PLUS = Pattern.compile("(\\d)\\s*\\+\\s*(?=\\d)");
    private static final Pattern MINUS = Pattern.compile("(\\d)\\s*-\\s*(?=\\d)");

    static boolean isPunct(String w) {
        return w.length() == 1 && ".,!?".indexOf(w.charAt(0)) >= 0;
    }

    /** Text -> lowercase words and punctuation, with slang and contractions expanded. */
    static List<String> normalize(String text) {
        String s = text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('`', '\'');
        s = PLUS.matcher(s).replaceAll("$1 plus ");
        s = MINUS.matcher(s).replaceAll("$1 minus ");
        s = s.replace("<unk>", " " + UNK_MARK + " ");
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(s);
        while (m.find()) {
            String w = m.group();
            if (w.indexOf('\'') >= 0) w = QUOTES.matcher(w).replaceAll("");
            if (w.isEmpty()) continue;
            char f = w.charAt(0);
            if (f == 'h' || f == 'n' || f == 'y' || f == 'l' || f == 'o') {
                if (HI.matcher(w).matches()) w = "hi";
                else if (HEY.matcher(w).matches()) w = "hey";
                else if (HAHA.matcher(w).matches()) w = "haha";
                else if (HELLO.matcher(w).matches()) w = "hello";
                else if (NO.matcher(w).matches()) w = "no";
                else if (YES.matcher(w).matches()) w = "yes";
                else if (LOL.matcher(w).matches()) w = "lol";
                else if (OKAY.matcher(w).matches()) w = "okay";
            }
            String exp = SHORT.get(w);
            if (exp != null) {
                for (String x : exp.split(" ")) out.add(x);
                continue;
            }
            if (w.indexOf('\'') >= 0) w = w.replace("'", "");
            if (w.isEmpty()) continue;
            if (isPunct(w) && !out.isEmpty() && isPunct(out.get(out.size() - 1))) continue;
            out.add(w);
        }
        return out;
    }

    /** What the user typed: words only. */
    static List<String> userWords(String text) {
        List<String> out = new ArrayList<>();
        for (String w : normalize(text)) if (!isPunct(w)) out.add(w);
        return out;
    }

    /** The word list. Word number = position. */
    static final class Vocab {
        final ArrayList<String> words = new ArrayList<>();
        private final HashMap<String, Integer> index = new HashMap<>();

        Vocab(List<String> initial) {
            for (String w : initial) add(w);
        }

        void add(String w) {
            if (!index.containsKey(w)) {
                index.put(w, words.size());
                words.add(w);
            }
        }

        int id(String w) {
            if (UNK_MARK.equals(w)) return UNK;
            Integer i = index.get(w);
            return i == null ? UNK : i;
        }

        int size() {
            return words.size();
        }
    }

    /** "<user> words <ai> reply <end>", or just "<user> words <ai>" when reply is null. */
    static void encode(Vocab v, String user, String reply, IntList out) {
        out.add(USER);
        for (String w : userWords(user)) out.add(v.id(w));
        out.add(AI);
        if (reply != null) {
            for (String w : normalize(reply)) out.add(v.id(w));
            out.add(END);
        }
    }

    // ---------------------------------------------------------------- readable text

    private static List<String> proper = new ArrayList<>();
    private static final Map<String, String> FIX = new HashMap<>();
    static {
        FIX.put("i", "I");
        FIX.put("ai", "AI");
        FIX.put("dc", "DC");
        FIX.put("tv", "TV");
    }

    static void setProper(List<String> names) {
        List<String> p = new ArrayList<>(names);
        p.sort((a, b) -> b.length() - a.length());
        proper = p;
    }

    static String detokenize(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!isPunct(w) && sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        String s = sb.toString();
        for (String p : proper) {
            if (!s.contains(p)) continue;
            s = s.replaceAll("\\b" + Pattern.quote(p) + "\\b", Matcher.quoteReplacement(title(p)));
        }
        Matcher m = WORD.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String f = FIX.get(m.group().toLowerCase(Locale.ROOT));
            m.appendReplacement(out, Matcher.quoteReplacement(f != null ? f : m.group()));
        }
        m.appendTail(out);
        s = out.toString();
        // Capital letter at the start of each sentence.
        StringBuilder r = new StringBuilder(s);
        boolean cap = true;
        for (int i = 0; i < r.length(); i++) {
            char ch = r.charAt(i);
            if (cap && Character.isLetter(ch)) {
                r.setCharAt(i, Character.toUpperCase(ch));
                cap = false;
            } else if (ch == '.' || ch == '!' || ch == '?') {
                cap = true;
            } else if (cap && ch != ' ') {
                cap = false;
            }
        }
        return r.toString();
    }

    private static String title(String p) {
        StringBuilder b = new StringBuilder(p);
        for (int i = 0; i < b.length(); i++) {
            if (i == 0 || b.charAt(i - 1) == ' ') b.setCharAt(i, Character.toUpperCase(b.charAt(i)));
        }
        return b.toString();
    }

    /** Growable int array, so big lesson files don't box millions of Integers. */
    static final class IntList {
        int[] a = new int[1024];
        int n;

        void add(int v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }

    private Words() {}
}
