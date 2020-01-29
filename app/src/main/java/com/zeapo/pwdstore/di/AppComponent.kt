/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.PasswordStore
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Reusable
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance applicationContext: Context): AppComponent
    }

    fun inject(activity: PasswordStore)
}

@Module
object AppModule {

    @Provides
    @Reusable
    fun provideSharedPrefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
}
