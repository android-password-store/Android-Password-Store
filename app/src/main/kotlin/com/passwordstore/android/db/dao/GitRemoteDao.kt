/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.passwordstore.android.db.entity.GitAuth
import com.passwordstore.android.db.entity.GitRemoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitRemoteDao {

    @Insert
    fun insertRemote(RemoteEntity: GitRemoteEntity)

    @Insert
    fun insertMultipleRemotes(vararg RemoteEntity: GitRemoteEntity)

    @Insert
    fun insertMultipleRemotes(RemoteEntities: List<GitRemoteEntity>)

    @Update
    fun updateRemote(RemoteEntity: GitRemoteEntity)

    @Update
    fun updateMultipleRemotes(vararg giteRemoteEntity: GitRemoteEntity)

    @Update
    fun updateMultipleRemotes(RemoteEntities: List<GitRemoteEntity>)

    @Delete
    fun deleteRemote(RemoteEntity: GitRemoteEntity)

    @Delete
    fun deleteMultipleRemotes(vararg RemoteEntity: GitRemoteEntity)

    @Delete
    fun deleteMultipleRemotes(RemoteEntities: List<GitRemoteEntity>)

    @Query("SELECT * FROM Remote")
    fun getAllRemotes(): Flow<List<GitRemoteEntity>>

    @Query("SELECT * FROM GitRemote WHERE name LIKE :name")
    fun getRemotesByName(name: String): Flow<List<GitRemoteEntity>>

    @Query("SELECT * FROM GitRemote WHERE auth LIKE :auth")
    fun getRemotesByAuthType(auth: GitAuth): Flow<List<GitRemoteEntity>>

    @Query("SELECT * FROM GitRemote WHERE id LIKE :id")
    fun getRemoteById(id: Int?): Flow<GitRemoteEntity>

    @Query("SELECT * FROM GitRemote WHERE store_id LIKE :storeId")
    fun getRemotesByStore(storeId: Int?): Flow<List<GitRemoteEntity>>
}
