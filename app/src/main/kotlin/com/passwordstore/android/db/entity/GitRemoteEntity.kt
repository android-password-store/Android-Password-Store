/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.passwordstore.android.db.converter.GitAuthConverter

@TypeConverters(GitAuthConverter::class)
@Entity(tableName = "GitRemote",
        indices = [Index("store_id"), Index("name")],
        foreignKeys = [ForeignKey(
                entity = StoreEntity::class,
                parentColumns = ["id"],
                childColumns = ["store_id"],
                onDelete = ForeignKey.CASCADE)
        ])
data class GitRemoteEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "store_id")
    val storeId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "auth")
    val auth: GitAuth,

    @Embedded(prefix = "ssh")
    val sshKey: SSHKeyEntity
)
