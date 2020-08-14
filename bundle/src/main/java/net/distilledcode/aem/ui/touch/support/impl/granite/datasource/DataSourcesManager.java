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
package net.distilledcode.aem.ui.touch.support.impl.granite.datasource;

import com.adobe.granite.ui.components.ExpressionResolver;
import net.distilledcode.aem.ui.touch.support.spi.granite.datasource.DataSourceFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class DataSourcesManager {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourcesManager.class);

    private final Map<ServiceReference<DataSourceFactory>, ServletRegistration> registrations = new ConcurrentHashMap<>();

    private final Set<ServiceReference<DataSourceFactory>> earlyDataSourceFactories = new CopyOnWriteArraySet<>();

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ExpressionResolver expressionResolver;

    private volatile boolean active;

    @Activate
    private void activate() {
        this.active = true;
        registerEarlyDataSourceFactories();
    }

    @Deactivate
    private void deactivate() {
        this.active = false;
        earlyDataSourceFactories.clear();

        if (!registrations.isEmpty()) {
            LOG.error("Remaining registrations on deactivate: {}", registrations);
        }
    }

    @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE, policy = ReferencePolicy.DYNAMIC)
    private void bindDataSourceFactory(@NotNull final ServiceReference<DataSourceFactory> reference) {
        if (!isActive()) {
            earlyDataSourceFactories.add(reference);
            return;
        }

        String[] resourceTypes = PropertiesUtil.toStringArray(reference.getProperty(DataSourceFactory.DATASOURCE_RESOURCE_TYPES));
        String prefix = PropertiesUtil.toString(reference.getProperty(DataSourceFactory.DATASOURCE_PREFIX), null);
        if (resourceTypes.length == 0) {
            LOG.warn("Skipping DataSourceFactory without '{}' property: {}",
                    DataSourceFactory.DATASOURCE_RESOURCE_TYPES, reference);
            return;
        }

        ServletRegistration reg = new ServletRegistration(reference, resourceTypes, prefix, expressionResolver);
        ServletRegistration oldRegistration = registrations.put(reference, reg);
        if (oldRegistration != null) {
            LOG.info("Unregistered previously registered servlet for {}", oldRegistration);
            oldRegistration.unregister();
        }
    }

    private void unbindDataSourceFactory(@NotNull final ServiceReference<DataSourceFactory> reference) {
        // in case no servlet was registered yet
        earlyDataSourceFactories.remove(reference);

        final ServletRegistration registration = registrations.remove(reference);
        if (registration != null) {
            LOG.info("Unregistered servlet for {}", reference);
            registration.unregister();
        }
    }

    private boolean isActive() {
        return active;
    }

    private void registerEarlyDataSourceFactories() {
        final Set<ServiceReference<DataSourceFactory>> copiesToBeRegistered = new HashSet<>(earlyDataSourceFactories);
        earlyDataSourceFactories.clear();

        for (ServiceReference<DataSourceFactory> ref : copiesToBeRegistered) {
            if (isActive()) {
                bindDataSourceFactory(ref);
            } else {
                break;
            }
        }
    }

    private static class ServletRegistration {

        private final BundleContext bundleContext;

        private final ServiceReference<DataSourceFactory> factoryReference;

        private final ServiceRegistration<Servlet> servletRegistration;

        ServletRegistration(@NotNull ServiceReference<DataSourceFactory> factoryReference,
                            @NotNull String[] resourceTypes,
                            @Nullable String prefix,
                            @NotNull ExpressionResolver expressionResolver) {
            this.factoryReference = factoryReference;
            this.bundleContext = factoryReference.getBundle().getBundleContext();

            DataSourceFactory factory = bundleContext.getService(factoryReference);
            DataSourceServlet dataSourceServlet = new DataSourceServlet(factory, expressionResolver);
            Hashtable<String, Object> servletProps = new Hashtable<>();
            servletProps.put("sling.servlet.resourceTypes", resourceTypes);
            servletProps.put("sling.servlet.prefix", prefix == null ? "0" : prefix);
            servletProps.put("service.description", "Servlet registered by DataSourcesManager on behalf of " + factory.getClass().getName());
            this.servletRegistration = bundleContext.registerService(Servlet.class, dataSourceServlet, servletProps);
        }

        void unregister() {
            servletRegistration.unregister();
            bundleContext.ungetService(factoryReference);
        }
    }
}
