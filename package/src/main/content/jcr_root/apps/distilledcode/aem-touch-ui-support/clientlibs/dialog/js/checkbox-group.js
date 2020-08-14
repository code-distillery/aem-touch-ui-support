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
(function(window, document, $, Granite) {
    'use strict';

    var selector = '.coral-Form-field.distilledcode-checkbox-group';
    var registry = $(window).adaptTo('foundation-registry');
    
    registry.register('foundation.validation.selector', {
        submittable: selector,
        candidate: '.coral-Form-field.distilledcode-checkbox-group:not([aria-disabled=true])'
    });
    
    registry.register('foundation.validation.validator', {
        selector: selector + '[required]',
        validate: function(el) {
            var $el = $(el);
            var checkedCheckboxes = $.grep($el.children('coral-checkbox'), function(cb) {
                return cb.checked;
            });
            var isValid = checkedCheckboxes.length > 0;
            if (!isValid) {
                var msg = $el.data('required-msg')
                if (msg) {
                    return Granite.I18n.getVar(msg)
                } else {
                    return Granite.I18n.get('Select at least one checkbox');
                }
            }
        }
    });

    $(document).on('change', selector + ' > coral-checkbox', function() {
        var api = $(this).parent().adaptTo("foundation-validation");
        if (api) {
            api.checkValidity();
            api.updateUI();
        }
    });
})(window, document, Granite.$, Granite);