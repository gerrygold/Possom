/*
 * Copyright (2005-2007) Schibsted Søk AS
 * This file is part of SESAT.
 * You can use, redistribute, and/or modify it, under the terms of the SESAT License.
 * You should have received a copy of the SESAT License along with this program.  
 * If not, see https://dev.schibstedsok.no/confluence/display/SESAT/SESAT+License
 */
package no.schibstedsok.searchportal.http.servlet;


import java.io.IOException;
import java.util.Properties;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import no.schibstedsok.commons.ioc.BaseContext;
import no.schibstedsok.commons.ioc.ContextWrapper;
import no.schibstedsok.searchportal.mode.SearchMode;
import no.schibstedsok.searchportal.mode.SearchModeFactory;
import no.schibstedsok.searchportal.run.QueryFactory;
import no.schibstedsok.searchportal.run.RunningQuery;
import no.schibstedsok.searchportal.security.MD5Generator;
import no.schibstedsok.searchportal.datamodel.DataModel;
import no.schibstedsok.searchportal.datamodel.DataModelFactory;
import no.schibstedsok.searchportal.datamodel.access.ControlLevel;
import no.schibstedsok.searchportal.datamodel.generic.DataObject;
import no.schibstedsok.searchportal.datamodel.generic.StringDataObject;
import no.schibstedsok.searchportal.datamodel.page.PageDataObject;
import no.schibstedsok.searchportal.datamodel.request.ParametersDataObject;
import no.schibstedsok.searchportal.http.servlet.FactoryReloads.ReloadArg;
import no.schibstedsok.searchportal.result.ResultItem;
import no.schibstedsok.searchportal.site.Site;
import no.schibstedsok.searchportal.site.SiteContext;
import no.schibstedsok.searchportal.site.SiteKeyedFactoryInstantiationException;
import no.schibstedsok.searchportal.site.config.*;
import no.schibstedsok.searchportal.view.config.SearchTab;
import no.schibstedsok.searchportal.view.SearchTabFactory;
import no.schibstedsok.searchportal.site.config.TextMessages;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/** The Central Controller to incoming queries.
 * Controls the SearchMode -> RunningQuery creation and handling.
 *
 * @author <a href="mailto:magnus.eklund@schibsted.no">Magnus Eklund</a>
 * @author <a href="mailto:mick@wever.org">Mck</a>
 * @version <tt>$Id$</tt>
 */
public final class SearchServlet extends HttpServlet {

   // Constants -----------------------------------------------------

    /** The serialVersionUID. */
    private static final long serialVersionUID = 3068140845772756438L;

    private static final Logger LOG = Logger.getLogger(SearchServlet.class);
    private static final Logger ACCESS_LOG = Logger.getLogger("no.schibstedsok.Access");
    private static final Logger STATISTICS_LOG = Logger.getLogger("no.schibstedsok.Statistics");

    private static final String ERR_MISSING_TAB = "No existing implementation for tab ";
    private static final String ERR_MISSING_MODE = "No existing implementation for mode ";


    // Attributes ----------------------------------------------------


    // Attributes ----------------------------------------------------
    //  Important that a Servlet does not have instance fields for synchronisation reasons.
    //

    // Static --------------------------------------------------------

    static{

        // when the root logger is set to DEBUG do not limit connection times
        if(Logger.getRootLogger().getLevel().isGreaterOrEqual(Level.INFO)){
            System.setProperty("sun.net.client.defaultConnectTimeout", "1000");
            System.setProperty("sun.net.client.defaultReadTimeout", "3000");
            System.setProperty("sun.net.http.errorstream.enableBuffering", "true");
        }
    }

    // Constructors --------------------------------------------------

    // Public --------------------------------------------------------

    /** {@inheritDoc}
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    // Package protected ---------------------------------------------

    // Protected -----------------------------------------------------

    /** {@inheritDoc}
     */
    @Override
    protected void doGet(
            final HttpServletRequest request,
            final HttpServletResponse response)
                throws ServletException, IOException {

        final String url = request.getRequestURI()
                + (null != request.getQueryString() ? '?' + request.getQueryString() : "");

        ACCESS_LOG.info("<search-servlet>"
                + "<real-url>" + StringEscapeUtils.escapeXml(url) + "</real-url>"
                + "</search-servlet>");

        final DataModel datamodel = (DataModel) request.getSession().getAttribute(DataModel.KEY);
        final ParametersDataObject parametersDO = datamodel.getParameters();
        final Site site = datamodel.getSite().getSite();

        // BaseContext providing SiteContext and ResourceContext.
        //  We need it casted as a SiteContext for the ResourceContext code to be happy.
        final SiteContext genericCxt = new SiteContext(){
            public PropertiesLoader newPropertiesLoader(
                    final SiteContext siteCxt,
                    final String resource,
                    final Properties properties) {

                return UrlResourceLoader.newPropertiesLoader(siteCxt, resource, properties);
            }
            public DocumentLoader newDocumentLoader(
                        final SiteContext siteCxt,
                        final String resource,
                        final DocumentBuilder builder) {

                return UrlResourceLoader.newDocumentLoader(siteCxt, resource, builder);
            }

            public BytecodeLoader newBytecodeLoader(SiteContext context, String className, final String jar) {
                return UrlResourceLoader.newBytecodeLoader(context, className, jar);
            }

            public Site getSite() {
                return site;
            }
        };

        final DataModelFactory dmFactory;
        try{
            dmFactory = DataModelFactory.valueOf(ContextWrapper.wrap(DataModelFactory.Context.class, genericCxt));

        }catch(SiteKeyedFactoryInstantiationException skfe){
            throw new ServletException(skfe);
        }

        // DataModel's ControlLevel will be VIEW_CONSTRUCTION (safe setting set by DataModelFilter)
        //  Bring it back to VIEW_CONSTRUCTION.
        dmFactory.assignControlLevel(datamodel, ControlLevel.REQUEST_CONSTRUCTION);
        
        /* Clean out old search results and parameters. */
        for(String key : datamodel.getSearches().keySet()){
            datamodel.setSearch(key, null);
        }
        datamodel.setQuery(null);
        
        try{

            if (!isEmptyQuery(datamodel, response, genericCxt)) {

                LOG.trace("doGet()");
                LOG.debug("Character encoding ="  + request.getCharacterEncoding());

                final StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                performFactoryReloads(request.getParameter("reload"), genericCxt);

                updateContentType(site, response, request);

                final SearchTab searchTab = updateSearchTab(request, dmFactory, genericCxt);

                if (null!= searchTab) {

                    // If the rss is hidden, require a partnerId.
                    // The security by obscurity has been somewhat improved by the
                    // addition of rssPartnerId as a md5-protected parameter (MD5ProtectedParametersFilter).
                    final StringDataObject output = parametersDO.getValue("output");
                    boolean hiddenRssWithoutPartnerId = null != output
                        && "rss".equals(output.getString())
                        && searchTab.isRssHidden()
                        && null == parametersDO.getValues().get("rssPartnerId");

                    if (hiddenRssWithoutPartnerId) {

                        response.sendError(HttpServletResponse.SC_NOT_FOUND);

                    }else{

                        performSearch(request, response, genericCxt, searchTab, stopWatch);
                    }
                }else{
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }

        }finally{

            // DataModel's ControlLevel will be REQUEST_CONSTRUCTION or RUNNING_QUERY_RESULT_HANDLING
            //  Increment it onwards to VIEW_CONSTRUCTION.
            dmFactory.assignControlLevel(datamodel, ControlLevel.VIEW_CONSTRUCTION);
        }
    }

    // Private -------------------------------------------------------

    private static boolean isEmptyQuery(
            final DataModel datamodel,
            final HttpServletResponse response,
            final SiteContext ctx) throws IOException{

        String redirect = null;
        final ParametersDataObject parametersDO = datamodel.getParameters();
        final Map<String,StringDataObject> params = parametersDO.getValues();
        final String qParam = null != params.get("q") ? params.get("q").getString() : "";
        final String cParm = null != params.get("c") ? params.get("c").getString() : "";
        final String wParam = null != params.get("where") ? params.get("where").getString() : "";

        // check if this is a sitesearch
        final Properties props = datamodel.getSite().getSiteConfiguration().getProperties();
        final boolean isSitesearch = Boolean.valueOf(props.getProperty(SiteConfiguration.IS_SITESEARCH_KEY));

        if (qParam == null) {
            redirect = "/";
        } else if (null != cParm
                && ("d".equals(cParm) || "g".equals(cParm) || "cat".equals(cParm) || "catip".equals(cParm))
                && !isSitesearch) {
            // Extra check for the Norwegian web search. Search with an empty query string
            // should return the first page.
            if (qParam.trim().length() == 0) {
                redirect = "/";
            }
        }

        if (null != redirect) {
            LOG.info("doGet(): Empty Query String redirect=" + redirect);
            response.sendRedirect(redirect);
        }
        return null != redirect;
    }

    private static void performFactoryReloads(
            final String reload,
            final SiteContext genericCxt){

        if (null != reload && reload.length() > 0){
            try{
                final ReloadArg arg = ReloadArg.valueOf(reload.toUpperCase());
                FactoryReloads.performReloads(genericCxt, arg);

            }catch(IllegalArgumentException ex){
                LOG.info("Invalid reload parameter -->" + reload);
            }
        }
    }

    private static void updateContentType(
            final Site site,
            final HttpServletResponse response,
            final HttpServletRequest request){

        /* Setting default encoding */
        response.setCharacterEncoding("UTF-8");

        // TODO. Any better way to do this. Sitemesh?
        if (request.getParameter("output") != null && request.getParameter("output").equals("rss")) {
            if (request.getParameter("encoding") != null && request.getParameter("encoding").equals("iso-8859-1")){
                response.setContentType("text/xml; charset=iso-8859-1");
                response.setCharacterEncoding("iso-8859-1"); // correct encoding
            } else {
                response.setContentType("text/xml; charset=utf-8");
            }
        } else if (site.getName().startsWith("mobil") || site.getName().startsWith("xml")) {
            response.setContentType("text/xml; charset=utf-8");
            try {
                // Just can't get sitemesh to work in the way I imagine it works.
                response.getWriter().write(
                    "<html><head><META name=\"decorator\" content=\"mobiledecorator\"/></head></html>");
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else if (request.getParameter("output") != null
                && request.getParameter("output").equals("savedecorator")) {
            final String fileName = ".ics";
            final String charset = "utf-8";
            String showid = request.getParameter("showId");

            if (showid == null) {
                showid = "";
            }

            response.setContentType("text/calendar; charset=" + charset);
            response.setHeader("Content-Disposition","attachment;filename=sesam-tvsok-" + showid + fileName);
        } else if (request.getParameter("output") != null && request.getParameter("output").equals("vcarddecorator")) {
            final String userAgent = request.getHeader("User-Agent");
            String showid = request.getParameter("showId");
            String charset = "utf-8";

            if (userAgent.indexOf("Windows") != -1) {
                charset = "iso-8859-1";
            }
            if (showid == null) {
                showid = "";
            }

            response.setCharacterEncoding(charset);
            response.setContentType("text/x-vcard; charset=" + charset);
            response.setHeader("Content-Disposition","attachment;filename=vcard-" + showid + ".vcf");
        } else if (request.getParameter("output") != null
                && (request.getParameter("output").equals("opensearch")
                        || request.getParameter("output").equals("xml"))) {
            final String charset = "utf-8";
            response.setCharacterEncoding(charset);
            response.setContentType("text/xml; charset=" + charset);
        } else {
            final String charset = "utf-8";
            response.setContentType("text/html; charset=" + charset);
        }
    }

    private static void updateAttributes(
            final HttpServletRequest request,
            final RunningQuery.Context rqCxt){


        final DataModel datamodel = (DataModel) request.getSession().getAttribute(DataModel.KEY);
        final ParametersDataObject parametersDO = datamodel.getParameters();

        if (null == parametersDO.getValue("offset") || 0 == parametersDO.getValue("offset").getString().length()) {
            request.setAttribute("offset", "0"); // TODO remove, access through datamodel instead.
        }

         // TODO remove next two, access through datamodel instead.
        request.setAttribute("text",TextMessages.valueOf(ContextWrapper.wrap(
                TextMessages.Context.class,
                rqCxt,new SiteContext(){
                public Site getSite() {
                    return datamodel.getSite().getSite();
                }
        })));
        request.setAttribute("no.schibstedsok.Statistics", new StringBuffer());

    }

    /* TODO Move into a RunningQueryHandler
     *
     *  redirects to yellowinfopage if request is from finn.no -> req.param("finn") = "finn"
     *  finn sends orgnumber as queryparam, if only 1 hit, then redirect.
     * @return true if a response.sendRedirect(..) was performed.
     */
    private static boolean checkFinn(
            final HttpServletRequest request,
            final HttpServletResponse response, final DataModel datamodel) throws IOException{

        if ("finn".equalsIgnoreCase(request.getParameter("finn"))) {

            if (datamodel.getSearch("catalogue").getResults().getHitCount() > 0) {

                if (datamodel.getSearch("catalogue").getResults().getHitCount() == 1) {
                    final ResultItem sri = datamodel.getSearch("catalogue").getResults().getResults().get(0);
                    final String recordid = sri.getField("contentid").toString();                    
                    final String url = "/search/?c=yip&q=" + datamodel.getQuery().getQuery().getQueryString()
                            + "&companyId=" + recordid
                            + "&companyId_x=" + new MD5Generator("S3SAM rockz").generateMD5(recordid)
                            + (null != datamodel.getParameters().getValue("showtab").getUtf8UrlEncoded()
                            ? "&showtab=" + datamodel.getParameters().getValue("showtab").getUtf8UrlEncoded()
                            : "");

                    LOG.info("Finn.no redirect: " + url);
                    response.sendRedirect(url);
                    return true;
                }
            }
        }
        return false;
    }

    private static SearchTab updateSearchTab(
            final HttpServletRequest request, 
            final DataModelFactory dmFactory,
            final BaseContext genericCxt){

        // determine the c parameter. default is 'd' unless there exists a page parameter when it becomes 'i'.
        final DataModel datamodel = (DataModel) request.getSession().getAttribute(DataModel.KEY);
        final ParametersDataObject parametersDO = datamodel.getParameters();
        final StringDataObject c = parametersDO.getValue("c");
        final StringDataObject page = parametersDO.getValue("page");
        final String defaultSearchTabKey 
                = datamodel.getSite().getSiteConfiguration().getProperty(SiteConfiguration.DEFAULTTAB_KEY);
        
        final String searchTabKey = null != c &&  null != c.getString() && 0 < c.getString().length()
                ? c.getString()
                : null != page && null != page.getString() && 0 < page.getString().length() 
                    ? "i" 
                    : null != defaultSearchTabKey && !defaultSearchTabKey.equals("") ? defaultSearchTabKey: "c";

        LOG.info("searchTabKey:" +searchTabKey);

        SearchTab result = null;
        try{
            final SearchTabFactory stFactory = SearchTabFactory.valueOf(
                ContextWrapper.wrap(
                    SearchTabFactory.Context.class,
                    genericCxt));
            
            result = stFactory.getTabByKey(searchTabKey);
            
            if(null == datamodel.getPage()){
                
                final PageDataObject pageDO = dmFactory.instantiate(
                        PageDataObject.class,
                        new DataObject.Property("tabs", stFactory.getTabsByName()),
                        new DataObject.Property("currentTab", result));
                
                datamodel.setPage(pageDO);
            }else{
                datamodel.getPage().setCurrentTab(result);
            }
            
            // this is legacy. shorter to write in templates than $datamodel.page.currentTab
            request.setAttribute("tab", datamodel.getPage().getCurrentTab());
            
        }catch(AssertionError ae){
            // it's not normal to catch assert errors but we really want a 404 not 500 response error.
            LOG.error("Caught Assertion: " + ae);
        }
        if(null==result){
            LOG.error(ERR_MISSING_TAB + searchTabKey);
        }
        return result;
    }

    private static void performSearch(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final SiteContext genericCxt,
            final SearchTab searchTab,
            final StopWatch stopWatch) throws IOException{

        final SearchMode mode = SearchModeFactory.valueOf(
                ContextWrapper.wrap(SearchModeFactory.Context.class, genericCxt))
                .getMode(searchTab.getMode());

        if (mode == null) {
            LOG.error(ERR_MISSING_MODE + searchTab.getMode());
            throw new UnsupportedOperationException(ERR_MISSING_MODE + searchTab.getMode());
        }

        final DataModel datamodel = (DataModel) request.getSession().getAttribute(DataModel.KEY);
        final StringDataObject output = datamodel.getParameters().getValue("output");

        final RunningQuery.Context rqCxt = ContextWrapper.wrap(
                RunningQuery.Context.class,
                new BaseContext() {
                    public DataModel getDataModel(){
                        return datamodel;
                    }
                    public SearchMode getSearchMode() {
                        return mode;
                    }
                    public SearchTab getSearchTab() {
                        return searchTab;
                    }
                },
                genericCxt
        );

        updateAttributes(request, rqCxt);

        if(null == output || !"opensearch".equalsIgnoreCase(output.getString())){

            try {

                // DataModel's ControlLevel will be REQUEST_CONSTRUCTION
                //  Increment it onwards to RUNNING_QUERY_CONSTRUCTION.
                DataModelFactory
                        .valueOf(ContextWrapper.wrap(DataModelFactory.Context.class, genericCxt))
                        .assignControlLevel(datamodel, ControlLevel.RUNNING_QUERY_CONSTRUCTION);

                final RunningQuery query = QueryFactory.getInstance().createQuery(rqCxt, request, response);

                if( !datamodel.getQuery().getQuery().isBlank() || searchTab.isExecuteOnBlank() ){
                    
                    query.run();
                    stopWatch.stop();
                    LOG.info("Search took " + stopWatch + " " + datamodel.getQuery().getString());

                    if(!"NOCOUNT".equals(request.getParameter("IGNORE"))){

                        STATISTICS_LOG.info(
                            "<search-servlet"
                                + (null != output ? " output=\"" + output.getXmlEscaped() + "\">" : ">")
                                + "<query>" + datamodel.getQuery().getXmlEscaped() + "</query>"
                                + "<time>" + stopWatch + "</time>"
                                + ((StringBuffer)request.getAttribute("no.schibstedsok.Statistics")).toString()
                            + "</search-servlet>");
                    }
                }


                checkFinn(request, response, datamodel);

            } catch (InterruptedException e) {
                LOG.error("Task timed out");
            } catch (SiteKeyedFactoryInstantiationException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    // Inner classes -------------------------------------------------

}
