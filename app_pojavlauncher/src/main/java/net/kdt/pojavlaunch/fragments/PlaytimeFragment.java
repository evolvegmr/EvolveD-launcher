package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.widget.NestedScrollView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.performance.PlaytimeStore;

import java.util.concurrent.atomic.AtomicInteger;

/** Playtime dashboard backed by persisted Minecraft game sessions. */
public class PlaytimeFragment extends Fragment {
    private static final int BACKGROUND = Color.rgb(11, 9, 7);
    private static final int CARD = Color.rgb(23, 19, 14);
    private static final int HERO = Color.rgb(30, 23, 15);
    private static final int GOLD = Color.rgb(230, 184, 76);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(174, 167, 193);

    private final AtomicInteger refreshGeneration = new AtomicInteger();
    private TextView todayValue;
    private TextView averageValue;
    private TextView maximumValue;
    private TextView weeklyValue;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        FrameLayout page = new FrameLayout(context);
        scrollView.addView(page, new NestedScrollView.LayoutParams(-1, -2));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.setBackgroundColor(BACKGROUND);
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        page.addView(root, rootParams);

        page.addOnLayoutChangeListener((view, left, top, right, bottom,
                                       oldLeft, oldTop, oldRight, oldBottom) -> {
            int availableWidth = right - left;
            int contentWidth = Math.min(availableWidth, dp(960));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
            if (params.width != contentWidth) {
                params.width = contentWidth;
                root.setLayoutParams(params);
            }
        });

        TextView title = text(context, "Playtime", 24, WHITE, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text(context, "Your tracked Minecraft playtime", 13, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
        root.addView(content, contentParams);

        LinearLayout hero = card(context, HERO);
        TextView todayLabel = text(context, "TODAY", 11, GOLD, true);
        todayLabel.setLetterSpacing(.14f);
        hero.addView(todayLabel, new LinearLayout.LayoutParams(-1, -2));
        todayValue = text(context, "0m", 42, WHITE, true);
        todayValue.setPadding(0, dp(4), 0, 0);
        hero.addView(todayValue, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = dp(12);
        content.addView(hero, heroParams);

        LinearLayout secondary = new LinearLayout(context);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(secondary, new LinearLayout.LayoutParams(-1, -2));
        averageValue = statCard(context, secondary, "AVERAGE");
        maximumValue = statCard(context, secondary, "MAXIMUM");
        weeklyValue = statCard(context, secondary, "WEEKLY");

        refresh();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) refresh();
    }

    private void refresh() {
        if (getContext() == null) return;
        int generation = refreshGeneration.incrementAndGet();
        Context app = requireContext().getApplicationContext();
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            PlaytimeStore.Stats stats = PlaytimeStore.stats(app);
            activity.runOnUiThread(() -> {
                if (!isAdded() || generation != refreshGeneration.get()
                        || todayValue == null || averageValue == null
                        || maximumValue == null || weeklyValue == null) return;
                todayValue.setText(PlaytimeStore.formatDuration(stats.todayMs));
                averageValue.setText(PlaytimeStore.shortDuration(stats.averageDailyMs));
                maximumValue.setText(PlaytimeStore.shortDuration(stats.maximumDailyMs));
                weeklyValue.setText(PlaytimeStore.shortDuration(stats.weekMs));
            });
        });
    }

    private TextView statCard(Context context, LinearLayout row, String label) {
        LinearLayout card = card(context, CARD);
        TextView caption = text(context, label, 10, MUTED, true);
        caption.setLetterSpacing(.08f);
        card.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        TextView value = text(context, "0m", 21, WHITE, true);
        value.setPadding(0, dp(8), 0, 0);
        card.addView(value, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(card, params);
        return value;
    }

    private LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        card.setBackground(background);
        return card;
    }

    private TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
