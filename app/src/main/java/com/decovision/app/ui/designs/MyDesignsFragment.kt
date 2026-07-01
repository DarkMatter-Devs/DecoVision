package com.decovision.app.ui.designs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.decovision.app.R
import com.decovision.app.adapter.DesignAdapter
import com.decovision.app.data.model.Design
import com.decovision.app.databinding.FragmentMyDesignsBinding
import com.decovision.app.util.UiState
import com.decovision.app.util.hide
import com.decovision.app.util.show
import com.decovision.app.util.showSnackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MyDesignsFragment : Fragment() {

    private var _binding: FragmentMyDesignsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MyDesignsViewModel by viewModels()
    private var previewDialog: Dialog? = null

    private val designAdapter = DesignAdapter(
        onItemClick = { design -> showDesignPreview(design) },
        onLongClick  = { design -> confirmDelete(design) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyDesignsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvDesigns.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = designAdapter
        }
        observeViewModel()
    }

    private fun showDesignPreview(design: Design) {
        previewDialog?.dismiss()

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        // Build content view
        val root = FrameLayout(requireContext())
        root.setBackgroundColor(Color.BLACK)

        val iv = ImageView(requireContext())
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(iv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val tvName = TextView(requireContext())
        tvName.text = design.name
        tvName.setTextColor(Color.WHITE)
        tvName.textSize = 18f
        tvName.setPadding(32, 48, 32, 16)
        tvName.setBackgroundColor(Color.argb(200, 0, 0, 0))
        root.addView(tvName, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        val btnClose = TextView(requireContext())
        btnClose.text = "✕  CLOSE"
        btnClose.setTextColor(Color.WHITE)
        btnClose.textSize = 16f
        btnClose.gravity = Gravity.CENTER
        btnClose.setPadding(60, 30, 60, 30)
        btnClose.setBackgroundColor(Color.argb(220, 30, 30, 30))
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.bottomMargin = 80
        root.addView(btnClose, lp)

        dialog.setContentView(root)

        // Window full screen — EXACT correct way
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setGravity(Gravity.CENTER)
        }

        // Load image AFTER setContentView and window setup
        val file = File(design.thumbnailPath)
        val loadFile = if (file.exists() && file.length() > 0) file
            else File(requireContext().filesDir, "designs/${file.name}")
                .takeIf { it.exists() && it.length() > 0 }

        if (loadFile == null) {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        iv.setOnClickListener { dialog.dismiss() }
        root.setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Force full screen AFTER show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        previewDialog = dialog

        // Load image AFTER dialog is shown and laid out
        // iv.post ensures view is measured before Coil targets it
        iv.post {
            val w = iv.width.takeIf { it > 0 } ?: 1080
            val h = iv.height.takeIf { it > 0 } ?: 1920
            if (loadFile != null) {
                iv.load(loadFile) {
                    size(w, h)
                    crossfade(false)
                    allowHardware(false)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.designs.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressDesigns.show()
                    binding.rvDesigns.hide()
                    binding.layoutEmptyState.hide()
                }
                is UiState.Success -> {
                    binding.progressDesigns.hide()
                    if (state.data.isEmpty()) {
                        binding.rvDesigns.hide()
                        binding.layoutEmptyState.show()
                    } else {
                        binding.layoutEmptyState.hide()
                        binding.rvDesigns.show()
                        designAdapter.submitList(state.data)
                    }
                }
                is UiState.Error -> {
                    binding.progressDesigns.hide()
                    binding.layoutEmptyState.show()
                    binding.root.showSnackbar(state.message)
                }
            }
        }
        viewModel.deleteState.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Error) binding.root.showSnackbar(state.message)
        }
    }

    private fun confirmDelete(design: Design) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_design_title)
            .setMessage(getString(R.string.delete_design_message, design.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteDesign(design) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewDialog?.dismiss()
        previewDialog = null
        _binding = null
    }
}
