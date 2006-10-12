// Copyright (2006) Schibsted Søk AS
/*
 *
 * Created on March 4, 2006, 2:32 PM
 *
 */

package no.schibstedsok.searchportal.mode.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import no.schibstedsok.searchportal.mode.SearchCommandFactory;
import no.schibstedsok.searchportal.mode.config.SearchConfiguration;
import no.schibstedsok.searchportal.run.RunningQuery;
import no.schibstedsok.searchportal.result.SearchResult;
import org.apache.log4j.Logger;
import org.testng.annotations.Test;


/** Executes all search commands in the given different tabs.
 * Corresponding to that tab's mode.
 *
 * @author <a href="mailto:mick@wever.org">Michael Semb Wever</a>
 * @version $Id$
 */
public final class AllSearchCommandsTest extends AbstractSearchCommandTest {

    private static final Logger LOG = Logger.getLogger(AllSearchCommandsTest.class);

    private static final String DEBUG_EXECUTE_COMMAND = "Testing command ";

    public AllSearchCommandsTest(final String name) {
        super(name);
    }

    
    @Test
    public void testAllNorskNettsokSearchCommands() {
        executeTestOfQuery("linux", "d");
    }

    
    @Test
    public void testAllInternasjonalNettsokSearchCommands() {

        executeTestOfQuery("linux", "g");
    }

    @Test
    public void testAllWhitepagesSearchCommands() {

        executeTestOfQuery("linux", "w");
    }

    @Test
    public void testAllYellowpagesSearchCommands() {

        executeTestOfQuery("linux", "y");
    }

    @Test
    public void testAllNyheterSearchCommands() {

        executeTestOfQuery("linux", "m");
    }

    @Test
    public void testAllBilderSearchCommands() {

        executeTestOfQuery("linux", "p");
    }

    private void executeTestOfQuery(final String query, final String key) {

        // proxy it back to the RunningQuery context.
        final RunningQuery.Context rqCxt = createRunningQueryContext(key);

        final Collection<Callable<SearchResult>> commands = new ArrayList<Callable<SearchResult>>();

        for(SearchConfiguration conf : rqCxt.getSearchMode().getSearchConfigurations()){

            LOG.info(DEBUG_EXECUTE_COMMAND + conf.getName());

            final SearchCommand.Context cxt = createCommandContext(query, rqCxt, conf.getName());

            final AbstractSearchCommand cmd
                    = (AbstractSearchCommand) SearchCommandFactory.createSearchCommand(cxt, Collections.EMPTY_MAP);

            commands.add(cmd);
        }
        try {

            rqCxt.getSearchMode().getExecutor().invokeAll(commands, 10000);
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

}