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

import com.day.cq.wcm.api.PageManager;
import net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil;
import net.distilledcode.aem.ui.touch.support.api.ui.Link;
import net.distilledcode.aem.ui.touch.support.api.ui.QuickActions;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.models.annotations.Model;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil.COMPARISON_PATH_PREFIX;
import static net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil.isClassicComponentDialog;
import static net.distilledcode.aem.ui.touch.support.api.ui.DialogUtil.isPageRenderingComponent;
import static net.distilledcode.aem.ui.touch.support.api.ui.Link.attributes;

@Model(adaptables = Resource.class)
public class ConsoleItem {

    private static final String CRX_DE_PATH_PREFIX = "/crx/de/index.jsp#";

    private final ResourceResolver resolver;

    private final Resource resource;

    @Inject
    public ConsoleItem(Resource resource) {
        this.resource = resource;
        this.resolver = resource.getResourceResolver();
    }

    public String getId() {
        return resource.getPath();
    }

    public String getType() {
        if (isClassicComponentDialog(resource)) {
            if (isPageRenderingComponent(resource.getParent())) {
                return "Classic Page Dialog";
            } else {
                return "Classic Dialog";
            }
        } else {
            return "Include";
        }
    }

    public String getClassicPath() {
        return resource.getPath();
    }

    public String getTouchPath() {
        // TODO: ask converter for path
        return DialogUtil.getTouchUiPath(resource);
    }

    public boolean isConverted() {
        return resolver.getResource(getTouchPath()) != null;
    }

    public Collection<Link> getClassicLinks() {
        final ArrayList<Link> links = new ArrayList<>();
        final String componentPath = getComponentPath();
        if (componentPath != null) {
            links.add(new Link("view", DialogUtil.getClassicUiDialogUrl(componentPath), attributes(
                    "target", "_blank",
                    "title", "View Classic Dialog"
            )));
        }
        links.add(new Link("edit", CRX_DE_PATH_PREFIX + getClassicPath(), attributes(
                "target", "_blank",
                "x-cq-linkchecker", "skip",
                "title", "Open in CRX DE"
        )));
        return links;
    }

    public Collection<Link> getTouchUILinks() {
        final List<Link> links = new ArrayList<>();
        if (isConverted()) {
            final String componentPath = getComponentPath();
            if (componentPath != null) {
                links.add(new Link("compare", COMPARISON_PATH_PREFIX + ".html" + componentPath, attributes(
                        "target", "_blank",
                        "title", "Compare Dialogs"
                )));
                links.add(new Link("view", DialogUtil.getTouchUiDialogUrl(componentPath), attributes(
                        "target", "_blank",
                        "title", "View Touch UI Dialog"
                )));
            }
            // CRX DE expects the URL fragment's 'cq:dialog' to be URI encoded with capital letters
            final String crxdeHref = getTouchPath().replace("/cq:dialog", "/cq%3Adialog");
            links.add(new Link("edit", CRX_DE_PATH_PREFIX + crxdeHref, attributes(
                    "target", "_blank",
                    "x-cq-linkchecker", "skip",
                    "title", "Open in CRX DE"
            )));
        }
        return links;
    }

    public Collection<Link> getExampleLinks() {
        final PageManager pageManager = Optional.ofNullable(resolver.adaptTo(PageManager.class))
                .orElseThrow(() -> new NullPointerException("Could not adapt ResourceResolver to PageManager"));
        final String query = String.format("/jcr:root/content//*[@sling:resourceType='%s']", getResourceType());
        final Iterable<Resource> resources = () -> resolver.findResources(query, "xpath");
        return StreamSupport.stream(resources.spliterator(), false)
                .map(pageManager::getContainingPage)
                .filter(Objects::nonNull)
                .map(page -> {
                    final String title = page.getProperties().get("jcr:title", page.getName());
                    return new Link(title, "/editor.html" + page.getPath() + ".html",
                            attributes("title", title, "target", "_blank"));
                })
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private String getResourceType() {
        return StringUtils.removeStart(getParentPath(), "/apps/");
    }

    public QuickActions getQuickActions() {
        return new QuickActions();
    }

    private String getComponentPath() {
        return isClassicComponentDialog(resource) ? getParentPath() : null;
    }

    private String getParentPath() {
        return ResourceUtil.getParent(resource.getPath());
    }
}
