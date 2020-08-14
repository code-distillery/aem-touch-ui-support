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
import com.day.cq.i18n.I18n;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Map;

// TODO: support render condition
@Model(adaptables = SlingHttpServletRequest.class)
public class Component {

    protected final @Nullable ExpressionHelper expressionHelper;

    protected final @NotNull Resource resource;

    protected final @NotNull ValueMap properties;

    protected final @NotNull I18n i18n;

    @Inject
    public Component(@NotNull SlingHttpServletRequest request) {
        this.expressionHelper = CoralUtil.getExpressionHelper(request);
        this.resource = request.getResource();
        this.properties = resource.getValueMap();
        this.i18n = new I18n(request);
    }

    public boolean isRenderConditionSatisfied() {
        return true;
    }

    public Map<String, Object> getAttributes() {
        return CoralUtil.getGraniteCommonAttributes(resource, expressionHelper);
    }
}
