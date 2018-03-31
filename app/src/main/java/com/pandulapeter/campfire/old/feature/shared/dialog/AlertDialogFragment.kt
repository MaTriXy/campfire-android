package com.pandulapeter.campfire.old.feature.shared.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.old.util.withArguments
import org.koin.android.ext.android.inject

/**
 * Wrapper for [AlertDialog] with that handles state saving.
 */
class AlertDialogFragment : AppCompatDialogFragment() {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val onDialogItemsSelectedListener get() = parentFragment as? OnDialogItemsSelectedListener ?: activity as? OnDialogItemsSelectedListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        context?.let { context ->
            arguments?.let { arguments ->
                return AlertDialog.Builder(context, if (userPreferenceRepository.shouldUseDarkTheme) R.style.DarkAlertDialog else R.style.LightAlertDialog)
                    .setTitle(arguments.title)
                    .setMessage(arguments.message)
                    .setPositiveButton(arguments.positiveButton, { _, _ -> onDialogItemsSelectedListener?.onPositiveButtonSelected() })
                    .setNegativeButton(arguments.negativeButton, null)
                    .create()
            }
        }
        return super.onCreateDialog(savedInstanceState)
    }


    interface OnDialogItemsSelectedListener {

        fun onPositiveButtonSelected()
    }

    companion object {
        private var Bundle?.title by BundleArgumentDelegate.Int("title")
        private var Bundle?.message by BundleArgumentDelegate.Int("message")
        private var Bundle?.positiveButton by BundleArgumentDelegate.Int("positiveButton")
        private var Bundle?.negativeButton by BundleArgumentDelegate.Int("negativeButton")

        fun show(
            fragmentManager: FragmentManager,
            @StringRes title: Int,
            @StringRes message: Int,
            @StringRes positiveButton: Int,
            @StringRes negativeButton: Int
        ) {
            AlertDialogFragment().withArguments {
                it.title = title
                it.message = message
                it.positiveButton = positiveButton
                it.negativeButton = negativeButton
            }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }
}