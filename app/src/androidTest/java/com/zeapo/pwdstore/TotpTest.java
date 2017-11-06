package com.zeapo.pwdstore;

import com.zeapo.pwdstore.utils.Totp;

import junit.framework.TestCase;

public class TotpTest extends TestCase {
    public void testTotp() {
        String code = Totp.calculateCode("JBSWY3DPEHPK3PXP", 0L);
        assertEquals("282760", code);
    }
}
