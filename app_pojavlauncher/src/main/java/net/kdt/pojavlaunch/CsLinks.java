package net.kdt.pojavlaunch;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * Centralized EvolveD Launcher community links.
 *
 * <p>Keeping the URLs here makes it easy to update the launcher brand without
 * scattering social links across the UI. {@link #open} tries the native app
 * first and falls back to the browser when the platform can not resolve it.
 */
public final class CsLinks {

    /** Official community invite. */
    public static final String DISCORD = "https://discord.gg/bpgYQMA59D";
    /** EvolveD Launcher project channel. */
    public static final String YOUTUBE = "https://www.youtube.com/@evolvedlauncher";
    public static final String WEBSITE = "https://github.com/evolvegmr/EvolveD-launcher";
    public static final String GITHUB = "https://github.com/evolvegmr/EvolveD-launcher";

    private CsLinks() {}

    public static void open(@NonNull Context ctx, @NonNull String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(ctx, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }
}
