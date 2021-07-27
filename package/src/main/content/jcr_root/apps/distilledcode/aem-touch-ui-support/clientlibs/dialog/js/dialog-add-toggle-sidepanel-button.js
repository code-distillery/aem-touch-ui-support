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

    // Add a button with the class 'toggle-sidepanel' to the dialog. The according action is already
    // hooked-up with the CSS class.
    function addToggleSidePanelButton(e) {
        $(e.target || document).find('coral-dialog-header').each(function(_, header) {
            var $header = $(header);
            // add toggle-sidepanel button
            $('<button>', {
                attr: {
                    is: 'coral-button'
                },
                on: {
                    click: e => e.preventDefault()
                },
                class: 'cq-dialog-header-action toggle-sidepanel',
                icon: 'railLeft',
                title: Granite.I18n.get('Toggle Side Panel'),
                variant: 'minimal'
            }).insertAfter($header.find('.cq-dialog-help'));
        });
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', addToggleSidePanelButton);
    $(addToggleSidePanelButton);

})(document, jQuery);
