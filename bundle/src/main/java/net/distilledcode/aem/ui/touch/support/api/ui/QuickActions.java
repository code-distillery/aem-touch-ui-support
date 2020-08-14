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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper for simplifying rendering of quick actions "meta" and "link" HTML tags.
 */
public class QuickActions implements Iterable<QuickActions.Link> {

    private final List<Link> quickActions = new ArrayList<>();

    /**
     * Links added with {@code href == null} will only appear in the activators list,
     * but not in the link iterator.
     *
     * @param rel The identifier of the action.
     * @param href The URL to use for the action.
     */
    public void add(@NotNull String rel, @Nullable String href) {
        quickActions.add(new Link(rel, href));
    }

    public String getActivators() {
        return quickActions.stream()
                .map(Link::getActivator)
                .collect(Collectors.joining(" "));
    }

    @Override
    @NotNull
    public Iterator<Link> iterator() {
        return quickActions.stream()
                .filter(link -> link.href != null)
                .iterator();
    }

    public static class Link {

        public final String rel;

        public final String href;

        public Link(@NotNull String rel, @Nullable String href) {
            this.rel = rel;
            this.href = href;
        }

        private String getActivator() {
            return rel + "-activator";
        }
    }
}
