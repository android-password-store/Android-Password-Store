/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.git

import android.view.View
import android.widget.RadioGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.google.android.material.button.MaterialButtonToggleGroup
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.BaseGitActivity.GitUpdateUrlResult
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class GitServerConfigActivityTest {

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(GitServerConfigActivity::class.java, true)

    private val activity get() = activityRule.activity

    @Test
    fun invalidValuesFailPredictably() {
        setDefaultSshValues()
        onView(withId(R.id.server_port)).perform(replaceText("69420"))
        assertEquals(activity.updateUrl(), GitUpdateUrlResult.CustomPortRequiresAbsoluteUrlError)

        setDefaultSshValues()
        onView(withId(R.id.server_url)).perform(replaceText(""))
        assertEquals(activity.updateUrl(), GitUpdateUrlResult.EmptyHostnameError)

        setDefaultSshValues()
        onView(withId(R.id.server_port)).perform(replaceText("xyz_is_not_a_port"))
        assertEquals(activity.updateUrl(), GitUpdateUrlResult.NonNumericPortError)

        setDefaultHttpsValues()
        onView(withId(R.id.server_port)).perform(replaceText("xyz_is_not_a_port"))
        assertEquals(activity.updateUrl(), GitUpdateUrlResult.NonNumericPortError)

        setDefaultHttpsValues()
        onView(withId(R.id.server_url)).perform(replaceText(""))
        assertEquals(activity.updateUrl(), GitUpdateUrlResult.EmptyHostnameError)
    }

    @Test
    fun urlIsConstructedCorrectly() {
        setDefaultSshValues()
        activity.updateUrl()
        assertEquals("john_doe@github.com:john_doe/my_secret_repository", activity.url)

        setDefaultSshValues()
        onView(withId(R.id.server_port)).perform(replaceText("69420"))
        onView(withId(R.id.server_url)).perform(replaceText("192.168.0.102"))
        onView(withId(R.id.server_path)).perform(replaceText("/home/john_doe/my_secret_repository"))
        activity.updateUrl()
        assertEquals("ssh://john_doe@192.168.0.102:69420/home/john_doe/my_secret_repository", activity.url)

        setDefaultHttpsValues()
        activity.updateUrl()
        assertEquals("https://github.com/john_doe/my_secret_repository", activity.url)

        setDefaultHttpsValues()
        onView(withId(R.id.server_port)).perform(replaceText("69420"))
        onView(withId(R.id.server_url)).perform(replaceText("192.168.0.102"))
        onView(withId(R.id.server_path)).perform(replaceText("/home/john_doe/my_secret_repository"))
        activity.updateUrl()
        assertEquals("https://192.168.0.102:69420/home/john_doe/my_secret_repository", activity.url)
    }

    private fun <T> callMethod(message: String = "", viewMethod: (view: T) -> Unit): ViewAction {
        return object : ViewAction {
            override fun getDescription(): String {
                return if (message.isBlank()) viewMethod.toString() else message
            }

            override fun getConstraints(): Matcher<View> {
                return isEnabled()
            }

            @Suppress("UNCHECKED_CAST")
            override fun perform(uiController: UiController?, view: View?) {
                viewMethod(view!! as T)
            }

        }
    }

    private fun setDefaultHttpsValues() {
        onView(withId(R.id.clone_protocol_group)).perform(callMethod<MaterialButtonToggleGroup> {
            it.check(R.id.clone_protocol_https)
        })
        onView(withId(R.id.connection_mode_group)).perform(callMethod<RadioGroup> {
            it.check(R.id.connection_mode_password)
        })
        onView(withId(R.id.server_path)).perform(replaceText("john_doe/my_secret_repository"))
        onView(withId(R.id.server_port)).perform(replaceText(""))
        onView(withId(R.id.server_url)).perform(replaceText("github.com"))
        onView(withId(R.id.server_user)).perform(replaceText("john_doe"))
    }

    private fun setDefaultSshValues() {
        onView(withId(R.id.clone_protocol_group)).perform(callMethod<MaterialButtonToggleGroup> {
            it.check(R.id.clone_protocol_ssh)
        })
        onView(withId(R.id.connection_mode_group)).perform(callMethod<RadioGroup> {
            it.check(R.id.connection_mode_ssh_key)
        })
        onView(withId(R.id.server_path)).perform(replaceText("john_doe/my_secret_repository"))
        onView(withId(R.id.server_port)).perform(replaceText(""))
        onView(withId(R.id.server_url)).perform(replaceText("github.com"))
        onView(withId(R.id.server_user)).perform(replaceText("john_doe"))
    }
}
