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

    var attrField = 'distilledcode-derived-field';
    var attrValues = 'distilledcode-derived-values';
    var attrDefault = 'distilledcode-derived-default';
    var selField = `[data-${attrField}][data-${attrDefault}]`;

    function deriveField(derivedField) {
        var $derivedField = $(derivedField);
        var fieldName = $derivedField.data(attrField);
        var fieldValue = $derivedField.closest('form').find(`[name="${fieldName}"]`).val();

        var valueMapping = $derivedField.data(attrValues) || {};
        var defaultValue = $derivedField.data(attrDefault)
        if (valueMapping.hasOwnProperty(fieldValue)) {
            var value = valueMapping[fieldValue];
            $derivedField.val(value);
        } else if (defaultValue) {
            $derivedField.val(defaultValue);
        }
    }

    var initialize = function initialize() {
        var derivedFields = $.makeArray($(selField));
        derivedFields.forEach(derivedField => {
            var $derivedField = $(derivedField);
            var fieldName = $derivedField.data(attrField);
            $derivedField
                .closest('form')
                .on('change', `[name="${fieldName}"]`, function() {
                    deriveField(derivedField);
                });
        });

        derivedFields.forEach(deriveField);
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
    $(initialize);

})(document, jQuery);

