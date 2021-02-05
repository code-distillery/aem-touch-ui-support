/*
 *  Copyright 2020 Code Distillery GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
;(function (window, document, $) {
    'use strict';

    function removeDynamicHiddenParams() {
        var $form = $(this);
        $form.find('input[type=hidden][data-distilledcode-is-dynamic-hidden-param=true]').remove();
    }

    Coral.commons.ready(function () {
        $(document).on('click',
            'form button[data-distilledcode-dynamic-hidden-param-names][data-distilledcode-dynamic-hidden-param-values],' +
            'button[form][data-distilledcode-dynamic-hidden-param-names][data-distilledcode-dynamic-hidden-param-values]',
            function (event) {
                var $button = $(this);
                var paramNames = $button.data('distilledcode-dynamic-hidden-param-names').split(',');
                var paramValues = $button.data('distilledcode-dynamic-hidden-param-values').toString().split(',');
                if (paramNames.length !== paramValues.length) {
                    throw "distilledcode-dynamic-hidden-param has an unequal number of names and values";
                }

                var formId = $button.attr('form');
                var $form;
                if (formId) {
                    $form = $('#' + formId);
                } else {
                    $form = $($button.closest('form'));
                }

                if ($form.length === 0) {
                    if (formId) {
                        throw "button if has no valid form id: " + formId;
                    } else {
                        throw "button is not within a form";
                    }
                }

                for (var i = 0; i < paramNames.length; i++) {
                    var $input = $form.find('[name="' + paramNames[i] + '"]');
                    if ($input.length === 0) {
                        $input = $('<input/>').attr({
                            'type': 'hidden',
                            'name': paramNames[i],
                            'data-distilledcode-is-dynamic-hidden-param': true
                        });
                        $form.append($input);
                    }
                    $input.val(paramValues[i]);
                }

                // after the form is submitted, remove dynamic hidden params in case the form is reused
                $form.one('foundation-form-submitted', removeDynamicHiddenParams);
            }
        );
    });
})(window, document, Granite.$, Coral);
