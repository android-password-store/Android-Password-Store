package com.zeapo.pwdstore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.*
import android.support.test.espresso.assertion.ViewAssertions
import android.support.test.espresso.matcher.ViewMatchers.withId
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.zeapo.pwdstore.crypto.PgpActivity
import kotlinx.android.synthetic.main.encrypt_layout.*
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
@LargeTest
class EncryptTest {
    lateinit var targetContext: Context
    lateinit var testContext: Context
    lateinit var activity: PgpActivity

    val name = "sub"
    val parentPath = "/category/"
    lateinit var path: String
    lateinit var repoPath: String

    @Rule @JvmField
    var mActivityRule: ActivityTestRule<PgpActivity> = ActivityTestRule<PgpActivity>(PgpActivity::class.java, true, false)

    fun init() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        testContext = InstrumentationRegistry.getContext()

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

    @Test
    fun activityShouldShowCategory() {
        init()

        val categoryView = activity.crypto_password_category
        assertNotNull(categoryView)
        assertEquals(parentPath, categoryView.text.toString())
    }

    @SuppressLint("ApplySharedPref", "SetTextI18n")
    @Test
    fun shouldEncrypt() {
        init()

        activity.onBound(null)
        val clearPass = IOUtils.toString(testContext.assets.open("clear-store/category/sub"), Charsets.UTF_8.name())
        val passEntry = PasswordEntry(clearPass)
        val encryptedEntry = IOUtils.toByteArray(testContext.assets.open("clear-store/category/sub"))

        onView(withId(R.id.crypto_password_file_edit)).perform(typeText("category/sub"))
        onView(withId(R.id.crypto_password_edit)).perform(typeText(passEntry.password))
        onView(withId(R.id.crypto_extra_edit)).perform(scrollTo(), click())
        onView(withId(R.id.crypto_extra_edit)).perform(typeText(passEntry.extraContent))

        // we should return to the home screen once we confirm
        onView(withId(R.id.crypto_confirm_add)).perform(click()).check(ViewAssertions.matches(withId(R.id.fab)))

        val resultEntry = FileUtils.readFileToByteArray(File("$path/$name.gpg"))

    }
}



