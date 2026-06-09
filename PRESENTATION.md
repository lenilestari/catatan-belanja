# AETHERSEA — Presentasi Portfolio
> File ini berisi konten slide per slide (untuk PPT/Canva) + caption LinkedIn.
> Setiap bagian `## Slide N` = 1 halaman presentasi.

---

## Slide 1 — COVER

**Judul Besar:**
# AETHERSEA
**Subtitle:**
Smart Finance Tracker — Android App

**Tagline:**
> Catat belanja dengan suara, analisis keuangan dengan AI.

**Visual suggestion:** Logo Aethersea di tengah, background gelap navy (#1A1A2E), teks teal/putih.

---

## Slide 2 — LATAR BELAKANG MASALAH

**Judul:** Masalah yang Ingin Dipecahkan

**Poin-poin:**
- Banyak orang lupa mencatat pengeluaran karena prosesnya yang ribet
- Aplikasi keuangan yang ada terlalu kompleks atau berbayar
- Tidak ada cara cepat mencatat belanja saat tangan penuh di toko
- Laporan keuangan bulanan sulit dibuat secara manual

**Visual suggestion:** Ikon handphone + ikon dompet + tanda tanya, warna merah/oranye.

---

## Slide 3 — SOLUSI

**Judul:** Aethersea Hadir Sebagai Solusinya

**3 Pilar Utama:**

| 🎙️ Input Cepat | 🤖 AI Parsing | 📊 Laporan Otomatis |
|---|---|---|
| Cukup ucapkan "beli beras 2 kg 15 ribu" | Gemini AI mengurai item, harga, dan jumlah | Export Excel lengkap langsung ke Downloads |

**Visual suggestion:** Flow 3 langkah dengan ikon besar, background putih bersih.

---

## Slide 4 — TECH STACK

**Judul:** Dibangun dengan Teknologi Modern

```
Language      : Kotlin
UI            : XML Views + ViewBinding
Auth          : Firebase Authentication (Google Sign-In)
Database      : Cloud Firestore (Named Database: aethersea)
AI Engine     : Gemini 2.0 Flash REST API
Voice Input   : Android SpeechRecognizer API
Export        : fastexcel (XLSX) + MediaStore API
Image Loading : Coil
HTTP Client   : OkHttp
```

**Highlight:**
- Min SDK 29 (Android 10+), Target SDK 36
- Offline-first: Firestore persistent cache 100MB
- Arsitektur: Repository pattern + Coroutines

**Visual suggestion:** Grid ikon teknologi (Firebase, Kotlin, Google AI), background gelap.

---

## Slide 5 — FITUR UTAMA (1/2)

**Judul:** Fitur Unggulan — Input & AI

**🎙️ Voice Input + AI Parsing**
- Pengguna berbicara → SpeechRecognizer transkripsi → Gemini AI parse
- AI mengenali: nama barang, jumlah, satuan, harga per item
- Fallback ke fuzzy matching lokal jika offline (Levenshtein distance)
- Hasil tampil sebagai daftar item yang bisa diedit sebelum disimpan

**🧠 Kamus Pintar Lokal**
- 700+ istilah produk & unit dalam kamus bawaan
- TextNormalizer: normalisasi ejaan, singkatan, angka teks ("dua ratus ribu")
- KnowledgeCache: update kamus dari Firestore tanpa update app

**Visual suggestion:** Screenshot mockup layar input suara + hasil parse.

---

## Slide 6 — FITUR UTAMA (2/2)

**Judul:** Fitur Unggulan — Keuangan & Laporan

**💰 Budget Tracking**
- Input sumber budget per bulan (gaji, transferan, dll.)
- Carry-over otomatis sisa bulan lalu ke bulan berikutnya
- Notifikasi visual ketika budget mendekati/melewati limit

**📊 Financial Summary Report (Excel)**
- 4 sheet: Ringkasan, Per Bulan, Per Kategori, Hari Tertinggi
- 10 KPI metrics + Unicode bar chart trend
- 7 AI-generated insights berbasis aturan (tanpa network call)
- Desain fintech gelap: navy + teal + green/red

**🗂️ Fitur Lain:**
- Wishlist tracker dengan kalkulator tabungan
- Auto-cleanup data 90 hari (export otomatis sebelum hapus)
- Riwayat sesi belanja dengan filter bulan

**Visual suggestion:** Mockup layar history + preview file Excel.

---

## Slide 7 — ARSITEKTUR

**Judul:** Arsitektur Aplikasi

```
┌─────────────────────────────────────────┐
│              UI Layer                   │
│  Activities + ViewBinding + Adapters    │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│           Repository Layer              │
│  SessionRepo / MonthlyBudgetRepo / ...  │
│  FirestoreInstance (Named DB: aethersea)│
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│           Data Layer                   │
│  Cloud Firestore + Local Cache 100MB   │
└─────────────────────────────────────────┘

     AI Layer (Parallel)
┌────────────────────────────────────────┐
│  GeminiClient → REST API → Parse Model │
│  KnowledgeCache + KamusSeeder (fallback│
└────────────────────────────────────────┘
```

**Key Decisions:**
- `FirestoreInstance` singleton → satu sumber koneksi ke named database
- Semua repo gunakan `Source.CACHE` dulu → fallback ke server
- Coroutines + `withTimeoutOrNull` untuk semua operasi jaringan

**Visual suggestion:** Diagram kotak berwarna (UI=teal, Repo=navy, Data=abu).

---

## Slide 8 — TANTANGAN TEKNIS

**Judul:** Tantangan & Solusi

| Tantangan | Solusi |
|---|---|
| Firebase named database selalu salah (ke `(default)`) | Buat `FirestoreInstance` singleton dengan `getInstance(app, "aethersea")` |
| AI parse gagal saat offline | Fallback ke KnowledgeCache + Levenshtein fuzzy match lokal |
| Seeder kamus diam-diam skip saat timeout | Ubah timeout = false (bukan success), strict server ACK |
| Excel export lambat di main thread | Pindahkan seluruh write ke `Dispatchers.IO` + MediaStore IS_PENDING |
| Kotlin `object` init order crash | Pindahkan `DEFAULT_*` vals ke baris pertama object body |
| Double navigate saat tombol diklik cepat | Debounce 500ms via `setDebounceClickListener()` extension |

**Visual suggestion:** Tabel 2 kolom dengan ikon ⚠️ dan ✅.

---

## Slide 9 — HIGHLIGHT ENGINEERING

**Judul:** Yang Membuat Proyek Ini Berbeda

**1. Offline-First Architecture**
> App tetap bisa input & baca data tanpa internet. Data tersimpan di cache Firestore 100MB, sync otomatis saat online.

**2. AI tanpa Ketergantungan Network untuk Insights**
> Laporan keuangan dengan 7 insight cerdas dihasilkan murni dari Kotlin (rule-based), nol network call. Gemini AI hanya dipakai saat input suara.

**3. Premium Report Export**
> File Excel yang dihasilkan bukan sekadar tabel biasa — ada bar chart Unicode, warna KPI adaptif (hijau/merah/oranye sesuai kondisi budget), dan halaman AI insights.

**Visual suggestion:** Potongan file Excel dengan warna gelap + highlight angka-angka penting.

---

## Slide 10 — STATISTIK PROYEK

**Judul:** Fakta & Angka

| Metric | Nilai |
|---|---|
| Total file Kotlin | 40+ file |
| Fitur utama | 8 fitur |
| Min Android version | Android 10 (API 29) |
| AI model | Gemini 2.0 Flash |
| Ukuran kamus lokal | 700+ istilah |
| Sheet Excel yang digenerate | 4 sheet |
| KPI metrics di laporan | 10 KPI |
| Pattern yang dipakai | Repository + Coroutines + ViewBinding |

**Visual suggestion:** Angka-angka besar dengan highlight warna teal, background gelap.

---

## Slide 11 — PENUTUP / CTA

**Judul:** Terima Kasih

**Teks:**
> Aethersea adalah bukti bahwa mencatat keuangan bisa semudah berbicara.

**Kontak / Link:**
- 🔗 GitHub: [link repo kamu]
- 💼 LinkedIn: [profil kamu]
- 📧 Email: lenilestari107@gmail.com

**Tagline akhir:**
> Built with Kotlin · Firebase · Gemini AI · Passion

**Visual suggestion:** Background navy gelap, logo di tengah, teks putih/teal.

---
---

# CAPTION LINKEDIN

> Copy bagian ini sebagai teks post LinkedIn. Boleh dipotong sesuai kebutuhan.

---

🚀 **Memperkenalkan Aethersea — Smart Finance Tracker yang saya bangun dari nol!**

Selama ini saya sering lupa mencatat pengeluaran karena prosesnya melelahkan. Jadi saya putuskan untuk membangun solusinya sendiri.

**Aethersea** adalah aplikasi Android yang memungkinkan kamu mencatat belanja hanya dengan berbicara 🎙️ — AI akan mengurai item, harga, dan jumlah secara otomatis.

---

🛠️ **Tech stack yang digunakan:**
- **Kotlin** + XML Views + ViewBinding
- **Firebase** Auth & Firestore (offline-first, 100MB cache)
- **Gemini 2.0 Flash** untuk AI parsing suara
- **Android SpeechRecognizer** untuk voice input
- **fastexcel** untuk export laporan Excel premium
- **Repository pattern** + Coroutines

---

✨ **Fitur yang sudah dibangun:**
✅ Input belanja via suara → AI parse otomatis
✅ Budget tracking bulanan dengan carry-over
✅ Laporan keuangan Excel 4 sheet + 10 KPI metrics
✅ 7 AI insights otomatis tanpa network call
✅ Wishlist tracker dengan kalkulator tabungan
✅ Auto-cleanup data 90 hari (export dulu, baru hapus)
✅ Offline-first — tetap bisa pakai tanpa internet

---

🧠 **Yang paling challenging:**
Salah satu tantangan terbesar adalah Firebase Named Database — `getInstance()` default selalu mengarah ke database yang salah. Solusinya: buat singleton `FirestoreInstance` yang secara eksplisit mengarah ke named database yang benar. Detail kecil, tapi kritikal untuk production.

---

💡 **Yang saya pelajari:**
Membangun app dari nol memaksa saya untuk memikirkan setiap layer — dari UX micro-interaction (debounce klik, shimmer anti-flicker) sampai arsitektur data (offline cache strategy, seeder reliability).

Ini bukan sekadar latihan coding — ini adalah latihan berpikir seperti seorang product engineer.

---

Saya terbuka untuk diskusi tentang Android development, Firebase architecture, atau AI integration! 👇

#AndroidDev #Kotlin #Firebase #GeminiAI #MobileApp #PortfolioProject #SoftwareEngineering #FinTech

---
*Gunakan slide dari presentasi sebagai carousel LinkedIn (upload gambar slide 1 s/d 10 sebagai carousel post).*
