// Copyright (2006) Schibsted Søk AS
/*
 * SearchModeFactoryTest.java
 * JUnit based test
 *
 * Created on April 19, 2006, 3:31 PM
 */

package no.schibstedsok.searchportal.mode.config;

import java.util.Locale;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import no.schibstedsok.searchportal.TestCase;
import no.schibstedsok.searchportal.mode.SearchModeFactory;
import no.schibstedsok.searchportal.site.SiteContext;
import no.schibstedsok.searchportal.util.config.DocumentLoader;
import no.schibstedsok.searchportal.util.config.FileResourceLoader;
import no.schibstedsok.searchportal.util.config.PropertiesLoader;
import no.schibstedsok.searchportal.site.Site;
import org.apache.log4j.Logger;

/**
 *
 * @author <a href="mailto:mick@wever.org">Michael Semb Wever</a>
 */
public final class SearchModeFactoryTest extends TestCase {

    private static final Logger LOG = Logger.getLogger(SearchModeFactoryTest.class);

    /** TODO comment me. **/
    public SearchModeFactoryTest(final String testName) {
        super(testName);
    }

    /** TODO comment me. **/
    protected void setUp() throws Exception {
    }

    /** TODO comment me. **/
    protected void tearDown() throws Exception {
    }

    /**
     * Test of valueOf method, of class no.schibstedsok.searchportal.configuration.SearchModeFactory.
     */
    public void testGetModeFactory(){

        assertNotNull(getModeFactory(null));
    }

    private SearchModeFactory getModeFactory(final Locale locale) {
        LOG.trace("getModeFactory");

        final SearchModeFactory.Context cxt = new SearchModeFactory.Context(){
            private final Site.Context siteConstructorContext = new Site.Context(){
                public String getParentSiteName(final SiteContext siteContext){
                    return Site.DEFAULT.getName();
                }
            };

            public DocumentLoader newDocumentLoader(final String resource, final DocumentBuilder builder) {
                return FileResourceLoader.newDocumentLoader(this, resource, builder);
            }
            public Site getSite()  {
                return locale == null
                        ? Site.DEFAULT
                        : Site.valueOf(siteConstructorContext, Site.DEFAULT.getName(), locale);
            }
            public PropertiesLoader newPropertiesLoader(final String resource, final Properties properties) {
                return FileResourceLoader.newPropertiesLoader(this, resource, properties);
            }
        };

        final SearchModeFactory result = SearchModeFactory.valueOf(cxt);
        assertNotNull(result);

        return result;
    }

    /**
     * Test of getMode method, of class no.schibstedsok.searchportal.configuration.SearchModeFactory.
     */
    public void testGetMode() {
        LOG.trace("testGetMode");

        final String id = "norsk-magic";
        final SearchModeFactory instance = getModeFactory(null);

        final SearchMode result = instance.getMode(id);
        assertNotNull(result);
    }

    /**
     * Test of memory against getMode method,
     * of class no.schibstedsok.searchportal.configuration.SearchModeFactory.
     */
    public void testGetModeOnAllAvailableLocales() {
        LOG.trace("testGetModeOnAllAvailableLocales");

        final String id = "norsk-magic";
        System.gc();
        final long initialTotal = Runtime.getRuntime().totalMemory();
        final long initialFree = Runtime.getRuntime().freeMemory();
        LOG.info("Number of Available locales " + Locale.getAvailableLocales().length);
        for(Locale l : Locale.getAvailableLocales()){
            final SearchModeFactory instance = getModeFactory(l);

            final SearchMode result = instance.getMode(id);
            assertNotNull(result);
        }
        LOG.info("Total memory increased "+(Runtime.getRuntime().totalMemory()-initialTotal) + " bytes");
        LOG.info("Free memory decreased "+(initialFree-Runtime.getRuntime().freeMemory()) + " bytes");
        System.gc();
    }

}
