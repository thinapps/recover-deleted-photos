package top.thinapps.recoverdeletedphotos.ui

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.thinapps.recoverdeletedphotos.MainActivity
import top.thinapps.recoverdeletedphotos.R
import top.thinapps.recoverdeletedphotos.databinding.FragmentRecoveredAudioBinding
import top.thinapps.recoverdeletedphotos.databinding.ItemMediaBinding
import top.thinapps.recoverdeletedphotos.model.MediaItem
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.log10
import kotlin.math.pow

class RecoveredAudioFragment : Fragment() {

    private var _vb: FragmentRecoveredAudioBinding? = null
    private val vb get() = _vb!!
    private lateinit var adapter: RecoveredAudioAdapter
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _vb = FragmentRecoveredAudioBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setToolbarVisible(true)
        (activity as? MainActivity)?.setToolbarTitle(
            getString(R.string.recovered_audio_title)
        )

        vb.recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecoveredAudioAdapter { item -> openItem(item) }
        vb.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadRecoveredItems()
    }

    private fun loadRecoveredItems() {
        val binding = _vb ?: return
        val resolver = requireContext().contentResolver

        loadJob?.cancel()
        adapter.submit(emptyList())

        if (!hasPermission()) {
            binding.stateMessage.text = getString(R.string.recovered_permission_required)
            binding.stateMessage.isVisible = true
            return
        }

        binding.stateMessage.isVisible = true
        binding.stateMessage.text = getString(R.string.recovered_loading)

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { loadItems(resolver) }

            val currentBinding = _vb ?: return@launch
            if (list.isEmpty()) {
                currentBinding.stateMessage.text = getString(R.string.recovered_audio_empty)
            } else {
                currentBinding.stateMessage.isVisible = false
                adapter.submit(list)
            }
        }
    }

    private fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT < 33) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_MEDIA_AUDIO
        }

        return ContextCompat.checkSelfPermission(
            requireContext(), perm
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun loadItems(resolver: ContentResolver): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val collection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )

        try {
            queryCancellable(
                resolver = resolver,
                collection = collection,
                projection = projection,
                selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)",
                selectionArgs = arrayOf("Music/Recovered", "Music/Recovered/"),
                sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                while (c.moveToNext()) {
                    coroutineContext.ensureActive()

                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "Unnamed"
                    val size = c.getLong(sizeIdx)
                    val date = c.getLong(dateIdx)
                    val mime = c.getString(mimeIdx) ?: ""
                    val uri = ContentUris.withAppendedId(collection, id)

                    val item = MediaItem(
                        id = id,
                        uri = uri,
                        displayName = name,
                        sizeBytes = size,
                        dateAddedSec = date,
                        origin = MediaItem.Origin.NORMAL,
                        isProbablyVideo = false,
                        mimeType = mime
                    )

                    out += item
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OperationCanceledException) {
            coroutineContext.ensureActive()
        } catch (_: SecurityException) {
            // permission can change while the viewer is loading; return no items
        } catch (_: IllegalArgumentException) {
            // return no items for unsupported or inaccessible provider queries
        } catch (_: RuntimeException) {
            // return no items for unexpected device-specific MediaStore failures
        }

        return out
    }

    // ties a blocking provider query to coroutine cancellation
    private suspend fun queryCancellable(
        resolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String
    ): Cursor? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }

        try {
            val cursor = resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder,
                signal
            )
            if (continuation.isActive) {
                continuation.resume(cursor)
            } else {
                cursor?.close()
            }
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    private fun openItem(item: MediaItem) {
        val ctx = context ?: return
        val mime = if (item.mimeType.isNotBlank()) item.mimeType else "audio/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            _vb?.root?.let {
                ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.REJECT)
            }
            Toast.makeText(
                ctx,
                getString(R.string.recovered_open_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
            .coerceAtMost(units.lastIndex)
        val scaled = bytes / 1024.0.pow(group.toDouble())
        return String.format(Locale.US, "%.1f %s", scaled, units[group])
    }

    private inner class RecoveredAudioAdapter(
        private val click: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<RecoveredAudioAdapter.VH>() {

        private val data = mutableListOf<MediaItem>()

        fun submit(list: List<MediaItem>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemMediaBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: MediaItem) {
                val b = binding

                b.playIcon?.isVisible = false
                b.audioIcon?.isVisible = true

                // static thumb for audio items
                b.thumb.setImageResource(R.drawable.ic_audio_overlay)
                b.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE

                b.name?.text = item.displayName
                b.meta?.text = buildString {
                    append(item.dateReadable)
                    if (item.sizeBytes > 0) {
                        append("\n${formatSize(item.sizeBytes)}")
                    }
                }

                // recovered audio viewer is read-only; hide checkbox + overlay
                b.check?.isVisible = false
                b.overlay?.isVisible = false

                b.root.setOnClickListener { click(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemMediaBinding.inflate(inflater, parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position])
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        _vb = null
        super.onDestroyView()
    }
}
