package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.Locale

/**
 * Multi-task SMS classifier that extracts multiple pieces of information from SMS text.
 * 
 * Model Input: int32[1, 200] token IDs
 * Model Outputs: 5 different outputs for classification and entity extraction
 */
class SmsMultiTaskClassifier(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    private var inputTensorName: String = "serving_default_input_ids:0"
    private var outputTensorNames: Map<String, String> = mapOf(
        "direction" to "StatefulPartitionedCall_1:4",
        "merchant_ner" to "StatefulPartitionedCall_1:1", 
        "amount_ner" to "StatefulPartitionedCall_1:3",
        "classification" to "StatefulPartitionedCall_1:0",
        "type_ner" to "StatefulPartitionedCall_1:2"
    )
    
    // Output buffers for the model
    private lateinit var directionBuffer: TensorBuffer
    private lateinit var merchantBuffer: TensorBuffer
    private lateinit var amountBuffer: TensorBuffer
    private lateinit var classificationBuffer: TensorBuffer
    private lateinit var typeBuffer: TensorBuffer
    
    companion object {
        private const val TAG = "SmsMultiTaskClassifier"
        private const val MAX_SEQUENCE_LENGTH = 200
        private const val VOCAB_SIZE = 8000 // Must match training vocab_size
        
        // BIO tagging constants
        private const val BIO_OUTSIDE = 0
        private const val BIO_BEGINNING = 1
        private const val BIO_INSIDE = 2
    }
    
    /**
     * Load the multi-task TFLite model
     */
    fun loadModel(modelPath: String): Boolean {
        return try {
            Log.d(TAG, "Attempting to load model: $modelPath")
            
            // Check if model file exists in assets
            try {
                context.assets.open(modelPath).use { 
                    Log.d(TAG, "Model file found in assets: $modelPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model file NOT found in assets: $modelPath", e)
                return false
            }
            
            val modelBuffer = loadModelFile(modelPath)
            Log.d(TAG, "Model buffer loaded, size: ${modelBuffer.capacity()} bytes")
            
            val options = Interpreter.Options()
            options.setNumThreads(1)
            
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "Interpreter created successfully")
            
            // Validate the loaded model
            if (interpreter == null) {
                Log.e(TAG, "Failed to create interpreter")
                return false
            }
            
            // Check model input/output details
            val inputCount = interpreter?.inputTensorCount ?: 0
            val outputCount = interpreter?.outputTensorCount ?: 0
            
            if (inputCount == 0 || outputCount == 0) {
                Log.e(TAG, "Invalid model: inputCount=$inputCount, outputCount=$outputCount")
                return false
            }
            
            // Log model details for debugging
            for (i in 0 until inputCount) {
                val inputTensor = interpreter?.getInputTensor(i)
                Log.d(TAG, "Input $i: name=${inputTensor?.name()}, shape=${inputTensor?.shape()?.contentToString()}, type=${inputTensor?.dataType()}")
                // Update input tensor name if found
                inputTensor?.name()?.let { name ->
                    if (name.contains("input_ids")) {
                        inputTensorName = name
                        Log.d(TAG, "Using input tensor name: $inputTensorName")
                    }
                }
            }
            
            for (i in 0 until outputCount) {
                val outputTensor = interpreter?.getOutputTensor(i)
                Log.d(TAG, "Output $i: name=${outputTensor?.name()}, shape=${outputTensor?.shape()?.contentToString()}, type=${outputTensor?.dataType()}")
            }
            
            // Log the actual tensor names for debugging
            Log.d(TAG, "Actual output tensor names:")
            for (i in 0 until outputCount) {
                val outputTensor = interpreter?.getOutputTensor(i)
                Log.d(TAG, "  Output $i: ${outputTensor?.name()}")
            }
            
            // Validate this is the correct multi-task model
            if (!validateMultiTaskModel()) {
                Log.e(TAG, "Model validation failed - this is not the expected multi-task model")
                interpreter?.close()
                interpreter = null
                return false
            }
            
            // Try to detect output tensor names based on shapes
            detectOutputTensorNames()
            
            // Initialize output buffers based on model structure
            initializeOutputBuffers()
            
            isModelLoaded = true
            Log.d(TAG, "Multi-task model loaded successfully: $inputCount inputs, $outputCount outputs")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load multi-task model: ${e.message}", e)
            isModelLoaded = false
            interpreter = null
            false
        }
    }
    
    /**
     * Load the multi-task model only (no fallback to incompatible models)
     */
    fun loadModelWithFallback(): Boolean {
        Log.d(TAG, "=== MODEL LOADING START ===")
        Log.d(TAG, "loadModelWithFallback() method called")
        
        // Only try the multi-task model
        val success = loadModel("sms_multi_task.tflite")
        
        if (success) {
            Log.d(TAG, "Multi-task model loaded successfully")
            Log.d(TAG, "isModelLoaded: $isModelLoaded")
            Log.d(TAG, "interpreter null: ${interpreter == null}")
            return true
        }
        
        Log.e(TAG, "Multi-task model failed to load, will use regex fallback only")
        Log.e(TAG, "isModelLoaded: $isModelLoaded")
        Log.e(TAG, "interpreter null: ${interpreter == null}")
        return false
    }
    
    /**
     * Initialize output buffers based on model structure
     */
    private fun initializeOutputBuffers() {
        try {
            directionBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 3), org.tensorflow.lite.DataType.FLOAT32)
            merchantBuffer = TensorBuffer.createFixedSize(intArrayOf(1, MAX_SEQUENCE_LENGTH, 3), org.tensorflow.lite.DataType.FLOAT32)
            amountBuffer = TensorBuffer.createFixedSize(intArrayOf(1, MAX_SEQUENCE_LENGTH, 3), org.tensorflow.lite.DataType.FLOAT32)
            classificationBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), org.tensorflow.lite.DataType.FLOAT32)
            typeBuffer = TensorBuffer.createFixedSize(intArrayOf(1, MAX_SEQUENCE_LENGTH, 3), org.tensorflow.lite.DataType.FLOAT32)
            
            Log.d(TAG, "Output buffers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize output buffers: ${e.message}", e)
        }
    }
    
    /**
     * Detect output tensor names based on their shapes
     */
    private fun detectOutputTensorNames() {
        val outputCount = interpreter?.outputTensorCount ?: 0
        val detectedNames = mutableMapOf<String, String>()
        
        for (i in 0 until outputCount) {
            val outputTensor = interpreter?.getOutputTensor(i)
            val shape = outputTensor?.shape()
            val name = outputTensor?.name() ?: "output_$i"
            
            when {
                shape?.contentEquals(intArrayOf(1, 3)) == true -> {
                    detectedNames["direction"] = name
                    Log.d(TAG, "Detected direction tensor: $name")
                }
                shape?.contentEquals(intArrayOf(1, 1)) == true -> {
                    detectedNames["classification"] = name
                    Log.d(TAG, "Detected classification tensor: $name")
                }
                shape?.contentEquals(intArrayOf(1, MAX_SEQUENCE_LENGTH, 3)) == true -> {
                    // Need to determine which NER tensor this is based on position or name
                    when (i) {
                        1 -> detectedNames["merchant_ner"] = name
                        2 -> detectedNames["amount_ner"] = name
                        4 -> detectedNames["type_ner"] = name
                        else -> {
                            // Try to guess based on name
                            when {
                                name.contains("merchant") -> detectedNames["merchant_ner"] = name
                                name.contains("amount") -> detectedNames["amount_ner"] = name
                                name.contains("type") -> detectedNames["type_ner"] = name
                                else -> Log.w(TAG, "Unknown NER tensor at index $i: $name")
                            }
                        }
                    }
                }
            }
        }
        
        // Update output tensor names if we detected them
        if (detectedNames.isNotEmpty()) {
            outputTensorNames = detectedNames.toMap()
            Log.d(TAG, "Updated output tensor names: $outputTensorNames")
        }
    }
    
    /**
     * Analyze SMS text and extract transaction information
     */
    @Synchronized
    fun analyzeSms(smsText: String): SmsAnalysis? {
        Log.d(TAG, "=== SMS ANALYSIS START ===")
        Log.d(TAG, "isModelLoaded: $isModelLoaded")
        Log.d(TAG, "interpreter null: ${interpreter == null}")
        
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, attempting to load from assets...")
            val loaded = loadModelWithFallback()
            if (!loaded || interpreter == null) {
                Log.w(TAG, "Model still not available, using fallback analysis")
                return analyzeSmsFallback(smsText)
            }
        }
        
        return try {
            val input = preprocessText(smsText)
            val outputs = runInference(input)
            if (outputs != null) {
                postprocessOutputs(outputs, smsText)
            } else {
                Log.w(TAG, "AI inference failed, using fallback analysis")
                analyzeSmsFallback(smsText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing SMS: ${e.message}")
            Log.w(TAG, "Using fallback analysis due to AI failure")
            analyzeSmsFallback(smsText)
        }
    }
    
    /**
     * Fallback SMS analysis using basic regex patterns when AI fails
     */
    private fun analyzeSmsFallback(smsText: String): SmsAnalysis {
        val text = smsText.lowercase(Locale.ROOT)
        
        // Basic amount detection
        val amountMatch = Regex("""(?:₹|rs\.?|inr|rupees?)\s*([0-9,]+(?:\.\d{2})?)""").find(text)
        val amount = amountMatch?.groupValues?.get(1)
        
        // Basic transaction type detection
        val transactionType = when {
            text.contains("upi") -> "UPI"
            text.contains("imps") -> "IMPS"
            text.contains("neft") -> "NEFT"
            text.contains("rtgs") -> "RTGS"
            text.contains("pos") -> "POS"
            text.contains("atm") -> "ATM"
            text.contains("card") -> "CARD"
            else -> null
        }
        
        // Basic direction detection
        val direction = when {
            text.contains("debited") || text.contains("withdrawn") || text.contains("deducted") -> TransactionDirection.DEBIT
            text.contains("credited") || text.contains("deposited") || text.contains("received") -> TransactionDirection.CREDIT
            else -> TransactionDirection.NONE
        }
        
        // Basic merchant detection (try to extract from common patterns)
        val merchant = extractMerchantFallback(text)
        
        // Determine if transactional based on presence of amount and direction
        val isTransactional = amount != null && direction != TransactionDirection.NONE
        
        return SmsAnalysis(
            isTransactional = isTransactional,
            confidence = if (isTransactional) 0.8f else 0.3f,
            merchant = merchant,
            amount = amount,
            transactionType = transactionType,
            direction = direction,
            directionConfidence = if (direction != TransactionDirection.NONE) 0.7f else 0.0f
        )
    }
    
    /**
     * Extract merchant name using fallback patterns
     */
    private fun extractMerchantFallback(text: String): String? {
        // Common merchant patterns
        val patterns = listOf(
            Regex("""from\s+([a-zA-Z\s]+?)(?:\s+upi|$)"""),
            Regex("""to\s+([a-zA-Z\s]+?)(?:\s+upi|$)"""),
            Regex("""([a-zA-Z\s]+?)\s+upi"""),
            Regex("""([a-zA-Z\s]+?)\s+pos"""),
            Regex("""([a-zA-Z\s]+?)\s+transaction""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 2 && merchant.length < 50) {
                    return merchant
                }
            }
        }
        
        return null
    }
    
    /**
     * Preprocess text to token IDs
     */
    private fun preprocessText(text: String): IntArray {
        val cleaned = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
        
        val tokens = cleaned.split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .take(MAX_SEQUENCE_LENGTH)
        
        val tokenIds = tokens.map { token ->
            // Simple hash-based tokenization (fallback since no vocab file)
            (token.hashCode() % VOCAB_SIZE).let { if (it < 0) it + VOCAB_SIZE else it }
        }.toMutableList()
        
        // Pad to MAX_SEQUENCE_LENGTH
        while (tokenIds.size < MAX_SEQUENCE_LENGTH) {
            tokenIds.add(0) // PAD token
        }
        
        return tokenIds.toIntArray()
    }
    
    /**
     * Run inference on the model - CORRECTED version based on model development
     */
    @Synchronized
    private fun runInference(inputIds: IntArray): Map<String, FloatArray>? {
        return try {
            Log.d(TAG, "=== INFERENCE DEBUG START ===")
            Log.d(TAG, "Input IDs size: ${inputIds.size}")
            Log.d(TAG, "MAX_SEQUENCE_LENGTH: $MAX_SEQUENCE_LENGTH")
            
            // Validate input length and pad if necessary
            val paddedTokens = if (inputIds.size > MAX_SEQUENCE_LENGTH) {
                inputIds.take(MAX_SEQUENCE_LENGTH).toIntArray()
            } else if (inputIds.size < MAX_SEQUENCE_LENGTH) {
                inputIds + IntArray(MAX_SEQUENCE_LENGTH - inputIds.size) { 0 } // Pad with 0
            } else {
                inputIds
            }

            Log.d(TAG, "Padded tokens size: ${paddedTokens.size}")
            Log.d(TAG, "First 10 tokens: ${paddedTokens.take(10).joinToString()}")

            // Create input tensor with proper shape and data type
            // TensorBuffer doesn't support INT32, so we need to use ByteBuffer directly
            val inputBuffer = ByteBuffer.allocateDirect(4 * MAX_SEQUENCE_LENGTH) // 4 bytes per int
            inputBuffer.order(ByteOrder.nativeOrder())
            
            // Load INT32 tokens into ByteBuffer
            for (token in paddedTokens) {
                inputBuffer.putInt(token)
            }
            inputBuffer.rewind()
            
            Log.d(TAG, "ByteBuffer created successfully for INT32 input")
            Log.d(TAG, "First 5 tokens in ByteBuffer: ${paddedTokens.take(5).joinToString ()}")

            // Log input details for debugging
            Log.d(TAG, "Input buffer capacity: ${inputBuffer.capacity()}")
            Log.d(TAG, "Input buffer remaining: ${inputBuffer.remaining()}")
            Log.d(TAG, "Input data size: ${paddedTokens.size}")

            // CORRECTED: Use proper input/output mapping based on actual model
            val inputs = mapOf("serving_default_input_ids:0" to inputBuffer)

            // Reset output buffers' positions before each run to avoid BufferOverflow
            directionBuffer.buffer.clear()
            merchantBuffer.buffer.clear()
            amountBuffer.buffer.clear()
            classificationBuffer.buffer.clear()
            typeBuffer.buffer.clear()

            // CORRECTED: Output order matches actual model - using integer indices
            val outputMap = mutableMapOf<Int, Any>()
            outputMap[0] = directionBuffer.buffer      // direction [1, 3]
            outputMap[1] = merchantBuffer.buffer       // merchant_ner [1, 200, 3]
            outputMap[2] = amountBuffer.buffer         // amount_ner [1, 200, 3]
            outputMap[3] = classificationBuffer.buffer // classification [1, 1]
            outputMap[4] = typeBuffer.buffer           // type_ner [1, 200, 3]

            Log.d(TAG, "Output map size: ${outputMap.size}")
            Log.d(TAG, "Output buffer sizes:")
            Log.d(TAG, "  Direction: ${directionBuffer.buffer.capacity()}")
            Log.d(TAG, "  Merchant: ${merchantBuffer.buffer.capacity()}")
            Log.d(TAG, "  Amount: ${amountBuffer.buffer.capacity()}")
            Log.d(TAG, "  Classification: ${classificationBuffer.buffer.capacity()}")
            Log.d(TAG, "  Type: ${typeBuffer.buffer.capacity()}")

            // Run inference with error handling
            interpreter?.let { interp ->
                try {
                    Log.d(TAG, "Running inference with proper input/output mapping")
                    Log.d(TAG, "About to call runForMultipleInputsOutputs...")
                    interp.runForMultipleInputsOutputs(inputs.values.toTypedArray(), outputMap)
                    Log.d(TAG, "Inference completed successfully!")
                    
                    // Extract results
                    val results = mapOf(
                        "direction" to directionBuffer.floatArray,
                        "merchant_ner" to merchantBuffer.floatArray,
                        "amount_ner" to amountBuffer.floatArray,
                        "classification" to classificationBuffer.floatArray,
                        "type_ner" to typeBuffer.floatArray
                    )
                    Log.d(TAG, "Results extracted successfully")
                    Log.d(TAG, "=== INFERENCE DEBUG END ===")
                    results
                } catch (e: Exception) {
                    Log.e(TAG, "Inference failed with error: ${e.message}", e)
                    Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "Stack trace: ${e.stackTrace.take(5).joinToString("\n")}")
                    null
                }
            } ?: throw IllegalStateException("Interpreter not initialized")

        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            null
        }
    }
    

    
    /**
     * Postprocess model outputs to extract meaningful information
     */
    private fun postprocessOutputs(outputs: Map<String, FloatArray>, originalText: String): SmsAnalysis {
        val classification = outputs["classification"]?.get(0) ?: 0f
        val direction = outputs["direction"] ?: FloatArray(3)
        val merchantNer = outputs["merchant_ner"] ?: FloatArray(MAX_SEQUENCE_LENGTH * 3)
        val amountNer = outputs["amount_ner"] ?: FloatArray(MAX_SEQUENCE_LENGTH * 3)
        val typeNer = outputs["type_ner"] ?: FloatArray(MAX_SEQUENCE_LENGTH * 3)
        
        // Determine if this is a transactional SMS
        val isTransactional = classification > 0.5f
        
        // Extract direction (DEBIT/CREDIT/NONE)
        val directionIndex = direction.indices.maxByOrNull { direction[it] } ?: 2
        val transactionDirection = when (directionIndex) {
            0 -> TransactionDirection.DEBIT
            1 -> TransactionDirection.CREDIT
            else -> TransactionDirection.NONE
        }
        val directionConfidence = direction[directionIndex]
        
        // Extract entities using BIO tagging
        val merchant = extractEntity(merchantNer, originalText, "merchant")
        val amount = extractEntity(amountNer, originalText, "amount")
        val transactionType = extractEntity(typeNer, originalText, "type")
        
        return SmsAnalysis(
            isTransactional = isTransactional,
            confidence = classification,
            merchant = merchant,
            amount = amount,
            transactionType = transactionType,
            direction = transactionDirection,
            directionConfidence = directionConfidence
        )
    }
    
    /**
     * Extract entity from BIO-tagged output (3D tensor [1, 200, 3])
     */
    private fun extractEntity(nerOutput: FloatArray, text: String, entityType: String): String? {
        val tokens = text.split("\\s+".toRegex())
        val entities = mutableListOf<String>()
        var currentEntity = StringBuilder()
        var inEntity = false
        
        // Validate input size - should be [1, 200, 3] = 600 elements
        if (nerOutput.size != MAX_SEQUENCE_LENGTH * 3) {
            Log.w(TAG, "NER output size mismatch for $entityType: expected ${MAX_SEQUENCE_LENGTH * 3}, got ${nerOutput.size}")
            return null
        }
        
        for (i in tokens.indices.take(MAX_SEQUENCE_LENGTH)) {
            // For 3D tensor [1, 200, 3], each token has 3 probabilities at consecutive indices
            val startIdx = i * 3
            if (startIdx + 2 < nerOutput.size) {
                val probs = floatArrayOf(
                    nerOutput[startIdx],     // O (Outside)
                    nerOutput[startIdx + 1], // B (Beginning)
                    nerOutput[startIdx + 2]  // I (Inside)
                )
                
                // Find the tag with highest probability
                val tag = probs.indices.maxByOrNull { probs[it] } ?: 0
                
                when (tag) {
                    BIO_BEGINNING -> {
                        // If we were already in an entity, save it
                        if (inEntity) {
                            val entity = currentEntity.toString().trim()
                            if (entity.isNotEmpty()) {
                                entities.add(entity)
                            }
                        }
                        // Start new entity
                        currentEntity = StringBuilder(tokens[i])
                        inEntity = true
                    }
                    BIO_INSIDE -> {
                        // Continue current entity
                        if (inEntity) {
                            currentEntity.append(" ").append(tokens[i])
                        }
                    }
                    BIO_OUTSIDE -> {
                        // End current entity if any
                        if (inEntity) {
                            val entity = currentEntity.toString().trim()
                            if (entity.isNotEmpty()) {
                                entities.add(entity)
                            }
                            currentEntity = StringBuilder()
                            inEntity = false
                        }
                    }
                }
            }
        }
        
        // Add final entity if any
        if (inEntity) {
            val entity = currentEntity.toString().trim()
            if (entity.isNotEmpty()) {
                entities.add(entity)
            }
        }
        
        Log.d(TAG, "Extracted $entityType entities: $entities")
        return entities.firstOrNull()
    }
    
    /**
     * Validate that the loaded model is the correct multi-task model
     */
    private fun validateMultiTaskModel(): Boolean {
        val inputCount = interpreter?.inputTensorCount ?: 0
        val outputCount = interpreter?.outputTensorCount ?: 0
        
        // Multi-task model should have 1 input and 5 outputs
        if (inputCount != 1 || outputCount != 5) {
            Log.e(TAG, "Invalid model structure: expected 1 input, 5 outputs; got $inputCount inputs, $outputCount outputs")
            return false
        }
        
        // Check input tensor
        val inputTensor = interpreter?.getInputTensor(0)
        val inputShape = inputTensor?.shape()
        val inputName = inputTensor?.name()
        
        if (inputShape == null || !inputShape.contentEquals(intArrayOf(1, 200))) {
            Log.e(TAG, "Invalid input shape: expected [1, 200], got ${inputShape?.contentToString()}")
            return false
        }
        
        if (inputName == null || !inputName.contains("input_ids")) {
            Log.e(TAG, "Invalid input name: expected to contain 'input_ids', got '$inputName'")
            return false
        }
        
        // Check output tensors - validate each output individually
        Log.d(TAG, "Validating output shapes...")
        
        // Expected shapes in order
        val expectedShapes = arrayOf(
            intArrayOf(1, 3),      // Output 0: direction
            intArrayOf(1, 200, 3), // Output 1: merchant_ner
            intArrayOf(1, 200, 3), // Output 2: amount_ner
            intArrayOf(1, 1),      // Output 3: classification
            intArrayOf(1, 200, 3)  // Output 4: type_ner
        )
        
        for (i in 0 until outputCount) {
            val outputTensor = interpreter?.getOutputTensor(i)
            val outputShape = outputTensor?.shape()
            
            Log.d(TAG, "Output $i shape: ${outputShape?.contentToString()}")
            
            if (outputShape == null) {
                Log.e(TAG, "Output $i has null shape")
                return false
            }
            
            // Check if this shape matches the expected shape for this output
            val expectedShape = expectedShapes[i]
            if (!outputShape.contentEquals(expectedShape)) {
                Log.e(TAG, "Output $i shape mismatch: expected ${expectedShape.contentToString()}, got ${outputShape.contentToString()}")
                return false
            }
            
            Log.d(TAG, "Output $i shape validation passed: ${outputShape.contentToString()}")
        }
        
        Log.d(TAG, "Multi-task model validation passed")
        return true
    }
    
    /**
     * Load model file from assets
     */
    private fun loadModelFile(modelPath: String): ByteBuffer {
        val afd = context.assets.openFd(modelPath)
        afd.use { fd ->
            return FileUtil.loadMappedFile(context, modelPath)
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}

/**
 * Data class for SMS analysis results
 */
data class SmsAnalysis(
    val isTransactional: Boolean,
    val confidence: Float,
    val merchant: String?,
    val amount: String?,
    val transactionType: String?,
    val direction: TransactionDirection,
    val directionConfidence: Float
)

/**
 * Enum for transaction direction
 */
enum class TransactionDirection {
    DEBIT, CREDIT, NONE
}
