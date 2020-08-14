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
;(function(window, $, Coral) {
    'use strict';

    function byProperty(name, value) {
        return function(obj) {
            return obj[name] === value;
        }
    }

    function getDynamicDefaultFunction(name) {
        var config = $(window)
            .adaptTo('foundation-registry')
            .get('distilledcode.dynamic.default.values')
            .find(byProperty('name', name));
        return !!config && config.handler;
    }

    Coral.commons.ready(function() {
        $('[data-distilledcode-dynamic-default-value]').each(function(idx, el) {
            var $el = $(el);
            var field = $el.adaptTo('foundation-field');
            if (!field.getValue()) {
                var funcName = $el.attr('data-distilledcode-dynamic-default-value');
                if (!!funcName && !!getDynamicDefaultFunction(funcName)) {
                    var func = getDynamicDefaultFunction(funcName);
                    var defaultValue = func($el);
                    if (defaultValue !== undefined) {
                        field.setValue(defaultValue);
                    }
                } else {
                    console.warn('Could not find dynamic default value function named', funcName, 'for', el);
                }
            }
        });
    });
})(window, jQuery, Coral);

