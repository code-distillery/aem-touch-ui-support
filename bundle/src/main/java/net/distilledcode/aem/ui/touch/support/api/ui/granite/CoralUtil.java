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
package net.distilledcode.aem.ui.touch.support.api.ui.granite;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.day.cq.wcm.api.WCMMode;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CoralUtil {

    @NotNull
    public static Iterable<Resource> getItemDataSource(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response, @NotNull Resource resource) {
        return Optional.ofNullable(resource.getChild("items"))
                .map(Resource::getChildren)
                .orElseGet(() -> getDataSource(request, response, resource));
    }

    @NotNull
    private static Iterable<Resource> getDataSource(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response, @NotNull Resource resource) {
        final Resource datasource = resource.getChild("datasource");
        if (datasource != null) {
            try {
                final RequestDispatcher requestDispatcher = request.getRequestDispatcher(datasource);
                if (requestDispatcher != null) {
                    final WCMMode originalWcmMode = WCMMode.DISABLED.toRequest(request);
                    try {
                        requestDispatcher.include(request, response);
                    } finally {
                        originalWcmMode.toRequest(request);
                    }
                    final DataSource ds = (DataSource)request.getAttribute(DataSource.class.getName());
                    if (ds != null) {
                        request.removeAttribute(DataSource.class.getName());
                        return () -> {
                            final Iterator<Resource> iterator = ds.iterator();
                            return iterator != null ? iterator : Stream.<Resource>empty().iterator();
                        };
                    }
                }
            } catch (ServletException|IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        return Stream.<Resource>empty()::iterator;
    }

    @Nullable
    public static ExpressionHelper getExpressionHelper(@NotNull SlingHttpServletRequest request) {
        SlingBindings slingBindings = (SlingBindings) request.getAttribute(SlingBindings.class.getName());
        if (slingBindings != null) {
            SlingScriptHelper sling = slingBindings.getSling();
            if (sling != null) {
                ExpressionResolver expressionResolver = sling.getService(ExpressionResolver.class);
                if (expressionResolver != null) {
                    return new ExpressionHelper(expressionResolver, request);
                }
            }
        }
        return null;
    }

    /**
     * Used to add css class names to a 'class' attribute in a Map.
     *
     * <pre>
     *     Map&lt;String, String&gt; attributes = ...;
     *     attributes.compute("class", addClasses("extra-class", "super-extra-class"));
     * </pre>
     *
     *
     * @param cssClasses A list of one or more css class names.
     * @return A BiFunction that can be passed as Map#compute's second argument.
     */
    public static BiFunction<String, Object, String> addClasses(String... cssClasses) {
        return (key, value) -> {
            if (!Objects.equals("class", key)) {
                throw new IllegalArgumentException("\"addClasses\" only handles the \"class\" key");
            }
            final Stream<String> oldClasses = Optional.ofNullable(value)
                    .filter(v -> v instanceof String)
                    .map(Object::toString)
                    .map(StringUtils::split)
                    .map(Stream::of)
                    .orElseGet(Stream::empty);

            final Stream<String> newClasses = Stream.of(cssClasses)
                    .filter(StringUtils::isNotBlank)
                    .map(StringUtils::strip);

            return Stream.concat(oldClasses, newClasses)
                    .distinct()
                    .collect(Collectors.joining(" "));
        };
    }

    public static BiFunction<String, Object, Object> putNullSafe(@Nullable Object value) {
        return (key, oldValue) -> value;
    }

    public static Map<String, Object> getGraniteCommonAttributes(@NotNull Resource resource, @Nullable ExpressionHelper expressionHelper) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        ValueMap properties = resource.getValueMap();
        safeAddToMap(attributes, "id", properties.get("granite:id", String.class));
        safeAddToMap(attributes, "rel", properties.get("granite:rel", String.class));
        safeAddToMap(attributes, "class", properties.get("granite:class", String.class));
        safeAddToMap(attributes, "title", properties.get("granite:title", String.class));
        safeAddToMap(attributes, "hidden", properties.get("granite:hidden", false));
        safeAddToMap(attributes, "itemscope", properties.get("granite:itemscope", String.class));
        safeAddToMap(attributes, "itemtype", properties.get("granite:itemtype", String.class));
        safeAddToMap(attributes, "itemprop", properties.get("granite:itemitemprop", String.class));
        Resource graniteData = resource.getChild("granite:data");
        if (graniteData != null) {
            addGraniteDataAttributes(attributes, graniteData.getValueMap(), expressionHelper);
        }
        return attributes;
    }

    private static void addGraniteDataAttributes(@NotNull Map<String, Object> attributes, @NotNull ValueMap dataProperties, @Nullable ExpressionHelper expressionHelper) {
        dataProperties.keySet().stream()
                .filter(key -> !key.contains(":"))
                .forEach(key -> {
                    String expression = dataProperties.get(key, String.class);
                    String value = expressionHelper == null ? expression : expressionHelper.getString(expression);
                    if (value != null) {
                        attributes.put("data-" + key, value);
                    }
                });
    }

    private static void safeAddToMap(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    private CoralUtil() {
        // static utility - no instances
    }
}
