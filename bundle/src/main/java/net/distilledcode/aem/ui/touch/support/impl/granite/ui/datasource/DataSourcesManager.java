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

import com.adobe.granite.ui.components.ExpressionResolver;
import net.distilledcode.aem.ui.touch.support.impl.granite.ui.AbstractProxyServletManager;
import net.distilledcode.aem.ui.touch.support.spi.granite.ui.datasource.DataSourceFactory;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

@Component
public class DataSourcesManager extends AbstractProxyServletManager<DataSourceFactory> {

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ExpressionResolver expressionResolver;

    public DataSourcesManager() {
        super(
                DataSourceServlet::new,
                DataSourceFactory.DATASOURCE_RESOURCE_TYPES,
                DataSourceFactory.DATASOURCE_PREFIX
        );
    }

    @Override
    @Activate
    protected void activate() {
        super.activate(expressionResolver);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE, policy = ReferencePolicy.DYNAMIC)
    protected void bindFactory(@NotNull final ServiceReference<DataSourceFactory> reference) {
        super.bindFactory(reference);
    }

    @Override
    protected void unbindFactory(@NotNull final ServiceReference<DataSourceFactory> reference) {
        super.unbindFactory(reference);
    }
}
