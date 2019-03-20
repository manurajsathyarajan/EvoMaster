package org.evomaster.core.search

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.impact.ImpactOfGene
import org.evomaster.core.search.service.tracer.Impact
import org.evomaster.core.search.service.tracer.TraceableElement
import org.evomaster.core.search.service.tracer.WithImpacts

/**
 * An individual for the search.
 * Also called "chromosome" in evolutionary algorithms.
 * In our context, most of the time an Individual will represent
 * a single test case, composed by 1 or more "actions" (eg, calls
 * to a RESTful API, SQL operations on a database or WireMock setup)
 *
 * Individual allows to track its evolution, created by sampler, changed by mutator or crossover
 * @property description presents an operator to change an individual, e.g., mutator
 * @property track is a list of Individual, indicating its evolution
 */
abstract class Individual : TraceableElement, WithImpacts{

    constructor() : super()
    constructor(description : String) : super(description)
    constructor(description : String, traces : MutableList<out Individual>) : super(description, traces)

    /**
     * Make a deep next of this individual
     */
    abstract fun copy(): Individual

    override fun copy(withTrack: Boolean): TraceableElement {
        return copy()
    }
    enum class GeneFilter { ALL, NO_SQL, ONLY_SQL }

    /**
     * Return a view of all the Genes in this chromosome/individual
     */
    abstract fun seeGenes(filter: GeneFilter = GeneFilter.ALL): List<out Gene>

    /**
     * An estimation of the "size" of this individual.
     * Longer/bigger individuals are usually considered worse,
     * unless they cover more coverage targets
     */
    abstract fun size(): Int

    /**
     * Return a view of all the "actions" defined in this individual.
     * Note: each action could be composed by 0 or more genes
     */
    abstract fun seeActions(): List<out Action>

    /**
     * Return a view of all initializing actions done before the main
     * ones. Example: these could set up database before doing HTTP
     * calls toward the SUT.
     * A test does not require to have initializing actions.
     */
    open fun seeInitializingActions(): List<Action> = listOf()

    /**
     * Determine if the structure (ie the actions) of this individual
     * can be mutated (eg, add/remove actions).
     * Note: even if this is false, it would still be possible to
     * mutate the genes in those actions
     */
    open fun canMutateStructure() = false


    /**
     * Returns true if the initialization actions
     * are correct (i.e. all constraints are satisfied)
     */
    abstract fun verifyInitializationActions(): Boolean;

    /**
     * Attempts to repair the initialization actions.
     * Initialization actions must pass the verifyInitializationAction()
     * test after this method is invoked.
     */
    abstract fun repairInitializationActions(randomness: Randomness);

    open fun seeGenesIdMap() : Map<Gene, String>{
        return seeActions().flatMap { a -> a.seeGenes().map { it to ImpactOfGene.generateId(a, it)}}.toMap()
    }

}

