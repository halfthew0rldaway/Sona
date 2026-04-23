package dev.bleu.usbaudiopoc.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.bleu.usbaudiopoc.R
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.databinding.FragmentPlayingBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FragmentPlaying : Fragment() {

    private var _binding: FragmentPlayingBinding? = null
    private val binding get() = _binding!!
    private val vm: PlayerViewModel by activityViewModels()

    private var lastArtKey: Int = -1
    private var seekDragging = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentPlayingBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Marquee on title
        binding.trackTitle.isSelected = true

        // Playback control buttons
        binding.btnPlayPause.setOnClickListener {
            if (vm.playerState.value.isPlaying) vm.pause() else vm.play()
        }
        binding.btnPrev.setOnClickListener { vm.skipPrev() }
        binding.btnNext.setOnClickListener { vm.skipNext() }

        // Seek bar
        binding.progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { seekDragging = true }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                seekDragging = false
                val st = vm.playerState.value
                if (st.durationMs > 0) {
                    val targetMs = (bar.progress.toLong() * st.durationMs) / bar.max
                    vm.seekTo(targetMs)
                }
            }
        })

        // Audio path bottom sheet
        binding.btnAudioPath.setOnClickListener {
            AudioPathSheet().show(childFragmentManager, AudioPathSheet.TAG)
        }

        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectPlayer() }
            }
        }
    }

    private suspend fun collectPlayer() {
        vm.playerState.collect { s ->
            val track = s.currentTrack
            val isBitPerfect = s.routeOverride == AudioRouteOverride.BIT_PERFECT

            // ── Album art (decode only on track change) ──
            if (s.currentIndex != lastArtKey) {
                lastArtKey = s.currentIndex
                val artBytes = s.artBytes
                if (artBytes != null) {
                    val bmp = withContext(Dispatchers.Default) {
                        runCatching {
                            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        binding.coverArt.setImageBitmap(bmp)
                        binding.coverArt.visibility = View.VISIBLE
                        binding.coverPlaceholder.visibility = View.GONE
                        setBlurredBackground(bmp)
                    } else {
                        showPlaceholder()
                    }
                } else {
                    showPlaceholder()
                }
            }

            // ── Track info ──
            binding.trackTitle.text = track?.title ?: "No track loaded"
            binding.trackArtist.text = when {
                !track?.artist.isNullOrBlank() -> track!!.artist
                !track?.album.isNullOrBlank()  -> track!!.album
                else -> ""
            }

            // ── Mode pill ──
            val warmXLight = ContextCompat.getColor(requireContext(), R.color.accent_warm_xlight)
            val warmOrange  = ContextCompat.getColor(requireContext(), R.color.accent_warm)
            val lavXLight   = ContextCompat.getColor(requireContext(), R.color.accent_lavender_xlight)
            val lavender    = ContextCompat.getColor(requireContext(), R.color.accent_lavender)

            if (isBitPerfect) {
                binding.modePillContainer.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(warmXLight)
                binding.modePillText.text = "BIT-PERFECT"
                binding.modePillText.setTextColor(warmOrange)
                binding.modePillDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(warmOrange)
            } else {
                binding.modePillContainer.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(lavXLight)
                binding.modePillText.text = "DSP ACTIVE"
                binding.modePillText.setTextColor(lavender)
                binding.modePillDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(lavender)
            }

            // ── HI-RES badge ──
            binding.badgeBitPerfect.visibility = if (isBitPerfect) View.VISIBLE else View.GONE

            // ── Play/Pause icon ──
            binding.btnPlayPause.setImageResource(
                if (s.isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
            )
            binding.btnPrev.alpha = if (s.hasPrev) 1f else 0.35f
            binding.btnNext.alpha = if (s.hasNext) 1f else 0.35f

            // ── Queue counter removed ──

            // ── Progress / time ──
            if (s.durationMs > 0) {
                binding.timeElapsed.text = fmtTime(s.positionMs)
                binding.timeDuration.text = fmtTime(s.durationMs)
                if (!seekDragging) {
                    val prog = ((s.positionMs.toFloat() / s.durationMs) * binding.progressBar.max).toInt()
                    binding.progressBar.progress = prog.coerceIn(0, binding.progressBar.max)
                }
            } else {
                binding.timeElapsed.text = "0:00"
                binding.timeDuration.text = "--:--"
                if (!seekDragging) binding.progressBar.progress = 0
            }
        }
    }

    private fun showPlaceholder() {
        binding.coverArt.setImageDrawable(null)
        binding.coverArt.visibility = View.GONE
        binding.coverPlaceholder.visibility = View.VISIBLE
        binding.bgArtBlur.setImageDrawable(null)
    }

    private fun setBlurredBackground(bmp: Bitmap) {
        binding.bgArtBlur.setImageBitmap(bmp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.bgArtBlur.setRenderEffect(
                RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
