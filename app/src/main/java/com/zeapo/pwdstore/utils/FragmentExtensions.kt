package com.zeapo.pwdstore.utils

import android.content.pm.PackageManager
import android.view.View
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

fun FragmentManager.performTransaction(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
    this.commit {
        beginTransaction()
        setCustomAnimations(
            R.animator.slide_in_left,
            R.animator.slide_out_left,
            R.animator.slide_in_right,
            R.animator.slide_out_right)
        replace(containerViewId, destinationFragment)
    }
}

fun FragmentManager.performTransactionWithBackStack(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
    this.commit {
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

fun FragmentManager.performSharedElementTransaction(destinationFragment: Fragment, views: List<View>, @IdRes containerViewId: Int = android.R.id.content) {
    this.commit {
        beginTransaction()
        for (view in views) {
            addSharedElement(view, view.transitionName)
        }
        addToBackStack(destinationFragment.tag)
        replace(containerViewId, destinationFragment)
    }
}
