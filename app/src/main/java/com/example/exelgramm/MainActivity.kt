package com.example.exelgramm

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.example.exelgramm.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyBrandChrome()
        styleBottomNav()
        applyWindowInsets()
        setupNavigation()
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val topLevelDestinations = setOf(R.id.nav_chat, R.id.nav_profile, R.id.nav_settings, R.id.nav_participants)
        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)
        NavigationUI.setupWithNavController(binding.bottomNav, navController)

        val white = ContextCompat.getColor(this, R.color.white)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLoginScreen = destination.id == R.id.loginFragment
            binding.statusBarBackground.isVisible = !isLoginScreen
            binding.toolbar.isVisible = !isLoginScreen
            binding.bottomNav.isVisible = !isLoginScreen

            if (!isLoginScreen) {
                binding.toolbar.navigationIcon?.setTint(white)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()

    private fun applyBrandChrome() {
        val navBg = ContextCompat.getColor(this, R.color.tg_nav_bg)
        binding.toolbar.setBackgroundColor(navBg)
        binding.statusBarBackground.setBackgroundColor(navBg)
        binding.toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.statusBarBackground.updateLayoutParams { height = statusBar.top }
            binding.bottomNav.updatePadding(bottom = navBar.bottom)
            insets
        }
    }

    private fun styleBottomNav() {
        val navBg = ContextCompat.getColor(this, R.color.tg_nav_bg)
        val itemColors = ContextCompat.getColorStateList(this, R.color.bottom_nav_item)
        binding.bottomNav.apply {
            setBackgroundColor(navBg)
            backgroundTintList = ColorStateList.valueOf(navBg)
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
}
