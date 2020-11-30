/*
 * Copyright 2020 Code Distillery GmbH
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
;(function ($, CUI) {

    // HACK - CUI.RichText handleKeyDown removes all keydown listeners from document.
    // Prevent that. Up until AEM 6.5.4 this method does not exist.
    CUI.RichText.prototype.handleKeyDown = function() {};

    var PlaceholderDialog = new Class({

        extend: CUI.rte.ui.cui.AbstractBaseDialog,

        toString: 'PlaceholderDialog',

        construct: function (editorKernel, config) {
            this.content = this._template(editorKernel, config);
        },

        getDataType: function () {
            return 'placeholder';
        },

        _template: function(editorKernel, config) {
            var fragment = document.createDocumentFragment();
            var list = $('<coral-buttonlist/>').appendTo(fragment);
            config.placeholders.forEach(def => {
                $('<button/>', {
                    html: def.label,
                    attr: {
                        is: 'coral-buttonlist-item',
                        value: def.placeholder
                    }
                }).appendTo(list);
            });

            var self = this;
            list.on('click', 'button[is=coral-buttonlist-item]', function (e) {
                var toInsert = $(e.currentTarget).attr('value');
                var dm = editorKernel.getDialogManager();
                dm.hide(self);
                editorKernel.focus();
                config.insert(toInsert);
                e.stopPropagation();
                e.preventDefault();
            });
            return fragment;
        }
    });

    var PlaceholderPlugin = new Class({

        toString: 'PlaceholderPlugin',

        extend: CUI.rte.plugins.Plugin,

        placeholderDialog: null,

        placeholders: null,
        
        placeholderToLabel: null,

        defaultConfig: {
            placeholderPrefix: '${',
            placeholderSuffix: '}',
            placeholders: {
                firstname: {
                    label: 'First name',
                    placeholder: 'firstName'
                },
                lastname: {
                    label: 'Last name',
                    placeholder: 'lastName'
                },
                salutation: {
                    label: 'Salutation',
                    placeholder: 'salutation'
                }
            },
            tooltips: {
                addplaceholder: {
                    title: 'Insert placeholder',
                    text: 'Insert placeholder'
                }
            }
        },

        config: Object.create(null),

        getFeatures: function () {
            return ['addplaceholder'];
        },

        initializeUI: function (tbGenerator) {
            var plg = CUI.rte.plugins;
            if (this.isFeatureEnabled('addplaceholder')) {
                var placeholderUI = tbGenerator.createElement('addplaceholder', this, false,
                    this.getTooltip('addplaceholder'));
                tbGenerator.addElement('placeholder', plg.Plugin.SORT_MISC, placeholderUI, 100);
                tbGenerator.registerAdditionalClasses('placeholder#addplaceholder', ['rte--trigger']);
                tbGenerator.registerIcon('placeholder#addplaceholder', 'coral-Icon coral-Icon--brackets');

                var editorKernel = this.editorKernel;
                editorKernel.addPluginListener('beforekeydown', this._handleBeforeKeyDown, this, this, false);
                editorKernel.addPluginListener('keyup', this._handleKeyUp, this, this, false);
                editorKernel.addPluginListener('commandexecuted', this._handleCommandExecuted, this, this, false);
                editorKernel.addPluginListener('beforecommandexecuted', this._handleBeforeCommandExecuted, this, this, false);
                this._setupContentInterception(editorKernel);
            }
        },

        notifyPluginConfig: function (pluginConfig) {
            pluginConfig = CUI.rte.Utils.copyObject(pluginConfig || Object.create(null));
            var defaults = CUI.rte.Utils.copyObject(this.defaultConfig);

            var rawPlaceholders = pluginConfig.placeholders || defaults.placeholders;
            delete pluginConfig.placeholders;
            delete defaults.placeholders;

            this.config = CUI.rte.Utils.applyDefaults(pluginConfig, defaults);
            
            var placeholderToLabel = Object.create(null);
            var placeholders = [];
            for (var name in rawPlaceholders) {
                if (rawPlaceholders[name].placeholder != null) {
                    var def = rawPlaceholders[name];
                    var label = Granite.I18n.get(def.label || def.placeholder)
                    placeholderToLabel[def.placeholder] = label;
                    placeholders.push({
                        label: label,
                        placeholder: this.config.placeholderPrefix + def.placeholder + this.config.placeholderSuffix
                    });
                }
            }

            this.placeholders = placeholders;
            this.placeholderToLabel = placeholderToLabel;
        },

        execute: function (id, value, options) {
            var context = options.editContext;
            if (id === 'addplaceholder') {
                this._insertPlaceholder(context);
            }
        },

        isHeadless: function (cmd, value) {
            return (cmd !== 'addplaceholder');
        },

        updateState: function(selDef) {
            // handles mouse clicks
            this._maybeSelectPlaceholderSpan(selDef.editContext, false, false);
        },

        _insertPlaceholder: function (context) {
            var dm = this.editorKernel.getDialogManager();
            if (dm.isShown(this.placeholderDialog) && dm.toggleVisibility(this.placeholderDialog)) {
                dm.hide(this.placeholderDialog);
                return;
            }

            if (!this.placeholderDialog || dm.mustRecreate(this.placeholderDialog)) {
                var defaultDialogConfig = {
                    'insert': CUI.rte.Utils.scope(function (placeholder) { this._insert(placeholder); }, this),
                    'parameters': {
                        'command': this.pluginId + '#addplaceholder'
                    }
                };
                var dialogConfig = CUI.rte.Utils.applyDefaults(Object.create(null), defaultDialogConfig);
                dialogConfig.placeholders = this.placeholders;
                this.placeholderDialog = this._createDialog(dialogConfig);
            }

            dm.show(this.placeholderDialog);
        },

        _createDialog: function (dialogConfig) {
            var context = this.editorKernel.getEditContext();
            var $container = CUI.rte.UIUtils.getUIContainer($(context.root));
            var dialog = new PlaceholderDialog(this.editorKernel, dialogConfig);
            dialog.attach(dialogConfig, $container, this.editorKernel);
            return dialog;
        },

        _insert: function (placeholder) {
            var rawHtml = this._insertPlaceholderSpanTags(placeholder);
            this.editorKernel.relayCmd('InsertHTML', rawHtml);
        },

        _extractPlaceholderText: function(placeholder) {
            var prefix = this.config.placeholderPrefix;
            var suffix = this.config.placeholderSuffix;

            if (placeholder.startsWith(prefix) && placeholder.endsWith(suffix)) {
                return placeholder.substring(prefix.length, placeholder.length - suffix.length);
            }
            return placeholder;
        },

        _isInputKeyEvent: function (e) {
            // check for shift (16), ctrl (17), alt (18) and meta (91 on mac, 93 on windows)
            // checking the event's e.isShift() etc methods would also return true if eg. shift + m was pressed
            return !(e.isCaretMovement() || e.key === 16 || e.key === 17 || e.key === 18
                || e.key === 91 || e.key === 93 || e.key == 224);
        },

        _handleBeforeKeyDown: function(e) {
            if (!e.isCaretMovement()) {
                var ctx = e.editContext;
                var sel = CUI.rte.Selection.getSelection(ctx);

                if (!this._isCaret(sel) && this._isInputKeyEvent(e)) {
                    if (sel.rangeCount > 0) {
                        var range = sel.getRangeAt(0);
                        range.deleteContents();
                    }
                    if (e.isDelete() || e.isBackSpace()) {
                        e.cancelKey = true;
                    }
                } else {
                    var span = this._getPlaceholderSpanFromSelection(ctx, sel);
                    if (span) {
                        if (e.isDelete()) {
                            e.cancelKey = this._isCaretAtStartOfNode(ctx, span, sel);
                        } else if (e.isBackSpace()) {
                            e.cancelKey = this._isCaretAtEndOfNode(ctx, span, sel);
                        } else {
                            this._adjustCaretPositionBeforeInsert(ctx);
                        }
                    }
                }
            }
        },

        _handleKeyUp: function(e) {
            if (e.isCaretMovement() || e.isDelete() || e.isBackSpace()) {
                this._maybeSelectPlaceholderSpan(e.editContext, e.isDelete(), e.isBackSpace());
            }
        },

        _handleBeforeCommandExecuted: function(e) {
            if (e.cmd === 'InsertHTML' && e.cmdValue.indexOf('class="distilledcode-rte-plugin-placeholder"') > -1) {
                var ctx = e.editContext;
                var sel = CUI.rte.Selection.getSelection(ctx);
                if (!this._isCaret(sel)) {
                    if (sel.rangeCount > 0) {
                        var range = sel.getRangeAt(0);
                        range.deleteContents();
                    }
                } else {
                    this._adjustCaretPositionBeforeInsert(ctx);
                }
            }
        },

        _handleCommandExecuted: function(e) {
            if (e.cmd === 'InsertHTML' && e.cmdValue.indexOf('class="distilledcode-rte-plugin-placeholder"') > -1) {

                var ctx = e.editContext;
                var sel = CUI.rte.Selection.getSelection(ctx);
                var span = this._getPlaceholderSpanFromSelection(ctx, sel);
                if (span && span.childElementCount > 0) {
                    var parent = span.parentNode;
                    var childNodes = span.childNodes;
                    var afterPlaceholder = false;
                    for (var i = 0; i < childNodes.length; i++) {
                        var child = childNodes[i];
                        var isPlaceholder = child.nodeType === Node.TEXT_NODE
                            && child.nodeValue.startsWith(this.config.placeholderPrefix)
                            && child.nodeValue.endsWith(this.config.placeholderSuffix);
                        afterPlaceholder = afterPlaceholder || isPlaceholder;
                        if (!isPlaceholder) {
                            var ref = !afterPlaceholder ? span : span.nextSibling;
                            child.remove();
                            i--; // adjust index, childNodes is a 'live' view into the DOM
                            parent.insertBefore(child, ref);
                        }
                    }
                }
                this._adjustCaretPositionAfterInsert(ctx);
            }
        },

        _isPlaceholderSpan: function (node) {
            return node && node.tagName === 'SPAN' && node.className === 'distilledcode-rte-plugin-placeholder';
        },

        _getContainingPlaceholderSpan: function (node) {
            var candidate = node;
            while (candidate && !this._isPlaceholderSpan(candidate)) {
                candidate = candidate.parentNode;
            }
            return candidate;
        },

        _getPlaceholderSpanFromSelection: function (editContext, selection) {
            var anchorNode = selection.anchorNode;
            if (anchorNode === null) {
                // console.debug('_getPlaceholderSpanFromSelection: anchorNode == null', selection);
                return null;
            }

            // if the caret offset is 0 any input is appended to the previous node
            if (selection.type === 'Caret' && selection.anchorOffset === 0 && anchorNode.previousSibling !== null) {
                anchorNode = anchorNode.previousSibling;
            }

            if (this._isPlaceholderSpan(anchorNode)) {
                return anchorNode;
            }

            var parent = CUI.rte.Common.getParentNode(editContext, anchorNode);
            if (this._isPlaceholderSpan(parent)) {
                return parent;
            }

            var node = anchorNode.previousSibling;
            if (this._isCaretAfter(node, selection) && this._isPlaceholderSpan(node)) {
                return node;
            }

            node = anchorNode.nextSibling;
            if (this._isCaretBefore(node, selection) && this._isPlaceholderSpan(node)) {
                return node;
            }

            if (selection.anchorNode === selection.focusNode) {
                var selParent = selection.anchorNode;
                if (selParent.nodeType === Node.ELEMENT_NODE) {
                    node = selParent.childNodes[selection.anchorOffset];
                    if (this._isPlaceholderSpan(node)) {
                        return node;
                    }
                }
            }

            return null;
        },

        _isCaretBefore: function (node, selection) {
            return node !== null
                && selection.isCollapsed
                && selection.anchorNode.nextSibling === node
                && selection.anchorOffset === selection.anchorNode.length;
        },

        _isCaretAfter: function (node, selection) {
            return node !== null
                && selection.isCollapsed
                && selection.anchorNode.previousSibling === node
                && selection.anchorOffset === 0;
        },

        _adjustCaretPositionAfterInsert: function (editContext) {
            var ctx = editContext;
            var sel = CUI.rte.Selection.getSelection(ctx);
            var span = this._getPlaceholderSpanFromSelection(ctx, sel);
            if (span) {
                CUI.rte.Selection.selectAfterNode(ctx, span);
            }
        },

        _maybeSelectPlaceholderSpan: function (editContext, isDelete, isBackSpace) {
            var ctx = editContext;
            var sel = CUI.rte.Selection.getSelection(ctx);
            // console.debug('_maybeSelectPlaceholderSpan');
            if (this._isCaret(sel)) {
                var span = this._getPlaceholderSpanFromSelection(ctx, sel);
                if (span) {
                    var isCaretAtStartOfNode = this._isCaretAtStartOfNode(ctx, span, sel);
                    var isCaretAtEndOfNode = this._isCaretAtEndOfNode(ctx, span, sel);
                    if ((!isCaretAtEndOfNode && !isCaretAtStartOfNode) || (isDelete && isCaretAtStartOfNode) || (isBackSpace && isCaretAtEndOfNode)) {
                        var newRange = CUI.rte.Selection.createRange(ctx);
                        newRange.selectNode(span);
                        CUI.rte.Selection.selectRange(ctx, newRange);
                        // console.debug('_maybeSelectPlaceholderSpan: selecting', span);
                    }
                }
            } else if (sel.rangeCount > 0) {
                var range = sel.getRangeAt(0);
                // console.debug('_maybeSelectPlaceholderSpan: handling range', range.startContainer, range.endContainer, range.startOffset, range.endOffset);

                var containingSpan = this._getContainingPlaceholderSpan(range.startContainer);
                if (containingSpan && !this._isAtStartOfNode(ctx, containingSpan, range.startContainer, range.startOffset)) {
                    // console.debug('_maybeSelectPlaceholderSpan: setStartBefore', containingSpan);
                    range.setStartBefore(containingSpan);
                }
                containingSpan = this._getContainingPlaceholderSpan(range.endContainer);
                if (containingSpan && !this._isAtEndOfNode(ctx, containingSpan, range.endContainer, range.endOffset)) {
                    // console.debug('_maybeSelectPlaceholderSpan: setEndAfter', containingSpan);
                    range.setEndAfter(containingSpan);
                }
            }
        },

        _adjustCaretPositionBeforeInsert: function (ctx) {
            var sel = CUI.rte.Selection.getSelection(ctx);
            var span = this._getPlaceholderSpanFromSelection(ctx, sel);
            if (span) {
                if (this._isCaretAtStartOfFirstNode(ctx, span, sel)) {
                    // console.debug('_adjustCaretPositionBeforeInsert: beforeNode', span);
                    CUI.rte.Selection.selectBeforeNode(ctx, span);
                } else if (this._isCaretAtEndOfNode(ctx, span, sel)) {
                    // console.debug('_adjustCaretPositionBeforeInsert: afterNode', span);
                    CUI.rte.Selection.selectAfterNode(ctx, span);
                }
            }
        },

        _isCaretAtStartOfFirstNode: function (editContext, node, sel) {
            if (node && this._isCaret(sel)) {
                var textNode = CUI.rte.Common.getFirstTextChild(node, false, true);
                // console.debug('_isCaretAtStartOfFirstNode - 2', textNode);
                if (textNode) {
                    return sel.anchorNode === textNode && 0 === sel.anchorOffset;
                }
            }
            return false;
        },

        _isAtStartOfNode: function (editContext, node, refNode, offset) {
            // for Gecko
            if (refNode.hasChildNodes()
                && refNode.childNodes.length > offset
                && refNode.childNodes[offset] === node) {
                return true;
            }

            // for Webkit
            var textNode = CUI.rte.Common.getPreviousTextNode(editContext, node, false);
            if (textNode) {
                return this._isAtEndOfNode(editContext, textNode, refNode, offset);
            }
            return false;
        },

        _isCaretAtStartOfNode: function (editContext, node, sel) {
            if (node && this._isCaret(sel)) {
                return this._isAtStartOfNode(editContext, node, sel.anchorNode, sel.anchorOffset)
                    || this._isCaretAtStartOfFirstNode(editContext, node, sel);
            }
            return false;
        },

        _isAtEndOfNode: function (editContext, node, refNode, offset) {
            // for Webkit
            var lastTextChild = CUI.rte.Common.getLastTextChild(node, false, true);
            // for Gecko
            var nextTextSibling = node.nextSibling && CUI.rte.Common.getFirstTextChild(node.nextSibling, false, true);
            var lastOffsetOfLastTextChild = refNode === lastTextChild && lastTextChild.length === offset;
            var firstOffsetOfNextTextSibling = refNode === nextTextSibling && offset === 0;
            return lastOffsetOfLastTextChild || firstOffsetOfNextTextSibling;
        },

        _isCaretAtEndOfNode: function (editContext, node, sel) {
            if (node && this._isCaret(sel)) {
                return this._isAtEndOfNode(editContext, node, sel.anchorNode, sel.anchorOffset);
            }
            return false;
        },

        _isCaret: function(selection) {
            return selection.anchorNode === selection.focusNode && selection.anchorOffset === selection.focusOffset;
        },

        // HACK: The plugin's 'interceptContent' method is inadequate, because
        // it requires changing the DOM of the edited content rather than
        // the resulting HTML, AND it is called e.g. when a toolbar-dialog
        // is opened to 'cleanDom' and when closed 'postprocessDom', which
        // means that selections and caret positions are lost, leading to
        // a whole range of new issues to work around.
        _setupContentInterception: function (editorKernel) {
            var self = this;
            var originalGetProcessedHtml = editorKernel.getProcessedHtml;
            editorKernel.getProcessedHtml = function() {
                var html = originalGetProcessedHtml.apply(this, arguments);
                return self._removePlaceholderSpanTags(html);
            };

            var originalSetUnprocessedHtml = editorKernel.setUnprocessedHtml;
            editorKernel.setUnprocessedHtml = function(html) {
                var html = self._insertPlaceholderSpanTags(html);
                originalSetUnprocessedHtml.call(this, html);
            };
        },

        _insertPlaceholderSpanTags: function(html, prefix, suffix) {
            prefix = prefix || this._escapeForRegexp(this.config.placeholderPrefix);
            suffix = suffix || this._escapeForRegexp(this.config.placeholderSuffix);

            // strip any remaining placeholder span tags just to be
            // sure no duplicates are injected
            html = this._removePlaceholderSpanTags(html, prefix, suffix)
            return html.replace(new RegExp('(' + prefix + '(.+?)' + suffix + ')', 'g'), (match, p1, p2) => {
                var title = this.placeholderToLabel[p2] || p2;
                return '<span class="distilledcode-rte-plugin-placeholder" title="' + title + '">' + p1 + '</span>';
            });
        },

        _removePlaceholderSpanTags: function(html, prefix, suffix) {
            prefix = prefix || this._escapeForRegexp(this.config.placeholderPrefix);
            suffix = suffix || this._escapeForRegexp(this.config.placeholderSuffix);
            return html.replace(new RegExp(
                '<span.+?distilledcode-rte-plugin-placeholder.+?>(' + prefix + '.+?' + suffix + ')</span>', 'g'),
                '$1');
        },

        _escapeForRegexp(str) {
            return str.replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1");
        }
    });

    CUI.rte.plugins.PluginRegistry.register('placeholder', PlaceholderPlugin);

}(jQuery, CUI, Class));
