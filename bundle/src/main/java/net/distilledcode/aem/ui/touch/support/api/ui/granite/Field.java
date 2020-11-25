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

import com.adobe.granite.ui.components.Config;
import com.adobe.granite.ui.components.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static net.distilledcode.aem.ui.touch.support.api.ui.granite.CoralUtil.addClasses;

// TODO:
// - support render condition
// - support renderWrapper == false, i.e. if the field is not a root field (e.g. multi-field)
public abstract class Field<V> extends Component {

    private final @NotNull Value value;

    protected Field(@NotNull SlingHttpServletRequest request) {
        super(request);
        this.value = new Value(request, new Config(resource));
    }

    public String getLabel() {
        return i18n.getVar(properties.get("fieldLabel", String.class));
    }

    public String getDescription() {
        return i18n.getVar(properties.get("fieldDescription", String.class));
    }

    public String getName() {
        return properties.get("name", String.class);
    }

    public boolean isRequired() {
        return properties.get("required", false);
    }

    public boolean isDisabled() {
        return properties.get("disabled", false);
    }

    public boolean isRenderWrapper() {
        return true;
    }

    public Map<String, Object> getAttributes() {
        final Map<String, Object> attributes = super.getAttributes();
        attributes.put("name", getName());
        attributes.compute("class", addClasses("coral-Form-field"));
        attributes.put("required", isRequired());
        attributes.put("disabled", isDisabled());
        addValidationAttributes(attributes);
        return attributes;
    }

    @NotNull
    protected abstract V getDefaultValue();

    @NotNull
    protected final Optional<V> getStoredValue() {
        final V defaultValue = getDefaultValue();
        return getStoredValueByName(getName(), defaultValue);
    }

    @NotNull
    protected final <T> Optional<T> getStoredValueByName(String name, Class<T> type) {
        return Optional.ofNullable(value.get(name, type));
    }

    @NotNull
    protected final Optional<V> getStoredValueByName(String name, V defaultValue) {
        return Optional.ofNullable(value.val(name, defaultValue));
    }

    protected void addStoredValue(Map<String, Object> attributes) {
        getStoredValue().ifPresent(val -> attributes.put("value", val));
    }

    private void addValidationAttributes(Map<String, Object> attributes) {
        final String validation = StringUtils.join(properties.get("validation", new String[0]), ' ');
        attributes.put("data-foundation-validation", validation);
        attributes.put("data-validation", validation); // Compatibility
    }

    public Map<String, Object> getWrapperAttributes() {
        final Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("hidden", properties.get("renderHidden", false));
        attributes.compute("class", addClasses(
                "coral-Form-fieldwrapper",
                properties.get("wrapperClass", String.class),
                "foundation-field-edit"
        ));
        return attributes;
    }
}
