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

package com.android.settingslib.users;

import android.content.Context;
import android.content.pm.UserInfo;
import android.ext.settings.AdbDataWipeUtils;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helpers for the Hide Users feature: Global flag, secret Dialer codes, and UI list filtering.
 *
 * @hide
 */
public final class HideUsersUtils {
    public static final String DEFAULT_DISABLE_CODE = "*#8321#";
    public static final String DEFAULT_SWITCHER_CODE = "*#8322#";

    /** Reserved for stock IMEI / regulatory and ADB data wipe disable. */
    private static final String[] RESERVED_CODES = {
            "*#06#",
            "*#07#",
            "*#8331#",
    };

    private static final Pattern SECRET_CODE_PATTERN = Pattern.compile("\\*#[0-9]+#");

    private HideUsersUtils() {}

    public static boolean isFeatureEnabled(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.HIDE_USERS, 0) != 0;
    }

    public static boolean isUiHidden(@Nullable UserInfo user) {
        return user != null && user.isUiHidden();
    }

    /** Drop snapshot-hidden users from UI lists. Guest and post-enable users pass through. */
    @NonNull
    public static List<UserInfo> filterUiUsers(@Nullable List<UserInfo> users) {
        if (users == null || users.isEmpty()) {
            return users == null ? new ArrayList<>() : users;
        }
        final List<UserInfo> result = new ArrayList<>(users.size());
        for (UserInfo user : users) {
            if (!isUiHidden(user)) {
                result.add(user);
            }
        }
        return result;
    }

    @NonNull
    public static String getDisableCode(@NonNull Context context) {
        final String stored = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.HIDE_USERS_CODE_DISABLE);
        return TextUtils.isEmpty(stored) ? DEFAULT_DISABLE_CODE : stored;
    }

    @NonNull
    public static String getSwitcherCode(@NonNull Context context) {
        final String stored = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.HIDE_USERS_CODE_SWITCHER);
        return TextUtils.isEmpty(stored) ? DEFAULT_SWITCHER_CODE : stored;
    }

    /**
     * Validates a Dialer-style secret code: {@code *#} + digits + {@code #}.
     * Rejects reserved IMEI/regulatory and ADB wipe default sequences.
     */
    public static boolean isValidSecretCode(@Nullable String code) {
        if (TextUtils.isEmpty(code) || !SECRET_CODE_PATTERN.matcher(code).matches()) {
            return false;
        }
        for (String reserved : RESERVED_CODES) {
            if (reserved.equals(code)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Like {@link #isValidSecretCode(String)}, and also rejects the currently configured
     * ADB data wipe disable code (which may differ from the default reserved value).
     */
    public static boolean isValidSecretCode(@NonNull Context context, @Nullable String code) {
        if (!isValidSecretCode(code)) {
            return false;
        }
        return !TextUtils.equals(code, AdbDataWipeUtils.getDisableCode(context));
    }

    /**
     * Returns true if {@code disableCode} and {@code switcherCode} are both valid and distinct.
     */
    public static boolean areCodesValidAndDistinct(
            @Nullable String disableCode, @Nullable String switcherCode) {
        return isValidSecretCode(disableCode)
                && isValidSecretCode(switcherCode)
                && !TextUtils.equals(disableCode, switcherCode);
    }
}
