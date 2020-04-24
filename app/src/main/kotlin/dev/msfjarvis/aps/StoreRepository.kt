/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.ajalt.timberkt.d
import dagger.Reusable
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.db.entity.StoreEntity
import dev.msfjarvis.aps.utils.PreferenceKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Reusable
class StoreRepository @Inject constructor(private val storeDao: StoreDao, private val prefs: SharedPreferences) {

  private val _currentStore: MutableLiveData<StoreEntity> = MutableLiveData()
  val currentStore: LiveData<StoreEntity> get() = _currentStore

  private val job = Job()
  private val scope = CoroutineScope(job + Dispatchers.Main)

  init {
    val storeId = getCurrentStore()
    if (storeId != -1) {
      scope.launch {
        withContext(Dispatchers.IO) {
          val store = storeDao.getStoreById(storeId).first()
          _currentStore.postValue(store)
        }
      }
    }
  }

  suspend fun addStoreToDB(storeEntity: StoreEntity, setCurrent: Boolean = false) {
    withContext(Dispatchers.IO) {
      storeDao.insertStore(storeEntity)
    }
    withContext(Dispatchers.Main) {
      d { "Store added to DB" }
      if (setCurrent) setCurrentStore(storeEntity.id)
    }
  }

  suspend fun removeStoreFromDB(storeEntity: StoreEntity) {
    withContext(Dispatchers.IO) {
      if (storeEntity.id == getCurrentStore()) {
        val storeList = storeDao.getAllStores()
        setCurrentStore(storeList.first().id)
      }
      storeDao.deleteStore(storeEntity)
    }
    withContext(Dispatchers.Main) {
      d { "Store removed from DB" }
    }
  }

  private fun setCurrentStore(id: Int) {
    prefs.edit {
      putInt(PreferenceKeys.CURRENT_STORE_ID, id);
      apply()
    }
  }

  private fun getCurrentStore(): Int {
    return prefs.getInt(PreferenceKeys.CURRENT_STORE_ID, -1);
  }

  public fun onClear() {
    scope.cancel()
  }
}
