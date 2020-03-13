package org.evomaster.core.mongo.filter

/**
 *  A filter that matches all documents matching the given search term.
 */
class SearchFilter(
        val search: String) : ASTNodeFilter() {

    override fun <T, K> accept(visitor: FilterVisitor<T, K>, arg: K): T {
        return visitor.visit(this, arg)
    }

}