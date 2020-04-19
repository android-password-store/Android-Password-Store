/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db.dao

import androidx.room.*
import dev.msfjarvis.aps.db.entity.PasswordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

  @Insert
  fun insertPassword(passwordEntity: PasswordEntity)

  @Insert
  fun insertMultiplePasswords(vararg passwordEntity: PasswordEntity)

  @Insert
  fun insertMultiplePasswords(passwordEntities: List<PasswordEntity>)

  @Update
  fun updatePassword(passwordEntity: PasswordEntity)

  @Update
  fun updateMultiplePasswords(vararg passwordEntity: PasswordEntity)

  @Update
  fun updateMultiplePasswords(passwordEntities: List<PasswordEntity>)

  @Delete
  fun deletePassword(passwordEntity: PasswordEntity)

  @Delete
  fun deleteMultiplePasswords(vararg passwordEntity: PasswordEntity)

  @Delete
  fun deleteMultiplePasswords(passwordEntities: List<PasswordEntity>)

  @Query("SELECT * FROM Password")
  fun getAllPasswords(): Flow<PasswordEntity>

  @Query("SELECT * FROM Password WHERE name LIKE :name")
  fun getPasswordsByName(name: String): Flow<PasswordEntity>

  @Query("SELECT * FROM Password WHERE username LIKE :username")
  fun getPasswordsByUsername(username: String): Flow<PasswordEntity>

  @Query("SELECT * FROM Password WHERE id LIKE :id")
  fun getPasswordById(id: Int): Flow<PasswordEntity>

  @Query("SELECT * FROM Password WHERE password_location LIKE :location")
  fun getPasswordsByLocation(location: String): Flow<PasswordEntity>

  @Query("SELECT * FROM Password WHERE store_id LIKE :storeId")
  fun getPasswordsByStore(storeId: Int): Flow<PasswordEntity>
}
