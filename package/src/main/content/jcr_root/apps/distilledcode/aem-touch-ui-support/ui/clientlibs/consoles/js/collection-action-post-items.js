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
;(function (window, $) {
    'use strict';

    var registry = $(window).adaptTo('foundation-registry');
    var ui = $(window).adaptTo('foundation-ui');

    function reload(collection, delay) {
        ui.wait();
        var reloadableCollection = collection.dataset.foundationCollectionSrc && $(collection).adaptTo('foundation-collection');
        var reloadFn = function reloadFn() {
            if (reloadableCollection) {
                reloadableCollection.reload();
                ui.clearWait();
            } else {
                window.location.reload();
            }
        };
        if (delay) {
            var delayMs = parseInt(delay);
            if (!isNaN(delayMs)) {
                return function delayedReload() {
                    setTimeout(function () {
                        reloadFn();
                    }, delayMs);
                }
            }
        }
        return reloadFn;
    }

    function actionHandler(el, config, collection, itemIds) {
        return function performAction() {
            var postData = {};
            postData['_charset_'] = 'utf-8';
            postData['action'] = config.data.action;
            postData[config.data.paramName] = itemIds;

            return $.ajax({
                    method: config.data.method || 'post',
                    url: config.data.href,
                    contentType: 'application/x-www-form-urlencoded; charset=UTF-8',
                    data: postData
                })
                .done(reload(collection, config.data.delay))
                .done(function(data, textStatus, jqXhr) {
                    $(el).trigger('foundation-form-submitted', {
                        status: true,
                        xhr: jqXhr
                    });
                })
                .fail(function (jqXHR, textStatus, errorThrown) {
                    var $messageObj = $(jqXHR.responseText).find('div#Message');
                    if ($messageObj.length > 0) {
                        var message = $messageObj.text();
                        ui.notify('Error', message, 'error');
                    } else {
                        ui.notify('Error', 'Failed to refresh', 'error');
                    }
                    $(el).trigger('foundation-form-submitted', {
                        status: false,
                        xhr: jqXhr
                    });
                });
        };
    }

    registry.register('foundation.collection.action.action', {
        name: 'distilledcode.collection.action.post.items',
        handler: function (name, el, config, collection, selections) {
            var ok = true;
            var itemIds = selections.map(function (item) {
                return item.dataset.foundationCollectionItemId;
            });

            var titles = selections.map(function (item) {
                return $(item).find('.foundation-collection-item-title').text()
                    || item.dataset.foundationCollectionItemId;
            });

            var handler = actionHandler(el, config, collection, itemIds);

            if (config.data.confirmText) {
                var label = $(el).find('coral-button-label').text();
                var title = config.data.confirmTitle || label;
                var text = config.data.confirmText.replace(
                    /\{titles\}/g,
                    // use $("<div/>").text(title).html() for HTML entity encoding
                    '<ul>' + titles.map(function (title) {
                        return '<li>' + $('<div/>').text(title).html() + '</li>';
                    }).join('') + '</ul>');

                var actions = [{
                    id: 'cancel',
                    text: 'Cancel'
                }, {
                    id: 'ok',
                    text: label || 'Ok',
                    primary: true,
                    handler: handler
                }];

                ui.prompt(title, text, "default", actions);
            } else {
                handler();
            }
        }
    });

})(window, jQuery);

