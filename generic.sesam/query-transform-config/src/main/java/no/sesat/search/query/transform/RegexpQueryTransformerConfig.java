/*
 * Copyright (2005-2007) Schibsted Søk AS
 * This file is part of SESAT.
 *
 *   SESAT is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SESAT is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with SESAT.  If not, see <http://www.gnu.org/licenses/>.
 */
package no.sesat.search.query.transform;

import no.sesat.search.query.transform.AbstractQueryTransformerConfig.Controller;
import no.sesat.search.site.config.AbstractDocumentFactory;
import no.sesat.search.site.config.AbstractDocumentFactory.ParseType;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

/**
 * Configuration bean for RegexpQueryTransformer.
 *
 * @version $Id$
 * @author <a href="mailto:mick@wever.org">Michael Semb Wever</a>
 *
 */
@Controller("RegexpQueryTransformer")
public final class RegexpQueryTransformerConfig extends AbstractQueryTransformerConfig {

    private static final Logger LOG = Logger.getLogger(RegexpQueryTransformerConfig.class);

    /**
     * Holds value of property regexp.
     */
    private String regexp;

    /**
     * Holds value of property replacement.
     */
    private String replacement = "";

    @Override
    public RegexpQueryTransformerConfig readQueryTransformer(final Element qt){

        super.readQueryTransformer(qt);
        AbstractDocumentFactory.fillBeanProperty(this, null, "regexp", ParseType.String, qt, "");
        AbstractDocumentFactory.fillBeanProperty(this, null, "replacement", ParseType.String, qt, "");
        return this;
    }

    /**
     * Setter for property regexp.
     * @param regexp New value of property regexp.
     */
    public void setRegexp(final String regexp) {
        this.regexp = regexp;
    }

    /**
     *
     * @return
     */
    public String getRegexp(){
        return regexp;
    }

    /**
     * Setter for property replacement.
     * @param replacement New value of property replacement.
     */
    public void setReplacement(final String replacement) {
        this.replacement = replacement;
    }

    /**
     *
     * @return
     */
    public String getReplacement(){
        return replacement;
    }
}