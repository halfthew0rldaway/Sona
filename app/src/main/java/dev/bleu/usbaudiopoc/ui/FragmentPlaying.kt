package dev.bleu.usbaudiopoc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.bleu.usbaudiopoc.audio.UsbAudioBackend
import dev.bleu.usbaudiopoc.databinding.FragmentPlayingBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import kotlinx.coroutines.launch

class FragmentPlaying : Fragment() {

    private var _binding: FragmentPlayingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        collectUiState()
    }

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            if (viewModel.playerState.value.isPlaying) {
                viewModel.pause()
            } else {
                viewModel.play()
            }
        }
        binding.btnStop.setOnClickListener { viewModel.stop() }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerState.collect { playerState ->
                        val hasSelection = playerState.selectedFileLabel != "none selected"
                        val isUsb = playerState.activeBackend == UsbAudioBackend::class.simpleName

                        binding.trackTitle.text = if (hasSelection) {
                            playerState.selectedFileLabel
                        } else {
                            "No Master Loaded"
                        }
                        binding.trackSubtitle.text = when {
                            playerState.isPlaying -> "Playback is running through the selected output path."
                            playerState.isPaused -> "Playback paused. Resume whenever you're ready."
                            hasSelection -> "Selected in Browser and ready to route."
                            else -> "Choose a WAV file in Browser to begin playback."
                        }

                        binding.currentPathPrimary.text = when {
                            isUsb -> "USB"
                            playerState.activeBackend != null -> "SYS"
                            hasSelection -> "READY"
                            else -> "IDLE"
                        }
                        binding.currentPathSecondary.text = when {
                            playerState.isPlaying -> "LIVE"
                            playerState.isPaused -> "HOLD"
                            hasSelection -> "ARMED"
                            else -> "STBY"
                        }
                        binding.sourceFileValue.text = if (hasSelection) "WAV MASTER" else "NONE"
                        binding.backendName.text = when (playerState.activeBackend) {
                            UsbAudioBackend::class.simpleName -> "BIT-PERFECT BACKEND"
                            "AudioTrackBackend" -> "ANDROID AUDIOTRACK"
                            null -> if (hasSelection) "READY TO ROUTE" else "NO ACTIVE BACKEND"
                            else -> playerState.activeBackend
                        }
                        binding.playbackStatusText.text = playerState.statusMessage.uppercase()
                        binding.badgeBitPerfect.visibility = if (isUsb) View.VISIBLE else View.GONE

                        if (playerState.isPlaying) {
                            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                        } else {
                            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        }
                    }
                }
                launch {
                    viewModel.usbState.collect { usbState ->
                        binding.outputDevice.text = usbState.compatibleDevice?.productName
                            ?: usbState.compatibleDevice?.deviceName
                            ?: "PHONE OUTPUT"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
