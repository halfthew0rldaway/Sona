package dev.bleu.usbaudiopoc.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.bleu.usbaudiopoc.R
import dev.bleu.usbaudiopoc.player.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackAdapter(
    private var tracks: List<TrackItem> = emptyList(),
    private var selectedIndex: Int = -1,
    private val onTrackClick: (Int) -> Unit,
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    var isGridView: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val artCache = LruCache<String, Bitmap>(50) // Cache up to 50 thumbnails

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.track_number)
        val art: ImageView = v.findViewById(R.id.item_art)
        val title: TextView = v.findViewById(R.id.item_title)
        val artist: TextView = v.findViewById(R.id.item_artist)
        val duration: TextView = v.findViewById(R.id.item_duration)
        var artJob: Job? = null
        init { v.setOnClickListener { onTrackClick(pos) } }
        val pos get() = adapterPosition
    }

    override fun getItemViewType(position: Int): Int = if (isGridView) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layoutId = if (viewType == 1) R.layout.item_track_grid else R.layout.item_track
        return VH(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val t = tracks[pos]
        val ctx = h.itemView.context
        val active = pos == selectedIndex
        h.title.text = t.title
        h.artist.text = if (t.artist.isNotBlank()) t.artist else if (t.album.isNotBlank()) t.album else t.fileName
        h.duration.text = fmtTime(t.durationMs)
        val accent = ContextCompat.getColor(ctx, R.color.accent_warm)
        val normal = ContextCompat.getColor(ctx, R.color.text_primary_soft)
        val muted = ContextCompat.getColor(ctx, R.color.outline_dark)
        h.title.setTextColor(if (active) accent else normal)
        h.number.setTextColor(if (active) accent else muted)
        h.itemView.alpha = if (active) 1f else 0.85f

        h.artJob?.cancel()
        h.art.setImageDrawable(null)
        if (t.artBytes != null) {
            h.number.visibility = View.GONE
            val cacheKey = t.uri.toString()
            val cached = artCache.get(cacheKey)
            if (cached != null) {
                h.art.setImageBitmap(cached)
            } else {
                h.artJob = scope.launch {
                    val bmp = withContext(Dispatchers.Default) {
                        try {
                            val opts = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(t.artBytes, 0, t.artBytes.size, opts)
                            opts.inSampleSize = calculateInSampleSize(opts, 120, 120)
                            opts.inJustDecodeBounds = false
                            BitmapFactory.decodeByteArray(t.artBytes, 0, t.artBytes.size, opts)
                        } catch (e: Exception) { null }
                    }
                    if (bmp != null) {
                        artCache.put(cacheKey, bmp)
                        h.art.setImageBitmap(bmp)
                    }
                }
            }
        } else {
            h.number.visibility = View.VISIBLE
            
            // Extract extension from filename (e.g. "FLAC", "WAV")
            val ext = t.fileName.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) {
                h.number.text = ext
                h.number.textSize = 10f // Kept small but bold for flag style
                h.number.setTypeface(h.number.typeface, android.graphics.Typeface.BOLD)
                h.number.setTextColor(if (active) accent else ContextCompat.getColor(ctx, R.color.text_secondary_soft))
            } else {
                h.number.text = (pos + 1).toString()
                h.number.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    override fun getItemCount() = tracks.size

    fun update(newTracks: List<TrackItem>, newIndex: Int) {
        tracks = newTracks
        selectedIndex = newIndex
        notifyDataSetChanged()
    }

    private fun fmtTime(ms: Long): String {
        if (ms <= 0) return "--:--"
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
