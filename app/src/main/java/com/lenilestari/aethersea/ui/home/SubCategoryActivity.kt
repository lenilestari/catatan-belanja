package com.lenilestari.aethersea.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.data.model.Category
import com.lenilestari.aethersea.data.repository.CategoryRepository
import com.lenilestari.aethersea.databinding.ActivitySubcategoryBinding
import com.lenilestari.aethersea.ui.adapters.SubCategoryAdapter
import com.lenilestari.aethersea.ui.input.InputActivity
import com.lenilestari.aethersea.util.Constants
import kotlinx.coroutines.launch

class SubCategoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubcategoryBinding
    private lateinit var adapter: SubCategoryAdapter
    private lateinit var categoryRepo: CategoryRepository
    private var selectedSub: Category? = null
    private var mainCategoryId = ""
    private var mainCategoryName = ""
    private var mainCategoryIcon = ""
    private var selectedDate = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubcategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        categoryRepo = CategoryRepository(userId)

        mainCategoryId = intent.getStringExtra(Constants.EXTRA_CATEGORY_ID) ?: ""
        mainCategoryName = intent.getStringExtra(Constants.EXTRA_CATEGORY_NAME) ?: ""
        mainCategoryIcon = intent.getStringExtra(Constants.EXTRA_CATEGORY_ICON) ?: ""
        selectedDate = intent.getLongExtra(Constants.EXTRA_SELECTED_DATE, System.currentTimeMillis())

        binding.tvCategoryIcon.text = mainCategoryIcon
        binding.tvCategoryName.text = mainCategoryName
        binding.btnBack.setOnClickListener { finish() }
        binding.btnTambahSub.setOnClickListener { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }

        adapter = SubCategoryAdapter { sub -> selectedSub = sub }
        binding.rvSubCategories.apply {
            layoutManager = GridLayoutManager(this@SubCategoryActivity, 2)
            adapter = this@SubCategoryActivity.adapter
        }

        binding.btnLanjutInput.setOnClickListener {
            val sub = selectedSub
            if (sub == null) { Toast.makeText(this, "Pilih sub-kategori dulu", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, InputActivity::class.java).apply {
                putExtra(Constants.EXTRA_CATEGORY_ID, mainCategoryId)
                putExtra(Constants.EXTRA_CATEGORY_NAME, mainCategoryName)
                putExtra(Constants.EXTRA_SUB_CATEGORY_ID, sub.id)
                putExtra(Constants.EXTRA_SUB_CATEGORY_NAME, sub.name)
                putExtra(Constants.EXTRA_SELECTED_DATE, selectedDate)
            })
        }

        lifecycleScope.launch {
            val subs = categoryRepo.getSubCategories(mainCategoryId)
            adapter.submitList(subs)
        }
    }
}
