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
import net.distilledcode.aem.ui.touch.support.impl.requestprocessing.SyntheticHttpServletRequest;
import net.distilledcode.aem.ui.touch.support.impl.requestprocessing.SyntheticHttpServletResponse;
import net.distilledcode.aem.ui.touch.support.spi.granite.datasource.DataSourceFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.engine.SlingRequestProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
@Component(
        service = DataSourceFactory.class,
        property = {
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES + "=distilledcode/cq/wcm/foundation/json"
        }
)
public class JsonOptionsDataSourceFactory extends DataSourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JsonOptionsDataSourceFactory.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRequestProcessor requestProcessor;


    @Override
    @Nullable
    public Iterable<Resource> computeResources(@NotNull SlingHttpServletRequest request, @NotNull Resource dsResource, @NotNull ExpressionHelper ex) {

        final ValueMap datasourceProps = dsResource.getValueMap();
        final String selectedValue = datasourceProps.get("selected", String.class);
        final String url = datasourceProps.get("url", String.class);
        if (url == null) {
            return null;
        }

        final String interpolatedUrl = ex.getString(url);

        final ResourceResolver resolver = request.getResourceResolver();
        final HttpServletRequest syntheticRequest = SyntheticHttpServletRequest.builder(interpolatedUrl).build();
        final Object requestProgressTracker = request.getAttribute(RequestProgressTracker.class.getName());
        if (requestProgressTracker != null) {
            syntheticRequest.setAttribute(RequestProgressTracker.class.getName(), requestProgressTracker);
        }
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SyntheticHttpServletResponse syntheticResponse = new SyntheticHttpServletResponse(outputStream);
        try {
            requestProcessor.processRequest(syntheticRequest, syntheticResponse, resolver);
            syntheticResponse.flushBuffer();

            final List<Resource> resources = new ArrayList<>();
            final int statusCode = syntheticResponse.getStatusCode();
            if (200 <= statusCode && statusCode < 300) {
                final String outputJson = outputStream.toString("UTF-8");
                try (final JsonReader reader = Json.createReader(new StringReader(outputJson))) {
                    try {
                        final JsonArray options = reader.readArray();
                        for (int i = 0; i < options.size(); i++) {
                            final JsonObject option = options.getJsonObject(i);
                            final ValueMapDecorator props = new ValueMapDecorator(new HashMap<>());
                            props.put("text", option.getString("text"));
                            final String value = option.getString("value");
                            props.put("value", value);
                            if (Objects.equals(value, selectedValue)) {
                                props.put("selected", true);
                            }
                            resources.add(new ValueMapResource(resolver, "", null, props));
                        }
                    } catch (JsonParsingException e) {
                        LOG.warn("Failed to parse json '{}'", outputJson.substring(0, Math.min(outputJson.length(), 256)), e);
                    }
                }
            } else {
                LOG.warn("Retrieving '{}' returned response with non 2xx status code: '{}'", interpolatedUrl, statusCode);
            }
            return resources;
        } catch (ServletException | IOException e) {
            LOG.warn("Failed to retrieve or process JSON from {}", interpolatedUrl,  e);
        }

        return null;
    }
}
