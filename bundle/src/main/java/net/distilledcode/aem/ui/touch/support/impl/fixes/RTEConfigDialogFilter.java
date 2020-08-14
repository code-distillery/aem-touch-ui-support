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
package net.distilledcode.aem.ui.touch.support.impl.fixes;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * Filter to add utf-8 encoding to the request headers in order to properly support special chars dialogs.
 */
@Component(
        service = Filter.class,
        property = {
                "sling.filter.scope=REQUEST",
                "sling.filter.methods=GET",
                "sling.filter.resourceTypes=cq/gui/components/authoring/dialog/richtext",
                "sling.filter.selectors=config",
                "sling.filter.extensions=json"
        }
)
public class RTEConfigDialogFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to do
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof SlingHttpServletRequest) {
            final SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;

            if (Objects.equals(slingRequest.getMethod(), "GET")
                    && slingRequest.getResource().isResourceType("cq/gui/components/authoring/dialog/richtext")) {
                final RequestPathInfo rpi = slingRequest.getRequestPathInfo();
                if (Objects.equals(rpi.getSelectorString(), "config") && Objects.equals(rpi.getExtension(), "json")) {
                    response.setContentType("application/json;charset=utf-8");
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing to do
    }
}
