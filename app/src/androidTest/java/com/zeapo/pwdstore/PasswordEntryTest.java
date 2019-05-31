package com.zeapo.pwdstore;

import junit.framework.TestCase;

public class PasswordEntryTest extends TestCase {

    public void testGetPassword() {
        assertEquals("fooooo", new PasswordEntry("fooooo\nbla\n").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo\nbla").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo\n").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo").getPassword());
        assertEquals("", new PasswordEntry("\nblubb\n").getPassword());
        assertEquals("", new PasswordEntry("\nblubb").getPassword());
        assertEquals("", new PasswordEntry("\n").getPassword());
        assertEquals("", new PasswordEntry("").getPassword());
    }

    public void testGetExtraContent() {
        assertEquals("bla\n", new PasswordEntry("fooooo\nbla\n").getExtraContent());
        assertEquals("bla", new PasswordEntry("fooooo\nbla").getExtraContent());
        assertEquals("", new PasswordEntry("fooooo\n").getExtraContent());
        assertEquals("", new PasswordEntry("fooooo").getExtraContent());
        assertEquals("blubb\n", new PasswordEntry("\nblubb\n").getExtraContent());
        assertEquals("blubb", new PasswordEntry("\nblubb").getExtraContent());
        assertEquals("", new PasswordEntry("\n").getExtraContent());
        assertEquals("", new PasswordEntry("").getExtraContent());
    }

    public void testGetUsername() {
        assertEquals("username", new PasswordEntry("secret\nextra\nlogin: username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nextra\nusername: username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nUSERNaMe:  username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nLOGiN:username").getUsername());
        assertNull(new PasswordEntry("secret\nextra\ncontent\n").getUsername());
    }

    public void testHasUsername() {
        assertTrue(new PasswordEntry("secret\nextra\nlogin: username\ncontent\n").hasUsername());
        assertFalse(new PasswordEntry("secret\nextra\ncontent\n").hasUsername());
        assertFalse(new PasswordEntry("secret\nlogin failed\n").hasUsername());
        assertFalse(new PasswordEntry("\n").hasUsername());
        assertFalse(new PasswordEntry("").hasUsername());
    }

    public void testNoTotpUriPresent() {
        PasswordEntry entry = new PasswordEntry("secret\nextra\nlogin: username\ncontent");
        assertFalse(entry.hasTotp());
        assertNull(entry.getTotpSecret());
    }

    public void testTotpUriInPassword() {
        PasswordEntry entry = new PasswordEntry("otpauth://totp/test?secret=JBSWY3DPEHPK3PXP");
        assertTrue(entry.hasTotp());
        assertEquals("JBSWY3DPEHPK3PXP", entry.getTotpSecret());
    }

    public void testTotpUriInContent() {
        PasswordEntry entry = new PasswordEntry("secret\nusername: test\notpauth://totp/test?secret=JBSWY3DPEHPK3PXP");
        assertTrue(entry.hasTotp());
        assertEquals("JBSWY3DPEHPK3PXP", entry.getTotpSecret());
    }

    public void testNoHotpUriPresent() {
        PasswordEntry entry = new PasswordEntry("secret\nextra\nlogin: username\ncontent");
        assertFalse(entry.hasHotp());
        assertNull(entry.getHotpSecret());
        assertNull(entry.getHotpCounter());
    }

    public void testHotpUriInPassword() {
        PasswordEntry entry = new PasswordEntry("otpauth://hotp/test?secret=JBSWY3DPEHPK3PXP&counter=25");
        assertTrue(entry.hasHotp());
        assertEquals("JBSWY3DPEHPK3PXP", entry.getHotpSecret());
        assertEquals(new Long(25 ), entry.getHotpCounter());
    }

    public void testHotpUriInContent() {
        PasswordEntry entry = new PasswordEntry("secret\nusername: test\notpauth://hotp/test?secret=JBSWY3DPEHPK3PXP&counter=25");
        assertTrue(entry.hasHotp());
        assertEquals("JBSWY3DPEHPK3PXP", entry.getHotpSecret());
        assertEquals(new Long(25), entry.getHotpCounter());
    }
}