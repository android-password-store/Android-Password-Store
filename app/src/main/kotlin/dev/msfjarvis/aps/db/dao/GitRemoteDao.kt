/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db.dao

import androidx.room.*
import dev.msfjarvis.aps.db.entity.GitAuth
import dev.msfjarvis.aps.db.entity.GitRemoteEntity
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

  @Query("SELECT * FROM GitRemote")
  fun getAllRemotes(): Flow<GitRemoteEntity>

  @Query("SELECT * FROM GitRemote WHERE name LIKE :name")
  fun getRemotesByName(name: String): Flow<GitRemoteEntity>

  @Query("SELECT * FROM GitRemote WHERE auth LIKE :auth")
  fun getRemotesByAuthType(auth: GitAuth): Flow<GitRemoteEntity>

  @Query("SELECT * FROM GitRemote WHERE id LIKE :id")
  fun getRemoteById(id: Int): Flow<GitRemoteEntity>

  @Query("SELECT * FROM GitRemote WHERE store_id LIKE :storeId")
  fun getRemotesByStore(storeId: Int): Flow<GitRemoteEntity>
}
