/*
 * Copyright (2005-2007) Schibsted Søk AS
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
 *
 */
package no.sesat.search.mode;

import java.io.Serializable;
import no.sesat.search.mode.config.*;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import no.sesat.search.mode.config.SearchConfiguration;
import no.sesat.search.run.handler.RunHandlerConfig;
import no.sesat.search.run.transform.RunTransformerConfig;

/**
 * @author <a href="mailto:magnus.eklund@schibsted.no">Magnus Eklund</a>
 * @version <tt>$Id$</tt>
 */
public final class SearchMode implements Serializable {

    // Constants -----------------------------------------------------

    /**
     *
     */
    public enum SearchCommandExecutorConfig{
        /**
         *
         */
        @Controller("SequentialSearchCommandExecutor")
        SEQUENTIAL,
        /**
         *
         */
        @Controller("ThrottledSearchCommandExecutor")
        PARALLEL;
        /**
         *
         */
        @Documented
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.FIELD})
        @Inherited
        public @interface Controller {
            /**
             *
             * @return
             */
            public String value();
        }
    }

    // Attributes ----------------------------------------------------

    private SearchCommandExecutorConfig searchCommandExecutor = SearchCommandExecutorConfig.SEQUENTIAL;

    private Collection<SearchConfiguration> searchConfigurations;
    private SearchMode parentSearchMode;
    private boolean queryAnalysisEnabled = false;
    private String parentMode;
    private String id;
    private List<RunHandlerConfig> runHandlers;
    private List<RunTransformerConfig> runTransformers;


    // Static --------------------------------------------------------

    // Constructors --------------------------------------------------

    /**
     *
     */
    public SearchMode(){
    }

    /**
     *
     * @param inherit
     */
    public SearchMode(final SearchMode inherit){
        if( inherit != null ){
            parentSearchMode = inherit;
            parentMode = inherit.parentMode;
            queryAnalysisEnabled = inherit.queryAnalysisEnabled;
            searchCommandExecutor = inherit.searchCommandExecutor;
        }
    }

    /**
     *
     * @return
     */
    public Collection<SearchConfiguration> getSearchConfigurations() {
        return searchConfigurations;
    }

    /**
     *
     * @param name
     * @return
     */
    public SearchConfiguration getSearchConfiguration(final String name) {

        for( SearchConfiguration sc : searchConfigurations){
            if( sc.getName().equals(name) ){
                return sc;
            }
        }
        return null;
    }

    /**
     *
     * @param searchConfigurations
     */
    public void setSearchConfigurations(Collection<SearchConfiguration> searchConfigurations) {
        this.searchConfigurations = Collections.unmodifiableCollection(searchConfigurations);
    }

    /**
     *
     * @return
     */
    public SearchCommandExecutorConfig getExecutor() {
        return searchCommandExecutor;
    }

    /**
     *
     * @param searchCommandExecutor
     */
    public void setExecutor(final SearchCommandExecutorConfig searchCommandExecutor) {
        this.searchCommandExecutor = searchCommandExecutor;
    }

    /**
     *
     * @return
     */
    public SearchMode getParentSearchMode() {
        return parentSearchMode;
    }

    /**
     * Get the queryAnalysisEnabled.
     *
     * @return the queryAnalysisEnabled.
     */
    public boolean isAnalysis() {
        return queryAnalysisEnabled;
    }

    /**
     * Set the queryAnalysisEnabled.
     *
     * @param queryAnalysisEnabled The queryAnalysisEnabled to set.
     */
    public void setAnalysis(boolean queryAnalysisEnabled) {
        this.queryAnalysisEnabled = queryAnalysisEnabled;
    }

    /**
     * Getter for property parentMode.
     * @return Value of property parentMode.
     */
    public String getParentMode() {
        return this.parentMode;
    }

    /**
     * Setter for property parentMode.
     * @param parentMode New value of property parentMode.
     */
    public void setParentMode(String parentMode) {
        this.parentMode = parentMode;
    }

    @Override
    public String toString(){
        return id + (parentSearchMode != null ? " --> " + parentSearchMode.toString() : "");
    }

    /**
     * Getter for property id.
     * @return Value of property id.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Setter for property id.
     * @param id New value of property id.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Setter for run handlers
     * @param runHandlers New list of run handlers
     */
    public void setRunHandlers(final List<RunHandlerConfig> runHandlers) {
        this.runHandlers = Collections.unmodifiableList(runHandlers);
    }

    /**
     * Getter for run handlers
     * @return List of run handlers
     */
     public List<RunHandlerConfig> getRunHandlers() {
         return runHandlers;
     }

     /**
      * Setter for run transformers
      * @param runTransformers New List of run transformers
      */
     public void setRunTransformers(final List<RunTransformerConfig> runTransformers) {
         this.runTransformers = Collections.unmodifiableList(runTransformers);
     }

     /**
      * Getter for run transformers
      * @return List of run transformers
      */
     public List<RunTransformerConfig> getRunTransformers() {
         return runTransformers;
     }
     
    // Inner classes -------------------------------------------------

}
