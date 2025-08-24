package com.dheeraj.smartexpenses.sms


import com.dheeraj.smartexpenses.data.Transaction

object SmsParser {
    private val amtRegex = Regex("""(?i)(?:INR|Rs\.?|₹)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]+)?|[0-9]+(?:\.[0-9]+)?)""")
    private val accTailRegex = Regex("""(?i)(?:A/c|AC|Acct|account)(?:\s*No\.?|)\s*(?:xx|XX|X{2,}|x{2,}|#|-)?\s*([0-9]{2,6})""")
    private val upiRegex = Regex("""(?i)\bUPI\b|\bVPA\b|\b@""")
    private val cardRegex = Regex("""(?i)\b(?:CARD|VISA|MASTERCARD|RUPAY)\b|\bxx[0-9]{4}\b""")
    private val impsRegex = Regex("""(?i)\bIMPS\b""")
    private val neftRegex = Regex("""(?i)\bNEFT\b""")
    private val posRegex = Regex("""(?i)\bPOS\b""")

    private val creditKeywords = listOf("credited", "received", "deposited", "refunded")
    private val debitKeywords  = listOf("debited", "spent", "withdrawn", "purchase", "paid", "sent")

    private val merchantHintRegex = Regex("""(?i)\b(?:at|to|for)\s+([A-Z0-9&\-\._ ]{3,30})""")

    fun parse(sender: String, body: String, ts: Long): Transaction? {
        // ignore OTPs
        if (Regex("(?i)OTP|one[- ]time|verification").containsMatchIn(body)) return null

        val amount = amtRegex.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        val tail = accTailRegex.find(body)?.groupValues?.get(1)
        val bank = bankFromSender(sender)

        val lower = body.lowercase()
        val isCredit = creditKeywords.any { lower.contains(it) }
        val isDebit  = debitKeywords.any  { lower.contains(it) }
        val type = when {
            isCredit && !isDebit -> "CREDIT"
            isDebit  && !isCredit -> "DEBIT"
            lower.contains("received") -> "CREDIT"
            else -> "DEBIT"
        }

        val channel = when {
            upiRegex.containsMatchIn(body) -> "UPI"
            cardRegex.containsMatchIn(body) -> "CARD"
            posRegex.containsMatchIn(body) -> "POS"
            impsRegex.containsMatchIn(body) -> "IMPS"
            neftRegex.containsMatchIn(body) -> "NEFT"
            else -> null
        }

        val merchant = merchantHintRegex.find(body)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.any(Char::isLetter) }

        return Transaction(
            ts = ts,
            amount = amount,
            type = type,
            channel = channel,
            merchant = merchant,
            accountTail = tail,
            bank = bank,
            source = "SMS",
            rawSender = sender,
            rawBody = body
        )
    }

    private fun bankFromSender(sender: String): String? = when (sender.uppercase()) {
        in setOf("HDFC", "HDFCBK", "HDFC-BANK", "HDFCBN") -> "HDFC"
        in setOf("ICICI", "ICICIB", "ICICI-BANK") -> "ICICI"
        else -> when {
            sender.uppercase().contains("SBI") -> "SBI"
            sender.uppercase().contains("AXIS") -> "AXIS"
            sender.uppercase().contains("KOTAK") -> "KOTAK"
            sender.uppercase().contains("YES") && sender.uppercase().contains("BANK") -> "YES"
            sender.uppercase().contains("PAYTM") -> "PAYTM"
            sender.uppercase().contains("AMAZON") -> "AMAZON PAY"
            else -> null
        }
    }
}
