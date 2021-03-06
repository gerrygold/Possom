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
package no.sesat.search.query.analyser;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.PredicateUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A test class for {@link AnalysisRule}.
 *
 *
 * @version $Revision$
 */
public final class AnalysisRuleTest  {

    private AnalysisRule rule = null;
    private Predicate truePredicate = PredicateUtils.truePredicate();
    private Predicate falsePredicate = PredicateUtils.falsePredicate();


    @BeforeClass
    protected void setUp() throws Exception {
        this.rule = new AnalysisRule();
    }

    /**
     * Test method for 'no.sesat.search.analyzer.AnalysisRule.addPredicateScore(Predicate, int)'.
     */
    @Test
    public void testAddPredicateScore() {
        rule.addPredicateScore(truePredicate, 0);
    }

}
