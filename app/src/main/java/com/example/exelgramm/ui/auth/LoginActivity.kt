package com.example.exelgramm.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.MainActivity
import com.example.exelgramm.R
import com.example.exelgramm.databinding.ActivityLoginBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val session = (application as ExelgrammApp).sessionStore.session.first()
            if (session.isLoggedIn) {
                openMain()
                return@launch
            }
        }

        binding.loginButton.setOnClickListener {
            val name = binding.displayNameInput.text?.toString().orEmpty().trim()
            if (name.isBlank()) {
                Toast.makeText(this, R.string.error_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                (application as ExelgrammApp).sessionStore.saveDisplayName(name)
                openMain()
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
