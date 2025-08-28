package com.dheeraj.smartexpenses.sms

object ModelDownload {
    const val MODEL_URL = "https://github.com/vemuladheeraj/smart-expense-models/releases/download/v1.0-model/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task"
    const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct.task"
    const val MODEL_SIZE_MB = 1536L // 1536 MB
    const val MODEL_SIZE_BYTES = 1_610_613_760L // 1536 MB in bytes
    const val REQUIRED_SPACE_BYTES = 2_013_771_776L // ~2GB (2x model size for safety)
}
