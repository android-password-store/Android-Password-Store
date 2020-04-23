/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di.modules

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dev.msfjarvis.aps.StoreRepository
import dev.msfjarvis.aps.db.PasswordStoreDatabase
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.di.factory.RoomFactory
import javax.inject.Singleton


@Module
object RoomModule {
  @Provides
  @Singleton
  fun provideStoreDatabase(context: Context): PasswordStoreDatabase = RoomFactory.provideRoom(context)

  @Provides
  @Singleton
  fun provideStoreDao(storeDatabase: PasswordStoreDatabase): StoreDao = storeDatabase.getStoreDao()

  @Provides
  @Singleton
  fun storeRepository(storeDao: StoreDao, prefs: SharedPreferences): StoreRepository = StoreRepository(storeDao, prefs)
}
