/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.user

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.systemui.user.domain.interactor.UserSwitcherInteractor
import javax.inject.Inject

class UserDialogReceiver
@Inject
constructor(private val userSwitcherInteractor: UserSwitcherInteractor) : BroadcastReceiver() {
    companion object {
        private const val TAG = "UserDialogReceiver"
        const val LAUNCH_USER_SWITCHER_DIALOG =
            "com.android.systemui.action.LAUNCH_USER_SWITCHER_DIALOG"
        /** When true, include Hide Users snapshot-hidden users in the switcher for this session. */
        const val EXTRA_SHOW_HIDDEN_USERS = "show_hidden_users"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.getAction()) {
                LAUNCH_USER_SWITCHER_DIALOG -> {
                    if (intent.getBooleanExtra(EXTRA_SHOW_HIDDEN_USERS, false)) {
                        userSwitcherInteractor.setShowHiddenUsersSession(true)
                    }
                    userSwitcherInteractor.showUserSwitcher(null, context)
                }
                else -> Log.e(TAG, "Unknown action " + intent.getAction())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ", e)
        }
    }
}
