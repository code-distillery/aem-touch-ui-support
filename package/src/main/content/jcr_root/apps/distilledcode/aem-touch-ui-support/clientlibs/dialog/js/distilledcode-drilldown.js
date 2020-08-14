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
;(function(document, Coral, $, URITemplate) {
    'use strict';

    var registry = $(window).adaptTo('foundation-registry');
    registry.register('foundation.adapters', {
        type: 'distilledcode-reloadable',
        selector: 'coral-select[data-distilledcode-options-url]',
        adapter: function(select) {
            return {
                reload: function(values) {
                    var urlTemplate = select.dataset.distilledcodeOptionsUrl;
                    var url = URITemplate.expand(urlTemplate, values);
                    return $.ajax({
                        url: url,
                        cache: false
                    }).then(function(jsonOptions) {
                        var oldValue = select.value;
                        select.items.clear();
                        if (Array.isArray(jsonOptions)) {
                            jsonOptions.forEach(function(option) {
                                select.items.add({
                                    value: option.value,
                                    selected: oldValue === option.value,
                                    content: {
                                      textContent: option.text
                                    }
                                });
                            });
                        }
                    });
                }
            };
        }
    });

    function reload(el) {
        var reloadable = $(el).adaptTo('distilledcode-reloadable');
        if (reloadable) {
            var drilldownEls = $.makeArray(el.closest('form').querySelectorAll('[data-distilledcode-drilldown-id]'))
            var values = collectValues(drilldownEls);
            reloadable.reload(values).then(function() {
                el.trigger('distilledcode-drilldown:reload');
            });
        } else {
            throw 'Cannot adapt ' + el + ' to \'distilledcode-reloadable\'';
        }
    }

    function collectValues(drilldownElements) {
        return drilldownElements.reduce(
            function(acc, el) {
                if (el.value) {
                    acc[el.dataset.distilledcodeDrilldownId] = el.value;
                }
                return acc;
            },
            Object.create(null)
        );
    }

    function updateFn(el) {
        return function(event) {
            reload(el);
        };
    }

    function initializeDependencies(form) {
        var drilldownElementsById = $.makeArray(form.querySelectorAll('[data-distilledcode-drilldown-id]')).reduce(
            function(acc, el) {
                return acc.set(el.dataset.distilledcodeDrilldownId, el);
            },
            new Map
        );

        form.querySelectorAll('[data-distilledcode-drilldown-dependency]').forEach(function(dependant) {
            var rawDependency = dependant.dataset.distilledcodeDrilldownDependency;
            if (!!rawDependency) {
                var dependencies = rawDependency.split(',');
                dependencies.forEach(function(dep) {
                    var dependencyEl = drilldownElementsById.get(dep.trim());
                    $(dependencyEl).on(
                        'change distilledcode-drilldown:reload',
                        Granite.UI.Foundation.Utils.debounce(updateFn(dependant), 50));
                });
            }
        });
    }

    function loadSelectOptions(form) {
        form
            .querySelectorAll('[data-distilledcode-drilldown-id]:not([data-distilledcode-drilldown-dependency])')
            .forEach(function(el) {
                updateFn(el)();
            });
    }

    function initialize() {
        this.querySelectorAll('form').forEach(function(form) {
            initializeDependencies(form);
            loadSelectOptions(form);
        });
    }

    Coral.commons.ready(function() {
        $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
        initialize.call(document);
    });

})(document, Coral, jQuery, Granite.URITemplate);