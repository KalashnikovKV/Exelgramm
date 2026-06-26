package com.example.exelgramm

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.example.exelgramm.databinding.ActivityMainBinding
import com.example.exelgramm.di.AppPrefsEntryPoint
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val bottomNavDestinations = setOf(
        R.id.nav_chat,
        R.id.nav_profile,
        R.id.nav_settings,
        R.id.nav_participants,
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var navBg: Int = 0
    private var chatBg: Int = 0
    private var white: Int = 0
    private var iconOnChatBg: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val appPrefs = EntryPointAccessors.fromApplication(
            applicationContext,
            AppPrefsEntryPoint::class.java,
        ).appPrefsStore()
        AppCompatDelegate.setDefaultNightMode(appPrefs.themeMode)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navBg = ContextCompat.getColor(this, R.color.tg_nav_bg)
        chatBg = ContextCompat.getColor(this, R.color.tg_chat_bg)
        white = ContextCompat.getColor(this, R.color.white)
        iconOnChatBg = ContextCompat.getColor(this, R.color.tg_text_primary)

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

        val topLevelDestinations = bottomNavDestinations + R.id.loginFragment
        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)
        NavigationUI.setupWithNavController(binding.bottomNav, navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateToolbarForDestination(destination.id)
            hideKeyboard()
        }
        updateToolbarForDestination(navController.currentDestination?.id ?: R.id.loginFragment)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        when (navController.currentDestination?.id) {
            R.id.loginFragment -> {
                menuInflater.inflate(R.menu.toolbar_login, menu)
                menu.findItem(R.id.action_faq)?.icon?.setTint(iconOnChatBg)
            }
            R.id.participantDetailFragment,
            R.id.messageDetailFragment,
            -> {
                menuInflater.inflate(R.menu.toolbar_detail, menu)
                menu.findItem(R.id.action_close_to_chat)?.icon?.setTint(white)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_close_to_chat -> {
                closeDetailToOriginTab()
                true
            }
            R.id.action_faq -> {
                navController.navigate(R.id.action_login_to_faq)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun updateToolbarForDestination(destinationId: Int) {
        val isAuthFlow = destinationId == R.id.loginFragment ||
            destinationId == R.id.faqFragment

        binding.statusBarBackground.isVisible = true
        binding.toolbar.isVisible = true
        binding.bottomNav.isVisible = !isAuthFlow

        val toolbarInset = resources.getDimensionPixelSize(R.dimen.toolbar_horizontal_inset)
        if (destinationId == R.id.loginFragment) {
            binding.statusBarBackground.setBackgroundColor(chatBg)
            binding.toolbar.setBackgroundColor(chatBg)
            supportActionBar?.setDisplayShowTitleEnabled(false)
            binding.toolbar.title = ""
            binding.toolbar.navigationIcon = null
            binding.toolbar.setContentInsetsRelative(0, toolbarInset)
        } else {
            binding.statusBarBackground.setBackgroundColor(navBg)
            binding.toolbar.setBackgroundColor(navBg)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            binding.toolbar.navigationIcon?.setTint(white)
            binding.toolbar.setContentInsetsRelative(toolbarInset, toolbarInset)
        }

        invalidateOptionsMenu()
    }

    private fun closeDetailToOriginTab() {
        while (navController.currentDestination?.id !in bottomNavDestinations) {
            if (!navController.popBackStack()) break
        }
        syncBottomNavSelection()
    }

    private fun syncBottomNavSelection() {
        val destinationId = navController.currentDestination?.id ?: return
        if (destinationId !in bottomNavDestinations) return
        binding.bottomNav.menu.findItem(destinationId)?.isChecked = true
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()

    private fun hideKeyboard() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
    }

    private fun applyBrandChrome() {
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
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.statusBarBackground.updateLayoutParams { height = statusBar.top }
            // navBar padding for gesture/button nav only — not the keyboard
            binding.bottomNav.updatePadding(bottom = navBar.bottom)
            // Shift root up by keyboard height so the weighted fragment shrinks and input stays visible
            binding.root.updatePadding(bottom = ime.bottom)
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
