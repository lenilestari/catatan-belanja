package com.lenilestari.aethersea.ui.result

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.model.ShoppingItem
import com.lenilestari.aethersea.data.model.ShoppingSession
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.databinding.ActivityResultBinding
import com.lenilestari.aethersea.processor.VoiceBatchManager
import com.lenilestari.aethersea.ui.adapters.ParsedItemAdapter
import com.lenilestari.aethersea.ui.home.MainActivity
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.setBtnLoading
import com.lenilestari.aethersea.util.showSnackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private lateinit var adapter: ParsedItemAdapter
    private var items = mutableListOf<ShoppingItem>()
    private var grandTotal = 0.0
    private var mainCategoryId = ""
    private var mainCategoryName = ""
    private var subCategoryId = ""
    private var subCategoryName = ""
    private var selectedDate = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainCategoryId = intent.getStringExtra(Constants.EXTRA_CATEGORY_ID) ?: ""
        mainCategoryName = intent.getStringExtra(Constants.EXTRA_CATEGORY_NAME) ?: ""
        subCategoryId = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_ID) ?: ""
        subCategoryName = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_NAME) ?: ""
        selectedDate = intent.getLongExtra(Constants.EXTRA_SELECTED_DATE, System.currentTimeMillis())

        val json = intent.getStringExtra(Constants.EXTRA_PARSED_ITEMS_JSON)
        if (json != null) {
            // BUG-07: JSON bisa malformed jika respons AI terpotong
            try {
                val type = object : TypeToken<List<ShoppingItem>>() {}.type
                items = Gson().fromJson<List<ShoppingItem>>(json, type).toMutableList()
                grandTotal = intent.getDoubleExtra(Constants.EXTRA_GRAND_TOTAL, items.sumOf { it.total })
            } catch (e: Exception) {
                Toast.makeText(this, "Data hasil AI tidak valid, coba input ulang", Toast.LENGTH_LONG).show()
                VoiceBatchManager.clear()
                finish(); return
            }
        } else {
            items = VoiceBatchManager.getManualItems().toMutableList()
            grandTotal = items.sumOf { it.total }
            VoiceBatchManager.clear()
        }

        adapter = ParsedItemAdapter()
        adapter.setItems(items)

        binding.rvItems.apply {
            layoutManager = LinearLayoutManager(this@ResultActivity)
            adapter = this@ResultActivity.adapter
        }

        updateTotals()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.visibility = android.view.View.GONE  // BUG-13: fitur belum diimplementasi
        binding.btnSimpan.setOnClickListener { simpanSession() }
    }

    private fun setSimpanLoading(loading: Boolean) =
        setBtnLoading(binding.btnSimpan, binding.tvBtnSimpan, binding.pbBtnSimpan, loading)

    private fun updateTotals() {
        val total = adapter.getItems().sumOf { it.total }
        binding.tvGrandTotal.text = CurrencyUtils.format(total)
        binding.tvItemCount.text = "${adapter.getItems().size} item"
    }

    private fun simpanSession() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sessionRepo = SessionRepository(userId)
        val budgetSourceRepo = BudgetSourceRepository(userId)
        val monthlyBudgetRepo = MonthlyBudgetRepository(userId)
        val budgetCalc = BudgetCalculator(budgetSourceRepo, sessionRepo, monthlyBudgetRepo)
        val finalItems = adapter.getItems()
        // Recalculate total dari qty*price, bukan pakai stored total dari AI (bisa salah)
        val total = finalItems.sumOf { it.qty * it.price }

        setSimpanLoading(true)
        var didNavigate = false
        lifecycleScope.launch {
            try {
                val session = ShoppingSession(
                    date = Timestamp(Date(selectedDate)),
                    mainCategoryId = mainCategoryId,
                    mainCategoryName = mainCategoryName,
                    subCategoryId = subCategoryId,
                    subCategoryName = subCategoryName,
                    items = finalItems,
                    grandTotal = total,
                    createdAt = Timestamp.now()
                )
                val result = sessionRepo.addSession(session)
                if (result.isSuccess) {
                    // Recalculate diberi batas waktu — tidak boleh block navigasi
                    try {
                        withTimeoutOrNull(5_000L) { budgetCalc.recalculateCurrentMonth() }
                    } catch (e: CancellationException) {
                        throw e  // wajib re-throw agar structured concurrency tidak rusak
                    } catch (_: Exception) {}
                    showSnackbar(binding.root, "✓ Belanja berhasil disimpan!")
                    kotlinx.coroutines.delay(800)
                    didNavigate = true
                    startActivity(Intent(this@ResultActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "unknown"
                    android.util.Log.e("ResultActivity", "addSession failed: $errMsg")
                    showSnackbar(binding.root, "Gagal menyimpan: $errMsg", isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ResultActivity", "simpanSession crash: ${e.message}", e)
                showSnackbar(binding.root, "Error: ${e.message ?: "coba lagi"}", isError = true)
            } finally {
                // Selalu reset loading kecuali sudah navigasi — activity tetap aman di-call finish()
                if (!didNavigate) setSimpanLoading(false)
            }
        }
    }
}
