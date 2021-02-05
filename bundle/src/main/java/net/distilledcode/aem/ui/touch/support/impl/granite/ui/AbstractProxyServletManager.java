/*
 *  Copyright 2020 Code Distillery GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.distilledcode.aem.ui.touch.support.impl.granite.ui;

import com.adobe.granite.ui.components.ExpressionResolver;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractProxyServletManager<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProxyServletManager.class);

    private final Map<ServiceReference<T>, ServletRegistration> registrations = new ConcurrentHashMap<>();

    private final Set<ServiceReference<T>> earlyFactories = new CopyOnWriteArraySet<>();

    private volatile boolean active;

    private ExpressionResolver expressionResolver;

    private final ServletFactory<T> servletFactory;

    private final String resourceTypesProperty;

    private final String prefixProperty;

    public AbstractProxyServletManager(ServletFactory<T> servletFactory, String resourceTypesProperty, String prefixProperty) {
        this.servletFactory = servletFactory;
        this.resourceTypesProperty = resourceTypesProperty;
        this.prefixProperty = prefixProperty;
    }

    protected abstract void activate();

    protected final void activate(ExpressionResolver expressionResolver) {
        this.expressionResolver = expressionResolver;
        this.active = true;
        registerEarlyFactories();
    }

    protected void deactivate() {
        this.active = false;
        earlyFactories.clear();

        if (!registrations.isEmpty()) {
            LOG.warn("Remaining registrations on deactivate (will be unregistered): {}", registrations);
            registrations.values().forEach(ServletRegistration::unregister);
            registrations.clear();
        }
    }

    protected void bindFactory(@NotNull final ServiceReference<T> reference) {
        if (!isActive()) {
            earlyFactories.add(reference);
            return;
        }

        String[] resourceTypes = PropertiesUtil.toStringArray(reference.getProperty(resourceTypesProperty));
        String prefix = PropertiesUtil.toString(reference.getProperty(prefixProperty), null);
        if (resourceTypes.length == 0) {
            LOG.warn("Skipping DataSourceFactory without '{}' property: {}",
                    resourceTypes, reference);
            return;
        }

        ServletRegistration reg = new ServletRegistration(reference, resourceTypes, prefix);
        ServletRegistration oldRegistration = registrations.put(reference, reg);
        if (oldRegistration != null) {
            LOG.info("Unregistered previously registered servlet for {}", oldRegistration);
            oldRegistration.unregister();
        }
    }

    protected void unbindFactory(@NotNull final ServiceReference<T> reference) {
        // in case no servlet was registered yet
        earlyFactories.remove(reference);

        final ServletRegistration registration = registrations.remove(reference);
        if (registration != null) {
            LOG.info("Unregistered servlet for {}", reference);
            registration.unregister();
        }
    }

    private boolean isActive() {
        return active;
    }

    private void registerEarlyFactories() {
        final Set<ServiceReference<T>> copiesToBeRegistered = new HashSet<>(earlyFactories);
        earlyFactories.clear();

        for (ServiceReference<T> ref : copiesToBeRegistered) {
            if (isActive()) {
                bindFactory(ref);
            } else {
                break;
            }
        }
    }

    private class ServletRegistration {

        private final BundleContext bundleContext;

        private final ServiceReference<T> factoryReference;

        private final ServiceRegistration<Servlet> servletRegistration;

        ServletRegistration(@NotNull ServiceReference<T> factoryReference,
                            @NotNull String[] resourceTypes,
                            @Nullable String prefix) {
            this.factoryReference = factoryReference;
            this.bundleContext = factoryReference.getBundle().getBundleContext();

            T factory = bundleContext.getService(factoryReference);
            Servlet dataSourceServlet = servletFactory.createServlet(factory, expressionResolver);
            Hashtable<String, Object> servletProps = new Hashtable<>();
            servletProps.put("sling.servlet.resourceTypes", resourceTypes);
            servletProps.put("sling.servlet.prefix", prefix == null ? "0" : prefix);
            servletProps.put("service.description",
                    "Servlet registered by " + AbstractProxyServletManager.this.getClass().getSimpleName() +
                            " on behalf of " + factory.getClass().getName());
            this.servletRegistration = bundleContext.registerService(Servlet.class, dataSourceServlet, servletProps);
        }

        void unregister() {
            servletRegistration.unregister();
            bundleContext.ungetService(factoryReference);
        }
    }

    protected interface ServletFactory<FACTORY> {
        Servlet createServlet(FACTORY factory, ExpressionResolver expressionResolver);
    }
}
