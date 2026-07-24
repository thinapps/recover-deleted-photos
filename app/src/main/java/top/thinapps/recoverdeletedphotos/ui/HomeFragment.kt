package top.thinapps.recoverdeletedphotos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.fragment.findNavController
import top.thinapps.recoverdeletedphotos.MainActivity
import top.thinapps.recoverdeletedphotos.R
import top.thinapps.recoverdeletedphotos.databinding.FragmentHomeBinding

// arg key for scan destination
private const val ARG_TYPE = "type"

class HomeFragment : Fragment() {

    // viewbinding backing property
    private var _vb: FragmentHomeBinding? = null
    private val vb get() = _vb!!

    // media type choices
    private enum class TypeChoice { PHOTOS, VIDEOS, AUDIO }

    // pending type across permission request
    private var pendingType: TypeChoice? = null

    // tracks which permissions were already requested in this view session
    private val requestedPermissions = mutableSetOf<String>()

    // permission launcher for a single permission
    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // skip if fragment is not attached
        if (!isAdded) return@registerForActivityResult

        // read and clear pending type
        val type = pendingType
        pendingType = null

        // reenable primary action after request completes
        vb.startButton.isEnabled = true

        // navigate on success else refresh label
        if (granted && type != null) {
            navigateToScan(type)
        } else {
            updateButtonText()
        }
    }

    // android 13 feature gate
    private fun isAndroid13Plus(): Boolean = Build.VERSION.SDK_INT >= TIRAMISU

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // inflate layout
        _vb = FragmentHomeBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // ensure toolbar visible with app title
        (activity as? MainActivity)?.setToolbarVisible(true)
        (activity as? MainActivity)?.setToolbarTitle(getString(R.string.app_name))

        // show subtitle on home
        vb.subtitle.isVisible = true

        // open the in-app Recovered Photos/Videos list
        vb.buttonViewRecoveredPhotosVideos.setOnClickListener { button ->
            button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_home_to_recovered)
        }

        // open the in-app Recovered Audio list
        vb.buttonViewRecoveredAudio.setOnClickListener { button ->
            button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_home_to_recovered_audio)
        }

        // disable scan features on pre android 13
        if (!isAndroid13Plus()) {
            vb.startButton.isEnabled = false
            vb.homeTypeRow.isVisible = false
            vb.stateMessage.text = getString(R.string.android_13_required_msg)
            vb.stateMessage.isVisible = true
            return
        }

        // set initial button label
        updateButtonText()

        // keep button label in sync with radio choice
        vb.homeTypeRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateButtonText()
        }

        // main action for request or scan or settings
        vb.startButton.setOnClickListener { button ->
            button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            vb.startButton.isEnabled = false

            val type = currentType()
            val perm = requiredPerm(type)

            // flow 1 permission granted -> start scan
            if (hasPermission(perm)) {
                navigateToScan(type)
                vb.startButton.isEnabled = true
                return@setOnClickListener
            }

            // flow 2 request this permission once, then route to settings after denial
            if (perm !in requestedPermissions) {
                pendingType = type
                requestedPermissions += perm
                requestPerm.launch(perm)
            } else {
                openAppSettings()
                vb.startButton.isEnabled = true
            }
        }

        // subtle entrance animation for title and subtitle
        val interp = FastOutSlowInInterpolator()

        vb.title.translationY = resources.getDimension(R.dimen.space_12)
        vb.title.alpha = 0f
        vb.title.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(250L)
            .setInterpolator(interp)
            .start()

        vb.subtitle.alpha = 0f
        vb.subtitle.animate()
            .alpha(1f)
            .setStartDelay(60L)
            .setDuration(220L)
            .setInterpolator(interp)
            .start()
    }

    // updates the primary button text based on permission and request history
    private fun updateButtonText() {
        val perm = requiredPerm(currentType())
        val textRes = when {
            hasPermission(perm) -> R.string.start_scan
            perm !in requestedPermissions -> R.string.start_scan
            else -> R.string.action_grant_settings
        }
        vb.startButton.text = getString(textRes)
        vb.startButton.isEnabled = true
    }

    // returns the selected media type
    private fun currentType(): TypeChoice = when {
        vb.homeTypeVideos?.isChecked == true -> TypeChoice.VIDEOS
        vb.homeTypeAudio?.isChecked == true -> TypeChoice.AUDIO
        else -> TypeChoice.PHOTOS
    }

    // returns the permission required for a given media type
    private fun requiredPerm(type: TypeChoice): String {
        return if (Build.VERSION.SDK_INT < 33) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            when (type) {
                TypeChoice.PHOTOS -> Manifest.permission.READ_MEDIA_IMAGES
                TypeChoice.VIDEOS -> Manifest.permission.READ_MEDIA_VIDEO
                TypeChoice.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
            }
        }
    }

    // checks if a permission is already granted
    private fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    // opens system app settings for this package
    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

    // navigates to the scan screen with the selected type
    private fun navigateToScan(type: TypeChoice) {
        findNavController().navigate(
            R.id.action_home_to_scan,
            bundleOf(ARG_TYPE to type.name)
        )
    }

    override fun onResume() {
        super.onResume()
        if (_vb != null && isAndroid13Plus()) updateButtonText()
    }

    override fun onDestroyView() {
        // clear transient state and bindings
        pendingType = null
        requestedPermissions.clear()
        _vb = null
        super.onDestroyView()
    }
}
