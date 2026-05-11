package com.lenilestari.aethersea.ui.budget

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.databinding.ActivityBudgetSourcesBinding
import com.lenilestari.aethersea.ui.adapters.BudgetSourceAdapter
import com.lenilestari.aethersea.ui.home.MainActivity
import com.lenilestari.aethersea.ui.profile.ProfileActivity
import com.lenilestari.aethersea.ui.wishlist.WishlistActivity
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.disableActiveIndicator
import com.lenilestari.aethersea.util.hideShimmerList
import com.lenilestari.aethersea.util.setDebounceClickListener
import com.lenilestari.aethersea.util.showShimmerList
import com.lenilestari.aethersea.util.showSnackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BudgetSourcesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBudgetSourcesBinding
    private lateinit var adapter: BudgetSourceAdapter
    private lateinit var budgetSourceRepo: BudgetSourceRepository
    private lateinit var budgetCalc: BudgetCalculator
    private var currentPeriod = ""
    private var shimmerShownAt = 0L
    private var isFirstLoad = true
    private var loadJob: Job? = null
    private var deleteJob: Job? = null
    private var editJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetSourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        budgetSourceRepo = BudgetSourceRepository(userId)
        val sessionRepo = SessionRepository(userId)
        val monthlyBudgetRepo = MonthlyBudgetRepository(userId)
        budgetCalc = BudgetCalculator(budgetSourceRepo, sessionRepo, monthlyBudgetRepo)
        // Restore period setelah rotation, atau gunakan intent/default
        currentPeriod = savedInstanceState?.getString(KEY_PERIOD)
            ?: intent.getStringExtra(com.lenilestari.aethersea.util.Constants.EXTRA_PERIOD)
            ?: budgetCalc.getCurrentPeriod()
        isFirstLoad = savedInstanceState == null

        adapter = BudgetSourceAdapter(
            onEdit = { source -> showEditDialog(source) },
            onDelete = { source ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Hapus sumber budget")
                    .setMessage("Hapus budget dari ${source.name}?")
                    .setPositiveButton("Ya") { _, _ -> deleteSource(source) }
                    .setNegativeButton("Batal", null).show()
            }
        )

        binding.rvSources.apply {
            layoutManager = LinearLayoutManager(this@BudgetSourcesActivity)
            adapter = this@BudgetSourcesActivity.adapter
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPrevMonth.setDebounceClickListener(300L) {
            currentPeriod = budgetCalc.getPreviousPeriod(currentPeriod)
            triggerLoad()
        }
        binding.btnNextMonth.setDebounceClickListener(300L) {
            currentPeriod = budgetCalc.getNextPeriod(currentPeriod)
            triggerLoad()
        }
        binding.btnTambahSource.setOnClickListener {
            startActivity(Intent(this, AddBudgetSourceActivity::class.java).apply {
                putExtra(com.lenilestari.aethersea.util.Constants.EXTRA_PERIOD, currentPeriod)
            })
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, BudgetHistoryActivity::class.java))
        }

        setupBottomNav()
        shimmerShownAt = System.currentTimeMillis()
        showShimmerList(binding.shimmerContainer, binding.rvSources)
        triggerLoad()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_budget
        if (!isFirstLoad) triggerLoad()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PERIOD, currentPeriod)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
        deleteJob?.cancel()
        editJob?.cancel()
    }

    companion object {
        private const val KEY_PERIOD = "key_current_period"
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
                R.id.nav_budget -> true
                R.id.nav_wishlist -> {
                    startActivity(Intent(this, WishlistActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerLoad() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { loadData() }
    }

    private fun deleteSource(source: BudgetSource) {
        if (deleteJob?.isActive == true) return
        deleteJob = lifecycleScope.launch {
            try {
                val result = budgetSourceRepo.delete(source.id)
                if (result.isSuccess) {
                    budgetCalc.recalculateCurrentMonth()
                    triggerLoad()
                    showSnackbar(binding.root, "Budget '${source.name}' berhasil dihapus")
                } else {
                    showSnackbar(binding.root, "Gagal menghapus budget", isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showSnackbar(binding.root, "Gagal menghapus", isError = true)
            }
        }
    }

    private fun showEditDialog(source: BudgetSource) {
        val container = android.widget.FrameLayout(this)
        val margin = (16 * resources.displayMetrics.density).toInt()
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            this, null,
            com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox
        ).apply {
            hint = "Nama sumber"
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(margin, margin / 2, margin, 0) }
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            setText(source.name)
            setSingleLine(true)
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit sumber budget")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    showSnackbar(binding.root, "Nama tidak boleh kosong", isError = true)
                    return@setPositiveButton
                }
                if (editJob?.isActive == true) return@setPositiveButton
                editJob = lifecycleScope.launch {
                    try {
                        val updated = source.copy(name = newName)
                        val result = budgetSourceRepo.update(updated)
                        if (result.isSuccess) {
                            triggerLoad()
                            showSnackbar(binding.root, "Budget berhasil diupdate")
                        } else {
                            showSnackbar(binding.root, "Gagal mengupdate budget", isError = true)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showSnackbar(binding.root, "Gagal mengupdate", isError = true)
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private suspend fun loadData() {
        binding.tvCurrentPeriod.text = budgetCalc.formatPeriodDisplay(currentPeriod)
        binding.tvPeriodHeader.text = budgetCalc.formatPeriodDisplay(currentPeriod)

        try {
            coroutineScope {
                val summaryDeferred = async { budgetCalc.getSummaryForPeriod(currentPeriod) }
                val sourcesDeferred = async { budgetSourceRepo.getByPeriod(currentPeriod) }

                val summary = summaryDeferred.await()
                binding.tvBudget.text = CurrencyUtils.format(summary.totalBudget)
                binding.tvSpending.text = CurrencyUtils.format(summary.totalSpending)
                binding.tvLeft.text = CurrencyUtils.format(summary.leftAmount)

                val leftColor = if (summary.leftAmount >= 0) R.color.success_text else R.color.danger_text
                binding.tvLeft.setTextColor(ContextCompat.getColor(this@BudgetSourcesActivity, leftColor))

                if (summary.carryOverFromPrevious != 0L) {
                    binding.bannerCarryOver.visibility = View.VISIBLE
                    val sign = if (summary.carryOverFromPrevious > 0) "+" else ""
                    val icon = if (summary.carryOverFromPrevious > 0) "💰 Sisa bulan lalu:" else "⚠️ Hutang bulan lalu:"
                    binding.tvCarryOver.text = "$icon ${sign}${CurrencyUtils.format(summary.carryOverFromPrevious)}"
                    val bgRes = if (summary.carryOverFromPrevious > 0) R.drawable.bg_badge_green else R.drawable.bg_badge_red
                    binding.bannerCarryOver.setBackgroundResource(bgRes)
                    val textColor = if (summary.carryOverFromPrevious > 0) R.color.success_text else R.color.danger_text
                    binding.tvCarryOver.setTextColor(ContextCompat.getColor(this@BudgetSourcesActivity, textColor))
                } else {
                    binding.bannerCarryOver.visibility = View.GONE
                }

                val sources = sourcesDeferred.await()
                adapter.submitList(sources)
                binding.layoutEmptySources.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE
                binding.rvSources.visibility = if (sources.isEmpty()) View.INVISIBLE else View.VISIBLE
            }

            if (isFirstLoad) {
                val elapsed = System.currentTimeMillis() - shimmerShownAt
                val remaining = 300L - elapsed
                if (remaining > 0) delay(remaining)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            showSnackbar(binding.root, "Gagal memuat budget, coba lagi", isError = true)
        } finally {
            isFirstLoad = false
            hideShimmerList(binding.shimmerContainer, binding.rvSources)
        }
    }
}
