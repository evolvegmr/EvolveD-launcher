package net.kdt.pojavlaunch.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.sound.CsSoundPlayer;

/**
 * EvolveD Launcher — legacy first-launch popup retained as a no-op.
 *
 * <p>This launcher opens directly into the real home screen and never starts a
 * tutorial or first-run onboarding flow.
 */
public final class PlusWelcomeDialog extends DialogFragment {

    public static final String TAG = "PLUS_WELCOME_DIALOG";
    private static final String PREF_SEEN = "plusWelcomeDialogSeen";

    /** True when the popup has already been shown once this install. */
    public static boolean wasShown(@NonNull Context context) {
        try {
            return LauncherPreferences.DEFAULT_PREF != null
                    ? LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_SEEN, false)
                    : context.getApplicationContext().getSharedPreferences("cslauncher_settings", Context.MODE_PRIVATE)
                            .getBoolean(PREF_SEEN, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void markShown(@NonNull Context context) {
        try {
            if (LauncherPreferences.DEFAULT_PREF != null) {
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_SEEN, true).apply();
            } else {
                context.getApplicationContext().getSharedPreferences("cslauncher_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean(PREF_SEEN, true).apply();
            }
        } catch (Throwable ignored) { }
    }

    /** The launcher opens directly to the main EvolveD experience without a
     * startup welcome or tutorial overlay. */
    public static boolean maybeShow(@NonNull FragmentActivity activity) {
        return false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.65f);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_plus_welcome, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View card = view.findViewById(R.id.plus_welcome_card);
        if (card != null) {
            card.setAlpha(0f);
            card.setScaleX(0.88f);
            card.setScaleY(0.88f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                    .start();
        }

        // Phase 10: feature rows cascade in after the card lands.
        ViewGroup rows = view.findViewById(R.id.plus_welcome_rows);
        if (rows != null) {
            for (int i = 0; i < rows.getChildCount(); i++) {
                View r = rows.getChildAt(i);
                r.setAlpha(0f);
                r.setTranslationX(28f * getResources().getDisplayMetrics().density);
                r.animate().alpha(1f).translationX(0f)
                        .setStartDelay(220 + i * 70L).setDuration(360)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                        .start();
            }
        }

        // The welcome chime plays as the card lands (a no-op until the sound
        // file is dropped into res/raw/cs_plus_welcome.*).
        CsSoundPlayer.playWelcome(requireContext());

        View cancel = view.findViewById(R.id.plus_welcome_btn_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(v -> dismiss());
        }
        View dim = view.findViewById(R.id.plus_welcome_dim);
        if (dim != null) {
            dim.setOnClickListener(v -> dismiss());
        }
        View cont = view.findViewById(R.id.plus_welcome_btn_continue);
        if (cont != null) {
            cont.setOnClickListener(v -> dismiss());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Phase 10: the card is a full-screen two-pane sheet now — size the
        // window here (the only place a DialogFragment's window honours it).
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            d.getWindow().setGravity(android.view.Gravity.CENTER);
        }
        // Marked seen the moment it is on screen — a crash mid-card must not
        // replay it forever, and re-showing it would break the tutorial chain.
        markShown(requireContext());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        releaseTutorial();
    }

    /** EvolveD Launcher does not run a startup tutorial or welcome flow. */
    private void releaseTutorial() {
        // Intentionally left blank: the launcher opens directly into the real UI.
    }
}
