package com.lenilestari.aethersea.ui.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.data.model.MonthSummary
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Sealed UI State ──────────────────────────────────────────
data class BudgetUiState(
    val isLoading: Boolean = false,
    val sources: List<BudgetSource> = emptyList(),
    val summary: MonthSummary? = null,
    val currentPeriod: String = "",
    val isEmpty: Boolean = false,
    val errorMessage: String? = null
)

// One-time events (Snackbar, navigation, dll) — tidak ikut compose state
sealed class BudgetEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : BudgetEvent()
    object NavigateBack : BudgetEvent()
}

// ── ViewModel ────────────────────────────────────────────────
class BudgetSourceViewModel(
    private val budgetSourceRepo: BudgetSourceRepository,
    private val budgetCalc: BudgetCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    // SharedFlow untuk one-time events — tidak pernah di-replay setelah rotation
    private val _events = MutableSharedFlow<BudgetEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        val initialPeriod = savedStateHandle.get<String>(KEY_PERIOD)
            ?: budgetCalc.getCurrentPeriod()
        _uiState.update { it.copy(currentPeriod = initialPeriod) }
        load()
    }

    fun navigatePreviousMonth() {
        val prev = budgetCalc.getPreviousPeriod(_uiState.value.currentPeriod)
        _uiState.update { it.copy(currentPeriod = prev) }
        load()
    }

    fun navigateNextMonth() {
        val next = budgetCalc.getNextPeriod(_uiState.value.currentPeriod)
        _uiState.update { it.copy(currentPeriod = next) }
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val period = _uiState.value.currentPeriod
                coroutineScope {
                    val summaryDef = async { budgetCalc.getSummaryForPeriod(period) }
                    val sourcesDef = async { budgetSourceRepo.getByPeriod(period) }
                    val summary = summaryDef.await()
                    val sources = sourcesDef.await()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sources = sources,
                            summary = summary,
                            isEmpty = sources.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BudgetVM", "load error", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Gagal memuat budget") }
            }
        }
    }

    fun deleteSource(source: BudgetSource) {
        viewModelScope.launch {
            try {
                val result = budgetSourceRepo.delete(source.id)
                if (result.isSuccess) {
                    budgetCalc.recalculateCurrentMonth()
                    load()
                    _events.emit(BudgetEvent.ShowMessage("Budget '${source.name}' berhasil dihapus"))
                } else {
                    _events.emit(BudgetEvent.ShowMessage("Gagal menghapus budget", isError = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(BudgetEvent.ShowMessage("Gagal menghapus", isError = true))
            }
        }
    }

    fun updateSourceName(source: BudgetSource, newName: String) {
        if (newName.isBlank()) {
            viewModelScope.launch { _events.emit(BudgetEvent.ShowMessage("Nama tidak boleh kosong", isError = true)) }
            return
        }
        viewModelScope.launch {
            try {
                val result = budgetSourceRepo.update(source.copy(name = newName.trim()))
                if (result.isSuccess) {
                    load()
                    _events.emit(BudgetEvent.ShowMessage("Budget berhasil diupdate"))
                } else {
                    _events.emit(BudgetEvent.ShowMessage("Gagal mengupdate", isError = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(BudgetEvent.ShowMessage("Gagal mengupdate", isError = true))
            }
        }
    }

    fun formatPeriodDisplay(period: String) = budgetCalc.formatPeriodDisplay(period)

    companion object {
        const val KEY_PERIOD = "key_budget_period"
    }
}
