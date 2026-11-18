package com.myapp.files;

import java.util.ArrayList;
import java.util.List;

/** Items waiting to be pasted. */
final class Clip {
    static final List<Item> items = new ArrayList<>();
    static boolean move;

    static void set(List<Item> list, boolean mv) {
        items.clear();
        items.addAll(list);
        move = mv;
    }

    static boolean has() {
        return !items.isEmpty();
    }

    static void clear() {
        items.clear();
    }

    private Clip() {}
}
