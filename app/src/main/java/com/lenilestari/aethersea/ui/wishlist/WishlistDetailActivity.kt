package com.lenilestari.aethersea.ui.wishlist

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lenilestari.aethersea.util.Constants

class WishlistDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wishlistId = intent.getStringExtra(Constants.EXTRA_WISHLIST_ID) ?: run { finish(); return }
        startActivity(Intent(this, AddWishlistActivity::class.java).apply {
            putExtra(Constants.EXTRA_WISHLIST_ID, wishlistId)
        })
        finish()
    }
}
