package com.dheeraj.smartexpenses.sms

/**
 * Lightweight AI contract for SMS transaction detection and enrichment.
 * Provide an implementation (e.g., TFLite) and assign it to [SmsAiProvider.instance].
 */
interface SmsAi {
	data class Prediction(
		val isTransaction: Boolean,
		val type: String?,            // "CREDIT" | "DEBIT" if known
		val amountMinor: Long?,       // suggested minor units (paise)
		val channel: String?,         // e.g., UPI, CARD, IMPS, NEFT, POS
		val merchant: String?         // cleaned, human readable if known
	)

	fun predict(sender: String, body: String): Prediction?
}

/** Provider to inject the AI at runtime (e.g., from Application). */
object SmsAiProvider {
	@Volatile
	var instance: SmsAi? = null
}


