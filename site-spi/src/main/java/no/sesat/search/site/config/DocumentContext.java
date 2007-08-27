/* Copyright (2005-2007) Schibsted Søk AS
 * This file is part of SESAT.
 * You can use, redistribute, and/or modify it, under the terms of the SESAT License.
 * You should have received a copy of the SESAT License along with this program.  
 * If not, see https://dev.sesat.no/confluence/display/SESAT/SESAT+License
 *
 * Created on 23 January 2006, 13:54
 */

package no.sesat.search.site.config;

import javax.xml.parsers.DocumentBuilder;
import no.schibstedsok.commons.ioc.BaseContext;
import no.sesat.search.site.SiteContext;

/** Defines the context for consumers of DocumentLoaders.
 *
 * @version $Id: ResourceContext.java 2045 2006-01-25 12:10:01Z mickw $
 * @author <a href="mailto:mick@wever.org">Michael Semb Wever</a>
 */
public interface DocumentContext extends BaseContext {
    /** Create a new DocumentLoader for the given resource name/path and load it with the given DocumentBuilder.
     * @param resource the resource name/path.
     * @param builder the DocumentBuilder to build the DOM resource with.
     * @return the new DocumentLoader to use.
     **/
    DocumentLoader newDocumentLoader(SiteContext siteCxt, String resource, DocumentBuilder builder);
}