package com.example.lensly.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lensly.screen.NodeTraverser

/**
 * ActionDiscoveryEngine — scans the current Accessibility tree for
 * interactive UI elements that can reduce our parsing workload.
 *
 * Philosophy: Use what the app already built before we do any heavy lifting.
 *   - Sort by price → we only parse the top-N cheapest products
 *   - Filter by category → fewer irrelevant products
 *   - Search → go straight to the right products
 *
 * Output: CapabilityMap describing what the current screen can do.
 */
object ActionDiscoveryEngine {

    /**
     * Scans the root node and returns a CapabilityMap for the current screen.
     */
    fun discover(root: AccessibilityNodeInfo): CapabilityMap {
        val sortNodes = findSortControls(root)
        val filterNodes = findFilterControls(root)
        val searchNode = findSearchBar(root)
        val paginationNode = findPagination(root)

        return CapabilityMap(
            canSort = sortNodes.isNotEmpty(),
            sortOptions = sortNodes.mapNotNull { extractText(it) },
            canFilter = filterNodes.isNotEmpty(),
            filterOptions = filterNodes.mapNotNull { extractText(it) },
            hasSearchBar = searchNode != null,
            hasPagination = paginationNode != null,
            rawSortNodes = sortNodes,
            rawSearchNode = searchNode
        )
    }

    // --- Sort control detection ---

    private val sortKeywords = listOf("sort", "order by", "sort by", "price: low", "price low")
    private val sortOptionKeywords = listOf(
        "price", "low to high", "high to low", "popularity",
        "rating", "relevance", "newest", "discount"
    )

    private fun findSortControls(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        // Look for spinner/dropdown with sort-related text
        sortKeywords.forEach { keyword ->
            results += NodeTraverser.findNodesByText(root, keyword)
        }
        return results.distinctBy { extractText(it) }
    }

    // --- Filter control detection ---

    private val filterKeywords = listOf("filter", "refine", "category", "brand", "type")

    private fun findFilterControls(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        filterKeywords.forEach { keyword ->
            results += NodeTraverser.findNodesByText(root, keyword)
        }
        return results.distinctBy { extractText(it) }
    }

    // --- Search bar detection ---

    private val searchHints = listOf("search", "find products", "what are you looking for")

    private fun findSearchBar(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (hint in searchHints) {
            val nodes = NodeTraverser.findNodesByText(root, hint)
            val editText = nodes.firstOrNull {
                it.className?.contains("EditText") == true || it.isEditable
            }
            if (editText != null) return editText
        }
        return null
    }

    // --- Pagination detection ---

    private fun findPagination(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return NodeTraverser.findNodesByText(root, "load more").firstOrNull()
            ?: NodeTraverser.findNodesByText(root, "next page").firstOrNull()
            ?: NodeTraverser.findNodesByText(root, "show more").firstOrNull()
    }

    private fun extractText(node: AccessibilityNodeInfo): String? {
        return node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Attempts to activate "Sort by Price" on the current screen.
     * Returns true if the action was performed.
     */
    fun sortByPrice(capabilityMap: CapabilityMap): Boolean {
        val priceNode = capabilityMap.rawSortNodes.firstOrNull { node ->
            val text = extractText(node)?.lowercase() ?: ""
            text.contains("price") || text.contains("low to high")
        } ?: return false

        return priceNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}

/**
 * Describes what interactive capabilities the current screen has.
 */
data class CapabilityMap(
    val canSort: Boolean,
    val sortOptions: List<String>,
    val canFilter: Boolean,
    val filterOptions: List<String>,
    val hasSearchBar: Boolean,
    val hasPagination: Boolean,
    // Internal refs for action execution — not serialized
    val rawSortNodes: List<AccessibilityNodeInfo> = emptyList(),
    val rawSearchNode: AccessibilityNodeInfo? = null
)
