/*
 * Copyright (2006-2007) Schibsted Søk AS
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

package no.sesat.search.run.handler;

import org.apache.log4j.Logger;

/**
 * $Id$
 * @author <a href="mailto:anders@jamtli.no">Anders Johan Jamtli</a>
 */
public final class NullRunHandler implements RunHandler {

    private static final Logger LOG = Logger.getLogger(NullRunHandler.class);

    public NullRunHandler(final RunHandlerConfig rhc) {}

    public void handleRunningQuery(Context context) {
        LOG.info("NullRunHandler");
    }

}