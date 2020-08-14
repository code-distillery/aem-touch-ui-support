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
package net.distilledcode.aem.ui.touch.support.impl.foundation.form;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.wcm.foundation.forms.FormsManager;
import com.day.cq.wcm.foundation.forms.FormsManager.ComponentDescription;
import net.distilledcode.aem.ui.touch.support.spi.granite.datasource.DataSourceFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings("unused")
@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=distilledcode/cq/wcm/foundation/form/actions"
        }
)
public class FormActionsDataSourceFactory extends DataSourceFactory {

    @Override
    @Nullable
    public Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        final ResourceResolver resolver = request.getResourceResolver();
        final FormsManager formsManager = resolver.adaptTo(FormsManager.class);
        return () -> Optional.ofNullable(formsManager)
                .map(FormsManager::getActions)
                .map(actions -> (Iterable<ComponentDescription>) () -> actions)
                .map(Iterable::spliterator)
                .map(actions -> StreamSupport.stream(actions, false))
                .orElseGet(Stream::empty)
                .map(toResource(resolver))
                .iterator();
    }

    @NotNull
    private static Function<ComponentDescription, Resource> toResource(ResourceResolver resolver) {
        return desc -> {
            final ValueMap properties = new ValueMapDecorator(new HashMap<>());
            properties.put("text", desc.getTitle());
            properties.put("value", desc.getResourceType());
            return new ValueMapResource(resolver, "", null, properties);
        };
    }
}
