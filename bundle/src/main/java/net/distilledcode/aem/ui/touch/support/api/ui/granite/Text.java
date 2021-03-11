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

@Model(adaptables = SlingHttpServletRequest.class)
public class Text extends Component {

    public Text(@NotNull SlingHttpServletRequest request) {
        super(request);
    }

    public String getText() {
        return properties.get("text", String.class);
    }

    public boolean allowHtml() {
        return properties.get("allowHtml", false);
    }

    public String getTagName() {
        return properties.get("tagName", String.class);
    }
}
