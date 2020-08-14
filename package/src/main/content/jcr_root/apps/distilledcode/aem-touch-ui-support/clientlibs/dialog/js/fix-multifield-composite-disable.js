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
;(function(window, $) {
    'use strict';

    function reflectDisabledState(mutations, observer) {
        mutations.forEach(mutation => {
            if (mutation.type === 'attributes' && mutation.attributeName === 'disabled') {
                var $button = $(mutation.target);
                var isDisabled = $button.attr('disabled') === 'disabled';
                var submittables = $button
                    .closest('coral-multifield')
                    .find(':-foundation-submittable, .coral-Multifield-remove, .coral-Multifield-move');
                submittables.each((idx, el) => {
                    var field = $(el).adaptTo('foundation-field');
                    if (field) { // first try if .adaptTo('foundation-field') worked
                        if (field.isDisabled() !== isDisabled) {
                            field.setDisabled(isDisabled); // and set the 'disabled' state if necessary
                        }
                    } else if (el.disabled !== undefined) { // then check if the element has a disabled property
                        if (el.disabled !== isDisabled) {   // and toggle the 'disabled' attribute if necessary
                            el.toggleAttribute('disabled', isDisabled);
                        }
                    }
                });
            }
        });
    }

    var observer = new MutationObserver(reflectDisabledState);

    function initialize() {
        // we leverage the fact that the coral-multifield-add button is
        // correctly disabled and then observe its disabled attribute
        // to re-apply its disabled state to all fields in the composite
        // multifield
        $('button[coral-multifield-add]').each(function(idx, button) {
            observer.observe(button, { attributes: true, attributeFilter: ['disabled'] });
        });
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
    $(initialize);

})(window, jQuery);