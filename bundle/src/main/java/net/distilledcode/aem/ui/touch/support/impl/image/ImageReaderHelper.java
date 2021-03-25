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
package net.distilledcode.aem.ui.touch.support.impl.image;

import org.apache.sling.api.resource.Resource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

public class ImageReaderHelper {

    private final Supplier<InputStream> imageSupplier;

    public static ImageReaderHelper from(final Supplier<InputStream> imageSuplier) {
        return new ImageReaderHelper(imageSuplier);
    }

    private ImageReaderHelper(Supplier<InputStream> imageSupplier) {
        this.imageSupplier = imageSupplier;
    }

    public static InputStream getRenditionInputStream(final Resource rendition) {
        return rendition.adaptTo(InputStream.class);
    }

    public static Dimension getImageDimensions(final ImageReader reader) throws IOException {
        return new Dimension(reader.getWidth(0), reader.getHeight(0));
    }

    public <R> Optional<R> withImageReader(ThrowingFunction<ImageReader, R, IOException> action) throws IOException {
        try (final ImageInputStream imageInputStream = ImageIO.createImageInputStream(imageSupplier.get())) {
            final ImageReader imageReader = getImageReader(imageInputStream);
            if (imageReader != null) {
                final R result = action.apply(imageReader);
                return Optional.ofNullable(result);
            }
            return Optional.empty();
        }
    }

    private static ImageReader getImageReader(ImageInputStream imageInputStream) {
        final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            // TODO: detect and blacklist non conforming implementations automatically on first use
            // skip reader that caches entire image on #getWidth(0), preventing ImageReadParams to take effect
            if (readers.hasNext() && "ch.randelshofer.media.jpeg.CMYKJPEGImageReader".equals(reader.getClass().getName())) {
                continue;
            }

            reader.setInput(imageInputStream);
            return reader;
        }
        return null;
    }

    public interface ThrowingFunction<S, R, T extends Exception> {
        R apply(final S s) throws T;
    }
}
