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
(function (channel, window, undefined) {

    channel.on('cq-editor-loaded', _ => {
        var name = 'Images';
        var ui = window.Granite.author.ui;
        var AssetDragAndDrop = ui.assetFinder.AssetDragAndDrop;

        var originalHandleDragStart = AssetDragAndDrop.prototype.handleDragStart;
        AssetDragAndDrop.prototype.handleDragStart = function (event) {
            originalHandleDragStart.apply(this, arguments);
            $('.cq-Overlay-subdroptarget.cq-droptarget').each((function(i, target) {
                var $target = $(target);
                var fakeEvent = $.extend(Object.create(null), event, {
                    target: target
                });
                var targetEditable = ui.dropController.getEventTargetEditable(fakeEvent);
                $.extend(fakeEvent, {
                    currentDropTarget: {
                        path: targetEditable.path,
                        dom: target,
                        insertBehavior: targetEditable.config.insertBehavior ?
                            targetEditable.config.insertBehavior.split(' ')[1] : null,
                        targetEditable: targetEditable
                    }
                });

                $(target).toggleClass('distilledcode-mimetype-is-allowed', this.isInsertAllowed(fakeEvent));
            }).bind(this));
        };

        var originalHandleDragEnd = AssetDragAndDrop.prototype.handleDragEnd;
        AssetDragAndDrop.prototype.handleDragEnd = function (event) {
            originalHandleDragEnd.apply(this, arguments);
            $('.cq-Overlay-subdroptarget.cq-droptarget').removeClass('distilledcode-mimetype-is-allowed');
        };
    });
}(jQuery(document), this));