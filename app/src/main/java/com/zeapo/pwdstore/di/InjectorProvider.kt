/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.di

import android.app.Service
import android.content.ContentProvider
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference

interface InjectorProvider {
    val component: AppComponent
}

val ContentProvider.injector get() = (context?.applicationContext as InjectorProvider).component
val FragmentActivity.injector get() = (application as InjectorProvider).component
val Fragment.injector get() = (requireContext().applicationContext as InjectorProvider).component
val Preference.injector get() = (context.applicationContext as InjectorProvider).component
val Service.injector get() = (applicationContext as InjectorProvider).component
fun getInjector(context: Context) = (context.applicationContext as InjectorProvider).component
