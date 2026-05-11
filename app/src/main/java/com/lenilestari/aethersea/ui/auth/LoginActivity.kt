package com.lenilestari.aethersea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.auth.AuthManager
import com.lenilestari.aethersea.data.model.User
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.databinding.ActivityLoginBinding
import com.lenilestari.aethersea.ui.home.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        if (auth.currentUser != null) { goToMain(); return }

        binding.btnMasuk.setOnClickListener { signInWithEmail() }

        binding.btnGoogle.setOnClickListener {
            setLoading(true)
            startActivityForResult(authManager.getSignInIntent(), RC_SIGN_IN)
        }

        binding.tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun signInWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (email.isEmpty()) { binding.etEmail.error = "Email wajib diisi"; return }
        if (password.isEmpty()) { binding.etPassword.error = "Kata sandi wajib diisi"; return }

        setLoading(true)
        lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                goToMain()
            } catch (e: Exception) {
                setLoading(false)
                val msg = when {
                    e.message?.contains("no user record") == true -> "Email tidak terdaftar"
                    e.message?.contains("password is invalid") == true -> "Kata sandi salah"
                    e.message?.contains("badly formatted") == true -> "Format email tidak valid"
                    else -> "Login gagal. Periksa email dan kata sandi."
                }
                Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            lifecycleScope.launch {
                val result = authManager.handleSignInResult(data)
                if (result.isSuccess) {
                    val user = result.getOrNull()!!
                    val userRepo = UserRepository(user.uid)
                    val existing = userRepo.getProfile()
                    if (existing == null) {
                        userRepo.createOrUpdateProfile(User(
                            uid = user.uid,
                            displayName = user.displayName ?: "",
                            email = user.email ?: "",
                            photoUrl = user.photoUrl?.toString() ?: "",
                            createdAt = Timestamp.now()
                        ))
                    }
                    goToMain()
                } else {
                    setLoading(false)
                    Toast.makeText(this@LoginActivity, "Login gagal. Coba lagi.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnMasuk.isClickable = !loading
        binding.btnGoogle.isClickable = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
