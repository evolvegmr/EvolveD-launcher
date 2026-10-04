package net.kdt.pojavlaunch.fragments;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

/** Displays system memory values reported by Android's ActivityManager. */
public class MemoryFragment extends Fragment {
    private static final int BACKGROUND = Color.rgb(11, 9, 7);
    private static final int CARD = Color.rgb(23, 19, 14);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(184, 179, 170);
    private final TextView[] values = new TextView[4];

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text(context, "Memory", 24, WHITE, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text(context, "Live system memory reported by Android", 13, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        card.setBackground(background);
        String[] labels = {"TOTAL RAM", "USED RAM", "AVAILABLE RAM", "MEMORY USAGE"};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(12), 0, dp(12));
            TextView label = text(context, labels[i], 11, MUTED, true);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            values[i] = text(context, "Unavailable", 17, WHITE, true);
            row.addView(values[i], new LinearLayout.LayoutParams(-2, -2));
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < labels.length - 1) {
                View divider = new View(context);
                divider.setBackgroundColor(Color.argb(28, 255, 255, 255));
                card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        Button refresh = new Button(context);
        refresh.setText("REFRESH");
        refresh.setOnClickListener(v -> updateMemory());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, -2);
        buttonParams.topMargin = dp(12);
        root.addView(refresh, buttonParams);

        updateMemory();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMemory();
    }

    private void updateMemory() {
        if (getContext() == null || values[0] == null) return;
        ActivityManager manager = (ActivityManager) requireContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            setUnavailable();
            return;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        if (info.totalMem <= 0L) {
            setUnavailable();
            return;
        }

        long total = info.totalMem;
        long available = Math.min(total, Math.max(0L, info.availMem));
        long used = total - available;
        values[0].setText(formatBytes(total));
        values[1].setText(formatBytes(used));
        values[2].setText(formatBytes(available));
        values[3].setText(String.format(Locale.US, "%.0f%%", used * 100d / total));
    }

    private void setUnavailable() {
        for (TextView value : values) {
            if (value != null) value.setText("Unavailable");
        }
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private static TextView text(Context context, String content, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
