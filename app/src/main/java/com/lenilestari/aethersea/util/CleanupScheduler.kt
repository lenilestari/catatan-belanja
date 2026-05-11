package com.lenilestari.aethersea.util

import android.content.Context
import com.google.firebase.Timestamp
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.export.ExcelExporter

class CleanupScheduler(
    private val context: Context,
    private val userRepo: UserRepository,
    private val sessionRepo: SessionRepository,
    private val wishlistRepo: WishlistRepository,
    private val budgetSourceRepo: BudgetSourceRepository,
    private val monthlyBudgetRepo: MonthlyBudgetRepository
) {
    suspend fun checkAndCleanup(): Boolean {
        val profile = userRepo.getProfile() ?: return false
        val lastReset = profile.lastResetAt ?: profile.createdAt
        if (!DateUtils.isOlderThan(lastReset, Constants.CLEANUP_INTERVAL_DAYS)) return false

        val allSessions = sessionRepo.getAllSessions()
        val allWishlists = wishlistRepo.getAll()
        val allSources = budgetSourceRepo.getAll()
        val allMonthly = monthlyBudgetRepo.getAll()

        val exported = ExcelExporter.export(context, allSessions, allWishlists, allSources, allMonthly)
        if (!exported) return false  // BUG-01: jangan hapus data jika export gagal

        sessionRepo.deleteAllSessions()
        userRepo.updateLastResetAt(Timestamp.now())
        return true
    }

    suspend fun daysUntilReset(): Int {
        val profile = userRepo.getProfile()
        val lastReset = profile?.lastResetAt ?: profile?.createdAt ?: Timestamp.now()
        return DateUtils.daysUntilReset(lastReset, Constants.CLEANUP_INTERVAL_DAYS)
    }
}
