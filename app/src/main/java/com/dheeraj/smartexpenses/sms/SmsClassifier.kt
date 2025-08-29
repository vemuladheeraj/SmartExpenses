package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * Single-task SMS classifier (transactional vs non-transactional) using LiteRT only.
 *
 * Model I/O:
 *  - Input:  int32[1, 200] (token IDs)
 *  - Output: float32[1, 1] (p(transactional))
 */
class SmsClassifier(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    private var tokenizer: SimpleSentencePieceTokenizer? = null

    companion object {
        private const val TAG = "SmsClassifier"
        private const val MAX_SEQUENCE_LENGTH = 200
        private const val VOCAB_SIZE = 8000

        // precompiled regex
        private val AMOUNT_REGEX = Regex("(?i)(?:₹|rs\\.?|inr)\\s*[:=\\-]?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)")
        private val BALANCE_HINTS = Regex("(?i)\\b(avl|avail|available|balance|bal|ledger|closing)\\b")
        private val TXN_HINTS     = Regex("(?i)\\b(debit(?:ed)?|credit(?:ed)?|spent|purchase|paid|payment|transfer|withdrawn|deducted|pos txn|atm txn|upi)\\b")

        private val DEBIT_WORDS  = Regex("(?i)\\b(debit(?:ed)?|spent|purchase|paid|payment|withdrawn|deducted|pos txn|atm txn|autodebit|mandate)\\b")
        private val CREDIT_WORDS = Regex("(?i)\\b(credit(?:ed)?|received|deposit(?:ed)?|refund(?:ed)?|reversal|cashback)\\b")

        private val OTP_REGEX = Regex("(?i)\\b(?:otp|one[-\\s]?time\\s?password)\\b|\\b\\d{4,8}\\b(?=.*(otp|do not share))")
        private val PROMO_REGEX = Regex("(?i)(offer|sale|discount|deal|win|congratulations|gift|voucher|subscribe|cashback up to|%\\s*off)")
        private val LOAN_REGEX = Regex("(?i)(loan|pre[- ]?approved|preapproved|finance|emi|interest rate|personal loan|gold loan)")
        private val STATEMENT_REGEX = Regex("(?i)(statement|bill\\s+generated|e-?statement|monthly statement|due date|minimum due)")
        private val DUE_REMINDER_REGEX = Regex("(?i)(due|overdue|pay now|last date|past due)")
        private val BALANCE_ONLY_REGEX = Regex("(?i)\\b(avail(?:able)?|ledger|closing|updated)?\\s*bal(?:ance)?\\b")
        private val LINK_HEAVY_REGEX = Regex("(?i)https?://|bit\\.ly|tinyurl|tap to|click to|download app")

        internal val CARDPAY_SKIP = Regex("(?i)(payment of|online payment).*\\b(card|cc)\\b|\\bcredited to your card\\b")
        internal val REFUND_SKIP  = Regex("(?i)\\b(refund|reversal|chargeback|cashback)\\b")

        internal val ACCOUNT_TAIL_REGEX = Regex("(?i)(?:a/?c(?:count)?|ac|acct|a/c)\\s*(?:xx|x|[*Xx]+)?\\s*([0-9]{3,5})")
        internal val REF_REGEX = Regex("(?i)(?:ref|utr|txn)\\s*[#:]?\\s*([a-zA-Z0-9]{8,20})")

        private val VPA_TO   = Regex("(?i)\\bto\\s+([a-z0-9._-]+@[a-z]+)\\b")
        private val VPA_FROM = Regex("(?i)\\bfrom\\s+([a-z0-9._-]+@[a-z]+)\\b")
        private val NAME_PATTERNS = listOf(
            Regex("(?i)\\bto\\s+([a-z&][a-z0-9& .'_-]{2,40})"),
            Regex("(?i)\\bat\\s+([a-z&][a-z0-9& .'_-]{2,40})"),
            Regex("(?i)\\bfrom\\s+([a-z&][a-z0-9& .'_-]{2,40})"),
            Regex("(?i)\\bpos\\s+([a-z&][a-z0-9& .'_-]{2,40})")
        )
    }

    /** Load model from assets using LiteRT API. */
    fun loadModel(modelPath: String): Boolean {
        Log.d(TAG, "loadModel: Starting to load model from $modelPath")
        return try {
            // quick existence check
            try { 
                context.assets.open(modelPath).close() 
                Log.d(TAG, "loadModel: Model file exists in assets")
            }
            catch (e: Exception) {
                Log.e(TAG, "Model not found in assets: $modelPath", e)
                return false
            }

            Log.d(TAG, "loadModel: Loading model file into memory")
            val modelBuffer = loadModelFileMapped(modelPath)
            Log.d(TAG, "loadModel: Model file loaded, creating interpreter")
            
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "loadModel: Interpreter created successfully")

            Log.d(TAG, "loadModel: Loading tokenizer")
            tokenizer = SimpleSentencePieceTokenizer(context).also {
                if (!it.loadVocab()) Log.w(TAG, "Tokenizer missing; using fallback")
            }

            isModelLoaded = true
            Log.d(TAG, "loadModel: Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "loadModel failed: ${e.message}", e)
            isModelLoaded = false
            interpreter = null
            false
        }
    }

    fun loadModelWithFallback(): Boolean {
        Log.d(TAG, "loadModelWithFallback: Attempting to load transaction_model.tflite")
        val result = loadModel("transaction_model.tflite")
        Log.d(TAG, "loadModelWithFallback: Result = $result")
        return result
    }

    @Synchronized
    fun analyzeSms(smsText: String): SmsAnalysis? {
        val raw = smsText.trim()
        if (isEarlyDropNonTransactional(raw)) {
            Log.d(TAG, "analyzeSms: Early drop - non-transactional")
            return SmsAnalysis(false, 0f, null, null, null, TransactionDirection.NONE, 0f)
        }

        if (!isModelLoaded || interpreter == null) {
            Log.d(TAG, "analyzeSms: Model not loaded, attempting to load...")
            if (!loadModelWithFallback() || interpreter == null) {
                Log.d(TAG, "analyzeSms: Model loading failed, using fallback")
                return analyzeSmsFallback(smsText)
            }
        }
        
        Log.d(TAG, "analyzeSms: Using AI model for analysis")

        return try {
            val input = preprocessText(smsText)
            val prob = runInference(input) ?: return analyzeSmsFallback(smsText)
            val analysis = postprocessOutput(prob, smsText)

            val flags = derivePostFilterFlags(smsText)
            if (flags.isCardBillPayment || flags.isRefundOrReversal || flags.isInternalTransfer) {
                analysis.copy(isTransactional = false, confidence = analysis.confidence * 0.3f)
            } else analysis
        } catch (e: Exception) {
            Log.e(TAG, "analyzeSms error: ${e.message}", e)
            analyzeSmsFallback(smsText)
        }
    }

    private fun analyzeSmsFallback(smsText: String): SmsAnalysis {
        Log.d(TAG, "analyzeSmsFallback: Using regex-based fallback analysis")
        val t = smsText.lowercase(Locale.ROOT)
        val amount = pickTransactionAmount(t)
        val type = when {
            "upi" in t -> "UPI"
            "imps" in t -> "IMPS"
            "neft" in t -> "NEFT"
            "rtgs" in t -> "RTGS"
            "pos" in t -> "POS"
            "atm" in t -> "ATM"
            "card" in t -> "CARD"
            else -> null
        }
        val direction = when {
            DEBIT_WORDS.containsMatchIn(t) -> TransactionDirection.DEBIT
            CREDIT_WORDS.containsMatchIn(t) -> TransactionDirection.CREDIT
            else -> TransactionDirection.NONE
        }
        val merchant = extractMerchantFallback(t)
        val isTxn = amount != null && direction != TransactionDirection.NONE
        val result = SmsAnalysis(isTxn, if (isTxn) 0.8f else 0.3f, merchant, amount, type, direction, if (direction != TransactionDirection.NONE) 0.7f else 0f)
        Log.d(TAG, "analyzeSmsFallback: Result - isTxn=$isTxn, amount=$amount, direction=$direction")
        return result
    }

    private fun pickTransactionAmount(t: String): String? {
        val all = AMOUNT_REGEX.findAll(t).map { it.groupValues[1] to it.range.first }.toList()
        if (all.isEmpty()) return null
        return all.minBy { (_, idx) ->
            val w = t.substring((idx - 25).coerceAtLeast(0), (idx + 25).coerceAtMost(t.length))
            var score = 0
            if (TXN_HINTS.containsMatchIn(w)) score -= 2
            if (BALANCE_HINTS.containsMatchIn(w)) score += 2
            score
        }.first
    }

    private fun extractMerchantFallback(t: String): String? {
        VPA_TO.find(t)?.let { return it.groupValues[1] }
        VPA_FROM.find(t)?.let { return it.groupValues[1] }
        for (r in NAME_PATTERNS) r.find(t)?.let { return it.groupValues[1].trim() }
        return null
    }

    private fun preprocessText(text: String): IntArray {
        val ids = tokenizer?.tokenize(text) ?: fallbackTokenize(text)
        return when {
            ids.size > MAX_SEQUENCE_LENGTH -> ids.take(MAX_SEQUENCE_LENGTH).toIntArray()
            ids.size < MAX_SEQUENCE_LENGTH -> ids + IntArray(MAX_SEQUENCE_LENGTH - ids.size) { 0 }
            else -> ids
        }
    }

    private fun fallbackTokenize(text: String): IntArray {
        val tokens = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .take(MAX_SEQUENCE_LENGTH)

        val ids = tokens.map { tok ->
            val h = tok.hashCode() % VOCAB_SIZE
            if (h < 0) h + VOCAB_SIZE else h
        }.toMutableList()

        while (ids.size < MAX_SEQUENCE_LENGTH) ids.add(0)
        return ids.toIntArray()
    }

    @Synchronized
    private fun runInference(inputIds: IntArray): Float? {
        return try {
            val padded = if (inputIds.size == MAX_SEQUENCE_LENGTH) inputIds
            else inputIds + IntArray(MAX_SEQUENCE_LENGTH - inputIds.size) { 0 }

            val inputBuffer = ByteBuffer.allocateDirect(4 * MAX_SEQUENCE_LENGTH).order(ByteOrder.nativeOrder())
            inputBuffer.asIntBuffer().put(padded)

            val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

            interpreter?.run(inputBuffer, outputBuffer) ?: return null
            outputBuffer.rewind()
            outputBuffer.getFloat(0)
        } catch (e: Exception) {
            Log.e(TAG, "runInference error: ${e.message}", e); null
        }
    }

    private fun postprocessOutput(prob: Float, originalText: String): SmsAnalysis {
        val isTxn = prob > 0.5f
        val t = originalText.lowercase(Locale.ROOT)
        val amount = pickTransactionAmount(t)
        val type = when {
            "upi" in t -> "UPI"
            "imps" in t -> "IMPS"
            "neft" in t -> "NEFT"
            "rtgs" in t -> "RTGS"
            "pos" in t -> "POS"
            "atm" in t -> "ATM"
            "card" in t -> "CARD"
            else -> null
        }
        val direction = when {
            DEBIT_WORDS.containsMatchIn(t) -> TransactionDirection.DEBIT
            CREDIT_WORDS.containsMatchIn(t) -> TransactionDirection.CREDIT
            else -> TransactionDirection.NONE
        }
        val merchant = extractMerchantFallback(t)
        return SmsAnalysis(isTxn, prob, merchant, amount, type, direction, if (direction != TransactionDirection.NONE) 0.7f else 0f)
    }



    private fun derivePostFilterFlags(text: String): PostFilterFlags {
        val t = text.lowercase(Locale.ROOT)
        val cardPay = CARDPAY_SKIP.containsMatchIn(t)
        val refund  = REFUND_SKIP.containsMatchIn(t)
        val internal = Regex("(?i)\\bself\\b|to\\s+own\\s+acct|between\\s+your\\s+accounts").containsMatchIn(t)
        return PostFilterFlags(cardPay, refund, internal)
    }

    private fun isEarlyDropNonTransactional(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        if (OTP_REGEX.containsMatchIn(t)) return true
        if (PROMO_REGEX.containsMatchIn(t) && !Regex("(?i)(debited|credited|spent|payment|purchase|withdrawn)").containsMatchIn(t)) return true
        if (LOAN_REGEX.containsMatchIn(t)) return true
        if (STATEMENT_REGEX.containsMatchIn(t) && !Regex("(?i)(payment received|paid|credited)").containsMatchIn(t)) return true
        if (DUE_REMINDER_REGEX.containsMatchIn(t) && !Regex("(?i)(payment received|paid|credited|debited|spent)").containsMatchIn(t)) return true
        if (BALANCE_ONLY_REGEX.containsMatchIn(t) && !Regex("(?i)(debited|credited|spent|payment|purchase|withdrawn)").containsMatchIn(t)) return true
        if (LINK_HEAVY_REGEX.containsMatchIn(t) && !Regex("(?i)(debited|credited|spent|payment|purchase|withdrawn)").containsMatchIn(t)) return true
        return false
    }

    private fun loadModelFileMapped(assetPath: String): ByteBuffer {
        // requires noCompress on .tflite
        val afd = context.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        tokenizer = null
        isModelLoaded = false
    }

    /**
     * Convert SmsAnalysis to ParsedRow for aggregation
     */
    fun analysisToParsedRow(analysis: SmsAnalysis, timestamp: Long, rawText: String): ParsedRow? {
        if (!analysis.isTransactional || analysis.amount == null) {
            return null
        }

        val amountPaise = TransactionPairUtils.amountToPaise(analysis.amount)
        if (amountPaise <= 0) {
            return null
        }

        return ParsedRow(
            tsMillis = timestamp,
            direction = analysis.direction,
            amountPaise = amountPaise,
            accountTail = TransactionPairUtils.extractAccountTail(rawText),
            counterparty = analysis.merchant,
            refId = TransactionPairUtils.extractRefId(rawText),
            rawText = rawText,
            ignoreForCalculations = false
        )
    }
}

data class SmsAnalysis(
    val isTransactional: Boolean,
    val confidence: Float,
    val merchant: String?,
    val amount: String?,
    val transactionType: String?,
    val direction: TransactionDirection,
    val directionConfidence: Float
)

enum class TransactionDirection { DEBIT, CREDIT, NONE }

data class PostFilterFlags(
    val isCardBillPayment: Boolean,
    val isRefundOrReversal: Boolean,
    val isInternalTransfer: Boolean
)

data class ParsedRow(
    val tsMillis: Long,
    val direction: TransactionDirection,
    val amountPaise: Long,
    val accountTail: String?,
    val counterparty: String?,
    val refId: String?,
    val rawText: String,
    var ignoreForCalculations: Boolean = false
)

object TransactionPairUtils {
    fun amountsEqual(a: Long, b: Long) = a == b

    fun isLikelySameCounterparty(a: ParsedRow, b: ParsedRow): Boolean {
        if (a.refId != null && a.refId == b.refId) return true
        if (a.accountTail != null && b.accountTail != null && a.accountTail == b.accountTail) return true
        val ca = a.counterparty?.lowercase(Locale.ROOT)?.trim()
        val cb = b.counterparty?.lowercase(Locale.ROOT)?.trim()
        if (!ca.isNullOrBlank() && ca == cb) return true
        val selfHints = Regex("(?i)\\b(self|own\\s+account|between\\s+your\\s+accounts)\\b")
        if (selfHints.containsMatchIn(ca.orEmpty()) || selfHints.containsMatchIn(cb.orEmpty())) return true
        return false
    }

    fun markOffsettingPairs(rows: MutableList<ParsedRow>, windowMillis: Long = 2 * 60 * 60 * 1000L) {
        val byAmount = rows.groupBy { it.amountPaise }
        for ((_, list) in byAmount) {
            val debits = list.filter { it.direction == TransactionDirection.DEBIT && !it.ignoreForCalculations }.sortedBy { it.tsMillis }
            val credits = list.filter { it.direction == TransactionDirection.CREDIT && !it.ignoreForCalculations }.sortedBy { it.tsMillis }
            var i = 0; var j = 0
            while (i < debits.size && j < credits.size) {
                val d = debits[i]; val c = credits[j]
                val dt = kotlin.math.abs(d.tsMillis - c.tsMillis)
                if (dt <= windowMillis && isLikelySameCounterparty(d, c)) {
                    d.ignoreForCalculations = true
                    c.ignoreForCalculations = true
                    i++; j++
                } else {
                    if (d.tsMillis < c.tsMillis) i++ else j++
                }
            }
        }
    }

    fun shouldSkipForTotals(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return SmsClassifier.CARDPAY_SKIP.containsMatchIn(lower) || SmsClassifier.REFUND_SKIP.containsMatchIn(lower)
    }

    fun amountToPaise(amountStr: String?): Long = try {
        if (amountStr.isNullOrBlank()) 0L else {
            val clean = amountStr.replace("[₹,\\s]".toRegex(), "").replace("(?i)rs\\.?".toRegex(), "")
            (clean.toBigDecimal().movePointRight(2)).toLong()
        }
    } catch (_: Exception) { 0L }

    fun extractAccountTail(text: String): String? =
        SmsClassifier.ACCOUNT_TAIL_REGEX.find(text)?.groupValues?.get(1)

    fun extractRefId(text: String): String? =
        SmsClassifier.REF_REGEX.find(text)?.groupValues?.get(1)
}

/**
 * Simple word-level tokenizer. If your model was trained with SentencePiece,
 * ship the exact token->id vocab (or use an SP runtime) to avoid accuracy loss.
 */
class SimpleSentencePieceTokenizer(private val context: Context) {
    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()
    private var isLoaded = false

    companion object {
        private const val TAG = "SimpleSentencePieceTokenizer"
        private const val UNK_TOKEN = "<unk>"
        private const val PAD_TOKEN = "<pad>"
        private const val UNK_ID = 1
        private const val PAD_ID = 0
    }

    fun loadVocab(): Boolean = try {
        context.assets.open("sms_tokenizer.vocab").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val parts = line.trim().split("\t")
                    if (parts.size >= 2) {
                        val token = parts[0]
                        val id = parts[1].toInt()
                        vocab[token] = id
                        reverseVocab[id] = token
                    }
                }
            }
        }
        if (!vocab.containsKey(UNK_TOKEN)) { vocab[UNK_TOKEN] = UNK_ID; reverseVocab[UNK_ID] = UNK_TOKEN }
        if (!vocab.containsKey(PAD_TOKEN)) { vocab[PAD_TOKEN] = PAD_ID; reverseVocab[PAD_ID] = PAD_TOKEN }
        isLoaded = true
        Log.d(TAG, "Vocabulary loaded: ${vocab.size} entries")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load vocab: ${e.message}", e); false
    }

    fun tokenize(text: String): IntArray {
        if (!isLoaded) return fallbackTokenize(text)
        val words = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
        return words.map { w -> vocab[w] ?: UNK_ID }.toIntArray()
    }

    private fun fallbackTokenize(text: String): IntArray {
        val words = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .take(200)

        val ids = words.map { w ->
            val h = w.hashCode() % 8000
            if (h < 0) h + 8000 else h
        }.toMutableList()

        while (ids.size < 200) ids.add(PAD_ID)
        return ids.toIntArray()
    }

    fun detokenize(tokenIds: IntArray): String {
        if (!isLoaded) return tokenIds.joinToString(" ")
        val toks = buildList {
            for (id in tokenIds) reverseVocab[id]?.let { if (it != PAD_TOKEN) add(it) }
        }
        return toks.joinToString(" ")
    }
}
