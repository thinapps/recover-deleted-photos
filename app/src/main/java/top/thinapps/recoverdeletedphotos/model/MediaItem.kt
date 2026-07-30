package top.thinapps.recoverdeletedphotos.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// data class for media exposed by a scan or recovered-file query
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateTakenMs: Long? = null,
    val origin: Origin = Origin.NORMAL,
    val isProbablyVideo: Boolean = false,
    val mimeType: String = ""
) {

    // identifies whether media comes from normal storage or the system trash
    enum class Origin { NORMAL, TRASHED }

    val effectiveDateMs: Long
        get() = dateTakenMs ?: dateAddedSec * 1000

    // computed property to return a user-friendly date string for display
    val dateReadable: String
        get() = sharedFormatter.format(Date(effectiveDateMs))

    companion object {
        // singleton date formatter used to ensure consistent date output across locales
        private val sharedFormatter by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        }
    }
}
