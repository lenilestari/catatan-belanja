package com.lenilestari.aethersea.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.auth.AuthManager
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivityProfileBinding
import com.lenilestari.aethersea.ui.auth.LoginActivity
import com.lenilestari.aethersea.ui.budget.BudgetSourcesActivity
import com.lenilestari.aethersea.ui.home.MainActivity
import com.lenilestari.aethersea.ui.wishlist.WishlistActivity
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
import com.lenilestari.aethersea.util.disableActiveIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var authManager: AuthManager
    private var userId: String = ""
    private var uploadJob: Job? = null
    private var loadProfileJob: Job? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        userId = authManager.currentUser?.uid ?: run { goToLogin(); return }

        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.layoutAvatar.setOnClickListener { pickPhoto.launch("image/*") }
        binding.rowEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.rowBudgetSettings.setOnClickListener {
            startActivity(Intent(this, BudgetSourcesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.rowHelp.setOnClickListener { showHelpDialog() }

        setupBottomNav()
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_profile
    }

    override fun onDestroy() {
        super.onDestroy()
        uploadJob?.cancel()
        loadProfileJob?.cancel()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Yakin ingin keluar dari akun?")
            .setPositiveButton("Keluar") { _, _ ->
                lifecycleScope.launch {
                    authManager.signOut()
                    goToLogin()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditProfileDialog() {
        // Material3 TextInputLayout inside a padded FrameLayout
        val container = FrameLayout(this)
        val margin = (20 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(
            this, null,
            com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox
        ).apply {
            hint = "Nama tampilan"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(margin, margin / 2, margin, 0) }
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(binding.tvDisplayName.text)
            setSingleLine(true)
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Profil")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    binding.tvDisplayName.text = newName
                    binding.tvAvatarInitial.text = newName.firstOrNull()?.uppercase() ?: "?"
                    lifecycleScope.launch { UserRepository(userId).updateDisplayName(newName) }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Bantuan")
            .setMessage(
                "Catatan Belanja membantu kamu mencatat pengeluaran harian dengan mudah " +
                "menggunakan input suara atau teks.\n\n" +
                "• Tekan mikrofon untuk input suara\n" +
                "• Ketik manual untuk input cepat\n" +
                "• Lihat histori di menu Riwayat"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadProfile() {
        loadProfileJob?.cancel()
        loadProfileJob = lifecycleScope.launch {
            val fbUser = authManager.currentUser
            val user = UserRepository(userId).getProfile()

            val displayName = user?.displayName ?: fbUser?.displayName ?: "Pengguna"
            val email = user?.email ?: fbUser?.email ?: ""

            binding.tvDisplayName.text = displayName
            binding.tvEmail.text = email
            binding.tvAvatarInitial.text = displayName.firstOrNull()?.uppercase() ?: "?"

            val photoUrl = user?.photoUrl?.takeIf { it.isNotEmpty() }
                ?: fbUser?.photoUrl?.toString()
            showAvatar(photoUrl)

            val monthStart = DateUtils.startOfMonth()
            val sessions = SessionRepository(userId).getSessionsInRange(monthStart, Timestamp.now())
            binding.tvStatSesi.text = sessions.size.toString()
            binding.tvStatSpending.text = CurrencyUtils.format(sessions.sumOf { it.grandTotal })

            val wishlists = WishlistRepository(userId).getAll()
            binding.tvStatWishlist.text = wishlists.size.toString()
        }
    }

    private fun showAvatar(photoUrl: String?) {
        if (photoUrl.isNullOrEmpty()) {
            binding.ivAvatar.visibility = View.GONE
            binding.layoutInitials.visibility = View.VISIBLE
        } else {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.layoutInitials.visibility = View.GONE
            binding.ivAvatar.load(photoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.bg_badge_blue)
                error(R.drawable.bg_badge_blue)
                listener(onError = { _, _ ->
                    binding.ivAvatar.visibility = View.GONE
                    binding.layoutInitials.visibility = View.VISIBLE
                })
            }
        }
    }

    private fun uploadPhoto(uri: Uri) {
        if (uploadJob?.isActive == true) return
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("avatars/$userId.jpg")
        uploadJob = lifecycleScope.launch {
            var tempFile: java.io.File? = null
            try {
                binding.layoutAvatar.isClickable = false
                binding.ivAvatarProgress?.visibility = View.VISIBLE

                // Salin + compress URI ke file sementara (mencegah expired SAF URI + OOM)
                tempFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw Exception("Tidak bisa membuka file foto")
                    val tmp = java.io.File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    // Compress ke max 1024px dan quality 85 untuk hemat storage
                    val originalBytes = inputStream.use { it.readBytes() }
                    if (originalBytes.size > 2 * 1024 * 1024) {
                        // Hanya resize jika > 2MB
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                            ?: throw Exception("File bukan gambar valid")
                        val maxSide = 1024
                        val ratio = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height)
                        val scaled = if (ratio < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
                                .also { if (it != bmp) bmp.recycle() }
                        } else bmp
                        tmp.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        scaled.recycle()
                    } else {
                        tmp.writeBytes(originalBytes)
                    }
                    tmp
                }

                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg").build()

                val uploadResult = withTimeoutOrNull(30_000L) {
                    storageRef.putFile(android.net.Uri.fromFile(tempFile), metadata).await()
                }
                if (uploadResult == null) throw Exception("Upload timeout, coba lagi dengan koneksi lebih stabil")

                val downloadUrl = withTimeoutOrNull(10_000L) {
                    storageRef.downloadUrl.await().toString()
                } ?: throw Exception("Gagal mendapatkan URL foto")

                withTimeoutOrNull(8_000L) { UserRepository(userId).updatePhotoUrl(downloadUrl) }
                showAvatar(downloadUrl)
                Toast.makeText(this@ProfileActivity, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "Upload foto gagal", e)
                Toast.makeText(this@ProfileActivity, "Gagal upload: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
            } finally {
                tempFile?.delete()
                binding.layoutAvatar.isClickable = true
                binding.ivAvatarProgress?.visibility = View.GONE
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.disableActiveIndicator()
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetSourcesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_wishlist -> {
                    startActivity(Intent(this, WishlistActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
