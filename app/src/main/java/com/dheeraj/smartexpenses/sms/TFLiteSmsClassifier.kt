package com.dheeraj.smartexpenses.sms

import android.content.Context
import java.util.Locale

/**
 * Minimal stub for a TFLite-backed classifier. Replace TODO parts with real model code.
 * Keep small and optional: app should run even if this isn't initialized.
 */
class TFLiteSmsClassifier(
	private val context: Context
) : SmsAi {

	// TODO: load your tflite from assets (e.g., assets/models/sms.tflite)
	// private val interpreter: Interpreter by lazy { ... }

	override fun predict(sender: String, body: String): SmsAi.Prediction? {
		// TODO: tokenize/featurize sender+body, run model, map outputs to Prediction
		// Returning null means "no AI decision" so the regex pipeline will handle it
		val lower = body.lowercase(Locale.ROOT)
		// Example super-naive placeholder: only signal uncertainty, never block
		return if (lower.contains(" debited ") || lower.contains(" credited ")) {
			SmsAi.Prediction(
				isTransaction = true,
				type = if (lower.contains("credited")) "CREDIT" else if (lower.contains("debited")) "DEBIT" else null,
				amountMinor = null,
				channel = null,
				merchant = null
			)
		} else null
	}
}


