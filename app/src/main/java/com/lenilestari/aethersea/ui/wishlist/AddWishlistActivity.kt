package com.lenilestari.aethersea.ui.wishlist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.data.model.Wishlist
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivityAddWishlistBinding
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.setBtnLoading
import kotlinx.coroutines.launch
import kotlin.math.ceil

class AddWishlistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddWishlistBinding
    private lateinit var wishlistRepo: WishlistRepository
    private var editingId: String? = null
    private var targetRaw = 0L
    private var savedRaw = 0L
    private var monthlyRaw = 0L
    private var saveJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWishlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        wishlistRepo = WishlistRepository(userId)
        editingId = intent.getStringExtra(Constants.EXTRA_WISHLIST_ID)

        if (editingId != null) {
            binding.tvTitle.text = "Edit wishlist"
            binding.btnHapus.visibility = android.view.View.VISIBLE
            lifecycleScope.launch { loadExisting(editingId!!) }
        }

        setupWatcher(binding.etTarget) { targetRaw = it; updateEstimasi() }
        setupWatcher(binding.etSaved) { savedRaw = it; updateEstimasi() }
        setupWatcher(binding.etMonthly) { monthlyRaw = it; updateEstimasi() }

        binding.chip250k.setOnClickListener { addToField(binding.etMonthly, 250000L) { monthlyRaw = it; updateEstimasi() } }
        binding.chip500k.setOnClickListener { addToField(binding.etMonthly, 500000L) { monthlyRaw = it; updateEstimasi() } }
        binding.chip1m.setOnClickListener { addToField(binding.etMonthly, 1000000L) { monthlyRaw = it; updateEstimasi() } }

        binding.btnBatal.setOnClickListener { cancelAndFinish() }
        binding.btnSimpan.setOnClickListener { simpan() }
        binding.btnHapus.setOnClickListener { hapus() }
        binding.btnBack.setOnClickListener { cancelAndFinish() }
    }

    private fun setupWatcher(et: android.widget.EditText, onChanged: (Long) -> Unit) {
        et.addTextChangedListener(object : TextWatcher {
            private var updating = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                updating = true
                val raw = s.toString().replace(".", "").toLongOrNull() ?: 0L
                onChanged(raw)
                if (raw > 0) {
                    val fmt = "%,d".format(raw).replace(",", ".")
                    et.setText(fmt); et.setSelection(fmt.length)
                }
                updating = false
            }
        })
    }

    private fun addToField(et: android.widget.EditText, amount: Long, onChanged: (Long) -> Unit) {
        val cur = et.text.toString().replace(".", "").toLongOrNull() ?: 0L
        val new = cur + amount
        onChanged(new)
        val fmt = "%,d".format(new).replace(",", ".")
        et.setText(fmt); et.setSelection(fmt.length)
    }

    private fun updateEstimasi() {
        val rem = (targetRaw - savedRaw).coerceAtLeast(0L)
        val months = if (monthlyRaw > 0 && rem > 0) ceil(rem.toDouble() / monthlyRaw).toInt() else 0
        binding.tvEstimasi.text = if (months > 0) "~$months bulan lagi · Sisa Rp${"%,d".format(rem).replace(",", ".")}"
        else if (rem <= 0 && targetRaw > 0) "Sudah tercapai! 🎉"
        else "Isi form untuk estimasi"
    }

    private suspend fun loadExisting(id: String) {
        val w = wishlistRepo.getById(id) ?: return
        binding.etName.setText(w.name)
        targetRaw = w.targetPrice.toLong()
        savedRaw = w.savedAmount.toLong()
        monthlyRaw = w.monthlyTarget.toLong()
        binding.etTarget.setText("%,d".format(targetRaw).replace(",", "."))
        binding.etSaved.setText("%,d".format(savedRaw).replace(",", "."))
        binding.etMonthly.setText("%,d".format(monthlyRaw).replace(",", "."))
        updateEstimasi()
    }

    private fun cancelAndFinish() {
        saveJob?.cancel()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveJob?.cancel()
    }

    private fun simpan() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(this, "Nama harus diisi", Toast.LENGTH_SHORT).show(); return }
        if (targetRaw == 0L) { Toast.makeText(this, "Harga target harus diisi", Toast.LENGTH_SHORT).show(); return }
        if (saveJob?.isActive == true) return

        setBtnLoading(binding.btnSimpan, binding.tvBtnSimpan, binding.pbBtnSimpan, true)
        var didFinish = false
        saveJob = lifecycleScope.launch {
            try {
                val wishlist = Wishlist(
                    id = editingId ?: "",
                    name = name,
                    targetPrice = targetRaw.toDouble(),
                    savedAmount = savedRaw.toDouble(),
                    monthlyTarget = monthlyRaw.toDouble(),
                    createdAt = Timestamp.now()
                )
                val result = if (editingId != null) wishlistRepo.update(wishlist) else wishlistRepo.add(wishlist).map { }
                if (result.isSuccess) {
                    Toast.makeText(this@AddWishlistActivity, "Wishlist tersimpan!", Toast.LENGTH_SHORT).show()
                    didFinish = true
                    finish()
                } else {
                    Toast.makeText(this@AddWishlistActivity, "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@AddWishlistActivity, "Error: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
            } finally {
                if (!didFinish) setBtnLoading(binding.btnSimpan, binding.tvBtnSimpan, binding.pbBtnSimpan, false)
            }
        }
    }

    private fun hapus() {
        val id = editingId ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hapus Wishlist")
            .setMessage("Hapus wishlist ini?")
            .setPositiveButton("Ya") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val result = wishlistRepo.delete(id)
                        if (result.isSuccess) {
                            finish()
                        } else {
                            Toast.makeText(this@AddWishlistActivity, "Gagal menghapus wishlist", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(this@AddWishlistActivity, "Error: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null).show()
    }
}
