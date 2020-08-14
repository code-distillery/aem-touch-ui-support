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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.distilledcode.aem.ui.touch.support.api.ui.granite.CoralUtil.addClasses;


@Model(
        resourceType = "distilledcode/aem-touch-ui-support/components/checkboxgroup",
        adaptables = SlingHttpServletRequest.class,
        adapters = { Field.class, CheckboxGroup.class }
)
public class CheckboxGroup extends Field<String[]> {

    private final SlingHttpServletRequest request;

    private final SlingHttpServletResponse response;

    private final List<Checkbox> checkboxes;

    @Inject
    public CheckboxGroup(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        super(request);
        this.request = request;
        this.response = response;
        this.checkboxes = getCheckboxStream().collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getAttributes() {
        final Map<String, Object> attributes = super.getAttributes();
        attributes.compute("class", addClasses("distilledcode-checkbox-group"));
        attributes.remove("name");
        final String requiredMsg = properties.get("requiredMsg", String.class);
        if (requiredMsg != null) {
            attributes.put("data-required-msg", requiredMsg);
        }
        return attributes;
    }

    @Override
    @NotNull
    protected String[] getDefaultValue() {
        return checkboxes.stream()
                .filter(Checkbox::isCheckedByDefault)
                .map(Checkbox::getValue)
                .toArray(String[]::new);
    }

    public Iterable<Checkbox> getCheckboxes() {
        return checkboxes;
    }

    @NotNull
    private Stream<Checkbox> getCheckboxStream() {
        return Optional.of(CoralUtil.getItemDataSource(request, response, resource))
                .map(resources -> StreamSupport.stream(resources.spliterator(), false))
                .orElseGet(Stream::empty)
                .map(this::itemToCheckbox);
    }

    private Checkbox itemToCheckbox(Resource resource) {
        return new Checkbox(this, resource);
    }

    public static class Checkbox {

        private final CheckboxGroup checkboxGroup;

        private final Resource resource;

        private final ValueMap properties;

        Checkbox(CheckboxGroup checkboxGroup, Resource resource) {
            this.checkboxGroup = checkboxGroup;
            this.resource = resource;
            this.properties = resource.getValueMap();
        }

        public String getLabel() {
            final String text = properties.get("text", String.class);
            final boolean translateOptions = properties.get("translateOptions", true);
            return translateOptions ? checkboxGroup.i18n.getVar(text) : text;
        }

        public String getValue() {
            return properties.get("value", String.class);
        }

        public Map<String, Object> getAttributes() {
            final String checkboxValue = getValue();

            final ExpressionHelper eh = checkboxGroup.expressionHelper;
            final Map<String, Object> attributes = CoralUtil.getGraniteCommonAttributes(resource, eh);
            attributes.put("name", checkboxGroup.getName());
            attributes.put("value", checkboxValue);
            attributes.put("checked", isChecked());
            attributes.put("disabled", checkboxGroup.isDisabled());
            return attributes;
        }

        public boolean isChecked() {
            final String[] storedValue = checkboxGroup.getStoredValue()
                    .orElse(checkboxGroup.getDefaultValue());
            return ArrayUtils.contains(storedValue, getValue());
        }

        public boolean isCheckedByDefault() {
            return properties.get("checked", false);
        }
    }
}
