/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "PGPKey",
        foreignKeys = [ForeignKey(
                entity = StoreEntity::class,
                parentColumns = ["id"],
                childColumns = ["store_id"],
                onDelete = ForeignKey.CASCADE)
        ])
data class PGPKeyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "store_id")
    val storeId: Int,

    @ColumnInfo(name = "key_id")
    val keyId: String
)
