package com.passwordstore.android.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.passwordstore.android.db.dao.StoreDao
import com.passwordstore.android.db.entity.*

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