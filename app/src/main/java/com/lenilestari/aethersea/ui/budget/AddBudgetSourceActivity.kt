package com.lenilestari.aethersea.ui.budget

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.budget.BudgetCalculator
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.databinding.ActivityAddBudgetSourceBinding
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.DateUtils
import com.lenilestari.aethersea.util.setBtnLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Calendar

class AddBudgetSourceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddBudgetSourceBinding
    private lateinit var budgetSourceRepo: BudgetSourceRepository
    private lateinit var budgetCalc: BudgetCalculator
    private var amountRaw = 0L
    private var selectedDateCal: Calendar = Calendar.getInstance()
    private var saveJob: kotlinx.coroutines.Job? = null
    private var forcedPeriod: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBudgetSourceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        budgetSourceRepo = BudgetSourceRepository(userId)
        val sessionRepo = SessionRepository(userId)
        val monthlyRepo = MonthlyBudgetRepository(userId)
        budgetCalc = BudgetCalculator(budgetSourceRepo, sessionRepo, monthlyRepo)

        forcedPeriod = intent.getStringExtra(Constants.EXTRA_PERIOD)
        if (forcedPeriod != null) {
            // Sesuaikan selectedDateCal ke periode yang dikirim dari BudgetSourcesActivity
            val parts = forcedPeriod!!.split("-")
            if (parts.size == 2) {
                val year = parts[0].toIntOrNull() ?: selectedDateCal.get(Calendar.YEAR)
                val month = (parts[1].toIntOrNull() ?: (selectedDateCal.get(Calendar.MONTH) + 1)) - 1
                selectedDateCal.set(year, month, 1)
            }
        }

        updateDateDisplay()
        updateInfoCard()

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            private var updating = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                updating = true
                val raw = s.toString().replace(".", "").toLongOrNull() ?: 0L
                amountRaw = raw
                if (raw > 0) {
                    val fmt = "%,d".format(raw).replace(",", ".")
                    binding.etAmount.setText(fmt); binding.etAmount.setSelection(fmt.length)
                }
                updating = false
            }
        })

        fun addAmount(amount: Long) {
            amountRaw += amount
            val fmt = "%,d".format(amountRaw).replace(",", ".")
            binding.etAmount.setText(fmt); binding.etAmount.setSelection(fmt.length)
        }

        binding.chip500k.setOnClickListener { addAmount(500000L) }
        binding.chip1m.setOnClickListener { addAmount(1000000L) }
        binding.chip2m.setOnClickListener { addAmount(2000000L) }

        binding.layoutDatePicker.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                selectedDateCal.set(y, m, d)
                updateDateDisplay()
                updateInfoCard()
            }, selectedDateCal.get(Calendar.YEAR), selectedDateCal.get(Calendar.MONTH), selectedDateCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnBack.setOnClickListener { cancelAndFinish() }
        binding.btnBatal.setOnClickListener { cancelAndFinish() }
        binding.btnSimpan.setOnClickListener { simpan() }
    }

    private fun updateDateDisplay() {
        binding.tvDate.text = DateUtils.formatDisplay(Timestamp(selectedDateCal.time))
    }

    private fun updateInfoCard() {
        val period = budgetCalc.periodFromTimestamp(Timestamp(selectedDateCal.time))
        val displayMonth = budgetCalc.formatPeriodDisplay(period)
        binding.tvInfo.text = "Budget akan masuk ke bulan $displayMonth sesuai tanggal terima"
    }

    private fun cancelAndFinish() {
        saveJob?.cancel()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveJob?.cancel()
    }

    private fun simpan() {
        val name = binding.etName.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(this, "Nama harus diisi", Toast.LENGTH_SHORT).show(); return }
        if (amountRaw == 0L) { Toast.makeText(this, "Jumlah harus diisi", Toast.LENGTH_SHORT).show(); return }
        if (saveJob?.isActive == true) return

        setBtnLoading(binding.btnSimpan, binding.tvBtnSimpan, binding.pbBtnSimpan, true)
        var didFinish = false
        saveJob = lifecycleScope.launch {
            try {
                val ts = Timestamp(selectedDateCal.time)
                val period = budgetCalc.periodFromTimestamp(ts)
                val source = BudgetSource(
                    name = name,
                    amount = amountRaw,
                    period = period,
                    receivedDate = ts,
                    note = note,
                    createdAt = Timestamp.now()
                )
                val result = budgetSourceRepo.add(source)
                if (result.isSuccess) {
                    // Recalculate in background — must outlive Activity so we use GlobalScope intentionally
                    @OptIn(DelicateCoroutinesApi::class)
                    GlobalScope.launch { runCatching { budgetCalc.recalculateForPeriod(period) } }
                    Toast.makeText(this@AddBudgetSourceActivity, "Budget tersimpan!", Toast.LENGTH_SHORT).show()
                    didFinish = true
                    finish()
                } else {
                    Toast.makeText(this@AddBudgetSourceActivity, "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@AddBudgetSourceActivity, "Error: ${e.message ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
            } finally {
                if (!didFinish) setBtnLoading(binding.btnSimpan, binding.tvBtnSimpan, binding.pbBtnSimpan, false)
            }
        }
    }
}
