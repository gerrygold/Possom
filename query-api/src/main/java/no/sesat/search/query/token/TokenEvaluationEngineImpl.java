/*
 * Copyright (2005-2008) Schibsted Søk AS
 * This file is part of SESAT.
 *
 *   SESAT is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SESAT is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with SESAT.  If not, see <http://www.gnu.org/licenses/>.
 */
package no.sesat.search.query.token;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import no.schibstedsok.commons.ioc.ContextWrapper;
import no.sesat.search.query.Clause;
import no.sesat.search.query.Query;
import no.sesat.search.query.token.TokenPredicate.EvaluationException;
import no.sesat.search.site.Site;
import org.apache.log4j.Logger;

/**
 * TokenEvaluateFactory provides knowledge about which implementation of
 * {@link TokenEvaluator} that can handle a particular token.
 *
 * This class is not synchronised (Except for the evaluateTerm method).
 * Manual synhronisation must be taken when calling operate or setter methods from inside SearchCommand classes.
 *
 * @author <a href="mailto:magnus.eklund@gmail.com">Magnus Eklund</a>
 * @version $Id$
 */
public final class TokenEvaluationEngineImpl implements TokenEvaluationEngine {

    // Constants -----------------------------------------------------
        
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static final Logger LOG = Logger.getLogger(TokenEvaluationEngineImpl.class);
    private static final String ERR_FAILED_CONSTRUCTING_FAST_EVALUATOR = "Failed to construct the fast evaluator";
    private static final String ERR_FAST_EVALUATOR_CREATOR_INTERRUPTED =
            "Interrupted waiting for FastEvaluatorCreator. Evaluation on this query will fail.";
    private static final String ERR_TOKENTYPE_WIHOUT_IMPL = "Token type not known or implemented. ";
    private static final String DEBUG_POOL_COUNT = "Pool size: ";
    private static final String ERR_METHOD_CLOSED_TO_OTHER_THREADS
            = "TokenPredicate.evaluate(..) can only be used by same thread that created TokenEvaluationEngine!";
    private static final String ERR_ENGINE_MISSING_STATE = "TokenEvaluationEngine must have state assigned";

    // Attributes -----------------------------------------------------
        
    private final Context context;
    private final Future fastEvaluatorCreator;
    private TokenEvaluator fastEvaluator;
    private final JepTokenEvaluator jedEvaluator;
    private State state;
    private volatile Thread owningThread = Thread.currentThread();

    // Constructors -----------------------------------------------------
    
    /**
     * Create a new TokenEvaluationEngine.
     *
     * @param cxt
     */
    public TokenEvaluationEngineImpl(final Context cxt) {


        if(LOG.isDebugEnabled() && EXECUTOR instanceof ThreadPoolExecutor){
            final ThreadPoolExecutor tpe = (ThreadPoolExecutor)EXECUTOR;
            LOG.debug(DEBUG_POOL_COUNT + tpe.getActiveCount() + '/' + tpe.getPoolSize());
        }

        context = cxt;
        fastEvaluatorCreator = EXECUTOR.submit(new FastEvaluatorCreator());

        jedEvaluator = new JepTokenEvaluator(context.getQueryString());
    }


    public TokenEvaluator getEvaluator(final TokenPredicate token) throws VeryFastListQueryException {

        // TODO clean up when implementing SEARCH-3540. can be configured through annotations.
        if(TokenPredicate.Type.FAST == token.getType()){
            return getFastEvaluator();

        }else if(TokenPredicate.Type.REGEX == token.getType()){
            return RegExpEvaluatorFactory.instanceOf(
                        ContextWrapper.wrap(RegExpEvaluatorFactory.Context.class,context)).getEvaluator(token);

        }else if(TokenPredicate.Type.JEP == token.getType()){
            return jedEvaluator;

        }
        throw new IllegalArgumentException(ERR_TOKENTYPE_WIHOUT_IMPL + token);
    }

    public String getQueryString() {
        return context.getQueryString();
    }

    public Site getSite() {
        return context.getSite();
    }

    public synchronized boolean evaluateTerm(final TokenPredicate predicate, final String term) {

        return evaluateInAnyThread(predicate, new EvaluationState(term, Collections.EMPTY_SET, Collections.EMPTY_SET));
    }

    public synchronized boolean evaluateClause(final TokenPredicate predicate, final Clause clause) {

        return evaluateInAnyThread(predicate, new EvaluationState(clause));
    }

    public synchronized boolean evaluateQuery(final TokenPredicate predicate, final Query query) {

        return evaluateInAnyThread(predicate, query.getEvaluationState());
    }
    
    public boolean evaluate(final TokenPredicate token){

        // process
        if(Thread.currentThread() != getOwningThread()){
            throw new IllegalStateException(ERR_METHOD_CLOSED_TO_OTHER_THREADS);
        }

        try{

            // check that the evaluation hasn't already been done
            // we can only check against the knownPredicates because with the possiblePredicates we are not sure whether
            //  the evaluation is for the building of the known and possible predicate list
            //    (during query parsing)(in which
            //  case we could perform the check) or if we are scoring and need to know if the
            //    possible predicate is really
            //  applicable now (in the context of the whole query).
            final Set<TokenPredicate> knownPredicates = getState().getKnownPredicates();
            if(null != knownPredicates && knownPredicates.contains(token)){
                return true;
            }

            final TokenEvaluator evaluator = getEvaluator(token);

            if(null != getState().getTerm()){

                // Single term or clause evaluation
                return evaluator.evaluateToken(token, getState().getTerm(), getQueryString());

            }else if(null != getState().getQuery()){

                // Whole query evaluation
                return getState().getPossiblePredicates().contains(token)
                        && evaluator.evaluateToken(token, null, getQueryString());

            }

        }catch(VeryFastListQueryException ie){
            // unfortunately Predicate.evaluate(..) does not declare to throw any checked exceptions.
            //  so we must sneak the VeryFastListQueryException through as a run-time exception.
            throw new EvaluationException(ie);
        }

        throw new IllegalStateException(ERR_ENGINE_MISSING_STATE);        
    }


    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }
    
    // private -----------------------------------------------------  

    private boolean evaluateInAnyThread(final TokenPredicate predicate, final State state) {

        final Thread origThread = owningThread;
        try{
            // setup the engine's required state before any evaluation process
            setState(state);
            // temporarily change owningThread to allow this thread to evaluate
            owningThread = Thread.currentThread();
            // run the evaluation process
            return evaluate(predicate);

        }finally{
            setState(null);
            owningThread = origThread;
        }
    }
    
    private synchronized Thread getOwningThread() {
        return owningThread;
    }
    
    // inner classes -----------------------------------------------------    

    private final class FastEvaluatorCreator implements Runnable{
        public void run() {

            try {

                fastEvaluator = new VeryFastTokenEvaluator(
                        ContextWrapper.wrap(VeryFastTokenEvaluator.Context.class, context));

            } catch (VeryFastListQueryException ex) {
                LOG.error(ERR_FAILED_CONSTRUCTING_FAST_EVALUATOR);
            }
        }

    }

    private TokenEvaluator getFastEvaluator() throws VeryFastListQueryException {

        try {
            fastEvaluatorCreator.get();

        } catch (InterruptedException ex) {
            LOG.error(ERR_FAST_EVALUATOR_CREATOR_INTERRUPTED, ex);
            throw new VeryFastListQueryException(ERR_FAILED_CONSTRUCTING_FAST_EVALUATOR, ex);
        } catch (ExecutionException ex) {
            LOG.error(ERR_FAST_EVALUATOR_CREATOR_INTERRUPTED, ex);
            throw new VeryFastListQueryException(ERR_FAILED_CONSTRUCTING_FAST_EVALUATOR, ex);
        }
        if( null == fastEvaluator ){
            throw new VeryFastListQueryException(ERR_FAILED_CONSTRUCTING_FAST_EVALUATOR, new NullPointerException());
        }

        return fastEvaluator;
    }

}
