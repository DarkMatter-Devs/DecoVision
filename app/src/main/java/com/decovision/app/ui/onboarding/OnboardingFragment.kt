package com.decovision.app.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.decovision.app.R
import com.decovision.app.databinding.FragmentOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen onboarding screen shown only on first launch.
 * Automatically skips to [HomeFragment] if onboarding was previously completed.
 *
 * No ViewModel required — this screen has no async data, only SharedPreferences logic.
 */
@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("decovision_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If onboarding was already completed, skip directly to Home
        if (prefs.getBoolean("onboarding_complete", false)) {
            findNavController().navigate(R.id.action_onboarding_to_home)
            return
        }

        binding.btnGetStarted.setOnClickListener {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            findNavController().navigate(R.id.action_onboarding_to_home)
        }

        binding.btnSignIn.setOnClickListener {
            // FUTURE: Navigate to sign-in screen once Firebase Auth is integrated.
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            findNavController().navigate(R.id.action_onboarding_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
