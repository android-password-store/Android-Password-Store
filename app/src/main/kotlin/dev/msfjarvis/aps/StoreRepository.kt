/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps

import android.util.Log
import dagger.Reusable
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.db.entity.StoreEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Reusable
class StoreRepository @Inject constructor(private val storeDao: StoreDao) {

  suspend fun addStoreToDB(storeEntity: StoreEntity) {
    withContext(Dispatchers.IO) {
      storeDao.insertStore(storeEntity)
    }
    withContext(Dispatchers.Main) {
      Log.d("StoreRepository", "Store added to DB")
    }
  }
}
