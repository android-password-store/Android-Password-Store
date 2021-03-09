/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:JvmName("OpenPgpError")

package org.openintents.openpgp

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

public class OpenPgpError() : Parcelable {

    public var errorId: Int = 0
    public var message: String? = null

    private constructor(parcel: Parcel) : this() {
        errorId = parcel.readInt()
        message = parcel.readString()
    }

    internal constructor(errorId: Int, message: String?) : this() {
        this.errorId = errorId
        this.message = message
    }

    internal constructor(b: OpenPgpError) : this() {
        errorId = b.errorId
        message = b.message
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        /**
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * [PARCELABLE_VERSION].
         */
        dest.writeInt(PARCELABLE_VERSION)
        // Inject a placeholder that will store the parcel size from this point on
        // (not including the size itself).
        val sizePosition = dest.dataPosition()
        dest.writeInt(0)
        val startPosition = dest.dataPosition()
        // version 1
        dest.writeInt(errorId)
        dest.writeString(message)
        // Go back and write the size
        val parcelableSize = dest.dataPosition() - startPosition
        dest.setDataPosition(sizePosition)
        dest.writeInt(parcelableSize)
        dest.setDataPosition(startPosition + parcelableSize)
    }

    public companion object CREATOR : Creator<OpenPgpError> {

        /**
         * Since there might be a case where new versions of the client using the library getting
         * old versions of the protocol (and thus old versions of this class), we need a versioning
         * system for the parcels sent between the clients and the providers.
         */
        private const val PARCELABLE_VERSION = 1

        // possible values for errorId
        public const val CLIENT_SIDE_ERROR: Int = -1
        public const val GENERIC_ERROR: Int = 0
        public const val INCOMPATIBLE_API_VERSIONS: Int = 1
        public const val NO_OR_WRONG_PASSPHRASE: Int = 2
        public const val NO_USER_IDS: Int = 3
        public const val OPPORTUNISTIC_MISSING_KEYS: Int = 4

        override fun createFromParcel(source: Parcel): OpenPgpError? {
            source.readInt() // parcelableVersion
            val parcelableSize = source.readInt()
            val startPosition = source.dataPosition()
            val error = OpenPgpError()
            error.errorId = source.readInt()
            error.message = source.readString()
            // skip over all fields added in future versions of this parcel
            source.setDataPosition(startPosition + parcelableSize)
            return error
        }

        override fun newArray(size: Int): Array<OpenPgpError?> {
            return arrayOfNulls(size)
        }
    }
}
