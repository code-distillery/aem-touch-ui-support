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

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Model(
        resourceType = "distilledcode/aem-touch-ui-support/components/assetreferencefield",
        adaptables = SlingHttpServletRequest.class,
        adapters = { Field.class, AssetReferenceField.class }
)
public class AssetReferenceField extends GenericStringField {

    @Inject
    public AssetReferenceField(@NotNull SlingHttpServletRequest request) {
        super(request);
    }

    @Override
    public Map<String, Object> getAttributes() {
        final Map<String, Object> attributes = super.getAttributes();
        attributes.put("pickersrc", "/apps/distilledcode/aem-touch-ui-support/content/pathfield/assets.html{+value}?_charset_=utf-8&root=%2fcontent%2fdam&filter=hierarchyNotFile&selectionCount=single");

        setStoredTransformationValue("crop", attributes);
        setStoredTransformationValue("rotate", attributes);
        setStoredTransformationValue("map", attributes);
        setEditorConfiguration(attributes);
        return attributes;
    }

    private void setEditorConfiguration(Map<String, Object> attributes) {
        if (this.properties.get("enableImageEditor", false)) {
            final Resource cropConfig = this.resource.getChild("cropConfig");
            final JsonObjectBuilder config = Json.createObjectBuilder();
            if (cropConfig != null) {
                config.add("crop", toJson(cropConfig));
            }
            attributes.put("data-image-editor-config", config.build().toString());
        }
    }

    private JsonObjectBuilder toJson(Resource resource) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        final ValueMap properties = resource.getValueMap();
        for (String name : properties.keySet()) {
            if (name.startsWith("jcr:") || name.startsWith("nt:")) {
                continue;
            }
            final Object rawValue = properties.get(name);
            final Class<?> clazz = rawValue.getClass();
            if (clazz.isArray()) {
                final Stream<Object> rawValues = Arrays.stream((Object[]) rawValue);
                final Class<?> type = clazz.getComponentType();
                final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                if (type == int.class || type == Integer.class) {
                    rawValues.map(Integer.class::cast).forEach(arrayBuilder::add);
                } else if (type == long.class || type == Long.class) {
                    rawValues.map(Long.class::cast).forEach(arrayBuilder::add);
                } else if (type == double.class || type == Double.class) {
                    rawValues.map(Double.class::cast).forEach(arrayBuilder::add);
                } else if (type == String.class) {
                    rawValues.map(String.class::cast).forEach(arrayBuilder::add);
                } else if (type == boolean.class || type == Boolean.class) {
                    rawValues.map(Boolean.class::cast).forEach(arrayBuilder::add);
                } else if (type == BigDecimal.class) {
                    rawValues.map(BigDecimal.class::cast).forEach(arrayBuilder::add);
                } else if (type == BigInteger.class) {
                    rawValues.map(BigInteger.class::cast).forEach(arrayBuilder::add);
                } else if (type == Calendar.class) {
                    rawValues.map(Calendar.class::cast).map(ISO8601::format).forEach(arrayBuilder::add);
                } else {
                    throw new UnsupportedOperationException("Property " + name + " is of unsupported type " + clazz.getName());
                }
                builder.add(name, arrayBuilder);

            } else {
                if (clazz == int.class || clazz == Integer.class) {
                    builder.add(name, (int) rawValue);
                } else if (clazz == long.class || clazz == Long.class) {
                    builder.add(name, (long) rawValue);
                } else if (clazz == double.class || clazz == Double.class) {
                    builder.add(name, (double) rawValue);
                } else if (clazz == String.class) {
                    builder.add(name, (String) rawValue);
                } else if (clazz == boolean.class || clazz == Boolean.class) {
                    builder.add(name, (boolean) rawValue);
                } else if (clazz == BigDecimal.class) {
                    builder.add(name, (BigDecimal) rawValue);
                } else if (clazz == BigInteger.class) {
                    builder.add(name, (BigInteger) rawValue);
                } else if (clazz == Calendar.class) {
                    builder.add(name, ISO8601.format((Calendar) rawValue));
                } else {
                    throw new UnsupportedOperationException("Property " + name + " is of unsupported type " + clazz.getName());
                }
            }
        }

        StreamSupport.stream(resource.getChildren().spliterator(), false)
                .forEach(child -> builder.add(child.getName(), toJson(child)));
        return builder;
    }

    private void setStoredTransformationValue(String type, Map<String, Object> attributes) {
        final String capitalized = StringUtils.capitalize(type);
        getStoredValueByName(getPrefixedName("image" + capitalized), "")
                .ifPresent(val -> attributes.put("data-transformation-" + type, val));

    }

    private String getPrefixedName(String name) {
        final int pos = getName().lastIndexOf("/");
        if (pos == -1) {
            return name;
        } else {
            return getName().substring(0, pos + 1) + name;
        }
    }
}