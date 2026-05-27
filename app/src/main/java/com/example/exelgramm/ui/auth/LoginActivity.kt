package com.example.exelgramm.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.MainActivity
import com.example.exelgramm.R
import com.example.exelgramm.databinding.ActivityLoginBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val app get() = application as ExelgrammApp

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory(app.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch {
            val session = app.sessionStore.session.first()
            if (session.isLoggedIn) {
                openMain()
                return@launch
            }
            setupUi(isRegistered = session.isRegistered)
        }
    }

    private fun setupUi(isRegistered: Boolean) {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pad = resources.getDimensionPixelSize(R.dimen.login_content_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(pad, bars.top + pad, pad, bars.bottom + pad)
            insets
        }

        if (!isRegistered) {
            binding.loginTitle.setText(R.string.login_register_title)
            binding.loginSubtitle.setText(R.string.login_register_subtitle)
            binding.loginButton.setText(R.string.login_button_register)
        }

        binding.loginButton.setOnClickListener { submit() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is LoginEffect.NavigateToMain -> openMain()
                        is LoginEffect.ShowError ->
                            Toast.makeText(
                                this@LoginActivity,
                                effect.message.resolve(this@LoginActivity),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }
    }

    private fun submit() {
        viewModel.submit(
            username = binding.usernameInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
        )
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
