package com.zeapo.pwdstore;

import junit.framework.TestCase;

public class PasswordEntryTest extends TestCase {

    public void testGetPassword() throws Exception {
        assertEquals("fooooo", new PasswordEntry("fooooo\nbla\n").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo\nbla").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo\n").getPassword());
        assertEquals("fooooo", new PasswordEntry("fooooo").getPassword());
        assertEquals("", new PasswordEntry("\nblubb\n").getPassword());
        assertEquals("", new PasswordEntry("\nblubb").getPassword());
        assertEquals("", new PasswordEntry("\n").getPassword());
        assertEquals("", new PasswordEntry("").getPassword());
    }

    public void testGetExtraContent() throws Exception {
        assertEquals("bla\n", new PasswordEntry("fooooo\nbla\n").getExtraContent());
        assertEquals("bla", new PasswordEntry("fooooo\nbla").getExtraContent());
        assertEquals("", new PasswordEntry("fooooo\n").getExtraContent());
        assertEquals("", new PasswordEntry("fooooo").getExtraContent());
        assertEquals("blubb\n", new PasswordEntry("\nblubb\n").getExtraContent());
        assertEquals("blubb", new PasswordEntry("\nblubb").getExtraContent());
        assertEquals("", new PasswordEntry("\n").getExtraContent());
        assertEquals("", new PasswordEntry("").getExtraContent());
    }

    public void testGetUsername() throws Exception {
        assertEquals("username", new PasswordEntry("secret\nextra\nlogin: username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nextra\nusername: username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nUSERNaMe:  username\ncontent\n").getUsername());
        assertEquals("username", new PasswordEntry("\nLOGiN:username").getUsername());
        assertNull(new PasswordEntry("secret\nextra\ncontent\n").getUsername());
    }

    public void testHasUsername() throws Exception {
        assertTrue(new PasswordEntry("secret\nextra\nlogin: username\ncontent\n").hasUsername());
        assertFalse(new PasswordEntry("secret\nextra\ncontent\n").hasUsername());
        assertFalse(new PasswordEntry("secret\nlogin failed\n").hasUsername());
        assertFalse(new PasswordEntry("\n").hasUsername());
        assertFalse(new PasswordEntry("").hasUsername());
    }
}