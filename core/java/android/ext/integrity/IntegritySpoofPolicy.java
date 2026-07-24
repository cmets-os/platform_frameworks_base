package android.ext.integrity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.ext.PackageId;
import android.ext.settings.app.AswBlockPlayIntegrityApi;
import android.ext.settings.app.AswSpoofPlayIntegrity;
import android.ext.settings.app.AswSpoofTelephonyRegion;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * Deterministic spoof decision policy for Play Integrity and telephony region spoof.
 * Block PI wins over spoof. GMS/Vending are auto-included while any other app has the matching flag.
 *
 * @hide
 */
public final class IntegritySpoofPolicy {
    private IntegritySpoofPolicy() {
    }

    public static boolean isGmsOrVending(@Nullable String packageName) {
        return PackageId.GMS_CORE_NAME.equals(packageName)
                || PackageId.PLAY_STORE_NAME.equals(packageName);
    }

    public static boolean isPlayIntegritySpoofEnabled(@NonNull Context ctx, @NonNull String packageName,
            int userId) {
        ApplicationInfo ai = getAppInfo(ctx, packageName, userId);
        GosPackageState ps = GosPackageState.get(packageName, userId);
        if (ai != null && AswBlockPlayIntegrityApi.I.get(ctx, userId, ai, ps)) {
            return false;
        }
        if (ai != null && AswSpoofPlayIntegrity.I.get(ctx, userId, ai, ps)) {
            return true;
        }
        if (isGmsOrVending(packageName)
                && Settings.Global.getInt(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_ANY_PI, 0) == 1) {
            return true;
        }
        return false;
    }

    public static boolean isTelephonySpoofEnabled(@NonNull Context ctx, @NonNull String packageName,
            int userId) {
        ApplicationInfo ai = getAppInfo(ctx, packageName, userId);
        GosPackageState ps = GosPackageState.get(packageName, userId);
        if (ai != null && AswSpoofTelephonyRegion.I.get(ctx, userId, ai, ps)) {
            return true;
        }
        if (isGmsOrVending(packageName)
                && Settings.Global.getInt(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_ANY_TEL, 0) == 1) {
            return true;
        }
        return false;
    }

    public static boolean isPlayIntegritySpoofEnabledForCaller(@NonNull Context ctx) {
        String pkg = resolveCallingPackage();
        if (pkg == null) {
            return false;
        }
        return isPlayIntegritySpoofEnabled(ctx, pkg, UserHandle.myUserId());
    }

    public static boolean isTelephonySpoofEnabledForCaller() {
        String pkg = resolveCallingPackage();
        if (pkg == null) {
            return false;
        }
        Context ctx = ActivityThread.currentApplication();
        if (ctx == null) {
            return false;
        }
        return isTelephonySpoofEnabled(ctx, pkg, UserHandle.myUserId());
    }

    @Nullable
    public static String resolveCallingPackage() {
        String pkg = ActivityThread.currentPackageName();
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        Context ctx = ActivityThread.currentApplication();
        return ctx != null ? ctx.getPackageName() : null;
    }

    @Nullable
    private static ApplicationInfo getAppInfo(Context ctx, String packageName, int userId) {
        try {
            return ctx.getPackageManager().getApplicationInfoAsUser(packageName, 0, userId);
        } catch (Exception e) {
            return null;
        }
    }
}
