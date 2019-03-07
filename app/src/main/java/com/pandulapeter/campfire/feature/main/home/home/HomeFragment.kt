package com.pandulapeter.campfire.feature.main.home.home

import android.os.Build
import android.os.Bundle
import android.transition.Transition
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.app.SharedElementCallback
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.databinding.FragmentHomeBinding
import com.pandulapeter.campfire.databinding.ViewSearchControlsBinding
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.widget.SearchControlsViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onTextChanged
import com.pandulapeter.campfire.util.waitForPreDraw
import com.pandulapeter.campfire.util.withArguments
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : CampfireFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    override val viewModel by viewModel<HomeViewModel>()
    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    private val drawableCloseToSearch by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context?.animatedDrawable(R.drawable.avd_close_to_search) else context?.drawable(R.drawable.ic_search)
    }
    private val drawableSearchToClose by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context?.animatedDrawable(R.drawable.avd_search_to_close) else context?.drawable(R.drawable.ic_close)
    }
    private val searchToggle: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_search) {
            toggleTextInputVisibility()
        }
    }
    private val eraseButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_eraser) { toolbarTextInputView.textInput.setText("") }.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0.5f
            isEnabled = false
        }
    }
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager
    private var wasLastTransitionForACollection = false
    private lateinit var toolbarTextInputView: ToolbarTextInputView
    val recyclerAdapter = RecyclerAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragment?.setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                var index = recyclerAdapter.items.indexOfFirst { it is CollectionItemViewModel && it.collection.id == getCampfireActivity()?.lastCollectionId }
                if (wasLastTransitionForACollection && index != RecyclerView.NO_POSITION) {
                    binding.recyclerView.findViewHolderForAdapterPosition(index)?.let {
                        val view = it.itemView
                        view.transitionName = "card-${getCampfireActivity()?.lastCollectionId}"
                        sharedElements[names[0]] = view
                        val image = view.findViewById<View>(R.id.image)
                        image.transitionName = "image-${getCampfireActivity()?.lastCollectionId}"
                        sharedElements[names[1]] = image
                    }
                } else {
                    index = recyclerAdapter.items.indexOfFirst { it is SongItemViewModel && it.song.id == getCampfireActivity()?.lastSongId }
                    if (index != RecyclerView.NO_POSITION) {
                        (binding.recyclerView.findViewHolderForAdapterPosition(index)
                            ?: binding.recyclerView.findViewHolderForAdapterPosition(linearLayoutManager.findLastVisibleItemPosition()))?.let {
                            sharedElements[names[0]] = it.itemView
                        }
                    }
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.stateLayout.animateFirstView = savedInstanceState == null
        getCampfireActivity()?.let { activity ->
            if (arguments?.shouldAnimate == true) {
                arguments?.shouldAnimate = false
                view.alpha = 0f
                view.waitForPreDraw {
                    view.translationY = view.height.toFloat()
                    view
                        .animate()
                        .translationY(0f)
                        .alpha(1f)
                        .apply {
                            startDelay = 300
                            duration = 600
                        }
                        .start()
                    activity.onScreenChanged()
                    false
                }
            }
            toolbarTextInputView = ToolbarTextInputView(activity.toolbarContext, R.string.home_search, true).apply {
                if (viewModel.isTextInputVisible) {
                    showTextInput()
                }
                textInput.setText(viewModel.query)
                visibilityChangeListener = { viewModel.isTextInputVisible = it }
                title.updateToolbarTitle(R.string.main_home)
                textInput.onTextChanged { if (isTextInputVisible) viewModel.query = it }
            }
            viewModel.shouldScrollToTop.observeAndReset { recyclerAdapter.shouldScrollToTop = it }
            viewModel.items.observeNotNull { recyclerAdapter.items = it }
            viewModel.changeEvent.observeAndReset { recyclerAdapter.notifyItemChanged(it.first, it.second) }
            viewModel.isSearchToggleVisible.observeAfterDelay {
                searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { (this as? AnimatedVectorDrawableCompat)?.start() })
                activity.transitionMode = true
                binding.root.post {
                    if (isAdded) {
                        viewModel.searchControlsViewModel.isVisible.value = it
                    }
                }
            }
            viewModel.searchControlsViewModel.firstCheckbox.observe {
                binding.root.postDelayed(
                    { if (isAdded) viewModel.shouldSearchInSongs = it },
                    SearchControlsViewModel.COMPOUND_BUTTON_LONG_TRANSITION_DELAY
                )
            }
            viewModel.searchControlsViewModel.secondCheckbox.observe {
                binding.root.postDelayed(
                    { if (isAdded) viewModel.shouldSearchInCollections = it },
                    SearchControlsViewModel.COMPOUND_BUTTON_LONG_TRANSITION_DELAY
                )
            }
            viewModel.shouldOpenSecondaryNavigationDrawer.observeAndReset { getCampfireActivity()?.openSecondaryNavigationDrawer() }
            viewModel.languages.observeNotNull { languages ->
                activity.updateAppBarView(
                    DataBindingUtil.inflate<ViewSearchControlsBinding>(LayoutInflater.from(activity.toolbarContext), R.layout.view_search_controls, null, false).apply {
                        viewModel = this@HomeFragment.viewModel.searchControlsViewModel
                        lifecycleOwner = viewLifecycleOwner
                    }.root,
                    savedInstanceState != null
                )
                activity.enableSecondaryNavigationDrawer(R.menu.home)
                initializeCompoundButton(R.id.song_of_the_day) { viewModel.shouldShowSongOfTheDay }
                initializeCompoundButton(R.id.new_collections) { viewModel.shouldShowNewCollections }
                initializeCompoundButton(R.id.new_songs) { viewModel.shouldShowNewSongs }
                initializeCompoundButton(R.id.random_collections) { viewModel.shouldShowRandomCollections }
                initializeCompoundButton(R.id.random_songs) { viewModel.shouldShowRandomSongs }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                activity.secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                activity.updateToolbarButtons(
                    listOf(
                        eraseButton,
                        searchToggle,
                        activity.toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort) { activity.openSecondaryNavigationDrawer() }
                    ))
            }
            viewModel.shouldShowEraseButton.observe {
                eraseButton.apply {
                    if (getCampfireActivity()?.isAfterFirstStart == true) {
                        animate()
                            .scaleX(if (it) 1f else 0f)
                            .scaleY(if (it) 1f else 0f)
                            .start()
                    } else {
                        scaleX = if (it) 1f else 0f
                        scaleY = if (it) 1f else 0f
                    }
                }
            }
            viewModel.shouldEnableEraseButton.observe {
                eraseButton.animate().alpha(if (it) 1f else 0.5f).start()
                eraseButton.isEnabled = it
            }
            savedInstanceState?.also {
                viewModel.searchControlsViewModel.isVisible.value = savedInstanceState.isTextInputVisible
                viewModel.buttonText.value = it.buttonText
                wasLastTransitionForACollection = it.wasLastTransitionForACollection
                viewModel.randomCollections = it.randomCollections
                viewModel.randomSongs = it.randomSongs
                if (it.isTextInputVisible) {
                    searchToggle.setImageDrawable(context?.drawable(R.drawable.ic_close))
                    toolbarTextInputView.textInput.run {
                        setText(savedInstanceState.searchQuery)
                        setSelection(text.length)
                        viewModel.query = text.toString()
                    }
                    toolbarTextInputView.showTextInput()
                }
                viewModel.shouldShowEraseButton.value = savedInstanceState.isEraseButtonVisible
                viewModel.shouldEnableEraseButton.value = savedInstanceState.isEraseButtonEnabled
            }
            toolbarTextInputView.textInput.requestFocus()
            viewModel.shouldShowUpdateErrorSnackbar.observeAndReset {
                showSnackbar(
                    message = R.string.something_went_wrong,
                    action = { viewModel.updateData() })
            }
            viewModel.downloadSongError.observeAndReset { song ->
                binding.root.post {
                    if (isAdded) {
                        showSnackbar(
                            message = getString(R.string.songs_song_download_error, song.title),
                            action = { viewModel.downloadSong(song) })
                    }
                }
            }
            binding.swipeRefreshLayout.run {
                setOnRefreshListener {
                    analyticsManager.onSwipeToRefreshUsed(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                    viewModel.updateData()
                }
                setColorSchemeColors(context.color(R.color.accent))
            }
            binding.recyclerView.apply {
                linearLayoutManager = DisableScrollLinearLayoutManager(activity).apply { interactionBlocker = viewModel.interactionBlocker }
                layoutManager = linearLayoutManager
                setHasFixedSize(true)
                adapter = recyclerAdapter
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy > 0 && !recyclerView.isAnimating) {
                            hideKeyboard(activity.currentFocus)
                        }
                    }
                })
                itemAnimator = object : DefaultItemAnimator() {
                    init {
                        supportsChangeAnimations = false
                    }
                }
            }
            fun toggleSearchViewIfEmpty() {
                if (toolbarTextInputView.isTextInputVisible && viewModel.query.trim().isEmpty()) {
                    toggleTextInputVisibility()
                }
            }
            recyclerAdapter.apply {
                collectionClickListener = { collection, clickedView, image ->
                    if (!isUiBlocked) {
                        if (items.size > 1) {
                            viewModel.isDetailScreenOpen = true
                        }
                        isUiBlocked = true
                        toggleSearchViewIfEmpty()
                        getCampfireActivity()?.openCollectionDetailsScreen(collection, clickedView, image, items.size > 1)
                        wasLastTransitionForACollection = true
                    }
                }
                collectionBookmarkClickListener = { collection, position ->
                    if (!isUiBlocked) {
                        viewModel.onBookmarkClicked(position, collection)
                    }
                }
                songClickListener = { song, position, clickedView ->
                    if (!isUiBlocked) {
                        if (items.size > 1) {
                            viewModel.isDetailScreenOpen = true
                        }
                        isUiBlocked = true
                        toggleSearchViewIfEmpty()
                        val shouldSendMultipleSongs = position > viewModel.firstRandomSongIndex && !(viewModel.query.isNotEmpty() && toolbarTextInputView.isTextInputVisible)
                        getCampfireActivity()?.openDetailScreen(
                            clickedView,
                            if (shouldSendMultipleSongs) viewModel.displayedRandomSongs else listOf(song),
                            items.size > 1,
                            if (shouldSendMultipleSongs) position - viewModel.firstRandomSongIndex - 2 else 0,
                            true
                        )
                        wasLastTransitionForACollection = false
                    }
                }
                songPlaylistClickListener = { song ->
                    if (!isUiBlocked) {
                        if (viewModel.areThereMoreThanOnePlaylists()) {
                            isUiBlocked = true
                            PlaylistChooserBottomSheetFragment.show(childFragmentManager, song.id, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                        } else {
                            viewModel.toggleFavoritesState(song.id)
                        }
                    }
                }
                songDownloadClickListener = { song ->
                    if (!isUiBlocked) {
                        analyticsManager.onDownloadButtonPressed(song.id)
                        viewModel.downloadSong(song)
                    }
                }
            }
            binding.recyclerView.addOnLayoutChangeListener(
                object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        binding.recyclerView.removeOnLayoutChangeListener(this)
                        if (reenterTransition != null) {
                            var index = recyclerAdapter.items.indexOfFirst { it is CollectionItemViewModel && it.collection.id == getCampfireActivity()?.lastCollectionId }
                            if (index != RecyclerView.NO_POSITION) {
                                val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                                if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                    binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                                }
                            } else {
                                index = recyclerAdapter.items.indexOfFirst { it is SongItemViewModel && it.song.id == getCampfireActivity()?.lastSongId }
                                if (index != RecyclerView.NO_POSITION) {
                                    val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                                    if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                        binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                                    }
                                }
                            }
                        }
                    }
                })
            (view.parent as? ViewGroup)?.waitForPreDraw {
                consume {
                    (sharedElementEnterTransition as? Transition)?.addListener(object : Transition.TransitionListener {

                        override fun onTransitionStart(transition: Transition?) = Unit

                        override fun onTransitionResume(transition: Transition?) = Unit

                        override fun onTransitionPause(transition: Transition?) = Unit

                        override fun onTransitionEnd(transition: Transition?) {
                            isUiBlocked = false
                            transition?.removeListener(this)
                        }

                        override fun onTransitionCancel(transition: Transition?) {
                            isUiBlocked = false
                            transition?.removeListener(this)
                        }
                    })
                    binding.recyclerView.postDelayed({ if (isAdded) parentFragment?.startPostponedEnterTransition() }, 50)
                }
            }
            activity.showPlayStoreRatingDialogIfNeeded()
            activity.updateToolbarTitleView(toolbarTextInputView)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.buttonText.value?.let { outState.buttonText = it }
        outState.wasLastTransitionForACollection = wasLastTransitionForACollection
        outState.randomCollections = ArrayList(viewModel.randomCollections.take(HomeViewModel.RANDOM_COLLECTION_COUNT + HomeViewModel.NEW_COLLECTION_COUNT + 1))
        outState.randomSongs = ArrayList(viewModel.randomSongs.take(HomeViewModel.RANDOM_SONG_COUNT + HomeViewModel.NEW_SONG_COUNT + 1))
        outState.isTextInputVisible = toolbarTextInputView.isTextInputVisible
        outState.searchQuery = viewModel.query
        outState.isEraseButtonVisible = viewModel.shouldShowEraseButton.value == true
        outState.isEraseButtonEnabled = viewModel.shouldEnableEraseButton.value == true
    }

    override fun onBackPressed() = if (toolbarTextInputView.isTextInputVisible) consume { toggleTextInputVisibility() } else isUiBlocked

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.song_of_the_day -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_SONG_OF_THE_DAY, it)
                shouldShowSongOfTheDay = it
            }, { shouldShowSongOfTheDay })
            R.id.new_collections -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_NEW_COLLECTIONS, it)
                shouldShowNewCollections = it
            }, { shouldShowNewCollections })
            R.id.new_songs -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_NEW_SONGS, it)
                shouldShowNewSongs = it
            }, { shouldShowNewSongs })
            R.id.random_collections -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_RANDOM_COLLECTIONS, it)
                shouldShowRandomCollections = it
            }, { shouldShowRandomCollections })
            R.id.random_songs -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_RANDOM_SONGS, it)
                shouldShowRandomSongs = it
            }, { shouldShowRandomSongs })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_EXPLICIT, it)
                shouldShowExplicit = it
            }, { shouldShowExplicit })
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.value.orEmpty().find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !viewModel.query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                viewModel.isSearchToggleVisible.value = toolbarTextInputView.isTextInputVisible
                if (shouldScrollToTop) {
                    viewModel.updateAdapterItems(false, !isTextInputVisible)
                }
                viewModel.buttonText.value = if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters
            }
            viewModel.shouldShowEraseButton.value = isTextInputVisible
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_LANGUAGE + languageId, contains(languageId))
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }

    companion object {
        private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
        private var Bundle.wasLastTransitionForACollection by BundleArgumentDelegate.Boolean("wasLastTransitionForACollection")
        private var Bundle.randomCollections by BundleArgumentDelegate.ParcelableArrayList<Collection>("randomCollections")
        private var Bundle.randomSongs by BundleArgumentDelegate.ParcelableArrayList<Song>("randomSongs")
        private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
        private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
        private var Bundle.isEraseButtonVisible by BundleArgumentDelegate.Boolean("isEraseButtonVisible")
        private var Bundle.isEraseButtonEnabled by BundleArgumentDelegate.Boolean("isEraseButtonEnabled")
        private var Bundle.shouldAnimate by BundleArgumentDelegate.Boolean("shouldAnimate")

        fun newInstance(shouldAnimate: Boolean) = HomeFragment().withArguments {
            it.shouldAnimate = shouldAnimate
        }
    }
}