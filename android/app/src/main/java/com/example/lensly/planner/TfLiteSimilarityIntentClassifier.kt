package com.example.lensly.planner

import android.content.Context
import android.util.Log
import com.example.lensly.models.Objective
import com.example.lensly.models.QueryIntent
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.regex.Pattern

/**
 * TfLiteSimilarityIntentClassifier computes sentence embeddings using on-device MiniLM
 * and maps queries to intents using cosine similarity against predefined anchor phrases.
 */
class TfLiteSimilarityIntentClassifier(private val context: Context) : IntentClassifier {
    private val TAG = "TfLiteSimilarityClassifier"
    private var interpreter: Interpreter? = null
    private var tokenizer: BertTokenizer? = null
    private var isInitialized = false

    private data class Anchor(
        val phrase: String,
        val intentName: String,
        val objective: Objective,
        var embedding: FloatArray? = null
    )

    private val anchors = listOf(
        // FIND_CHEAPEST
        Anchor("cheapest product", "FIND_CHEAPEST", Objective.MINIMIZE_PRICE_PER_UNIT),
        Anchor("lowest price shampoo", "FIND_CHEAPEST", Objective.MINIMIZE_PRICE_PER_UNIT),
        Anchor("show cheapest detergent", "FIND_CHEAPEST", Objective.MINIMIZE_PRICE_PER_UNIT),
        
        // VALUE_FOR_MONEY
        Anchor("best toothpaste per gram", "VALUE_FOR_MONEY", Objective.BEST_VALUE),
        Anchor("highest quantity for price", "VALUE_FOR_MONEY", Objective.BEST_VALUE),
        Anchor("most value for money soap", "VALUE_FOR_MONEY", Objective.BEST_VALUE),
        
        // COMPARE_PRODUCTS
        Anchor("compare colgate and pepsodent", "COMPARE_PRODUCTS", Objective.BEST_OVERALL),
        Anchor("which is better dove or lux", "COMPARE_PRODUCTS", Objective.BEST_OVERALL),
        
        // BEST_RATED
        Anchor("highest rated face wash", "BEST_RATED", Objective.BEST_OVERALL),
        Anchor("top rated shampoo", "BEST_RATED", Objective.BEST_OVERALL),
        
        // BUDGET_SEARCH
        Anchor("snacks under 50 rupees", "BUDGET_SEARCH", Objective.MINIMIZE_PRICE_PER_UNIT),
        Anchor("toothpaste below 100", "BUDGET_SEARCH", Objective.MINIMIZE_PRICE_PER_UNIT),
        
        // CATEGORY_SEARCH
        Anchor("show me all shampoos", "CATEGORY_SEARCH", Objective.BEST_VALUE),
        Anchor("find toothpaste options", "CATEGORY_SEARCH", Objective.BEST_VALUE),
        
        // DEAL_SEARCH
        Anchor("products with discounts", "DEAL_SEARCH", Objective.DETECT_FAKE_DISCOUNT),
        Anchor("best offers today", "DEAL_SEARCH", Objective.DETECT_FAKE_DISCOUNT)
    )

    init {
        try {
            // 1. Load model file
            val modelBuffer = loadModelFile("minilm.tflite")
            interpreter = Interpreter(modelBuffer)
            
            // 2. Load tokenizer vocabulary
            val vocabMap = loadVocab("vocab.txt")
            tokenizer = BertTokenizer(vocabMap)

            // 3. Pre-calculate embeddings for anchor phrases
            precomputeAnchorEmbeddings()
            
            isInitialized = true
            Log.i(TAG, "Successfully initialized TfLiteSimilarityIntentClassifier with ${anchors.size} anchors")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MiniLM similarity classifier: ${e.message}", e)
        }
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadVocab(path: String): Map<String, Int> {
        val vocabMap = mutableMapOf<String, Int>()
        context.assets.open(path).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                val term = line.trim()
                if (term.isNotEmpty()) {
                    vocabMap[term] = index
                }
            }
        }
        return vocabMap
    }

    private fun precomputeAnchorEmbeddings() {
        for (anchor in anchors) {
            anchor.embedding = computeEmbedding(anchor.phrase)
        }
    }

    private fun computeEmbedding(text: String): FloatArray {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")
        val interp = interpreter ?: throw IllegalStateException("Interpreter not initialized")
        
        val tokens = tok.tokenize(text)
        val seqLength = tokens.size

        // Input tensors: shape [1, seqLength]
        val inputIds = Array(1) { IntArray(seqLength) }
        val attentionMask = Array(1) { IntArray(seqLength) }
        
        for (i in 0 until seqLength) {
            inputIds[0][i] = tokens[i]
            attentionMask[0][i] = 1
        }

        // Output tensor: shape [1, 384]
        val outputs = Array(1) { FloatArray(384) }

        val inputs = arrayOf<Any>(inputIds, attentionMask)
        val outputsMap = mapOf(0 to outputs)

        interp.runForMultipleInputsOutputs(inputs, outputsMap)
        
        return outputs[0]
    }

    override fun classify(rawQuery: String): QueryIntent {
        if (!isInitialized) {
            return QueryIntent(Objective.BEST_VALUE, "general", confidence = 0.0, rawQuery = rawQuery)
        }

        return try {
            val queryEmbedding = computeEmbedding(rawQuery)
            
            var bestAnchor: Anchor? = null
            var highestSimilarity = -1.0f

            for (anchor in anchors) {
                val anchorEmbedding = anchor.embedding ?: continue
                val similarity = dotProduct(queryEmbedding, anchorEmbedding)
                if (similarity > highestSimilarity) {
                    highestSimilarity = similarity
                    bestAnchor = anchor
                }
            }

            if (bestAnchor != null) {
                Log.d(TAG, "Query '$rawQuery' matched anchor '${bestAnchor.phrase}' with similarity: $highestSimilarity")
                
                // Parse budget & category from raw query
                val budget = parseBudget(rawQuery)
                val category = parseCategory(rawQuery)

                QueryIntent(
                    objective = bestAnchor.objective,
                    category = category,
                    maxPriceInr = budget,
                    rawQuery = rawQuery,
                    confidence = highestSimilarity.toDouble()
                )
            } else {
                QueryIntent(Objective.BEST_VALUE, "general", confidence = 0.0, rawQuery = rawQuery)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Similarity classification failed: ${e.message}", e)
            QueryIntent(Objective.BEST_VALUE, "general", confidence = 0.0, rawQuery = rawQuery)
        }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var result = 0.0f
        val limit = minOf(a.size, b.size)
        for (i in 0 until limit) {
            result += a[i] * b[i]
        }
        return result
    }

    private fun parseBudget(query: String): Double? {
        val q = query.lowercase()
        val pattern = Pattern.compile("(?:under|below|budget|rs\\.?|rupees?\\.?)\\s*(\\d+)")
        val matcher = pattern.matcher(q)
        if (matcher.find()) {
            return matcher.group(1)?.toDoubleOrNull()
        }
        return null
    }

    private fun parseCategory(query: String): String {
        val q = query.lowercase().trim()
        val words = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            val lastWord = words.last()
            if (lastWord.all { it.isLetter() } && lastWord !in listOf("cheap", "under", "best", "rated", "value", "money")) {
                return lastWord
            }
        }
        return "general"
    }

    /**
     * Nested custom WordPiece Tokenizer to avoid library link issues.
     */
    class BertTokenizer(private val vocab: Map<String, Int>) {
        
        fun tokenize(text: String): List<Int> {
            val tokens = mutableListOf<Int>()
            tokens.add(vocab["[CLS]"] ?: 101)

            val words = splitOnPunctuationAndWhitespace(text.lowercase())
            for (word in words) {
                val pieces = wordPieceTokenize(word)
                for (wp in pieces) {
                    tokens.add(vocab[wp] ?: (vocab["[UNK]"] ?: 100))
                }
            }

            tokens.add(vocab["[SEP]"] ?: 102)
            return tokens
        }

        private fun splitOnPunctuationAndWhitespace(text: String): List<String> {
            val result = mutableListOf<String>()
            val currentWord = StringBuilder()
            for (char in text) {
                if (char.isWhitespace()) {
                    if (currentWord.isNotEmpty()) {
                        result.add(currentWord.toString())
                        currentWord.clear()
                    }
                } else if (isPunctuation(char)) {
                    if (currentWord.isNotEmpty()) {
                        result.add(currentWord.toString())
                        currentWord.clear()
                    }
                    result.add(char.toString())
                } else {
                    currentWord.append(char)
                }
            }
            if (currentWord.isNotEmpty()) {
                result.add(currentWord.toString())
            }
            return result
        }

        private fun isPunctuation(char: Char): Boolean {
            val type = Character.getType(char)
            return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                   type == Character.DASH_PUNCTUATION.toInt() ||
                   type == Character.START_PUNCTUATION.toInt() ||
                   type == Character.END_PUNCTUATION.toInt() ||
                   type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                   type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                   type == Character.OTHER_PUNCTUATION.toInt() ||
                   (char.code in 33..47) || (char.code in 58..64) || (char.code in 91..96) || (char.code in 123..126)
        }

        private fun wordPieceTokenize(word: String): List<String> {
            val pieces = mutableListOf<String>()
            var start = 0
            var isBad = false
            while (start < word.length) {
                var end = word.length
                var curSubstr: String? = null
                while (start < end) {
                    var substr = word.substring(start, end)
                    if (start > 0) {
                        substr = "##$substr"
                    }
                    if (vocab.containsKey(substr)) {
                        curSubstr = substr
                        break
                    }
                    end--
                }
                if (curSubstr == null) {
                    isBad = true
                    break
                }
                pieces.add(curSubstr)
                start = end
            }
            if (isBad) {
                return listOf("[UNK]")
            }
            return pieces
        }
    }
}
