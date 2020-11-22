package com.zeapo.pwdstore.model

import com.zeapo.pwdstore.fuzzyMatch
import com.zeapo.pwdstore.utils.PasswordItem
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class PasswordItemFuzzySearchTest {

    fun makeItem(path: String): PasswordItem {
        val passwordFile = File(ROOT_DIR, path)
        return PasswordItem.newPassword(passwordFile.name, passwordFile, ROOT_DIR)
    }

    @Test fun testDoesNotMatchExtension() {
        assertEquals(0, makeItem("foo/bar.gpg").fuzzyMatch("gpg"))
    }

    @Test fun testDoesNotMatchRootDir() {
        assertEquals(0, makeItem("aaa/bbb.gpg").fuzzyMatch("store"))
    }

    @Test fun testDoesNotMatchDeletions() {
        assertEquals(0, makeItem("foo/bar.gpg").fuzzyMatch("bag"))
        assertEquals(0, makeItem("foo/bar.gpg").fuzzyMatch("baf"))
    }

    @Test fun testMatchesCaseInsensitively() {
        assertEquals(makeItem("Foo/BaR/RaIf.gpg").fuzzyMatch("raif"),
            makeItem("Foo/BaR/RaIf.gpg").fuzzyMatch("raif"))
    }

    @Test fun testSubstringsMatchBest() {
        val filter = "raif"
        val bestScore = makeItem(filter).fuzzyMatch(filter)
        val bestMatch = "foo/ba/raif.gpg"
        assertEquals(bestScore, makeItem(bestMatch).fuzzyMatch(filter))

        val equalMatches = listOf(
            "foo/bar/raif.gpg",
            "rai/bar/raif.gpg",
        )
        for (equalMatch in equalMatches) {
            assertEquals(bestScore, makeItem(equalMatch).fuzzyMatch(filter), "$bestMatch should rank identically to $equalMatch")
        }

        val worseMatches = listOf(
            "foo/ra/if.gpg",
            "ra/bar/if.gpg",
            "foo/bar/koz.gpg",
        )
        for (worseMatch in worseMatches) {
            assertTrue("$bestMatch should rank higher than $worseMatch") {
                bestScore > makeItem(worseMatch).fuzzyMatch(filter)
            }
        }
    }

    companion object {

        val ROOT_DIR = File("/data/0/apps/aps/store")
    }
}
