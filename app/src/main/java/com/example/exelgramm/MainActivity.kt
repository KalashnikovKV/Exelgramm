package com.example.exelgramm

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import com.example.exelgramm.databinding.ActivityMainBinding
import com.example.exelgramm.ui.chat.ChatFragment
import com.example.exelgramm.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyBrandChrome()
        styleBottomNav()
        applyWindowInsets()

        if (savedInstanceState == null) {
            showChat()
            binding.bottomNav.selectedItemId = R.id.nav_chat
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> {
                    showChat()
                    true
                }
                R.id.nav_profile -> {
                    showProfile()
                    true
                }
                else -> false
            }
        }
    }

    private fun applyBrandChrome() {
        val primary = ContextCompat.getColor(this, R.color.tg_primary)
        binding.toolbar.setBackgroundColor(primary)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.statusBarBackground.updateLayoutParams {
                height = statusBar.top
            }
            binding.bottomNav.updatePadding(bottom = navBar.bottom)
            insets
        }
    }

    private fun styleBottomNav() {
        val primary = ContextCompat.getColor(this, R.color.tg_primary)
        val itemColors = ContextCompat.getColorStateList(this, R.color.bottom_nav_item)
        binding.bottomNav.apply {
            setBackgroundColor(primary)
            backgroundTintList = ColorStateList.valueOf(primary)
            itemIconTintList = itemColors
            itemTextColor = itemColors
            disableActiveIndicator(this)
        }
    }

    private fun disableActiveIndicator(bottomNav: BottomNavigationView) {
        bottomNav.isItemActiveIndicatorEnabled = false
        bottomNav.itemActiveIndicatorWidth = 0
        bottomNav.itemActiveIndicatorHeight = 0
        bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.transparent),
        )
    }

    private fun showChat() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, ChatFragment())
        }
    }

    private fun showProfile() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, ProfileFragment())
        }
    }
}
