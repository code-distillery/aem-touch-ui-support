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

    function initializeSubLabels(e) {
        $(e.target || document).find('.distilledcode-padded-dialog-content').each(function(_, content) {
            var $content = $(content);
            $content.find('[data-distilledcode-compat-sublabel]').each(function (i, el) {
                var $el = $(el);
                var subtitle = $(el).attr('data-distilledcode-compat-sublabel');
                var i18nSubtitle = Granite.I18n.getVar(subtitle);
                if (i18nSubtitle.trim()) {
                    // strip out any attributes starting with 'on' to prevent event-handlers
                    // from executing arbitrary scripts
                    // regexp inspired by https://stackoverflow.com/questions/317053/regular-expression-for-extracting-tag-attributes/319378#319378
                    var safeText = i18nSubtitle.replace(/\s(on[^=\s]+\s*=\s*(?:(['"])(.*?)\2|([^>\s'"]+)))/, '');
                    var newChildren = $.parseHTML(safeText);

                    var $label = $el.closest('.coral-Form-fieldwrapper').find('label.coral-Form-fieldlabel').first()
                    if ($label.length === 0) {
                        $label = $el.find('label').first();
                    }
                    $('<span/>', {
                        class: 'distilledcode-compat-sublabel',
                        html: newChildren
                    }).appendTo($label);
                }
            });
        });
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initializeSubLabels);
    $(initializeSubLabels);

})(document, jQuery, Granite);

