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

import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * By default the image-editor uses AEM's web rendition as the
 * reference image, i.e. the image that all absolute coordinates,
 * as used e.g. by cropping and image-maps, refer to. Out-of-the-box
 * this is an image resized to fit into a bounding box of 1280x1280.
 * <br>
 * An {@code ImageEditorPathProvider} allows providing an alternative
 * path for the reference image, similar to what could be achieved
 * with AEM's Ext.JS-based 'html5smartimage' widget, if a javascript
 * function was supplied via its 'pathProvider' config property.
 */
@ConsumerType
public interface AssetReferencePathProvider {

    /**
     * The name of the fileReference property.
     */
    String PN_FILE_REFERENCE = "fileReference";

    /**
     * Computes the reference image path given the content path and properties
     * of the image resource, i.e. the resource holding the {@code fileReference}
     * property.
     * <br>
     * Implementations can opt-out of handling a given resource by returning
     * {@code null}. Multiple {@code ImageEditorPathProvider} services are
     * called in descending order of their service-ranking property values,
     * and the first non-null return value is used without calling any further
     * services.
     *
     * @param contentPath the path of the resource being edited
     * @param properties the image resource's properties
     * @return the request path for the reference image or {@code null}
     */
    @Nullable String getReferenceImagePath(@Nullable String contentPath, @NotNull ValueMap properties);
}
