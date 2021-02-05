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
package net.distilledcode.aem.ui.touch.support.impl.granite.ui.rendercondition;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.rendercondition.RenderCondition;
import com.adobe.granite.ui.components.rendercondition.SimpleRenderCondition;
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.rendercondition.RenderConditionFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;

public class RenderConditionServlet extends SlingSafeMethodsServlet {

    private final RenderConditionFactory factory;

    private final ExpressionResolver expressionResolver;

    RenderConditionServlet(RenderConditionFactory factory, ExpressionResolver expressionResolver) {
        this.factory = factory;
        this.expressionResolver = expressionResolver;
    }

    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) {
        ExpressionHelper ex = new ExpressionHelper(expressionResolver, request);
        RenderCondition renderCondition = factory.createRenderCondition(request, ex);
        request.setAttribute(RenderCondition.class.getName(), renderCondition == null ? SimpleRenderCondition.TRUE : renderCondition);
    }
}
