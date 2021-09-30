/*
 * Copyright © 2019 The Android Password Authors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.openintents.openpgp;

interface IOpenPgpService2 {

    /**
     * see org.openintents.openpgp.util.OpenPgpApi for documentation
     */
    ParcelFileDescriptor createOutputPipe(in int pipeId);

    /**
     * see org.openintents.openpgp.util.OpenPgpApi for documentation
     */
    Intent execute(in Intent data, in ParcelFileDescriptor input, int pipeId);
}
