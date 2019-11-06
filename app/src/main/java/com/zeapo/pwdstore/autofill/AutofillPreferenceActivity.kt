/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.zeapo.pwdstore.R
import java.lang.ref.WeakReference
import java.util.ArrayList

class AutofillPreferenceActivity : AppCompatActivity() {

    internal var recyclerAdapter: AutofillRecyclerAdapter? = null // let fragment have access
    private var recyclerView: RecyclerView? = null
    private var pm: PackageManager? = null

    private var recreate: Boolean = false // flag for action on up press; origin autofill dialog? different act

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.autofill_recycler_view)
        recyclerView = findViewById(R.id.autofill_recycler)

        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        pm = packageManager

        PopulateTask(this).execute()

        // if the preference activity was started from the autofill dialog
        recreate = false
        val extras = intent.extras
        if (extras != null) {
            recreate = true

            showDialog(extras.getString("packageName"), extras.getString("appName"), extras.getBoolean("isWeb"))
        }

        title = "Autofill Apps"

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { showDialog("", "", true) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.autofill_preference, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                if (recyclerAdapter != null) {
                    recyclerAdapter!!.filter(s)
                }
                return true
            }
        })

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // in service, we CLEAR_TASK. then we set the recreate flag.
        // something of a hack, but w/o CLEAR_TASK, behaviour was unpredictable
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    fun showDialog(packageName: String?, appName: String?, isWeb: Boolean) {
        val df = AutofillFragment()
        val args = Bundle()
        args.putString("packageName", packageName)
        args.putString("appName", appName)
        args.putBoolean("isWeb", isWeb)
        df.arguments = args
        df.show(supportFragmentManager, "autofill_dialog")
    }

    companion object {
        private class PopulateTask(activity: AutofillPreferenceActivity) : AsyncTask<Void, Void, Void>() {

            val weakReference = WeakReference<AutofillPreferenceActivity>(activity)

            override fun onPreExecute() {
                weakReference.get()?.apply {
                    runOnUiThread { findViewById<View>(R.id.progress_bar).visibility = View.VISIBLE }
                }
            }

            override fun doInBackground(vararg params: Void): Void? {
                val pm = weakReference.get()?.pm ?: return null
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                val allAppsResolveInfo = pm.queryIntentActivities(intent, 0)
                val allApps = ArrayList<AutofillRecyclerAdapter.AppInfo>()

                for (app in allAppsResolveInfo) {
                    allApps.add(AutofillRecyclerAdapter.AppInfo(app.activityInfo.packageName, app.loadLabel(pm).toString(), false, app.loadIcon(pm)))
                }

                val prefs = weakReference.get()?.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
                val prefsMap = prefs!!.all
                for (key in prefsMap.keys) {
                    try {
                        allApps.add(AutofillRecyclerAdapter.AppInfo(key, key, true, pm.getApplicationIcon("com.android.browser")))
                    } catch (e: PackageManager.NameNotFoundException) {
                        allApps.add(AutofillRecyclerAdapter.AppInfo(key, key, true, null))
                    }
                }
                weakReference.get()?.recyclerAdapter = AutofillRecyclerAdapter(allApps, weakReference.get()!!)
                return null
            }

            override fun onPostExecute(ignored: Void?) {
                weakReference.get()?.apply {
                    runOnUiThread {
                        findViewById<View>(R.id.progress_bar).visibility = View.GONE
                        recyclerView!!.adapter = recyclerAdapter
                        val extras = intent.extras
                        if (extras != null) {
                            recyclerView!!.scrollToPosition(recyclerAdapter!!.getPosition(extras.getString("appName")!!))
                        }
                    }
                }
            }
        }
    }
}
