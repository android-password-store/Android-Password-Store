/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.passwordstore.android.db.dao.StoreDao
import com.passwordstore.android.db.entity.GitRemoteEntity
import com.passwordstore.android.db.entity.PGPKeyEntity
import com.passwordstore.android.db.entity.PasswordEntity
import com.passwordstore.android.db.entity.SSHKeyEntity
import com.passwordstore.android.db.entity.StoreEntity

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
