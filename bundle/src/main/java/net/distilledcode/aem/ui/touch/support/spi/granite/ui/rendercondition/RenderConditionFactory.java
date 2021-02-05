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
package net.distilledcode.aem.ui.touch.support.spi.granite.ui.rendercondition;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.rendercondition.RenderCondition;
import com.adobe.granite.ui.components.rendercondition.SimpleRenderCondition;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * {@code RenderConditionFactory} provide a convenient way to create custom {@link RenderCondition}s. A
 * {@code RenderConditionFactory} must be registered with a property {@code rendercondition.resourceTypes}.
 * {@code RenderCondition}s may then be used by creating a "granite:rendercondition" child node in UI elements,
 * referencing a resourceType using the sling:resourceType property.
 * <br>
 * The {@code RenderConditionFactory} is provided with the current {@code SlingHttpServletRequest},
 * the "granite:rendercondition" child of the current resource and an {@link ExpressionHelper} instance that
 * allow expressions in property values to be evaluated.
 * <br>
 * Furthermore, details of Granite {@code RenderCondition}s like registering a {@code Servlet} or setting
 * a request attribute are abstracted away and need not be taken care of by implementations of
 * {@code RenderConditionFactory}.
 */
@ConsumerType
public abstract class RenderConditionFactory {

    /**
     * Name of the service property indicating the rendercondition's resource type.
     */
    public static final String RENDERCONDITION_RESOURCE_TYPES = "rendercondition.resourceTypes";

    /**
     * Advanced: name of the service property indicating the rendercondition's resource type prefix.
     * This defaults to "0" and does not normally need to be set. It works like <a href="https://sling.apache.org/documentation/the-sling-engine/servlets.html#servlet-registration">'sling.servlet.prefix'</a>.
     */
    public static final String RENDERCONDITION_PREFIX = "rendercondition.prefix";

    /**
     * Method that implements the check of a {@code RenderCondition} for the current request. This method is called
     * by {@link #createRenderCondition(SlingHttpServletRequest, ExpressionHelper)}, unless overridden,
     * and results in {@link SimpleRenderCondition#TRUE} or {@link SimpleRenderCondition#FALSE} being returned.
     *
     * @param request The current request.
     * @param ex An ExpressionHelper for resolving expressions read from property values.
     *
     * @return {@code true} or {@code false}, depending on whether the render condition should succeed or not
     */
    protected abstract boolean check(@NotNull final SlingHttpServletRequest request,
                                     @NotNull final ExpressionHelper ex);

    /**
     * Method that creates a new {@code RenderCondition} for the current request. If {@code null}
     * is returned, the implementation sets the {@code RenderCondition} request attribute to
     * {@code SimpleRenderCondition.TRUE}.
     * <br>
     * Usually it is sufficient to implement {@link #check(SlingHttpServletRequest, ExpressionHelper)}.
     * Overriding this method may only be necessary for advanced use-cases, e.g. if lazy evaluation of the condition
     * is desired.
     *
     * @param request  The current request.
     * @param ex       An ExpressionHelper for resolving expressions read from property values.
     *
     * @return a {@code RenderCondition} that evaluates to {@code true} or {@code false}; or {@code null}, which
     *         is equivalent of returning {@code SimpleRenderCondition.TRUE}
     */
    @Nullable
    public RenderCondition createRenderCondition(@NotNull final SlingHttpServletRequest request,
                                                 @NotNull final ExpressionHelper ex) {
        return check(request, ex) ? SimpleRenderCondition.TRUE : SimpleRenderCondition.FALSE;
    }
}
