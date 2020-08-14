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
package net.distilledcode.aem.ui.touch.support.impl.foundation.form.tagfield;

import net.distilledcode.aem.ui.touch.support.spi.foundation.form.tagfield.TagNamespaceProvider;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component(service = TagNamespaceProvider.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = PathBasedTagNamespaceProvider.Config.class)
public class PathBasedTagNamespaceProvider implements TagNamespaceProvider {

    @ObjectClassDefinition(name = "Distilled Code ~ Path Based Tag Namespace Provider")
    @interface Config {
        @AttributeDefinition(description = "Regular expression followed by \" :: \" (space, 2x colon, space) " +
                "followed by a comma separated list of namespaces (may refer to regexp " +
                "groups). E.g \"/content/([^/]+).* :: generic,$1\"")
        String[] namespace_mappings() default {};
    }

    private List<Mapping> mappings;

    @Activate
    @Modified
    public void activate(Config config) {
        final String[] mappings = Optional.ofNullable(config.namespace_mappings()).orElse(new String[0]);
        this.mappings = Arrays.stream(mappings)
                .map(Mapping::create)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    @Deactivate
    public void deactivate() {
        this.mappings = null;
    }

    @NotNull
    @Override
    public String[] getNamespaces(@NotNull SlingHttpServletRequest request, @NotNull String path, @NotNull ValueMap config) {
        final Collection<String> namespaces = new TreeSet<>();
        for (Mapping mapping : mappings) {
            final Matcher matcher = mapping.getRegexp().matcher(path);
            if (matcher.matches()) {
                for (String namespace : mapping.getNamespaces()) {
                    namespaces.add(matcher.replaceAll(namespace));
                }
            }
        }
        return namespaces.toArray(new String[0]);
    }

    private static class Mapping {

        private final Pattern regexp;

        private final String[] namespaces;

        public static Mapping create(String config) {
            final String[] parts = config.split(" :: ");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid configuration \"" + config + "\"");
            }
            final Pattern regexp = Pattern.compile(parts[0].trim());
            final String[] namespaces = parts[1].split(",");
            return new Mapping(regexp, Arrays.stream(namespaces).map(String::trim).toArray(String[]::new));
        }

        private Mapping(Pattern regexp, String[] namespaces) {
            this.regexp = regexp;
            this.namespaces = namespaces;
        }

        public Pattern getRegexp() {
            return regexp;
        }

        public String[] getNamespaces() {
            return namespaces;
        }
    }
}
