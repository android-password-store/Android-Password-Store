/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.passwordstore.android.db.entity.PGPKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PGPKeyDao {

    @Insert
    fun insertKey(pgpKeyEntity: PGPKeyEntity)

    @Insert
    fun insertMultipleKeys(vararg pgpKeyEntity: PGPKeyEntity)

    @Insert
    fun insertMultipleKeys(pgpKeyEntities: List<PGPKeyEntity>)

    @Update
    fun updateKey(pgpKeyEntity: PGPKeyEntity)

    @Update
    fun updateMultipleKeys(vararg pgpKeyEntity: PGPKeyEntity)

    @Update
    fun updateMultipleKeys(pgpKeyEntities: List<PGPKeyEntity>)

    @Delete
    fun deleteKey(pgpKeyEntity: PGPKeyEntity)

    @Delete
    fun deleteMultipleKeys(vararg pgpKeyEntity: PGPKeyEntity)

    @Delete
    fun deleteMultipleKeys(pgpKeyEntities: List<PGPKeyEntity>)

    @Query("SELECT * FROM PGPKey")
    fun getAllKeys(): Flow<List<PGPKeyEntity>>

    @Query("SELECT * FROM PGPKey WHERE key_id LIKE :keyId")
    fun getKeyById(keyId: String): Flow<PGPKeyEntity>

    @Query("SELECT * FROM PGPKey WHERE id LIKE :databaseId")
    fun getKeyByDatabaseId(databaseId: Int?): Flow<PGPKeyEntity>

    @Query("SELECT * FROM PGPKey WHERE store_id LIKE :storeId")
    fun getKeysByStore(storeId: Int?): Flow<List<PGPKeyEntity>>
}
