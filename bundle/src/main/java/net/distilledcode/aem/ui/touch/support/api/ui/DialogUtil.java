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
package net.distilledcode.aem.ui.touch.support.api.ui;

import net.distilledcode.aem.ui.touch.support.impl.ui.Templates;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class DialogUtil {

    public static final String COMPARISON_PATH_PREFIX = "/apps/distilledcode/aem-touch-ui-support/ui/comparison";

    public static final String CLASSIC_DIALOG_PATH_PREFIX = COMPARISON_PATH_PREFIX + "/_jcr_content/content/items/comparison.classic-ui-dialog.html";

    public static String getTouchUiName(Resource resource) {
        if (isClassicComponentDialog(resource)) {
            return "cq:dialog";
        }

        if (isClassicDialog(resource) || !isWithinClassicDialog(resource)) {
            return "touch-ui-include-" + resource.getName();
        }
        
        return resource.getName();
    }

    public static String getTouchUiPath(Resource resource) {
        final String parentPath = ResourceUtil.getParent(resource.getPath());
        return parentPath + "/" + getTouchUiName(resource);

//        final Resource correspondingResource = DialogUtil.findTouchUiResource(resource);
//        if (correspondingResource == null) {
//            throw new NullPointerException("Could not find corresponding resource for '" + resource.getPath() + "'");
//        }
//        return correspondingResource.getPath();
    }

    public static Resource findTouchUiResource(Resource classicResource) {
        final ResourceResolver resolver = classicResource.getResourceResolver();
        
        final Resource classicDialog = findContainingDialog(classicResource);
        if (classicDialog != null) {
            final String touchUiPath = getTouchUiPath(classicDialog);
            final Resource touchDialog = resolver.getResource(touchUiPath);
            if (touchDialog != null) {
                return getCorrespondingTouchUiResource(classicResource, classicDialog, touchDialog);
            }
        }

        final Resource includeRoot = findContainingIncludeRoot(classicResource);
        if (includeRoot != null && includeRoot.getParent() != null) {
            final String touchUiPath = getTouchUiPath(includeRoot);
            final Resource touchUiIncludeRoot = resolver.getResource(touchUiPath);
            if (touchUiIncludeRoot != null) {
                return getCorrespondingTouchUiResource(classicResource, includeRoot, touchUiIncludeRoot);
            }
        }

        return classicResource;
    }

    private static Resource getCorrespondingTouchUiResource(Resource classicResource, Resource classicRoot, Resource touchRoot) {
        if (Objects.equals(classicResource.getPath(), classicRoot.getPath())) {
            return touchRoot;
        }

        final String suffix = StringUtils.removeStart(classicResource.getPath(), classicRoot.getPath());
        final String name = ResourceUtil.getName(suffix);
        List<Resource> descendants = findDescendants(touchRoot, resource -> Objects.equals(resource.getName(), name));

        // heuristic: smallest levenshtein distance
        final Comparator<Resource> comparing = Comparator.comparing(resource -> {
            final String touchSuffix = StringUtils.removeStart(resource.getPath(), touchRoot.getPath());
            return StringUtils.getLevenshteinDistance(touchSuffix, suffix);
        });
        descendants.sort(comparing);
        if (descendants.isEmpty()) {
            throw new IllegalStateException("No descendant named '"
                    + name + "' found in '" + touchRoot.getPath() + "'");
        }
        return descendants.get(0);
    }

    private static @Nullable Resource findContainingIncludeRoot(Resource classicResource) {
        return findMatchingAncestor(classicResource, DialogUtil::hasTouchUiIncludeSibling);
    }

    private static boolean hasTouchUiIncludeSibling(Resource resource) {
        final String touchUiName = getTouchUiName(resource);
        final Resource parent = resource.getParent();
        if (parent != null) {
            return parent.getChild(touchUiName) != null;
        }
        return false;
    }

    public static boolean hasXtypeInDescendants(Resource classicDialogResource) {
        return !findDescendants(classicDialogResource, res -> {
            final ValueMap properties = res.getValueMap();
            return properties.containsKey("xtype") || Objects.equals(properties.get("jcr:primaryType", String.class), "cq:Widget");
        }).isEmpty();
    }

    public static List<Resource> findDescendants(Resource resource, Predicate<Resource> filter) {
        List<Resource> matchingDescendants = new ArrayList<>();
        for (Resource child : resource.getChildren()) {
            if (filter.test(child)) {
                matchingDescendants.add(child);
            }
            matchingDescendants.addAll(findDescendants(child, filter));
        }
        return matchingDescendants;
    }

    @Nullable
    @Contract("null,_->null")
    private static Resource findMatchingAncestor(Resource resource, Predicate<Resource> toFind) {
        Resource candidate = resource;
        while (candidate != null && !toFind.test(candidate)) {
            candidate = candidate.getParent();
        }
        return candidate;
    }

    @Contract("null->null")
    public static Resource findContainingDialog(Resource classicDialogResource) {
        return findMatchingAncestor(classicDialogResource, DialogUtil::isClassicDialog);
    }


    @Contract("null->false")
    public static boolean isClassicDialog(Resource resource) {
        return matchProperty("jcr:primaryType", "cq:Dialog").test(resource);
    }

    @NotNull
    public static Predicate<Resource> matchProperty(String propertyName, Object value) {
        return resource -> resource != null
                && Objects.equals(resource.getValueMap().get(propertyName, value.getClass()), value);
    }
    
    public static boolean isClassicComponentDialog(@NotNull Resource resource) {
        return Objects.equals(resource.getName(), "dialog")
                && isClassicDialog(resource)
                && isComponent(resource.getParent());
    }

    @Contract("null->false")
    public static boolean isComponent(@Nullable Resource resource) {
        return matchProperty("jcr:primaryType", "cq:Component").test(resource);
    }

    @Contract("null->false")
    public static boolean isWithinClassicDialog(Resource resource) {
        return findContainingDialog(resource) != null;
    }

    @Contract("null->false")
    public static boolean isPageRenderingComponent(@Nullable Resource componentResource) {
        if (isComponent(componentResource)) {
            for (Resource candidate = componentResource; candidate != null; candidate = loadSuperTypeResource(candidate)) {
                Templates templates = candidate.adaptTo(Templates.class);
                if (templates != null && !templates.isEmpty() || candidate.getChild("cq:infoProviders") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static Resource loadSuperTypeResource(@NotNull Resource component) {
        final String resourceSuperType = component.getResourceSuperType();
        return resourceSuperType == null ? null : component.getResourceResolver().getResource(resourceSuperType);
    }

    public static String getTouchUiDialogUrl(String componentPath) {
        // TODO: don't use page=true, because it forces fullscreen mode
        return "/mnt/override" + componentPath + "/_cq_dialog.html?page=true";
    }

    public static String getClassicUiDialogUrl(String componentPath) {
        return CLASSIC_DIALOG_PATH_PREFIX + componentPath;
    }
}
