/*
 * SearchResultModuleParser.java
 *
 */

package no.schibstedsok.searchportal.view.output.syndication.modules;

import com.sun.syndication.feed.module.Module;
import com.sun.syndication.io.ModuleParser;
import org.jdom.Element;
import org.jdom.Namespace;

/**
 * A parser for the rome module defining the sesam syndication feed format.
 */
public class SearchResultModuleParser implements ModuleParser {
    
    /**
     * Creates a new instance of this class.
     */
    public SearchResultModuleParser() {
    }
    
    /**
     * @inherit
     */
    public String getNamespaceUri() {
        return SearchResultModule.URI;
    }
    
    /**
     * @inherit
     */
    public Module parse(final Element root) {
        
        final SearchResultModule m = new SearchResultModuleImpl();
        
        boolean touched = false;
        
        final Element e = root.getChild("numberOfHits", SearchResultModuleImpl.NS);
        
        if (e != null) {
            touched = true;
            m.setNumberOfHits(e.getText());
        }
        
        return touched == true ? m : null;
    }
}
