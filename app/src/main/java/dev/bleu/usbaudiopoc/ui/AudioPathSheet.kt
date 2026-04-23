package dev.bleu.usbaudiopoc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.bleu.usbaudiopoc.R
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.databinding.BottomSheetAudioPathBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel

class AudioPathSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAudioPathBinding? = null
    private val binding get() = _binding!!
    private val vm: PlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAudioPathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind(vm.playerState.value)
    }

    private fun bind(s: dev.bleu.usbaudiopoc.player.PlayerUiState) {
        val isBitPerfect = s.routeOverride == AudioRouteOverride.BIT_PERFECT
        val ctx = requireContext()

        fun color(id: Int) = ContextCompat.getColor(ctx, id)
        fun tint(v: View, id: Int) {
            v.backgroundTintList = android.content.res.ColorStateList.valueOf(color(id))
        }

        // Mode badge — warm for BIT-PERFECT, lavender for DSP
        if (isBitPerfect) {
            tint(binding.sheetModeBadge, R.color.accent_warm_xlight)
            binding.sheetModeText.text = "BIT-PERFECT"
            binding.sheetModeText.setTextColor(color(R.color.accent_warm))
        } else {
            tint(binding.sheetModeBadge, R.color.accent_lavender_xlight)
            binding.sheetModeText.text = "DSP ACTIVE"
            binding.sheetModeText.setTextColor(color(R.color.accent_lavender))
        }

        // DSP node visibility
        binding.sheetNodeDsp.visibility = if (isBitPerfect) View.GONE else View.VISIBLE

        // Format details
        binding.sheetFormat.text = fmtMime(s.mimeType).ifBlank { "—" }
        binding.sheetSampleRate.text = if (s.sampleRate > 0) fmtRate(s.sampleRate) else "—"
        binding.sheetBitDepth.text = if (s.sourceBitDepth > 0) "${s.sourceBitDepth}-bit" else "—"

        // Output status
        val isMatched = s.sampleRate > 0 &&
                (s.outputSampleRate == null || s.outputSampleRate == s.sampleRate)

        binding.sheetOutputStatus.text =
            if (isMatched) "Matched — No resample likely" else "Mismatch — Resampling likely"
        binding.sheetOutputStatus.setTextColor(
            color(if (isMatched) R.color.accent_mint_dark else R.color.accent_coral)
        )
        binding.sheetStatusBadgeText.text = if (isMatched) "MATCHED" else "MISMATCH"
        binding.sheetStatusBadgeText.setTextColor(
            color(if (isMatched) R.color.accent_mint_dark else R.color.accent_coral)
        )
        tint(binding.sheetStatusBadge,
            if (isMatched) R.color.accent_mint_xlight else R.color.accent_coral_light)
    }

    private fun fmtRate(hz: Int) =
        if (hz % 1000 == 0) "${hz / 1000}kHz" else "%.1fkHz".format(hz / 1000.0)

    private fun fmtMime(m: String) = when {
        m.contains("flac") -> "FLAC"
        m.contains("wav")  -> "WAV"
        m.contains("mpeg") || m.contains("mp3") -> "MP3"
        m.contains("ogg")  -> "OGG"
        m.contains("aac") || m.contains("mp4")  -> "AAC"
        m.isBlank()        -> ""
        else               -> m.substringAfterLast('/').uppercase().take(6)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        const val TAG = "AudioPathSheet"
    }
}
