package com.questionbubble.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Recent answers, newest first, kept on the device (max 60). */
final class History {

    static final int MAX = 60;

    static final class Item {
        final String source;   // "scan" | "type"
        final String question; // typed question, or "" for scans
        final String answer;
        final long time;

        Item(String source, String question, String answer, long time) {
            this.source = source;
            this.question = question;
            this.answer = answer;
            this.time = time;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    static synchronized void add(Context c, String source, String question, String answer) {
        List<Item> items = list(c);
        items.add(0, new Item(source, question == null ? "" : question, answer, System.currentTimeMillis()));
        while (items.size() > MAX) items.remove(items.size() - 1);
        save(c, items);
    }

    static synchronized List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(o.optString("s", "scan"), o.optString("q", ""),
                        o.optString("a", ""), o.optLong("t", 0)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void remove(Context c, long time) {
        List<Item> items = list(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).time == time) {
                items.remove(i);
                break;
            }
        }
        save(c, items);
    }

    static synchronized void clear(Context c) {
        sp(c).edit().remove("items").apply();
    }

    static Item latest(Context c) {
        List<Item> l = list(c);
        return l.isEmpty() ? null : l.get(0);
    }

    static int countToday(Context c) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        int n = 0;
        for (Item i : list(c)) if (i.time >= start) n++;
        return n;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item i : items) {
                arr.put(new JSONObject().put("s", i.source).put("q", i.question)
                        .put("a", i.answer).put("t", i.time));
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString("items", arr.toString()).apply();
    }

    /** "Just now", "5 min ago", "3:42 PM", "Yesterday", "Mar 4". */
    static String relative(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 60_000) return "Just now";
        if (d < 3_600_000) return (d / 60_000) + " min ago";
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(t);
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)) {
            int dd = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR);
            if (dd == 0) return new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(then.getTime());
            if (dd == 1) return "Yesterday";
        }
        return new java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(then.getTime());
    }

    /** Section header for grouping: "Today", "Yesterday", or a date. */
    static String day(long t) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(t);
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)) {
            int dd = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR);
            if (dd == 0) return "Today";
            if (dd == 1) return "Yesterday";
        }
        return new java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault()).format(then.getTime());
    }

    private History() {}
}
