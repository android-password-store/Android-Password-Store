/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test as test

private infix fun String.matchedForDomain(domain: String) =
    SearchableRepositoryViewModel.generateStrictDomainRegex(domain)?.containsMatchIn(this) == true

class StrictDomainRegexTest {
    @test fun acceptsLiteralDomain() {
        assertTrue("work/example.org/john.doe@example.org.gpg" matchedForDomain "example.org")
        assertTrue("example.org/john.doe@example.org.gpg" matchedForDomain "example.org")
        assertTrue("example.org.gpg" matchedForDomain "example.org")
    }

    @test fun acceptsSubdomains() {
        assertTrue("work/www.example.org/john.doe@example.org.gpg" matchedForDomain "example.org")
        assertTrue("www2.example.org/john.doe@example.org.gpg" matchedForDomain "example.org")
        assertTrue("www.login.example.org.gpg" matchedForDomain "example.org")
    }

    @test fun rejectsPhishingAttempts() {
        assertFalse("example.org.gpg" matchedForDomain "xample.org")
        assertFalse("login.example.org.gpg" matchedForDomain "xample.org")
        assertFalse("example.org/john.doe@exmple.org.gpg" matchedForDomain "xample.org")
        assertFalse("example.org.gpg" matchedForDomain "e/xample.org")
    }

    @test fun rejectNonGpgComponentMatches() {
        assertFalse("work/example.org" matchedForDomain "example.org")
    }

    @test fun rejectsEmailAddresses() {
        assertFalse("work/notexample.org/john.doe@example.org.gpg" matchedForDomain "example.org")
        assertFalse("work/notexample.org/john.doe@www.example.org.gpg" matchedForDomain "example.org")
        assertFalse("work/john.doe@www.example.org/foo.org" matchedForDomain "example.org")
    }

    @test fun rejectsPathSeparators() {
        assertNull(SearchableRepositoryViewModel.generateStrictDomainRegex("ex/ample.org"))
    }
}
