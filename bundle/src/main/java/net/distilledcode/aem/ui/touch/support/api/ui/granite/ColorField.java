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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Model(
        resourceType = "distilledcode/aem-touch-ui-support/components/colorfield",
        adaptables = SlingHttpServletRequest.class,
        adapters = { Field.class, ColorField.class }
)
@ProviderType
public class ColorField extends GenericStringField {

    private final Type type;

    private final Supplier<List<ColorDefinition>> colorDefinitions;

    @Inject
    public ColorField(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) {
        super(request);
        type = Type.fromString(properties.get("type", "picker"));
        colorDefinitions = () -> Optional.of(CoralUtil.getItemDataSource(request, response, resource))
                .map(resources -> StreamSupport.stream(resources.spliterator(), false))
                .orElseGet(Stream::empty)
                .map(ColorDefinition::create)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = super.getAttributes();
        Predicate<String> isOff = val -> Objects.equals(val, "off");
        copyPropertyIf(attributes, "showdefaultcolors", "on", isOff);
        copyPropertyIf(attributes, "showproperties", "on", isOff);
        attributes.compute("class", CoralUtil.addClasses("distilledcode-colorfield"));
        return attributes;
    }

    private <T> void copyPropertyIf(Map<String, Object> attributes, String name, T defaultValue, Predicate<T> predicate) {
        T value = properties.get(name, defaultValue);
        if (predicate.test(value)) {
            attributes.put(name, value);
        }
    }

    public String getTagName() {
        return type.getTagName();
    }

    public List<ColorDefinition> getColorDefinitions() {
        return colorDefinitions.get();
    }

    @Override
    public String toString() {
        return "ColorField{" +
                "type=" + type +
                ", colorDefinitions=" + colorDefinitions +
                '}';
    }

    public static class ColorDefinition {

        private final String label;

        private final String colorValue;

        ColorDefinition(String label, String colorValue) {
            this.label = label;
            this.colorValue = colorValue;
        }

        public String getLabel() {
            return label;
        }

        public String getColorValue() {
            return colorValue;
        }

        public static ColorDefinition create(Resource resource) {
            ValueMap props = resource.getValueMap();
            String label = props.get("label", resource.getName());
            String color = props.get("colorValue", String.class);
            return color != null ? new ColorDefinition(label, color) : null;
        }

        @Override
        public String toString() {
            return "ColorDefinition{" +
                    "label='" + label + '\'' +
                    ", colorValue='" + colorValue + '\'' +
                    '}';
        }
    }

    private enum Type {

        PICKER("coral-colorinput", "color-colorinput-item"),

        DROPDOWN("coral-select", "color-select-item");

        private final String htmlTagName;

        private final String itemTagName;

        static Type fromString(String type) {
            return Stream.of(values())
                    .filter(v -> v.name().equalsIgnoreCase(type))
                    .findFirst()
                    .orElse(PICKER);
        }

        Type(String tagName, String itemTagName) {
            this.htmlTagName = tagName;
            this.itemTagName = itemTagName;
        }

        public String getTagName() {
            return htmlTagName;
        }

        public String getItemTagName() {
            return itemTagName;
        }

        @Override
        public String toString() {
            return "Type{" +
                    "name='" + name() + '\'' +
                    ", htmlTagName='" + htmlTagName + '\'' +
                    ", itemTagName='" + itemTagName + '\'' +
                    '}';
        }
    }
}
