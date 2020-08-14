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

import net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Model;

import javax.inject.Inject;
import java.util.Optional;

@Model(adaptables = SlingHttpServletRequest.class)
public class DialogComparison {

    private final String componentPath;

    @Inject
    public DialogComparison(SlingHttpServletRequest request) {
        this.componentPath = Optional
                .ofNullable(request.getRequestPathInfo().getSuffix())
                .orElse("");
    }

    public String getOriginalHref() {
        return DialogUtil.getClassicUiDialogUrl(componentPath);
    }

    public String getTouchUiHref() {
        return DialogUtil.getTouchUiDialogUrl(componentPath);
    }
}
