/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps

import dagger.Reusable
import dev.msfjarvis.aps.db.dao.StoreDao
import javax.inject.Inject

@Reusable
class StoreRepository @Inject constructor(storeDao: StoreDao) {

}