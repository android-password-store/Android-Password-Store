/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.db.entity.GitRemoteEntity
import dev.msfjarvis.aps.db.entity.PGPKeyEntity
import dev.msfjarvis.aps.db.entity.PasswordEntity
import dev.msfjarvis.aps.db.entity.SSHKeyEntity
import dev.msfjarvis.aps.db.entity.StoreEntity

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
