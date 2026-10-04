package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Removes files only from private Android cache directories owned by the launcher. */
public class CleanerFragment extends Fragment {
    private static final int BG = Color.rgb(11, 9, 7);
    private static final int CARD = Color.rgb(23, 19, 14);
    private static final int GOLD = Color.rgb(230, 184, 76);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(184, 179, 170);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EvolveD-Cleaner");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private enum State { IDLE, SCANNING, READY, CLEANING, COMPLETE, ERROR }

    private TextView sizeValue;
    private TextView categoryCache;
    private TextView categoryTemporary;
    private TextView categoryLogs;
    private TextView status;
    private TextView stateIcon;
    private ProgressBar progress;
    private Button cleanButton;
    private Snapshot snapshot;
    private int operationId;

    public CleanerFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(14));
        root.setBackgroundColor(BG);

        TextView title = text(context, "Cleaner", 24, WHITE, true);
        root.addView(title, lp(-1, -2));
        TextView subtitle = text(context, "Remove safe temporary launcher cache", 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(12));
        root.addView(subtitle, lp(-1, -2));

        LinearLayout card = card(context);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        stateIcon = text(context, "⌕", 23, GOLD, true);
        stateIcon.setGravity(android.view.Gravity.CENTER);
        header.addView(stateIcon, lp(dp(36), dp(36)));
        status = text(context, "Preparing cache scan…", 13, MUTED, false);
        status.setPadding(dp(8), 0, 0, 0);
        header.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        header.addView(progress, lp(dp(28), dp(28)));
        card.addView(header, lp(-1, -2));

        sizeValue = text(context, "Scanning…", 32, WHITE, true);
        sizeValue.setPadding(0, dp(12), 0, dp(10));
        card.addView(sizeValue, lp(-1, -2));

        categoryCache = text(context, "Cache       —", 13, MUTED, false);
        categoryTemporary = text(context, "Temporary   —", 13, MUTED, false);
        categoryLogs = text(context, "Cached logs —", 13, MUTED, false);
        card.addView(categoryCache, lp(-1, -2));
        card.addView(categoryTemporary, lp(-1, -2));
        card.addView(categoryLogs, lp(-1, -2));

        TextView protectedInfo = text(context,
                "Only private Android cache directories are included. Worlds, versions, mods, "
                        + "resource packs, screenshots, runtimes, accounts and game settings are protected.",
                12, MUTED, false);
        protectedInfo.setPadding(0, dp(12), 0, dp(14));
        card.addView(protectedInfo, lp(-1, -2));

        cleanButton = new Button(context);
        cleanButton.setText("CLEAN SAFE CACHE");
        cleanButton.setTextColor(Color.rgb(25, 18, 9));
        cleanButton.setTextSize(12);
        cleanButton.setAllCaps(false);
        cleanButton.setMinHeight(dp(48));
        GradientDrawable buttonBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFFD54A, 0xFFFF8A18});
        buttonBackground.setCornerRadius(dp(8));
        cleanButton.setBackground(buttonBackground);
        cleanButton.setEnabled(false);
        cleanButton.setOnClickListener(v -> confirmClean());
        card.addView(cleanButton, lp(-1, dp(48)));
        root.addView(card, lp(-1, -2));
        scan();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && (snapshot == null || state == State.COMPLETE)) scan();
    }

    @Override
    public void onDestroyView() {
        operationId++;
        if (state == State.SCANNING || state == State.CLEANING) state = State.IDLE;
        super.onDestroyView();
    }

    private State state = State.IDLE;

    private void scan() {
        if (getContext() == null || sizeValue == null || state == State.SCANNING) return;
        final int id = ++operationId;
        final Context app = requireContext().getApplicationContext();
        setState(State.SCANNING, "Scanning safe cache locations…");
        sizeValue.setText("Scanning…");
        cleanButton.setEnabled(false);
        IO.execute(() -> {
            try {
                Snapshot result = scanSafeCache(app);
                postResult(id, () -> {
                    snapshot = result;
                    categoryCache.setText("Cache       " + formatBytes(result.cacheBytes));
                    categoryTemporary.setText("Temporary   " + formatBytes(result.temporaryBytes));
                    categoryLogs.setText("Cached logs " + formatBytes(result.logBytes));
                    sizeValue.setText(formatBytes(result.totalBytes));
                    cleanButton.setText(result.totalBytes == 0L ? "SCAN AGAIN" : "CLEAN SAFE CACHE");
                    cleanButton.setEnabled(true);
                    setState(result.totalBytes == 0L ? State.READY : State.READY,
                            result.totalBytes == 0L
                                    ? "No removable app-cache files found."
                                    : "Ready to remove the listed cache files.");
                });
            } catch (IOException | SecurityException e) {
                android.util.Log.e("EvolveD-Cleaner", "Safe cache scan failed", e);
                postResult(id, () -> {
                    snapshot = null;
                    setState(State.ERROR, "Unable to scan cache: " + e.getMessage());
                    sizeValue.setText("Unavailable");
                });
            }
        });
    }

    private void confirmClean() {
        if (snapshot == null || snapshot.files.isEmpty() || getContext() == null) {
            scan();
            return;
        }
        final Snapshot selected = snapshot;
        new AlertDialog.Builder(requireContext())
                .setTitle("Clean safe cache?")
                .setMessage("Delete " + formatBytes(selected.totalBytes)
                        + " of files from Android cache directories only?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clean", (dialog, which) -> clean(selected))
                .show();
    }

    private void clean(Snapshot selected) {
        if (getContext() == null) return;
        final int id = ++operationId;
        final Context app = requireContext().getApplicationContext();
        setState(State.CLEANING, "Removing scanned cache files…");
        cleanButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        IO.execute(() -> {
            long removedBytes = 0L;
            int removedFiles = 0;
            Exception failure = null;
            for (ScannedFile scanned : selected.files) {
                File file = scanned.file;
                try {
                    if (!file.getCanonicalPath().equals(scanned.canonicalPath)) {
                        throw new IOException("Cache file changed after scan: " + file.getName());
                    }
                    if (!file.exists()) continue;
                    long size = Math.max(0L, file.length());
                    if (file.delete()) {
                        removedBytes += size;
                        removedFiles++;
                    } else if (file.exists()) {
                        throw new IOException("Could not remove " + file.getName());
                    }
                } catch (IOException | SecurityException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            final long actualRemoved = removedBytes;
            final int actualRemovedFiles = removedFiles;
            final Exception cleanFailure = failure;
            Snapshot after = null;
            Exception scanFailure = null;
            try {
                after = scanSafeCache(app);
            } catch (IOException | SecurityException e) {
                scanFailure = e;
            }
            final Snapshot remaining = after;
            final Exception finalScanFailure = scanFailure;
            postResult(id, () -> {
                progress.setVisibility(View.GONE);
                if (remaining != null) {
                    snapshot = remaining;
                    categoryCache.setText("Cache       " + formatBytes(remaining.cacheBytes));
                    categoryTemporary.setText("Temporary   " + formatBytes(remaining.temporaryBytes));
                    categoryLogs.setText("Cached logs " + formatBytes(remaining.logBytes));
                    sizeValue.setText(formatBytes(remaining.totalBytes));
                }
                if (cleanFailure != null || finalScanFailure != null) {
                    Exception error = cleanFailure != null ? cleanFailure : finalScanFailure;
                    if (finalScanFailure != null && cleanFailure != null) error.addSuppressed(finalScanFailure);
                    setState(State.ERROR, "Partial cleanup: removed " + formatBytes(actualRemoved)
                            + " from " + actualRemovedFiles + " files. " + error.getMessage());
                    cleanButton.setEnabled(remaining != null && !remaining.files.isEmpty());
                } else {
                    setState(State.COMPLETE, actualRemoved == 0L
                            ? "Nothing to clean."
                            : "Cleaned: " + formatBytes(actualRemoved) + " from "
                                    + actualRemovedFiles + " files.");
                    cleanButton.setText(remaining != null && remaining.totalBytes > 0L
                            ? "CLEAN REMAINING CACHE" : "SCAN AGAIN");
                    cleanButton.setEnabled(true);
                    animateSuccess();
                }
            });
        });
    }

    private void postResult(int id, Runnable result) {
        android.app.Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (!isAdded() || id != operationId || getView() == null) return;
            result.run();
        });
    }

    private void setState(State next, String message) {
        state = next;
        if (status != null) status.setText(message);
        if (progress != null) progress.setVisibility(
                next == State.SCANNING || next == State.CLEANING ? View.VISIBLE : View.GONE);
        if (cleanButton != null) cleanButton.setEnabled(next == State.READY
                || next == State.COMPLETE);
        if (stateIcon != null) {
            stateIcon.setText(next == State.COMPLETE ? "✓" :
                    next == State.ERROR ? "!" :
                    next == State.SCANNING || next == State.CLEANING ? "…" : "⌕");
            stateIcon.setTextColor(next == State.ERROR ? 0xFFFF7770
                    : next == State.COMPLETE ? 0xFF9CBF91 : GOLD);
        }
    }

    private void animateSuccess() {
        if (stateIcon == null || !net.kdt.pojavlaunch.utils.animation.MotionSpeed.isEnabled()) return;
        stateIcon.setScaleX(0.7f);
        stateIcon.setScaleY(0.7f);
        stateIcon.setAlpha(0.5f);
        stateIcon.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(200L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private static Snapshot scanSafeCache(Context context) throws IOException {
        Snapshot result = new Snapshot();
        Set<String> visited = new HashSet<>();
        List<File> roots = new ArrayList<>();
        File cache = context.getCacheDir();
        if (cache != null) roots.add(cache);
        File codeCache = context.getCodeCacheDir();
        if (codeCache != null) roots.add(codeCache);
        File externalCache = context.getExternalCacheDir();
        if (externalCache != null) roots.add(externalCache);
        for (File root : roots) {
            if (!root.exists()) continue;
            String canonicalRoot = root.getCanonicalPath();
            collect(root, canonicalRoot, result, visited);
        }
        return result;
    }

    private static void collect(File file, String root, Snapshot result,
                                Set<String> visited) throws IOException {
        String canonical = file.getCanonicalPath();
        if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) return;
        if (file.isDirectory()) {
            if (!visited.add(canonical)) return;
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Unable to read " + file.getName());
            for (File child : children) collect(child, root, result, visited);
            return;
        }
        if (!file.isFile() || !visited.add(canonical)) return;
        result.files.add(new ScannedFile(
                file, canonical, Math.max(0L, file.length()), category(file)));
        result.totalBytes += Math.max(0L, file.length());
        switch (category(file)) {
            case TEMPORARY: result.temporaryBytes += Math.max(0L, file.length()); break;
            case LOG: result.logBytes += Math.max(0L, file.length()); break;
            default: result.cacheBytes += Math.max(0L, file.length()); break;
        }
    }

    private static Category category(File file) {
        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (path.contains(File.separator + "tmp" + File.separator)
                || path.contains(File.separator + "temp" + File.separator)
                || path.contains(File.separator + "temporary" + File.separator)) {
            return Category.TEMPORARY;
        }
        if (name.endsWith(".log")) return Category.LOG;
        return Category.CACHE;
    }

    private enum Category { CACHE, TEMPORARY, LOG }

    private static final class ScannedFile {
        final File file;
        final String canonicalPath;
        final long scannedBytes;
        final Category category;

        ScannedFile(File file, String canonicalPath, long scannedBytes, Category category) {
            this.file = file;
            this.canonicalPath = canonicalPath;
            this.scannedBytes = scannedBytes;
            this.category = category;
        }
    }

    private static final class Snapshot {
        final List<ScannedFile> files = new ArrayList<>();
        long totalBytes;
        long cacheBytes;
        long temporaryBytes;
        long logBytes;
    }

    private LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(15), dp(13), dp(15), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0x443F3322);
        layout.setBackground(background);
        return layout;
    }

    private TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
    }
}
