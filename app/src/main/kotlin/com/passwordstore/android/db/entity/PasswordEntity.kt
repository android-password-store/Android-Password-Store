package com.passwordstore.android.db.entity

import androidx.room.*

@Entity(tableName = "Password",
        foreignKeys = [ForeignKey(
                entity = StoreEntity::class,
                parentColumns = ["id"],
                childColumns = ["store_id"],
                onDelete = ForeignKey.CASCADE)
        ])
data class PasswordEntity (

        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "id")
        val id: Int,

        @ColumnInfo(name = "store_id")
        val storeId: Int,

        @ColumnInfo(name = "name")
        val name: String,

        @ColumnInfo(name = "username")
        val username: String,

        @ColumnInfo(name = "password_location")
        val passwordLocation: String,

        @ColumnInfo(name = "notes")
        val notes: String,

        @Embedded
        val passwordUrl: PasswordURLEntity
)