package org.evomaster.core.mongo.filter

import org.bson.Document

class DistanceEvaluator: FilterVisitor<Double,Document>() {

    override fun visit(comparisonFilter: ComparisonFilter<*>, argument: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: AndFilter, argument: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: OrFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(allFilter: AllFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(elemMatchFilter: ElemMatchFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: SizeFilter, argument: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: InFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: NotInFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: NorFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: ExistsFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: NotExistsFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: RegexFilter, argument: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: SearchFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: WhereFilter, arg: Document): Double {
        TODO("Not yet implemented")
    }

    override fun visit(filter: ModFilter, argument: Document): Double {
        TODO("Not yet implemented")
    }

}