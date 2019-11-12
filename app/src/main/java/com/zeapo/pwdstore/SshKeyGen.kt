/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

/*

class SshKeyGen : AppCompatActivity() {

    private lateinit var checkBox: CheckBox
    private lateinit var comment: EditText
    private lateinit var passphrase: TextInputEditText
    private lateinit var spinner: Spinner

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Generate SSH Key"
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, SshKeyGenFragment())
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // The back arrow in the action bar should act the same as the back button.
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    // SSH key generation UI
    class SshKeyGenFragment : Fragment() {
        override fun onCreateView(
                inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater.inflate(R.layout.fragment_ssh_keygen, container, false)
            findViews(v)
            val monoTypeface = Typeface.createFromAsset(
                    requireContext().assets, "fonts/sourcecodepro.ttf")
            val spinner = v.findViewById<Spinner>(R.id.length)
            val lengths = arrayOf(2048, 4096)
            val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    lengths)
            spinner.adapter = adapter
            (v.findViewById<View>(R.id.passphrase) as TextInputEditText).typeface = monoTypeface
            val checkbox = v.findViewById<CheckBox>(R.id.show_passphrase)
            checkbox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                val editText: TextInputEditText = v.findViewById(R.id.passphrase)
                val selection = editText.selectionEnd
                if (isChecked) {
                    editText.inputType = (
                            InputType.TYPE_CLASS_TEXT
                                    or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                } else {
                    editText.inputType = (
                            InputType.TYPE_CLASS_TEXT
                                    or InputType.TYPE_TEXT_VARIATION_PASSWORD)
                }
                editText.setSelection(selection)
            }
            return v
        }

        private fun findViews(view: View) {
        }
    }

    // Displays the generated public key .ssh_key.pub

}*/
