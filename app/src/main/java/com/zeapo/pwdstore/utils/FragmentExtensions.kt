package com.zeapo.pwdstore.utils

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.zeapo.pwdstore.R

/**
 * Check if [permission] is granted to the app. Aliases to [isPermissionGranted] internally.
 */
fun Fragment.isPermissionGranted(permission: String): Boolean {
    return requireActivity().isPermissionGranted(permission)
}

/**
 * Calls `finish()` on the enclosing [androidx.fragment.app.FragmentActivity]
 */
fun Fragment.finish() = requireActivity().finish()

/**
 * Perform a [commit] on this [FragmentManager] with custom animations and adding the [destinationFragment]
 * to the fragment backstack
 */
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
