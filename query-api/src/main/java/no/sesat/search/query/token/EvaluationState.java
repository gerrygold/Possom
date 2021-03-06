/* Copyright (2012) Schibsted ASA
 *   This file is part of Possom.
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
 *
 * EvaluationState.java
 *
 * Created on September 9, 2006, 1:12 PM
 *
 */

package no.sesat.search.query.token;

import java.util.Set;
import no.sesat.search.query.Clause;
import no.sesat.search.query.Query;

/** Default implementation of TokenEvaluationEngine.State.
 *
 *
 *
 * @version $Id$
 */
public class EvaluationState implements TokenEvaluationEngine.State{

    private final String term;
    private final Query query;
    private final Set<TokenPredicate> known;
    private Set<TokenPredicate> possible;

    /**
     * Creates a new instance of EvaluationState
     * @param term
     * @param known
     * @param possible
     */
    public EvaluationState(
            final String term,
            final Set<TokenPredicate> known,
            final Set<TokenPredicate> possible) {

        this.term = term;
        this.query = null;
        this.known = known;
        this.possible = possible;
    }

    public EvaluationState(final Clause clause){

        this.term = clause.getTerm();
        this.query = null;
        this.known = clause.getKnownPredicates();
        this.possible = clause.getPossiblePredicates();
    }

    public String getTerm() {
        return term;
    }

    public Query getQuery() {
        return query;
    }

    public Set<TokenPredicate> getKnownPredicates() {
        return known;
    }

    public Set<TokenPredicate> getPossiblePredicates() {
        return possible;
    }

}
