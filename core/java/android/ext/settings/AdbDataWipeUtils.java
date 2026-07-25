/*
 * Copyright (C) 2026 cmets-os
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.ext.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.RecoverySystem;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Helpers for ADB data wipe: Global arm flag, Dialer disable code, and wipe trigger.
 *
 * When armed, authorizing a new ADB host (USB confirm / allowDebugging / Wi-Fi pairing)
 * wipes userdata. Charging and reconnect of already-trusted keys do not.
 *
 * @hide
 */
public final class AdbDataWipeUtils {
    private static final String TAG = "AdbDataWipe";

    public static final String DEFAULT_DISABLE_CODE = "*#8331#";

    private static final Pattern SECRET_CODE_PATTERN = Pattern.compile("\\*#[0-9]+#");

    private static final AtomicBoolean sWipeStarted = new AtomicBoolean(false);

    private AdbDataWipeUtils() {}

    public static boolean isArmed(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ADB_DATA_WIPE, 0) != 0;
    }

    @NonNull
    public static String getDisableCode(@NonNull Context context) {
        final String stored = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.ADB_DATA_WIPE_CODE_DISABLE);
        return TextUtils.isEmpty(stored) ? DEFAULT_DISABLE_CODE : stored;
    }

    /**
     * Validates Dialer-style secret code format: {@code *#} + digits + {@code #}.
     */
    public static boolean isValidSecretCode(@Nullable String code) {
        return !TextUtils.isEmpty(code) && SECRET_CODE_PATTERN.matcher(code).matches();
    }

    /**
     * Validates format and rejects codes currently reserved by stock IMEI/regulatory or
     * Hide Users (via {@link SecretCodeRegistry}). The caller's own current disable code
     * is allowed so re-saving the existing value succeeds.
     */
    public static boolean isValidSecretCode(@NonNull Context context, @Nullable String code) {
        if (!isValidSecretCode(code)) {
            return false;
        }
        return !isReservedConflict(context, code);
    }

    /**
     * Returns true if {@code code} collides with a currently reserved Dialer secret code
     * other than this feature's own disable code.
     */
    public static boolean isReservedConflict(@NonNull Context context, @Nullable String code) {
        return SecretCodeRegistry.isReservedConflict(context, code, getDisableCode(context));
    }

    /**
     * Denies auth to adbd and starts a full userdata wipe. Idempotent across concurrent callers.
     */
    public static void triggerWipe(@NonNull Context context, @NonNull String reason) {
        if (!sWipeStarted.compareAndSet(false, true)) {
            Slog.w(TAG, "Wipe already in progress; ignoring reason=" + reason);
            return;
        }
        Slog.w(TAG, "Triggering userdata wipe; reason=" + reason);
        final Context appContext = context.getApplicationContext();
        final Thread wipeThread = new Thread(() -> {
            try {
                RecoverySystem.rebootWipeUserData(
                        appContext,
                        false /* shutdown */,
                        reason,
                        true /* force */,
                        false /* wipeEuicc */);
            } catch (IOException e) {
                Slog.e(TAG, "Failed to start userdata wipe; reason=" + reason, e);
                sWipeStarted.set(false);
            } catch (SecurityException e) {
                Slog.e(TAG, "Not allowed to wipe userdata; reason=" + reason, e);
                sWipeStarted.set(false);
            }
        }, "AdbDataWipe");
        wipeThread.setDaemon(false);
        wipeThread.start();
    }
}
