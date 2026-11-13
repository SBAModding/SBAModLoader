package dev.segually.sbamodloader.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import dev.segually.sbamodloader.R;

/**
 * A small floating log view. Only its header takes touches (to drag it); the log text lets touches
 * through to the game, so it stays out of the way.
 */
public final class LogcatPanel extends LinearLayout {
    private static final int VISIBLE_LINES = 12;
    private static final int KEPT_LINES = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final TextView body;
    private java.lang.Process process;
    private Thread reader;
    private boolean refreshQueued;
    private float dragDx, dragDy;

    private final Runnable refresh = () -> {
        refreshQueued = false;
        render();
    };

    @SuppressLint("ClickableViewAccessibility")
    public LogcatPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setAlpha(0.88f);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xE61C211E);
        header.setPadding(dp(10), 0, dp(2), 0);

        TextView title = text(context, context.getString(R.string.logcat_title), 12, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = text(context, context.getString(R.string.logcat_clear), 12, 0xFF7FD1A7);
        clear.setPadding(dp(8), dp(8), dp(8), dp(8));
        clear.setOnClickListener(v -> {
            lines.clear();
            render();
        });
        header.addView(clear);

        TextView close = text(context, "✕", 14, Color.WHITE);
        close.setPadding(dp(10), dp(8), dp(10), dp(8));
        close.setContentDescription(context.getString(android.R.string.cancel));
        close.setOnClickListener(v -> hide());
        header.addView(close);

        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        header.setOnTouchListener(this::drag);

        body = text(context, "", 9, 0xFFE6E9EE);
        body.setTypeface(Typeface.MONOSPACE);
        body.setBackgroundColor(0xB3000000);
        body.setPadding(dp(8), dp(6), dp(8), dp(6));
        body.setMaxLines(VISIBLE_LINES);
        body.setSingleLine(false);
        body.setHorizontallyScrolling(true);
        body.setGravity(Gravity.BOTTOM);
        // Not clickable, so touches over the log fall through to the game underneath.
        body.setClickable(false);
        body.setFocusable(false);
        addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(VISIBLE_LINES * 12 + 12)));
    }

    private TextView text(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private boolean drag(View header, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragDx = getX() - e.getRawX();
                dragDy = getY() - e.getRawY();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                View parent = (View) getParent();
                if (parent != null) {
                    float x = Math.max(0, Math.min(e.getRawX() + dragDx, parent.getWidth() - getWidth()));
                    float y = Math.max(0, Math.min(e.getRawY() + dragDy, parent.getHeight() - getHeight()));
                    setX(x);
                    setY(y);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /** Adds the panel to {@code parent} (top-right by default) and starts reading the log. */
    public void show(FrameLayout parent) {
        if (getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            lp.setMargins(dp(16), dp(16), dp(16), dp(16));
            parent.addView(this, lp);
        }
        start();
    }

    public void hide() {
        stop();
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    public boolean isShown2() {
        return getParent() != null;
    }

    private void start() {
        if (reader != null) return;
        reader = new Thread(() -> {
            try {
                process = new ProcessBuilder("logcat", "-v", "brief", "--pid=" + Process.myPid(), "-T", "60")
                        .redirectErrorStream(true).start();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        final String l = line;
                        handler.post(() -> push(l));
                    }
                }
            } catch (Exception ignored) {
                // The panel just stays empty if logcat cannot be started.
            }
        }, "logcat-overlay");
        reader.setDaemon(true);
        reader.start();
    }

    private void stop() {
        if (reader != null) {
            reader.interrupt();
            reader = null;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
        handler.removeCallbacks(refresh);
        refreshQueued = false;
    }

    private void push(String line) {
        if (lines.size() >= KEPT_LINES) lines.removeFirst();
        lines.addLast(line);
        if (!refreshQueued) {
            refreshQueued = true;
            handler.postDelayed(refresh, 250);
        }
    }

    private void render() {
        List<String> tail = new ArrayList<>(lines);
        int from = Math.max(0, tail.size() - VISIBLE_LINES);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = from; i < tail.size(); i++) {
            String l = tail.get(i);
            int start = sb.length();
            sb.append(l);
            int color = 0xFFE6E9EE;
            if (l.startsWith("E/") || l.startsWith("F/")) color = 0xFFFF6B6B;
            else if (l.startsWith("W/")) color = 0xFFFFC857;
            else if (l.startsWith("D/") || l.startsWith("V/")) color = 0xFF9AA3AF;
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < tail.size() - 1) sb.append('\n');
        }
        body.setText(sb);
    }
}
