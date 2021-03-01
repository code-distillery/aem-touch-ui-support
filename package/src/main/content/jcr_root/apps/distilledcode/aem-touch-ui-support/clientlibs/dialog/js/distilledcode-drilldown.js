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
        selector: 'select[data-distilledcode-options-url]',
        adapter: function() {
            return {
                updateOptions: function updateOptions(jsonOptions) {
                    var select = this;
                    var oldValue = select.value;
                    $(select).empty();
                    if (Array.isArray(jsonOptions)) {
                        var options = document.createDocumentFragment();
                        jsonOptions.forEach(function(option) {
                            var optEl = document.createElement('option');
                            optEl.innerText = option.text;
                            optEl.value = option.value;
                            optEl.selected = oldValue === option.value;
                            options.appendChild(optEl);
                        });
                        select.appendChild(options);
                    }
                }
            };
        }
    });

    registry.register('foundation.adapters', {
        type: 'distilledcode-reloadable',
        selector: 'coral-select[data-distilledcode-options-url]',
        adapter: function() {
            return {
                updateOptions: function updateOptions(jsonOptions) {
                    var select = this;
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
                }
            };
        }
    });

    function reload(el) {
        var $el = $(el);
        var reloadable = $el.adaptTo('distilledcode-reloadable');
        if (reloadable) {
            var drilldownEls = [...el.closest('form').querySelectorAll('[data-distilledcode-drilldown-id]')];
            var values = collectValues(drilldownEls);
            var urlTemplate = el.dataset.distilledcodeOptionsUrl;
            var url = URITemplate.expand(urlTemplate, values);
            if ($el.data('distilledcode-reloadable-internal-current-options-url') !== url) {
                $el.data('distilledcode-reloadable-internal-current-options-url', url);
                return $.ajax({
                    url: url,
                    cache: false
                })
                .then(reloadable.updateOptions.bind(el))
                .then(_ => $(el).trigger('distilledcode-drilldown:reloaded'));
            }
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

    function updateDependants(event) {
        event.stopPropagation();
        var dependants = findDependants(event.target);
        dependants.forEach(dependant => reload(dependant));
    }

    function findDependants(el) {
        var id = el.dataset.distilledcodeDrilldownId;
        return document.querySelectorAll('[data-distilledcode-drilldown-dependency~=' + id + ']');
    }

    function initializeDependencies(form) {
        form
            .querySelectorAll('[data-distilledcode-drilldown-id]')
            .forEach(el => {
                var $el = $(el);
                if (!$el.data('distilledcode-drilldown-initialized')) {
                    $el
                        .data('distilledcode-drilldown-initialized', true)
                        .on('change distilledcode-drilldown:reloaded', updateDependants);
                }
            });
    }

    function loadSelectOptions(form) {
        form
            .querySelectorAll('[data-distilledcode-drilldown-id]:not([data-distilledcode-drilldown-dependency])')
            .forEach(el => reload(el));
    }

    function initialize(event) {
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