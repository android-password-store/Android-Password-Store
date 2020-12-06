/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.subScreen
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.ActivityPreferenceRecyclerviewBinding
import dev.msfjarvis.aps.util.extensions.viewBinding
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

class MainSettingsScreen : AppCompatActivity() {

    private val miscSettings = MiscSettings(this)
    private val autofillSettings = AutofillSettings(this)
    private val passwordSettings = PasswordSettings(this)

    private val binding by viewBinding(ActivityPreferenceRecyclerviewBinding::inflate)
    private lateinit var preferencesAdapter: PreferencesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val screen = screen(this) {
            subScreen {
                titleRes = R.string.pref_category_autofill_title
                iconRes = R.drawable.ic_wysiwyg_24px
                autofillSettings.provideSettings(this)
            }
            subScreen {
                titleRes = R.string.pref_category_passwords_title
                iconRes = R.drawable.vpn_key_24px
                passwordSettings.provideSettings(this)
            }
            subScreen {
                titleRes = R.string.pref_category_misc_title
                iconRes = R.drawable.ic_miscellaneous_services_24px
                miscSettings.provideSettings(this)
            }
            subScreen {
                titleRes = R.string.pref_category_misc_title
                iconRes = R.drawable.ic_miscellaneous_services_24px
                miscSettings.provideSettings(this)
            }
        }
        val adapter = PreferencesAdapter(screen)
        preferencesAdapter = adapter
        adapter.onScreenChangeListener = PreferencesAdapter.OnScreenChangeListener { subScreen, entering ->
            supportActionBar?.title = if (!entering) {
                getString(R.string.action_settings)
            } else {
                getString(subScreen.titleRes)
            }
        }
        binding.preferenceRecyclerView.adapter = adapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> if (!preferencesAdapter.goBack()) {
                super.onOptionsItemSelected(item)
            } else {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (!preferencesAdapter.goBack())
            super.onBackPressed()
    }

}
