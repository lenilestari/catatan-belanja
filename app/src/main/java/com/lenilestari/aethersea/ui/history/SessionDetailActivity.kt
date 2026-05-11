package com.lenilestari.aethersea.ui.history

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivitySessionDetailBinding
import com.lenilestari.aethersea.export.ExcelExporter
import com.lenilestari.aethersea.ui.adapters.ParsedItemAdapter
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SessionDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var adapter: ParsedItemAdapter
    private lateinit var sessionRepo: SessionRepository
    private lateinit var userId: String
    private var sessionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        sessionRepo = SessionRepository(userId)
        sessionId = intent.getStringExtra(Constants.EXTRA_SESSION_ID) ?: run { finish(); return }

        adapter = ParsedItemAdapter()
        binding.rvItems.apply {
            layoutManager = LinearLayoutManager(this@SessionDetailActivity)
            adapter = this@SessionDetailActivity.adapter
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.setOnClickListener { Toast.makeText(this, "Edit coming soon", Toast.LENGTH_SHORT).show() }

        binding.btnHapus.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hapus Sesi")
                .setMessage("Hapus sesi belanja ini?")
                .setPositiveButton("Ya") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val result = sessionRepo.deleteSession(sessionId)
                            if (result.isSuccess) {
                                val budgetSourceRepo = BudgetSourceRepository(userId)
                                val monthlyBudgetRepo = MonthlyBudgetRepository(userId)
                                val calc = BudgetCalculator(budgetSourceRepo, sessionRepo, monthlyBudgetRepo)
                                try {
                                    withTimeoutOrNull(5_000L) { calc.recalculateCurrentMonth() }
                                } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                                Toast.makeText(this@SessionDetailActivity, "Sesi dihapus", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@SessionDetailActivity, "Gagal menghapus", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(this@SessionDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Batal", null).show()
        }

        binding.btnExport.setOnClickListener {
            lifecycleScope.launch {
                val session = sessionRepo.getSession(sessionId) ?: return@launch
                val success = ExcelExporter.export(this@SessionDetailActivity, listOf(session), emptyList(), emptyList(), emptyList())
                Toast.makeText(this@SessionDetailActivity, if (success) "Diekspor ke Downloads" else "Export gagal", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: run { finish(); return@launch }
            binding.tvDate.text = DateUtils.formatDisplay(session.date)
            binding.tvSubtitle.text = "${session.displayCategory} · ${session.items.size} item"
            adapter.setItems(session.items)
            binding.tvTotal.text = CurrencyUtils.format(session.grandTotal)
        }
    }
}
