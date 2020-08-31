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
package net.distilledcode.aem.ui.touch.support.spi;

import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.annotation.versioning.ProviderType;

import java.util.Collection;

@ProviderType
public interface DialogConverter {

    Collection<Result> convert(ResourceResolver resourceResolver, Collection<String> paths, boolean force);

    class Result {

        private final String path;
        
        private final String formattedErrorMessage;

        public Result(String path, String formattedErrorMessage) {
            this.path = path;
            this.formattedErrorMessage = formattedErrorMessage;
        }

        public boolean isSuccess() {
            return formattedErrorMessage == null;
        }

        public String getPath() {
            return path;
        }

        public String getError() {
            return formattedErrorMessage;
        }
    }
}
