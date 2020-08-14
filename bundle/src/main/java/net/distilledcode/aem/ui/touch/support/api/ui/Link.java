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
package net.distilledcode.aem.ui.touch.support.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Link {
    private final String icon;
    private final String title;
    private final String href;
    private final Map<String, String> extraAttributes;

    public Link(@NotNull String title, @NotNull String href) {
        this(null, title, href, Collections.emptyMap());
    }

    public Link(@NotNull String title, @NotNull String href, @NotNull Map<String, String> extraAttributes) {
        this(null, title, href, extraAttributes);
    }

    public Link(@Nullable String icon, @NotNull String title, @NotNull String href) {
        this(icon, title, href, Collections.emptyMap());
    }

    public Link(@Nullable String icon, @NotNull String title, @NotNull String href, @NotNull Map<String, String> extraAttributes) {
        this.icon = icon;
        this.title = title;
        this.href = href;
        this.extraAttributes = extraAttributes;
    }

    public String getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getHref() {
        return href;
    }

    public Map<String, String> getAttributes() {
        final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("title", title);
        attributes.put("href", href);
        attributes.putAll(extraAttributes);
        return attributes;
    }

    public static Map<String, String> attributes(String... attributeDefs) {
        if (attributeDefs.length % 2 != 0) {
            throw new IllegalArgumentException("Even number of arguments required");
        }
        
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < attributeDefs.length; i += 2) {
            String key = attributeDefs[i];
            String value = attributeDefs[i + 1];
            attributes.put(key, value);
        }
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link)) return false;
        Link link = (Link) o;
        return Objects.equals(icon, link.icon) &&
                title.equals(link.title) &&
                href.equals(link.href) &&
                extraAttributes.equals(link.extraAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(icon, title, href, extraAttributes);
    }
}
