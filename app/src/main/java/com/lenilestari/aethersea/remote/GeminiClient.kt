package com.lenilestari.aethersea.remote

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lenilestari.aethersea.data.model.KamusItem
import com.lenilestari.aethersea.data.model.LearningAnalysis
import com.lenilestari.aethersea.data.model.NewAliasSuggestion
import com.lenilestari.aethersea.data.model.NewItemSuggestion
import com.lenilestari.aethersea.data.model.NewUnitSuggestion
import com.lenilestari.aethersea.data.model.ShoppingItem
import com.lenilestari.aethersea.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "AiClient"

data class GeminiResult(val items: List<ShoppingItem>, val grandTotal: Double)

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val systemPrompt = """
Parser belanja Indonesia. Output HANYA JSON valid, tanpa teks lain:
{"items":[{"item":"string","qty":number,"unit":"string","price":number,"total":number,"confidence":"high|low"}],"grand_total":number}
Aturan: "ribu"/"rb"=×1000,"jt"/"juta"=×1000000; kilo→kg,liter→liter,gram→gram;
angka teks→numerik; pisah item per "dan","sama",",","lalu","terus","juga";
"per unit"=harga satuan,tanpa "per"=harga total; total=qty×price atau price;
koreksi typo ke ejaan baku; jika ragu confidence="low"
""".trimIndent()

    private val geminiResponseSchema = """
{"type":"OBJECT","properties":{"items":{"type":"ARRAY","items":{"type":"OBJECT",
"properties":{"item":{"type":"STRING"},"qty":{"type":"NUMBER"},"unit":{"type":"STRING"},
"price":{"type":"NUMBER"},"total":{"type":"NUMBER"},
"confidence":{"type":"STRING","enum":["high","low"]}},"required":["item","qty","unit","price","total"]}},
"grand_total":{"type":"NUMBER"}},"required":["items","grand_total"]}
""".trimIndent()

    // ── Entry point ──────────────────────────────────────────────────────────

    suspend fun parseShoppingInput(rawInput: String): Result<GeminiResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "════ parseShoppingInput() ════")
        Log.d(TAG, "  rawInput: \"$rawInput\"")

        // ── 1. Kotlin parser (PRIMARY — fast, no network) ──────────────────────
        val kotlinResult = parseWithKotlin(rawInput)
        if (kotlinResult.items.isNotEmpty()) {
            Log.d(TAG, "  ✓ Kotlin parser PRIMARY → ${kotlinResult.items.size} item, total=${kotlinResult.grandTotal}")
            kotlinResult.items.forEachIndexed { i, it ->
                Log.d(TAG, "    item[$i] \"${it.item}\" qty=${it.qty} unit=${it.unit} price=${it.price} total=${it.total}")
            }
            return@withContext Result.success(kotlinResult)
        }
        Log.d(TAG, "  ✗ Kotlin parser kosong → coba AI")

        // ── 2. Gemini (fallback jika Kotlin gagal) ─────────────────────────────
        val keys = Constants.GEMINI_API_KEYS
        for (model in Constants.GEMINI_FALLBACK_MODELS) {
            for ((idx, key) in keys.withIndex()) {
                ensureActive()
                Log.d(TAG, "  → Gemini/$model [key${idx + 1}]")
                val result = tryGemini(rawInput, model, key)
                if (result.isSuccess) {
                    val r = result.getOrNull()!!
                    Log.d(TAG, "  ✓ Gemini/$model [key${idx + 1}] OK → ${r.items.size} item, grandTotal=${r.grandTotal}")
                    r.items.forEachIndexed { i, it ->
                        Log.d(TAG, "    item[$i] item=\"${it.item}\" qty=${it.qty} unit=${it.unit} price=${it.price} total=${it.total} conf=${it.confidence}")
                    }
                    return@withContext result
                }
                val err = result.exceptionOrNull()?.message ?: ""
                val code = extractHttpCode(err)
                Log.w(TAG, "  ✗ Gemini/$model [key${idx + 1}] gagal HTTP=$code → $err")
                when (code) {
                    429, 503 -> continue
                    404, 400 -> break
                    else     -> break
                }
            }
        }

        // ── 3. Claude (fallback terakhir sebelum Kotlin) ───────────────────────
        ensureActive()
        Log.w(TAG, "  → Semua Gemini habis, mencoba Claude...")
        val claudeResult = tryClaude(rawInput)
        if (claudeResult.isSuccess) {
            val r = claudeResult.getOrNull()!!
            Log.d(TAG, "  ✓ Claude OK → ${r.items.size} item, grandTotal=${r.grandTotal}")
            r.items.forEachIndexed { i, it ->
                Log.d(TAG, "    item[$i] item=\"${it.item}\" qty=${it.qty} unit=${it.unit} price=${it.price} total=${it.total}")
            }
            return@withContext claudeResult
        }
        Log.w(TAG, "  ✗ Claude gagal: ${claudeResult.exceptionOrNull()?.message}")

        // ── 4. Kotlin result (last resort, sudah dipanggil di atas tapi kosong) ─
        Log.w(TAG, "  → Return Kotlin result kosong sebagai last resort")
        Result.success(kotlinResult)
    }

    // ── Gemini ──────────────────────────────────────────────────────────────

    private fun tryGemini(input: String, model: String, key: String): Result<GeminiResult> {
        // BUG-03: runCatching menelan CancellationException — ganti dengan try-catch manual
        return try {
            val url = "${Constants.GEMINI_BASE_URL}$model:generateContent?key=$key"
            val body = buildGeminiBody(input)
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .build()
            // BUG-10: response.use{} pastikan koneksi ditutup
            val (code, responseBody) = client.newCall(request).execute().use { response ->
                response.code to (response.body?.string() ?: error("HTTP ${response.code}: empty body"))
            }
            Log.d(TAG, "  [Gemini raw] HTTP $code body(500): ${responseBody.take(500)}")
            if (code !in 200..299) error("HTTP $code: $responseBody")
            Result.success(parseGeminiResponse(responseBody))
        } catch (e: CancellationException) {
            throw e  // wajib re-throw
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildGeminiBody(input: String): JsonObject {
        val root = JsonObject()

        val sysParts = com.google.gson.JsonArray()
        val sysPart = JsonObject(); sysPart.addProperty("text", systemPrompt)
        sysParts.add(sysPart)
        val systemInstruction = JsonObject(); systemInstruction.add("parts", sysParts)
        root.add("systemInstruction", systemInstruction)

        val part = JsonObject(); part.addProperty("text", input)
        val parts = com.google.gson.JsonArray(); parts.add(part)
        val content = JsonObject(); content.addProperty("role", "user"); content.add("parts", parts)
        val contents = com.google.gson.JsonArray(); contents.add(content)
        root.add("contents", contents)

        val genConfig = JsonObject()
        genConfig.addProperty("temperature", 0.1)
        genConfig.addProperty("maxOutputTokens", 1024)
        genConfig.addProperty("responseMimeType", "application/json")
        genConfig.add("responseSchema", JsonParser.parseString(geminiResponseSchema))
        root.add("generationConfig", genConfig)

        return root
    }

    private fun parseGeminiResponse(body: String): GeminiResult {
        // BUG-02: null/size check — kandidat bisa kosong saat safety filter aktif
        val root = JsonParser.parseString(body).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
            ?: error("Gemini: no candidates field")
        if (candidates.size() == 0) error("Gemini: empty candidates (safety filter?)")
        val content = candidates[0].asJsonObject.getAsJsonObject("content")
            ?: error("Gemini: no content in candidate")
        val parts = content.getAsJsonArray("parts")
            ?: error("Gemini: no parts in content")
        if (parts.size() == 0) error("Gemini: empty parts array")
        val text = parts[0].asJsonObject.get("text")?.asString
            ?: error("Gemini: no text in part")
        return parseItemsJson(text)
    }

    // ── Claude ───────────────────────────────────────────────────────────────

    private fun tryClaude(input: String): Result<GeminiResult> {
        return try {
            val root = JsonObject()
            root.addProperty("model", Constants.CLAUDE_MODEL)
            root.addProperty("max_tokens", 1024)
            root.addProperty("system", systemPrompt)

            val userMsg = JsonObject(); userMsg.addProperty("role", "user"); userMsg.addProperty("content", input)
            val messages = com.google.gson.JsonArray(); messages.add(userMsg)
            root.add("messages", messages)

            val request = Request.Builder()
                .url(Constants.CLAUDE_BASE_URL)
                .post(root.toString().toRequestBody(JSON))
                .addHeader("x-api-key", Constants.CLAUDE_API_KEY)
                .addHeader("anthropic-version", Constants.CLAUDE_API_VERSION)
                .addHeader("Content-Type", "application/json")
                .build()

            val (code, responseBody) = client.newCall(request).execute().use { response ->
                response.code to (response.body?.string() ?: error("HTTP ${response.code}: empty body"))
            }
            Log.d(TAG, "  [Claude raw] HTTP $code body(500): ${responseBody.take(500)}")
            if (code !in 200..299) error("HTTP $code: $responseBody")

            val content = JsonParser.parseString(responseBody).asJsonObject
                .getAsJsonArray("content")
            if (content == null || content.size() == 0) error("Claude: empty content array")
            val text = content[0].asJsonObject.get("text")?.asString ?: error("Claude: no text field")
            Log.d(TAG, "  [Claude text] ${text.take(300)}")
            // Claude sering wrap JSON dalam ```json ... ``` — strip dulu
            Result.success(parseItemsJson(extractJson(text)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Kotlin Parser (PRIMARY) ──────────────────────────────────────────────
    //
    // Algoritma: tokenize → kelompokkan berdasarkan pola [qty?] [name] [unit?] [price]
    // Contoh: "terong 100000 sawi 10000" → 2 item terpisah ✓
    //         "2 kg beras 50000"         → qty=2 unit=kg item=beras price=50000 ✓
    //         "beras 2 kg 50000"         → sama seperti di atas ✓

    private val KT_MULT_RE = Regex(
        """(\d+(?:[.,]\d+)?)\s*(ribu|rb|k|jt|juta|miliar)\b""",
        RegexOption.IGNORE_CASE
    )
    private val KT_EXPLICIT_SEP = Regex(
        """,|;|\n|\s+(?:dan|sama|lalu|terus|juga|plus|kemudian)\s+""",
        RegexOption.IGNORE_CASE
    )
    // Indonesian thousands format (\d{1,3}(?:\.\d{3})+) harus dicoba SEBELUM plain number
    // agar "200.000" ditangkap sebagai satu token 200.000, bukan "200" + "000"
    private val KT_TOKEN_RE = Regex("""[a-zA-Z]+|\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?""")

    private val KT_UNIT_MAP = mapOf(
        "kg" to "kg", "kilo" to "kg", "kilogram" to "kg",
        "gram" to "gram", "gr" to "gram", "grm" to "gram",
        "liter" to "liter", "ltr" to "liter", "lt" to "liter", "litre" to "liter",
        "bungkus" to "bungkus", "bks" to "bungkus",
        "botol" to "botol", "btl" to "botol",
        "buah" to "buah", "bh" to "buah", "pcs" to "buah",
        "ikat" to "ikat", "ikt" to "ikat",
        "kaleng" to "kaleng", "klg" to "kaleng",
        "lembar" to "lembar", "lbr" to "lembar",
        "meter" to "meter", "mtr" to "meter",
        "tray" to "tray", "rak" to "tray",
        "porsi" to "porsi", "lusin" to "lusin"
    )

    private val KT_STOP_WORDS = setOf(
        "beli", "harga", "seharga", "per", "dengan", "total",
        "bayar", "toko", "di", "dari", "untuk", "yang", "ini", "itu", "rp"
    )

    private data class KtToken(val text: String, val numValue: Double?, val unitCanon: String?)

    private fun expandMultipliers(input: String): String = KT_MULT_RE.replace(input) { m ->
        val n = m.groupValues[1].replace(",", ".").toDouble()
        val mult = when (m.groupValues[2].lowercase()) {
            "ribu", "rb", "k" -> 1_000.0
            "jt", "juta"      -> 1_000_000.0
            "miliar"          -> 1_000_000_000.0
            else              -> 1.0
        }
        (n * mult).toLong().toString()
    }

    private fun parseIndonesianNumber(t: String): Double? {
        // "200.000" / "1.000.000" / "1.500.000" — titik sebagai pemisah ribuan
        if (Regex("""^\d{1,3}(\.\d{3})+(,\d+)?$""").matches(t)) {
            val normalized = t.replace(".", "").replace(",", ".")
            return normalized.toDoubleOrNull()
        }
        // "1.000,50" — titik ribuan + koma desimal
        if (t.contains(",") && t.contains(".")) {
            return t.replace(".", "").replace(",", ".").toDoubleOrNull()
        }
        // "200,5" — koma sebagai desimal (gaya Indonesia)
        if (t.contains(",")) {
            return t.replace(",", ".").toDoubleOrNull()
        }
        // Angka biasa: "200000" atau "200.5" (US decimal)
        return t.toDoubleOrNull()
    }

    private fun tokenize(input: String): List<KtToken> = KT_TOKEN_RE.findAll(input).map { m ->
        val t = m.value
        val num = parseIndonesianNumber(t)
        if (num != null) KtToken(t, num, null)
        else KtToken(t, null, KT_UNIT_MAP[t.lowercase()])
    }.toList()

    private fun parseWithKotlin(rawInput: String): GeminiResult {
        val expanded = expandMultipliers(rawInput)
        val segments = expanded.split(KT_EXPLICIT_SEP).filter { it.isNotBlank() }
        val allItems = segments.flatMap { parseTokensIntoItems(tokenize(it.trim())) }
        return GeminiResult(allItems, allItems.sumOf { it.total })
    }

    private fun parseTokensIntoItems(tokens: List<KtToken>): List<ShoppingItem> {
        val items = mutableListOf<ShoppingItem>()
        var i = 0

        while (i < tokens.size) {
            val t = tokens[i]

            // Lewati stop words
            if (t.numValue == null && t.unitCanon == null && t.text.lowercase() in KT_STOP_WORDS) {
                i++; continue
            }

            var qty = 1.0
            var unit = "buah"
            val nameWords = mutableListOf<String>()

            // Cek apakah dimulai dengan angka qty (misal "2 baju 30000" atau "2 kg beras 50000")
            if (t.numValue != null) {
                val isQty = t.numValue <= 100 &&
                    tokens.drop(i + 1).any { it.numValue == null && it.unitCanon == null && it.text.lowercase() !in KT_STOP_WORDS } &&
                    tokens.drop(i + 1).any { it.numValue != null && it.numValue >= 500 }
                if (isQty) { qty = t.numValue; i++ } else { i++; continue }
            }

            // Unit opsional setelah qty (misal "2 kg ...")
            if (i < tokens.size && tokens[i].unitCanon != null) {
                unit = tokens[i].unitCanon!!; i++
            }

            // Kumpulkan kata-kata nama item (berhenti saat ketemu angka)
            while (i < tokens.size && tokens[i].numValue == null) {
                val tk = tokens[i]
                when {
                    tk.unitCanon != null -> { unit = tk.unitCanon!!; i++ }
                    tk.text.lowercase() in KT_STOP_WORDS -> i++
                    else -> { nameWords.add(tk.text); i++ }
                }
            }

            if (nameWords.isEmpty()) continue

            // Cek qty opsional SETELAH nama (misal "beras 2 kg 50000")
            if (i < tokens.size && tokens[i].numValue != null && tokens[i].numValue!! <= 100) {
                val peekUnit  = i + 1 < tokens.size && tokens[i + 1].unitCanon != null
                val priceIdx  = i + 1 + if (peekUnit) 1 else 0
                val hasPrice  = priceIdx < tokens.size && tokens[priceIdx].numValue != null && tokens[priceIdx].numValue!! >= 500
                if (hasPrice || peekUnit) {
                    qty = tokens[i].numValue!!; i++
                    if (i < tokens.size && tokens[i].unitCanon != null) { unit = tokens[i].unitCanon!!; i++ }
                }
            }

            // Ambil harga (angka berikutnya)
            if (i < tokens.size && tokens[i].numValue != null) {
                val price = tokens[i].numValue!!; i++
                if (price > 0) {
                    val name = nameWords.joinToString(" ").trim().replaceFirstChar { it.uppercase() }
                    items.add(ShoppingItem(
                        item = name, qty = qty, unit = unit,
                        price = price, total = qty * price,
                        source = "voice", confidence = "low"
                    ))
                }
            }
        }

        return items
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractJson(text: String): String {
        val stripped = text.trim()
        val match = Regex("```(?:json)?\\s*([\\s\\S]+?)```").find(stripped)
        return match?.groupValues?.get(1)?.trim() ?: stripped
    }

    private fun parseItemsJson(text: String): GeminiResult {
        val cleanText = extractJson(text)
        Log.d(TAG, "  [parseItemsJson] text: ${cleanText.take(400)}")
        val parsed    = JsonParser.parseString(cleanText).asJsonObject
        val grandTotal = parsed.get("grand_total")?.asDouble ?: 0.0
        val items = parsed.getAsJsonArray("items")?.map { el ->
            val obj = el.asJsonObject
            ShoppingItem(
                item       = obj.get("item")?.asString ?: "",
                qty        = obj.get("qty")?.asDouble ?: 1.0,
                unit       = obj.get("unit")?.asString ?: "buah",
                price      = obj.get("price")?.asDouble ?: 0.0,
                total      = obj.get("total")?.asDouble ?: 0.0,
                source     = "voice",
                confidence = obj.get("confidence")?.asString ?: "high"
            )
        } ?: emptyList()
        return GeminiResult(items, grandTotal)
    }

    private fun extractHttpCode(message: String?): Int =
        Regex("""HTTP (\d{3})""").find(message ?: "")
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ── Kamus Learning ───────────────────────────────────────────────────────

    private val learningSystemPrompt = """
Kamu adalah sistem analisis kamus belanja Indonesia. Tugasmu menganalisis nama item BELUM ADA di kamus.

Tentukan apakah setiap item adalah:
1. Item baru yang perlu ditambahkan (new_items)
2. Alias/typo dari item yang sudah ada (new_aliases)
3. Unit pengukuran baru (new_units)

Category yang tersedia: BAHAN_MAKANAN, KEBERSIHAN, PERALATAN, BENSIN, LISTRIK, BANGUNAN

Aturan ketat:
- Kemiripan > 80% dengan item kamus → alias, BUKAN item baru
- Jangan tambahkan kata umum: beli, itu, yang, mau, dll
- Hanya kata benda nyata (barang yang bisa dibeli)
- Jika tidak yakin → JANGAN tambahkan (return array kosong)
- Prioritaskan presisi daripada recall

Output HANYA JSON valid tanpa teks lain:
{"new_items":[{"name":"string","category":"string","aliases":[],"common_units":[]}],"new_aliases":[{"item_name":"string","alias":"string"}],"new_units":[{"original":"string","normalized":"string"}]}
""".trimIndent()

    suspend fun analyzeLearning(
        unknownNames: List<String>,
        kamusItems: List<KamusItem>,
        aliasHints: List<Pair<String, String>> = emptyList()
    ): Result<LearningAnalysis> = withContext(Dispatchers.IO) {
        // Summarize existing kamus (first 80 items to stay within token limit)
        val kamusSummary = kamusItems.take(80).joinToString("\n") { item ->
            "- ${item.name}" + if (item.aliases.isNotEmpty()) " (${item.aliases.joinToString(",")})" else ""
        }
        val hints = if (aliasHints.isNotEmpty())
            "\nHint similarity Kotlin (>80%):\n" +
            aliasHints.joinToString("\n") { (a, t) -> "- '$a' mirip '$t'" }
        else ""

        val userInput = """
Item belum dikenali: ${unknownNames.joinToString(", ")}

Kamus yang ada (sebagian):
$kamusSummary
$hints
""".trimIndent()

        Log.d(TAG, "  ── analyzeLearning ──")
        Log.d(TAG, "  unknowns: $unknownNames")
        Log.d(TAG, "  aliasHints: $aliasHints")
        Log.d(TAG, "  userInput snippet: ${userInput.take(300)}")

        for (model in Constants.GEMINI_FALLBACK_MODELS) {
            for ((idx, key) in Constants.GEMINI_API_KEYS.withIndex()) {
                ensureActive() // BUG-03
                Log.d(TAG, "  → learning Gemini/$model [key${idx + 1}]")
                val result = tryLearning(userInput, model, key)
                if (result.isSuccess) {
                    val a = result.getOrNull()!!
                    Log.d(TAG, "  ✓ learning OK: newItems=${a.newItems.size} aliases=${a.newAliases.size} units=${a.newUnits.size}")
                    return@withContext result
                }
                val err = result.exceptionOrNull()?.message ?: ""
                val code = extractHttpCode(err)
                Log.w(TAG, "  ✗ learning Gemini/$model [key${idx + 1}] HTTP=$code → $err")
                when (code) {
                    429, 503 -> continue
                    404, 400 -> break
                    else     -> break
                }
            }
        }
        Log.e(TAG, "  ✗ All Gemini keys exhausted for learning")
        Result.failure(Exception("All Gemini keys exhausted for learning"))
    }

    private fun tryLearning(input: String, model: String, key: String): Result<LearningAnalysis> = runCatching {
        // Note: runCatching OK di sini karena dipanggil dari suspend fun yg sudah punya ensureActive()
        val url = "${Constants.GEMINI_BASE_URL}$model:generateContent?key=$key"

        val reqBody = com.google.gson.JsonObject()

        val sysParts = com.google.gson.JsonArray()
        val sysPart = com.google.gson.JsonObject(); sysPart.addProperty("text", learningSystemPrompt)
        sysParts.add(sysPart)
        val sysInstruction = com.google.gson.JsonObject(); sysInstruction.add("parts", sysParts)
        reqBody.add("systemInstruction", sysInstruction)

        val part = com.google.gson.JsonObject(); part.addProperty("text", input)
        val parts = com.google.gson.JsonArray(); parts.add(part)
        val content = com.google.gson.JsonObject()
        content.addProperty("role", "user"); content.add("parts", parts)
        val contents = com.google.gson.JsonArray(); contents.add(content)
        reqBody.add("contents", contents)

        val genConfig = com.google.gson.JsonObject()
        genConfig.addProperty("temperature", 0.1)
        genConfig.addProperty("maxOutputTokens", 512)
        genConfig.addProperty("responseMimeType", "application/json")
        reqBody.add("generationConfig", genConfig)

        val request = Request.Builder()
            .url(url)
            .post(reqBody.toString().toRequestBody(JSON))
            .addHeader("Content-Type", "application/json")
            .build()

        val (httpCode, body) = client.newCall(request).execute().use { response ->
            response.code to (response.body?.string() ?: error("HTTP ${response.code}: empty body"))
        }
        Log.d(TAG, "  [learning raw] HTTP $httpCode body(500): ${body.take(500)}")
        if (httpCode !in 200..299) error("HTTP $httpCode: $body")

        val root = JsonParser.parseString(body).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
            ?: error("Learning: no candidates")
        if (candidates.size() == 0) error("Learning: empty candidates")
        val text = candidates[0].asJsonObject
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject?.get("text")?.asString
            ?: error("Learning: no text in response")

        Log.d(TAG, "  [learning text] ${text.take(400)}")
        parseLearningJson(text)
    }

    private fun parseLearningJson(text: String): LearningAnalysis {
        val json = JsonParser.parseString(text).asJsonObject
        val newItems = json.getAsJsonArray("new_items")?.map { el ->
            val o = el.asJsonObject
            NewItemSuggestion(
                name = o.get("name")?.asString ?: "",
                category = o.get("category")?.asString ?: "",
                aliases = o.getAsJsonArray("aliases")?.map { it.asString } ?: emptyList(),
                commonUnits = o.getAsJsonArray("common_units")?.map { it.asString } ?: emptyList()
            )
        } ?: emptyList()

        val newAliases = json.getAsJsonArray("new_aliases")?.map { el ->
            val o = el.asJsonObject
            NewAliasSuggestion(
                itemName = o.get("item_name")?.asString ?: "",
                alias = o.get("alias")?.asString ?: ""
            )
        } ?: emptyList()

        val newUnits = json.getAsJsonArray("new_units")?.map { el ->
            val o = el.asJsonObject
            NewUnitSuggestion(
                original = o.get("original")?.asString ?: "",
                normalized = o.get("normalized")?.asString ?: ""
            )
        } ?: emptyList()

        return LearningAnalysis(newItems, newAliases, newUnits)
    }
}
