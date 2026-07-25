package top.thinapps.recoverdeletedphotos

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import top.thinapps.recoverdeletedphotos.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    // navigation app bar configuration for proper up button behavior
    private lateinit var appBarConfig: AppBarConfiguration

    // cached reference to the navigation controller
    private lateinit var navController: NavController

    // view binding for the activity layout, owned by the activity
    private lateinit var vb: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enables the edge-to-edge behavior required by current Android versions
        enableEdgeToEdge()

        // inflate layout and set as content view
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        applyWindowInsets()

        // set the material toolbar as the action bar
        setSupportActionBar(vb.toolbar)

        // robust navigation controller lookup
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as? NavHostFragment
            ?: return // fails safe if layout id is missing

        navController = navHost.navController

        // wire navigation to action bar using nav graph labels for titles
        appBarConfig = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfig)
    }

    // keeps all app controls clear of status, navigation, and display-cutout areas
    private fun applyWindowInsets() {
        val baseLeft = vb.root.paddingLeft
        val baseTop = vb.root.paddingTop
        val baseRight = vb.root.paddingRight
        val baseBottom = vb.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(vb.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = baseLeft + bars.left,
                top = baseTop + bars.top,
                right = baseRight + bars.right,
                bottom = baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(vb.root)
    }

    // supports the up button navigation functionality
    override fun onSupportNavigateUp(): Boolean {
        // checks if the controller has been initialized
        if (!::navController.isInitialized) {
            return super.onSupportNavigateUp()
        }

        // Route screens with custom back feedback or cleanup through the back dispatcher.
        val currentId = navController.currentDestination?.id
        if (
            currentId == R.id.scanFragment ||
            currentId == R.id.resultsFragment ||
            currentId == R.id.recoveredFragment ||
            currentId == R.id.recoveredAudioFragment
        ) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }

        // attempts to navigate up using the navigation component
        return navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
    }

    // centralized helper for fragments to set a custom toolbar title
    fun setToolbarTitle(title: CharSequence?) {
        supportActionBar?.title = title
    }

    // centralized helper for fragments to toggle toolbar visibility
    fun setToolbarVisible(visible: Boolean) {
        // safely checks if view binding is initialized before accessing the view
        if (::vb.isInitialized) vb.toolbar.isVisible = visible
    }
}
