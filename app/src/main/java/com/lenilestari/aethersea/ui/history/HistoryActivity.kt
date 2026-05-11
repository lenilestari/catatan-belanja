package com.lenilestari.aethersea.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivityHistoryBinding
import com.lenilestari.aethersea.export.ExcelExporter
import com.lenilestari.aethersea.ui.adapters.SessionAdapter
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
import com.lenilestari.aethersea.util.hideShimmerList
import com.lenilestari.aethersea.util.setBtnLoading
import com.lenilestari.aethersea.util.showShimmerList
import com.lenilestari.aethersea.util.showSnackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: SessionAdapter
    private lateinit var sessionRepo: SessionRepository
    private lateinit var userId: String
    private var shimmerShownAt = 0L
    private var isFirstLoad = true
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        sessionRepo = SessionRepository(userId)

        adapter = SessionAdapter { session ->
            startActivity(Intent(this, SessionDetailActivity::class.java).apply {
                putExtra(Constants.EXTRA_SESSION_ID, session.id)
            })
        }

        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnExport.setOnClickListener {
            lifecycleScope.launch {
                setBtnLoading(binding.btnExport, binding.tvBtnExport, binding.pbBtnExport, true)
                try {
                    val sessions = sessionRepo.getAllSessions()
                    val wishlists = WishlistRepository(userId).getAll()
                    val sources = BudgetSourceRepository(userId).getAll()
                    val monthly = MonthlyBudgetRepository(userId).getAll()
                    val success = ExcelExporter.export(this@HistoryActivity, sessions, wishlists, sources, monthly)
                    if (success) {
                        showSnackbar(binding.root, "Excel berhasil disimpan ke Downloads/CatatanBelanja/")
                    } else {
                        showSnackbar(binding.root, "Export gagal, coba lagi", isError = true)
                    }
                } catch (e: Exception) {
                    showSnackbar(binding.root, "Export gagal: ${e.message}", isError = true)
                } finally {
                    setBtnLoading(binding.btnExport, binding.tvBtnExport, binding.pbBtnExport, false)
                }
            }
        }

        binding.btnOpenFolder.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FCatatanBelanja")))
            } catch (_: Exception) {
                showSnackbar(binding.root, "Buka Downloads/CatatanBelanja/ di file manager")
            }
        }

        shimmerShownAt = System.currentTimeMillis()
        showShimmerList(binding.shimmerContainer, binding.rvSessions)
        triggerLoad()
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) triggerLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    private fun triggerLoad() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { loadData() }
    }

    private suspend fun loadData() {
        try {
            coroutineScope {
                val sessionsDeferred = async { sessionRepo.getAllSessions() }
                val profileDeferred = async { UserRepository(userId).getProfile() }

                val sessions = sessionsDeferred.await()
                adapter.submitList(sessions)
                binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE

                val monthStart = DateUtils.startOfMonth()
                val monthSessions = sessions.filter { it.date >= monthStart }
                val monthTotal = monthSessions.sumOf { it.grandTotal }
                binding.tvBulanIni.text = CurrencyUtils.format(monthTotal)

                val dayOfMonth = maxOf(1, Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
                val avgPerDay = monthTotal / dayOfMonth
                binding.tvRataRata.text = CurrencyUtils.format(avgPerDay)

                val profile = profileDeferred.await()
                val lastReset = profile?.lastResetAt ?: profile?.createdAt
                if (lastReset != null) {
                    val days = DateUtils.daysUntilReset(lastReset, Constants.CLEANUP_INTERVAL_DAYS)
                    if (days <= 14) {
                        binding.bannerCleanup.visibility = View.VISIBLE
                        binding.tvCleanupWarning.text = "⚠️ Reset otomatis dalam $days hari. Excel akan diekspor sebelum data dihapus."
                    }
                }
            }

            if (isFirstLoad) {
                val elapsed = System.currentTimeMillis() - shimmerShownAt
                val remaining = 300L - elapsed
                if (remaining > 0) delay(remaining)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            showSnackbar(binding.root, "Gagal memuat riwayat, coba lagi", isError = true)
        } finally {
            isFirstLoad = false
            hideShimmerList(binding.shimmerContainer, binding.rvSessions)
        }
    }
}
