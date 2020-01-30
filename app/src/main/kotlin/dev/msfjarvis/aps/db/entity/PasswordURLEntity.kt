/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "PasswordUrl",
        foreignKeys = [ForeignKey(
                entity = PasswordEntity::class,
                parentColumns = ["id"],
                childColumns = ["password_id"],
                onDelete = ForeignKey.CASCADE)
        ])
data class PasswordURLEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "store_id")
    val storeId: Int,

    @ColumnInfo(name = "url")
    val url: String
)
