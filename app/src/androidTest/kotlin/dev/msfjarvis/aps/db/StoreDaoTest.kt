/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.asLiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.msfjarvis.aps.db.dao.StoreDao
import dev.msfjarvis.aps.db.entity.StoreEntity
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class StoreDaoTest {
  @get:Rule
  var rule: TestRule = InstantTaskExecutorRule()

  private lateinit var storeDao: StoreDao
  private lateinit var db: TestDatabase

  @Before
  fun createDB() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).allowMainThreadQueries().build()
    storeDao = db.getStoreDao()
  }

  @After
  @Throws(IOException::class)
  fun closeDb() {
    db.close()
  }

  @Test
  @Throws(Exception::class)
  fun testInsertStore() {
    val store = StoreEntity(0, "store", Uri.EMPTY, false, true, false)
    storeDao.insertStore(store)
    val byName = storeDao.getStoreByName("store").asLiveData().blockingObserve()
    assertThat(byName?.get(0)?.name, equalTo(store.name))
  }

  @Test
  @Throws(Exception::class)
  fun testInsertMultipleStores() {
    val storeEntries = arrayListOf<StoreEntity>()
    for (i in 0 until 7) {
      storeEntries.add(StoreEntity(0, "store$i", Uri.EMPTY, false, true, false))
    }
    storeDao.insertMultipleStores(storeEntries)
    storeDao.insertMultipleStores(storeEntries[5], storeEntries[6])
    val byName = storeDao.getAllStores().asLiveData().blockingObserve()
    for (i in 0 until 7) {
      assertThat(byName?.get(i)?.name, equalTo(storeEntries[i].name))
    }
  }

  @Test
  @Throws(Exception::class)
  fun testGetStoreByName() {
    val storeEntries = arrayListOf<StoreEntity>()
    for (i in 0 until 5) {
      storeEntries.add(StoreEntity(0, "store", Uri.EMPTY, false, true, false))
    }
    storeEntries.add(StoreEntity(0, "notStore", Uri.EMPTY, false, true, false))
    storeDao.insertMultipleStores(storeEntries)
    val byName = storeDao.getStoreByName("store").asLiveData().blockingObserve()
    assertThat(byName?.size, equalTo(5))
  }

  @Test
  @Throws(Exception::class)
  fun testGetStoreById() {
    val storeEntry = StoreEntity(0, "store", Uri.EMPTY, false, true, false)
    storeDao.insertStore(storeEntry)
    val byId = storeDao.getAllStores().asLiveData().blockingObserve()
    assertThat(storeEntry.name, equalTo(byId?.get(0)?.name))
  }
}
