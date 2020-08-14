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
;(function(window, document, $) {
    'use strict';

    var modeAttribute = 'data-distilledcode-mode-change-mode'
    var groupAttribute = 'data-distilledcode-mode-change-group'
    var triggerSelector = '.distilledcode-mode-change-field:-foundation-submittable[' + groupAttribute + ']';

    function toggleForSwitcher(switcher, what) {
        var $switcher = $(switcher);
        var field = $switcher.adaptTo('foundation-field');
        if (field) {
            var group = $switcher.attr(groupAttribute);
            // use requestAnimationFrame because field.getValue() can be (e.g. for radio group)
            // in an intermittent state, where it has multiple values. requestAnimationFrame
            // allows this to settle
            window.requestAnimationFrame(function() {
                var mode = field.getValue();
                $switcher.trigger('foundation-mode-change', [mode, group]);
            });
        }
    }

    $(document).on('change', triggerSelector, function(e) {
        toggleForSwitcher(e.currentTarget, true);
    });

    $(document).on('foundation-mode-change', function(e, mode, group) {
        $('.foundation-toggleable[' + modeAttribute + '][' + groupAttribute + '=' + group + ']').each(function(idx, el) {
            var $el = $(el);
            var elMode = $el.attr(modeAttribute);
            var t = $el.adaptTo('foundation-toggleable');
            if (mode === elMode) {
                t.show();
            } else {
                t.hide();
            }
        });
    });

    function initialize() {
        $(triggerSelector).each(function(idx, switcher) {
            toggleForSwitcher(switcher, false);
        });
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
    $(initialize);

})(window, document, jQuery);

