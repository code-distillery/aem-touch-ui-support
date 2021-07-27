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

import net.distilledcode.aem.ui.touch.support.impl.image.ImageReaderHelper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.awt.Dimension;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "dam:Asset",
        selectors = { "assetreference", "info" },
        extensions = "json"
)
public class AssetReferenceInfoServlet extends SlingSafeMethodsServlet {
    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws IOException {
        final ResourceResolver resolver = request.getResourceResolver();
        final Resource assetResource = request.getResource();
        final Resource renditions = assetResource.getChild("jcr:content/renditions");
        if (renditions == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No renditions found for asset");
            return;
        }
        final Resource original = renditions.getChild("original");
        if (original == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No original rendition found for asset");
            return;
        }

        final String mimeType = original.getValueMap().get("jcr:content/jcr:mimeType", "");
        final JsonObjectBuilder json = Json.createObjectBuilder()
                .add("name", assetResource.getName())
                .add("url", getUrl(resolver::map, assetResource))
                .add("thumbnailUrl", getThummbnailUrl(resolver::map, assetResource))
                .add("mimeType", mimeType);

        getDimensions(original).ifPresent(dimension -> {
            json.add("width", dimension.width)
                .add("height", dimension.height);
        });

        final JsonArrayBuilder renditionsJson = Json.createArrayBuilder();
        boolean hasSeenWebRendition = false;
        for (Resource rendition : renditions.getChildren()) {
            final String name = rendition.getName();
            if (Objects.equals(name, "original")) {
                continue;
            }

            final boolean isClassicUiCropReference = name.startsWith("cq5dam.web.") && !hasSeenWebRendition;
            if (isClassicUiCropReference) {
                hasSeenWebRendition = true;
            }

            getDimensions(rendition).ifPresent(dimensions -> {
                final JsonObjectBuilder renditionJson = Json.createObjectBuilder()
                        .add("name", name)
                        .add("url", getUrl(resolver::map, rendition))
                        .add("width", dimensions.width)
                        .add("height", dimensions.height);
                if (isClassicUiCropReference) {
                    renditionJson.add("isClassicUiCropReference", true);
                }
                renditionsJson.add(renditionJson);
            });
        }

        final JsonObject jsonObject = json
                .add("renditions", renditionsJson)
                .build();

        final JsonWriter writer = Json
                .createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true))
                .createWriter(response.getWriter());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        writer.writeObject(jsonObject);
    }

    private String getUrl(Function<String, String> mapper, Resource rendition) {
        return mapper.apply(rendition.getPath()) + "?ch_ck=" + getLastModified(rendition);
    }

    private String getThummbnailUrl(Function<String, String> mapper, Resource rendition) {
        return mapper.apply(rendition.getPath()) + ".thumb.319.319.png?ch_ck=" + getLastModified(rendition);
    }

    private static String getExtension(String filename) {
        final int lastDot = filename.lastIndexOf('.');
        return lastDot != -1 ? filename.substring(lastDot + 1) : null;
    }

    private static Optional<Dimension> getDimensions(Resource rendition) throws IOException {
        return ImageReaderHelper
                .from(() -> ImageReaderHelper.getRenditionInputStream(rendition))
                .withImageReader(ImageReaderHelper::getImageDimensions);
    }

    private static long getLastModified(@NotNull Resource rendition) {
        final ValueMap properties = rendition.getValueMap();
        final ZonedDateTime lastModified = Optional
                .ofNullable(properties.get("jcr:content/jcr:lastModified", Calendar.class))
                .map(created -> created.toInstant().atZone(created.getTimeZone().toZoneId()))
                .orElse(ZonedDateTime.now());
        return lastModified.toInstant().toEpochMilli();
    }
}
