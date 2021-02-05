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
package net.distilledcode.aem.ui.touch.support.impl.foundation;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.i18n.I18n;
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.datasource.DataSourceFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@SuppressWarnings("unused")
@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=distilledcode/cq/wcm/foundation/languages"
        }
)
public class LanguageOptionsDataSourceFactory extends DataSourceFactory {

    private static final String LANGUAGE_PATH = "/mnt/overlay/wcm/core/resources/languages";


    @Override
    public @Nullable Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        final ResourceResolver resolver = request.getResourceResolver();
        final Resource languageRoot = resolver.getResource(LANGUAGE_PATH);
        if (languageRoot == null) {
            return null;
        }

        final I18n i18n = new I18n(request);
        final Collator collator = Collator.getInstance(request.getLocale());
        collator.setStrength(Collator.PRIMARY);
        return () -> StreamSupport.stream(languageRoot.getChildren().spliterator(), false)
                        .map(toOptionResource(resolver, i18n))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(res -> res.getValueMap().get("text"), collator))
                        .iterator();
    }

    private static Function<Resource, Resource> toOptionResource(ResourceResolver resolver, I18n i18n) {
        return resource -> {
            final ValueMap properties = resource.getValueMap();
            final ValueMapDecorator props = new ValueMapDecorator(new HashMap<>());

            final String language = properties.get("language", String.class);
            if (language == null) {
                return null;
            }

            final String country = properties.get("country", "*");
            final String label = language + (Objects.equals(country, "*") ? "" : " (" + country + ")");
            props.put("text", i18n.get(label));
            props.put("value", resource.getName());

            return new ValueMapResource(resolver, "", null, props);
        };
    }
}
