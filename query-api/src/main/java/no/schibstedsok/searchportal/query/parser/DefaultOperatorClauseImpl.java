/*
 * Copyright (2005-2006) Schibsted Søk AS
 */
package no.schibstedsok.searchportal.query.parser;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import no.schibstedsok.searchportal.query.Clause;
import no.schibstedsok.searchportal.query.DefaultOperatorClause;
import no.schibstedsok.searchportal.query.LeafClause;
import no.schibstedsok.searchportal.query.token.EvaluationState;
import no.schibstedsok.searchportal.query.token.TokenEvaluationEngine;
import no.schibstedsok.searchportal.query.token.TokenPredicate;
import no.schibstedsok.searchportal.site.Site;

/**
 * The OrClauseImpl represents a joining clause between two terms in the query.
 * For example: "term1 OR term2".
 * <b>Objects of this class are immutable</b>
 *
 * @author <a hrefOrClauseImpl:mick@wever.org">Michael Semb Wever</a>
 * @version $Id: OrClauseImpl.java 2399 2006-03-01 21:01:02Z mickw $
 */
public class DefaultOperatorClauseImpl extends AbstractOperationClause implements DefaultOperatorClause {

    /** Values are WeakReference object to AbstractClause.
     * Unsynchronized are there are no 'changing values', just existance or not of the AbstractClause in the system.
     */
    private static final Map<Site,Map<String,WeakReference<DefaultOperatorClauseImpl>>> WEAK_CACHE
            = new HashMap<Site,Map<String,WeakReference<DefaultOperatorClauseImpl>>>();

    /* A WordClause specific collection of TokenPredicates that *could* apply to this Clause type. */
    private static final Collection<TokenPredicate> PREDICATES_APPLICABLE;

    static {
        final Collection<TokenPredicate> predicates = new ArrayList();

        // Add all TokenPredicates. Unfortunately we have now way of globally knowing
        //  which TokenPredicates can be multi-term (multi-word) matches.
        predicates.addAll(TokenPredicate.getTokenPredicates());
        PREDICATES_APPLICABLE = Collections.unmodifiableCollection(predicates);
    }

    private final Clause secondClause;

    /**
     * Creator method for OrClauseImpl objects. By avoiding the constructors,
     * and assuming all OrClauseImpl objects are immutable, we can keep track
     * (via a weak reference map) of instances already in use in this JVM and reuse
     * them.
     * The methods also allow a chunk of creation logic for the OrClauseImpl to be moved
     * out of the QueryParserImpl.jj file to here.
     * 
     * @param first the left child clause of the operation clause we are about to create (or find).
     * @param second the right child clause of the operation clause we are about to create (or find).
     * @param engine the factory handing out evaluators against TokenPredicates.
     * Also holds state information about the current term/clause we are finding predicates against.
     * @return returns a OrCOrClauseImplstance matching the term, left and right child clauses.
     * May be either newly created or reused.
     */
    public static DefaultOperatorClauseImpl createDefaultOperatorClause(
        final Clause first,
        final Clause second,
        final TokenEvaluationEngine engine) {

        // construct the proper "schibstedsøk" formatted term for this operation.
        //  XXX eventually it would be nice not to have to expose the internal string representation of this object.
        final String term =
                (first instanceof LeafClause && ((LeafClause) first).getField() != null
                    ?  ((LeafClause) first).getField() + ":"
                    : "")
                + first.getTerm()
                + " "
                + (second instanceof LeafClause && ((LeafClause) second).getField() != null
                    ?  ((LeafClause) second).getField() + ":"
                    : "")
                + second.getTerm();

        try{
            // create predicate sets
            engine.setState(new EvaluationState(term, new HashSet<TokenPredicate>(), new HashSet<TokenPredicate>()));

            final String unique = '(' + term + ')';

            // the weakCache to use.
            Map<String,WeakReference<DefaultOperatorClauseImpl>> weakCache
                    = WEAK_CACHE.get(engine.getSite());

            if(weakCache == null){
                weakCache = new HashMap<String,WeakReference<DefaultOperatorClauseImpl>>();
                WEAK_CACHE.put(engine.getSite(), weakCache);
            }

            // use helper method from AbstractLeafClause
            return createClause(
                    DefaultOperatorClauseImpl.class,
                    unique,
                    first,
                    second,
                    engine,
                    PREDICATES_APPLICABLE, weakCache);

        }finally{
            engine.setState(null);
        }
    }

    /**
     * Create the OrClauseImpl with the given term, left and right child clauses, and known and possible predicate sets.
     *
     * @param term the term for this OrClauseImpl.
     * @param knownPredicates set of known predicates.
     * @param possiblePredicates set of possible predicates.
     * @param first the left child clause.
     * @param second the right child clause.
     */
    protected DefaultOperatorClauseImpl(
            final String term,
            final Clause first,
            final Clause second,
            final Set<TokenPredicate> knownPredicates,
            final Set<TokenPredicate> possiblePredicates) {

        super(term, first, knownPredicates, possiblePredicates);
        this.secondClause = second;
    }

    /**
     * Get the secondClause.
     *
     * @return the secondClause.
     */
    public Clause getSecondClause() {
        return secondClause;
    }
}