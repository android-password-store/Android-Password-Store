/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di.factory

import android.content.Context
import androidx.room.Room
import dev.msfjarvis.aps.db.PasswordStoreDatabase

object RoomFactory {
  fun provideRoom(context: Context): PasswordStoreDatabase {
    synchronized(PasswordStoreDatabase::class) {
     return Room.databaseBuilder(
        context.applicationContext,
        PasswordStoreDatabase::class.java,
        "password_store_database"
      )
        .build()
    }
  }
}
