package com.zeapo.pwdstore;

import com.zeapo.pwdstore.utils.Otp;

import junit.framework.TestCase;

public class OtpTest extends TestCase {
    public void testOtp() {
        String code = Otp.calculateCode("JBSWY3DPEHPK3PXP", 0L);
        assertEquals("282760", code);
    }
}
