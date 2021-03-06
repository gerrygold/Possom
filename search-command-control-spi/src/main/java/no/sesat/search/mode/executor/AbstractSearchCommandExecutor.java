/* Copyright (2012) Schibsted ASA
 *   This file is part of Possom.
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
 * AbstractSearchCommandExecutor.java
 *
 * Created on 29 April 2006, 22:26
 *
 */

package no.sesat.search.mode.executor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import no.sesat.search.mode.command.SearchCommand;
import no.sesat.search.result.ResultItem;
import no.sesat.search.result.ResultList;
import org.apache.log4j.Logger;

/**
 *
 *
 * @version $Id$
 */
abstract class AbstractSearchCommandExecutor implements SearchCommandExecutor {


    protected static final Logger LOG = Logger.getLogger(AbstractSearchCommandExecutor.class);
    private static final String DEBUG_INVOKEALL = "invokeAll using ";


    // Constants -----------------------------------------------------

    private static final String ERR_COMMAND_TIMEOUT = "Timeout on search command ";

    // Attributes ----------------------------------------------------

    // Static --------------------------------------------------------

    // Constructors --------------------------------------------------

    /**
     * Creates a new instance of AbstractSearchCommandExecutor
     */
    AbstractSearchCommandExecutor() {
    }

    // Public --------------------------------------------------------


    public Map<Future<ResultList<ResultItem>>,SearchCommand> invokeAll(
            final Collection<SearchCommand> callables) throws InterruptedException  {

        LOG.debug(DEBUG_INVOKEALL + getClass().getSimpleName());

        final Map<Future<ResultList<ResultItem>>,SearchCommand> results
                = new HashMap<Future<ResultList<ResultItem>>,SearchCommand>();

        for (SearchCommand c : callables) {

            final ExecutorService es = getExecutorService();
            results.put(es.submit(c), c);
        }

        return results;
    }

    public Map<Future<ResultList<ResultItem>>,SearchCommand> waitForAll(
            final Map<Future<ResultList<ResultItem>>,SearchCommand> results,
            final int timeoutInMillis) throws InterruptedException, TimeoutException, ExecutionException{

        // Give the commands a chance to finish its work
        //  Note the current time and subtract any elapsed time from the timeout value
        //   (as the timeout value is intended overall and not for each).
        final long invokedAt = System.currentTimeMillis();

        for (Future<ResultList<ResultItem>> task : results.keySet()) {

            try{
                task.get(
                        Math.max(1, timeoutInMillis - (System.currentTimeMillis() - invokedAt)),
                        TimeUnit.MILLISECONDS);

            }catch(TimeoutException te){
                LOG.error(ERR_COMMAND_TIMEOUT
                        + results.get(task).getSearchConfiguration().getId()
                        +" [" + results.get(task).getSearchConfiguration().getStatisticalName() + ']');
                task.cancel(true);
            }
        }

        // Ensure any cancellations are properly handled
        for(SearchCommand command : results.values()){
            command.handleCancellation();
        }

        // throw a timeout exception if we did not complete in time.
        if(0 >= timeoutInMillis - (System.currentTimeMillis() - invokedAt)){
            throw new TimeoutException();
        }

        return results;
    }

    public void stop() {

        LOG.warn("Shutting down thread pool");
        getExecutorService().shutdownNow();
    }

    protected abstract ExecutorService getExecutorService();

}
