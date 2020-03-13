/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.db.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import dev.msfjarvis.aps.db.converter.UriConverter

@Entity(tableName = "Store")
@TypeConverters(UriConverter::class)
data class StoreEntity(

  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  val id: Int = 0,

  @ColumnInfo(name = "name")
  val name: String,

  @ColumnInfo(name = "uri")
  val uri: Uri? = null,

  @ColumnInfo(name = "external")
  val external: Boolean,

  @ColumnInfo(name = "initialized")
  val initialized: Boolean,

  @ColumnInfo(name = "git_store")
  val isGitStore: Boolean
)
