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
import androidx.cardview.widget.CardView

class FragmentEngine : Fragment() {

    private var _binding: FragmentEngineBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEngineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.routeBitperfectCard.setOnClickListener { viewModel.setRouteOverrideSafe(AudioRouteOverride.BIT_PERFECT) }
        binding.routeDspCard.setOnClickListener { viewModel.setRouteOverrideSafe(AudioRouteOverride.DSP) }
        binding.btnGrantUsbAccess.setOnClickListener { viewModel.requestUsbPermission() }

        binding.btnOpenEqModal.setOnClickListener {
            EqBottomSheet().show(parentFragmentManager, EqBottomSheet.TAG)
        }

        collectUiState()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerState.collect { state ->
                        val isBitPerfect = state.routeOverride == AudioRouteOverride.BIT_PERFECT
                        updateRouteSelection(state.routeOverride)

                        binding.transitionSpinner.visibility = if (state.isTransitioning) View.VISIBLE else View.GONE

                        binding.bitPerfectStatusText.text = when {
                            isBitPerfect -> "BIT-PERFECT ACTIVE"
                            else -> "DSP MODE ENABLED"
                        }

                        binding.nodeDsp.visibility = if (isBitPerfect) View.GONE else View.VISIBLE
                        binding.textDspHint.visibility = if (isBitPerfect) View.VISIBLE else View.GONE
                        binding.eqSectionContainer.visibility = if (isBitPerfect) View.GONE else View.VISIBLE

                        updateOutputInfoPanel(state)
                    }
                }
                launch {
                    viewModel.usbState.collect { usbState ->
                        val device = usbState.compatibleDevice
                        binding.hardwareName.text = device?.productName ?: device?.deviceName ?: "No DAC Connected"
                        binding.hardwareStatusText.text = when {
                            device == null -> "Connect a compatible USB DAC."
                            viewModel.playerState.value.routeOverride == AudioRouteOverride.BIT_PERFECT -> "DAC Active — Exclusive Access"
                            else -> "DAC Not Exclusive"
                        }
                        binding.vendorId.text = device?.vendorId?.let { formatHex(it) } ?: "--"
                        binding.productId.text = device?.productId?.let { formatHex(it) } ?: "--"
                        binding.hardwarePermissionStatus.text = when {
                            device == null -> "--"
                            usbState.hasPermission -> "GRANTED"
                            else -> "NEEDED"
                        }
                        // Warm palette: granted = mint for positive feedback, needed = error
                        binding.hardwarePermissionStatus.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (usbState.hasPermission) R.color.accent_mint_dark else R.color.error
                            )
                        )
                        binding.btnGrantUsbAccess.visibility =
                            if (device != null && !usbState.hasPermission) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun fmtMime(trackName: String, mimeType: String): String = when {
        mimeType.contains("flac") -> "FLAC"
        mimeType.contains("wav")  -> "WAV"
        mimeType.contains("mp4") || mimeType.contains("aac") -> "AAC"
        mimeType.contains("mpeg") -> "MP3"
        mimeType.contains("alac") || trackName.endsWith(".alac", true) -> "ALAC"
        trackName.endsWith(".dsf", true) || trackName.endsWith(".dff", true) -> "DSD"
        else -> "PCM"
    }

    private fun updateOutputInfoPanel(state: dev.bleu.usbaudiopoc.player.PlayerUiState) {
        val fmt = fmtMime(state.currentTrack?.title ?: "", state.mimeType)
        binding.textSourceInfo.text = "Source: $fmt | ${state.sampleRate} Hz / ${state.sourceBitDepth}-bit"

        val outRate = state.outputSampleRate ?: state.sampleRate
        binding.textOutputInfo.text = "Output: $outRate Hz"
        binding.textModeInfo.text = if (state.routeOverride == AudioRouteOverride.BIT_PERFECT) "Mode: Bit-Perfect" else "Mode: DSP"

        val isMatched = state.sampleRate > 0 && state.sampleRate == outRate
        binding.textStatusMatch.text = if (isMatched) "Matched (No resample likely)" else "Mismatch (Resampling likely)"
        binding.textStatusMatch.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isMatched) R.color.accent_mint_dark else R.color.accent_coral
            )
        )
    }

    private fun updateRouteSelection(route: AudioRouteOverride) {
        applyCardState(
            selected = route == AudioRouteOverride.BIT_PERFECT,
            card     = binding.routeBitperfectCard,
            title    = binding.routeBitperfectTitle,
            indicator = binding.routeBitperfectIndicator
        )
        applyCardState(
            selected = route == AudioRouteOverride.DSP,
            card     = binding.routeDspCard,
            title    = binding.routeDspTitle,
            indicator = binding.routeDspIndicator
        )
    }

    private fun applyCardState(
        selected: Boolean,
        card: CardView,
        title: TextView,
        indicator: View
    ) {
        val ctx = requireContext()
        card.setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (selected) R.color.card_active_bg else R.color.card_bg)
        )
        card.cardElevation = if (selected) 6f else 2f
        indicator.background = ContextCompat.getDrawable(
            ctx,
            if (selected) R.drawable.bg_route_indicator_active else R.drawable.bg_route_indicator
        )
        title.setTextColor(
            ContextCompat.getColor(ctx, if (selected) R.color.accent_warm else R.color.text_primary_soft)
        )
    }

    private fun formatHex(v: Int) = String.format(Locale.US, "0x%04X", v)

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
