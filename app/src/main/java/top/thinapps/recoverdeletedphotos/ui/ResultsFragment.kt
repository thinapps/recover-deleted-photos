package top.thinapps.recoverdeletedphotos.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.core.content.res.use
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import coil.load
import coil.request.Parameters
import coil.request.videoFrameMillis
import coil.size.ViewSizeResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.thinapps.recoverdeletedphotos.R
import top.thinapps.recoverdeletedphotos.databinding.FragmentResultsBinding
import top.thinapps.recoverdeletedphotos.databinding.ItemMediaBinding
import top.thinapps.recoverdeletedphotos.databinding.ItemMediaGridBinding
import top.thinapps.recoverdeletedphotos.model.MediaItem
import top.thinapps.recoverdeletedphotos.recover.Recovery
import java.text.Collator
import kotlin.math.log10
import kotlin.math.pow

class ResultsFragment : Fragment() {

    // view binding reference
    private var _vb: FragmentResultsBinding? = null
    private val vb get() = _vb!!

    // shared viewmodel with scan results
    private val vm: ScanViewModel by activityViewModels()

    // layout state and selections
    private var useGrid = true
    private val selectedIds = linkedSetOf<Long>()
    private lateinit var adapter: MediaAdapter

    // prevents user actions during recovery
    private var isRecovering = false

    // sorting options
    private enum class Sort { DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC, NAME_DESC }
    private var currentSort: Sort = Sort.DATE_DESC

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentResultsBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // handle system back to always go home
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitAndCleanup()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        // init adapter and layout manager
        adapter = MediaAdapter(
            isGrid = { useGrid },
            onToggleSelect = { item, source -> toggleSelection(item, source) },
            isSelected = { id -> selectedIds.contains(id) }
        )
        vb.list.adapter = adapter
        updateLayoutManager()

        // disable change animations for faster redraws
        (vb.list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        applySortAndShow()
        vb.empty.isVisible = adapter.itemCount == 0
        updateRecoverButton()

        // handle recover button click
        vb.recoverButton.setOnClickListener { button ->
            if (isRecovering) return@setOnClickListener
            val chosen = adapter.currentList.filter { selectedIds.contains(it.id) }
            if (chosen.isEmpty()) return@setOnClickListener

            button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isRecovering = true
            updateRecoverButton()
            adapter.notifyDataSetChanged()

            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val isMusicRecovery = areAllItemsAudio(chosen)

                try {
                    val recoveredCount = withContext(Dispatchers.IO) {
                        Recovery.copyAll(appContext, chosen)
                    }
                    selectedIds.clear()
                    if (_vb != null) adapter.notifyDataSetChanged()
                    performRecoveryResultHaptic(recoveredCount > 0)
                    activity?.let {
                        SnackbarUtils.showRecovered(it, recoveredCount, isMusicRecovery)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    performRecoveryResultHaptic(false)
                    activity?.let {
                        SnackbarUtils.showRecovered(it, 0, isMusicRecovery)
                    }
                } finally {
                    isRecovering = false
                    val binding = _vb
                    if (binding != null) {
                        binding.recoverButton.isPressed = false
                        binding.recoverButton.isActivated = false
                        binding.recoverButton.isSelected = false
                        updateRecoverButton()
                        adapter.notifyDataSetChanged()
                        binding.recoverButton.refreshDrawableState()
                    }
                }
            }
        }

        // sort dropdown setup
        val sortLabels = listOf(
            getString(R.string.sort_newest_first),
            getString(R.string.sort_oldest_first),
            getString(R.string.sort_largest_first),
            getString(R.string.sort_smallest_first),
            getString(R.string.sort_name_az_full),
            getString(R.string.sort_name_za_full)
        )
        vb.sortDropdown.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)

        vb.sortDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val newSort = when (position) {
                    0 -> Sort.DATE_DESC
                    1 -> Sort.DATE_ASC
                    2 -> Sort.SIZE_DESC
                    3 -> Sort.SIZE_ASC
                    4 -> Sort.NAME_ASC
                    else -> Sort.NAME_DESC
                }
                if (newSort != currentSort) {
                    currentSort = newSort
                    applySortAndShow()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // toolbar menu setup
        withMenu(R.menu.menu_results, onCreate = { menu ->
            val item = menu.findItem(R.id.action_toggle_layout)
            refreshToggleIcon(item)
        }) { item ->
            when (item.itemId) {
                R.id.action_toggle_layout -> {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    useGrid = !useGrid
                    updateLayoutManager()
                    refreshToggleIcon(item)
                    true
                }
                else -> false
            }
        }
    }

    // perform compatible success or failure feedback after recovery completes
    private fun performRecoveryResultHaptic(success: Boolean) {
        val root = _vb?.root ?: return
        val feedback = if (success) {
            HapticFeedbackConstantsCompat.CONFIRM
        } else {
            HapticFeedbackConstantsCompat.REJECT
        }
        ViewCompat.performHapticFeedback(root, feedback)
    }

    // clear results and return home
    private fun exitAndCleanup() {
        vm.results = emptyList()
        findNavController().popBackStack(R.id.homeFragment, false)
    }

    // update layout toggle icon based on mode
    private fun refreshToggleIcon(item: MenuItem?) {
        if (item == null) return
        val iconTint = resolveColorStateListAttr(
            androidx.appcompat.R.attr.colorControlNormal,
            com.google.android.material.R.attr.colorOnSurface
        )

        if (useGrid) {
            item.setIcon(R.drawable.ic_view_list)
            item.title = getString(R.string.action_view_list)
        } else {
            item.setIcon(R.drawable.ic_view_grid)
            item.title = getString(R.string.action_view_grid)
        }

        item.icon?.mutate()
        if (iconTint != null) item.icon?.setTintList(iconTint)
    }

    // switch between grid and list layout
    private fun updateLayoutManager() {
        vb.list.layoutManager = if (useGrid) GridLayoutManager(requireContext(), 3) else LinearLayoutManager(requireContext())
        (vb.list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    // sort results and refresh adapter
    private fun applySortAndShow() {
        val base = vm.results.orEmpty()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

        val sorted = when (currentSort) {
            Sort.DATE_DESC -> base.sortedByDescending { it.effectiveDateMs }
            Sort.DATE_ASC -> base.sortedBy { it.effectiveDateMs }
            Sort.SIZE_DESC -> base.sortedByDescending { it.sizeBytes }
            Sort.SIZE_ASC -> base.sortedBy { it.sizeBytes }
            Sort.NAME_ASC -> base.sortedWith(compareBy(collator) { it.displayName })
            Sort.NAME_DESC -> base.sortedWith(compareBy(collator) { it.displayName }).asReversed()
        }

        adapter.submitList(sorted) {
            val binding = _vb ?: return@submitList
            if (sorted.isNotEmpty()) {
                val layoutManager = binding.list.layoutManager
                (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                    ?: binding.list.scrollToPosition(0)
            }
            binding.empty.isVisible = sorted.isEmpty()
        }
    }

    // toggle selection for media item and provide state-specific feedback for normal taps
    private fun toggleSelection(item: MediaItem, source: View?) {
        if (isRecovering) return
        val selected = if (selectedIds.remove(item.id)) {
            false
        } else {
            selectedIds.add(item.id)
            true
        }
        source?.let {
            val feedback = if (selected) {
                HapticFeedbackConstantsCompat.TOGGLE_ON
            } else {
                HapticFeedbackConstantsCompat.TOGGLE_OFF
            }
            ViewCompat.performHapticFeedback(it, feedback)
        }
        updateRecoverButton()
        val position = adapter.currentList.indexOfFirst { it.id == item.id }
        if (position != -1) adapter.notifyItemChanged(position)
    }

    // update recover button label and state
    private fun updateRecoverButton() {
        if (isRecovering) {
            vb.recoverButton.isEnabled = false
            vb.recoverButton.text = getString(R.string.recovering)
            return
        }
        val count = selectedIds.size
        vb.recoverButton.isEnabled = count > 0
        vb.recoverButton.text = if (count > 0)
            getString(R.string.recover_selected_count, count)
        else getString(R.string.recover_selected)
    }

    override fun onDestroyView() {
        _vb = null
        super.onDestroyView()
    }

    // detect whether all selected items are audio
    private fun areAllItemsAudio(items: List<MediaItem>): Boolean {
        val contentResolver = requireContext().contentResolver
        return items.all { item ->
            val mimeType = item.mimeType.takeIf { it.isNotBlank() }
                ?: try { contentResolver.getType(item.uri) } catch (_: Exception) { null }
            mimeType?.startsWith("audio/") == true
        }
    }

    // load video frames with a graceful fallback
    private fun loadVideoThumbWithFallback(imageView: ImageView, uri: Uri, mimeType: String?) {
        imageView.tag = uri
        imageView.load(uri) {
            crossfade(true)
            videoFrameMillis(0)
            allowHardware(false)
            memoryCacheKey("$uri#t=0ms")
            if (!mimeType.isNullOrBlank()) parameters(Parameters.Builder().set("coil#image_source_mime_type", mimeType).build())
            size(ViewSizeResolver(imageView))
            listener(onError = { _, _ ->
                if (imageView.tag != uri) return@listener
                val owner = imageView.findViewTreeLifecycleOwner() ?: return@listener
                owner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val width = imageView.width.coerceAtLeast(200)
                        val height = imageView.height.coerceAtLeast(200)
                        val bitmap = imageView.context.contentResolver.loadThumbnail(uri, Size(width, height), CancellationSignal())
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == uri) imageView.setImageBitmap(bitmap)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        withContext(Dispatchers.Main) {
                            if (imageView.tag != uri) return@withContext
                            imageView.load(uri) {
                                crossfade(true)
                                videoFrameMillis(1_000)
                                allowHardware(false)
                                memoryCacheKey("$uri#t=1000ms")
                                if (!mimeType.isNullOrBlank()) {
                                    parameters(Parameters.Builder().set("coil#image_source_mime_type", mimeType).build())
                                }
                                size(ViewSizeResolver(imageView))
                            }
                        }
                    }
                }
            })
        }
    }

    // adapter for list/grid modes
    private inner class MediaAdapter(
        private val isGrid: () -> Boolean,
        private val onToggleSelect: (MediaItem, View?) -> Unit,
        private val isSelected: (Long) -> Boolean
    ) : ListAdapter<MediaItem, RecyclerView.ViewHolder>(
        object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem == newItem
        }
    ) {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).id
        override fun getItemViewType(position: Int) = if (isGrid()) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1)
                GridVH(ItemMediaGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else
                ListVH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            when (holder) {
                is ListVH -> holder.bind(item)
                is GridVH -> holder.bind(item)
            }
        }

        // list mode
        private inner class ListVH(private val b: ItemMediaBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: MediaItem) {
                val mimeType = item.mimeType.takeIf { it.isNotBlank() }
                val isVideo = item.isProbablyVideo || (mimeType?.startsWith("video/") == true)
                val isAudio = !isVideo && (mimeType?.startsWith("audio/") == true)

                // theme-aware audio background
                val bgColor = if (isAudio)
                    b.thumb.resolveThemeColorInt(com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorSecondary)
                else Color.TRANSPARENT
                b.thumb.setBackgroundColor(bgColor)
                b.thumb.tag = item.uri

                if (isVideo) {
                    loadVideoThumbWithFallback(b.thumb, item.uri, mimeType)
                } else {
                    b.thumb.load(item.uri) {
                        crossfade(true)
                        if (mimeType != null) parameters(Parameters.Builder().set("coil#image_source_mime_type", mimeType).build())
                        size(ViewSizeResolver(b.thumb))
                    }
                }

                b.playIcon?.isVisible = isVideo
                b.audioIcon?.isVisible = isAudio
                b.name?.text = item.displayName
                b.meta?.text = buildString {
                    append(item.dateReadable)
                    if (item.sizeBytes > 0) append("\n${formatSize(item.sizeBytes)}")
                }

                val selected = isSelected(item.id)
                b.root.findViewById<View>(R.id.overlay)?.isVisible = selected

                val disabled = isRecovering
                b.root.isEnabled = !disabled
                b.check.isEnabled = !disabled
                b.root.alpha = if (disabled) 0.92f else 1f

                b.check.setOnCheckedChangeListener(null)
                b.check.isChecked = selected
                b.check.setOnCheckedChangeListener { button, _ ->
                    if (!isRecovering) onToggleSelect(item, button)
                }

                val trashed = (item.origin == MediaItem.Origin.TRASHED)
                b.badge?.isVisible = trashed

                b.root.setOnClickListener { row ->
                    if (!isRecovering) onToggleSelect(item, row)
                }
                b.root.setOnLongClickListener {
                    if (!isRecovering) onToggleSelect(item, null)
                    !isRecovering
                }
            }
        }

        // grid mode
        private inner class GridVH(private val b: ItemMediaGridBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: MediaItem) {
                val mimeType = item.mimeType.takeIf { it.isNotBlank() }
                val isVideo = item.isProbablyVideo || (mimeType?.startsWith("video/") == true)
                val isAudio = !isVideo && (mimeType?.startsWith("audio/") == true)

                // theme-aware audio background
                val bgColor = if (isAudio)
                    b.thumb.resolveThemeColorInt(com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorSecondary)
                else Color.TRANSPARENT
                b.thumb.setBackgroundColor(bgColor)
                b.thumb.tag = item.uri

                if (isVideo) {
                    loadVideoThumbWithFallback(b.thumb, item.uri, mimeType)
                } else {
                    b.thumb.load(item.uri) {
                        crossfade(true)
                        if (mimeType != null) parameters(Parameters.Builder().set("coil#image_source_mime_type", mimeType).build())
                        size(ViewSizeResolver(b.thumb))
                    }
                }

                b.playIcon?.isVisible = isVideo
                b.audioIcon?.isVisible = isAudio
                b.caption?.text = item.displayName

                val selected = isSelected(item.id)
                b.root.findViewById<View>(R.id.overlay)?.isVisible = selected

                val disabled = isRecovering
                b.root.isEnabled = !disabled
                b.check.isEnabled = !disabled
                b.root.alpha = if (disabled) 0.92f else 1f

                b.check.setOnCheckedChangeListener(null)
                b.check.isChecked = selected
                b.check.setOnCheckedChangeListener { button, _ ->
                    if (!isRecovering) onToggleSelect(item, button)
                }

                val trashed = (item.origin == MediaItem.Origin.TRASHED)
                b.badge?.isVisible = trashed

                b.root.setOnClickListener { row ->
                    if (!isRecovering) onToggleSelect(item, row)
                }
                b.root.setOnLongClickListener {
                    if (!isRecovering) onToggleSelect(item, null)
                    !isRecovering
                }
            }
        }
    }
}

// format file sizes
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
    val scaled = bytes / 1024.0.pow(group)
    return String.format("%.1f %s", scaled, units[group])
}

// resolve color state list for icons or controls
private fun Fragment.resolveColorStateListAttr(@AttrRes attr: Int, @AttrRes fallbackAttr: Int? = null): ColorStateList? {
    val primary = requireContext().theme.obtainStyledAttributes(intArrayOf(attr)).use { it.getColorStateList(0) }
    if (primary != null) return primary
    return if (fallbackAttr != null) {
        requireContext().theme.obtainStyledAttributes(intArrayOf(fallbackAttr)).use { it.getColorStateList(0) }
    } else null
}

// resolve a raw color int from a theme attr with optional fallback
private fun View.resolveThemeColorInt(@AttrRes attr: Int, @AttrRes fallbackAttr: Int? = null): Int {
    val ta = context.theme.obtainStyledAttributes(intArrayOf(attr))
    val color = try { ta.getColor(0, Color.TRANSPARENT) } finally { ta.recycle() }
    if (color != Color.TRANSPARENT) return color
    if (fallbackAttr == null) return color
    val fb = context.theme.obtainStyledAttributes(intArrayOf(fallbackAttr))
    return try { fb.getColor(0, Color.TRANSPARENT) } finally { fb.recycle() }
}
