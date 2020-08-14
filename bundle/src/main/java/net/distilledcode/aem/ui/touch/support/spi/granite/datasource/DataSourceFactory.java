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
package net.distilledcode.aem.ui.touch.support.spi.granite.datasource;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ds.AbstractDataSource;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.EmptyDataSource;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

import java.util.Iterator;
import java.util.Optional;

/**
 * {@code DataSourceFactory} provide a convenient way to create custom {@link DataSource}s. A
 * {@code DataSourceFactory} must be registered with a property {@code datasource.resourceType}.
 * {@code DataSource}s may then be used by creating a "datasource" child node in UI elements,
 * referencing the resourceType using the sling:resourceType property.
 * <br>
 * The {@code DataSourceFactory} is provided with the current {@code SlingHttpServletRequest},
 * the "datasource" child of the current resource and an {@link ExpressionHelper} instance that
 * allow expressions in property values to be evaluated.
 * <br>
 * Often it is easiest to
 * <br>
 * Furthermore, details of Granite {@code DataSource}s like registering a {@code Servlet} or setting
 * a request attribute are abstracted away and need not be taken care of by implementations of
 * {@code DataSourceFactory}.
 */
@ConsumerType
public abstract class DataSourceFactory {

    /**
     * Name of the service property indicating the datasource's resource type.
     */
    public static final String DATASOURCE_RESOURCE_TYPES = "datasource.resourceTypes";

    /**
     * Advanced: name of the service property indicating the datasource's resource type prefix.
     * This defaults to "0" and does not normally need to be set. It works like <a href="https://sling.apache.org/documentation/the-sling-engine/servlets.html#servlet-registration">'sling.servlet.prefix'</a>.
     */
    public static final String DATASOURCE_PREFIX = "datasource.prefix";

    /**
     * Method that creates a new {@code DataSource} for the current request. If {@code null}
     * is returned, an empty {@code DataSource} is created by the implementation.
     *
     * @param request The current request.
     * @param dsResource The "datasource" child of the current resource.
     * @param ex An ExpressionHelper for resolving expressions read from property values.
     *
     * @return an {@code Iterable<Resource} to be presented as a {@code DataSource}
     */
    @Nullable
    public abstract Iterable<Resource> computeResources(@NotNull final SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex);

    /**
     * Method that creates a new {@code DataSource} for the current request. The default implementation
     * of this calls {@link #computeResources(SlingHttpServletRequest, Resource, ExpressionHelper)}
     * and creates a {@code DataSource} from it.
     * <br>
     * Only implementations that want to return their own implementation of a {@code DataSource} need
     * to override this method.
     *
     * @param request The current request.
     * @param dsResource The "datasource" child of the current resource.
     * @param ex An ExpressionHelper for resolving expressions read from property values.
     *
     * @return a {@code DataSource}
     */
    @NotNull
    public DataSource createDataSource(@NotNull final SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        return Optional.ofNullable(computeResources(request, dsResource, ex))
                .<DataSource>map(IterableDataSource::new)
                .orElse(EmptyDataSource.instance());
    }

    private static class IterableDataSource extends AbstractDataSource {

        private final Iterable<Resource> resources;

        IterableDataSource(Iterable<Resource> resources) {
            this.resources = resources;
        }

        @Override
        public Iterator<Resource> iterator() {
            return resources.iterator();
        }
    }
}
