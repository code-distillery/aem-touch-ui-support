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
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Map;

@Model(
        resourceType = "distilledcode/aem-touch-ui-support/components/field",
        adaptables = SlingHttpServletRequest.class,
        adapters = { Field.class, GenericStringField.class }
)
public class GenericStringField extends Field<String> {

    @Inject
    public GenericStringField(@NotNull SlingHttpServletRequest request) {
        super(request);
    }

    @Override
    public Map<String, Object> getAttributes() {
        final Map<String, Object> attributes = super.getAttributes();
        addStoredValue(attributes);
        return attributes;
    }

    @NotNull
    @Override
    protected String getDefaultValue() {
        return "";
    }
}
