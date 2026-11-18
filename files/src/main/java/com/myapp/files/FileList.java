package com.myapp.files;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** List of files and folders with thumbnails, a per-row menu and multi-select. */
final class FileList extends FrameLayout {

    interface Host {
        void open(Item i);

        void more(Item i);

        void selectionChanged(int count);
    }

    final ListView list;
    private final LinearLayout empty;
    private final Adapter adapter = new Adapter();
    private final Host host;
    private List<Item> items = new ArrayList<>();
    final LinkedHashMap<String, Item> selected = new LinkedHashMap<>();
    boolean selecting;

    FileList(Context c, Host host) {
        super(c);
        this.host = host;
        list = new ListView(c);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 120));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Item it = items.get(pos);
            if (selecting) {
                toggle(it);
                Ui.tick(v);
            } else {
                Ui.tap(v);
                host.open(it);
            }
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Ui.longPress(v);
            Item it = items.get(pos);
            if (!selecting) {
                selecting = true;
                selected.clear();
            }
            toggle(it);
            return true;
        });
        AnimationSet set = new AnimationSet(true);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        TranslateAnimation rise = new TranslateAnimation(0, 0, Ui.dp(c, 14), 0);
        set.addAnimation(fade);
        set.addAnimation(rise);
        set.setDuration(380);
        set.setInterpolator(c, android.R.interpolator.decelerate_cubic);
        LayoutAnimationController lac = new LayoutAnimationController(set, 0.06f);
        list.setLayoutAnimation(lac);
        addView(list, new LayoutParams(Ui.MATCH, Ui.MATCH));

        empty = Ui.col(c);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(Ui.dp(c, 36), Ui.dp(c, 80), Ui.dp(c, 36), 0);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    void setBottomPadding(int px) {
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), px);
    }

    void setItems(List<Item> items, boolean animate) {
        this.items = items;
        if (!selected.isEmpty()) {
            Set<String> keys = new HashSet<>();
            for (Item i : items) keys.add(i.key());
            selected.keySet().retainAll(keys);
            if (selected.isEmpty() && selecting) {
                selecting = false;
                host.selectionChanged(0);
            }
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(GONE);
        list.setVisibility(VISIBLE);
        if (animate) list.scheduleLayoutAnimation();
    }

    List<Item> items() {
        return items;
    }

    void showEmpty(int icon, String title, String msg, String button, Runnable action) {
        Context c = getContext();
        items = new ArrayList<>();
        adapter.notifyDataSetChanged();
        empty.removeAllViews();
        FrameLayout tile = Ui.iconTile(c, icon, 76);
        Ui.elevate(tile, 10, 24);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 76));
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        empty.addView(tile, tp);
        TextView t = Ui.text(c, title, Ui.Type.TITLE);
        t.setGravity(Gravity.CENTER);
        empty.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 22)));
        if (msg != null) {
            TextView m = Ui.text(c, msg, Ui.Type.BODY);
            m.setGravity(Gravity.CENTER);
            empty.addView(m, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 10)));
        }
        if (button != null) {
            TextView b = Ui.pill(c, button, true, v -> action.run());
            LinearLayout.LayoutParams bp = Ui.lp(Ui.WRAP, Ui.dp(c, 50), Ui.dp(c, 24));
            bp.gravity = Gravity.CENTER_HORIZONTAL;
            empty.addView(b, bp);
        }
        empty.setVisibility(VISIBLE);
        Ui.enter(empty, 0);
    }

    // ---------------------------------------------------------------- selection
    void toggle(Item i) {
        if (selected.remove(i.key()) == null) selected.put(i.key(), i);
        if (selected.isEmpty()) selecting = false;
        refreshRows();
        host.selectionChanged(selected.size());
    }

    void selectAll() {
        selecting = true;
        for (Item i : items) selected.put(i.key(), i);
        refreshRows();
        host.selectionChanged(selected.size());
    }

    void clearSelection() {
        selecting = false;
        selected.clear();
        refreshRows();
        host.selectionChanged(0);
    }

    List<Item> selection() {
        return new ArrayList<>(selected.values());
    }

    private void refreshRows() {
        for (int k = 0; k < list.getChildCount(); k++) {
            View v = list.getChildAt(k);
            Object tag = v.getTag();
            if (tag instanceof Holder) ((Holder) tag).bindSelection(true);
        }
    }

    // ---------------------------------------------------------------- rows
    private final class Holder {
        final LinearLayout row;
        final FrameLayout tile;
        final ImageView icon, thumb, more;
        final TextView name, meta;
        final CheckDot check;
        Item item;

        Holder(Context c) {
            row = Ui.row(c);
            row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
            row.setMinimumHeight(Ui.dp(c, 70));
            tile = new FrameLayout(c);
            tile.setBackground(Ui.tile(c, 50));
            int inset = Ui.stroke(c);
            tile.setPadding(inset, inset, inset, inset);
            icon = Ui.icon(c, R.drawable.ic_file, Ui.INK, 24);
            tile.addView(icon);
            thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.clip(thumb, 15);
            thumb.setVisibility(GONE);
            tile.addView(thumb, new LayoutParams(Ui.MATCH, Ui.MATCH));
            row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(c, 50), Ui.dp(c, 50)));

            LinearLayout col = Ui.col(c);
            col.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
            name = Ui.middle(Ui.text(c, "", Ui.Type.TITLE_S));
            name.setTextSize(15.5f);
            meta = Ui.oneLine(Ui.text(c, "", Ui.Type.CAPTION));
            col.addView(name);
            col.addView(meta, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 5)));
            row.addView(col, Ui.weight(Ui.WRAP));

            FrameLayout right = new FrameLayout(c);
            more = Ui.icon(c, R.drawable.ic_more, Ui.INK_2, 20);
            more.setBackground(Ui.ripple(null, Ui.dp(c, 20)));
            more.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            more.setLayoutParams(new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44), Gravity.CENTER));
            more.setOnClickListener(v -> {
                Ui.tap(v);
                if (item != null) host.more(item);
            });
            right.addView(more);
            check = new CheckDot(c);
            right.addView(check, new LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER));
            row.addView(right, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
            row.setTag(this);
        }

        void bind(Item i) {
            Context c = row.getContext();
            boolean same = item != null && item.key().equals(i.key());
            item = i;
            name.setText(i.name);
            meta.setText(Fs.meta(i));
            row.setAlpha(i.hidden() ? 0.6f : 1f);
            Item.Kind k = i.kind();
            icon.setImageResource(k.icon);
            if (!same) {
                thumb.setVisibility(GONE);
                icon.setVisibility(VISIBLE);
            }
            if (Thumbs.has(i)) {
                final boolean apk = k == Item.Kind.APK;
                Thumbs.load(thumb, i, Ui.dp(c, 96), b -> showThumb(b, apk));
            } else {
                thumb.setTag(R.id.thumb_key, null);
                thumb.setVisibility(GONE);
                icon.setVisibility(VISIBLE);
            }
            bindSelection(false);
        }

        void showThumb(Bitmap b, boolean apk) {
            if (b == null) {
                thumb.setVisibility(GONE);
                icon.setVisibility(VISIBLE);
                return;
            }
            thumb.setScaleType(apk ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            int p = apk ? Ui.dp(row.getContext(), 6) : 0;
            thumb.setPadding(p, p, p, p);
            thumb.setImageBitmap(b);
            if (thumb.getVisibility() != VISIBLE) {
                thumb.setVisibility(VISIBLE);
                thumb.setAlpha(0f);
                thumb.animate().alpha(1f).setDuration(220).start();
            }
            icon.setVisibility(GONE);
        }

        void bindSelection(boolean animate) {
            Context c = row.getContext();
            boolean on = item != null && selected.containsKey(item.key());
            more.setVisibility(selecting ? GONE : VISIBLE);
            check.setVisibility(selecting ? VISIBLE : GONE);
            check.set(on, animate);
            float r = Ui.dp(c, 20);
            row.setBackground(on ? Ui.ripple(Ui.rounded(Ui.FILL, r, Ui.LINE, Ui.stroke(c)), r) : Ui.ripple(null, r));
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int p) {
            return items.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h = convert != null ? (Holder) convert.getTag() : new Holder(parent.getContext());
            h.bind(items.get(pos));
            return h.row;
        }
    }
}
