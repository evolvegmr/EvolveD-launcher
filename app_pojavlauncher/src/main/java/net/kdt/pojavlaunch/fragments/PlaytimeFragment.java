package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.performance.PlaytimeStore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** EvolveD Playtime dashboard backed by real Minecraft game-process sessions. */
public class PlaytimeFragment extends Fragment {
    private static final int BG = android.graphics.Color.rgb(8, 6, 13);
    private static final int CARD = android.graphics.Color.rgb(22, 11, 36);
    private static final int CARD2 = android.graphics.Color.rgb(28, 18, 43);
    private static final int PURPLE = android.graphics.Color.rgb(139, 92, 246);
    private static final int BRIGHT = android.graphics.Color.rgb(168, 85, 247);
    private static final int WHITE = android.graphics.Color.WHITE;
    private static final int MUTED = android.graphics.Color.rgb(174, 167, 193);

    private PlaytimeChart chart;
    private TextView todayValue, avgToday, avgMonth, longest, daysPlayed, weekTotal;

    public PlaytimeFragment() {}

    @Nullable
    @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                       @Nullable android.view.ViewGroup container,
                                       @Nullable Bundle state) {
        Context c = requireContext();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(24));
        root.setBackgroundColor(BG);

        TextView title = text(c, "Playtime", 28, WHITE, true);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text(c, "Your real Minecraft session history", 13, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(18));
        root.addView(subtitle, lp(-1, -2));

        LinearLayout scrollContent = new LinearLayout(c);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        // Hero: Today is deliberately the largest number, matching the reference layout.
        LinearLayout hero = card(c, CARD2);
        TextView eyebrow = text(c, "TODAY", 11, BRIGHT, true);
        eyebrow.setLetterSpacing(.14f);
        hero.addView(eyebrow, lp(-1, -2));
        todayValue = text(c, "0m", 42, WHITE, true);
        todayValue.setPadding(0, dp(2), 0, 0);
        hero.addView(todayValue, lp(-1, -2));
        TextView todayLabel = text(c, "Total Minecraft playtime today", 12, MUTED, false);
        hero.addView(todayLabel, lp(-1, -2));
        weekTotal = text(c, "Last 7 days  •  0m", 12, MUTED, false);
        weekTotal.setPadding(0, dp(12), 0, 0);
        hero.addView(weekTotal, lp(-1, -2));
        scrollContent.addView(hero, lp(-1, -2));

        TextView graphTitle = text(c, "Last 7 days", 17, WHITE, true);
        graphTitle.setPadding(0, dp(20), 0, dp(8));
        scrollContent.addView(graphTitle, lp(-1, -2));

        chart = new PlaytimeChart(c);
        scrollContent.addView(chart, lp(-1, dp(190)));

        LinearLayout stats1 = new LinearLayout(c);
        stats1.setOrientation(LinearLayout.HORIZONTAL);
        avgToday = statCard(c, stats1, "AVERAGE SESSION", "0m");
        avgMonth = statCard(c, stats1, "MONTHLY AVERAGE / DAY", "0m");
        scrollContent.addView(stats1, lp(-1, -2));

        LinearLayout stats2 = new LinearLayout(c);
        stats2.setOrientation(LinearLayout.HORIZONTAL);
        longest = statCard(c, stats2, "LONGEST SESSION", "0m");
        daysPlayed = statCard(c, stats2, "DAYS PLAYED", "0");
        scrollContent.addView(stats2, lp(-1, -2));

        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(scrollContent);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        refresh();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        if (getView() != null) refresh();
    }

    private void refresh() {
        if (getContext() == null) return;
        PlaytimeStore.Stats s = PlaytimeStore.stats(requireContext());
        if (todayValue != null) todayValue.setText(PlaytimeStore.formatDuration(s.todayMs));
        if (avgToday != null) avgToday.setText(PlaytimeStore.shortDuration(s.avgTodayMs));
        if (avgMonth != null) avgMonth.setText(PlaytimeStore.shortDuration(s.avgMonthMs));
        if (longest != null) longest.setText(PlaytimeStore.shortDuration(s.longestMs));
        if (daysPlayed != null) daysPlayed.setText(String.valueOf(s.daysPlayed));
        if (weekTotal != null) weekTotal.setText("Last 7 days  •  " + PlaytimeStore.formatDuration(s.weekMs));
        if (chart != null) {
            chart.values = s.dailyMs;
            chart.invalidate();
        }
    }

    private TextView statCard(Context c, LinearLayout parent, String label, String value) {
        LinearLayout card = card(c, CARD);
        TextView l = text(c, label, 9, MUTED, true);
        l.setLetterSpacing(.08f);
        card.addView(l, lp(-1, -2));
        TextView v = text(c, value, 22, WHITE, true);
        v.setPadding(0, dp(7), 0, 0);
        card.addView(v, lp(-1, -2));
        parent.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
        ((LinearLayout.LayoutParams)card.getLayoutParams()).setMargins(0, dp(8), dp(4), 0);
        return v;
    }

    private LinearLayout card(Context c, int color) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), android.graphics.Color.argb(35, 255, 255, 255));
        l.setBackground(bg);
        return l;
    }

    private TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class PlaytimeChart extends View {
        long[] values = new long[7];
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF bar = new RectF();

        PlaytimeChart(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            label.setTextSize(dp(10));
            label.setColor(MUTED);
            label.setTypeface(android.graphics.Typeface.DEFAULT);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            p.setColor(android.graphics.Color.rgb(18, 12, 27));
            canvas.drawRoundRect(new RectF(0,0,w,h), dp(14), dp(14), p);

            long max = 0;
            for (long v : values) max = Math.max(max, v);
            max = Math.max(max, 60L * 60L * 1000L);

            float bottom = h - dp(30);
            float top = dp(18);
            float gap = dp(9);
            float bw = (w - dp(28) - gap * 6) / 7f;
            String[] labels = new String[7];
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -6);
            SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.US);
            for (int i = 0; i < 7; i++) {
                labels[i] = fmt.format(cal.getTime()).substring(0, 3);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            for (int i = 0; i < 7; i++) {
                float x = dp(14) + i * (bw + gap);
                float bh = (bottom - top) * Math.min(1f, values[i] / (float)max);
                float y = bottom - bh;
                p.setColor(i == 6 ? BRIGHT : android.graphics.Color.rgb(83, 58, 137));
                bar.set(x, Math.min(y, bottom - dp(4)), x + bw, bottom);
                canvas.drawRoundRect(bar, dp(6), dp(6), p);

                label.setTextAlign(Paint.Align.CENTER);
                label.setColor(i == 6 ? WHITE : MUTED);
                canvas.drawText(labels[i], x + bw / 2f, h - dp(10), label);

                if (values[i] > 0 && i == 6) {
                    label.setColor(WHITE);
                    canvas.drawText(PlaytimeStore.shortDuration(values[i]), x + bw/2f,
                            Math.max(dp(12), y - dp(5)), label);
                }
            }
        }
    }
}
