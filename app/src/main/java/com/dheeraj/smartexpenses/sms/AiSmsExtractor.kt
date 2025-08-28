package com.dheeraj.smartexpenses.sms

/**
 * High-level AI extractor contract for end-to-end SMS understanding.
 * Implementations (e.g., MediaPipe LLM) should fill in as many fields as possible.
 */
interface AiSmsExtractor {
	data class Extraction(
		val isTransaction: Boolean,
		val isInternalTransfer: Boolean? = null,
		val type: String? = null,            // "CREDIT" | "DEBIT"
		val amountMinor: Long? = null,
		val channel: String? = null,
		val merchant: String? = null,
		val accountTail: String? = null,
		val bank: String? = null
	)

	fun extract(sender: String, body: String, ts: Long): Extraction?
}

/** Provider to inject an AI-first extractor at runtime. */
object AiSmsExtractorProvider {
	@Volatile
	var instance: AiSmsExtractor? = null
}


