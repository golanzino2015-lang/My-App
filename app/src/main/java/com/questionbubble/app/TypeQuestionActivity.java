package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small pop-up window where you type a question; the answer is copied to the clipboard. */
public class TypeQuestionActivity extends Activity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Type Question");

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Type your question");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        root.addView(title);

        EditText input = new EditText(this);
        input.setHint("e.g. What is the capital of Australia?");
        input.setMinLines(3);
        input.setMaxLines(8);
        root.addView(input);

        TextView answer = new TextView(this);
        answer.setTextIsSelectable(true);
        answer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        answer.setPadding(0, dp(12), 0, dp(12));
        root.addView(answer);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button ask = new Button(this);
        ask.setText("Ask & Copy");
        Button close = new Button(this);
        close.setText("Close");
        row.addView(ask, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);

        close.setOnClickListener(v -> finish());
        ask.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(this, "Type a question first", Toast.LENGTH_SHORT).show();
                return;
            }
            ask.setEnabled(false);
            answer.setText("Thinking…");
            String key = Prefs.apiKey(this);
            String model = Prefs.model(this);
            worker.execute(() -> {
                String result;
                boolean ok;
                try {
                    result = OpenAiClient.ask(key, model, "Question: " + q, null);
                    ok = true;
                } catch (Exception e) {
                    result = "Error: " + e.getMessage();
                    ok = false;
                }
                final String text = result;
                final boolean success = ok;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    answer.setText(text);
                    ask.setEnabled(true);
                    if (success) {
                        ClipboardManager cm = getSystemService(ClipboardManager.class);
                        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
                        Toast.makeText(this, "Answer copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        setContentView(root);
        getWindow().setLayout(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
