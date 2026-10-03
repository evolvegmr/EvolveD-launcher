package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.Locale;

/**
 * EvolveD Cleaner.
 *
 * The cleaner intentionally operates only on Android app-cache locations. It never touches
 * Minecraft saves, versions, mods, modpacks, resource packs, shaders, runtimes, libraries,
 * accounts, screenshots, configs or user-created game files.
 */
public class CleanerFragment extends Fragment {
    private static final int BG = android.graphics.Color.rgb(8, 6, 13);
    private static final int CARD = android.graphics.Color.rgb(22, 11, 36);
    private static final int PURPLE = android.graphics.Color.rgb(255, 157, 0);
    private static final int ORANGE = android.graphics.Color.rgb(245, 158, 11);
    private static final int WHITE = android.graphics.Color.WHITE;
    private static final int MUTED = android.graphics.Color.rgb(174, 167, 193);

    private TextView cacheSize;
    private TextView status;

    public CleanerFragment() {}

    @Nullable
    @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                       @Nullable android.view.ViewGroup container,
                                       @Nullable Bundle state) {
        Context c = requireContext();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(24));
        root.setBackgroundColor(BG);

        TextView title = text(c, "Cleaner", 28, WHITE, true);
        root.addView(title, lp(-1, -2));
        TextView sub = text(c, "Remove safe temporary launcher cache only", 13, MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(18));
        root.addView(sub, lp(-1, -2));

        LinearLayout card = card(c);
        TextView label = text(c, "SAFE TO CLEAN", 10, PURPLE, true);
        label.setLetterSpacing(.12f);
        card.addView(label, lp(-1, -2));

        cacheSize = text(c, "Scanning…", 34, WHITE, true);
        cacheSize.setPadding(0, dp(7), 0, 0);
        card.addView(cacheSize, lp(-1, -2));

        TextView desc = text(c,
                "Launcher cache and temporary files. Your Minecraft worlds, versions, mods, modpacks, shaders, resource packs, accounts and runtimes are not included.",
                12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        card.addView(desc, lp(-1, -2));

        Button clean = new Button(c);
        clean.setText("CLEAN SAFE CACHE");
        clean.setTextColor(android.graphics.Color.rgb(14, 14, 17));
        clean.setTextSize(12);
        clean.setAllCaps(false);
        android.graphics.drawable.GradientDrawable b = new android.graphics.drawable.GradientDrawable();
        b.setColor(ORANGE);
        b.setCornerRadius(dp(12));
        clean.setBackground(b);
        clean.setOnClickListener(v -> confirmClean());
        card.addView(clean, lp(-1, dp(48)));
        root.addView(card, lp(-1, -2));

        status = text(c, "Only Android cache directories are scanned.", 12, MUTED, false);
        status.setPadding(0, dp(14), 0, 0);
        root.addView(status, lp(-1, -2));

        TextView protectedTitle = text(c, "Always protected", 17, WHITE, true);
        protectedTitle.setPadding(0, dp(26), 0, dp(8));
        root.addView(protectedTitle, lp(-1, -2));

        TextView protectedList = text(c,
                "✓ Worlds & saves\n✓ Installed Minecraft versions\n✓ Fabric / Forge / NeoForge / Quilt\n✓ Mods & modpacks\n✓ Resource packs & shaders\n✓ Java runtimes & libraries\n✓ Accounts, screenshots & configurations",
                13, MUTED, false);
        protectedList.setLineSpacing(dp(5), 1f);
        root.addView(protectedList, lp(-1, -2));

        refresh();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        if (getView() != null) refresh();
    }

    private void refresh() {
        if (cacheSize == null || getContext() == null) return;
        long size = safeCacheSize(requireContext());
        cacheSize.setText(formatBytes(size));
        if (status != null) status.setText(size == 0
                ? "No removable app-cache files found."
                : "Ready to remove " + formatBytes(size) + " of safe cache.");
    }

    private void confirmClean() {
        if (getContext() == null) return;
        long size = safeCacheSize(requireContext());
        if (size <= 0) {
            refresh();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Clean safe cache?")
                .setMessage("This will remove " + formatBytes(size)
                        + " from Android app-cache locations only. Minecraft data is protected.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clean", (d, w) -> {
                    long removed = clearSafeCache(requireContext());
                    if (status != null) status.setText("Cleaned " + formatBytes(removed) + ".");
                    refresh();
                })
                .show();
    }

    private static long safeCacheSize(Context c) {
        long total = sizeOf(c.getCacheDir());
        try {
            File ext = c.getExternalCacheDir();
            if (ext != null) total += sizeOf(ext);
        } catch (Throwable ignored) {}
        return total;
    }

    private static long clearSafeCache(Context c) {
        long before = safeCacheSize(c);
        deleteChildren(c.getCacheDir());
        try {
            File ext = c.getExternalCacheDir();
            if (ext != null) deleteChildren(ext);
        } catch (Throwable ignored) {}
        return before - safeCacheSize(c);
    }

    private static void deleteChildren(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) deleteRecursively(f);
    }

    private static void deleteRecursively(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        try { f.delete(); } catch (Throwable ignored) {}
    }

    private static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return Math.max(0, f.length());
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) total += sizeOf(child);
        return total;
    }

    private LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(CARD);
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

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        if (bytes < 1024 * 1024 * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
    }
}
