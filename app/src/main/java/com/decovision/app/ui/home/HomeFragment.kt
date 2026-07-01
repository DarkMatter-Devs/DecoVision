package com.decovision.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.decovision.app.R
import com.decovision.app.adapter.DesignAdapter
import com.decovision.app.databinding.FragmentHomeBinding
import com.decovision.app.util.UiState
import com.decovision.app.util.hide
import com.decovision.app.util.show
import dagger.hilt.android.AndroidEntryPoint

/**
 * Home screen displaying action cards and a recent-designs preview strip.
 * Observes [HomeViewModel.recentDesigns] using [viewLifecycleOwner].
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private val recentAdapter = DesignAdapter(
        onItemClick = { /* FUTURE: navigate to design detail screen */ },
        onLongClick = { /* No long-press action on home preview strip */ }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    /** Configures the horizontal RecyclerView for recent designs. */
    private fun setupRecyclerView() {
        binding.rvRecentDesigns.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = recentAdapter
        }
    }

    /** Wires the action card click listeners to navigate to the correct screens. */
    private fun setupClickListeners() {
        binding.cardStartAr.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_ar)
        }
        binding.cardMyDesigns.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_my_designs)
        }
        binding.tvSeeAll.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_my_designs)
        }
    }

    /** Observes [HomeViewModel.recentDesigns] and updates the UI accordingly. */
    private fun observeViewModel() {
        viewModel.recentDesigns.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressRecent.show()
                    binding.rvRecentDesigns.hide()
                    binding.tvNoRecent.hide()
                }
                is UiState.Success -> {
                    binding.progressRecent.hide()
                    if (state.data.isEmpty()) {
                        binding.rvRecentDesigns.hide()
                        binding.tvNoRecent.show()
                    } else {
                        binding.rvRecentDesigns.show()
                        binding.tvNoRecent.hide()
                        recentAdapter.submitList(state.data)
                    }
                }
                is UiState.Error -> {
                    binding.progressRecent.hide()
                    binding.rvRecentDesigns.hide()
                    binding.tvNoRecent.show()
                    binding.tvNoRecent.text = state.message
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
