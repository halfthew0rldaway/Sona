package dev.bleu.usbaudiopoc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import dev.bleu.usbaudiopoc.databinding.ActivityMainBinding
import dev.bleu.usbaudiopoc.ui.FragmentBrowser
import dev.bleu.usbaudiopoc.ui.FragmentEngine
import dev.bleu.usbaudiopoc.ui.FragmentPlaying

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        
        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(FragmentPlaying())
            binding.bottomNavigation.selectedItemId = R.id.navigation_playing
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
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        return true
    }
}
