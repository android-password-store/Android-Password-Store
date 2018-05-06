package com.zeapo.pwdstore.mainscreen

import android.app.Activity
import android.app.FragmentManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.mainscreen.fragments.SelectFolderFragment
import com.zeapo.pwdstore.utils.PasswordRepository

// TODO more work needed, this is just an extraction from PgpHandler

class SelectFolderActivity : AppCompatActivity() {
    private var passwordList: SelectFolderFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.select_folder_layout)

        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()


        passwordList = SelectFolderFragment()
        val args = Bundle()
        args.putString("Path", PasswordRepository.getRepositoryDirectory(applicationContext).absolutePath)

        passwordList?.arguments = args

        supportActionBar?.show()

        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        fragmentTransaction.replace(R.id.pgp_handler_linearlayout, passwordList, "PasswordsList")
        fragmentTransaction.commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pgp_handler_select_folder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return true
            }
            R.id.crypto_select -> selectFolder()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectFolder() {
        intent.putExtra("SELECTED_FOLDER_PATH", passwordList?.currentDir?.absolutePath)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}