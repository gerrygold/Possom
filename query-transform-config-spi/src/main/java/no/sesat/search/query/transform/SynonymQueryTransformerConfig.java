/* Copyright (2006-2007) Schibsted Søk AS
 * This file is part of SESAT.
 *
 *   SESAT is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SESAT is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with SESAT.  If not, see <http://www.gnu.org/licenses/>.

 */
/*
 * SynonymQueryTransformer.java
 *
 * Created on April 5, 2006, 8:05 PM
 *
 */

package no.sesat.search.query.transform;

import no.sesat.search.query.transform.AbstractQueryTransformerConfig.Controller;

/** XXX This will get largely rewritten when alternation rotation comes into play.
 * https://jira.sesam.no/jira/browse/SEARCH-863
 *
 * @author maek
 * @version $Id: SynonymQueryTransformerConfig.java 4655 2007-03-27 08:15:08Z ssmiweve $
 */
@Controller("SynonymQueryTransformer")
public final class SynonymQueryTransformerConfig extends AbstractQueryTransformerConfig {
}
