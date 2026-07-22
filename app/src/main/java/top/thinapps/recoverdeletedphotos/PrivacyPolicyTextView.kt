package top.thinapps.recoverdeletedphotos

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PrivacyPolicyTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    init {
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privacy_policy_title)
                .setMessage(R.string.privacy_policy_body)
                .setPositiveButton(R.string.action_close, null)
                .show()
        }
    }
}
