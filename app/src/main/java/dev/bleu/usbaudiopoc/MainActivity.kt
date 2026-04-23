package dev.bleu.usbaudiopoc

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.databinding.ActivityMainBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import dev.bleu.usbaudiopoc.ui.FragmentBrowser
import dev.bleu.usbaudiopoc.ui.FragmentEngine
import dev.bleu.usbaudiopoc.ui.FragmentPlaying
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var notificationManager: dev.bleu.usbaudiopoc.player.MediaNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationManager = dev.bleu.usbaudiopoc.player.MediaNotificationManager(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        setupNavigation()
        setupDrawer()
        observeState()
        setupBackPress()

        if (savedInstanceState == null) {
            loadFragment(FragmentPlaying())
            binding.bottomNavigation.selectedItemId = R.id.navigation_playing
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerState.collect { state ->
                        notificationManager.updateNotification(state)
                        val isBitPerfect = state.routeOverride == AudioRouteOverride.BIT_PERFECT
                        binding.statusBadge.text = if (isBitPerfect) "BIT-PERFECT" else "DSP MODE"
                        binding.statusBadge.setTextColor(
                            if (isBitPerfect) getColor(R.color.accent_warm) else getColor(R.color.outline_dark)
                        )
                    }
                }
                launch {
                    // Navigate to Now Playing when a track/folder is loaded
                    viewModel.navigateToNowPlaying.collect { navigate ->
                        if (navigate) {
                            viewModel.consumeNavigateToNowPlaying()
                            binding.bottomNavigation.selectedItemId = R.id.navigation_playing
                            loadFragment(FragmentPlaying())
                        }
                    }
                }
            }
        }
    }

    private fun setupDrawer() {
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.bottomNavigation.selectedItemId = R.id.navigation_playing
                R.id.nav_library -> binding.bottomNavigation.selectedItemId = R.id.navigation_browser
                R.id.nav_engine -> binding.bottomNavigation.selectedItemId = R.id.navigation_engine
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_browser -> loadFragment(FragmentBrowser())
                R.id.navigation_playing -> loadFragment(FragmentPlaying())
                R.id.navigation_engine -> loadFragment(FragmentEngine())
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        return super.onSupportNavigateUp()
    }
}
