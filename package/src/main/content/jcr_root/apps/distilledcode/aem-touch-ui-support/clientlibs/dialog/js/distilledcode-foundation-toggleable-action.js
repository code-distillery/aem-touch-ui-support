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

    $(window).adaptTo('foundation-registry').register('foundation.adapters', {
        type: 'foundation-toggleable',
        selector:
            '.foundation-toggleable:-foundation-submittable[data-distilledcode-foundation-toggleable-action="disabled"],' +
            '.foundation-toggleable:-foundation-submittable[data-distilledcode-foundation-toggleable-action="!disabled"]',
        adapter: function(el) {
            var $el = $(el);
            var toggleable = $el.adaptTo('foundation-field');
            var showState = $el.prop('data-distilledcode-foundation-toggleable-action') === 'disabled'
            return {
                isOpen: function() {
                    return toggleable.isDisabled() === showState;
                },
                show: function(anchor) {
                    toggleable.setDisabled(showState);
                    $el.trigger('foundation-toggleable-show');
                },
                hide: function() {
                    toggleable.setDisabled(!showState);
                    $el.trigger('foundation-toggleable-hide');
                }
            };
        }
    });

})(window, jQuery);

