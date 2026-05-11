package com.lenilestari.aethersea.ui.input

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.data.model.ShoppingItem
import com.lenilestari.aethersea.databinding.ActivityInputBinding
import com.lenilestari.aethersea.processor.VoiceBatchManager
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import java.util.Locale

class InputActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInputBinding
    private var isVoiceMode = true
    private var isRecording = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var recordingTimer: CountDownTimer? = null
    private var recordingSeconds = 0
    private var mainCategoryId = ""
    private var mainCategoryName = ""
    private var subCategoryId = ""
    private var subCategoryName = ""
    private var selectedDate = System.currentTimeMillis()
    private var currentPriceRaw = 0L

    companion object { private const val RC_AUDIO = 101 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainCategoryId = intent.getStringExtra(Constants.EXTRA_CATEGORY_ID) ?: ""
        mainCategoryName = intent.getStringExtra(Constants.EXTRA_CATEGORY_NAME) ?: ""
        subCategoryId = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_ID) ?: ""
        subCategoryName = intent.getStringExtra(Constants.EXTRA_SUB_CATEGORY_NAME) ?: mainCategoryName
        selectedDate = intent.getLongExtra(Constants.EXTRA_SELECTED_DATE, System.currentTimeMillis())

        val badgeText = if (subCategoryName.isNotEmpty()) subCategoryName else mainCategoryName
        binding.tvCategoryBadge.text = badgeText

        setupUnitSpinner()
        setupTabs()
        setupVoice()
        setupManual()
        setupFooter()
        updateInputCount()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabVoice.setOnClickListener { switchTab(true) }
        binding.tabManual.setOnClickListener { switchTab(false) }
    }

    private fun switchTab(voice: Boolean) {
        isVoiceMode = voice
        binding.layoutVoice.visibility = if (voice) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutManual.visibility = if (voice) android.view.View.GONE else android.view.View.VISIBLE
        binding.tabVoice.setBackgroundResource(if (voice) R.drawable.bg_tab_active else android.R.color.transparent)
        binding.tabManual.setBackgroundResource(if (!voice) R.drawable.bg_tab_active else android.R.color.transparent)
    }

    private fun setupUnitSpinner() {
        val units = resources.getStringArray(R.array.satuan_array)
        binding.spinnerUnit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupVoice() {
        binding.btnMic.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        binding.btnUlang.setOnClickListener {
            binding.etVoiceResult.setText("")
            if (isRecording) stopRecording()
        }

        binding.btnTambah.setOnClickListener {
            val text = binding.etVoiceResult.text.toString().trim()
            if (text.isEmpty()) { Toast.makeText(this, "Belum ada teks", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            VoiceBatchManager.addVoice(text)
            binding.etVoiceResult.setText("")
            updateInputCount()
            Toast.makeText(this, "Ditambahkan ke list", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupManual() {
        binding.etPrice.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true
                val raw = s.toString().replace(".", "").toLongOrNull() ?: 0L
                currentPriceRaw = raw
                if (raw > 0) {
                    val formatted = "%,d".format(raw).replace(",", ".")
                    binding.etPrice.setText(formatted)
                    binding.etPrice.setSelection(formatted.length)
                }
                isUpdating = false
            }
        })

        fun addAmount(amount: Long) {
            currentPriceRaw += amount
            val formatted = "%,d".format(currentPriceRaw).replace(",", ".")
            binding.etPrice.setText(formatted)
            binding.etPrice.setSelection(formatted.length)
        }

        binding.chip5k.setOnClickListener { addAmount(5000L) }
        binding.chip10k.setOnClickListener { addAmount(10000L) }
        binding.chip20k.setOnClickListener { addAmount(20000L) }
        binding.chip50k.setOnClickListener { addAmount(50000L) }

        binding.btnTambahManual.setOnClickListener {
            val name = binding.etItemName.text.toString().trim()
            val qtyStr = binding.etQty.text.toString().trim()
            val qty = qtyStr.toDoubleOrNull() ?: 1.0
            val unit = binding.spinnerUnit.selectedItem.toString()
            val price = if (qty > 0) currentPriceRaw.toDouble() / qty else 0.0

            if (name.isEmpty()) { Toast.makeText(this, "Nama barang harus diisi", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (currentPriceRaw == 0L) { Toast.makeText(this, "Harga harus diisi", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            binding.btnTambahManual.isClickable = false
            VoiceBatchManager.addManual(ShoppingItem(
                item = name, qty = qty, unit = unit,
                price = price, total = currentPriceRaw.toDouble(),
                source = "manual"
            ))
            binding.etItemName.setText("")
            binding.etQty.setText("")
            binding.etPrice.setText("")
            currentPriceRaw = 0L
            updateInputCount()
            Toast.makeText(this, "✓ Ditambahkan ke list", Toast.LENGTH_SHORT).show()
            binding.btnTambahManual.isClickable = true
        }
    }

    private fun setupFooter() {
        binding.btnLihatList.setOnClickListener {
            if (VoiceBatchManager.count == 0) { Toast.makeText(this, "Tambahkan minimal 1 item dulu", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, BatchListActivity::class.java).apply {
                putExtra(Constants.EXTRA_CATEGORY_ID, mainCategoryId)
                putExtra(Constants.EXTRA_CATEGORY_NAME, mainCategoryName)
                putExtra(Constants.EXTRA_SUB_CATEGORY_ID, subCategoryId)
                putExtra(Constants.EXTRA_SUB_CATEGORY_NAME, subCategoryName)
                putExtra(Constants.EXTRA_SELECTED_DATE, selectedDate)
            })
        }
    }

    private fun startRecording() {
        // BUG-11: cek ketersediaan speech recognizer sebelum mulai
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Fitur suara tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO)
            return
        }
        isRecording = true
        recordingSeconds = 0
        binding.btnMic.setBackgroundResource(R.drawable.bg_recording_circle)
        binding.tvRecordStatus.text = "Tap mic untuk berhenti"

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                stopRecording()
                // BUG-20: bedakan jenis error untuk pesan yang lebih informatif
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Suara tidak dikenali, coba ucapkan lebih jelas"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tidak ada koneksi internet untuk mengenali suara"
                    SpeechRecognizer.ERROR_AUDIO -> "Masalah pada mikrofon, coba lagi"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdeteksi, tap mic dan mulai bicara"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenalan suara sedang sibuk, coba lagi"
                    else -> "Tidak dapat mengenali suara (kode: $error)"
                }
                Toast.makeText(this@InputActivity, msg, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                binding.etVoiceResult.setText(text)
                stopRecording()
            }
            override fun onPartialResults(partial: Bundle?) {
                val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) binding.etVoiceResult.setText(text)
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)

        // BUG-24: ganti Long.MAX_VALUE dengan batas realistis 120 detik
        recordingTimer = object : CountDownTimer(120_000L, 1000) {
            override fun onTick(ms: Long) {
                recordingSeconds++
                val min = recordingSeconds / 60
                val sec = recordingSeconds % 60
                binding.tvRecordStatus.text = "● Merekam... ${String.format(Locale.getDefault(), "%d:%02d", min, sec)}"
            }
            override fun onFinish() {
                stopRecording()
                Toast.makeText(this@InputActivity, "Batas waktu perekaman 2 menit tercapai", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun stopRecording() {
        isRecording = false
        recordingTimer?.cancel()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        binding.btnMic.setBackgroundResource(R.drawable.bg_mic_idle)
        binding.tvRecordStatus.text = "Tap mic untuk mulai"
    }

    private fun updateInputCount() {
        binding.tvInputCount.text = "${VoiceBatchManager.count} input ditambahkan"
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, gr: IntArray) {
        super.onRequestPermissionsResult(rc, p, gr)
        if (rc == RC_AUDIO && gr.isNotEmpty() && gr[0] == PackageManager.PERMISSION_GRANTED) startRecording()
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}
