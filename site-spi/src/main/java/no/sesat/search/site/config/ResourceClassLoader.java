/* Copyright (2006-2012) Schibsted ASA
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
 */
package no.sesat.search.site.config;


import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import no.sesat.search.site.SiteContext;
import org.apache.log4j.Logger;

/** ClassLoader abstraction to handle loading classes across skins.
 *
 * @version $Id$
 */
public abstract class ResourceClassLoader extends ClassLoader {

    /**  Context needed by this class. */
    public interface Context extends BytecodeContext, SiteContext {}

    private static final Logger LOG = Logger.getLogger(ResourceClassLoader.class);

    private final Context context;

    private Collection<String> notFound = new HashSet<String>();
    private ReadWriteLock notFoundLock = new ReentrantReadWriteLock();


    /**
     * Creates a new resource class loader for a site.
     *
     * @param context the context.
     */
    public ResourceClassLoader(final Context context) {
        this.context = context;
    }

    public ResourceClassLoader(final Context context, final ClassLoader parent) {
        super(parent);
        this.context = context;
    }

    /**
     * Returns the jar file the class must be contained in. If null, all jar-files and classes available to the class
     * loader of the resource servlet are searched.
     *
     * @return the name of the jar file.
     */
    protected abstract String getJarName();

    /**
     * Finds classes using a {@link BytecodeLoader}.
     *
     * @param className the clas to find
     * @return the class.
     * @throws ClassNotFoundException if the class cannot be found in this class loader.
     */
    @Override
    protected Class<?> findClass(final String className) throws ClassNotFoundException {

        try {
            // Optimization. Do not try to load class if it has not been found before.
            notFoundLock.readLock().lock();
            if (notFound.contains(className)) {
                throw new ClassNotFoundException(className + " not found");
            }
        } finally {
            notFoundLock.readLock().unlock();
        }

        final BytecodeLoader loader = context.newBytecodeLoader(context, className, getJarName());
        loader.abut();

        final byte[] bytecode = loader.getBytecode();

        // Resource loader loaded empty result means class was not found.
        if (bytecode.length == 0) {
            try {
                notFoundLock.writeLock().lock();
                notFound.add(className);
            } finally {
                notFoundLock.writeLock().unlock();
            }
            throw new ClassNotFoundException(className + " not found");
        }

        // Make sure that the class hasn't been defined by another thread while we are loading the byte code.
        // Only checking below inside the synchronisation block leads to a performance bottleneck.
        if(null != findLoadedClass(className)){
            LOG.debug(className + " already loaded by some other thread ");
            return findLoadedClass(className);
        }

        synchronized(this) {
            // Check again! (failure to do so within the synchronisation block leads to LinkError from duplicates).
            // Make sure that the class hasn't been defined by another thread while we are loading the byte code.
            if(null != findLoadedClass(className)){
                LOG.debug(className + " already loaded by some other thread ");
                return findLoadedClass(className);
            }else{
                return defineClass(className, bytecode, 0, bytecode.length);
            }
        }
    }
}
