package com.pandulapeter.campfire.feature.shared

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.transition.Transition
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.dynamiclinks.DynamicLink
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.ShortDynamicLink
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.toDeepLinkUri
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.obtainColor
import org.koin.android.ext.android.inject

abstract class CampfireFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private var layoutResourceId: Int) : Fragment(), Transition.TransitionListener {

    private var realBinding: B? = null
    protected val binding: B get() = realBinding ?: throw IllegalStateException("The binding is null.")
    abstract val viewModel: VM
    protected open val shouldDelaySubscribing = false
    protected val analyticsManager by inject<AnalyticsManager>()
    private var snackbar: Snackbar? = null
    private var isResumingDelayed = false
    private val snackbarBackground by lazy { requireContext().drawable(R.drawable.bg_snackbar) }
    private val snackbarTextColor by lazy { requireContext().obtainColor(android.R.attr.colorPrimary) }
    private val snackbarActionTextColor by lazy { requireContext().color(R.color.accent) }
    var hasStartedListening = false
        private set
    var isUiBlocked
        get() = viewModel.isUiBlocked
        set(value) {
            viewModel.isUiBlocked = value
        }

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        DataBindingUtil.inflate<B>(inflater, layoutResourceId, container, false).apply {
            realBinding = this
            lifecycleOwner = viewLifecycleOwner
            setVariable(BR.viewModel, viewModel)
            executePendingBindings()
        }.root

    override fun onStart() {
        super.onStart()
        if (shouldDelaySubscribing) {
            isResumingDelayed = true
        } else {
            updateUI()
        }
    }

    override fun onStop() {
        super.onStop()
        isResumingDelayed = false
        snackbar?.dismiss()
        viewModel.unsubscribe()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        realBinding = null
    }

    fun onDialogOpened() {
        isUiBlocked = true
    }

    fun onDialogDismissed() {
        isUiBlocked = false
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val parent = parentFragment
        return if (!enter && parent != null && parent.isRemoving) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            AlphaAnimation(1f, 1f).apply { duration = getNextAnimationDuration(parent, 300L) }
        } else {
            super.onCreateAnimation(transit, enter, nextAnim)
        }
    }

    open fun onNavigationItemSelected(menuItem: MenuItem) = false

    protected fun initializeCompoundButton(itemId: Int, getValue: () -> Boolean) = consume {
        getCampfireActivity()?.secondaryNavigationMenu?.findItem(itemId)?.let {
            (it.actionView as? CompoundButton)?.run {
                isChecked = getValue()
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != getValue()) {
                        onNavigationItemSelected(it)
                    }
                }
            }
        }
    }

    protected inline fun Context.createToolbarButton(@DrawableRes drawableRes: Int, crossinline onClickListener: (View) -> Unit) = ToolbarButton(this).apply {
        setImageDrawable(drawable(drawableRes))
        setOnClickListener {
            if (isAdded && !isUiBlocked) {
                onClickListener(it)
            }
        }
    }

    private fun getNextAnimationDuration(fragment: androidx.fragment.app.Fragment, defValue: Long): Long {
        try {
            val animInfoField = androidx.fragment.app.Fragment::class.java.getDeclaredField("mAnimationInfo")
            animInfoField.isAccessible = true
            val animationInfo = animInfoField.get(fragment)
            val nextAnimField = animationInfo.javaClass.getDeclaredField("mNextAnim")
            nextAnimField.isAccessible = true
            val nextAnimResource = nextAnimField.getInt(animationInfo)
            val nextAnim = AnimationUtils.loadAnimation(fragment.activity, nextAnimResource)
            return nextAnim?.duration ?: defValue
        } catch (ex: NoSuchFieldException) {
            return defValue
        } catch (ex: IllegalAccessException) {
            return defValue
        } catch (ex: Resources.NotFoundException) {
            return defValue
        }
    }

    override fun setReenterTransition(transition: Any?) {
        super.setReenterTransition(transition)
        (transition as? Transition)?.let {
            it.removeListener(this)
            it.addListener(this)
        }
    }

    @CallSuper
    open fun updateUI() {
        viewModel.subscribe()
        binding.root.post { hasStartedListening = true }
    }

    open fun onBackPressed() = false

    fun getCampfireActivity() = activity as? CampfireActivity?

    protected fun showHint(@StringRes message: Int, action: () -> Unit) {
        snackbar?.dismiss()
        snackbar = getCampfireActivity()?.snackbarRoot
            ?.makeSnackbar(getString(message), Snackbar.LENGTH_INDEFINITE)
            ?.apply { setAction(R.string.got_it) { action() } }
        snackbar?.show()
    }

    protected fun isSnackbarVisible() = snackbar?.isShownOrQueued ?: false

    fun hideSnackbar() = snackbar?.dismiss()

    fun showSnackbar(@StringRes message: Int, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) =
        showSnackbar(getString(message), actionText, action, dismissAction)

    protected fun showSnackbar(message: String, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) {
        snackbar = getCampfireActivity()?.snackbarRoot
            ?.makeSnackbar(message, if (action == null && dismissAction == null) SNACKBAR_SHORT_DURATION else SNACKBAR_LONG_DURATION, dismissAction)
            ?.apply { action?.let { setAction(actionText) { action() } } }
        snackbar?.show()
    }

    protected fun consumeAndUpdateBoolean(menuItem: MenuItem, setValue: (Boolean) -> Unit, getValue: () -> Boolean) = consume {
        setValue(!getValue())
        (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(getValue())
    }

    protected fun CompoundButton?.updateCheckedStateWithDelay(checked: Boolean) {
        this?.postDelayed({ if (isAdded) isChecked = checked }, COMPOUND_BUTTON_TRANSITION_DELAY)
    }

    private fun View.makeSnackbar(message: String, duration: Int, dismissAction: (() -> Unit)? = null) = Snackbar.make(this, message, duration).apply {
        view.background = snackbarBackground
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).setTextColor(snackbarTextColor)
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action).apply {
            isAllCaps = false
            letterSpacing = 0f
        }
        setActionTextColor(snackbarActionTextColor)
        activity?.currentFocus?.let { hideKeyboard(it) }
        dismissAction?.let {
            addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_CONSECUTIVE) {
                        it()
                    }
                }
            })
        }
    }

    @CallSuper
    override fun onTransitionEnd(transition: Transition?) {
        transition?.removeListener(this)
        if (isResumingDelayed) {
            updateUI()
            isResumingDelayed = false
        }
        enterTransition = null
        exitTransition = null
        isUiBlocked = false
    }

    override fun onTransitionResume(transition: Transition?) = Unit

    override fun onTransitionPause(transition: Transition?) = Unit

    @CallSuper
    override fun onTransitionCancel(transition: Transition?) = onTransitionEnd(transition)

    override fun onTransitionStart(transition: Transition?) {
        isUiBlocked = true
    }

    protected fun shareSongs(songIds: List<String>) {
        showSnackbar(R.string.generating_link)
        FirebaseDynamicLinks.getInstance().createDynamicLink()
            .setLink(songIds.toDeepLinkUri())
            .setDomainUriPrefix("https://campfire.page.link")
            .setSocialMetaTagParameters(
                DynamicLink.SocialMetaTagParameters.Builder()
                    .setTitle(getString(R.string.campfire))
                    .setDescription(resources.getQuantityString(R.plurals.playlist_song_count, songIds.size, songIds.size))
                    .setImageUrl(Uri.parse(SHARE_LOGO_URL))
                    .build()
            )
            .setAndroidParameters(
                DynamicLink.AndroidParameters.Builder()
                    .setMinimumVersion(23)
                    .build()
            )
            .buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT)
            .addOnSuccessListener { result ->
                val shortLink = result.shortLink
                try {
                    snackbar?.dismiss()
                    startActivity(
                        Intent.createChooser(
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                            }.putExtra(Intent.EXTRA_TEXT, shortLink.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
                        )
                    )
                } catch (exception: ActivityNotFoundException) {
                    showSnackbar(R.string.options_about_error)
                }
            }.addOnFailureListener { showSnackbar(message = R.string.something_went_wrong, action = { shareSongs(songIds) }) }
    }

    protected inline fun <T> LiveData<T>.observe(crossinline callback: (T) -> Unit) = observe(viewLifecycleOwner, Observer {
        callback(it)
    })

    protected inline fun <T> LiveData<T>.observeAfterDelay(crossinline callback: (T) -> Unit) = observe(viewLifecycleOwner, Observer {
        if (hasStartedListening) {
            callback(it)
        }
    })

    protected inline fun <T> LiveData<T?>.observeNotNull(crossinline callback: (T) -> Unit) = observe(viewLifecycleOwner, Observer {
        if (it != null) {
            callback(it)
        }
    })

    protected inline fun <T> MutableLiveData<T?>.observeAndReset(crossinline callback: (T) -> Unit) = observe(viewLifecycleOwner, Observer {
        if (it != null) {
            callback(it)
            value = null
        }
    })

    protected fun TextView.updateToolbarTitle(@StringRes titleRes: Int, subtitle: String? = null) = updateToolbarTitle(context.getString(titleRes), subtitle)

    protected fun TextView.updateToolbarTitle(title: String, subtitle: String? = null) = setTitleSubtitle(this, title, subtitle)

    companion object {
        private const val SNACKBAR_SHORT_DURATION = 4000
        private const val SNACKBAR_LONG_DURATION = 7000
        private const val COMPOUND_BUTTON_TRANSITION_DELAY = 10L
        private const val SHARE_LOGO_URL = "${NetworkManager.BASE_URL}${NetworkManager.API_VERSION}image?id=campfire-logo"
    }
}