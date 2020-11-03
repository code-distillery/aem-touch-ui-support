/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package net.distilledcode.aem.ui.touch.support.impl.fixes;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceDecorator;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;

/**
 * This {@code ResourceDecorator} makes sure that the {@code cq:showOnCreate} property
 * is respected when it is defined on an {@link #INCLUDE_RESOURCE_TYPES include resource type}.
 */
@Component(
        service = {
                Filter.class,
                ResourceDecorator.class
        },
        property = {
                "sling.filter.scope=REQUEST",
                "sling.filter.resourceTypes=" + IncludeRespectingShowOnCreate.FILTER_RESOURCE_TYPE
        }
)
public class IncludeRespectingShowOnCreate implements Filter, ResourceDecorator {

    public static final String FILTER_RESOURCE_TYPE = "cq/gui/components/siteadmin/admin/createpagewizard/properties";

    public static final Collection<String> INCLUDE_RESOURCE_TYPES = asList(
            "granite/ui/components/coral/foundation/include",
            "granite/ui/components/foundation/include"
    );

    // use a List instead of a Set to represent included paths,
    // because the same path may be included multiple times
    private final ThreadLocal<List<String>> includedPathsHolder = new ThreadLocal<>();

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to do
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            if (request instanceof SlingHttpServletRequest) {
                final SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
                final String resourceType = slingRequest.getResource().getResourceType();

                // make sure the filter also works without support for "sling.filter.resourceTypes"
                if (Objects.equals(FILTER_RESOURCE_TYPE, resourceType)) {
                    // setting the ThreadLocal enables decoration for this request
                    includedPathsHolder.set(new ArrayList<>());
                }
            }
            chain.doFilter(request, response);
        } finally {
            includedPathsHolder.remove();
        }
    }

    @Override
    public void destroy() {
        // nothing to do
    }

    @Override
    @Nullable
    public Resource decorate(@NotNull Resource resource) {
        final List<String> includedPaths = includedPathsHolder.get();
        if (includedPaths == null) {
            return resource;
        }

        // store included paths as they are found
        if (INCLUDE_RESOURCE_TYPES.contains(resource.getResourceType())) {
            final boolean showOnCreate = resource.getValueMap().get("cq:showOnCreate", true);
            if (!showOnCreate) {
                String includePath = resource.getValueMap().get("path", String.class);
                includedPaths.add(includePath);
            }
        }

        // decorate resource if it is contained in the included paths
        if (includedPaths.remove(resource.getPath())) {
            return new ResourceWrapper(resource) {
                @Override
                public @NotNull ValueMap getValueMap() {
                    return new MergingValueMap(asList(
                            new ValueMapDecorator(Collections.singletonMap("cq:showOnCreate", false)),
                            super.getValueMap()
                    ));
                }
            };
        }
        return resource;
    }

    @Override
    @Nullable
    public Resource decorate(@NotNull Resource resource, @NotNull HttpServletRequest request) {
        return decorate(resource);
    }
}

