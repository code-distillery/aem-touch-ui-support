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

    var ASYNC_VALIDATION_CACHE = 'distilledcode.internal.validator.async.cache';
    var ASYNC_INTERNAL_SUBMIT = 'distilledcode.internal.validator.async.submit';
    var ASYNC_VALIDATION_ICON = 'distilledcode.internal.validator.async.icon';
    var ASYNC_VALIDATION_CONCURRENT = 'distilledcode.internal.validator.async.concurrent.counter';
    var VALIDATION_READY = 'distilledcode-validator-helper-ready';

    var registry = $(window).adaptTo('foundation-registry');

    // adapted from https://werxltd.com/wp/2010/05/13/javascript-implementation-of-javas-string-hashcode-method/
    function hashCode(str) {
        var hash = 0;
        if (str.length == 0) {
            return hash;
        }
        for (var i = 0; i < str.length; i++) {
            var char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return hash;
    }

    function getCachedPromise($field, value) {
        var cache = $field.data(ASYNC_VALIDATION_CACHE) || {};
        var hash = hashCode(value);
        if (cache.hasOwnProperty(hash)) {
            var entry = cache[hash];
            if (entry.value === value) {
                return entry.promise;
            }
        }
        return null;
    }

    function putCachedPromise($field, value, promise) {
        var cache = $field.data(ASYNC_VALIDATION_CACHE);
        if (!cache) {
            cache = {};
            $field.data(ASYNC_VALIDATION_CACHE, cache);
        }
        var hash = hashCode(value);
        cache[hash] = {
            value: value,
            promise: promise
        };
    }

    var asyncValidationUI = {
        show: function show($field) {
            var counter = $field.data(ASYNC_VALIDATION_CONCURRENT) || 0;
            $field.data(ASYNC_VALIDATION_CONCURRENT, counter + 1);
            if (counter === 0) {
                $field.nextAll('.coral-Form-fieldinfo')
                    .not('.distilledcode-async-validation-in-progress')
                    .addClass('distilledcode-async-validation-hidden');
                var $icon = $field.data(ASYNC_VALIDATION_ICON);
                if (!$icon) {
                    $icon = $('<coral-icon icon="gear" size="S"></coral-icon>')
                        .addClass('coral-Form-fieldinfo distilledcode-async-validation-in-progress');
                    $field.data(ASYNC_VALIDATION_ICON, $icon);
                }
                if ($icon.parent().length === 0) {
                    $field.after($icon);
                }
            }
        },
        hide: function hide($field) {
            var counter = $field.data(ASYNC_VALIDATION_CONCURRENT) || 1;
            $field.data(ASYNC_VALIDATION_CONCURRENT, counter - 1);
            if (counter === 1) {
                $field.nextAll('coral-icon.distilledcode-async-validation-in-progress').detach();
                $field.nextAll('.coral-Form-fieldinfo')
                    .not('.distilledcode-async-validation-in-progress')
                    .removeClass('distilledcode-async-validation-hidden');
            }
        }
    };

    // validationFn must return a Thenable
    function registerAsyncValidator(name, validationFn, defaultErrorMsg) {
        var i18nDefaultErrorMsg = Granite.I18n.get(defaultErrorMsg);
        var fieldSelector = "[data-foundation-validation~='" + name + "']";
        registry.register('foundation.validation.validator', {
            selector: fieldSelector,
            validate: function(field) {
                var $field = $(field);
                var value = getValue(field);
                if (value === '') {
                    return;
                }
                
                var msgName = name.split('.').pop();
                var errorMsg = $field.attr('data-distilledcode-validation-message-' + msgName)
                    || $field.attr('data-distilledcode-validation-message');
                var i18nErrorMsg = !!errorMsg ? Granite.I18n.get(errorMsg) : i18nDefaultErrorMsg;

                var promise = getCachedPromise($field, value);
                if (!promise) {
                    asyncValidationUI.show($field);
                    promise = $.when(validationFn(field, value)).always(function() {
                        asyncValidationUI.hide($field);
                    }).promise();
                    putCachedPromise($field, value, promise);
                }
                promise.then(function isValid() {
                    return value;
                }, function isInvalid(errorMsg) {
                    if (value === getValue(field)) { // in case the value has changed
                        var msg = !!errorMsg ? Granite.I18n.get(errorMsg) : i18nErrorMsg;
                        var validation = $field.adaptTo('foundation-validation');
                        validation.setCustomValidity(msg);
                        validation.updateUI();
                    }
                });
            }
        });

        var resetAsyncValidation = Granite.UI.Foundation.Utils.debounce(function resetAsyncValidation(e) {
            var field = e.target;
            var $field = $(field);
            var validation = $field.adaptTo('foundation-validation');
            validation.setCustomValidity('');
            validation.checkValidity();
            validation.updateUI();
        }, 500)
        $(document).on('change', fieldSelector, resetAsyncValidation);
        $(document).on('input', fieldSelector, resetAsyncValidation);
    }

    function initializeAjaxValidationSupport() {
        // support foundation.form.submit also when data-foundation-form-ajax is not set or false
        $(document).on('submit', 'form.foundation-form', function asyncValidationSubmitHandler(e) {
            var form = e.target;
            var $form = $(form);

            if (!$form.data('foundationFormAjax') && !$form.data(ASYNC_INTERNAL_SUBMIT)) {
                if (!$form.data('foundationFormDisable')) {
                    var prePromises = registry.get('foundation.form.submit')
                        .filter(function(item) { return $form.is(item.selector); })
                        .map(function(item) { return item.handler(form); })
                        .filter(function(result) { return !!result.preResult; })
                        .reduce(function(all, result) {
                            return all.concat(result.preResult);
                        }, []);

                    if (prePromises.length > 0) {
                        var ui = $(window).adaptTo('foundation-ui');
                        ui.wait($form);
                        e.preventDefault();

                        $.when.apply($, prePromises)
                            .always(ui.clearWait)
                            .then(function allResolved(value) {
                                $form.data(ASYNC_INTERNAL_SUBMIT, true)
                                $form.submit();
                            }, function someRejected(reason) {
                                return reason;
                            });
                    }
                }
            } else {
                $form.removeData(ASYNC_INTERNAL_SUBMIT);
            }
        });

        registry.register('foundation.form.submit', {
            selector: '*',
            handler: function(form) {
                var promises = $(form).find(':-foundation-submittable')
                    .map(function(i, field) {
                        var $field = $(field);
                        var value = getValue($field);
                        return !value ? undefined : getCachedPromise($field, value);
                    })
                    .filter(function(i, promise) {
                        return !!promise;
                    });
                return {
                    preResult: $.when.apply($, promises).promise()
                };
            }
        });

        registry.register('foundation.adapters', {
            type: 'distilledcode.validation.validator.helper',
            selector: $(window),
            adapter: function() {
                return {
                    registerValidator: registerValidator,
                    registerAsyncValidator: registerAsyncValidator,
                    getValue: getValue
                };
            }
        });

        $(document).trigger(VALIDATION_READY, [$(window).adaptTo('distilledcode.validation.validator.helper')]);
    }

    function registerValidator(name, validationFn, defaultErrorMsg) {
        var i18nDefaultErrorMsg = Granite.I18n.get(defaultErrorMsg);
        registry.register('foundation.validation.validator', {
            selector: "[data-foundation-validation~='" + name + "']",
            validate: function(field) {
                var msgName = name.split('.').pop();
                var errorMsg = $(field).attr('data-distilledcode-validation-message-' + msgName)
                    || $(field).attr('data-distilledcode-validation-message');
                var i18nErrorMsg = !!errorMsg ? Granite.I18n.get(errorMsg) : i18nDefaultErrorMsg;
                var value = getValue(field);
                return validationFn(field, value) ? undefined : i18nErrorMsg;
            }
        });
    }

    function getValue(field) {
        var fField = $(field).adaptTo('foundation-field');
        return !!fField && fField.getValue !== undefined ? fField.getValue() : field.value;
    }

    function regexpValidationFn(regexp) {
        return function(field, value) {
            return regexp.test(value);
        }
    }

    registerValidator(
        'distilledcode.cq5.compat.alpha',
        regexpValidationFn(/^[a-zA-Z_]+$/),
        'This field should only contain letters and _'
    );

    registerValidator(
        'distilledcode.cq5.compat.alphanum',
        regexpValidationFn(/^[a-zA-Z0-9_]+$/),
        'This field should only contain letters, numbers and _'
    );

    registerValidator(
        'distilledcode.cq5.compat.authorizableId',
        regexpValidationFn(/^[a-z0-9@_\-\.]+$/),
        'This field should only contain lowercase letters, numbers, ".", "-", "_" and "@".'
    );

    registerValidator(
        'distilledcode.cq5.compat.digits',
        regexpValidationFn(/^\d*$/),
        'This field should only contain numbers'
    );

    registerValidator(
        'distilledcode.cq5.compat.email',
        regexpValidationFn(/^(\w+)([\-+.\'][\w]+)*@(\w[\-\w]*\.){1,5}([A-Za-z]){2,6}$/),
        'This field should be an e-mail address in the format "user@domain.com"'
    );

    registerValidator(
        'distilledcode.cq5.compat.itemname',
        regexpValidationFn(/^[a-zA-Z0-9_\-]+$/),
        'This field should only contain numbers, letters, dashes and underscores'
    );

    registerValidator(
        'distilledcode.cq5.compat.name',
        regexpValidationFn(/^([^\|\[\]\*\/: \.]+|[^\|\[\]\*\/: \.]+[^\|\[\]\*\/:]*[^\|\[\]\*\/: ]+)$/),
        'The name must not contain<br><br>/ : [ ] * |<br><br>nor must it start with a space or a period<br>nor end with a space.'
    );

    registerValidator(
        'distilledcode.cq5.compat.percent',
        regexpValidationFn(/^\d.*\%?$/),
        "This field should only contain a percentage in the format '50%'"
    );

    registerValidator(
        'distilledcode.cq5.compat.url',
        regexpValidationFn(/(((^https?)|(^ftp)):\/\/([\-\w]+\.)+\w{2,3}(\/[%\-\w]+(\.\w{2,})?)*(([\w\-\.\?\\\/+@&#;`~=%!]*)(\.\w{2,})?)*\/?)/i),
        'This field should be a URL in the format "http://www.domain.com"'
    );

    function customRegexpValidation(field, value) {
        var $field = $(field);
        var regexp = $field.attr('data-distilledcode-validation-regexp')
        return new RegExp(regexp).test(value);
    }

    registerValidator(
        'distilledcode.cq5.compat.regexp',
        customRegexpValidation
    );

    function requiredOnLinkedData(field) {
        var $field = $(field);
        var linkedName = $field.attr('data-distilledcode-validation-linked-name')
        if (!linkedName) {
            // no link specified, therefore this field is valid
            return true;
        }

        var $form = $field.closest('form.cq-dialog');
        var $fileupload = $form.find('coral-fileupload[name="' + linkedName + '"]')
        return !$fileupload.hasClass('is-filled') || getValue(field) !== '';
    }

    registerValidator(
        'distilledcode.cq5.compat.requiredOnLinkedData',
        requiredOnLinkedData,
        'This field must not be empty when data in the linked field is available.'
    );

    function dependentFieldValidation(field, value) {
        var $dependentField = findDependentField($(field))
        var dependentField = $dependentField.adaptTo('foundation-field');
        if (!!dependentField) {
            var depVal = dependentField.getValue();
            var validity = !depVal || !!value;
            return validity;
        }
        return true;
    }

    function findDependentField($field) {
        var dependentFieldName = $field.attr('data-distilledcode-validation-depends-on')
        if (!dependentFieldName) {
            return undefined;
        }

        var $form = $field.closest('form.cq-dialog');
        var $dependentField = $form.find('[name="' + dependentFieldName + '"]');
        return $dependentField.length > 0 ? $dependentField : undefined;
    }

    function registerDependentFieldListeners() {
        $('[data-distilledcode-validation-depends-on]').each(function(i, field) {
            var $field = $(field);
            var $dependentField = findDependentField($field);
            $dependentField.on('change', function() {
                var validationAPI = $field.adaptTo('foundation-validation');
                if (!!validationAPI) {
                    validationAPI.checkValidity();
                    validationAPI.updateUI();
                }
            });
        });
    }

    registerValidator(
        'distilledcode.dependentField',
        dependentFieldValidation,
        'This field must not be empty when its dependency is set.'
    );

// This validator is only useful for testing of the registerAsyncValidator mechanism
//    registerAsyncValidator(
//        'distilledcode.ajax',
//        function(field, value) {
//            var $form = $(field).closest('form.cq-dialog');
//            return $.Deferred(function(deferred) {
//                setTimeout(function() {
//                    if (value === 'valid') {
//                        deferred.resolve();
//                    } else {
//                        deferred.reject('Invalid value: ' + value);
//                    }
//                }, 5000);
//            }).promise();
//        }
//    );

    function initialize() {
        registerDependentFieldListeners();
    }

    // initialize state for both dynamically loaded and static dialogs
    $(document).on('foundation-contentloaded', 'coral-dialog', initialize);
    $(initialize);
    $(initializeAjaxValidationSupport);

})(window, document, jQuery, Granite);

