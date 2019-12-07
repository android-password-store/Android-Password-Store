package com.passwordstore.android.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.passwordstore.android.db.entity.StoreEntity

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

    @Delete
    fun deleteStore(storeEntity: StoreEntity)

    @Delete
    fun deleteMultipleStore(storeEntity: StoreEntity)

    @Query("SELECT * FROM Store")
    fun getAllStores(): LiveData<List<StoreEntity>>

    @Query("SELECT * FROM Store WHERE name LIKE :storeName")
    fun getStoreByName(storeName: String): LiveData<List<StoreEntity>>
}