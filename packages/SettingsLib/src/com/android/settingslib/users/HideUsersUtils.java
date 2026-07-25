/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.ext.settings.SecretCodeRegistry;
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
    public static final String DEFAULT_DISABLE_CODE =
            SecretCodeRegistry.DEFAULT_HIDE_USERS_DISABLE_CODE;
    public static final String DEFAULT_SWITCHER_CODE =
            SecretCodeRegistry.DEFAULT_HIDE_USERS_SWITCHER_CODE;

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
        return SecretCodeRegistry.getHideUsersDisableCode(context);
    }

    @NonNull
    public static String getSwitcherCode(@NonNull Context context) {
        return SecretCodeRegistry.getHideUsersSwitcherCode(context);
    }

    /**
     * Validates Dialer-style secret code format: {@code *#} + digits + {@code #}.
     */
    public static boolean isValidSecretCode(@Nullable String code) {
        return !TextUtils.isEmpty(code) && SECRET_CODE_PATTERN.matcher(code).matches();
    }

    /**
     * Validates format and rejects codes currently reserved by stock IMEI/regulatory or
     * ADB data wipe (via {@link SecretCodeRegistry}). Either of this feature's own current
     * codes is allowed so re-saving an existing value succeeds; callers still enforce
     * distinctness between disable and switcher.
     */
    public static boolean isValidSecretCode(@NonNull Context context, @Nullable String code) {
        if (!isValidSecretCode(code)) {
            return false;
        }
        return !SecretCodeRegistry.isReservedConflict(context, code,
                getDisableCode(context), getSwitcherCode(context));
    }

    /**
     * Returns true if {@code disableCode} and {@code switcherCode} are both valid (against
     * currently reserved codes) and distinct.
     */
    public static boolean areCodesValidAndDistinct(@NonNull Context context,
            @Nullable String disableCode, @Nullable String switcherCode) {
        return isValidSecretCode(context, disableCode)
                && isValidSecretCode(context, switcherCode)
                && !TextUtils.equals(disableCode, switcherCode);
    }
}
