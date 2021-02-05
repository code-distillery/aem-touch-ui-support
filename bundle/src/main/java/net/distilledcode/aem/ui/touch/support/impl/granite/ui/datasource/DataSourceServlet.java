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
package net.distilledcode.aem.ui.touch.support.impl.granite.ui.datasource;

import com.adobe.granite.ui.components.ExpressionHelper;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.datasource.DataSourceFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;

class DataSourceServlet extends SlingSafeMethodsServlet {

    private final DataSourceFactory factory;

    private final ExpressionResolver expressionResolver;

    DataSourceServlet(DataSourceFactory factory, ExpressionResolver expressionResolver) {
        this.factory = factory;
        this.expressionResolver = expressionResolver;
    }

    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) {
        ExpressionHelper ex = new ExpressionHelper(expressionResolver, request);

        // request.getResource() may point at the parent of the "datasource" resource
        // or it may point directly at the "datasource" resource. Or in the case of
        // a coral table, it may point to a "columnsdatasource" for column definitions.
        // therefore we first check if there is a "datasource" child and use it if present.
        // otherwise we assume that request.getResource() is the correct datasource resource.
        Resource dsResource = request.getResource().getChild("datasource");
        if (dsResource == null) {
            dsResource = request.getResource();
        }
        DataSource dataSource = factory.createDataSource(request, dsResource, ex);
        request.setAttribute(DataSource.class.getName(), dataSource);
    }
}
