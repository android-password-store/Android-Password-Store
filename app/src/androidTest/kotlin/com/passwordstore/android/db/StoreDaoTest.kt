/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passwordstore.android.db.dao.StoreDao
import com.passwordstore.android.db.entity.StoreEntity
import java.io.IOException
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

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
    fun writeSingleEntry() {
        val store = StoreEntity(name = "store", external = false, initialized = true)
        storeDao.insertStore(store)
        val byName = storeDao.getStoreByName("store").blockingObserve()
        assertThat(byName?.get(0)?.name, equalTo(store.name))
    }

    @Test
    @Throws(Exception::class)
    fun writeMultipleEntries() {
        val storeEntries = arrayListOf<StoreEntity>()
        for (i in 0 until 5) {
            storeEntries.add(StoreEntity(name = "store$i", external = false, initialized = false))
        }
        storeDao.insertMultipleStores(storeEntries)
        val byName = storeDao.getAllStores().blockingObserve()
        for (i in 0 until 5) {
            assertThat(byName?.get(i)?.name, equalTo(storeEntries[i].name))
        }
    }

    @Test
    @Throws(Exception::class)
    fun getStoreByName() {
        val storeEntries = arrayListOf<StoreEntity>()
        for (i in 0 until 5) {
            storeEntries.add(StoreEntity(name = "store", external = false, initialized = false))
        }
        storeEntries.add(StoreEntity(name = "notStore", external = false, initialized = false))
        storeDao.insertMultipleStores(storeEntries)
        val byName = storeDao.getStoreByName("store").blockingObserve()
        assertThat(byName?.size, equalTo(5))
    }
}
