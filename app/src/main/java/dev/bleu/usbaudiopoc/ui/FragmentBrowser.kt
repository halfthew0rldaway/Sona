package dev.bleu.usbaudiopoc.ui

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.bleu.usbaudiopoc.databinding.FragmentBrowserBinding
import dev.bleu.usbaudiopoc.player.PlayerViewModel
import kotlinx.coroutines.launch

class FragmentBrowser : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by activityViewModels()

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::handleSelectedDocument)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSelectManual.setOnClickListener { openDocumentPicker() }
        binding.selectedFileCard.setOnClickListener { openDocumentPicker() }
        collectUiState()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { playerState ->
                    val hasSelection = playerState.selectedFileLabel != "none selected"
                    binding.selectedFileStatus.text = if (hasSelection) {
                        "READY FOR PLAYBACK"
                    } else {
                        "WAITING FOR IMPORT"
                    }
                    binding.selectedFileLabel.text = if (hasSelection) {
                        playerState.selectedFileLabel
                    } else {
                        "No WAV master imported"
                    }
                    binding.selectedFileMeta.text = if (hasSelection) {
                        playerState.statusMessage
                    } else {
                        "Tap this panel or use the import action above to choose a WAV file."
                    }
                }
            }
        }
    }

    private fun openDocumentPicker() {
        documentPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/vnd.wave"))
    }

    private fun handleSelectedDocument(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val displayName = resolveDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
        viewModel.selectFile(uri, displayName)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val cursor: Cursor = requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return null
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return it.getString(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
