/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore;

import com.zeapo.pwdstore.utils.Otp;
import junit.framework.TestCase;

public class OtpTest extends TestCase {
    public void testOtp() {
        String code = Otp.calculateCode("JBSWY3DPEHPK3PXP", 0L, "sha1", "s");
        assertEquals("282760", code);
    }
}
