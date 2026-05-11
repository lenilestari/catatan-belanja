package com.lenilestari.aethersea.ui.input

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lenilestari.aethersea.databinding.ActivityBatchListBinding
import com.lenilestari.aethersea.processor.VoiceBatchManager
import com.lenilestari.aethersea.ui.adapters.BatchInputAdapter
import com.lenilestari.aethersea.ui.result.AiLoadingActivity
import com.lenilestari.aethersea.ui.result.ResultActivity
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.setBtnLoading

class BatchListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchListBinding
    private lateinit var adapter: BatchInputAdapter
    private var mainCategoryId = ""
    private var mainCategoryName = ""
    private var subCategoryId = ""
    private var subCategoryName = ""
    private var selectedDate = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainCategoryId = intent.getStringExtra(Constants.EXTRA_CATEGORY_ID) ?: ""
        mainCategoryName = intent.getStringExtra(Constants.EXTRA_CATEGORY_NAME) ?: ""
        subCategoryId = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_ID) ?: ""
        subCategoryName = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_NAME) ?: ""
        selectedDate = intent.getLongExtra(Constants.EXTRA_SELECTED_DATE, System.currentTimeMillis())

        adapter = BatchInputAdapter { item ->
            VoiceBatchManager.remove(item.id)
            refreshList()
        }

        binding.rvBatchItems.apply {
            layoutManager = LinearLayoutManager(this@BatchListActivity)
            adapter = this@BatchListActivity.adapter
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRekamLagi.setOnClickListener { finish() }

        binding.btnSelesai.setOnClickListener {
            // BUG-12: disable dulu untuk cegah double-tap, reset di onResume
            binding.btnSelesai.isEnabled = false
            val extras = Intent().apply {
                putExtra(Constants.EXTRA_CATEGORY_ID, mainCategoryId)
                putExtra(Constants.EXTRA_CATEGORY_NAME, mainCategoryName)
                putExtra(Constants.EXTRA_SUB_CATEGORY_ID, subCategoryId)
                putExtra(Constants.EXTRA_SUB_CATEGORY_NAME, subCategoryName)
                putExtra(Constants.EXTRA_SELECTED_DATE, selectedDate)
            }
            if (VoiceBatchManager.hasVoiceInputs) {
                startActivity(Intent(this, AiLoadingActivity::class.java).putExtras(extras))
            } else {
                startActivity(Intent(this, ResultActivity::class.java).putExtras(extras))
            }
        }

        if (VoiceBatchManager.isEmpty) {
            finish(); return
        }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        binding.btnSelesai.isEnabled = true
        refreshList()
    }

    private fun refreshList() {
        val items = VoiceBatchManager.items
        adapter.submitList(items)
        binding.tvSubtitle.text = "${items.size} input · belum diproses"
    }
}
