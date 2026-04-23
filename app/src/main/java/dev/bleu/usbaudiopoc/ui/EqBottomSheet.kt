package dev.bleu.usbaudiopoc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.bleu.usbaudiopoc.R
import dev.bleu.usbaudiopoc.player.PlayerViewModel

class EqBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: PlayerViewModel by activityViewModels()

    private val bands = arrayOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_eq, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<LinearLayout>(R.id.eq_sliders_container)
        val ctx = requireContext()

        for (i in bands.indices) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val label = TextView(ctx).apply {
                text = "${bands[i]}"
                textSize = 12f
                typeface = resources.getFont(R.font.inter)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary_soft))
                layoutParams = LinearLayout.LayoutParams(90, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val slider = SeekBar(ctx).apply {
                max = 200
                progress = 100 // 0 dB
                progressTintList = ContextCompat.getColorStateList(ctx, R.color.accent_lavender)
                thumbTintList = ContextCompat.getColorStateList(ctx, R.color.accent_lavender)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if(fromUser) viewModel.setEqGain(i, p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })

            row.addView(label)
            row.addView(slider)
            container.addView(row)
        }
    }

    companion object {
        const val TAG = "EqBottomSheet"
    }
}
