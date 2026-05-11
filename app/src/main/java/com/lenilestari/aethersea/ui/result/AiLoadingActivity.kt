package com.lenilestari.aethersea.ui.result

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.lenilestari.aethersea.databinding.ActivityAiLoadingBinding
import com.lenilestari.aethersea.processor.VoiceBatchManager
import com.lenilestari.aethersea.remote.GeminiClient
import com.lenilestari.aethersea.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AiLoadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAiLoadingBinding
    private var aiJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val voiceCount = VoiceBatchManager.items.count { it.source == "voice" }
        binding.tvSubtitle.text = "Menstrukturkan $voiceCount input belanja"

        aiJob = lifecycleScope.launch {
            val rawInput = VoiceBatchManager.buildGeminiInput()
            val result = GeminiClient.parseShoppingInput(rawInput)

            if (result.isSuccess) {
                val geminiResult = result.getOrNull()!!
                val manualItems = VoiceBatchManager.getManualItems()
                val allItems = geminiResult.items + manualItems
                val grandTotal = allItems.sumOf { it.total }
                val json = Gson().toJson(allItems)

                VoiceBatchManager.clear()

                startActivity(Intent(this@AiLoadingActivity, ResultActivity::class.java).apply {
                    putExtras(intent)
                    putExtra(Constants.EXTRA_PARSED_ITEMS_JSON, json)
                    putExtra(Constants.EXTRA_GRAND_TOTAL, grandTotal)
                })
                finish()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("GeminiAI", "Parse failed: $errorMsg")
                binding.tvSubtitle.text = "❌ Gagal memproses. Coba lagi."
                delay(3500)
                finish()
            }
        }
    }

    // BUG-04: Beri user opsi membatalkan proses AI
    override fun onBackPressed() {
        if (aiJob?.isActive == true) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Batalkan pemrosesan?")
                .setMessage("Input yang sudah ditambahkan tetap tersimpan di list.")
                .setPositiveButton("Batalkan") { _, _ ->
                    aiJob?.cancel()
                    finish()
                }
                .setNegativeButton("Tunggu", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiJob?.cancel()
    }
}
