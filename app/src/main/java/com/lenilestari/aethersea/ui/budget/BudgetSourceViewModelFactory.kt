package com.lenilestari.aethersea.ui.budget

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import android.os.Bundle
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository

class BudgetSourceViewModelFactory(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null,
    private val budgetSourceRepo: BudgetSourceRepository,
    private val budgetCalc: BudgetCalculator
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        if (modelClass.isAssignableFrom(BudgetSourceViewModel::class.java)) {
            return BudgetSourceViewModel(budgetSourceRepo, budgetCalc, handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
