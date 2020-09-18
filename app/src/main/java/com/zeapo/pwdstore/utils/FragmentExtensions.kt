package com.zeapo.pwdstore.utils

import android.content.pm.PackageManager
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.zeapo.pwdstore.R

fun Fragment.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(requireActivity(), permission) == PackageManager.PERMISSION_GRANTED
}

fun Fragment.finish() = requireActivity().finish()

fun FragmentManager.performTransactionWithBackStack(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
    commit {
        beginTransaction()
        addToBackStack(destinationFragment.tag)
        setCustomAnimations(
            R.animator.slide_in_left,
            R.animator.slide_out_left,
            R.animator.slide_in_right,
            R.animator.slide_out_right)
        replace(containerViewId, destinationFragment)
    }
}
