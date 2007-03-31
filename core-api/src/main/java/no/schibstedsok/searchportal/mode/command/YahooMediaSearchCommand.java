// Copyright (2007) Schibsted Søk AS
package no.schibstedsok.searchportal.mode.command;

import no.schibstedsok.searchportal.result.SearchResult;
import no.schibstedsok.searchportal.result.BasicSearchResult;
import no.schibstedsok.searchportal.result.SearchResultItem;
import no.schibstedsok.searchportal.result.BasicSearchResultItem;
import no.schibstedsok.searchportal.mode.config.YahooMediaSearchConfiguration;
import no.schibstedsok.searchportal.InfrastructureException;
import no.schibstedsok.searchportal.query.AndClause;
import no.schibstedsok.searchportal.query.OrClause;
import no.schibstedsok.searchportal.query.NotClause;
import no.schibstedsok.searchportal.query.AndNotClause;
import no.schibstedsok.searchportal.query.LeafClause;
import java.util.Map;
import java.text.MessageFormat;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import no.schibstedsok.searchportal.datamodel.DataModel;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Command for searching images and videos using Yahoo! as a provider.
 *
 * Yahoo API documentation can be found here:
 * https://dev.schibstedsok.no/confluence/display/TECHDEV/Yahoo+Media+Search
 *
 * @version $Id$
 */
public final class YahooMediaSearchCommand extends AbstractYahooSearchCommand {
    
    private static final Logger LOG = Logger.getLogger(YahooMediaSearchCommand.class);

    private static final String COMMAND_URL_PATTERN =
            "/std_xmls_a00?type=any&query={0}&offset={1}&custid1={2}&hits={3}&ocr={4}&catalog={5}&encoding=utf-8";

    private static final String ERR_FAILED_CREATING_URL = "Failed to encode URL";

    private static final String RESULT_HEADER_ELEMENT = "GRP";
    private static final String TOTAL_HITS_ATTR = "TOT";
    private static final String RESULT_ELEMENT = "RES";

    private static final String URL_ENCODING = "UTF-8";

    private static final String YAHOO_SIZE_PARAM = "dimensions";
    private static final String SIZE_PARAM = "sz";
    private static final String SITE_FILTER = "site";
    private static final String OCR_PARAM = "ocr";

    private static final String FIELD_THUMB_HEIGHT = "thumb_height";
    private static final String FIELD_THUMB_WIDTH = "thumb_width";

    private static final String ATTRIBUTE_THUMB_GEO = "TGEO";

    /**
     * provides a mapping betweeen sizes defined by us
     * and sizes defined by yahoo. Currently one to one.
     */
    private static enum ImageMapping {
        SMALL ("small"),
        MEDIUM ("medium"),
        LARGE ("large"),
        WALLPAPER ("wallpaper"),
        WIDEWALLPAPER ("widewallpaper");

        private final String sizes;

        ImageMapping(final String sizes) {
            this.sizes = sizes;
        }

        /**
         * Getter for property 'sizes'.
         *
         * @return Value for property 'sizes'.
         */
        public String getSizes() {
            return sizes;
        }
    }

    /**
     * Create new yahoo media command.
     *
     * @param cxt Context to execute in.
     * @param parameters Command parameters to use.
     */
    public YahooMediaSearchCommand(final Context cxt) {

        super(cxt);
    }

    /** {@inheritDoc} */
    protected String createRequestURL() {

        String query = getTransformedQuery();

        final YahooMediaSearchConfiguration cfg = (YahooMediaSearchConfiguration) context.getSearchConfiguration();

        if (cfg.getSite().length() > 0) {
            query += " +site:" + cfg.getSite();
        }

        try {

            final String ocr = getSingleParameter(OCR_PARAM) != null ? getSingleParameter(OCR_PARAM) : cfg.getOcr();

            String url = MessageFormat.format(
                    COMMAND_URL_PATTERN,
                    URLEncoder.encode(query, URL_ENCODING),
                    getCurrentOffset(0),
                    getPartnerId(),
                    cfg.getResultsToReturn(),
                    ocr,
                    cfg.getCatalog());

            if (getSingleParameter(SIZE_PARAM) != null && !getSingleParameter(SIZE_PARAM).equals("")) {
                final ImageMapping mapping = ImageMapping.valueOf(getSingleParameter(SIZE_PARAM).toUpperCase());

                if (mapping != null)
                    url += "&" + YAHOO_SIZE_PARAM + "=" + mapping.getSizes();
            }

            return url;

        } catch (final UnsupportedEncodingException e) {
            throw new InfrastructureException(ERR_FAILED_CREATING_URL, e);
        }
    }

    /** {@inheritDoc} */
    public SearchResult execute() {
        try {

            final Document doc = getXmlResult();
            final SearchResult searchResult = new BasicSearchResult(this);

            if (doc != null) {

                searchResult.setHitCount(0);

                final Element searchResponseE = doc.getDocumentElement();
                final Element resultHeaderE = (Element) searchResponseE.getElementsByTagName(RESULT_HEADER_ELEMENT).item(0);

                if (resultHeaderE != null) {

                    searchResult.setHitCount(Integer.parseInt(resultHeaderE.getAttribute(TOTAL_HITS_ATTR)));

                    final NodeList list = searchResponseE.getElementsByTagName(RESULT_ELEMENT);

                    for (int i = 0; i < list.getLength(); ++i) {
                        final Element listing = (Element) list.item(i);
                        searchResult.addResult(createResultItem(listing));
                    }
                }
            }

            searchResult.addField("generatedQuery", getQueryRepresentation(datamodel.getQuery().getQuery()));

            return searchResult;

        } catch (SocketTimeoutException ste) {

            LOG.error(getSearchConfiguration().getName() + ste.getMessage());
            return new BasicSearchResult(this);

        } catch (final IOException e) {
            throw new InfrastructureException(e);
            
        } catch (final SAXException e) {
            throw new InfrastructureException(e);
        }
    }


    // AbstractReflectionVisitor overrides ----------------------------------------------

    private boolean insideNot = false;
    private Boolean writeAnd = Boolean.TRUE;

    /** {@inheritDoc} */
    protected void visitImpl(final LeafClause clause) {

        final String transformedTerm = getTransformedTerm(clause);
        if (clause.getField() != null && clause.getField().equals(SITE_FILTER)) {
            appendToQueryRepresentation("+" + clause.getField() + ":");
            appendToQueryRepresentation(transformedTerm);

        } else if (null == clause.getField() || null == getFieldFilter(clause)) {
            if (transformedTerm != null && transformedTerm.length() > 0) {
                if (insideNot) {
                    appendToQueryRepresentation("-");
                }  else if (writeAnd != null && writeAnd) {
                    appendToQueryRepresentation("+");
                }
                appendToQueryRepresentation((null == clause.getField()
                        ? ""
                        : clause.getField() + "\\:")
                        + transformedTerm);
            }
        }
    }


    /** {@inheritDoc} */
    protected void visitImpl(final AndClause clause) {
        final Boolean originalWriteAnd = writeAnd;
        writeAnd = Boolean.TRUE;
        clause.getFirstClause().accept(this);
        appendToQueryRepresentation(" ");
        clause.getSecondClause().accept(this);
        writeAnd = originalWriteAnd;
    }

    /** {@inheritDoc} */
    protected void visitImpl(final OrClause clause) {
        final Boolean originalWriteAnd = writeAnd;
        writeAnd = Boolean.FALSE;
//        appendToQueryRepresentation(" (");
        clause.getFirstClause().accept(this);
        appendToQueryRepresentation(" ");
        clause.getSecondClause().accept(this);
//        appendToQueryRepresentation(") ");
        writeAnd = originalWriteAnd;
    }

    /** {@inheritDoc} */
    protected void visitImpl(final NotClause clause) {
        if (writeAnd == null) {
            // must start prefixing terms with +
            writeAnd = Boolean.TRUE;
        }
        final boolean originalInsideAndNot = insideNot;
        insideNot = true;
        clause.getFirstClause().accept(this);
        insideNot = originalInsideAndNot;

    }

    /** {@inheritDoc} */
    protected void visitImpl(final AndNotClause clause) {
        if (writeAnd == null) {
            // must start prefixing terms with +
            writeAnd = Boolean.TRUE;
        }
        final boolean originalInsideAndNot = insideNot;
        insideNot = true;
        clause.getFirstClause().accept(this);
        insideNot = originalInsideAndNot;
    }

    private SearchResultItem createResultItem(final Element listing) {
        final BasicSearchResultItem item = new BasicSearchResultItem();

        for (final Map.Entry<String,String> entry : context.getSearchConfiguration().getResultFields().entrySet()){

            // Special case for thumb width & height.
            if (entry.getKey().equals(FIELD_THUMB_HEIGHT)) {
                final String geometry = listing.getAttribute(ATTRIBUTE_THUMB_GEO);
                final String height = geometry.substring(geometry.indexOf("x") + 1);
                item.addField(entry.getValue(), height);
            } else if (entry.getKey().equals(FIELD_THUMB_WIDTH)) {
                final String geometry = listing.getAttribute(ATTRIBUTE_THUMB_GEO);
                final String width = geometry.substring(0, geometry.indexOf("x"));
                item.addField(entry.getValue(), width);
            } else {

                final String field = listing.getAttribute(entry.getKey().toUpperCase());

                if (!"".equals(field)) {
                    item.addField(entry.getValue(), field);
                }
            }
        }

        return item;
    }
}
