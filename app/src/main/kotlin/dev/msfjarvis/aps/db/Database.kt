/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.db.entity.*

@Database(entities = [
  StoreEntity::class,
  GitRemoteEntity::class,
  SSHKeyEntity::class,
  PGPKeyEntity::class,
  PasswordEntity::class
], version = 1)
abstract class Database : RoomDatabase() {
  abstract fun getStoreDao(): StoreDao
}
