/* Copyright (2006-2012) Schibsted ASA
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
 *
 * SiteLocatorFilter.java
 *
 * Created on 9 February 2006, 11:30
 */

package no.sesat.search.http.filters;



import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;
import java.text.MessageFormat;
import java.util.Deque;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import no.sesat.search.site.config.SiteConfiguration;
import no.sesat.search.site.config.UrlResourceLoader;
import no.sesat.search.site.Site;
import no.sesat.search.view.FindResource;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

/** Loads the appropriate Site object in as a request attribute.
 * Will redirect to correct (search-front-config) url for resources (css,images, javascript). <br/>
 * Also responsible for logging each request and response like an apache access logfile.
 *
 *
 * @version $Id$
 */
public final class SiteLocatorFilter implements Filter {

    // Constants -----------------------------------------------------

    private static final Logger LOG = Logger.getLogger(SiteLocatorFilter.class);
    private static final Logger ACCESS_LOG = Logger.getLogger("no.sesat.Access");

    private static final String ERR_NOT_FOUND = "Failed to find resource ";
    private static final String ERR_UNCAUGHT_RUNTIME_EXCEPTION
            = "Following runtime exception was let loose in tomcat against ";

    private static final String INFO_USING_DEFAULT_LOCALE = " is falling back to the default locale ";
    private static final String DEBUG_REQUESTED_VHOST = "Virtual host is ";
    private static final String DEBUG_REDIRECTING_TO = " redirect to ";
    private static final String WARN_FAULTY_BROWSER = "Site in datamodel does not match requested site. User agent is ";

    private static final String PUBLISH_DIR = "/img/";

    private static final String UNKNOWN = "unknown";

    private static final String USER_REQUEST_QUEUE = "userRequestQueue";
    private static final String USER_REQUEST_LOCK = "userRequestSemaphore";
    private static final long WAIT_TIME = 5000;
    private static final int REQUEST_QUEUE_SIZE = 5;


    // Any request coming into Possom with /conf/ is immediately returned as a 404.
    // It should have been directed to a skin.
    private static final String CONFIGURATION_RESOURCE= "/conf/";

    /** Changes to this list must also change the ProxyPass|ProxyPassReverse configuration in httpd.conf **/
    private static final Collection<String> EXTERNAL_DIRS =
            Collections.unmodifiableCollection(Arrays.asList(new String[]{
                PUBLISH_DIR, "/css/", "/images/", "/javascript/"
    }));

    /** The context that we'll need to use every invocation of doFilter(..).
     * @throws IllegalArgumentException when there exists no skin matching the siteContext.getSite() argument.
     **/
    public static final Site.Context SITE_CONTEXT = UrlResourceLoader.SITE_CONTEXT;

    // Attributes ----------------------------------------------------

    // The filter configuration object we are associated with.  If
    // this value is null, this filter instance is not currently
    // configured.
    private FilterConfig filterConfig = null;
    private static final String LOCALE_DETAILS = "Locale details: Language: {0}, Country: {1} and Variant: {2}";

    // Static --------------------------------------------------------

    static String getRequestId(final ServletRequest servletRequest){

        if(null == servletRequest.getAttribute("UNIQUE_ID")){
            servletRequest.setAttribute("UNIQUE_ID", UUID.randomUUID().toString());
        }
        return (String)servletRequest.getAttribute("UNIQUE_ID");
    }

    /** The method to obtain the correct Site from the request.
     * It only returns a site with a locale supported by that site.
     ** @param servletRequest
     * @return the site instance. or null if no such skin has been deployed.
     */
    public static Site getSite(final ServletRequest servletRequest) {
        // find the current site. Since we are behind a ajp13 connection request.getServerName() won't work!
        // httpd.conf needs:
        //      1) "JkEnvVar SERVER_NAME" inside the virtual host directive.
        //      2) "UseCanonicalName Off" to assign ServerName from client's request.
        final String vhost = getServerName(servletRequest);

        // Tweak the port if SERVER_PORT has been explicitly set. (We may have gone through Apache or Cisco LB).
        final String correctedVhost = Site.SERVER_PORT > 0 && vhost.indexOf(':') > 0
                ? vhost.substring(0, vhost.indexOf(':') + 1) + Site.SERVER_PORT
                : vhost;

        LOG.trace(DEBUG_REQUESTED_VHOST + correctedVhost);

        // Construct the site object off the browser's locale, even if it won't finally be used.
        final Locale locale = servletRequest.getLocale();

        final Site result;
        try{
            result = Site.valueOf(SITE_CONTEXT, correctedVhost, locale);

            final SiteConfiguration.Context siteConfCxt = UrlResourceLoader.newSiteConfigurationContext(result);
            final SiteConfiguration siteConf = SiteConfiguration.instanceOf(siteConfCxt);
            servletRequest.setAttribute(SiteConfiguration.NAME_KEY, siteConf);

            if(LOG.isTraceEnabled()){ // MessageFormat.format(..) is expensive
                LOG.trace(MessageFormat.format(
                        LOCALE_DETAILS, locale.getLanguage(), locale.getCountry(), locale.getVariant()));
            }

            // Check if the browser's locale is supported by this skin. Use it if so.
            if( siteConf.isSiteLocaleSupported(locale) ){
                return result;
            }

            // Use the skin's default locale. For some reason that fails use JVM's default.
            final String[] prefLocale = null != siteConf.getProperty(SiteConfiguration.SITE_LOCALE_DEFAULT)
                    ? siteConf.getProperty(SiteConfiguration.SITE_LOCALE_DEFAULT).split("_")
                    : new String[]{Locale.getDefault().toString()};

            switch(prefLocale.length){

                case 3:
                    LOG.trace(result+INFO_USING_DEFAULT_LOCALE + prefLocale[0] + '_' + prefLocale[1] + '_' + prefLocale[2]);
                    return Site.valueOf(SITE_CONTEXT, correctedVhost, new Locale(prefLocale[0], prefLocale[1], prefLocale[2]));

                case 2:
                    LOG.trace(result+INFO_USING_DEFAULT_LOCALE + prefLocale[0] + '_' + prefLocale[1]);
                    return Site.valueOf(SITE_CONTEXT, correctedVhost, new Locale(prefLocale[0], prefLocale[1]));

                case 1:
                default:
                    LOG.trace(result+INFO_USING_DEFAULT_LOCALE + prefLocale[0]);
                    return Site.valueOf(SITE_CONTEXT, correctedVhost, new Locale(prefLocale[0]));

            }
        }catch(IllegalArgumentException iae){
            return null;
        }
    }

    // Constructors --------------------------------------------------

    /** Default constructor. **/
    public SiteLocatorFilter() {
    }

    // Public --------------------------------------------------------

    /** Will redirect to correct (search-config) url for resources (css,images, javascript).
     *
     * @param request The servlet request we are processing
     * @param r The servlet response
     * @param chain The filter chain we are processing
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(
            final ServletRequest request,
            final ServletResponse r,
            final FilterChain chain)
                throws IOException, ServletException {

        LOG.trace("doFilter(..)");

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final ServletResponse response = r instanceof HttpServletResponse
            ? new AccessLogResponse((HttpServletResponse)r)
            : r;

        try{
            if(request instanceof HttpServletRequest) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                final HttpServletResponse  httpServletResponse = (HttpServletResponse) response;
                if (httpServletRequest.getRequestURI().contains(CONFIGURATION_RESOURCE)){
                    /* We are looping, looking for a site search which does not exsist */
                    LOG.info("We are looping, looking for a site search which does not exist");
                    httpServletResponse.reset();
                    httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }

            doBeforeProcessing(request, response);
            logAccessRequest(request);

            if (request instanceof HttpServletRequest) {

                final HttpServletRequest req = (HttpServletRequest)request;
                final HttpServletResponse res = (HttpServletResponse) response;
                final Site site = (Site) req.getAttribute(Site.NAME_KEY);
                final String uri = req.getRequestURI();
                final String resource = uri;
                final String rscDir = resource != null && resource.indexOf('/',1) >= 0
                        ? resource.substring(0, resource.indexOf('/',1)+1)
                        : null;

                if(isAccessAllowed(req)){

                    if (rscDir != null && EXTERNAL_DIRS.contains(rscDir)) {

                        // This URL does not belong to search-portal
                        final String url = FindResource.find(site, resource);

                        if (url != null) {
                            // Cache the client-resource redirects on a short (session-equivilant) period
                            res.setHeader("Cache-Control", "Public");
                            res.setDateHeader("Expires", System.currentTimeMillis() + 1000*60*10); // ten minutes
                            // send the redirect to where the resource really resides
                            res.sendRedirect(url);
                            LOG.trace(resource + DEBUG_REDIRECTING_TO + url);


                        }else if (resource.startsWith(PUBLISH_DIR)){
                            // XXX Why do we avoid sending 404 for publish resources?

                            res.sendError(HttpServletResponse.SC_NOT_FOUND);

                            if(resource.endsWith(".css")){
                                LOG.info(ERR_NOT_FOUND + resource);
                            }else{
                                LOG.error(ERR_NOT_FOUND + resource);
                            }
                        }

                    } else  {
                        doChainFilter(chain, request, response);
                    }

                }else{
                    // Forbidden client
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                }

            }  else  {
                doChainFilter(chain, request, response);
            }

            doAfterProcessing(request, response);

        }  catch (Exception e) {
            // Don't let anything through without logging it.
            //  Otherwise it ends in a different logfile.
            LOG.error(ERR_UNCAUGHT_RUNTIME_EXCEPTION);
            for (Throwable t = e; t != null; t = t.getCause()) {
                LOG.error(t.getMessage(), t);
            }
            throw new ServletException(e);

        }finally{
            logAccessResponse(request, response, stopWatch);
        }

    }

    /**
     * Return the filter configuration object for this filter.
     * @return
     */
    public FilterConfig getFilterConfig() {
        return (filterConfig);
    }


    /**
     * Set the filter configuration object for this filter.
     *
     * @param filterConfig The filter configuration object
     */
    public void setFilterConfig(final FilterConfig filterConfig) {

        this.filterConfig = filterConfig;
    }

    /**
     * Destroy method for this filter
     *
     */
    @Override
    public void destroy() {
    }


    /**
     * Init method for this filter
     *
     */
    @Override
    public void init(final FilterConfig filterConfig) {

        this.filterConfig = filterConfig;
        if (filterConfig != null) {
            LOG.debug("Initializing filter");
        }
    }

    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        return filterConfig == null
                ? "ResourceRedirectFilter()"
                : "ResourceRedirectFilter(" + filterConfig + ")";

    }

    // Package protected ---------------------------------------------

    // Protected -----------------------------------------------------

    // Private -------------------------------------------------------

    private static void doChainFilter(
            final FilterChain chain,
            final ServletRequest request,
            final ServletResponse response) throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            doChainFilter(chain, (HttpServletRequest)request, (HttpServletResponse)response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static void doChainFilter(
            final FilterChain chain,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, ServletException {

        final HttpSession session = request.getSession();

        // fetch the user's deque
        final Deque<ServletRequest> deque = getUsersDeque(session);

        // lock to execute
        final ReentrantLock lock = (ReentrantLock) session.getAttribute(USER_REQUEST_LOCK);

        // deque has a time limit. start counting.
        long timeLeft = WAIT_TIME;

        try{
            // attempt to join deque
            if (deque.offerFirst(request)) {
                timeLeft = tryLock(request, deque, lock, timeLeft);
            }

            if(lock.isHeldByCurrentThread()){

                // waiting is over. and we can execute
                chain.doFilter(request, response);

            }else{
                // we failed to execute. return 409 response.
                if (response instanceof HttpServletResponse) {

                    LOG.warn(" -- response 409 " +
                            (0 < timeLeft
                            ? "(More then " + REQUEST_QUEUE_SIZE + " requests already in queue)"
                            : "(Timeout: Waited " + WAIT_TIME + " ms)"));

                    response.sendError(HttpServletResponse.SC_CONFLICT);
                }
            }
        }finally{

            // take out of deque first
            deque.remove(request);

            // release the lock, waiting up the next request
            if(lock.isHeldByCurrentThread()){ lock.unlock(); }
        }
    }

    private static Deque<ServletRequest> getUsersDeque(final HttpSession session){

        @SuppressWarnings("unchecked")
        Deque<ServletRequest> deque = (BlockingDeque<ServletRequest>) session.getAttribute(USER_REQUEST_QUEUE);

        // construct deque if necessary
        if (null == deque) {
            // it may be possible for duplicates across threads to be constructed here
            deque = new LinkedBlockingDeque<ServletRequest>(REQUEST_QUEUE_SIZE);
            session.setAttribute(USER_REQUEST_QUEUE, deque);
            session.setAttribute(USER_REQUEST_LOCK, new ReentrantLock());
        }

        return deque;
    }

    private static long tryLock(
            final HttpServletRequest request,
            final Deque<ServletRequest> deque,
            final Lock lock,
            long timeLeft){

        final long start = System.currentTimeMillis();

        try {
            do{
                timeLeft = WAIT_TIME - (System.currentTimeMillis() - start);

                // let's sleep. sleeping too long results in 409 response
                if(0 >= timeLeft || !lock.tryLock(timeLeft, TimeUnit.MILLISECONDS)){
                    // we timed out or got the lock. waiting is over
                    break;

                }else if(deque.peek() != request){
                    // we've acquired the lock but we're not at front of deque
                    // release the lock and try again
                    lock.unlock();
                }
            }while(deque.peek() != request);


        }catch(InterruptedException ie){
            LOG.error("Failed using user's lock", ie);
        }

        return timeLeft;
    }

    private void doBeforeProcessing(final ServletRequest request, final ServletResponse response)
            throws IOException, ServletException {

        LOG.trace("doBeforeProcessing()");

        final Site site = getSite(request);

        if(null != site){

            request.setAttribute(Site.NAME_KEY, site);
            request.setAttribute("startTime", FindResource.START_TIME);
            MDC.put(Site.NAME_KEY, site.getName());
            MDC.put("UNIQUE_ID", getRequestId(request));

            /* Setting default encoding */
            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");

        }else{
            throw new ServletException("SiteLocatorFilter with no Site :-(");
        }
    }

    private void doAfterProcessing(final ServletRequest request, final ServletResponse response)
            throws IOException, ServletException {

        LOG.trace("doAfterProcessing()");
        //
        // Write code here to process the request and/or response after
        // the rest of the filter chain is invoked.
        //

    }

    private static void logAccessRequest(final ServletRequest request){

        final StringBuilder url = new StringBuilder();
        final String referer;
        final String method;
        final String ip = request.getRemoteAddr();
        final String userAgent;
        final String sesamId;
        final String sesamUser;

        if(request instanceof HttpServletRequest){

            final HttpServletRequest req = (HttpServletRequest)request;
            url.append(req.getRequestURI() + (null != req.getQueryString() ? '?' + req.getQueryString() : ""));
            referer = req.getHeader("Referer");
            method = req.getMethod();
            userAgent = req.getHeader("User-Agent");
            sesamId = getCookieValue(req, "SesamID");
            sesamUser = getCookieValue(req, "SesamUser");


        }else{

            for(@SuppressWarnings("unchecked")
                    Enumeration<String> en = request.getParameterNames(); en.hasMoreElements(); ){

                final String param = en.nextElement();
                url.append(param + '=' + request.getParameter(param));
                if(en.hasMoreElements()){
                    url.append('&');
                }
            }
            referer = method = userAgent = sesamId = sesamUser = UNKNOWN;
        }

        ACCESS_LOG.info("<request>"
                + "<url method=\"" + method + "\">" + StringEscapeUtils.escapeXml(url.toString()) + "</url>"
                + (null != referer ? "<referer>" + StringEscapeUtils.escapeXml(referer) + "</referer>" : "")
                + "<browser ipaddress=\"" + ip + "\">" + StringEscapeUtils.escapeXml(userAgent) + "</browser>"
                + "<user id=\"" + sesamId + "\">" + sesamUser + "</user>"
                + "</request>");
    }

    private static void logAccessResponse(
            final ServletRequest request,
            final ServletResponse response,
            final StopWatch stopWatch){

        final String code;

        if(request instanceof HttpServletRequest){

            final HttpServletRequest req = (HttpServletRequest)request;

        }else{

        }

        if(response instanceof AccessLogResponse){

            final AccessLogResponse res = (AccessLogResponse)response;
            code = String.valueOf(res.getStatus());

        }else{

            code = UNKNOWN;
        }

        stopWatch.stop();

        ACCESS_LOG.info("<response code=\"" + code + "\" time=\"" + stopWatch + "\"/>");
    }

    // probably apache commons could simplify this // duplicated in SearchServlet
    private static String getCookieValue(final HttpServletRequest request, final String cookieName){

        String value = "";
        // Look in attributes (it could have already been updated this request)
        if( null != request ){

            // Look through cookies
            if( null != request.getCookies() ){
                for( Cookie c : request.getCookies()){
                    if( c.getName().equals( cookieName ) ){
                        value = c.getValue();
                        break;
                    }
                }
            }
        }

        return value;
    }

    private static String getServerName(final ServletRequest servletRequest){

        // find the current site. Since we are behind a ajp13 connection request.getServerName() won't work!
        // httpd.conf needs:
        //      1) "JkEnvVar SERVER_NAME" inside the virtual host directive.
        //      2) "UseCanonicalName Off" to assign ServerName from client's request.
        return null != servletRequest.getAttribute("SERVER_NAME")
            ? (String) servletRequest.getAttribute("SERVER_NAME")
            // falls back to this when not behind Apache. (Development machine).
            : servletRequest.getServerName() + ":" + servletRequest.getServerPort();
    }

    private static boolean isAccessAllowed(final HttpServletRequest request){

        final SiteConfiguration siteConf = (SiteConfiguration) request.getAttribute(SiteConfiguration.NAME_KEY);
        final String allowedList = siteConf.getProperty(SiteConfiguration.ALLOW_LIST);
        final String disallowedList = siteConf.getProperty(SiteConfiguration.DISALLOW_LIST);
        final String ipaddress = request.getRemoteAddr();

        boolean allowed = false;
        boolean disallowed = false;
        if(null != allowedList && 0 < allowedList.length()){
            for(String allow : allowedList.split(",")){
                allowed |= ipaddress.startsWith(allow);
            }
        }else{
            allowed = true;
        }
        if(null != disallowedList && 0 < disallowedList.length()){
            for(String disallow : disallowedList.split(",")){
                disallowed |= ipaddress.startsWith(disallow);
            }
        }
        return allowed && !disallowed;
    }

    private static class AccessLogResponse extends HttpServletResponseWrapper{

        private int status = HttpServletResponse.SC_OK;

        public AccessLogResponse(final HttpServletResponse response){
            super(response);
        }

        @Override
        public void setStatus(final int status){
            super.setStatus(status);
            this.status = status;
        }
        @Override
        public void setStatus(final int status, final String msg){
            super.setStatus(status, msg);
            this.status = status;
        }
        @Override
        public void sendError(final int sc) throws IOException{
            super.sendError(sc);
            status = sc;
        }
        @Override
        public void sendError(final int sc, final String msg) throws IOException{
            super.sendError(sc, msg);
            status = sc;
        }
        @Override
        public void sendRedirect(final String arg0) throws IOException {
            super.sendRedirect(arg0);
            this.status = HttpServletResponse.SC_FOUND;
        }

        public int getStatus(){
            return status;
        }
    }
}
