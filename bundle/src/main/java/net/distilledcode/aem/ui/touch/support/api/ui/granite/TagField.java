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

import com.day.cq.tagging.TagManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.distilledcode.aem.ui.touch.support.api.ui.granite.CoralUtil.addClasses;
import static net.distilledcode.aem.ui.touch.support.api.ui.granite.CoralUtil.putNullSafe;

@Model(
        resourceType = "distilledcode/aem-touch-ui-support/components/tagfield",
        adaptables = SlingHttpServletRequest.class,
        adapters = { Field.class, TagField.class }
)
public class TagField extends Field<String[]> {

    private final Locale locale;

    private final TagManager tagManager;

    @Inject
    public TagField(final SlingHttpServletRequest request) {
        super(request);
        this.locale = request.getLocale();
        this.tagManager = request.getResourceResolver().adaptTo(TagManager.class);
    }

    @Override
    @NotNull
    protected String[] getDefaultValue() {
        return new String[0];
    }

    @Override
    public Map<String, Object> getAttributes() {
        final Map<String, Object> attributes = super.getAttributes();

        attributes.compute("class", addClasses("cq-ui-tagfield"));
        attributes.compute("placeholder", putNullSafe(properties.get("emptyText", String.class)));

        final String defaultPickerSrc = "/apps/distilledcode/aem-touch-ui-support/content/tagfield/" +
                "picker.html${param.item != null ? param.item : requestPathInfo.suffix}" +
                "?_charset_=utf-8&selectionCount=multiple&configPath=" + resource.getPath();
        final String pickerSrc = properties.get("pickerSrc", defaultPickerSrc);
        attributes.compute("pickersrc", putNullSafe(
                Optional.ofNullable(expressionHelper)
                        .map(ex -> ex.getString(pickerSrc))
                        .orElse(pickerSrc)
        ));

        attributes.put("required", properties.get("required", false));
        attributes.put("disabled", properties.get("disabled", false));
        attributes.put("forceselection", properties.get("forceSelection", false));

        final boolean isMultiple = properties.get("multiple", false);
        attributes.put("multiple", isMultiple);
        if (isMultiple) {
            attributes.put("valuedisplaymode", "block");
        }

        if (properties.get("autocreateTag", false)) {
            attributes.put("data-cq-ui-tagfield-create-action", resource.getPath());
        }

        return attributes;
    }

    public Map<String, Object> getSuggestAttributes() {
        final Map<String, Object> suggestAttributes = new LinkedHashMap<>();

        final String defaultSuggestSrc = "/apps/distilledcode/aem-touch-ui-support/content/tagfield/" +
                "suggestions{.offset,limit}.html${param.item != null ? param.item : requestPathInfo.suffix}" +
                "?_charset_=utf-8&configPath=" + resource.getPath() + "{&query}";
        final String suggestSrc = properties.get("suggestSrc", defaultSuggestSrc);

        suggestAttributes.put("foundation-autocomplete-suggestion", true);
        suggestAttributes.compute("class", addClasses("foundation-picker-buttonlist"));
        suggestAttributes.compute("data-foundation-picker-buttonlist-src", putNullSafe(
                Optional.ofNullable(expressionHelper)
                        .map(ex -> ex.getString(suggestSrc))
                        .orElse(suggestSrc)
        ));
        return suggestAttributes;
    }

    public Map<String, Object> getValueAttributes() {
        final Map<String, Object> valueAttributes = new LinkedHashMap<>();
        valueAttributes.put("foundation-autocomplete-value", true);
        valueAttributes.put("name", getName());
        return valueAttributes;
    }

    public Set<Tag> getTags() {
        return getStoredValue()
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(this::toTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Tag toTag(String tagId) {
        final com.day.cq.tagging.Tag cqTag = tagManager.resolve(tagId);
        return new Tag(cqTag.getTitlePath(locale), tagId);
    }

    public static class Tag {

        private final String title;

        private final String value;

        Tag(String title, String value) {
            this.title = title;
            this.value = value;
        }

        public String getTitle() {
            return title;
        }

        public String getValue() {
            return value;
        }
    }
}

