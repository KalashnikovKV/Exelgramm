package com.example.exelgramm.ui.profile

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutButton.setOnClickListener { logout() }

        binding.darkThemeSwitch.isChecked = viewModel.isDarkTheme
        binding.darkThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTheme(isChecked)
        }

        binding.languageValue.text = Locale.getDefault().displayLanguage
            .replaceFirstChar { it.uppercaseChar() }

        val pInfo = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(pInfo).toInt()
        binding.versionValue.text = getString(
            R.string.profile_version_format,
            pInfo.versionName,
            versionCode,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.username.collect { username ->
                        binding.usernameValue.text = username
                        binding.avatarInitials.text = username
                            .trim()
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            .orEmpty()
                        setAvatarColor(username)
                    }
                }
                launch {
                    viewModel.createdAt.collect { timestamp ->
                        binding.registeredAtValue.text = if (timestamp > 0L) {
                            val date = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                                .format(Date(timestamp))
                            getString(R.string.profile_registered_label) + ": " + date
                        } else {
                            getString(R.string.profile_registered_unknown)
                        }
                    }
                }
            }
        }
    }

    private fun setAvatarColor(username: String) {
        val index = if (username.isEmpty()) 0
                    else abs(username.hashCode()) % AVATAR_COLORS.size
        val drawable = binding.avatarBackground.background.mutate() as? GradientDrawable
            ?: GradientDrawable().also { it.shape = GradientDrawable.OVAL }
        drawable.setColor(AVATAR_COLORS[index])
        binding.avatarBackground.background = drawable
    }

    private fun logout() {
        viewModel.logout()
        findNavController().navigate(R.id.action_logout)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val AVATAR_COLORS = intArrayOf(
            0xFF3390EC.toInt(), // Telegram blue
            0xFFE17076.toInt(), // Red
            0xFFFF9472.toInt(), // Orange
            0xFFA0DE7E.toInt(), // Green
            0xFF72D5FD.toInt(), // Sky blue
            0xFFDB9FE5.toInt(), // Purple
            0xFFE6B450.toInt(), // Yellow
            0xFF5BCEFA.toInt(), // Cyan
        )
    }
}
