package com.example.lensly.screen

import android.view.accessibility.AccessibilityNodeInfo

/**
 * NodeTraverser — traverses the Android Accessibility node tree to find product cards.
 *
 * Detection strategy:
 *   Product cards are typically groups of repeated sibling nodes at the same depth,
 *   each containing a price-like text child. This is more robust than hardcoded
 *   selectors and handles A/B test UI variations gracefully.
 */
object NodeTraverser {

    private val pricePattern = Regex("""₹\s*\d+|Rs\.?\s*\d+|\d+\.\d{2}""")
    private val weightPattern = Regex(
        """\d+\s*(?:kg|g|gm|gram|grams|l|ltr|litre|ml)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts raw text blocks from product cards in the accessibility tree.
     * Each item in the returned list represents one product card's combined text.
     *
     * @param root  The root AccessibilityNodeInfo of the active window
     * @return List of raw text strings, one per detected product card
     */
    fun extractProductCardTexts(root: AccessibilityNodeInfo): List<String> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectProductCandidates(root, candidates)
        return candidates.map { node -> extractTextFromSubtree(node) }
            .filter { it.isNotBlank() }
    }

    /**
     * Recursively traverses the node tree, collecting nodes that look like product cards.
     *
     * A node is treated as a product card root when:
     *   - It has multiple children (typically 3+)
     *   - Its subtree contains both a price pattern and a weight/volume pattern
     *   - It is not itself a leaf node (has children)
     */
    private fun collectProductCandidates(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val subtreeText = extractTextFromSubtree(node)

        // A card candidate must have price + weight info in its subtree
        val hasPrice = pricePattern.containsMatchIn(subtreeText)
        val hasWeight = weightPattern.containsMatchIn(subtreeText)

        if (hasPrice && hasWeight && node.childCount in 2..15) {
            results.add(node)
            return  // Don't recurse into confirmed cards to avoid duplicates
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectProductCandidates(child, results)
        }
    }

    /**
     * Collects all visible text from a node's subtree.
     */
    fun extractTextFromSubtree(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        collectText(node, sb)
        return sb.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb)
        }
    }

    /**
     * Finds all nodes containing specific text (for Action Discovery Engine).
     */
    fun findNodesByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findByText(root, text.lowercase(), results)
        return results
    }

    private fun findByText(
        node: AccessibilityNodeInfo,
        text: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
        if (nodeText.contains(text)) results.add(node)
        for (i in 0 until node.childCount) {
            findByText(node.getChild(i) ?: continue, text, results)
        }
    }
}
