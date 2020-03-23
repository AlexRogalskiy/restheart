/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import io.undertow.predicate.Predicate;
import io.undertow.server.handlers.PathHandler;
import java.util.LinkedHashSet;
import java.util.Set;
import org.restheart.Bootstrapper;
import org.restheart.ConfigurationException;
import org.restheart.plugins.mongodb.Checker;
import org.restheart.plugins.mongodb.GlobalChecker;
import org.restheart.plugins.mongodb.GlobalHook;
import org.restheart.plugins.mongodb.GlobalTransformer;
import org.restheart.plugins.mongodb.Hook;
import org.restheart.plugins.mongodb.Transformer;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsRegistryImpl implements PluginsRegistry {
    private Set<PluginRecord<AuthMechanism>> authMechanisms;

    private Set<PluginRecord<Authenticator>> authenticators;

    private Set<PluginRecord<Authorizer>> authorizers;

    private PluginRecord<TokenManager> tokenManager;

    private Set<PluginRecord<Service>> services;

    private Set<PluginRecord<Initializer>> initializers;

    private Set<PluginRecord<Interceptor>> interceptors;

    private final Set<Predicate> globalSecurityPredicates
            = new LinkedHashSet<>();
    
    // depecated plugins
    
    private Set<PluginRecord<Checker>> checkers;
    
    private Set<PluginRecord<Transformer>> transformers;

    private Set<PluginRecord<Hook>> hooks;

    private final Set<GlobalChecker> globalCheckers
            = new LinkedHashSet<>();
    
    private final Set<GlobalTransformer> globalTransformers
            = new LinkedHashSet<>();

    private final Set<GlobalHook> globalHooks
            = new LinkedHashSet<>();

    private static PluginsRegistryImpl HOLDER;

    public static synchronized PluginsRegistryImpl getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistryImpl();
        }

        return HOLDER;
    }

    private PluginsRegistryImpl() {
    }
    
    /**
     * force plugin objects instantiation
     */
    public void instantiateAll() {
        getAuthMechanisms();
        getAuthorizers();
        getTokenManager();
        getAuthenticators();
        
        getInitializers();
        getInterceptors();
        getServices();
        
        getTransformers();
        getCheckers();
        getHooks();
    }

    /**
     * @return the authMechanisms
     */
    @Override
    public Set<PluginRecord<AuthMechanism>> getAuthMechanisms() {
        if (this.authMechanisms == null) {
            this.authMechanisms = new LinkedHashSet<>();
            this.authMechanisms.addAll(PluginsFactory.authMechanisms());
        }

        return this.authMechanisms;
    }

    /**
     * @return the authenticators
     */
    @Override
    public Set<PluginRecord<Authenticator>> getAuthenticators() {
        if (this.authenticators == null) {
            this.authenticators = new LinkedHashSet<>();
            this.authenticators.addAll(PluginsFactory.authenticators());
        }

        return this.authenticators;
    }

    /**
     *
     * @param name the name of the authenticator
     * @return the authenticator
     * @throws org.restheart.ConfigurationException
     */
    @Override
    public PluginRecord<Authenticator> getAuthenticator(String name) throws
            ConfigurationException {

        var auth = getAuthenticators()
                .stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst();

        if (auth != null && auth.isPresent()) {
            return auth.get();
        } else {
            throw new ConfigurationException("Authenticator "
                    + name
                    + " not found");

        }
    }

    /**
     * @return the authenticators
     */
    @Override
    public PluginRecord<TokenManager> getTokenManager() {
        if (this.tokenManager == null) {
            this.tokenManager = PluginsFactory.tokenManager();
        }

        return this.tokenManager;
    }

    /**
     * @return the authenticators
     */
    @Override
    public Set<PluginRecord<Authorizer>> getAuthorizers() {
        if (this.authorizers == null) {
            this.authorizers = PluginsFactory.authorizers();
        }

        return this.authorizers;
    }

    /**
     * @return the initializers
     */
    @Override
    public Set<PluginRecord<Initializer>> getInitializers() {
        if (this.initializers == null) {
            this.initializers = new LinkedHashSet<>();
            this.initializers.addAll(PluginsFactory.initializers());
        }

        return this.initializers;
    }

    @Override
    public Set<PluginRecord<Interceptor>> getInterceptors() {
        if (this.interceptors == null) {
            this.interceptors = new LinkedHashSet<>();
            this.interceptors.addAll(PluginsFactory
                    .interceptors());
        }

        return this.interceptors;
    }

    /**
     * @return the services
     */
    @Override
    public Set<PluginRecord<Service>> getServices() {
        if (this.services == null) {
            this.services = new LinkedHashSet<>();
            this.services.addAll(PluginsFactory.services());
        }

        return this.services;
    }

    /**
     * global security predicates must all resolve to true to allow the request
     *
     * @return the globalSecurityPredicates allow to get and set the global
     * security predicates to apply to all requests
     */
    @Override
    public Set<Predicate> getGlobalSecurityPredicates() {
        return globalSecurityPredicates;
    }
    
    /**
     *
     * @return the globalCheckers
     */
    @Override
    public Set<PluginRecord<Checker>> getCheckers() {
        if (this.checkers == null) {
            this.checkers = new LinkedHashSet<>();
            this.checkers.addAll(PluginsFactory.checkers());
        }

        return this.checkers;
    }
    
    /**
     *
     * @return the globalCheckers
     */
    @Override
    public Set<PluginRecord<Transformer>> getTransformers() {
        if (this.transformers == null) {
            this.transformers = new LinkedHashSet<>();
            this.transformers.addAll(PluginsFactory.transformers());
        }

        return this.transformers;
    }
    
    /**
     *
     * @return the globalCheckers
     */
    @Override
    public Set<PluginRecord<Hook>> getHooks() {
        if (this.hooks == null) {
            this.hooks = new LinkedHashSet<>();
            this.hooks.addAll(PluginsFactory.hooks());
        }

        return this.hooks;
    }
    
    
    /**
     *
     * @return the globalCheckers
     */
    @Override
    public Set<GlobalChecker> getGlobalCheckers() {
        return globalCheckers;
    }

    /**
     * @return the globalTransformers
     */
    @Override
    public Set<GlobalTransformer> getGlobalTransformers() {
        return globalTransformers;
    }

    /**
     * @return the globalHooks
     */
    @Override
    public synchronized Set<GlobalHook> getGlobalHooks() {
        return globalHooks;
    }

    @Override
    public PathHandler getRootPathHandler() {
       return Bootstrapper.getRootPathHandler();
    }
}
