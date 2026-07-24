package top.thinapps.recoverdeletedphotos.ui

import android.app.Activity
import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.thinapps.recoverdeletedphotos.R

object SnackbarUtils {

    private fun rootView(activity: Activity): View =
        activity.findViewById(android.R.id.content)

    private fun anchorView(activity: Activity): View? =
        activity.findViewById(R.id.recoverButton)

    suspend fun showRecovered(activity: Activity, count: Int, isAudioOnly: Boolean) {
        withContext(Dispatchers.Main) {
            val message = if (count == 0) {
                activity.getString(R.string.recovery_none)
            } else {
                val destinationRes = if (isAudioOnly) {
                    R.string.recovery_folder_music
                } else {
                    R.string.recovery_folder_pictures
                }
                val destination = activity.getString(destinationRes)
                activity.resources.getQuantityString(
                    R.plurals.recovery_success,
                    count,
                    count,
                    destination
                )
            }
            Snackbar.make(
                rootView(activity),
                message,
                Snackbar.LENGTH_LONG
            ).setAnchorView(anchorView(activity))
             .show()
        }
    }
}
