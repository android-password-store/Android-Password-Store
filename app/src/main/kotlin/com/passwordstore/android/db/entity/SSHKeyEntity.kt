/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "SSHKey")
data class SSHKeyEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "priv_key_path")
    val privateKeyPath: String,

    @ColumnInfo(name = "pub_key_path")
    val publicKeyPath: String,

    @ColumnInfo(name = "key_passphrase")
    val KeyPassphrase: String?
)
