/* Copyright (2006-2007) Schibsted Søk AS
 * This file is part of SESAT.
 *
 *   SESAT is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SESAT is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with SESAT.  If not, see <http://www.gnu.org/licenses/>.

 */
package no.sesat.search.mode.executor;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import no.sesat.search.mode.command.SearchCommand;
import no.sesat.search.result.ResultItem;
import no.sesat.search.result.ResultList;

/**
 * An object that executes a list of {@link java.util.concurrent.Callable} tasks.
 *
 * @author <a href="mailto:magnus.eklund@schibsted.no">Magnus Eklund</a>
 * @version <tt>$Revision$</tt>
 */
public interface SearchCommandExecutor {

    /**
     * Stops this executor, cancelling all pending tasks.
     */
    void stop();

    /**
     * Invoke all commands returning a list of {@link java.util.concurrent.Future}
     * holding their results.
     *
     * @param callables       The list of {@link java.util.concurrent.Callable} to execute.
     * @param timeoutInMillis The timeout in milliseconds
     * @return the list of Futures holding the results.
     * @throws InterruptedException
     */
    Map<Future<ResultList<? extends ResultItem>>,SearchCommand> invokeAll(
            Collection<SearchCommand> callables) throws InterruptedException;
    
    Map<Future<ResultList<? extends ResultItem>>,SearchCommand> waitForAll(
            final Map<Future<ResultList<? extends ResultItem>>,SearchCommand> results,
            final int timeoutInMillis) throws InterruptedException, TimeoutException, ExecutionException;

}
