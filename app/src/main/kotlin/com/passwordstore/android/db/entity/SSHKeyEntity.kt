/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "SSHKey")
data class SSHKeyEntity(

    @ColumnInfo(name = "priv_key_path")
    val privateKeyPath: String,

    @ColumnInfo(name = "pub_key_path")
    val publicKeyPath: String,

    @ColumnInfo(name = "key_passphrase")
    val KeyPassphrase: String?
)
