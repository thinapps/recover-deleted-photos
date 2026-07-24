package top.thinapps.recoverdeletedphotos.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import top.thinapps.recoverdeletedphotos.model.MediaItem

class MediaScanner(private val context: Context) {

    // constants for selection and paging
    private companion object {
        private const val SEL_BASE =
            "${MediaStore.MediaColumns.SIZE} > ? AND ${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL"
        private val SEL_ARGS_BASE = arrayOf("0")
        private const val PAGE_SIZE = 500
    }

    // main scan routine across image/video/audio tables
    suspend fun scan(
        includeImages: Boolean,
        includeVideos: Boolean,
        includeAudio: Boolean,
        onProgress: (found: Int, total: Int) -> Unit
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<MediaItem>()
        val seenUris = mutableSetOf<Uri>()

        // build query list based on user selections
        val queries = buildList {
            if (includeImages) {
                add(
                    QuerySpec(
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id = MediaStore.Images.Media._ID,
                        name = MediaStore.Images.Media.DISPLAY_NAME,
                        size = MediaStore.Images.Media.SIZE,
                        datePrimary = MediaStore.Images.Media.DATE_ADDED,
                        dateTaken = MediaStore.Images.Media.DATE_TAKEN,
                        excludedPath = "Pictures/Recovered"
                    )
                )
            }
            if (includeVideos) {
                add(
                    QuerySpec(
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id = MediaStore.Video.Media._ID,
                        name = MediaStore.Video.Media.DISPLAY_NAME,
                        size = MediaStore.Video.Media.SIZE,
                        datePrimary = MediaStore.Video.Media.DATE_ADDED,
                        dateTaken = MediaStore.Video.Media.DATE_TAKEN,
                        excludedPath = "Pictures/Recovered"
                    )
                )
            }
            if (includeAudio) {
                add(
                    QuerySpec(
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id = MediaStore.Audio.Media._ID,
                        name = MediaStore.Audio.Media.DISPLAY_NAME,
                        size = MediaStore.Audio.Media.SIZE,
                        datePrimary = MediaStore.Audio.Media.DATE_ADDED,
                        dateTaken = null,
                        excludedPath = "Music/Recovered"
                    )
                )
            }
        }

        // return early if nothing selected
        if (queries.isEmpty()) return@withContext emptyList<MediaItem>()

        // calculate total count (helps progress tracking)
        val total = queries.sumOf { q ->
            safeQueryCount(q, selectionFor(q).first, selectionFor(q).second)
        }.coerceAtLeast(1)

        var found = 0
        var lastEmit = 0L

        // process each query sequentially
        for (q in queries) {
            if (!coroutineContext.isActive) break

            val projection = buildProjection(q)

            // page results on api 30+ to reduce cursor memory
            if (Build.VERSION.SDK_INT >= 30) {
                var offset = 0
                var done = false
                while (!done && coroutineContext.isActive) {
                    val cancel = CancellationSignal()
                    val (sel, selArgs) = selectionFor(q)
                    val consumed = resolverQuery(
                        uri = q.uri,
                        projection = projection,
                        sortCol = q.sortCol(),
                        selection = sel,
                        selectionArgs = selArgs,
                        limit = PAGE_SIZE,
                        offset = offset,
                        signal = cancel
                    )?.use { c ->
                        consumeCursor(c, q, seenUris, out) { inc ->
                            found += inc
                            maybeEmitProgress(onProgress, found, total, ::nanoNow, lastEmit).also {
                                lastEmit = it
                            }
                        }
                    } ?: 0
                    if (consumed < PAGE_SIZE) {
                        done = true
                    } else {
                        offset += PAGE_SIZE
                    }
                }
            } else {
                // legacy non-paged path
                val cancel = CancellationSignal()
                val (sel, selArgs) = selectionFor(q)
                resolverQuery(
                    uri = q.uri,
                    projection = projection,
                    sortCol = q.sortCol(),
                    selection = sel,
                    selectionArgs = selArgs,
                    limit = null,
                    offset = null,
                    signal = cancel
                )?.use { c ->
                    consumeCursor(c, q, seenUris, out) { inc ->
                        found += inc
                        maybeEmitProgress(onProgress, found, total, ::nanoNow, lastEmit).also {
                            lastEmit = it
                        }
                    }
                }
            }
        }

        // final progress update
        onProgress(found, total)
        out
    }

    // count rows safely with fallback to 0
    private fun safeQueryCount(q: QuerySpec, selection: String?, args: Array<String>?): Int {
        return try {
            resolverQuery(
                uri = q.uri,
                projection = arrayOf(q.id),
                sortCol = q.sortCol(),
                selection = selection,
                selectionArgs = args,
                limit = null,
                offset = null,
                signal = null
            )?.use { it.count } ?: 0
        } catch (_: SecurityException) {
            0
        } catch (_: IllegalArgumentException) {
            0
        }
    }

    // selection helper excludes pending rows and this app's recovery output folders
    private fun selectionFor(q: QuerySpec): Pair<String?, Array<String>?> {
        var sel = SEL_BASE
        val args = SEL_ARGS_BASE.toMutableList()

        if (Build.VERSION.SDK_INT == 29) {
            sel += " AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        }

        if (Build.VERSION.SDK_INT >= 29) {
            sel +=
                " AND (${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} NOT IN (?, ?))"
            args += q.excludedPath
            args += "${q.excludedPath}/"
        }

        return sel to args.toTypedArray()
    }

    // build projection dynamically per query type
    private fun buildProjection(q: QuerySpec): Array<String> {
        val base = mutableListOf(q.id, q.name, q.size, q.datePrimary)
        if (q.dateTaken != null) base += q.dateTaken
        if (Build.VERSION.SDK_INT >= 29) base += MediaStore.MediaColumns.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 30) base += MediaStore.MediaColumns.IS_TRASHED
        base += MediaStore.MediaColumns.MIME_TYPE
        return base.toTypedArray()
    }

    // unified resolver query for both modern and legacy apis
    private fun resolverQuery(
        uri: Uri,
        projection: Array<String>,
        sortCol: String,
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int?,
        offset: Int?,
        signal: CancellationSignal?
    ) = if (Build.VERSION.SDK_INT >= 26) {
        val extras = buildQueryExtras(
            sortCol = sortCol,
            selection = selection,
            selectionArgs = selectionArgs,
            limit = limit,
            offset = offset
        )
        context.contentResolver.query(uri, projection, extras, signal)
    } else {
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "$sortCol DESC, ${MediaStore.MediaColumns._ID} DESC"
        )
    }

    // extras bundle for api 26+ query options
    private fun buildQueryExtras(
        sortCol: String,
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int?,
        offset: Int?
    ): Bundle {
        return Bundle().apply {
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(sortCol, MediaStore.MediaColumns._ID)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            if (selection != null) {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (selectionArgs != null) {
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        selectionArgs
                    )
                }
            }
            if (limit != null) putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            if (offset != null) putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            if (Build.VERSION.SDK_INT >= 30) {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
        }
    }

    // iterate cursor and build media items
    private suspend fun consumeCursor(
        c: android.database.Cursor,
        q: QuerySpec,
        seenUris: MutableSet<Uri>,
        out: MutableList<MediaItem>,
        onItems: (inc: Int) -> Unit
    ): Int {
        val idIdx = c.getColumnIndexOrThrow(q.id)
        val nameIdx = c.getColumnIndexOrThrow(q.name)
        val sizeIdx = c.getColumnIndexOrThrow(q.size)
        val dateIdx = c.getColumnIndexOrThrow(q.datePrimary)

        val dateTakenIdx = q.dateTaken?.let { c.getColumnIndex(it) } ?: -1
        val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val trashedIdx =
            if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1

        var consumed = 0
        while (c.moveToNext()) {
            if (!coroutineContext.isActive) break

            consumed++
            val id = c.getLong(idIdx)
            val uri = ContentUris.withAppendedId(q.uri, id)
            if (!seenUris.add(uri)) continue

            val name = c.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: "recovered_$id"
            val size = c.getLong(sizeIdx)
            val dateAdded = c.getLong(dateIdx)
            val dateTaken = if (dateTakenIdx != -1 && !c.isNull(dateTakenIdx)) {
                c.getLong(dateTakenIdx).takeIf { it > 0L }
            } else {
                null
            }
            val isTrashed = trashedIdx != -1 && c.getInt(trashedIdx) == 1

            val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: "" else ""
            val isProbablyVideo = mime.startsWith("video/")

            out += MediaItem(
                id = id,
                uri = uri,
                displayName = name,
                sizeBytes = size,
                dateAddedSec = dateAdded,
                dateTakenMs = dateTaken,
                origin = if (isTrashed) MediaItem.Origin.TRASHED else MediaItem.Origin.NORMAL,
                isProbablyVideo = isProbablyVideo,
                mimeType = mime
            )

            onItems(1)

            // yield occasionally to stay responsive
            if ((consumed and 127) == 0) yield()
        }
        return consumed
    }

    // rate-limit progress updates to ~100ms
    private fun maybeEmitProgress(
        onProgress: (Int, Int) -> Unit,
        found: Int,
        total: Int,
        now: () -> Long,
        lastEmit: Long
    ): Long {
        val n = now()
        if (found == total || n - lastEmit > 100_000_000L || (found and 127) == 0) {
            onProgress(found, total)
            return n
        }
        return lastEmit
    }

    // monotonic timer for throttling
    private fun nanoNow(): Long = System.nanoTime()

    // spec definition for per-table query
    private data class QuerySpec(
        val uri: Uri,
        val id: String,
        val name: String,
        val size: String,
        val datePrimary: String,
        val dateTaken: String?,
        val excludedPath: String
    ) {
        fun sortCol(): String = datePrimary
    }
}
