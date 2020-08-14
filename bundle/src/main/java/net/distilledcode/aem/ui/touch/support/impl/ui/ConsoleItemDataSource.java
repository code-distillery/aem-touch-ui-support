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

import com.adobe.granite.ui.components.ExpressionHelper;
import net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil;
import net.distilledcode.aem.ui.touch.support.api.ui.consoles.ConversionConfig;
import net.distilledcode.aem.ui.touch.support.spi.granite.datasource.DataSourceFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil.matchProperty;
import static org.apache.commons.lang3.StringUtils.startsWithAny;

@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=distilledcode/aem-touch-ui-support/ui-components/console/datasource"
        }
)
public class ConsoleItemDataSource extends DataSourceFactory {
    
    @Override
    public @Nullable Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        
        final ResourceResolver resolver = request.getResourceResolver();
        final String configPath = ex.getString(dsResource.getValueMap().get("configPath", String.class));

        final String[] searchRoots = Optional.ofNullable(configPath)
                .map(resolver::getResource)
                .map(ConversionConfig::getScope)
                .orElse(new String[0]);

        final Comparator<@NotNull Resource> displayOrder = Comparator
                .comparing(DialogUtil::isClassicComponentDialog).reversed()
                .thenComparing(Resource::getPath);
        return searchRoots.length == 0 ? null : getConvertibleResources(resolver, searchRoots).sorted(displayOrder)::iterator;
    }

    @NotNull
    public static Stream<Resource> getConvertibleResources(@NotNull ResourceResolver resolver, @NotNull String[] searchRoots) {

        final Stream<String> dialogs = findResources(resolver, searchRoots, DialogUtil::isClassicDialog)
                .filter(resource -> !Objects.equals(resource.getName(), "design_dialog"))
                .filter(resource -> !Objects.equals(resource.getName(), "cq:dialog"))
                .map(Resource::getPath);

        // find all existing resources referenced by cqinclude path
        final Stream<String> includes =
                findResources(resolver, searchRoots, matchProperty("xtype", "cqinclude"))
                .map(Resource::getValueMap)
                .map(map -> map.get("path", String.class))
                .filter(Objects::nonNull)
                .map(path -> StringUtils.removeEnd(path, ".infinity.json"))
                .filter(path -> startsWithAny(path, searchRoots))
                .distinct()
                .map(resolver::getResource)
                .filter(Objects::nonNull)
                //.filter(((Predicate<Resource>) DialogUtil::isWithinClassicDialog).negate())
                .filter(DialogUtil::hasXtypeInDescendants)
                .map(Resource::getPath);

        // join includes and dialogs
        final Set<String> paths = Stream.concat(includes, dialogs)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // filter out descendants of other elements that will be converted (descendants are converted implicitly)
        return paths.stream()
                .filter(path -> paths.stream().noneMatch(isAncestorOf(path)))
                .map(resolver::getResource)
                .filter(Objects::nonNull);
    }

    private static Predicate<? super String> isAncestorOf(String path) {
        return ancestor -> ancestor.length() < path.length()
                && path.startsWith(ancestor) && path.charAt(ancestor.length()) == '/';
    }

    private static Stream<Resource> findResources(@NotNull ResourceResolver resolver, @NotNull String[] searchRoots, @NotNull Predicate<Resource> filter) {
        final Stream.Builder<Resource> streamBuilder = Stream.builder();
        for (String searchRoot : searchRoots) {
            final Resource root = resolver.getResource(searchRoot);
            if (root != null) {
                DialogUtil
                        .findDescendants(root, filter)
                        .forEach(streamBuilder::add);
            }
        }
        return streamBuilder.build();
    }
}
