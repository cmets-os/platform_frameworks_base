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

import android.content.ContentResolver;
import android.content.Context;
import android.ext.settings.ExtSettings;
import android.net.Uri;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Resolves the DocumentsContract tree URI for saving screenshots under Shared when the
 * per-user {@link ExtSettings#SCREENSHOT_SAVE_LOCATION} preference requests it.
 */
public final class ScreenshotSaveLocation {
    private static final String TAG = LogConfig.logTag(ScreenshotSaveLocation.class);

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_SHARED = "shared";

    private static final String SHARED_DIR = "Shared";
    private static final String SHARED_DOC_ID =
            DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID + ":" + SHARED_DIR;
    private static final String SHARED_SCREENSHOTS_DOC_ID =
            SHARED_DOC_ID + "/" + Environment.DIRECTORY_SCREENSHOTS;

    private ScreenshotSaveLocation() {}

    /** Whether the export owner has selected Shared as the screenshot destination. */
    public static boolean isSharedSelected(Context context, UserHandle owner) {
        return VALUE_SHARED.equals(
                ExtSettings.SCREENSHOT_SAVE_LOCATION.get(context, owner.getIdentifier()));
    }

    /** True if {@code uri} is a DocumentsContract tree under {@code primary:Shared}. */
    public static boolean isSharedTreeUri(@Nullable Uri uri) {
        if (uri == null || !DocumentsContract.isTreeUri(uri)) {
            return false;
        }
        final String docId = DocumentsContract.getTreeDocumentId(uri);
        return docId != null
                && (SHARED_DOC_ID.equals(docId) || docId.startsWith(SHARED_DOC_ID + "/"));
    }

    /**
     * Returns a DocumentsContract tree URI for {@code primary:Shared/Screenshots} when Shared
     * save is selected, opted-in for {@code owner}, and Shared storage is unlocked; otherwise
     * {@code null} (caller should use the default MediaStore path).
     */
    @Nullable
    public static Uri resolveSharedSaveUri(Context context, UserHandle owner) {
        if (!isSharedSelected(context, owner)) {
            return null;
        }
        final int userId = owner.getIdentifier();
        final UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager == null || !userManager.isSharedEncryptedStorageEnabled(userId)) {
            return null;
        }
        final StorageManager storageManager = context.getSystemService(StorageManager.class);
        if (storageManager == null || !storageManager.isSharedEncryptedStorageUnlocked()) {
            return null;
        }

        try {
            final Context userContext = context.createContextAsUser(owner, 0);
            final ContentResolver resolver = userContext.getContentResolver();
            final Uri sharedTree = DocumentsContract.buildTreeDocumentUri(
                    DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY, SHARED_DOC_ID);
            final Uri sharedDoc = DocumentsContract.buildDocumentUriUsingTree(
                    sharedTree, SHARED_DOC_ID);
            try {
                DocumentsContract.createDocument(
                        resolver,
                        sharedDoc,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        Environment.DIRECTORY_SCREENSHOTS);
            } catch (Exception e) {
                // Directory may already exist; image create will fail and fall back if not.
                Log.d(TAG, "Screenshots dir create under Shared skipped/failed", e);
            }
            return DocumentsContract.buildTreeDocumentUri(
                    DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                    SHARED_SCREENSHOTS_DOC_ID);
        } catch (Exception e) {
            Log.w(TAG, "Unable to resolve Shared screenshot save URI for user " + userId, e);
            return null;
        }
    }
}
