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
package net.distilledcode.aem.ui.touch.support.impl;

import net.distilledcode.aem.ui.touch.support.api.ui.consoles.ConsoleItem;
import net.distilledcode.aem.ui.touch.support.api.ui.consoles.ConversionConfig;
import net.distilledcode.aem.ui.touch.support.spi.DialogConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.distilledcode.aem.ui.touch.support.impl.ui.ConsoleItemDataSource.getConvertibleResources;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.methods=POST",
                "sling.servlet.resourceTypes=distilledcode/aem-touch-ui-support/ui/components/conversion-config"
        }
)
public class DialogConversionServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DialogConversionServlet.class);

    private static final String PARAM_PATHS = "paths";
    private static final String PARAM_FORCE = "force";

    @Reference(policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
    private DialogConverter dialogConverter;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private Packaging packaging;

    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
        final RequestPathInfo requestPathInfo = request.getRequestPathInfo();
        final String selector = requestPathInfo.getSelectorString();
        final Resource resource = request.getResource();
        final String[] searchRoots = ConversionConfig.getScope(resource);
        
        if (searchRoots.length == 0) {
            response.sendError(404, "No scope defined in configuration");
            return;
        }
        if (Objects.equals(selector, "package")) {
            final ResourceResolver resolver = request.getResourceResolver();
            final Iterable<Resource> convertibleResources = getConvertibleResources(resolver, searchRoots)::iterator;
            final List<PathFilterSet> convertedPaths = StreamSupport.stream(convertibleResources.spliterator(), false)
                    .map(ConsoleItem::new)
                    .filter(ConsoleItem::isConverted)
                    .map(ConsoleItem::getTouchPath)
                    .map(PathFilterSet::new)
                    .collect(Collectors.toList());

            String packagePath = null;
            final Session session = Optional.ofNullable(resolver.adaptTo(Session.class))
                    .orElseThrow(() -> new NullPointerException("Cannot adapt ResourceResolver to Session"));
            try {
                final JcrPackageManager jcrPackageManager = packaging.getPackageManager(session);
                final JcrPackage jcrPackage = jcrPackageManager.create("aem-touch-ui-support", "converted");
                final Node node = jcrPackage.getNode();
                packagePath = node == null ? null : node.getPath();
                final JcrPackageDefinition definition = Optional.ofNullable(jcrPackage.getDefinition())
                        .orElseThrow(() -> new NullPointerException("JCR Package definition is null"));
                final DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
                convertedPaths.forEach(filter::add);
                definition.setFilter(filter, false);
                session.save();

                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + resource.getName() + "-converted-dialogs.zip\"");
                response.setContentType("application/zip");
                jcrPackageManager.assemble(definition, null, response.getOutputStream());
            } catch (RepositoryException | PackageException e) {
                throw new RuntimeException(e);
            } finally {
                if (packagePath != null) {
                    try {
                        session.getNode(packagePath).remove();
                        session.save();
                    } catch (RepositoryException e) {
                        LOG.error("Failed to remove temporary package '{}'", packagePath);
                    }
                }
            }
        } else {
            super.doGet(request, response);
        }
    }

    @Override
    protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws IOException {
        
        final Set<String> paths = Optional.ofNullable(request.getRequestParameters(PARAM_PATHS))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(RequestParameter::getString)
                .collect(Collectors.toSet());

        final String[] searchRoots = ConversionConfig.getScope(request.getResource());
        final List<String> orderedPaths = getConvertibleResources(request.getResourceResolver(), searchRoots)
                .map(Resource::getPath)
                .filter(paths::contains)
                .collect(Collectors.toList());

        boolean force = Optional.ofNullable(request.getRequestParameter(PARAM_FORCE))
                .map(RequestParameter::getString)
                .map(Boolean::parseBoolean)
                .orElse(false);

        if (paths.isEmpty()) {
            LOG.warn("Missing or empty parameter '" + PARAM_PATHS + "'");
            response.setContentType("text/plain");
            response.getWriter().println("Missing or empty parameter '" + PARAM_PATHS + "'");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (dialogConverter == null) {
            response.setContentType("text/plain");
            response.getWriter().println("No DialogConverter is available");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        try {
            final ResourceResolver resolver = request.getResourceResolver();
            Collection<DialogConverter.Result> results = dialogConverter.convert(resolver, orderedPaths, force);

            response.setContentType("text/plain");
            final PrintWriter writer = response.getWriter();
            for (DialogConverter.Result result : results) {
                final String error = result.getError();
                if (error != null) {
                    // TODO: persist error messages in order to display them in UI
                    writer.append("Failed to convert ").println(result.getPath());
                    final String[] lines = StringUtils.split(error, "\n");
                    for (String line : lines) {
                        writer.append("    ").println(line);
                    }
                } else {
                    writer.append("Successfully converted ").println(result.getPath());
                }
            }
        } catch (Exception e) {
            response.setContentType("text/plain");
            Throwable ex = e;
            LOG.warn("There was a problem during dialog conversion: ", e);
            final PrintWriter writer = response.getWriter();
            writer.println("There was an error (see logs for stack trace):");
            while (ex != null) {
                writer.println(ex.getMessage());
                ex = ex.getCause();
            }
        }
    }
}
