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

    function getValue(switcher) {

        var $switcher = $(switcher)
        var field = $switcher.adaptTo('foundation-field');

        var value;
        if (field) {
            value = field.getValue();
        }

        // workaround: when the dialog is loaded via ajax, the 'foundation-field' implementation for
        // coral-select does not retrieve a value, so we help it along (verified to be needed in
        // AEM 6.3 and AEM 6.5, very likely needed in AEM 6.4)
        if (!value && $switcher.is('coral-select') && !!switcher.selectedItem) {
            value = switcher.selectedItem.value;
        }

        return value;
    }

    function toggleForSwitcher(switcher) {
        var container = $(switcher).closest('coral-tabview.distilledcode-conditional-tabs-container');
        if (container.length === 0) {
            return;
        }

        var value = getValue(switcher);
        var tabview = container[0];
        var tabs = tabview.tabList.items.getAll();
        var panels = tabview.panelStack.items.getAll();
        for(var i = 0; i < panels.length; i++) {
            var tab = tabs[i];
            var panel = panels[i];
            if (value.length > 0 && $(panel).has('[data-distilledcode-conditional-tabs-value=' + value + ']').length > 0) {
                tab.disabled = false;
            } else if ($(panel).has('[data-distilledcode-conditional-tabs-value]').length > 0) {
                tab.disabled = true;
            }
        }
    }

    function countChildIndex(child) {
        for (var i = 0; (child = child.previousElementSibling) != null; i++);
        return i;
    }

    function reflectDisabledState(mutations, observer) {
        mutations.forEach(mutation => {
            if (mutation.type === 'attributes' && mutation.attributeName === 'disabled') {
                var tab = mutation.target;
                var tabIndex = countChildIndex(tab);
                var $tab = $(tab);
                var isDisabled = $tab.attr('disabled') === 'disabled';
                var tabview = $tab.closest('coral-tabview')[0]
                var panels = tabview.panelStack.items.getAll();
                var $panel = $(panels[tabIndex]);
                var submittables = $panel.find(':-foundation-submittable');
                submittables.each((idx, el) => {
                    var field = $(el).adaptTo('foundation-field');
                    if (field && !!field.isDisabled) { // first try if .adaptTo('foundation-field') worked
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

        $('coral-tabview.distilledcode-conditional-tabs-container > coral-tablist > coral-tab')
            .each((idx, tab) => observer.observe(tab, { attributes: true, attributeFilter: ['disabled'] }));

        var selectors = 'coral-select.distilledcode-conditional-tab-switcher:not([multiple])'
                        + ', .coral-RadioGroup.distilledcode-conditional-tab-switcher'
                        // TODO: test with coral-buttongroup
                        // + ', coral-buttongroup.distilledcode-conditional-tab-switcher[selectionmode="single"]'
                        ;
        $(document).on('change', selectors, e => toggleForSwitcher(e.target));
        Coral.commons.ready(_ => $(selectors).each((idx, switcher) => toggleForSwitcher(switcher)));
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
    $(initialize);

})(document, jQuery, Coral);

