package com.lenilestari.aethersea.util

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.lenilestari.aethersea.R

fun View.startShimmer() {
    startAnimation(AnimationUtils.loadAnimation(context, R.anim.shimmer_fade))
}

fun View.stopShimmer() {
    clearAnimation()
}

fun showShimmerList(shimmer: View, list: View) {
    shimmer.visibility = View.VISIBLE
    list.visibility = View.INVISIBLE
    shimmer.startShimmer()
}

fun hideShimmerList(shimmer: View, list: View) {
    shimmer.stopShimmer()
    shimmer.visibility = View.GONE
    list.visibility = View.VISIBLE
}

fun setBtnLoading(btn: View, tvLabel: TextView, pb: ProgressBar, loading: Boolean) {
    btn.isClickable = !loading
    btn.alpha = if (loading) 0.65f else 1f
    tvLabel.visibility = if (loading) View.GONE else View.VISIBLE
    pb.visibility = if (loading) View.VISIBLE else View.GONE
}

fun showSnackbar(anchor: View, message: String, isError: Boolean = false) {
    val snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
    if (isError) {
        snackbar.setBackgroundTint(ContextCompat.getColor(anchor.context, R.color.danger_text))
        snackbar.setTextColor(ContextCompat.getColor(anchor.context, R.color.white))
    } else {
        snackbar.setBackgroundTint(ContextCompat.getColor(anchor.context, R.color.success_text))
        snackbar.setTextColor(ContextCompat.getColor(anchor.context, R.color.white))
    }
    snackbar.show()
}

// Matikan active indicator ungu Material 3 secara programatik
fun BottomNavigationView.disableActiveIndicator() {
    setItemActiveIndicatorEnabled(false)
}

fun View.setDebounceClickListener(delayMs: Long = 500L, onClick: (View) -> Unit) {
    var lastClickAt = 0L
    setOnClickListener {
        val now = System.currentTimeMillis()
        if (now - lastClickAt > delayMs) {
            lastClickAt = now
            onClick(it)
        }
    }
}
