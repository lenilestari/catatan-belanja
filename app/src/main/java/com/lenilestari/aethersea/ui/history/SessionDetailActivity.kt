package com.lenilestari.aethersea.ui.history

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.databinding.ActivitySessionDetailBinding
import com.lenilestari.aethersea.export.ExcelExporter
import com.lenilestari.aethersea.ui.adapters.ParsedItemAdapter
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SessionDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var adapter: ParsedItemAdapter
    private lateinit var sessionRepo: SessionRepository
    private lateinit var userId: String
    private var sessionId = ""
    private var deleteJob: Job? = null
    private var exportJob: Job? = null

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
            if (deleteJob?.isActive == true) return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hapus Sesi")
                .setMessage("Hapus sesi belanja ini?")
                .setPositiveButton("Ya") { _, _ -> doDelete() }
                .setNegativeButton("Batal", null).show()
        }

        binding.btnExport.setOnClickListener {
            if (exportJob?.isActive == true) return@setOnClickListener
            doExport()
        }

        lifecycleScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: run { finish(); return@launch }
            binding.tvDate.text = DateUtils.formatDisplay(session.date)
            binding.tvSubtitle.text = "${session.displayCategory} · ${session.items.size} item"
            adapter.setItems(session.items)
            binding.tvTotal.text = CurrencyUtils.format(session.grandTotal)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteJob?.cancel()
        exportJob?.cancel()
    }

    private fun doDelete() {
        setDeleteLoading(true)
        deleteJob = lifecycleScope.launch {
            try {
                val result = sessionRepo.deleteSession(sessionId)
                if (result.isSuccess) {
                    // Recalculate fire-and-forget — jangan block navigasi
                    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch {
                        runCatching {
                            val calc = BudgetCalculator(
                                BudgetSourceRepository(userId),
                                sessionRepo,
                                MonthlyBudgetRepository(userId)
                            )
                            calc.recalculateCurrentMonth()
                        }
                    }
                    Toast.makeText(this@SessionDetailActivity, "Sesi berhasil dihapus", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "coba lagi"
                    AppLogger.e("SessionDetail", "deleteSession failed: $errMsg")
                    Toast.makeText(this@SessionDetailActivity, "Gagal menghapus: $errMsg", Toast.LENGTH_SHORT).show()
                    setDeleteLoading(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SessionDetail", "doDelete crash", e)
                Toast.makeText(this@SessionDetailActivity, "Error: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
                setDeleteLoading(false)
            }
        }
    }

    private fun doExport() {
        setExportLoading(true)
        exportJob = lifecycleScope.launch {
            try {
                val session = sessionRepo.getSession(sessionId)
                if (session == null) {
                    Toast.makeText(this@SessionDetailActivity, "Data sesi tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val success = ExcelExporter.export(
                    this@SessionDetailActivity,
                    listOf(session),
                    emptyList(),
                    emptyList(),
                    emptyList()
                )
                if (success) {
                    Toast.makeText(this@SessionDetailActivity, "✓ Diekspor ke Downloads/CatatanBelanja/", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SessionDetailActivity, "Export gagal, coba lagi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SessionDetail", "doExport crash", e)
                Toast.makeText(this@SessionDetailActivity, "Export error: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
            } finally {
                setExportLoading(false)
            }
        }
    }

    private fun setDeleteLoading(loading: Boolean) {
        binding.btnHapus.isEnabled = !loading
        binding.btnHapus.alpha = if (loading) 0.5f else 1f
    }

    private fun setExportLoading(loading: Boolean) {
        binding.btnExport.isEnabled = !loading
        binding.btnExport.alpha = if (loading) 0.5f else 1f
    }
}
