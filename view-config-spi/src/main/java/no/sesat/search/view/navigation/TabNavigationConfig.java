/* Copyright (2005-2012) Schibsted ASA
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
* Jul 20, 2007 1:35:17 PM
*/
package no.sesat.search.view.navigation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static no.sesat.search.site.config.AbstractDocumentFactory.fillBeanProperty;
import static no.sesat.search.site.config.AbstractDocumentFactory.ParseType;
import org.w3c.dom.Element;

import no.sesat.search.view.navigation.NavigationConfig.Nav.ControllerFactory;
import no.sesat.search.site.config.AbstractDocumentFactory;

/**
 *
 * @version $Id$
 */
@ControllerFactory("no.sesat.search.view.navigation.tab.TabNavigationControllerFactory")
public final class TabNavigationConfig extends NavigationConfig.Nav {

    private final List<String> commandNames;
    private final List<String> values;
    private final String image;
    private final String template;
    private final String urlSuffix;

    @SuppressWarnings("unchecked")
    public TabNavigationConfig(
            final NavigationConfig.Nav parent,
            final NavigationConfig.Navigation navigation,
            final Element navElement) {

        super(parent, navigation, navElement);

        final String commandNames = AbstractDocumentFactory.parseString(navElement.getAttribute("command-names").replaceAll("\\s", ""), null);
        this.commandNames =  null != commandNames
                ? Collections.unmodifiableList(Arrays.asList(commandNames.split(",")))
                : Collections.EMPTY_LIST;

        final String values = AbstractDocumentFactory.parseString(navElement.getAttribute("values").replaceAll("\\s", ""), null);
        this.values = null != values
                ? Collections.unmodifiableList(Arrays.asList(values.split(",")))
                : null;

        image = AbstractDocumentFactory.parseString(navElement.getAttribute("image"), null);
        template = AbstractDocumentFactory.parseString(navElement.getAttribute("template"), null);
        urlSuffix = AbstractDocumentFactory.parseString(navElement.getAttribute("url-suffix"), null);
    }

    public List<String> getCommandNames() {
        return commandNames;
    }

    @Override
    public String getField() {
        return "c";
    }

    public List<String> getValues(){
        // XXX expensive to create new array and list each call
        return null != values ? values : Collections.unmodifiableList(Arrays.asList(new String[]{getTab()}));
    }

    public String getImage(){
        return image;
    }

    public String getTemplate(){
        return template;
    }

    public String getUrlSuffix(){
        return urlSuffix;
    }

    @Override
    public String toString() {
        return "Tab{ id=\"" + getId() + "\" tab=\"" + getTab() + "\"}";
    }

}
