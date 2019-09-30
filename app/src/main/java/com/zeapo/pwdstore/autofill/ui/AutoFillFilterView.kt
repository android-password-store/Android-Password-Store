package com.zeapo.pwdstore.autofill.ui

import android.annotation.TargetApi
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import com.zeapo.pwdstore.R
import kotlinx.android.synthetic.main.activity_auto_fill_filter_view.*

@TargetApi(Build.VERSION_CODES.O)
class AutoFillFilterView : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_fill_filter_view)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)


        bindUiEvent()
    }

    private fun bindUiEvent() {
        fill.setOnClickListener {
            finish()
        }
    }
}
