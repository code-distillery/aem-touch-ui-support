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
import com.day.cq.commons.date.RelativeTimeFormat;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.PrefixRenditionPicker;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.models.annotations.Model;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;

@Model(adaptables = SlingHttpServletRequest.class)
public class ColumnPreviewAsset {

    private final SlingHttpServletRequest request;

    private final ResourceResolver resourceResolver;

    private final @Nullable ExpressionHelper expressionHelper;
    
    private final Resource contentResource;

    @Inject
    public ColumnPreviewAsset(SlingHttpServletRequest request) {
        this.request = request;
        this.resourceResolver = request.getResourceResolver();
        this.expressionHelper = CoralUtil.getExpressionHelper(request);

        final String expression = request.getResource().getValueMap().get("path", String.class);
        final String path = expressionHelper != null ? expressionHelper.get(expression, String.class) : expression;
        this.contentResource = path != null ? resourceResolver.getResource(path) : null;
    }

    @Nullable
    public String getThumbnailHref() {
        return withAsset()
            .map(asset -> getThumbnailPath(asset) + "?" + getCacheKiller(asset))
            .orElse(null);
    }

    private String getCacheKiller(Asset asset) {
        final long lastModified = asset.getLastModified() / 1000 * 1000;
        return "ch_ck=" + lastModified;
    }

    @NotNull
    private String getThumbnailPath(Asset asset) {
        return asset.getRendition(new PrefixRenditionPicker("cq5dam.thumbnail.319.319")).getPath();
    }

    @Nullable
    public String getTitle() {
        return withAsset()
                .map(asset -> new ValueMapDecorator(asset.getMetadata()))
                .map(vm -> vm.get("dc:title", String.class))
                .orElse(null);
    }

    @Nullable
    public String getLastModifiedRelative() {
        return Optional.ofNullable(getLastModified())
                .map(this::toRelativeDate)
                .orElse(null);
    }

    @Nullable
    public Long getLastModified() {
        return withAsset()
                .map(Asset::getLastModified)
                .orElse(null);
    }

    @Nullable
    public String getMimeType() {
        return withAsset()
                .map(Asset::getMimeType)
                .orElse(null);
    }

    public boolean isPublished() {
        return Optional.ofNullable(contentResource)
                .map(assetRes -> assetRes.getChild("jcr:content"))
                .map(Resource::getValueMap)
                .map(props -> props.get("cq:lastReplicationAction", String.class))
                .map(lastAction -> Objects.equals("Activate", lastAction))
                .orElse(false);
    }

    private Optional<Asset> withAsset() {
        return Optional.ofNullable(contentResource)
                .map(res -> res.adaptTo(Asset.class));
    }

    private String toRelativeDate(long timeMillis) {
        RelativeTimeFormat rtf = new RelativeTimeFormat("r", request.getResourceBundle(request.getLocale()));
        return rtf.format(timeMillis, true);
    }
}
