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
 */
package no.sesat.search.result.handler;

import no.sesat.search.datamodel.DataModel;
import no.sesat.search.datamodel.generic.StringDataObject;
import no.sesat.search.result.FastSearchResult;
import org.apache.log4j.Logger;

/**
 * Adapts a old searchresult to use the newsSimpeOffsetPager
 *
 *
 */
public class ClusterOffsetAdapter implements ResultHandler {
    private static final Logger LOG = Logger.getLogger(ClusterOffsetAdapter.class);
    private ClusterOffsetAdapterResultHandlerConfig config;

    public ClusterOffsetAdapter(final ResultHandlerConfig config) {
        this.config = (ClusterOffsetAdapterResultHandlerConfig) config;
    }

    public void handleResult(Context cxt, DataModel datamodel) {
        if (cxt.getSearchResult() instanceof FastSearchResult) {
            int offsetInt = 0;
            FastSearchResult searchResult = (FastSearchResult) cxt.getSearchResult();
            StringDataObject offset = datamodel.getParameters().getValue(config.getOffsetField());
            if (offset != null) {
                try {
                    offsetInt = Integer.parseInt(offset.getString());
                } catch (NumberFormatException e) {
                    LOG.error("Could not parse offset", e);
                }
            }
            offsetInt += config.getOffsetInterval();
            if (offsetInt < searchResult.getHitCount()) {
                searchResult.addField(config.getOffsetResultField(), Integer.toString(offsetInt));
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.error("Can only adapt FastSearchResults");

            }
        }
    }
}
