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

package com.android.systemui.screenshot;

import android.content.Context;
import android.ext.settings.ExtSettings;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;

import java.io.File;

/**
 * Helpers for the per-user {@link ExtSettings#SCREENSHOT_SAVE_LOCATION} preference that
 * chooses between the default Pictures/Screenshots MediaStore path and Shared/Screenshots.
 */
public final class ScreenshotSaveLocation {
    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_SHARED = "shared";

    /** MediaStore {@code RELATIVE_PATH} for screenshots under Shared storage. */
    public static final String SHARED_SCREENSHOTS_PATH =
            "Shared" + File.separator + Environment.DIRECTORY_SCREENSHOTS;

    private ScreenshotSaveLocation() {}

    /** Whether the export owner has selected Shared as the screenshot destination. */
    public static boolean isSharedSelected(Context context, UserHandle owner) {
        return VALUE_SHARED.equals(
                ExtSettings.SCREENSHOT_SAVE_LOCATION.get(context, owner.getIdentifier()));
    }

    /** Whether Shared encrypted storage is opted-in for {@code owner}. */
    public static boolean isSharedOptedIn(Context context, UserHandle owner) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        return userManager != null
                && userManager.isSharedEncryptedStorageEnabled(owner.getIdentifier());
    }

    /**
     * True when Shared save is selected for {@code owner}, Shared encrypted storage is enabled
     * for that user, and Shared storage is currently unlocked.
     */
    public static boolean shouldSaveToShared(Context context, UserHandle owner) {
        if (!isSharedSelected(context, owner) || !isSharedOptedIn(context, owner)) {
            return false;
        }
        final StorageManager storageManager = context.getSystemService(StorageManager.class);
        return storageManager != null && storageManager.isSharedEncryptedStorageUnlocked();
    }
}
