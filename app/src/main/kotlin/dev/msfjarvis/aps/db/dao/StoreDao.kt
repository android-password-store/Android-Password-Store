/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db.dao

import androidx.room.*
import dev.msfjarvis.aps.db.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

  @Insert
  fun insertStore(storeEntity: StoreEntity)

  @Insert
  fun insertMultipleStores(vararg storeEntity: StoreEntity)

  @Insert
  fun insertMultipleStores(storeEntities: List<StoreEntity>)

  @Update
  fun updateStore(storeEntity: StoreEntity)

  @Update
  fun updateMultipleStore(vararg storeEntity: StoreEntity)

  @Update
  fun updateMultipleStore(storeEntities: List<StoreEntity>)

  @Delete
  fun deleteStore(storeEntity: StoreEntity)

  @Delete
  fun deleteMultipleStore(vararg storeEntity: StoreEntity)

  @Delete
  fun deleteMultipleStore(storeEntities: List<StoreEntity>)

  @Query("SELECT * FROM Store")
  fun getAllStores(): Flow<StoreEntity>

  @Query("SELECT * FROM Store WHERE name LIKE :storeName")
  fun getStoreByName(storeName: String): Flow<StoreEntity>

  // This function can be useful when we save the current store id in shared prefs
  //  // Since store names can be same in a db.
  @Query("SELECT * FROM Store WHERE id LIKE :storeId")
  fun getStoreById(storeId: Int): Flow<StoreEntity>

  @Query("SELECT * FROM Store WHERE external = 1")
  fun getAllExternalStores(): Flow<StoreEntity>

  @Query("SELECT * FROM Store WHERE initialized = 1")
  fun getAllInitializedStores(): Flow<StoreEntity>
}
