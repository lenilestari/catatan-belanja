package com.lenilestari.aethersea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.lenilestari.aethersea.data.model.User
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.databinding.ActivityRegisterBinding
import com.lenilestari.aethersea.ui.home.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvMasuk.setOnClickListener { finish() }
        binding.btnDaftar.setOnClickListener { register() }
    }

    private fun register() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (name.isEmpty()) { binding.etName.error = "Nama wajib diisi"; return }
        if (email.isEmpty()) { binding.etEmail.error = "Email wajib diisi"; return }
        if (password.length < 6) { binding.etPassword.error = "Minimal 6 karakter"; return }
        if (password != confirmPassword) { binding.etConfirmPassword.error = "Kata sandi tidak cocok"; return }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val fbUser = result.user!!

                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                fbUser.updateProfile(profileUpdate).await()

                UserRepository(fbUser.uid).createOrUpdateProfile(User(
                    uid = fbUser.uid,
                    displayName = name,
                    email = email,
                    photoUrl = "",
                    createdAt = Timestamp.now()
                ))

                startActivity(Intent(this@RegisterActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } catch (e: Exception) {
                setLoading(false)
                val msg = when {
                    e.message?.contains("email address is already in use") == true -> "Email sudah terdaftar"
                    e.message?.contains("badly formatted") == true -> "Format email tidak valid"
                    else -> "Gagal membuat akun. Coba lagi."
                }
                Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.pbDaftar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvBtnDaftar.text = if (loading) "" else "Buat Akun"
        binding.btnDaftar.isClickable = !loading
    }
}
