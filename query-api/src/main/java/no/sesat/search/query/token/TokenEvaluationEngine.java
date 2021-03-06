/*
 * Copyright (2005-2012) Schibsted ASA
 * This file is part of Possom.
 *
 *   Possom is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Lesser General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Possom is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public License
 *   along with Possom.  If not, see <http://www.gnu.org/licenses/>.
 */
package no.sesat.search.query.token;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import no.sesat.commons.ioc.BaseContext;
import no.sesat.search.query.Clause;
import no.sesat.search.query.Query;
import no.sesat.search.site.config.ResourceContext;
import no.sesat.search.query.QueryStringContext;
import no.sesat.search.site.Site;
import no.sesat.search.site.SiteContext;

/**
 * TokenEvaluationEngine contains state as to what is the current term being tokenised,
 * and the term's sets of known and possible predicates.
 * These sets can be in building process, and provide performance improvement by not having to
 * evaluate the token twice.
 *
 * A TokenEvaluationEngine also provides knowledge about which implementation of
 * {@link TokenEvaluator} that can handle a particular token {@link TokenPredicate}.
 *
 *
 *
 *
 * @version <tt>$Revision$</tt>
 */
public interface TokenEvaluationEngine {

    interface Context extends BaseContext, QueryStringContext, ResourceContext, SiteContext{
        /** A uniqueId for the request.
         * Is not mission critical, ie can be left leave blank.
         * @return the uniqueId
         */
        String getUniqueId();
    }

    /**
     * Evaluator that will return false under all circumstances.
     */
    static final TokenEvaluator ALWAYS_FALSE_EVALUATOR = new TokenEvaluator() {
        @Override
        public boolean evaluateToken(final TokenPredicate token, final String term, final String query) {
            return false;
        }
        @Override
        public boolean isQueryDependant(final TokenPredicate predicate) {
            return false;
        }
        @Override
        public Set<String> getMatchValues(final TokenPredicate token, final String term) {
            return Collections.<String>emptySet();
        }
    };

    /**
     * Evaluator that will return true under all circumstances.
     */
    static final TokenEvaluator ALWAYS_TRUE_EVALUATOR = new TokenEvaluator() {
        @Override
        public boolean evaluateToken(final TokenPredicate token, final String term, final String query) {
            return true;
        }
        @Override
        public boolean isQueryDependant(final TokenPredicate predicate) {
            return false;
        }
        @Override
        public Set<String> getMatchValues(final TokenPredicate token, final String term) {
            return Collections.<String>emptySet();
        }
    };

    /**
     * Evaluator that will throws an EvaluationException under all circumstances.
     */
    static final TokenEvaluator DEAD_EVALUATOR = new TokenEvaluator() {
        @Override
        public boolean evaluateToken(final TokenPredicate token, final String term, final String query) {
            throw new EvaluationRuntimeException(
                    new EvaluationException("DEAD_EVALUATOR", null));
        }
        @Override
        public boolean isQueryDependant(final TokenPredicate predicate) {
            return false;
        }
        @Override
        public Set<String> getMatchValues(final TokenPredicate token, final String term) {
            return Collections.<String>emptySet();
        }
    };

    /**
     * Holder for evaluation state during the engine's evaluation.
     * Evaluation on any term, clause, or query requires state of the current term and query,
     *  and of the already matched known and possible predicates.
     */
    interface State extends Serializable {
        /** the current clause's term, or null if in query-evaluation mode. *
         * @return
         */
        String getTerm();
        /** the current query, or null if in term-evaluation mode. *
         * @return
         */
        Query getQuery();
        /** known matching predicates. by making this available performance is improved. *
         * @return
         */
        Set<TokenPredicate> getKnownPredicates();
        /** possible matching predicates. by making this available performance is improved. *
         * @return
         */
        Set<TokenPredicate> getPossiblePredicates();
    }

    /** Find or create the TokenEvaluator that will evaluate if given (Token)Predicate is true.
     *
     * @param token
     * @return
     * @throws EvaluationException if something goes wrong constructing or finding an appropriate evaluator.
     */
    TokenEvaluator getEvaluator(TokenPredicate token) throws EvaluationException;

    /**
     * The query string we're evaluating. May be null if we're only evaluating a term.
     *
     * @return
     */
    String getQueryString();

     /** The site the evaluation's request is against
      *
      * @return
      */
    Site getSite();

    /** The real evaluation method all other evaluate...(..) methods will delegate to.
     *
     * @param token
     * @return
     */
    boolean evaluate(TokenPredicate token) throws EvaluationException;

    /** Utility method to perform one-off evaluations on terms from non RunningQuery threads.
     * Typically used by TokenTransformers or performing evaluations on non-clause oriented strings.
     *
     * @param predicate
     * @param term
     * @return
     */
    boolean evaluateTerm(TokenPredicate predicate, String term) throws EvaluationException;

    /** Utility method to perform one-off evaluations on clauses from non RunningQuery threads.
     * Typically used by TokenTransformers or performing evaluations on non-clause oriented strings.
     *
     * @param predicate
     * @param clause
     * @return
     */
    boolean evaluateClause(TokenPredicate predicate, Clause clause) throws EvaluationException;

    /** Utility method to perform one-off evaluations on queries from non RunningQuery threads.
     * Typically used by TokenTransformers or performing evaluations on non-clause oriented strings.
     *
     * @param predicate
     * @param query
     * @return
     */
    boolean evaluateQuery(TokenPredicate predicate, Query query) throws EvaluationException;

    /**
     * Getter for property state.
     * @return Value of property state.
     */
    State getState();

    /**
     * Setter for property state.
     * @param state New value of property state.
     */
    void setState(State state);


}
