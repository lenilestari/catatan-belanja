package com.lenilestari.aethersea.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.model.Category
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.CategoryRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.data.repository.UserRepository
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivityMainBinding
import com.lenilestari.aethersea.ui.adapters.MainCategoryAdapter
import com.lenilestari.aethersea.ui.adapters.WishlistAdapter
import com.lenilestari.aethersea.ui.auth.LoginActivity
import com.lenilestari.aethersea.ui.budget.BudgetSourcesActivity
import com.lenilestari.aethersea.ui.history.HistoryActivity
import com.lenilestari.aethersea.ui.input.InputActivity
import com.lenilestari.aethersea.ui.profile.ProfileActivity
import com.lenilestari.aethersea.ui.wishlist.AddWishlistActivity
import com.lenilestari.aethersea.ui.wishlist.WishlistActivity
import com.lenilestari.aethersea.ui.wishlist.WishlistDetailActivity
import com.lenilestari.aethersea.util.CleanupScheduler
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var userId: String
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var wishlistRepo: WishlistRepository
    private lateinit var userRepo: UserRepository
    private lateinit var budgetSourceRepo: BudgetSourceRepository
    private lateinit var monthlyBudgetRepo: MonthlyBudgetRepository
    private lateinit var budgetCalculator: BudgetCalculator
    private lateinit var categoryAdapter: MainCategoryAdapter
    private lateinit var wishlistAdapter: WishlistAdapter
    private var selectedDate: Long = System.currentTimeMillis()
    private var shimmerShownAt = 0L
    private var isFirstLoad = true
    private var loadJob: Job? = null
    private var isSpeedDialOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { goToLogin(); return }
        userId = user.uid

        initRepos()
        setupAdapters()
        setupClickListeners()
        setupGreeting(user.displayName ?: "")
        loadAvatarPhoto(user.photoUrl?.toString())

        binding.tvSelectedDate.text = DateUtils.formatDisplay(selectedDate)
        binding.tvTodayDate.text = DateUtils.formatDisplay(selectedDate)

        shimmerShownAt = System.currentTimeMillis()
        showShimmerList(binding.shimmerCategories, binding.rvCategories)
        showShimmerList(binding.shimmerWishlists, binding.rvWishlists)

        // Langsung load dari cache agar UI tidak blank 15 detik
        android.util.Log.d("DBG_AETHER", "── STARTUP: triggerLoad() immediate")
        triggerLoad()

        if (savedInstanceState == null) {
            // Rollover + seed jalan paralel di background — setelah selesai reload
            lifecycleScope.launch {
                android.util.Log.d("DBG_AETHER", "── STARTUP BG: rolloverPreviousMonths...")
                try {
                    budgetCalculator.rolloverPreviousMonths()
                    android.util.Log.d("DBG_AETHER", "── STARTUP BG: rolloverPreviousMonths OK")
                    categoryRepo.seedDefaultsIfNeeded()
                    android.util.Log.d("DBG_AETHER", "── STARTUP BG: seedDefaultsIfNeeded OK → reload")
                    // Reload setelah seed agar kategori muncul
                    triggerLoad()
                    val cleanup = CleanupScheduler(this@MainActivity, userRepo, sessionRepo,
                        wishlistRepo, budgetSourceRepo, monthlyBudgetRepo)
                    cleanup.checkAndCleanup()
                    android.util.Log.d("DBG_AETHER", "── STARTUP BG: cleanup OK")
                } catch (e: Exception) {
                    android.util.Log.e("DBG_AETHER", "── STARTUP BG: EXCEPTION ${e::class.simpleName}: ${e.message}", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_home
        // BUG-18: refresh avatar jika user baru update foto di ProfileActivity
        val currentPhotoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        if (currentPhotoUrl != null) loadAvatarPhoto(currentPhotoUrl)
        if (!isFirstLoad) triggerLoad()
    }

    override fun onBackPressed() {
        if (isSpeedDialOpen) { closeSpeedDial(); return }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    private fun triggerLoad() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { loadData() }
    }

    private fun initRepos() {
        userRepo = UserRepository(userId)
        categoryRepo = CategoryRepository(userId)
        sessionRepo = SessionRepository(userId)
        wishlistRepo = WishlistRepository(userId)
        budgetSourceRepo = BudgetSourceRepository(userId)
        monthlyBudgetRepo = MonthlyBudgetRepository(userId)
        budgetCalculator = BudgetCalculator(budgetSourceRepo, sessionRepo, monthlyBudgetRepo)
    }

    private fun setupAdapters() {
        categoryAdapter = MainCategoryAdapter { category -> navigateToCategory(category) }
        wishlistAdapter = WishlistAdapter { wishlist ->
            startActivity(Intent(this, WishlistDetailActivity::class.java).apply {
                putExtra(Constants.EXTRA_WISHLIST_ID, wishlist.id)
            })
        }
        binding.rvCategories.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = categoryAdapter
        }
        binding.rvWishlists.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wishlistAdapter
        }
    }

    private fun navigateToCategory(category: Category) {
        setFabLoading(true)
        lifecycleScope.launch {
            try {
                val hasSub = categoryRepo.hasSubCategories(category.id)
                if (hasSub) {
                    startActivity(Intent(this@MainActivity, SubCategoryActivity::class.java).apply {
                        putExtra(Constants.EXTRA_CATEGORY_ID, category.id)
                        putExtra(Constants.EXTRA_CATEGORY_NAME, category.name)
                        putExtra(Constants.EXTRA_CATEGORY_ICON, category.icon)
                        putExtra(Constants.EXTRA_SELECTED_DATE, selectedDate)
                    })
                } else {
                    startActivity(Intent(this@MainActivity, InputActivity::class.java).apply {
                        putExtra(Constants.EXTRA_CATEGORY_ID, category.id)
                        putExtra(Constants.EXTRA_CATEGORY_NAME, category.name)
                        putExtra(Constants.EXTRA_SELECTED_DATE, selectedDate)
                    })
                }
            } catch (e: Exception) {
                showSnackbar(binding.root, "Gagal membuka kategori", isError = true)
            } finally {
                setFabLoading(false)
            }
        }
    }

    private fun setFabLoading(loading: Boolean) {
        binding.fabMain.isClickable = !loading
        binding.fabMain.alpha = if (loading) 0.6f else 1f
    }

    private fun setupClickListeners() {
        binding.fabMain.setOnClickListener {
            if (isSpeedDialOpen) closeSpeedDial() else openSpeedDial()
        }

        binding.speedDialScrim.setOnClickListener { closeSpeedDial() }

        binding.sdFabRiwayat.setOnClickListener {
            closeSpeedDial()
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.sdFabBudget.setOnClickListener {
            closeSpeedDial()
            startActivity(Intent(this, BudgetSourcesActivity::class.java))
        }

        binding.cardBudget.setDebounceClickListener {
            startActivity(Intent(this, BudgetSourcesActivity::class.java))
        }

        binding.btnTambahWishlist.setDebounceClickListener {
            startActivity(Intent(this, AddWishlistActivity::class.java))
        }

        binding.btnTambahKategori.setOnClickListener {
            Toast.makeText(this, "Fitur tambah kategori coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.layoutAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        binding.cardDate.setDebounceClickListener { showDatePicker() }

        binding.bottomNav.disableActiveIndicator()
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetSourcesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
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

    private fun openSpeedDial() {
        isSpeedDialOpen = true
        binding.speedDialScrim.visibility = View.VISIBLE
        binding.speedDialScrim.alpha = 0f
        binding.speedDialScrim.animate().alpha(1f).setDuration(200).start()

        binding.fabMain.animate().rotation(45f).setDuration(200).start()

        showSpeedDialItem(binding.sdItemBudget, 0)
        showSpeedDialItem(binding.sdItemRiwayat, 80)

        // BUG-19: nonaktifkan bottomNav agar tidak bisa di-tap saat speed dial terbuka
        binding.bottomNav.isEnabled = false
        for (i in 0 until binding.bottomNav.menu.size()) {
            binding.bottomNav.menu.getItem(i).isEnabled = false
        }
    }

    private fun closeSpeedDial() {
        isSpeedDialOpen = false
        binding.speedDialScrim.animate().alpha(0f).setDuration(150).withEndAction {
            binding.speedDialScrim.visibility = View.GONE
        }.start()

        binding.fabMain.animate().rotation(0f).setDuration(200).start()

        hideSpeedDialItem(binding.sdItemBudget, 0)
        hideSpeedDialItem(binding.sdItemRiwayat, 40)

        // BUG-19: aktifkan kembali bottomNav
        binding.bottomNav.isEnabled = true
        for (i in 0 until binding.bottomNav.menu.size()) {
            binding.bottomNav.menu.getItem(i).isEnabled = true
        }
    }

    private fun showSpeedDialItem(item: View, delayMs: Long) {
        item.visibility = View.VISIBLE
        item.alpha = 0f
        item.translationY = 40f
        item.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(200).start()
    }

    private fun hideSpeedDialItem(item: View, delayMs: Long) {
        item.animate().alpha(0f).translationY(40f).setStartDelay(delayMs).setDuration(150)
            .withEndAction { item.visibility = View.INVISIBLE }.start()
    }

    private fun setupGreeting(displayName: String) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 0..11 -> "Selamat pagi,"
            in 12..14 -> "Selamat siang,"
            in 15..17 -> "Selamat sore,"
            else -> "Selamat malam,"
        }
        val firstName = displayName.split(" ").firstOrNull() ?: "Kamu"
        binding.tvUsername.text = "$firstName 👋"
        val initials = displayName.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
        binding.tvAvatarInitial.text = initials.ifEmpty { "?" }
    }

    private fun loadAvatarPhoto(photoUrl: String?) {
        if (photoUrl.isNullOrEmpty()) return
        binding.ivAvatarMain.visibility = View.VISIBLE
        binding.layoutInitials.visibility = View.GONE
        binding.ivAvatarMain.load(photoUrl) {
            transformations(CircleCropTransformation())
            error(R.drawable.bg_badge_blue)
            listener(onError = { _, _ ->
                binding.ivAvatarMain.visibility = View.GONE
                binding.layoutInitials.visibility = View.VISIBLE
            })
        }
    }

    private suspend fun loadData() {
        android.util.Log.d("DBG_AETHER", "── loadData() START")
        try {
            val loadSuccess = withTimeoutOrNull(10_000L) {
                coroutineScope {
                    val categoriesDeferred = async {
                        android.util.Log.d("DBG_AETHER", "   [async] getMainCategories start")
                        val r = categoryRepo.getMainCategories()
                        android.util.Log.d("DBG_AETHER", "   [async] getMainCategories done → ${r.size} item")
                        r
                    }
                    val summaryDeferred = async {
                        android.util.Log.d("DBG_AETHER", "   [async] getCurrentMonthSummary start")
                        val r = budgetCalculator.getCurrentMonthSummary()
                        android.util.Log.d("DBG_AETHER", "   [async] getCurrentMonthSummary done → budget=${r.totalBudget}")
                        r
                    }
                    val wishlistsDeferred = async {
                        android.util.Log.d("DBG_AETHER", "   [async] wishlist.getAll start")
                        val r = wishlistRepo.getAll()
                        android.util.Log.d("DBG_AETHER", "   [async] wishlist.getAll done → ${r.size} item")
                        r
                    }

                    val categories = categoriesDeferred.await()
                    android.util.Log.d("DBG_AETHER", "── categories await OK: ${categories.size} kategori")
                    categoryAdapter.submitList(categories)

                    val summary = summaryDeferred.await()
                    android.util.Log.d("DBG_AETHER", "── summary await OK")
                    binding.tvBudget.text = CurrencyUtils.format(summary.totalBudget)
                    binding.tvSpending.text = CurrencyUtils.format(summary.totalSpending)
                    binding.tvLeft.text = CurrencyUtils.format(summary.leftAmount)
                    val leftColor = if (summary.leftAmount >= 0) R.color.success_text else R.color.danger_text
                    binding.tvLeft.setTextColor(ContextCompat.getColor(this@MainActivity, leftColor))

                    android.util.Log.d("DBG_AETHER", "── getSessionsThisMonth start")
                    val (start, _) = budgetCalculator.getMonthBoundaries(budgetCalculator.getCurrentPeriod())
                    val sessions = sessionRepo.getSessionsThisMonth(start)
                    android.util.Log.d("DBG_AETHER", "── getSessionsThisMonth done → ${sessions.size} sesi")
                    binding.tvTotalBulan.text = CurrencyUtils.format(sessions.sumOf { it.grandTotal })
                    binding.tvSessionCount.text = "📈 ${sessions.size} sesi"

                    val wishlists = wishlistsDeferred.await().take(2)
                    android.util.Log.d("DBG_AETHER", "── wishlists await OK: ${wishlists.size} item")
                    wishlistAdapter.submitList(wishlists)
                }
                true
            }

            if (loadSuccess == null) {
                android.util.Log.w("DBG_AETHER", "── loadData() TIMEOUT (>10 detik)")
                showSnackbar(binding.root, "Koneksi lambat, coba refresh", isError = true)
            } else {
                if (isFirstLoad) {
                    val elapsed = System.currentTimeMillis() - shimmerShownAt
                    val remaining = 300L - elapsed
                    if (remaining > 0) delay(remaining)
                }
                android.util.Log.d("DBG_AETHER", "── loadData() SUCCESS → shimmer akan disembunyikan")
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                android.util.Log.w("DBG_AETHER", "── loadData() CANCELLED")
                throw e
            }
            android.util.Log.e("DBG_AETHER", "── loadData() ERROR: ${e::class.simpleName}: ${e.message}", e)
            showSnackbar(binding.root, "Gagal memuat data, coba lagi", isError = true)
        } finally {
            android.util.Log.d("DBG_AETHER", "── loadData() finally → hideShimmer")
            isFirstLoad = false
            hideShimmerList(binding.shimmerCategories, binding.rvCategories)
            hideShimmerList(binding.shimmerWishlists, binding.rvWishlists)
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            selectedDate = cal.timeInMillis
            binding.tvSelectedDate.text = DateUtils.formatDisplay(selectedDate)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
