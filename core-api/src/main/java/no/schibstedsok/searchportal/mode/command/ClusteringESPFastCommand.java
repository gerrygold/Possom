// Copyright (2007) Schibsted Søk AS
package no.schibstedsok.searchportal.mode.command;

import com.fastsearch.esp.search.query.BaseParameter;
import com.fastsearch.esp.search.query.IQuery;
import com.fastsearch.esp.search.result.EmptyValueException;
import com.fastsearch.esp.search.result.IDocumentSummary;
import com.fastsearch.esp.search.result.IDocumentSummaryField;
import com.fastsearch.esp.search.result.IQueryResult;
import com.fastsearch.esp.search.result.IllegalType;
import no.schibstedsok.searchportal.datamodel.generic.StringDataObject;
import no.schibstedsok.searchportal.mode.config.ClusteringEspFastCommandConfig;
import no.schibstedsok.searchportal.result.BasicSearchResult;
import no.schibstedsok.searchportal.result.FastSearchResult;
import no.schibstedsok.searchportal.result.SearchResult;
import no.schibstedsok.searchportal.result.SearchResultItem;
import org.apache.log4j.Logger;

import java.io.IOException;

public class ClusteringESPFastCommand extends NewsEspSearchCommand {
    public static final String PARAM_NEXT_OFFSET = "nextOffset";

    private static final Logger LOG = Logger.getLogger(ClusteringESPFastCommand.class);

    public ClusteringESPFastCommand(Context cxt) {
        super(cxt);
    }

    /**
     * Modifies several queryParameters depending on situation.
     *
     * @param query the FAST IQuery to modify
     */
    @Override
    protected void modifyQuery(IQuery query) {
        super.modifyQuery(query);
        final ClusteringEspFastCommandConfig config = getSearchConfiguration();

        // Because of a bug in FAST ESP5 related to collapsing and sorting, we must use sort direcetion,
        // and not the +fieldname syntax
        final StringDataObject sort = datamodel.getParameters().getValue(config.getUserSortParameter());
        String sortType;
        if (sort != null) {
            sortType = sort.getString();
        } else {
            sortType = config.getDefaultSort();
        }
        if (!sortType.equals("relevance")) {
            query.setParameter(BaseParameter.SORT_BY, config.getSortField());
            query.setParameter(BaseParameter.SORT_DIRECTION, sortType);
        }

        final StringDataObject clusterId = datamodel.getParameters().getValue(config.getClusterIdParameter());
        if (clusterId == null) {
            LOG.debug("--- Modifying query. ---");
            final int resultsPerCluster = config.getResultsPerCluster();
            final int resultCount = config.getResultsToReturn() * resultsPerCluster;

            query.setParameter("collapseon", "batv" + config.getClusterField());
            query.setParameter("collapsenum", resultsPerCluster);
            query.setParameter(BaseParameter.HITS, Math.max(resultCount, config.getCollapsingMaxFetch()));
        }
    }

    /**
     * Creates a clustered FastSearchResult.
     *
     * @param result the clustered searchResult
     * @return the FAST IQueryReslt to make the searchResult from
     * @throws IOException
     */
    protected FastSearchResult createSearchResult(final IQueryResult result) throws IOException {
        try {
            final ClusteringEspFastCommandConfig config = getSearchConfiguration();
            StringDataObject clusterId = datamodel.getParameters().getValue(config.getClusterIdParameter());
            if (clusterId == null) {
                return createClusteredSearchResult(config, getOffset(), result);
            } else {
                return createCollapsedResults(config, getOffset(), result);
            }
        } catch (IllegalType e) {
            LOG.error("Could not convert result", e);
        } catch (EmptyValueException e) {
            LOG.error("Could not convert result", e);
        } catch (RuntimeException e) {
            LOG.error("Could not convert result", e);
        }
        // Falling back to super implementation, because this one does not work.
        return super.createSearchResult(result);
    }

    private FastSearchResult createClusteredSearchResult(ClusteringEspFastCommandConfig config, int offset, IQueryResult result) throws IllegalType, EmptyValueException {
        final String clusterField = config.getClusterField();
        final String nestedResultsField = config.getNestedResultsField();
        final FastSearchResult searchResult = new FastSearchResult(this);
        final int maxClusterCount = config.getResultsToReturn();

        IDocumentSummaryField currentClusterId;
        IDocumentSummaryField lastClusterId = null;
        int collectedClusters = 0;
        int collectedHits = 0;
        SearchResultItem clusterEntry = null;
        SearchResult subResult = null;

        LOG.debug("HitCount=" + result.getDocCount() + ", clusterField=" + clusterField + ", nestedResultsField=" + nestedResultsField + ", offset=" + offset);
        for (int i = offset; i < result.getDocCount(); i++) {
            try {
                final IDocumentSummary document = result.getDocument(i + 1);
                currentClusterId = document.getSummaryField(clusterField);
                if (currentClusterId.isEmpty() ||
                        lastClusterId == null ||
                        lastClusterId.isEmpty() ||
                        currentClusterId.getStringValue().equals("0") ||
                        (!currentClusterId.getStringValue().equals(lastClusterId.getStringValue()))) {
                    collectedClusters++;
                    LOG.debug("Adding new cluster: " + currentClusterId + ", count is: " + collectedClusters);
                    if (collectedClusters < maxClusterCount) {
                        clusterEntry = addResult(config, searchResult, document);
                        if (!currentClusterId.isEmpty()) {
                            clusterEntry.addField(config.getClusterIdParameter(), currentClusterId.getStringValue());
                        }
                        lastClusterId = currentClusterId;
                    } else {
                        break;
                    }
                    subResult = null;
                } else {
                    LOG.debug("Adding subResult for: " + currentClusterId.getStringValue());
                    if (subResult == null) {
                        subResult = new BasicSearchResult(this);
                        IDocumentSummaryField clusterHitCount = document.getSummaryField("fcocount");
                        if (!clusterHitCount.isEmpty()) {
                            subResult.setHitCount(Integer.parseInt(clusterHitCount.getStringValue()));
                        }
                        clusterEntry.addNestedSearchResult(nestedResultsField, subResult);
                    }
                    addResult(config, subResult, document);
                }
                collectedHits++;
            } catch (NullPointerException e) {
                // The doc count is not 100% accurate.
                LOG.debug("Error finding document " + e);
                break;
            }
        }
        if (offset + collectedHits < result.getDocCount()) {
            searchResult.addField(ClusteringESPFastCommand.PARAM_NEXT_OFFSET, Integer.toString(offset + collectedHits));
        }
        searchResult.setHitCount(result.getDocCount());
        return searchResult;
    }

    public ClusteringEspFastCommandConfig getSearchConfiguration() {
        return (ClusteringEspFastCommandConfig) super.getSearchConfiguration();
    }


}
