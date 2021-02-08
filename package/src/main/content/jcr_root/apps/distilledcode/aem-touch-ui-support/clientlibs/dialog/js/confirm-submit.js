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
;(function(window, document, $, Granite) {
    'use strict';

    var ui = $(window).adaptTo('foundation-ui');
    
    var registry = $(window).adaptTo('foundation-registry');
    registry.register('foundation.form.submit', {
        selector: 'form.foundation-form[data-foundation-form-ajax=true]',
        handler: function(form) {
            var $form = $(form);
            var submitter = $form.data('distilledcode-confirm-submitter');
            $form.removeData('distilledcode-confirm-submitter');

            var deferred =$.Deferred();
            if (submitter) {
                var $submitter = $(submitter);
                var msg = $submitter.attr('data-distilledcode-confirm-message');

                // temporarily hide foundation-form loading mask, as it covers the prompt
                var loadingMask = $(form).data('foundationForm.internal.currentLoadingMask');
                var noop = function() {};
                var loadingMaskCtrl = loadingMask && loadingMask.impl ? loadingMask.impl : { hide: noop, show: noop };
                loadingMaskCtrl.hide();

                var title = Granite.I18n.get($submitter.attr('data-distilledcode-confirm-title') || $submitter.text());
                var yesLabel = Granite.I18n.get($submitter.attr('data-distilledcode-confirm-label-yes') || 'Yes');
                var noLabel = Granite.I18n.get($submitter.attr('data-distilledcode-confirm-label-no') || 'No');

                ui.prompt(title,
                    Granite.I18n.get(msg),
                    'default',
                    [{
                        text: noLabel,
                        handler: function() {
                            loadingMask.impl.show();
                            deferred.reject();
                        }
                    }, {
                        text: yesLabel,
                        primary: true,
                        handler: function() {
                            loadingMask.impl.show();
                            deferred.resolve();
                        }
                    }]);
            } else {
                deferred.resolve();
            }

            return {
                preResult: deferred.promise()
            };
        }
    });

    $(document).on('click',
        'form button[data-distilledcode-confirm-message],' +
        'button[form][data-distilledcode-confirm-message]',
        function(event) {
            var submitter = event.currentTarget;
            $(submitter.form).data('distilledcode-confirm-submitter', submitter);
        }
    );

})(window, document, jQuery, Granite);

