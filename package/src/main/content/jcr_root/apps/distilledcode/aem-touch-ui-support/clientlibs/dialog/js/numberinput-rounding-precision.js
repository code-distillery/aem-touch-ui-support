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
;(function(document, $, Granite) {
    'use strict';

    var truncationMultiplier = Math.round(Math.pow(10, 9))

    // cleanup doubles like 0.15000000000000002 to 0.15 by truncating them after 9 digits
    function truncateValue(value) {
        return Math.round(value * truncationMultiplier) / truncationMultiplier;
    }

    function roundToPrecision(value, precision) {
        var x = value + (precision / 2);
        return truncateValue(x - (x % precision));
    }

    function fixRounding(e) {
        var numberinput = e.target;
        var step = numberinput.step;
        var value = numberinput.valueAsNumber;
        var precision = numberinput.dataset.distilledcodeNumberinputRoundingPrecision || step;
        if (value) {
            var roundedValue = roundToPrecision(value, precision);
            if (value !== roundedValue) {
                numberinput.valueAsNumber = roundedValue;
            }
        }
    }

    $(document).on('change', 'coral-numberinput[step]',
        Granite.UI.Foundation.Utils.debounce(fixRounding, 50, true));

})(document, jQuery, Granite);