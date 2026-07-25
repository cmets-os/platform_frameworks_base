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
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Central registry of Dialer secret codes currently in use.
 *
 * <p>Validators consult {@link #getReservedCodes(Context)} (current Global values with defaults
 * as fallback, plus stock IMEI/regulatory) instead of hardcoding other features' default codes.
 *
 * @hide
 */
public final class SecretCodeRegistry {
    public static final String STOCK_IMEI_CODE = "*#06#";
    public static final String STOCK_REGULATORY_CODE = "*#07#";

    /** Default Hide Users Dialer disable code when Global is unset. */
    public static final String DEFAULT_HIDE_USERS_DISABLE_CODE = "*#8321#";
    /** Default Hide Users Dialer switcher code when Global is unset. */
    public static final String DEFAULT_HIDE_USERS_SWITCHER_CODE = "*#8322#";

    private SecretCodeRegistry() {}

    @NonNull
    public static String getHideUsersDisableCode(@NonNull Context context) {
        final String stored = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.HIDE_USERS_CODE_DISABLE);
        return TextUtils.isEmpty(stored) ? DEFAULT_HIDE_USERS_DISABLE_CODE : stored;
    }

    @NonNull
    public static String getHideUsersSwitcherCode(@NonNull Context context) {
        final String stored = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.HIDE_USERS_CODE_SWITCHER);
        return TextUtils.isEmpty(stored) ? DEFAULT_HIDE_USERS_SWITCHER_CODE : stored;
    }

    /**
     * Currently reserved Dialer secret codes: stock IMEI/regulatory, current Hide Users
     * disable and switcher codes, and current ADB data wipe disable code.
     */
    @NonNull
    public static String[] getReservedCodes(@NonNull Context context) {
        return new String[] {
                STOCK_IMEI_CODE,
                STOCK_REGULATORY_CODE,
                getHideUsersDisableCode(context),
                getHideUsersSwitcherCode(context),
                AdbDataWipeUtils.getDisableCode(context),
        };
    }

    /**
     * Returns true if {@code code} equals a currently reserved code other than any of
     * {@code allowedOwnCodes} (typically the caller's own currently configured values).
     */
    public static boolean isReservedConflict(@NonNull Context context, @Nullable String code,
            @Nullable String... allowedOwnCodes) {
        if (TextUtils.isEmpty(code)) {
            return true;
        }
        if (allowedOwnCodes != null) {
            for (String allowed : allowedOwnCodes) {
                if (TextUtils.equals(code, allowed)) {
                    return false;
                }
            }
        }
        for (String reserved : getReservedCodes(context)) {
            if (reserved.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
