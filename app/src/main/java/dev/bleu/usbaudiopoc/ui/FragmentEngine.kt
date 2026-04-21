package dev.bleu.usbaudiopoc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.bleu.usbaudiopoc.R
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.databinding.FragmentEngineBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class FragmentEngine : Fragment() {

    private var _binding: FragmentEngineBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEngineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRouteCards()
        collectUiState()
    }

    private fun setupRouteCards() {
        binding.routeAutoCard.setOnClickListener {
            viewModel.setRouteOverride(AudioRouteOverride.AUTO)
        }
        binding.routeUsbCard.setOnClickListener {
            viewModel.setRouteOverride(AudioRouteOverride.USB_ONLY)
        }
        binding.routeAndroidCard.setOnClickListener {
            viewModel.setRouteOverride(AudioRouteOverride.ANDROID_ONLY)
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerState.collect { playerState ->
                        val isUsb = playerState.activeBackend == "UsbAudioBackend"
                        updateRouteSelection(playerState.routeOverride)
                        binding.bitPerfectStatusText.text = when {
                            isUsb -> "BIT-PERFECT MODE ACTIVE"
                            playerState.routeOverride == AudioRouteOverride.USB_ONLY -> "USB EXCLUSIVE MODE ARMED"
                            playerState.routeOverride == AudioRouteOverride.ANDROID_ONLY -> "ANDROID MIXER PATH SELECTED"
                            else -> "AUTO ROUTING ENABLED"
                        }
                    }
                }
                launch {
                    viewModel.usbState.collect { usbState ->
                        val device = usbState.compatibleDevice
                        binding.hardwareName.text = if (device != null) {
                            device.productName ?: device.deviceName
                        } else {
                            "No DAC Connected"
                        }
                        binding.hardwareStatusText.text = if (device != null) {
                            usbState.description
                        } else {
                            "Connect a compatible USB DAC to unlock direct playback."
                        }
                        binding.vendorId.text = device?.vendorId?.let(::formatHex) ?: "--"
                        binding.productId.text = device?.productId?.let(::formatHex) ?: "--"
                        binding.hardwarePermissionStatus.text = when {
                            device == null -> "WAITING FOR HARDWARE"
                            usbState.hasPermission -> "PERMISSION GRANTED"
                            else -> "PERMISSION REQUIRED"
                        }
                    }
                }
            }
        }
    }

    private fun updateRouteSelection(routeOverride: AudioRouteOverride) {
        setRouteCardState(
            selected = routeOverride == AudioRouteOverride.AUTO,
            card = binding.routeAutoCard,
            title = binding.routeAutoTitle,
            indicator = binding.routeAutoIndicator,
        )
        setRouteCardState(
            selected = routeOverride == AudioRouteOverride.USB_ONLY,
            card = binding.routeUsbCard,
            title = binding.routeUsbTitle,
            indicator = binding.routeUsbIndicator,
        )
        setRouteCardState(
            selected = routeOverride == AudioRouteOverride.ANDROID_ONLY,
            card = binding.routeAndroidCard,
            title = binding.routeAndroidTitle,
            indicator = binding.routeAndroidIndicator,
        )
    }

    private fun setRouteCardState(selected: Boolean, card: View, title: TextView, indicator: View) {
        card.setBackgroundResource(if (selected) R.drawable.bg_route_card_active else R.drawable.bg_route_card)
        indicator.setBackgroundResource(if (selected) R.drawable.bg_route_indicator_active else R.drawable.bg_route_indicator)
        title.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.primary_container else R.color.on_surface,
            )
        )
    }

    private fun formatHex(value: Int): String {
        return String.format(Locale.US, "0x%04X", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
