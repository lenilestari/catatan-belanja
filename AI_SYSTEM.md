# Sistem AI Aethersea — Dokumentasi Teknis

> Dokumen ini mencakup seluruh pipeline AI: parsing input belanja, kamus learning,
> fallback logic, Firestore schema, dan panduan debug.

---

## Daftar Isi

1. [Gambaran Umum](#1-gambaran-umum)
2. [Pipeline Input Parsing](#2-pipeline-input-parsing)
3. [Pipeline AI Parsing (GeminiClient)](#3-pipeline-ai-parsing-geminiclient)
4. [Pipeline Kamus Learning](#4-pipeline-kamus-learning)
5. [LocalDictionary](#5-localdictionary)
6. [Data Models](#6-data-models)
7. [Konfigurasi & Constants](#7-konfigurasi--constants)
8. [Firestore Schema](#8-firestore-schema)
9. [Panduan Debug](#9-panduan-debug)
10. [Masalah Umum & Solusi](#10-masalah-umum--solusi)

---

## 1. Gambaran Umum

```
Input user (voice/teks)
        │
        ▼
 TextNormalizer          ← normalisasi kata angka, ribu/juta, unit alias
        │
        ▼
  InputParser            ← ekstrak nama/qty/harga (Kotlin, kasus sederhana)
        │
        ▼
VoiceBatchManager        ← kumpulkan semua input dalam 1 sesi
        │
        ▼
  GeminiClient           ← kirim ke Gemini → parse JSON → ShoppingItem[]
  (parseShoppingInput)      fallback: Claude → Kotlin parser
        │
        ▼
  Hasil ditampilkan ke user
        │
        ▼ (background, fire-and-forget)
KamusLearningManager     ← cek item yang belum dikenal
        │
        ▼
  GeminiClient           ← analisis: item baru? alias? unit baru?
  (analyzeLearning)
        │
        ▼
LearningRepository       ← upsert ke Firestore learning_candidates
                            promote ke kamus jika count ≥ threshold
```

**Split tanggung jawab:**
- **Kotlin (20%)** — normalisasi teks, filter noise, Levenshtein pre-check
- **AI/Gemini (80%)** — klasifikasi item, koreksi typo, angka kompleks, learning

---

## 2. Pipeline Input Parsing

### 2.1 TextNormalizer
**File:** `processor/TextNormalizer.kt`  
**TAG Logcat:** `TextNorm`

Menormalisasi teks mentah sebelum parsing angka.

| Langkah | Contoh input | Contoh output |
|---------|-------------|---------------|
| Kata angka | `"dua belas"` | `"12"` |
| Multiplier | `"50 ribu"` | `"50000"` |
| Unit alias | `"kilo"` | `"kg"`, `"ltr"` → `"liter"` |
| Extra spaces | `"tomat  2  buah"` | `"tomat 2 buah"` |

**Kata angka yang dikenali:**
```
nol→0, satu→1, dua→2, tiga→3, empat→4, lima→5, enam→6,
tujuh→7, delapan→8, sembilan→9, sepuluh→10, sebelas→11,
dua belas→12, dua puluh→20, tiga puluh→30, empat puluh→40,
lima puluh→50, setengah→0.5, seperempat→0.25, selusin→12
```

**Multiplier yang dikenali:**
```
ribu/rb/rbu → ×1.000
juta/jt     → ×1.000.000
miliar      → ×1.000.000.000
```

**Catatan:** `TextNormalizer` TIDAK menangani `ratus` (itu tugas `InputParser.expandRatus`).

---

### 2.2 InputParser
**File:** `processor/InputParser.kt`  
**TAG Logcat:** `InputParser`

Pre-processor Kotlin untuk input teks/suara. Menangani kasus sederhana; kasus kompleks diserahkan ke Gemini.

**8 langkah parse (semua dilog):**

```
[0] raw input        "tomat 2 50.000"
[1] after normalize  "tomat 2 50000"    ← TextNormalizer
[2] after expandRatus                   ← "dua ratus" → "200"
[3] after stripNoise                    ← hapus: eh/yang/itu/beli/dll
[4] after mergeDigits                   ← "5 0 0 0" → "5000" (voice artifact)
[5] numbers found    ["2", "50000"]
[6] nama extracted   "tomat"
[7] numbers (int)    [2, 50000]
[8] RESULT           nama="tomat" qty=2 harga=50000
```

**Aturan qty/harga:**
- 0 angka → `qty=1, harga=0`
- 1 angka → `qty=1, harga=angka[0]`
- 2+ angka → `qty=angka[-2], harga=angka[-1]` (angka terakhir = harga)

**Noise words yang dihapus:**
```
eh, yang, itu, ya, dong, beli, mau, tolong, seharga, dengan, harga,
bayar, rupiah, rp, nih, deh, si, tuh, juga, lagi, tadi, ini, aja,
minta, kasih, ambil, cari
```

**Format angka Indonesia:**
- Titik = ribuan separator: `50.000` → `50000`
- Koma dihapus: `50,000` → `50000`

---

### 2.3 VoiceBatchManager
**File:** `processor/VoiceBatchManager.kt`

In-memory batch manager untuk 1 sesi input.

| Method | Fungsi |
|--------|--------|
| `addVoice(rawText)` | Tambah input suara mentah |
| `addManual(item)` | Tambah item manual (sudah parsed) |
| `buildGeminiInput()` | Gabungkan semua voice input → 1 string untuk Gemini |
| `getManualItems()` | Ambil item manual (bypass Gemini) |
| `clear()` | Reset setelah sesi selesai |

Input manual **tidak dikirim ke Gemini** — langsung dipakai apa adanya.  
Input voice dikumpulkan, dinormalisasi via `TextNormalizer`, lalu dikirim ke Gemini sebagai 1 batch.

---

## 3. Pipeline AI Parsing (GeminiClient)

**File:** `remote/GeminiClient.kt`  
**TAG Logcat:** `AiClient`  
**Entry point:** `GeminiClient.parseShoppingInput(rawInput)`

### 3.1 Fallback Chain

```
Gemini gemini-2.0-flash-lite  [key1, key2, key3]
    ↓ 429/503 = coba key berikutnya
    ↓ 404/400 = skip ke model berikutnya
Gemini gemini-2.0-flash        [key1, key2, key3]
Gemini gemini-2.0-flash-exp    [key1, key2, key3]
Gemini gemini-2.5-flash-preview-05-20
Gemini gemini-1.5-flash-8b
Gemini gemini-1.5-flash
Gemini gemini-1.5-pro
Gemini gemini-1.0-pro
    ↓ semua gagal
Claude claude-haiku-4-5-20251001
    ↓ gagal
Kotlin Parser (no AI, confidence="low")
```

### 3.2 System Prompt (Parsing)

```
Parser belanja Indonesia. Output HANYA JSON valid, tanpa teks lain:
{"items":[{"item":"string","qty":number,"unit":"string","price":number,
           "total":number,"confidence":"high|low"}],"grand_total":number}

Aturan:
- "ribu"/"rb"=×1000, "jt"/"juta"=×1000000
- kilo→kg, liter→liter, gram→gram
- angka teks→numerik
- pisah item per "dan","sama",",","lalu","terus","juga"
- "per unit"=harga satuan, tanpa "per"=harga total
- total=qty×price atau price
- koreksi typo ke ejaan baku
- jika ragu confidence="low"
```

### 3.3 Response Schema (Gemini structured output)

```json
{
  "items": [
    {
      "item": "string",
      "qty": number,
      "unit": "string",
      "price": number,
      "total": number,
      "confidence": "high" | "low"
    }
  ],
  "grand_total": number
}
```

### 3.4 Kotlin Parser Fallback

Digunakan saat **semua AI gagal**. Hasil selalu `confidence="low"`.

**Logika:**
1. Split input dengan separator (`dan`, `sama`, `,`, `lalu`, `terus`, `juga`)
2. Per segment: cari harga dengan multiplier (`ribu/rb/k/juta/jt`) → cari angka biasa
3. Nilai terbesar = harga, nilai terkecil = qty
4. Unit dari regex: `kg|kilo|gram|gr|liter|ltr|buah|bungkus|pcs|pack|...`
5. Nama = sisa setelah hapus angka, unit, stopword

---

## 4. Pipeline Kamus Learning

### 4.1 KamusLearningManager
**File:** `processor/KamusLearningManager.kt`  
**TAG Logcat:** `KamusLearn`  
**Entry point:** `KamusLearningManager.analyze(context, parsedItems)`

Dipanggil **fire-and-forget** setelah parsing berhasil. Error ditelan (tidak crash app).

**Alur detail:**

```
parsedItems → ambil semua nama lowercase
      │
      ▼
[1] Filter: sudah ada di kamus? (exact match nama atau alias)
      │
      ├── sudah dikenal → skip
      └── belum dikenal → unknownNames[]
            │
            ▼
[2] Levenshtein pre-check (Kotlin)
      Bandingkan tiap unknownName vs semua kamus item + alias
      similarity = 1 - (levenshtein_distance / max_length)
      ≥ 80% → kirim sebagai "hint alias" ke AI
      < 80% → biarkan AI putuskan sendiri
            │
            ▼
[3] GeminiClient.analyzeLearning(unknownNames, kamusItems, aliasHints)
            │
            ▼
[4] Simpan ke Firestore learning_candidates
      newItems  → upsert "new_item"
      newAliases → upsert "new_alias"
      newUnits  → upsert "new_unit"
            │
            ▼
[5] promoteAboveThreshold(3)
      count ≥ 3 → pindah ke koleksi "kamus"
      count <  3 → tetap di learning_candidates
```

**Threshold:** `PROMOTE_THRESHOLD = 3` (item harus muncul 3× sebelum masuk kamus)  
**Alias similarity:** `ALIAS_SIMILARITY_THRESHOLD = 0.80` (80%)

---

### 4.2 GeminiClient.analyzeLearning
**TAG Logcat:** `AiClient` (bagian `analyzeLearning`)

**System Prompt (Learning):**

```
Kamu adalah sistem analisis kamus belanja Indonesia.
Tentukan apakah setiap item adalah:
1. Item baru yang perlu ditambahkan (new_items)
2. Alias/typo dari item yang sudah ada (new_aliases)
3. Unit pengukuran baru (new_units)

Category: BAHAN_MAKANAN, KEBERSIHAN, PERALATAN, BENSIN, LISTRIK, BANGUNAN

Aturan ketat:
- Kemiripan >80% dengan item kamus → alias, BUKAN item baru
- Jangan tambahkan kata umum: beli, itu, yang, mau, dll
- Hanya kata benda nyata (barang yang bisa dibeli)
- Jika tidak yakin → JANGAN tambahkan (return array kosong)
```

**Response schema:**
```json
{
  "new_items":   [{ "name": "string", "category": "string", "aliases": [], "common_units": [] }],
  "new_aliases": [{ "item_name": "string", "alias": "string" }],
  "new_units":   [{ "original": "string", "normalized": "string" }]
}
```

**User input yang dikirim ke AI:**
```
Item belum dikenali: tomatillo, cengek, santan kara

Kamus yang ada (sebagian, maks 80 item):
- tomat (tomat merah, tom)
- cabai (cabe, lombok)
- ...

Hint similarity Kotlin (>80%):
- 'cengek' mirip 'cabai'
```

---

### 4.3 LearningRepository
**File:** `data/repository/LearningRepository.kt`  
**TAG Logcat:** `LearningRepo`  
**Koleksi Firestore:** `learning_candidates`

**upsert logic:**
```
docId = "item_{name}" | "alias_{alias}" | "unit_{original}"

if doc exists:
    count += 1
    lastSeenMs = now()
else:
    set(LearningCandidate{count=1, ...})
```

**promote logic:**
- `new_item` → buat dokumen baru di koleksi `kamus`
- `new_alias` → update field `aliases[]` di dokumen kamus yang ada
- `new_unit` → (belum dipromosikan ke kamus, hanya dicatat)
- Setelah promote → dokumen di `learning_candidates` dihapus

---

## 5. LocalDictionary

**File:** `processor/LocalDictionary.kt`

Fallback dictionary dari file asset `assets/kamus_items.json` ketika Firestore cache belum terisi.

| Method | Keterangan |
|--------|-----------|
| `findExact(query)` | Cek Firestore cache → fallback asset JSON |
| `findClosest(query, maxDistance=2)` | Levenshtein fuzzy, maks jarak edit 2 |

**Prioritas lookup:**
1. Exact match Firestore (hit cache lokal)
2. Fuzzy match Firestore (Levenshtein ≤ 2)
3. Exact match asset JSON
4. Fuzzy match asset JSON
5. `null` (tidak ditemukan)

---

## 6. Data Models

### ShoppingItem
```kotlin
data class ShoppingItem(
    val item: String,        // nama item, e.g. "Tomat"
    val qty: Double,         // jumlah, e.g. 2.0
    val unit: String,        // satuan, e.g. "kg"
    val price: Double,       // harga satuan
    val total: Double,       // qty × price
    val source: String,      // "voice" | "manual"
    val confidence: String   // "high" | "low"
)
```

### LearningCandidate
```kotlin
data class LearningCandidate(
    val id: String,           // "item_tomat" | "alias_tom" | "unit_kilogram"
    val type: String,         // "new_item" | "new_alias" | "new_unit"
    val itemName: String,     // nama item canonical
    val alias: String,        // (untuk type new_alias)
    val category: String,     // "BAHAN_MAKANAN" dll
    val commonUnits: List<String>,
    val originalUnit: String, // (untuk type new_unit)
    val normalizedUnit: String,
    val count: Int,           // berapa kali ditemui (promote di ≥ 3)
    val lastSeenMs: Long
)
```

### LearningAnalysis (response AI)
```kotlin
data class LearningAnalysis(
    val newItems: List<NewItemSuggestion>,
    val newAliases: List<NewAliasSuggestion>,
    val newUnits: List<NewUnitSuggestion>
)
```

### KamusItem
```kotlin
data class KamusItem(
    val id: String,
    val name: String,           // nama canonical, lowercase
    val aliases: List<String>,  // ["tomat merah", "tom"]
    val commonUnits: List<String>, // ["kg", "buah"]
    val category: String
)
```

### BatchInput
```kotlin
data class BatchInput(
    val id: String,
    val rawText: String,
    val source: String,          // "voice" | "manual"
    val parsedItem: ShoppingItem? // hanya untuk source="manual"
)
```

---

## 7. Konfigurasi & Constants

**File:** `util/Constants.kt`

| Konstanta | Nilai | Keterangan |
|-----------|-------|-----------|
| `GEMINI_API_KEYS` | dari `local.properties` | 3 key, dicoba berurutan |
| `CLAUDE_API_KEY` | dari `local.properties` | fallback jika semua Gemini habis |
| `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/models/` | |
| `CLAUDE_BASE_URL` | `https://api.anthropic.com/v1/messages` | |
| `CLAUDE_MODEL` | `claude-haiku-4-5-20251001` | |
| `GEMINI_FALLBACK_MODELS` | 8 model, urut dari yang tercepat | lihat list di bawah |

**Urutan model Gemini (fallback):**
```
1. gemini-2.0-flash-lite        ← paling cepat & murah, dicoba duluan
2. gemini-2.0-flash
3. gemini-2.0-flash-exp
4. gemini-2.5-flash-preview-05-20
5. gemini-1.5-flash-8b
6. gemini-1.5-flash
7. gemini-1.5-pro
8. gemini-1.0-pro
```

**local.properties (tidak di-commit ke git):**
```properties
GEMINI_API_KEY_1=AIza...
GEMINI_API_KEY_2=AIza...
GEMINI_API_KEY_3=AIza...
CLAUDE_API_KEY=sk-ant-...
```

---

## 8. Firestore Schema

### Koleksi `kamus`
```
kamus/
  {docId = name.replace(" ","_")}/
    id:          string   "tomat"
    name:        string   "tomat"
    category:    string   "BAHAN_MAKANAN"
    aliases:     string[] ["tomat merah", "tom", "tomatillo"]
    commonUnits: string[] ["kg", "buah", "gram"]
```

### Koleksi `learning_candidates`
```
learning_candidates/
  item_{name}/              ← type: new_item
    id:             string  "item_tomatillo"
    type:           string  "new_item"
    itemName:       string  "tomatillo"
    category:       string  "BAHAN_MAKANAN"
    commonUnits:    string[] ["buah"]
    count:          number  2         ← promote saat ≥ 3
    lastSeenMs:     number  1714900000000

  alias_{alias}/            ← type: new_alias
    id:             string  "alias_cengek"
    type:           string  "new_alias"
    itemName:       string  "cabai"   ← canonical name di kamus
    alias:          string  "cengek"
    count:          number  1
    lastSeenMs:     number  ...

  unit_{original}/          ← type: new_unit
    id:             string  "unit_kilogram"
    type:           string  "new_unit"
    originalUnit:   string  "kilogram"
    normalizedUnit: string  "kg"
    count:          number  1
    lastSeenMs:     number  ...
```

### Koleksi `users/{uid}/`
```
users/{uid}/
  displayName: string
  email:       string
  photoUrl:    string   ← Firebase Storage URL
  createdAt:   timestamp
  lastResetAt: timestamp
```

---

## 9. Panduan Debug

### Filter Logcat (Android Studio)

**Filter semua AI sekaligus:**
```
tag:InputParser|TextNorm|KamusLearn|AiClient|LearningRepo
```

**Filter per komponen:**
| Filter | Lihat apa |
|--------|----------|
| `tag:TextNorm` | Normalisasi teks per langkah |
| `tag:InputParser` | Parsing nama/qty/harga step-by-step |
| `tag:AiClient` | Request ke Gemini/Claude, raw response, parsed items |
| `tag:KamusLearn` | Item dikenal vs tidak, skor Levenshtein, hasil AI learning |
| `tag:LearningRepo` | Upsert baru vs increment, promote ke kamus |

---

### Peta Log Lengkap

#### TextNorm (TextNormalizer.kt:44)
```
D TextNorm  normalize IN : "tomat dua kg lima puluh ribu"
D TextNorm    after numberWords  : "tomat 2 kg 50 ribu"
D TextNorm    multiplier hit: "50 ribu" → 50000
D TextNorm    after multipliers  : "tomat 2 kg 50000"
D TextNorm  normalize OUT: "tomat 2 kg 50000"
```

#### InputParser (InputParser.kt:26)
```
D InputParser  ━━━━━━ parse() START ━━━━━━
D InputParser    [0] raw input       : "tomat 2 kg 50.000"
D InputParser    [1] after normalize  : "tomat 2 kg 50000"
D InputParser    [2] after expandRatus: "tomat 2 kg 50000"
D InputParser    [3] after stripNoise : "tomat 2 kg 50000"
D InputParser    [4] after mergeDigits: "tomat 2 kg 50000"
D InputParser    [5] numbers found    : [2, 50000]
D InputParser    [6] nama extracted   : "tomat 2 kg"
D InputParser    [7] numbers (int)    : [2, 50000]
D InputParser    [8] RESULT           : nama="tomat 2 kg" qty=2 harga=50000
D InputParser  ━━━━━━ parse() END ━━━━━━
```

> **Bug umum:** nama masih mengandung angka/unit → lihat step [6]

#### AiClient — Parsing (GeminiClient.kt:53)
```
D AiClient  ════ parseShoppingInput() ════
D AiClient    rawInput: "tomat 2 kg 50 ribu, bawang 1 kg 30 ribu"
D AiClient    → Gemini/gemini-2.0-flash-lite [key1]
D AiClient    [Gemini raw] HTTP 200 body(500): {"candidates":[...
D AiClient    [parseItemsJson] text: {"items":[{"item":"Tomat",...
D AiClient    ✓ Gemini/gemini-2.0-flash-lite [key1] OK → 2 item, grandTotal=80000.0
D AiClient      item[0] item="Tomat" qty=2.0 unit=kg price=25000.0 total=50000.0 conf=high
D AiClient      item[1] item="Bawang" qty=1.0 unit=kg price=30000.0 total=30000.0 conf=high
```

#### AiClient — Learning (GeminiClient.kt:314)
```
D AiClient    ── analyzeLearning ──
D AiClient    unknowns: [tomatillo, cengek]
D AiClient    aliasHints: [(cengek, cabai)]
D AiClient    → learning Gemini/gemini-2.0-flash-lite [key1]
D AiClient    [learning raw] HTTP 200 body(500): ...
D AiClient    [learning text] {"new_items":[{"name":"tomatillo",...
D AiClient    ✓ learning OK: newItems=1 aliases=1 units=0
```

#### KamusLearn (KamusLearningManager.kt:16)
```
D KamusLearn  ════ analyze() START ════
D KamusLearn    parsedItems (3): [Tomat, Bawang, Cengek]
D KamusLearn    kamus size: 142
D KamusLearn    [1] kandidat nama    : [tomat, bawang, cengek]
D KamusLearn    [1] sudah dikenal    : [tomat, bawang]
D KamusLearn    [1] BELUM dikenal    : [cengek]
D KamusLearn      levenshtein 'cengek' ↔ 'cabai' = 33% ✗ bukan alias
D KamusLearn      levenshtein 'cengek' ↔ 'cabe' = 50% ✗ bukan alias
D KamusLearn    [2] alias hints: tidak ada yang mirip (semua < 80%)
D KamusLearn    [3] kirim ke Gemini: [cengek]
D KamusLearn    [3] Gemini response:
D KamusLearn         new_items   (1): [cengek[BAHAN_MAKANAN]]
D KamusLearn         new_aliases (0): []
D KamusLearn         new_units   (0): []
D KamusLearn    [4] upsert selesai
D KamusLearn    [5] promote (threshold=3) selesai
D KamusLearn  ════ analyze() END ════
```

#### LearningRepo (LearningRepository.kt)
```
D LearningRepo    upsert NEW [item_cengek] type=new_item itemName=cengek
D LearningRepo    upsert INCREMENT [item_tomat] count: 2 → 3
D LearningRepo    promoteAboveThreshold(3): 1 kandidat siap dipromosi
D LearningRepo      → promote [new_item] "tomat" count=3
D LearningRepo    PROMOTE item_new → kamus: "tomat" [BAHAN_MAKANAN]
D LearningRepo      → hapus kandidat [item_tomat]
```

---

## 10. Masalah Umum & Solusi

### Nama item salah (mengandung angka/unit)

**Gejala:**
```
InputParser [8] RESULT: nama="tomat 2 kg" qty=2 harga=50000
```

**Penyebab:** `extractName()` di `InputParser.kt:97` mengambil semua teks sebelum angka pertama, tapi kalau ada unit setelah nama, unit ikut masuk.

**Cara debug:** Lihat `InputParser [5] numbers found` dan `[6] nama extracted`. Kalau nama masih ada unit, berarti unit tidak diparsing sebelum nama diambil.

**Solusi:** Gemini akan memperbaiki ini saat parsing selesai — unit dihapus dari nama item di sisi AI.

---

### Qty dan harga kebalik

**Gejala:**
```
InputParser [8] RESULT: nama="bawang" qty=30000 harga=2
```

**Penyebab:** Aturan `InputParser` adalah angka terkecil = qty, tapi input seperti `"bawang 30.000 2 kg"` → angka terakhir = 2 → dijadikan harga.

**Cara debug:** Lihat `InputParser [5] numbers found` urutan kemunculannya.

**Solusi:** Ini kasus yang diserahkan ke Gemini. Kotlin parser hanya untuk pre-processing sederhana.

---

### Gemini selalu gagal / timeout

**Cara debug:**
```
AiClient [Gemini raw] HTTP 429 body(500): {"error":{"code":429,...
```

- HTTP 429 → quota habis → otomatis coba key berikutnya
- HTTP 401 → API key salah → cek `local.properties`
- HTTP 404 → model tidak tersedia di region → fallback ke model berikutnya

---

### Item tidak masuk kamus setelah beberapa kali

**Cara debug:**
```
LearningRepo  upsert INCREMENT [item_cengek] count: 2 → 3
LearningRepo  promoteAboveThreshold(3): 0 kandidat siap dipromosi
```

Kalau count sudah ≥ 3 tapi tidak dipromosi:
- Cek query Firestore: `whereGreaterThanOrEqualTo("count", 3)` — apakah field `count` tersimpan sebagai number (bukan string)?
- Buka Firebase Console → `learning_candidates` → cek tipe field `count`

---

### AI learning tidak berjalan sama sekali

**Cara debug:** Kalau tidak ada log `KamusLearn ════ analyze()` berarti fungsi tidak dipanggil.

Cek di `AiLoadingActivity.kt` apakah ada:
```kotlin
KamusLearningManager.analyze(context, parsedItems)
```

Pastikan dipanggil setelah hasil parsing berhasil ditampilkan.

---

### JSON response AI tidak valid

**Cara debug:**
```
AiClient [parseItemsJson] text: Maaf, saya tidak bisa...
```

Berarti AI mengembalikan teks biasa, bukan JSON. Ini bisa terjadi kalau:
- Model lama (gemini-1.0-pro) tidak support `responseMimeType: application/json`
- Input terlalu panjang melebihi context window

Cek apakah response body mengandung `"finishReason": "MAX_TOKENS"`.

---

## Catatan untuk Pengembangan Selanjutnya

- **Threshold learning** saat ini `3` (hardcoded di `KamusLearningManager.kt:12`). Bisa dijadikan konfigurasi di Firestore remote config.
- **Kamus summary** yang dikirim ke AI dibatasi 80 item pertama (`GeminiClient.kt:320`). Jika kamus sudah besar, pertimbangkan semantic search / category filter dulu.
- **Unit promotion** (`new_unit`) saat ini hanya disimpan di `learning_candidates` tapi tidak ada logika promosi ke tabel unit. Perlu ditambahkan di `LearningRepository.promoteUnit()`.
- **Offline mode**: Saat ini kalau semua AI gagal, Kotlin parser dipakai dengan `confidence="low"`. Pertimbangkan menyimpan pending items untuk di-retry saat online.
