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
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.datasource.DataSourceFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

@SuppressWarnings("unused")
@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=distilledcode/cq/wcm/foundation/children"
        }
)
public class ChildrenDataSourceFactory extends DataSourceFactory {

    @Override
    @Nullable
    public Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {
        final ValueMap datasourceProps = dsResource.getValueMap();
        final String path = ex.getString(datasourceProps.get("path", String.class));
        if (path == null) {
            return null;
        }

        final ResourceResolver resolver = request.getResourceResolver();
        final Resource resource = resolver.getResource(path);
        if (resource == null) {
            return null;
        }
        return resource.getChildren();
    }
}
