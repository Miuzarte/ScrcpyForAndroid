/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */
package com.termux.view.support

import android.widget.PopupWindow

/**
 * Implementation of PopupWindow compatibility that can call Gingerbread APIs.
 * https://chromium.googlesource.com/android_tools/+/HEAD/sdk/extras/android/support/v4/src/gingerbread/android/support/v4/widget/PopupWindowCompatGingerbread.java
 */
object PopupWindowCompatGingerbread {

    private var setWindowLayoutTypeMethod: java.lang.reflect.Method? = null
    private var setWindowLayoutTypeMethodAttempted = false
    private var getWindowLayoutTypeMethod: java.lang.reflect.Method? = null
    private var getWindowLayoutTypeMethodAttempted = false

    @JvmStatic
    fun setWindowLayoutType(popupWindow: PopupWindow, layoutType: Int) {
        if (!setWindowLayoutTypeMethodAttempted) {
            try {
                setWindowLayoutTypeMethod = PopupWindow::class.java.getDeclaredMethod(
                    "setWindowLayoutType", Int::class.javaPrimitiveType
                )
                setWindowLayoutTypeMethod?.isAccessible = true
            } catch (e: Exception) {
                // Reflection method fetch failed. Oh well.
            }
            setWindowLayoutTypeMethodAttempted = true
        }
        setWindowLayoutTypeMethod?.let {
            try {
                it.invoke(popupWindow, layoutType)
            } catch (e: Exception) {
                // Reflection call failed. Oh well.
            }
        }
    }

    @JvmStatic
    fun getWindowLayoutType(popupWindow: PopupWindow): Int {
        if (!getWindowLayoutTypeMethodAttempted) {
            try {
                getWindowLayoutTypeMethod = PopupWindow::class.java.getDeclaredMethod(
                    "getWindowLayoutType"
                )
                getWindowLayoutTypeMethod?.isAccessible = true
            } catch (e: Exception) {
                // Reflection method fetch failed. Oh well.
            }
            getWindowLayoutTypeMethodAttempted = true
        }
        getWindowLayoutTypeMethod?.let {
            try {
                return it.invoke(popupWindow) as Int
            } catch (e: Exception) {
                // Reflection call failed. Oh well.
            }
        }
        return 0
    }
}
