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
package net.distilledcode.aem.ui.touch.support.impl.foundation.form.tagfield;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import net.distilledcode.aem.ui.touch.support.spi.foundation.form.tagfield.TagNamespaceProvider;
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.datasource.DataSourceFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings("unused")
@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=" + TagFieldDataSourceFactory.RT_CHILD_TAGS,
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=" + TagFieldDataSourceFactory.RT_TAG_SEARCH
        }
)
public class TagFieldDataSourceFactory extends DataSourceFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(TagFieldDataSourceFactory.class);

    public static final String RT_CHILD_TAGS = "distilledcode/ui/components/coral/foundation/form/tagfield/datasources/tags";

    public static final String RT_TAG_SEARCH = "distilledcode/ui/components/coral/foundation/form/tagfield/datasources/search";

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    private final List<TagNamespaceProvider> dynamicNamespaceProviders = new CopyOnWriteArrayList<>();
    
    private static final TagNamespaceProvider DEFAULT_TAG_NAMESPACE_PROVIDER = new DefaultTagNamespaceProvider();

    @Override
    @Nullable
    public Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        final boolean isChildTags = dsResource.isResourceType(RT_CHILD_TAGS);
        if (isChildTags || dsResource.isResourceType(RT_TAG_SEARCH)) {
            final ValueMap dsProperties = dsResource.getValueMap();

            final String path = ex.getString(dsProperties.get("path", String.class));
            if (StringUtils.isBlank(path)) {
                return null;
            }

            final String configPath = ex.getString(dsProperties.get("configPath", String.class));
            final ResourceResolver resolver = request.getResourceResolver();
            final ValueMap config = Optional.ofNullable(configPath)
                    .map(resolver::getResource)
                    .map(Resource::getValueMap)
                    .orElseGet(() -> ResourceUtil.getValueMap(null));

            Collection<String> namespaces = getAvailableNamespacesForPath(request, path, config);

            if (isChildTags) {
                return computeChildTagResources(request, dsProperties, ex, namespaces);
            } else {
                return computeTagSearchResources(request, dsProperties, ex, namespaces);
            }
        }
        
        throw new IllegalStateException("Called TagsDataSourceFactory for invalid resource type: " +
                dsResource.getResourceType());
    }

    private Iterable<Resource> computeChildTagResources(SlingHttpServletRequest request, ValueMap dsProperties, ExpressionHelper ex, Collection<String> namespaces) {

        final String tagId = ex.getString(dsProperties.get("tagId", String.class));
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        return Optional.ofNullable(request.getResourceResolver().adaptTo(TagManager.class))
                .map(tm -> tm.resolve(tagId))
                .map(tag -> computeChildTagResources(tag, namespaces))
                .orElse(null);
    }

    @Nullable
    private Iterable<Resource> computeChildTagResources(Tag tag, Collection<String> namespaces) {
        // TODO: take into account that /etc/tags moves to /content/cq:tags in AEM 6.5
        if (Objects.equals(tag.getPath(), "/etc/tags")) {
            return Optional.ofNullable(tag.adaptTo(Resource.class))
                    .<Iterable<Resource>>map(tagResource -> namespaces.stream()
                            .map(tagResource::getChild)
                            .filter(Objects::nonNull)
                            .filter(child -> child.adaptTo(Tag.class) != null)
                            .sorted(Comparator.comparing(Resource::getName))
                            ::iterator
                    )
                    .orElse(null);
        }

        Tag namespace = tag.isNamespace() ? tag : tag.getNamespace();
        if (namespaces.contains(namespace.getName())) {
            return Optional.<Iterable<Tag>>of(tag::listChildren)
                    .<Iterable<Resource>>map(childTags -> StreamSupport.stream(childTags.spliterator(), false)
                            .map(childTag -> childTag.adaptTo(Resource.class))
                            .filter(Objects::nonNull)
                            ::iterator
                    )
                    .orElse(null);
        }

        return null;
    }

    private Iterable<Resource> computeTagSearchResources(SlingHttpServletRequest request, ValueMap dsProperties, ExpressionHelper ex, Collection<String> namespaces) {
        final String titleQuery = ex.getString(dsProperties.get("query", String.class));

        if (StringUtils.isBlank(titleQuery)) {
            return null;
        }

        final int offset = ex.get(dsProperties.get("offset", "0"), int.class);
        final int limit = ex.get(dsProperties.get("limit", "20"), int.class);


        final String query = createQuery(request.getLocale(), titleQuery, namespaces);
        LOG.debug("Executing xpath query '{}'", query);
        Iterable<Resource> result = () -> request.getResourceResolver().findResources(query, "xpath");
        return () -> StreamSupport.stream(result.spliterator(), false)
                .skip(offset)
                .limit(limit)
                .iterator();
    }

    // Namespaces are returned sorted in natural order (via TreeSet)
    private Collection<String> getAvailableNamespacesForPath(@NotNull SlingHttpServletRequest request, String path, ValueMap config) {

        final boolean allowDynamicNamespaces = config.get("allowDynamicNamespaces", false);
        final Stream<TagNamespaceProvider> dynamicProviders =
                allowDynamicNamespaces ? dynamicNamespaceProviders.stream() : Stream.empty();

        final Set<@NotNull String> allNamespaces = Stream.concat(
                Stream.of(DEFAULT_TAG_NAMESPACE_PROVIDER),
                dynamicProviders)
                .map(provider -> {
                    final @NotNull String[] namespaces = provider.getNamespaces(request, path, config);
                    LOG.debug("'{}' provides the following namespaces for '{}': {}", provider, path, namespaces);
                    return namespaces;
                })
                .flatMap(Arrays::stream)
                .collect(Collectors.toCollection(TreeSet::new));
        LOG.debug("Effective namespaces for '{}': {}", path, allNamespaces);
        return allNamespaces;
    }

    private static String createQuery(Locale locale, String query, Collection<String> namespaces) {
        final String nsSegment = namespaces.stream().map(ISO9075::encode)
                .collect(Collectors.joining("|", "/(", ")"));
        final String localeCountry = ISO9075.encode(locale.toString().toLowerCase());
        final String localeLanguage = ISO9075.encode(new Locale(locale.getLanguage()).toString().toLowerCase());
        final String escapedQuery = Text.escapeIllegalXpathSearchChars(query).replaceAll("'", "''");

        final String conditions = Stream.of("", localeCountry, localeLanguage)
                .distinct()
                .map(suffix -> "jcr:title" + (suffix.isEmpty() ? suffix : "." + suffix))
                .map(propertyName -> String.format(
                        "jcr:like(fn:lower-case(@%1$s), '%%%2$s%%')", propertyName, escapedQuery))
                .collect(Collectors.joining(" or ", "(", ")"));

        return String.format("/jcr:root/etc/tags%1$s//element(*, cq:Tag)[not(@cq:movedTo) and %2$s] order by @jcr:path",
                nsSegment, conditions);
    }

    private static class DefaultTagNamespaceProvider implements TagNamespaceProvider {
        @NotNull
        @Override
        public String[] getNamespaces(@NotNull SlingHttpServletRequest request, @NotNull String path, @NotNull ValueMap config) {

            final String[] namespaces = config.get("namespaces", String[].class);
            if (namespaces == null && !config.get("allowDynamicNamespaces", false)) {
                return Optional.ofNullable(request.getResourceResolver().adaptTo(TagManager.class))
                        .map(TagManager::getNamespaces)
                        .map(Arrays::stream)
                        .orElseGet(Stream::empty)
                        .map(Tag::getName)
                        .toArray(String[]::new);
            } else {
                return namespaces == null ? new String[0] : namespaces;
            }
        }

        @Override
        public String toString() {
            return "DefaultTagNamespaceProvider";
        }
    }
}
