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
package net.distilledcode.aem.ui.touch.support.api.ui.consoles;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Optional;

@Model(adaptables = Resource.class)
public class ConversionConfig {

    private final Resource resource;
    private final ValueMap properties;

    @Inject
    public ConversionConfig(@NotNull Resource resource) {
        this.resource = resource;
        this.properties = resource.getValueMap();
    }

    public String getTitle() {
        return properties.get("jcr:title", String.class);
    }

    public String getDescription() {
        return properties.get("jcr:description", String.class);
    }

    public String getPath() {
        return resource.getPath();
    }

    @NotNull
    public String[] getScope() {
        return properties.get("paths", new String[0]);
    }

    public String getConsoleHref() {
        return "/apps/distilledcode/aem-touch-ui-support/ui/content/converter/console.html" + getPath();
    }

    public static String[] getScope(@Nullable Resource configResource) {
        return Optional.ofNullable(configResource)
                .map(ConversionConfig::new)
                .map(ConversionConfig::getScope)
                .orElse(new String[0]);


    }
}
