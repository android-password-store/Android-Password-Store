package com.zeapo.pwdstore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.zeapo.pwdstore.crypto.PgpActivity
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
@LargeTest
class EncryptTest {
    private lateinit var targetContext: Context
    private lateinit var testContext: Context
    private lateinit var activity: PgpActivity

    private val name = "sub"
    private val parentPath = "/category/"
    private lateinit var path: String
    private lateinit var repoPath: String

    @Rule @JvmField
    var mActivityRule: ActivityTestRule<PgpActivity> = ActivityTestRule<PgpActivity>(PgpActivity::class.java, true, false)

    private fun init() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        testContext = InstrumentationRegistry.getInstrumentation().context

        // have an empty store
        FileUtils.forceMkdir(File(targetContext.filesDir, "test-store"))
        FileUtils.cleanDirectory(File(targetContext.filesDir, "test-store"))

        repoPath = File(targetContext.filesDir, "test-store").absolutePath

        path = "$repoPath/$parentPath/".replace("//", "/")

        val intent = Intent(targetContext, PgpActivity::class.java)
        intent.putExtra("OPERATION", "ENCRYPT")
        intent.putExtra("FILE_PATH", path)
        intent.putExtra("REPO_PATH", repoPath)

        activity = mActivityRule.launchActivity(intent)
    }

    @SuppressLint("ApplySharedPref", "SetTextI18n")
    @Test
    fun shouldEncrypt() {
        init()

        onView(withId(R.id.crypto_password_category)).check(ViewAssertions.matches(withText(parentPath)))
        activity.onBound(null)
        val clearPass = IOUtils.toString(testContext.assets.open("clear-store/category/sub"), Charsets.UTF_8.name())
        val passEntry = PasswordEntry(clearPass)

        onView(withId(R.id.crypto_password_file_edit)).perform(typeText("sub"))
        onView(withId(R.id.crypto_password_edit)).perform(typeText(passEntry.password))
        onView(withId(R.id.crypto_extra_edit)).perform(scrollTo(), click())
        onView(withId(R.id.crypto_extra_edit)).perform(typeText(passEntry.extraContent))

        // we should return to the home screen once we confirm
        onView(withId(R.id.crypto_confirm_add)).perform(click())

        // The resulting file should exist
        assert(File("$path/$name.gpg").exists())
    }
}



