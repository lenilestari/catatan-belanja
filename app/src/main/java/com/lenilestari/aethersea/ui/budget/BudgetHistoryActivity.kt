package com.lenilestari.aethersea.ui.budget

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.databinding.ActivityBudgetHistoryBinding
import com.lenilestari.aethersea.ui.adapters.MonthlyBudgetAdapter
import com.lenilestari.aethersea.util.hideShimmerList
import com.lenilestari.aethersea.util.showShimmerList
import kotlinx.coroutines.launch

class BudgetHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBudgetHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        val monthlyBudgetRepo = MonthlyBudgetRepository(userId)

        val monthlyAdapter = MonthlyBudgetAdapter { mb ->
            startActivity(Intent(this, BudgetSourcesActivity::class.java).apply {
                putExtra(com.lenilestari.aethersea.util.Constants.EXTRA_PERIOD, mb.period)
            })
        }

        binding.rvMonths.apply {
            layoutManager = LinearLayoutManager(this@BudgetHistoryActivity)
            adapter = monthlyAdapter
        }

        binding.btnBack.setOnClickListener { finish() }

        showShimmerList(binding.shimmerContainer, binding.rvMonths)

        lifecycleScope.launch {
            val months = monthlyBudgetRepo.getAll()
            monthlyAdapter.submitList(months)
            hideShimmerList(binding.shimmerContainer, binding.rvMonths)
        }
    }
}
