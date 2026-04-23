package dev.bleu.usbaudiopoc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.bleu.usbaudiopoc.databinding.FragmentBrowserBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import kotlinx.coroutines.launch

class FragmentBrowser : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private val vm: PlayerViewModel by activityViewModels()

    private val adapter = TrackAdapter { index -> vm.selectQueueIndex(index) }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        vm.selectFile(uri, name)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.trackList.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        vm.openFolder(uri)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentBrowserBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.trackList.layoutManager = LinearLayoutManager(requireContext())
        binding.trackList.adapter = adapter

        // View Mode Toggles
        binding.btnViewList.setOnClickListener { setViewMode(false) }
        binding.btnViewGrid.setOnClickListener { setViewMode(true) }

        binding.btnSelectFile.setOnClickListener {
            filePicker.launch(arrayOf("audio/*", "audio/flac", "audio/x-wav", "audio/mpeg"))
        }
        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(null)
        }

        // Show empty state immediately on view creation (before any state update arrives)
        if (vm.playerState.value.queue.isEmpty()) {
            binding.trackList.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.trackList.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }

        observe()
    }

    private fun setViewMode(isGrid: Boolean) {
        if (adapter.isGridView == isGrid) return
        adapter.isGridView = isGrid
        if (isGrid) {
            binding.trackList.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
            binding.btnViewGrid.apply {
                setBackgroundResource(dev.bleu.usbaudiopoc.R.drawable.bg_glass_chip)
                setColorFilter(androidx.core.content.ContextCompat.getColor(context, dev.bleu.usbaudiopoc.R.color.secondary))
            }
            binding.btnViewList.apply {
                setBackgroundResource(android.R.color.transparent)
                setColorFilter(androidx.core.content.ContextCompat.getColor(context, dev.bleu.usbaudiopoc.R.color.outline))
            }
        } else {
            binding.trackList.layoutManager = LinearLayoutManager(requireContext())
            binding.btnViewList.apply {
                setBackgroundResource(dev.bleu.usbaudiopoc.R.drawable.bg_glass_chip)
                setColorFilter(androidx.core.content.ContextCompat.getColor(context, dev.bleu.usbaudiopoc.R.color.secondary))
            }
            binding.btnViewGrid.apply {
                setBackgroundResource(android.R.color.transparent)
                setColorFilter(androidx.core.content.ContextCompat.getColor(context, dev.bleu.usbaudiopoc.R.color.outline))
            }
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectPlayerState() }
                launch { collectFolderName() }
            }
        }
    }

    private suspend fun collectPlayerState() {
        vm.playerState.collect { state ->
            val queue = state.queue
            adapter.update(queue, state.currentIndex)
            binding.loadingIndicator.visibility = View.GONE
            if (queue.isEmpty()) {
                binding.trackList.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.queueSummary.text = "No tracks loaded"
            } else {
                binding.trackList.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                val albums = queue.mapNotNull { it.album.takeIf { a -> a.isNotBlank() } }.distinct()
                binding.queueSummary.text = when {
                    albums.size == 1 -> "${queue.size} tracks · ${albums[0]}"
                    else -> "${queue.size} tracks"
                }
            }
        }
    }

    private suspend fun collectFolderName() {
        vm.loadedFolderName.collect { name ->
            if (name != null) {
                binding.folderNameLabel.text = name
                binding.folderNameLabel.setCompoundDrawablesWithIntrinsicBounds(
                    androidx.core.content.ContextCompat.getDrawable(requireContext(), dev.bleu.usbaudiopoc.R.drawable.ic_folder)?.apply { 
                        setTint(androidx.core.content.ContextCompat.getColor(requireContext(), dev.bleu.usbaudiopoc.R.color.outline)) 
                    }, null, null, null
                )
                binding.folderNameLabel.compoundDrawablePadding = 8
                binding.folderNameLabel.visibility = View.VISIBLE
            } else {
                binding.folderNameLabel.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
