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

import static no.sesat.search.site.config.AbstractDocumentFactory.fillBeanProperty;
import static no.sesat.search.site.config.AbstractDocumentFactory.ParseType;
import no.sesat.search.view.navigation.NavigationConfig.Nav.ControllerFactory;
import org.w3c.dom.Element;

/** The configuration bean for navigation items generated from a Faceted Search Command's Modifiers.
 *
 * @todo RENAME to ModifierNavigationConfig
 */
@ControllerFactory("no.sesat.search.view.navigation.fast.FastNavigationControllerFactory")
public class FastNavigationConfig extends NavigationConfig.Nav {

    private String commandName;
    private boolean excludeOtherMatches;

    public FastNavigationConfig(
            final NavigationConfig.Nav parent,
            final NavigationConfig.Navigation navigation,
            final Element navElement) {

        super(parent, navigation, navElement);

        /* TODO: temporarily inherit from navigation to provide backward compatibility. remove command-name from navigation */
        fillBeanProperty(this,navigation, "commandName", ParseType.String, navElement, null);
        fillBeanProperty(this, navigation, "excludeOtherMatches", ParseType.Boolean, navElement, "false");
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    @Override
    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public boolean isExcludeOtherMatches() {
        return excludeOtherMatches;
    }

    public void setExcludeOtherMatches(boolean excludeOtherMatches) {
        this.excludeOtherMatches = excludeOtherMatches;
    }
}
