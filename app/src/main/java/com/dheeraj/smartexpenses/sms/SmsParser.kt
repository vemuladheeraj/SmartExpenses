package com.dheeraj.smartexpenses.sms

import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.MediaPipeAiSmsExtractor
import java.math.BigDecimal
import java.util.Locale

object SmsParser {
    @Volatile
    private var aiEnabled: Boolean = true

    fun setAiEnabled(enabled: Boolean) { aiEnabled = enabled }
    // ===================== Short-window internal transfer detector =====================
    private object RecentAmountWindow {
        private val lock = Any()
        // Keep minimal recent events per amount: list of (ts, type)
        private val amountToEvents = mutableMapOf<Long, MutableList<Pair<Long, String>>>()
        private const val WINDOW_MS: Long = 3 * 60 * 1000 // 3 minutes

        fun recordAndDetect(amountMinor: Long, type: String, ts: Long): Boolean {
            synchronized(lock) {
                val list = amountToEvents.getOrPut(amountMinor) { mutableListOf() }
                // Drop stale
                val cutoff = ts - WINDOW_MS
                val fresh = list.filter { it.first >= cutoff }.toMutableList()

                // Detect opposite type within window
                val hasOpposite = fresh.any { (t, ty) -> kotlin.math.abs(ts - t) <= WINDOW_MS && ty != type }

                // Record current
                fresh.add(ts to type)
                // Keep bounded list to avoid growth
                if (fresh.size > 20) fresh.removeFirst()
                amountToEvents[amountMinor] = fresh

                return hasOpposite
            }
        }
    }

    // ===================== Core Regex (compiled once) =====================

    // Amounts like: ₹1,23,456.78 /- , Rs.999/- , INR 2500, 1,234.00
    // Important: require at least one comma in the commaed branch to avoid
    // mistakenly matching only the first 3 digits of 4+ digit amounts without commas (e.g., 3630.00)
    private val amtRegex = Regex(
        pattern = "(?i)(?:\\bINR\\.?\\b|\\bRs\\.?\\b|₹)\\s*[:=\\-]?\\s*(" +
                "[0-9]+(?:\\.[0-9]+)?(?!,)" +                // plain digits (no trailing comma)
                "|[0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.[0-9]+)?" + // commaed variant (requires at least one comma)
                ")\\s*(?:/-)?"
    )

    // A/c ****1234, A/c xx1234, AC No 12345, account XXXX1234, ending 1234
    private val accTailRegex = Regex(
        "(?i)(?:\\bA/?c(?:count)?\\b|\\bAC\\b|\\bacct\\b)(?:\\s*No\\.?)?\\s*(?:[*x#\\-]{2,}\\s*)?([0-9]{4,8})"
    )
    private val accTailLoose = Regex("(?i)(?:xx+|\\*{2,}|ending)\\s*\\d{3,6}|\\b\\d{4,6}\\b")

    // Channels / rails
    private val upiKeywordRegex = Regex("(?i)\\bUPI\\b|\\bVPA\\b")
    private val vpaRegex        = Regex("(?i)([a-z0-9._\\-]{2,}@[a-z][a-z0-9\\-]{2,}(?!\\.[a-z]{2,}\\b))") // avoid emails
    private val cardRegex       = Regex("(?i)\\b(?:CARD|VISA|MASTERCARD|RUPAY|AMEX)\\b|\\b\\*{2,}\\d{2,4}\\b|\\bxx\\d{2,4}\\b")
    private val impsRegex       = Regex("(?i)\\bIMPS\\b|\\bMMID\\b")
    private val neftRegex       = Regex("(?i)\\bNEFT\\b|\\bRTGS\\b|\\bUTR\\b")
    private val posRegex        = Regex("(?i)\\bPOS\\b|\\bE\\-?COM\\b|\\bMERCHANT\\b")

    // Transaction verb / context signals
    private val txnVerbRegex     = Regex("(?i)\\b(credited|debited|txn|transaction|imps|neft|rtgs|utr|pos|e-?com)\\b")
    private val bankyContextRegex= Regex("(?i)(avl\\s*bal|available\\s*balance|min(?:imum)?\\s*due|total\\s*due)")
    private val refRegex         = Regex("(?i)\\b(utr|ref(?:\\.|erence)?\\s*no\\.?|mmid|tid|rrn|auth|rrn)\\b")
    private val dateLikeRegex    = Regex("(?i)\\b(?:\\d{1,2}[-/ ][A-Z]{3}[-/ ]\\d{2,4}|\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4})\\b")

    // Spam / promo detectors
    private val linkRegex        = Regex("(?i)https?://\\S+")
    private val shortenerRegex   = Regex("(?i)https?://(?:bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|ow\\.ly|is\\.gd|rb\\.gy|s\\.id|lnkd\\.in|rebrand\\.ly)/\\S+")
    private val spamPhraseRegex  = Regex("(?i)(up\\s*to|eligible|pre[- ]?approved|ready\\s*to\\s*be\\s*credited|apply\\s*now|just\\s*complete.*kyc|instant\\s*loan|emi\\s*from)")
    private val eduPromoRegex    = Regex("(?i)(scholarship|program|course|learning|training|pg\\s*program|ai\\s*&\\s*machine\\s*learning|great\\s*learning|coursera|udemy|edx)")

    // Quick filters (OTP/marketing baseline)
    private val otpRegex   = Regex("(?i)\\bOTP\\b|one\\s*[- ]*time\\s*password|verification\\s*code")
    private val promoRegex = Regex("(?i)\\bwin\\b|\\boffer\\b|\\bsale\\b|\\bcongratulations\\b|\\bapply now\\b")
    private val loanRegex  = Regex("(?i)\\bloan\\b|\\bemi\\b|\\bpre[- ]?approved\\b|\\bdisburs\\w+\\b|\\beligible\\b|\\blimit\\b")

    // Card statements & bill payments (exclude)
    private val cardStatementHints = listOf(
        "statement is generated", "statement generated", "total due", "minimum due", "min due",
        "payment due", "due by", "bill generated", "new statement"
    )
    private val cardPaymentHints = listOf(
        "payment received", "payment of", "bill paid", "autopay", "bbps", "towards card", "card bill"
    )

    // Internal/self transfer phrases
    private val selfXferHints = listOf(
        "self transfer", "own account", "between your accounts", "internal transfer",
        "intra bank", "same bank", "to your a/c", "from your a/c", "a/c to a/c", "account to account",
        "funds transfer", "fund transfer", "ib funds transfer", "ibft", "internet banking transfer", "net banking transfer"
    )

    // Credit / Debit cue words
    private val creditWords = listOf(
        "credited", "credit", "cr", "received", "deposit", "deposited",
        "refund", "refunded", "reversal", "chargeback", "cashback", "salary",
        "interest", "td credit", "imps in", "neft in", "upi in"
    )
    private val debitWords  = listOf(
        "debited", "debit", "dr", "spent", "withdrawn", "withdrawal",
        "purchase", "paid", "payment made", "sent", "transfer to", "trf to",
        "upi out", "cash wdl", "atm", "autopay", "ach", "ecs", "recharge", "fuel"
    )

    // ===================== Public API =====================

    fun parse(sender: String, body: String, ts: Long): Transaction? {
        // Regex-first filtering and quick heuristics
        val lower = body.lowercase(Locale.ROOT)

        // (0) Drop obvious OTP / baseline promos immediately
        if (otpRegex.containsMatchIn(body)) return null
        if (promoRegex.containsMatchIn(body)) return null

        // (0.1) Promo vs Transaction gate: block promos unless there is strong evidence of a real txn
        val promoish = looksLikePromo(body)
        val txnish   = looksLikeTransaction(sender, body)
        val hasStrongTxnEvidence = accTailRegex.containsMatchIn(body) || refRegex.containsMatchIn(body)
        if (promoish && !hasStrongTxnEvidence) return null

        // (1) Amount extraction with scoring to avoid balance figures
        val matches = amtRegex.findAll(body).toList()
        val amount = if (matches.isNotEmpty()) {
            fun score(match: MatchResult): Int {
                val start = (match.range.first - 90).coerceAtLeast(0)
                val endExclusive = (match.range.last + 90 + 1).coerceAtMost(body.length) // substring end is exclusive
                val ctx = body.substring(start, endExclusive).lowercase(Locale.ROOT)
                var s = 0
                val debitCues = listOf("spent", "debited", "purchase", "paid", "sent", "transaction", "txn", "upi to", "neft", "imps")
                val creditCues = listOf("credited", "received", "deposit", "refunded", "reversal", "cashback", "salary", "interest")
                if (debitCues.any { ctx.contains(it) }) s += 5
                if (creditCues.any { ctx.contains(it) }) s += 4
                val negativeCues = listOf(
                    "avl bal", "available bal", "available balance", "closing balance",
                    "ledger balance", "available limit", "total outstanding", "outstanding",
                    "balance", "total due", "minimum due", "min due"
                )
                if (negativeCues.any { ctx.contains(it) }) s -= 6
                val positionBias = (match.range.first / 500).coerceAtMost(5)
                return s - positionBias
            }
            val best = matches.maxByOrNull { score(it) } ?: matches.first()
            best.groupValues.getOrNull(1)?.replace(",", "")?.let { runCatching { BigDecimal(it) }.getOrNull() }
        } else null
        if (amount == null) return null

        // (1.5) Early exclusions for loan marketing unless clearly money movement
        if (loanRegex.containsMatchIn(body)) {
            val loanCreditLike = lower.contains("credit") || lower.contains("credited")
                    || creditWords.any { lower.contains(it) } || lower.contains("disburs")
            val isEmiPayment = lower.contains("emi paid") || lower.contains("payment received")
            if (!loanCreditLike && !isEmiPayment) return null
        }

        // (2) Bank / account tail
        val bank = bankFromSender(sender)
        val accountTail = accTailRegex.find(body)?.groupValues?.getOrNull(1)

        // (3) Channel detection (prefer instrument CARD before POS)
        val hasUpiKeyword = upiKeywordRegex.containsMatchIn(body)
        val vpaStrict = vpaRegex.find(body) != null
        val channel = when {
            (hasUpiKeyword || vpaStrict)    -> "UPI"
            cardRegex.containsMatchIn(body) -> "CARD"
            posRegex.containsMatchIn(body)  -> "POS"
            impsRegex.containsMatchIn(body) -> "IMPS"
            neftRegex.containsMatchIn(body) -> "NEFT"
            else -> null
        }

        // (4) Early exclusions you want to enforce
        val mentionsCard = cardRegex.containsMatchIn(body) || lower.contains("credit card") || lower.contains("card ")
        if (mentionsCard && cardStatementHints.any { lower.contains(it) }) return null   // statements
        if (mentionsCard && cardPaymentHints.any { lower.contains(it) })  return null   // bill payments
        if (isInternalTransferText(body)) return null                                   // self transfers

        // (5) Type detection only for external transactions
        val creditHit = creditWords.any { lower.contains(it) }
        val debitHit  = debitWords.any  { lower.contains(it) }

        val type = when {
            creditHit.xor(debitHit) -> if (creditHit) "CREDIT" else "DEBIT"
            lower.contains("credited to your a/c") -> "CREDIT"
            lower.contains("debited from your a/c")-> "DEBIT"
            lower.contains("payment received")     -> "CREDIT"
            lower.contains("payment made")         -> "DEBIT"
            else -> {
                // Directional inference for external rails
                if (channel == "UPI" || channel == "IMPS" || channel == "NEFT") {
                    val toHit = Regex("(?i)\\bto\\b").containsMatchIn(body)
                    val fromHit = Regex("(?i)\\bfrom\\b").containsMatchIn(body)
                    when {
                        toHit && !fromHit -> "DEBIT"
                        fromHit && !toHit -> "CREDIT"
                        else -> return null // still ambiguous → drop
                    }
                } else return null
            }
        }

        // (5.5) Short-window internal transfer pairing by same amount
        val amountMinor = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        val looksPairedInternal = RecentAmountWindow.recordAndDetect(amountMinor, type, ts)
        if (looksPairedInternal) return null

        // (6) Merchant / counterparty (prefer names; fall back to VPA if present)
        val toRegex    = Regex("(?i)\\b(?:to|at|for)\\s+([A-Za-z0-9&._\\- ]{3,40})")
        val fromRegex  = Regex("(?i)\\b(?:from|by)\\s+([A-Za-z0-9&._\\- ]{3,40})")

        val merchantRaw = when (type) {
            "CREDIT" -> fromRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            else     -> toRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        }
        val merchantCandidate = merchantRaw?.takeIf { it.any(Char::isLetter) }
            ?: vpaRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        val merchant = merchantCandidate?.let { cleanMerchant(it) }

        // (7) After regex filtering and internal-transfer gate, consult AI for enrichment only
        val aiHints = if (aiEnabled) try {
            SmsAiProvider.instance?.predict(sender, body)
                ?: AiSmsExtractorProvider.instance?.extract(sender, body, ts)?.let {
                    SmsAi.Prediction(
                        isTransaction = true,
                        type = it.type,
                        amountMinor = it.amountMinor,
                        channel = it.channel,
                        merchant = it.merchant
                    )
                }
        } catch (e: OutOfMemoryError) {
            (AiSmsExtractorProvider.instance as? MediaPipeAiSmsExtractor)?.clearResources()
            null
        } catch (e: Exception) {
            android.util.Log.e("SmsParser", "AI enrichment failed: ${e.message}", e)
            null
        } else null
        val mergedChannel = aiHints?.channel ?: channel
        val mergedMerchant = aiHints?.merchant ?: merchant

        return Transaction(
            ts = ts,
            amountMinor = amountMinor,
            type = aiHints?.type ?: type,         // only CREDIT/DEBIT reach here
            channel = mergedChannel,
            merchant = mergedMerchant,
            accountTail = accountTail,
            bank = bank,
            source = "SMS",
            rawSender = sender,
            rawBody = body
        )
    }

    // ===================== Heuristics & Helpers =====================

    private fun looksLikeTransaction(sender: String, body: String): Boolean {
        val hasTxnVerb = txnVerbRegex.containsMatchIn(body)
        val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
        val hasBankCtx = bankyContextRegex.containsMatchIn(body)
        val hasRef     = refRegex.containsMatchIn(body)
        val hasDate    = dateLikeRegex.containsMatchIn(body)
        val bankKnown  = bankFromSender(sender) != null

        // Need verbs + at least one other strong signal
        val strongCount = listOf(hasTxnVerb, hasAccTail, hasRef, hasDate, bankKnown, hasBankCtx).count { it }
        return hasTxnVerb && strongCount >= 2
    }

    private fun looksLikePromo(body: String): Boolean {
        val hasSpamPhrase = spamPhraseRegex.containsMatchIn(body)
        val hasLink       = linkRegex.containsMatchIn(body)
        val hasShortener  = shortenerRegex.containsMatchIn(body)
        val eduPromo      = eduPromoRegex.containsMatchIn(body)
        // Often promos have a link or “eligible/up to/shortlisted/PG Program” wording
        return hasSpamPhrase || eduPromo || hasShortener || hasLink
    }

    private fun isInternalTransferText(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)

        // Strong phrases
        if (selfXferHints.any { lower.contains(it) }) return true

        // Both credited & debited + two distinct tails → intra-account notice
        val credited = lower.contains("credited")
        val debited  = lower.contains("debited")
        if (credited && debited) {
            val tails = accTailRegex.findAll(body).map { it.groupValues[1] }.toSet()
            if (tails.size >= 2) return true
        }

        // “from your a/c XXXX to your a/c YYYY”
        if (lower.contains("to your a/c") && lower.contains("from your a/c")) return true

        // Guardrails: do NOT mark as internal if it clearly looks like third-party spend
        if (posRegex.containsMatchIn(body) || Regex("(?i)\\b(purchase|pos|e-?com)\\b").containsMatchIn(body)) return false
        if (vpaRegex.containsMatchIn(body) && (lower.contains(" to ") || lower.contains(" upi to "))) return false

        return false
    }

    private fun bankFromSender(senderRaw: String): String? {
        // DLT headers like "VM-HDFCBK", "AX-ICICIB", "TD-SBINB", or "VK-ICICIB-ALERT"
        val tokens = senderRaw.uppercase(Locale.ROOT).split('-', ' ').filter { it.isNotBlank() }
        val cand = tokens.lastOrNull { it.any(Char::isLetter) && it.length >= 4 } ?: tokens.lastOrNull() ?: return null
        return when {
            cand.contains("HDFCBK") || cand.contains("HDFC") -> "HDFC"
            cand.contains("ICICIB") || cand.contains("ICICI") -> "ICICI"
            cand.contains("SBINB") || cand.contains("SBIPSG") || cand.contains("SBI") -> "SBI"
            cand.contains("AXISBK") || cand.contains("AXIS") -> "AXIS"
            cand.contains("KOTAK") -> "KOTAK"
            cand.contains("YESBNK") || cand.contains("YES") -> "YES"
            cand.contains("IDFCFB") || cand.contains("IDFC") -> "IDFC"
            cand.contains("BANKOFBARODA") || cand.contains("BOB") -> "BOB"
            cand.contains("PAYTM") -> "PAYTM"
            cand.contains("AMAZON") -> "AMAZON PAY"
            cand.contains("PHONEPE") -> "PHONEPE"
            else -> null
        }
    }

    private fun Boolean.xor(other: Boolean) = (this && !other) || (!this && other)

    private fun cleanMerchant(input: String): String {
        val stop = Regex(
            "(?i)(?:\\bon\\b|\\bvia\\b|\\bref(?:\\.|erence|\\s*no\\.)?\\b|\\btxn\\b|\\btx\\b|\\butr(?:\\s*no\\.)?\\b|\\bauth\\b|\\bord\\b|\\btid\\b|\\bend(?:ing)?\\b|\\ba/?c\\b)"
        )
        val first = input.split(stop, limit = 2)[0]
        return first.trim(' ', '.', ',', '-', '_').replace("\\s+".toRegex(), " ")
    }
}
