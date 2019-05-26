package com.zeapo.pwdstore.autofill

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.zeapo.pwdstore.R
import java.util.ArrayList
import java.util.Locale

internal class AutofillRecyclerAdapter(
        allApps: List<AppInfo>,
        private val activity: AutofillPreferenceActivity
) : RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder>() {

    private val apps: SortedList<AppInfo>
    private val allApps: ArrayList<AppInfo> // for filtering, maintain a list of all
    private var browserIcon: Drawable? = null

    init {
        val callback = object : SortedListAdapterCallback<AppInfo>(this) {
            // don't take into account secondary text. This is good enough
            // for the limited add/remove usage for websites
            override fun compare(o1: AppInfo, o2: AppInfo): Int {
                return o1.appName.toLowerCase(Locale.ROOT).compareTo(o2.appName.toLowerCase(Locale.ROOT))
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.appName == newItem.appName
            }

            override fun areItemsTheSame(item1: AppInfo, item2: AppInfo): Boolean {
                return item1.appName == item2.appName
            }
        }
        apps = SortedList(AppInfo::class.java, callback)
        apps.addAll(allApps)
        this.allApps = ArrayList(allApps)
        try {
            browserIcon = activity.packageManager.getApplicationIcon("com.android.browser")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.autofill_row_layout, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps.get(position)
        holder.packageName = app.packageName
        holder.appName = app.appName
        holder.isWeb = app.isWeb

        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName

        holder.secondary.visibility = View.VISIBLE
        holder.view.setBackgroundResource(R.color.grey_white_1000)

        val prefs: SharedPreferences
        prefs = if (app.appName != app.packageName) {
            activity.applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
        } else {
            activity.applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
        }
        when (val preference = prefs.getString(holder.packageName, "")) {
            "" -> {
                holder.secondary.visibility = View.GONE
                holder.view.setBackgroundResource(0)
            }
            "/first" -> holder.secondary.setText(R.string.autofill_apps_first)
            "/never" -> holder.secondary.setText(R.string.autofill_apps_never)
            else -> {
                holder.secondary.setText(R.string.autofill_apps_match)
                holder.secondary.append(" " + preference!!.splitLines()[0])
                if (preference.trim { it <= ' ' }.splitLines().size - 1 > 0) {
                    holder.secondary.append(" and "
                            + (preference.trim { it <= ' ' }.splitLines().size - 1) + " more")
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return apps.size()
    }

    fun getPosition(appName: String): Int {
        return apps.indexOf(AppInfo(null, appName, false, null))
    }

    private fun String.splitLines(): Array<String> {
        return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    // for websites, URL = packageName == appName
    fun addWebsite(packageName: String) {
        apps.add(AppInfo(packageName, packageName, true, browserIcon))
        allApps.add(AppInfo(packageName, packageName, true, browserIcon))
    }

    fun removeWebsite(packageName: String) {
        apps.remove(AppInfo(null, packageName, false, null))
        allApps.remove(AppInfo(null, packageName, false, null)) // compare with equals
    }

    fun updateWebsite(oldPackageName: String, packageName: String) {
        apps.updateItemAt(getPosition(oldPackageName), AppInfo(packageName, packageName, true, browserIcon))
        allApps.remove(AppInfo(null, oldPackageName, false, null)) // compare with equals
        allApps.add(AppInfo(null, packageName, false, null))
    }

    fun filter(s: String) {
        if (s.isEmpty()) {
            apps.addAll(allApps)
            return
        }
        apps.beginBatchedUpdates()
        for (app in allApps) {
            if (app.appName.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT))) {
                apps.add(app)
            } else {
                apps.remove(app)
            }
        }
        apps.endBatchedUpdates()
    }

    internal class AppInfo(var packageName: String?, var appName: String, var isWeb: Boolean, var icon: Drawable?) {

        override fun equals(other: Any?): Boolean {
            return other is AppInfo && this.appName == other.appName
        }

        override fun hashCode(): Int {
            var result = packageName?.hashCode() ?: 0
            result = 31 * result + appName.hashCode()
            result = 31 * result + isWeb.hashCode()
            result = 31 * result + (icon?.hashCode() ?: 0)
            return result
        }
    }

    internal inner class ViewHolder(var view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        var name: TextView = view.findViewById(R.id.app_name)
        var icon: ImageView = view.findViewById(R.id.app_icon)
        var secondary: TextView = view.findViewById(R.id.secondary_text)
        var packageName: String? = null
        var appName: String? = null
        var isWeb: Boolean = false

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            activity.showDialog(packageName, appName, isWeb)
        }

    }
}
