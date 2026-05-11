package com.lenilestari.aethersea.ui.wishlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.data.repository.WishlistRepository
import com.lenilestari.aethersea.databinding.ActivityWishlistBinding
import com.lenilestari.aethersea.ui.adapters.WishlistAdapter
import com.lenilestari.aethersea.ui.budget.BudgetSourcesActivity
import com.lenilestari.aethersea.ui.home.MainActivity
import com.lenilestari.aethersea.ui.profile.ProfileActivity
import com.lenilestari.aethersea.util.disableActiveIndicator
import com.lenilestari.aethersea.util.hideShimmerList
import com.lenilestari.aethersea.util.hideShimmerOnly
import com.lenilestari.aethersea.util.showShimmerList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WishlistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWishlistBinding
    private lateinit var adapter: WishlistAdapter
    private lateinit var repo: WishlistRepository
    private var shimmerShownAt = 0L
    private var isFirstLoad = true
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWishlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        repo = WishlistRepository(userId)

        adapter = WishlistAdapter { wishlist ->
            startActivity(Intent(this, WishlistDetailActivity::class.java).apply {
                putExtra(com.lenilestari.aethersea.util.Constants.EXTRA_WISHLIST_ID, wishlist.id)
            })
        }

        binding.rvWishlists.layoutManager = LinearLayoutManager(this)
        binding.rvWishlists.adapter = adapter

        binding.btnTambahWishlist.setOnClickListener {
            startActivity(Intent(this, AddWishlistActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener { loadData(forceRefresh = true) }
        binding.btnRetry.setOnClickListener { loadData() }

        setupBottomNav()
        shimmerShownAt = System.currentTimeMillis()
        showShimmerList(binding.shimmerContainer, binding.swipeRefresh)
        loadData()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_wishlist
        if (!isFirstLoad) loadData()
    }

    override fun onStop() {
        super.onStop()
        // Bersihkan SwipeRefresh indicator agar tidak stuck saat kembali
        binding.swipeRefresh.isRefreshing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    private fun loadData(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                if (!forceRefresh && isFirstLoad) {
                    showShimmerList(binding.shimmerContainer, binding.swipeRefresh)
                }
                val wishlists = repo.getAll()

                if (isFirstLoad) {
                    val elapsed = System.currentTimeMillis() - shimmerShownAt
                    val remaining = 300L - elapsed
                    if (remaining > 0) delay(remaining)
                    isFirstLoad = false
                }

                hideShimmerOnly(binding.shimmerContainer)
                binding.swipeRefresh.isRefreshing = false
                adapter.submitList(wishlists)

                binding.tvWishlistCount.text = "${wishlists.size} item"
                val hasData = wishlists.isNotEmpty()
                binding.swipeRefresh.visibility = if (hasData) View.VISIBLE else View.GONE
                binding.layoutEmpty.visibility = if (!hasData) View.VISIBLE else View.GONE
                binding.layoutError.visibility = View.GONE

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                isFirstLoad = false
                hideShimmerOnly(binding.shimmerContainer)
                binding.swipeRefresh.isRefreshing = false
                binding.swipeRefresh.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutError.visibility = View.VISIBLE
            }
        }
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
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetSourcesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_wishlist -> true
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
}
