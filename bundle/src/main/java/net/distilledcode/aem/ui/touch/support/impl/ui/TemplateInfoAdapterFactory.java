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
package net.distilledcode.aem.ui.touch.support.impl.ui;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.ResourceResolver",
                AdapterFactory.ADAPTER_CLASSES + "=net.distilledcode.aem.ui.touch.support.impl.ui.Templates",
                AdapterFactory.ADAPTER_CLASSES + "=net.distilledcode.aem.ui.touch.support.impl.ui.TemplateInfoAdapterFactory"
        }
)
public class TemplateInfoAdapterFactory implements AdapterFactory {

    private final Map<ResourceResolver, Map<String, Set<String>>> cache = new WeakHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <AdapterType> AdapterType getAdapter(@NotNull Object adaptable, @NotNull Class<AdapterType> type) {
        if (adaptable instanceof Resource && type == Templates.class) {
            final Resource resource = (Resource) adaptable;
            return (AdapterType) new Templates(getTemplates(resource));
        }
        return null;
    }

    @NotNull
    private Collection<? extends String> getTemplates(@NotNull Resource resource) {
        final ResourceResolver resolver = resource.getResourceResolver();
        final Map<String, Set<String>> resourceTypeToTemplates = cache.computeIfAbsent(resolver, TemplateInfoAdapterFactory::createTemplateMap);

        final String path = resource.getPath();
        final String resourceType = toResourceType(resolver.getSearchPath(), path);
        return resourceTypeToTemplates.getOrDefault(resourceType, Collections.emptySet());
    }

    private static Map<String, Set<String>> createTemplateMap(ResourceResolver resolver) {

        final Iterable<Resource> templateResources = () -> resolver.findResources(
                "/jcr:root//element(*, cq:Template)/jcr:content", "xpath"
        );

        final Map<String, Set<String>> templateMap = new HashMap<>();
        for (Resource template : templateResources) {
            final ValueMap properties = template.getValueMap();
            final String resourceType = toResourceType(
                    resolver.getSearchPath(), properties.get("sling:resourceType", String.class));
            final String path = ResourceUtil.getParent(template.getPath());
            if (resourceType != null) {
                final Set<String> templatePaths = templateMap.computeIfAbsent(resourceType, key -> new HashSet<>());
                templatePaths.add(path);
            }
        }

        return makeImmutableCopy(templateMap);
    }

    private static Map<String, Set<String>> makeImmutableCopy(Map<String, Set<String>> templateMap) {
        final Map<String, Set<String>> copy = new HashMap<>();
        for (String key : templateMap.keySet()) {
            copy.put(key, unmodifiableSet(new HashSet<>(templateMap.get(key))));
        }
        return unmodifiableMap(copy);
    }

    @Nullable
    private static String toResourceType(@NotNull String[] searchPath, @Nullable String path) {
        if (path == null) {
            return null;
        }
        String resourceType = path;
        for (String prefix : searchPath) {
            if (path.startsWith(prefix)) {
                resourceType = path.substring(prefix.length());
                break;
            }
        }
        return resourceType;
    }

}
