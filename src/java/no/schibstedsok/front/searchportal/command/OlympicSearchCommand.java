/*
 * OlympicSearchCommand.java
 *
 * Created on February 8, 2006, 2:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package no.schibstedsok.front.searchportal.command;

import com.thoughtworks.xstream.XStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import no.schibstedsok.front.searchportal.command.impl.SearchCommandFactory;
import no.schibstedsok.front.searchportal.configuration.FastConfiguration;
import no.schibstedsok.front.searchportal.configuration.OlympicSearchConfiguration;
import no.schibstedsok.front.searchportal.configuration.PicSearchConfiguration;
import no.schibstedsok.front.searchportal.configuration.SearchConfiguration;
import no.schibstedsok.front.searchportal.configuration.loader.DocumentLoader;
import no.schibstedsok.front.searchportal.configuration.loader.PropertiesLoader;
import no.schibstedsok.front.searchportal.configuration.loader.XStreamLoader;
import no.schibstedsok.front.searchportal.query.transform.AbstractQueryTransformer;
import no.schibstedsok.front.searchportal.query.transform.ExactTitleMatchTransformer;
import no.schibstedsok.front.searchportal.query.RunningQuery;
import no.schibstedsok.front.searchportal.result.BasicSearchResult;
import no.schibstedsok.front.searchportal.result.BasicSearchResultItem;
import no.schibstedsok.front.searchportal.result.SearchResult;
import no.schibstedsok.front.searchportal.result.SearchResultItem;
import no.schibstedsok.front.searchportal.site.Site;
import no.schibstedsok.front.searchportal.util.OlympicData;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class OlympicSearchCommand extends AbstractSearchCommand {
    
    private OlympicSearchConfiguration configuration;
    private final SearchCommand.Context cxt;
    
    
    public OlympicSearchCommand(final SearchCommand.Context cxt, Map parameters) {
        super(cxt, parameters);
        this.cxt = cxt;
    }
    
    public SearchResult execute() {
        OlympicData data = OlympicData.instance();
        
        SearchResult result = new BasicSearchResult(this);
        
        result.setHitCount(0);
        
        if (data.getParticipants().containsKey(getQuery().getQueryString().toLowerCase(getQuery().getLocale()))) {
            SearchResultItem resultItem = getParticipantItem(data, result);
            result.setHitCount(1);
            SearchResult picSearchResult = doPicSearch(null);
            
            
            if (picSearchResult != null && picSearchResult.getHitCount() > 0) {
                SearchResultItem picture = (SearchResultItem) picSearchResult.getResults().get(0);
                resultItem.addField("pictureHits", new Integer(picSearchResult.getHitCount()).toString());
                resultItem.addField("thumb_url", picture.getField("thumb_url"));
                resultItem.addField("page_url", picture.getField("page_url"));
            }
            
            SearchResult wikiResult = doWikiSearch();
            
            
            if (wikiResult != null && wikiResult.getHitCount() > 0) {
                SearchResultItem wikiItem = (SearchResultItem) wikiResult.getResults().get(0);
                resultItem.addField("wikiUrl", wikiItem.getField("url"));
            }

            result.addResult(resultItem);
        } else if (data.getDiciplines().containsKey(getQuery().getQueryString().toLowerCase(getQuery().getLocale()))) {
            result.setHitCount(1);
            SearchResultItem resultItem = getDiciplineItem(data, result);
            
            SearchResult picSearchResult = doPicSearch(resultItem.getField("alternativePicSearch"));
            
            resultItem.addNestedSearchResult("picSearch",picSearchResult);
            
            SearchResult wikiResult = doWikiSearch();
            
            if (wikiResult != null && wikiResult.getHitCount() > 0) {
                SearchResultItem wikiItem = (SearchResultItem) wikiResult.getResults().get(0);
                resultItem.addField("wikiUrl", wikiItem.getField("url"));
            }
            
            List participants = (List) data.getParticipantsPerDicipline().get(getQuery().getQueryString().toLowerCase(getQuery().getLocale()));
            
            if (participants != null) {
                for (Iterator it = participants.iterator(); it.hasNext();) {
                    String elem = (String) it.next();
                    resultItem.addToMultivaluedField("participants", elem);
                }
            }
            result.addResult(resultItem);
        } else {
            result.setHitCount(1);
            SearchResultItem item = new BasicSearchResultItem();
            result.addResult(item);
        }
        
        return result;
    }
    
    private SearchResult doWikiSearch() {
        
        
        FastConfiguration wikiConf = new FastConfiguration();
        wikiConf.setQueryServerURL(((OlympicSearchConfiguration)cxt.getSearchConfiguration()).getWikiQrServer());
        wikiConf.setSortBy("standard");
        wikiConf.addCollection("wikipedia2");
        wikiConf.setResultsToReturn(1);
        wikiConf.addQueryTransformer(new ExactTitleMatchTransformer());
        wikiConf.addResultField("url");
        SearchCommand wikiCommand = SearchCommandFactory.createSearchCommand(getCommandContext(wikiConf), getParameters());
        SearchResult wikiResult = null;
        try {
            wikiResult = (SearchResult) wikiCommand.call();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return wikiResult;
    }
    
    private SearchResult doPicSearch(final String alternative) {
        
        final PicSearchConfiguration pc = new PicSearchConfiguration();
        
        pc.addResultField("thumb_url");
        pc.addResultField("page_url");
        pc.addResultField("thumb_width");
        pc.addResultField("thumb_height");
        pc.addResultField("height");
        pc.addResultField("width");
        pc.addResultField("filesize");
        pc.setResultsToReturn(3);
        
        if (alternative != null) {
            pc.addQueryTransformer(new AbstractQueryTransformer() {
                public String getTransformedQuery(String query) {
                    return alternative;
                }
            });
        }
        
        SearchCommand picSearch = SearchCommandFactory.createSearchCommand(getCommandContext(pc), getParameters());
        SearchResult picSearchResult = null;
        try {
            picSearchResult = (SearchResult) picSearch.call();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return picSearchResult;
    }

    private SearchCommand.Context getCommandContext(final SearchConfiguration c) {
        SearchCommand.Context picSearchCxt = new SearchCommand.Context() {
            public PropertiesLoader newPropertiesLoader(String resource, Properties properties) {
                return cxt.newPropertiesLoader(resource, properties);
            }

            public XStreamLoader newXStreamLoader(String resource, XStream xstream) {
                return cxt.newXStreamLoader(resource, xstream);
            }

            public DocumentLoader newDocumentLoader(String resource, DocumentBuilder builder) {
                return cxt.newDocumentLoader(resource, builder);
            }

            public Site getSite() {
                return cxt.getSite();
            }

            public SearchConfiguration getSearchConfiguration() {
                return c;
            }

            public RunningQuery getQuery() {
                return cxt.getQuery();
            }
        };
        return picSearchCxt;
    }
    
    private SearchResultItem getParticipantItem(final OlympicData data, final SearchResult result) {
        Map participant = (Map) data.getParticipants().get(getQuery().getQueryString().toLowerCase(getQuery().getLocale()));
        
        SearchResultItem resultItem = new BasicSearchResultItem();
        resultItem.addField("participantName", getQuery().getQueryString());
        resultItem.addField("diciplineName", (String) participant.get("diciplineName"));
        
        if (participant.containsKey("infoLink1")) {
            resultItem.addField("infoLink1", (String) participant.get("infoLink1"));
        }
        if (participant.containsKey("infoLink2")) {
            resultItem.addField("infoLink2", (String) participant.get("infoLink2"));
        }
        if (participant.containsKey("infoLink3")) {
            resultItem.addField("infoLink3", (String) participant.get("infoLink3"));
        }
        
        Map dicipline =  (Map) data.getDiciplines().get((String) participant.get("diciplineName"));
        
        if (dicipline != null) {
            if (dicipline.containsKey("tvLink")) {
                resultItem.addField("tvLink", (String) dicipline.get("tvLink"));
            }
            
            if (dicipline.containsKey("programLink")) {
                resultItem.addField("programLink", (String) dicipline.get("programLink"));
            }
        }
        return resultItem;
    }
    
    private SearchResultItem getDiciplineItem(OlympicData data, SearchResult result) {
        Map dicipline = (Map) data.getDiciplines().get(getQuery().getQueryString().toLowerCase(getQuery().getLocale()));
        
        SearchResultItem resultItem = new BasicSearchResultItem();
        
        resultItem.addField("diciplineName", getQuery().getQueryString());
        resultItem.addField("tvLink", (String) dicipline.get("tvLink"));
        resultItem.addField("programLink", (String) dicipline.get("programLink"));
        resultItem.addField("alternativePicSearch", (String) dicipline.get("alternativePicSearch"));
        return resultItem;
    }
}
