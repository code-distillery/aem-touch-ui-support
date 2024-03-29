/*
 *  Copyright 2020 Code Distillery GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
;(function(document, $) {
    'use strict';

    // set the dialog's sling:resourceType input field to the value of the last other input field
    // defining sling:resourceType (if present) and delete all except the dialog's sling:resourceType
    // input field
    function fixSlingResourceTypeFields(e) {
        $(e.target || document).find('coral-dialog-content').each(function(_, content) {
            var $content = $(content);
            var $dialogRT = $content.children('[name="./sling:resourceType"]');
            if ($dialogRT.length > 0) {
                $content.find('* [name="./sling:resourceType"]').each(function(_, overrideRT) {
                    var $overrideRT = $(overrideRT);
                    $dialogRT.val($overrideRT.val());
                    $overrideRT.remove();
                });
            }
        });
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', fixSlingResourceTypeFields);
    $(fixSlingResourceTypeFields);

})(document, jQuery);

