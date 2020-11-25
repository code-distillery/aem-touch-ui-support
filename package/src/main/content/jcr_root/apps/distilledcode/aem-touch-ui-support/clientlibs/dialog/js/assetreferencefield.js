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
(function(window, document, $, Coral, Granite, URITemplate) {
    'use strict';

    // TODO
    // x support per dialog config, e.g. cropConfig node, enable/disable plugins etc
    // x reflect transformations in dialog thumbnail
    // x enable/disable imageeditor
    // x double check crop string format
    // - support ddGroups and/or mimeTypes and/or extensions
    // x gracefully handle absence of web rendition
    // - enable/disable imageeditor automatically, depending on mimeType (i.e. image/*),
    //   but allow overriding to disable it completely; also disable if web rendition is missing
    // very optional:
    // - optimize zoom factor for cropped/rotated images after applying the transformation
    //   x when opening the editor
    //   - after performing the operation
    // - adjust zoom on window resize (but only if it hasn't been manually zoomed?)

    var DEFAULT_IMAGE_EDITOR_OPTIONS = {
        'ui': {
            'fullscreen': {
                'toolbar': [
                    [
                        'crop#launchwithratio',
                        'rotate#left',
                        'rotate#right',
//                        'flip#vertical',
//                        'flip#horizontal',
                        'map#launch',
                        'zoom#reset100',
                        'zoom#popupslider'
                    ],
                    [
                        'history#undo',
                        'history#redo',
                        'control#close',
                        'control#finish'
                    ]
                ],
                'replacementToolbars': {
                    'crop': [
                        [
                            'crop#identifier'
                        ],
                        [
                            'crop#unlaunch',
                            'crop#confirm'
                        ]
                    ],
                    'map': [
                        [
                            'map#rectangle',
                            'map#circle',
                            'map#polygon',
                        ],
                        [
                            'map#unlaunch',
                            'map#confirm'
                        ]
                    ]
                }
            }
        },
        plugins: {
            crop: {
                features: '*'
            },
            map: {
                features: '*',

                // pathbrowser config copied from Granite.author.editor.ImageEditor.defaults
                // because that one is only available when editing a page and not when editing
                // a page properties dialog
                // - adjusted pickerTitle for i18n
                // - renamed pickerCrumbRoot to correct crumbRoot, added i18n and correct icon
                pathbrowser: {
                    type: 'picker',
                    
                    /* options for autocomplete and picker: */
                    rootPath: '/content',
                    showTitles: false,
                    optionLoader: function(path, callback) {
                        $.get(path + ".pages.json", {
                                predicate: "hierarchyNotFile"
                            },
                            function(data) {
                                var pages = data.pages;
                                var result = [];
                                for(var i = 0; i < pages.length; i++) {
                                    result.push(pages[i].label);
                                }
                                if (callback) {
                                    callback(result);
                                }
                            }, "json");
                        return false;
                    },

                    /* autocomplete configuration: */
                    optionLoaderRoot: null, // provide property path to array in return value of optionLoader, e.g. 'results.values',
                    optionValueReader: function (object) {
                        return '' + object;
                    },
                    optionTitleReader: function (object) {
                        return '' + object;
                    },

                    /* picker configuration: */
                    pickerSrc: '/libs/wcm/core/content/common/pathbrowser/column.html/content?predicate=hierarchyNotFile',
                    pickerTitle: Granite.I18n.get('Select Path'),
                    crumbRoot: {
                        title: Granite.I18n.get('Content Root'),
                        icon: 'coral3-Icon coral3-Icon--home'
                    }
                }
            },
            rotate: {
                features: '*'
            },
        }
    };

    var HIDDEN_INPUT_NAMES = {
        fileReference: 'fileReference',
        crop: 'imageCrop',
        map: 'imageMap',
        rotate: 'imageRotate'
        // TODO: flip maps to properties imageFlipHorizontal = "true" and imageFlipVertical = "true"
    };

    var TRANSFORMATION_HANDLERS = {
        crop: {
            serialize: function serializeCrop(data, naturalWidth, naturalHeight) {
                var resizeBaseline = Math.max(naturalWidth, naturalHeight)
                var ratio = resizeBaseline > 1280 ? (1280 / resizeBaseline) : 1;
                return [data.left, data.top, data.left + data.width, data.top + data.height]
                    .map(val => Math.round(val * ratio))
                    .join(',');
            },
            deserialize: function deserializeCrop(serializedData, naturalWidth, naturalHeight) {
                var slashPos = serializedData.indexOf('/')
                var rawCrop = slashPos === -1 ? serializedData : serializedData.substring(0, slashPos);
                var coords = rawCrop.split(',');
                if (coords.length != 4) {
                    throw `'${serializedData}' cannot be split into 4 crop coordinates`;
                }

                var resizeBaseline = Math.max(naturalWidth, naturalHeight)
                var ratio = resizeBaseline > 1280 ? (resizeBaseline / 1280) : 1;
                coords = coords.map(n => Math.round(parseInt(n, 10) * ratio));
                return {
                    left:   coords[0],
                    top:    coords[1],
                    width:  coords[2] - coords[0],
                    height: coords[3] - coords[1]
                };
            }
        },

        rotate: {
            serialize: function serializeRotate(data) {
                // normalize both positive and negative angle values
                var angle = (360 + (data.angle % 360)) % 360;
                return angle === 0 ? '' : '' + angle;
            },
            deserialize: function deserializeRotate(serializedData) {
                return {
                    angle: parseInt(serializedData, 10)
                };
            }
        },

        map: {
            serialize: function serializeMap(data, naturalWidth, naturalHeight) {
                var areas = [];
                for (var i = 0; i < data.areas.length; i++) {
                    var area = data.areas[i];
                    var shape = area.shape;
                    var coords = [];
                    switch(shape) {
                        case 'circle':
                            var radius = Math.round(area.selection.width / 2.0);
                            coords.push(
                                area.selection.left + radius,
                                area.selection.top + radius,
                                radius
                            );
                            break;
                        case 'rect':
                            coords.push(
                                area.selection.left,
                                area.selection.top,
                                area.selection.width + area.selection.left,
                                area.selection.height + area.selection.top,
                            );
                            break;
                        case 'polygon':
                            shape = 'poly';
                            area.points.forEach(p => coords.push(p.w, p.h));
                            break;
                        default:
                            throw "Unsupported image-map shape: " + data.shape;
                    }

                    var relCoords = coords.map((coord, idx) => Math.round(coord / (idx % 2 === 0 ? naturalWidth : naturalHeight) * 10000) / 10000);

                    areas.push(`[${shape}(${coords.join(',')})` +
                        `"${area.href}"|"${area.target}"|"${area.alt}"|(${relCoords.join(',')})]`);
                }
                return areas.join('');
            },
            deserialize: function deserializeMap(serializedData) {
                if (!(serializedData.charAt(0) === '[' && serializedData.charAt(serializedData.length - 1) === ']')) {
                    throw 'Invalid shape string: "' + serializedData;
                }

                var mapDefs = serializedData.substring(1, serializedData.length - 1).split('][');
                var areas = [];
                for (var i = 0; i < mapDefs.length; i++) {
                    // e.g. 'rect(596,182,1024,512)"/content/geometrixx/en"|"_blank"|"Geometrixx"|(0.4656,0.2844,0.8,0.8)'
                    // but could also just be 'rect(1535,1013,1752,1168)||'
                    var mapDef = mapDefs[i];
                    var parts = mapDef.split(')');

                    var area = Object.create(null);
                    if (parts.length < 2) {
                        continue;
                    }

                    var shapeDef = parts[0].split('(');
                    var linkDef = parts[1].split('|');
                    // we don't care about relative coordinates that can optionally be in parts[2]

                    function unquote(str) {
                        if (str.charAt(0) == '"' && str.charAt(str.length - 1) == '"') {
                            return str.substring(1, str.length - 1);
                        }
                        return str;
                    }

                    var area = {
                        shape: shapeDef[0],
                        href: linkDef.length > 0 ? unquote(linkDef[0]) : '',
                        target: linkDef.length > 1 ? unquote(linkDef[1]) : '',
                        alt: linkDef.length > 2 ? unquote(linkDef[2]) : ''
                    };

                    var coords = shapeDef[1].split(',').map(x => parseInt(x, 10));
                    switch(area.shape) {
                        case 'circle':
                            var radius = coords[2];
                            area.selection = {
                                left:   coords[0] - radius,
                                top:    coords[1] - radius,
                                width:  2 * radius,
                                height: 2 * radius
                            };
                            break;
                        case 'rect':
                            area.selection = {
                                left:   coords[0],
                                top:    coords[1],
                                width:  coords[2] - coords[0],
                                height: coords[3] - coords[1]
                            };
                            break;
                        case 'poly':
                            area.shape = 'polygon';
                            area.points = [];
                            for (var j = 0; j < coords.length - 1; j+=2) {
                                area.points.push({w: coords[j], h: coords[j + 1]});
                            }
                            break;
                        default:
                            throw "Unsupported image-map shape: " + area.shape;
                    }
                    areas.push(area);
                 }

                 return {
                    areas: areas
                 };
            }
        }
    };

    function createButton(label, icon, cssClassSuffix) {
        var button = new Coral.Button().set({
            label: {
                innerHTML: label
            },
            type: 'button',
            variant: 'quiet',
            icon: icon
        });
        button.setAttribute('aria-label', label);
        button.classList.add(`distilledcode-assetreference-${cssClassSuffix}`);
        return button;
    }

    function resolveElement(src) {
        if (!src) {
            return $.Deferred().reject().promise();
        }

        if (src[0] === "#") {
            return $.when(document.querySelector(src));
        }

        return $.ajax({
            url: src,
            cache: false
        }).then(function(html) {
            return $(window).adaptTo("foundation-util-htmlparser").parse(html);
        }).then(function(fragment) {
            return $(fragment).children()[0];
        });
    }


    Coral['DistilledCode'] = Coral['DistilledCode'] || {};
    Coral.register({
        name: 'DistilledCode.AssetReference',
        tagName: 'distilledcode-assetreference',
        className: 'distilledcode-AssetReference',

        mixins: [
            Coral.mixin.formField
        ],
        
        properties: {
            'name': {
                'default': '',
                reflectAttribute: true,
                get: function() {
                    return this.getAttribute('name');
                },
                set: function(value) {
                    this.setAttribute('name', value);
                },
                sync: function() {
                    this._syncName();
                }
            },
            'value': {
                'default': '',
                reflectAttribute: false,
                get: function() {
                    return this._elements.inputs['fileReference'].value;
                },
                set: function(value) {
                    if (this._elements.inputs['fileReference'].value) {
                        this._storeTransformations([]);
                    }
                    this._elements.inputs['fileReference'].value = value;
                },
                alsoSync: ['name'], // needed for adding @Delete when value is empty
                sync: function() {
                    this._updatePreview();
                }
            },
            'pickersrc': {
                reflectAttribute: true,
                get: function() {
                    var src = this.getAttribute('pickersrc');
                    return src === null ? src : URITemplate.expand(src, { value: this.value || '/content/dam' });
                },
                set: function() {
                    if (this._elements.picker.el !== null) {
                        if (this._elements.picker.open) {
                            this._onCancelPicker();
                        }
                        this._elements.picker = {
                            el: null,
                            open: false,
                            loading: false,
                            api: null
                        };
                    }
                }
            }
        },
        
        _render: function() {
            while (this.firstChild) {
                this.removeChild(this.firstChild);
            }

            var e = this._elements;
            var self = this;

            e.picker = {
                el: null,
                open: false,
                loading: false,
                api: null
            };

            this._createHiddenInputs();

            e.img = document.createElement('img');
            e.img.classList.add('distilledcode-assetreference-thumbnail');

            e.placeholder = new Coral.Icon().set({
                icon: 'imageAdd',
                size: 'L'
            });
            e.placeholder.classList.add('distilledcode-assetreference-placeholder');

            e.thumbnail = document.createElement('div');
            e.thumbnail.classList.add('distilledcode-assetreference-preview');
            e.thumbnail.appendChild(e.img);
            e.thumbnail.appendChild(e.placeholder);

            e.thumbnail.classList.add('cq-FileUpload'); // enables drag & drop from asset finder
            $(e.thumbnail)
                .on('assetselected', e => {
                    self.value = e.path;
                });

            e.choose = createButton(Granite.I18n.get('Select Asset'), 'images', 'choose');

            e.edit = createButton(Granite.I18n.get('Edit'), 'edit', 'edit');
            e.edit.hidden = true;

            e.clear = createButton(Granite.I18n.get('Clear'), 'exclude', 'clear');
            e.clear.hidden = true;

            this.appendChild(e.thumbnail);
            this.appendChild(e.choose);
            if ($(this).attr('data-image-editor-config') !== undefined) {
                this.appendChild(e.edit);
            }
            this.appendChild(e.clear);
        },

        _initialize: function() {
            var self = this;
            this._elements.choose.addEventListener('click', function(e) {
                e.preventDefault();
                self._togglePicker();
            });

            this._elements.edit.addEventListener('click', function(e) {
                e.preventDefault();
                self._startImageEditor();
            });

            this._elements.clear.addEventListener('click', function(e) {
                e.preventDefault();
                self.clear();
            });
        },

        _createHiddenInputs: function() {
            this._elements.inputs = this._elements.inputs || {};

            var types = Object.keys(HIDDEN_INPUT_NAMES)
            for (var i = 0; i < types.length; i++) {
                var type = types[i];
                var fieldId = HIDDEN_INPUT_NAMES[type];
                this._elements.inputs[fieldId] = this._elements.inputs[fieldId] || document.createElement('input');
                this._elements.inputs[fieldId].type = 'hidden';
                var value = $(this).attr('data-transformation-' + type);
                if (value) {
                    this._elements.inputs[fieldId].value = value;
                }

                this.appendChild(this._elements.inputs[fieldId]);

                fieldId = fieldId + '@Delete';
                this._elements.inputs[fieldId] = this._elements.inputs[fieldId] || document.createElement('input');
                this._elements.inputs[fieldId].type = 'hidden';
                this.appendChild(this._elements.inputs[fieldId]);
            }

            var self = this;
            ['jcr:lastModified', 'jcr:lastModifiedBy'].forEach(name => {
                var input = self._elements.inputs[name] = self._elements.inputs[name] || document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = '';
                input.disabled = true;
                self.appendChild(input);
            });
        },

        _storeTransformations: function(data, naturalWidth, naturalHeight) {
            var handlers = Object.keys(TRANSFORMATION_HANDLERS);
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                this._setHiddenInput(HIDDEN_INPUT_NAMES[type], '');
                for (var j = 0; j < data.length; j++) {
                    if (type === data[j].transformation) {
                        var serializedData = TRANSFORMATION_HANDLERS[type].serialize(data[j], naturalWidth, naturalHeight);
                        this._setHiddenInput(HIDDEN_INPUT_NAMES[type], serializedData);
                        break;
                    }
                }
            }
            this._setLastModified();
            this._updatePreview();
        },

        _setLastModified: function() {
            this._elements.inputs['jcr:lastModified'].disabled = false;
            this._elements.inputs['jcr:lastModifiedBy'].disabled = false;
        },

        _setHiddenInput: function(name, value) {
            this._elements.inputs[name].value = value;
        },

        _deserializeTransformation: function(type, data, naturalWidth, naturalHeight) {
            var value = TRANSFORMATION_HANDLERS[type].deserialize(data, naturalWidth, naturalHeight);
            value.transformation = type;
            return value;
        },

        _loadTransformations: function (naturalWidth, naturalHeight) {
            var handlers = Object.keys(TRANSFORMATION_HANDLERS);
            var result = Object.create(null);
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                var fieldName = HIDDEN_INPUT_NAMES[type];
                var value = this._elements.inputs[fieldName].value;
                if (value) {
                    var deserializedValue = this._deserializeTransformation(type, value, naturalWidth, naturalHeight);
                    if (deserializedValue) {
                        result[type] = deserializedValue;
                    }
                }
            }
            return result;
        },

        _syncName: function() {
            var pos = this.name.lastIndexOf('/');
            var prefix, name;
            if (pos == -1) {
                prefix = '';
                name = this.name;
            } else {
                prefix = this.name.substring(0, pos + 1);
                name = this.name.substring(pos + 1);
            }

            var self = this;
            var inputNames = Object.keys(this._elements.inputs);
            inputNames.filter(inputName => !inputName.endsWith('@Delete')).forEach(inputName => {
                var fieldId = HIDDEN_INPUT_NAMES[inputName] || inputName;
                var fieldName = inputName === 'fileReference' ? name : fieldId;
                self._elements.inputs[fieldId].name = prefix + fieldName;

                if (!!HIDDEN_INPUT_NAMES[inputName]) {
                    self._elements.inputs[fieldId + '@Delete'].name = prefix + fieldName + '@Delete';
                }
            });
        },

        _updatePreview: function() {
            var self = this;
            var img = this._elements.img;
            var plh = this._elements.placeholder;
            var edit = this._elements.edit;
            var clear = this._elements.clear;
            var val = this.value;
            if (val) {
                function parseDate(dateStr) {
                    return moment(dateStr, 'ddd MMM D YYYY HH:mm:ss [GMT]ZZ', 'en', true)
                }

                $(img).one('load', e => {
                    var image = e.target;
                    self._updateImageCss(self._loadTransformations(image.naturalWidth, image.naturalHeight));
                });
                $.getJSON(`${val}/_jcr_content/renditions.tidy.1.json`, function(json) {
                    var defaultWebRendition = 'cq5dam.web.1280.1280.jpeg';
                    // support non-default web rendition sizes; sort if there
                    // is more than one for predictable results

                    function dimensions(renditionName) {
                        return renditionName
                            .replace(/cq5dam\.web\.(\d+\.\d+)\.jpeg/, "$1")
                            .split('.')
                            .map(i => parseInt(i, 10));
                    }

                    var keys = Object.keys(json)
                        .filter(k => k.startsWith('cq5dam.web.'))
                        .sort((a, b) => {
                            // prefer the default web rendition over any custom ones
                            if (a == defaultWebRendition) {
                                return -1;
                            }
                            if (b == defaultWebRendition) {
                                return 1;
                            }

                            var dimA = dimensions(a),
                                dimB = dimensions(b);

                            // compare x values
                            if (dimA[0] < dimB[0]) {
                                return -1;
                            }
                            if (dimA[0] > dimB[0]) {
                                return 1;
                            }

                            // x values are equal, compare y values
                            if (dimA[1] < dimB[1]) {
                                return -1;
                            }
                            if (dimA[1] > dimB[1]) {
                                return 1;
                            }

                            return 0;
                        });

                    if (keys.length > 0) {
                        var key = keys[0];
                        var date = parseDate(json[key]['jcr:created']);
                        img.src = `${val}/_jcr_content/renditions/${key}?ch_ck=${date.format('x')}`;
                    } else {
                        var date = parseDate(json['jcr:created']);
                        img.src = `${val}.thumb.319.319.png?ch_ck=${date.format('x')}`;
                    }
                });
                img.alt = val;
                img.title = val;
                img.hidden = false;
                plh.hidden = true;
                edit.hidden = false;
                clear.hidden = false;
            } else {
                img.src = null;
                img.alt = null;
                img.title = null;
                img.hidden = true;
                img.removeAttribute('style');
                plh.hidden = false;
                edit.hidden = true;
                clear.hidden = true;
            }
        },

        _updateImageCss: function(transformations) {
            function resizeRatio(container, content) {
                return 1 / Math.max(
                    content.w / container.w,
                    content.h / container.h,
                    1.0
                );
            }
            
            function scale(ratio, values, keys) {
                var result = {};
                keys.forEach(key => {
                    result[key] = values[key] * ratio;
                });
                return result;
            }
            
            var img = this._elements.img, // assume that img is web rendition @ 1280x1280
                $img = $(img).removeAttr('style'),
                $containingPanel = $img.closest('coral-panel'),
                crop = transformations['crop'],
                angle = transformations['rotate'] !== undefined ? transformations['rotate'].angle : 0,
                // rotated 90 or 270 degrees, which causes width and height to be swapped
                swapDimensions = angle % 180 === 90,
                transformOrigin = undefined,
                margins = undefined,
                scaledBinary = undefined,
                css = {},
                binarySize = {
                    w: img.naturalWidth,
                    h: img.naturalHeight
                };

                // workaround: if panel is hidden, the availableSize gets a hight of 0
                if (img.parentElement.offsetWidth === 0) {
                    $containingPanel.addClass('is-selected distilledcode-workaround');
                }
                var availableSize = {
                    w: img.parentElement.offsetWidth,
                    h: Math.max(parseInt(getComputedStyle(img).getPropertyValue('max-height').replace('px', ''), 10), 256)
                };

            if (crop) {
                $.extend(crop, {
                    right: binarySize.w - crop.left - crop.width,
                    bottom: binarySize.h - crop.top - crop.height,
                });

                var ratio = resizeRatio(availableSize, swapDimensions ? { w: crop.height, h: crop.width } : { w: crop.width, h: crop.height });
                scaledBinary = scale(ratio, binarySize, 'w h'.split(' '));
                var scaledCrop = scale(ratio, crop, 'left top right bottom width height'.split(' '));

                transformOrigin = {
                    x: scaledCrop.width / 2 + scaledCrop.left,
                    y: scaledCrop.height / 2 + scaledCrop.top
                };

                margins = scale(-1, scaledCrop, 'left top right bottom'.split(' '));
                if (swapDimensions) {
                    var correction = (scaledCrop.width - scaledCrop.height) / 2;
                    margins.top += correction;
                    margins.bottom += correction;
                }

                $.extend(css, {
                    'clip-path': `inset(${scaledCrop.top}px ${scaledCrop.right}px ${scaledCrop.bottom}px ${scaledCrop.left}px)`
                });
            } else {
                var ratio = resizeRatio(availableSize, swapDimensions ? { w: binarySize.h, h: binarySize.w } : { w: binarySize.w, h: binarySize.h });
                scaledBinary = scale(ratio, binarySize, 'w h'.split(' '));
                margins = {
                    top: 0,
                    left: 0,
                    bottom: 0,
                    right: 0
                }
                if (swapDimensions) {
                    var correction = (scaledBinary.w - scaledBinary.h) / 2;
                    margins.top += correction;
                    margins.bottom += correction;
                }
            }

            $.extend(css, {
                'max-height': `${scaledBinary.h}px`,
                'max-width': 'unset',
                'margin': `${margins.top}px ${margins.right}px ${margins.bottom}px ${margins.left}px`,
                'position': 'relative',
                'transform-origin': !transformOrigin ? '50% 50%' : `${transformOrigin.x}px ${transformOrigin.y}px`,
                'transform': `scale(1) rotate(${angle}deg) scaleX(1) scaleY(1)`
            });

            $img.css(css);
            $containingPanel.filter('.distilledcode-workaround').removeClass('is-selected distilledcode-workaround');
        },

        clear: function() {
            this.value = null;
        },

        _startImageEditor: function() {

            var self = this;
            var canvas = $('body').children('.distilledcode-imageeditor-canvas');
            if (canvas.length === 0) {
                canvas = $('<div>', {
                    class: 'distilledcode-imageeditor-canvas',
                    css: { // TODO - move to CSS style sheet
                        height: '100%',
                        width: '100%',
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        zIndex: 10500
                    }
                });
                $('body').append(canvas);
            }
            canvas.empty();

            var val = this.value;
            var editorConfig = this._postProcessPluginConfig($(this).data('image-editor-config') || {})

            $('<img>', {
                src: editorConfig.referenceImagePath || this._elements.img.src,
                alt: val,
                title: val
            })
            .one('load', e => {
                var image = e.target;
                var editor = new CUI.ImageEditor({
                        parent: canvas,
                        mode: 'fullscreen',
                        element : image,
                        overrides: {
                            naturalHeight: image.naturalHeight,
                            naturalWidth: image.naturalWidth
                        }
                    });

                var transformations = this._loadTransformations(image.naturalWidth, image.naturalHeight);

                $(image)
                    .one('editing-cancelled', function(event) {
                        canvas.remove();
                    })
                    .one('editing-finished', function(event, data) {
                        canvas.remove();
                        self._storeTransformations(data.result, event.target.naturalWidth, event.target.naturalHeight);
                    })
                    .one('editing-start', function() {
                        var toolbars = canvas.find('.imageeditor-toolbar');
                        var toolbarHeights = $.map(toolbars, function(el) { return $(el).height(); });
                        var maxToolbarHeight = Math.max.apply(null, toolbarHeights);
                        var availableDimensions = {
                            height: canvas.height() - 10 - 2 * maxToolbarHeight,
                            width: canvas.width() - 10,
                        };

                        var translationUtil = new CUI.imageeditor.TranslationUtil({
                            rotation: transformations['rotate'] !== undefined ? transformations['rotate'].angle : 0,
                            naturalHeight: image.naturalHeight,
                            naturalWidth: image.naturalWidth,
                            cropOnOriginal: transformations['crop']
                        });
                        var imageDimensions = translationUtil.getDisplayDimensions();
                        var zoomFactor = Math.min(
                            availableDimensions.height / imageDimensions.height,
                            availableDimensions.width / imageDimensions.width,
                        );

                        if (zoomFactor < 1.0) {
                            editor.zoom(zoomFactor, false);
                        }
                    });

                var transformationsArray = Object.keys(transformations)
                    .reduce((acc, key) => {
                        acc[key] = transformations[key];
                        return acc;
                    }, Object.create(null))

                var options = $.extend(true, {}, DEFAULT_IMAGE_EDITOR_OPTIONS, {
                    plugins: editorConfig,
                    result: transformationsArray
                });

                editor.start(options);
            })
            .appendTo(canvas);
        },

        _postProcessPluginConfig: function(config) {
            var classicToCUIRatio = function(ratio) {
                var vals = ratio.split(',');
                if (vals.length === 2) {
                    return parseInt(vals[1], 10) / parseInt(vals[0], 10);
                }
                return null;
            };

            // Convert aspect ratios
            if (config && config.crop && config.crop.aspectRatios) {
                if (!$.isArray(config.crop.aspectRatios)) {
                    var aspectRatios = [];
                    for (var key in config.crop.aspectRatios) {
                        if (config.crop.aspectRatios.hasOwnProperty(key)) {
                            var aspectRatio = config.crop.aspectRatios[key];
                            aspectRatios.push({
                                name: aspectRatio.text,
                                ratio: classicToCUIRatio(aspectRatio.value)
                            });
                        }
                    }
                    config.crop.aspectRatios = aspectRatios;
                }
            }
            return config;
        },

        _loadAndShowPicker: function() {
            var self = this;
            this._elements.picker.loading = true;
            resolveElement(this.pickersrc).then(function(picker) {
                self._elements.picker.loading = false;
                self._elements.picker.el = picker;
                self._elements.picker.api = $(picker).adaptTo('foundation-picker');
                self._showPicker();
            }, function() {
                self._elements.picker.loading = false;
            });
        },

        _togglePicker: function() {
            if (this._elements.picker.loading) {
                return;
            }

            if (this._elements.picker.el) {
                if (this._elements.picker.open) {
                    this._elements.picker.api.cancel();
                    this._onCancelPicker();
                } else {
                    this._loadAndShowPicker();
                }
            } else {
                this._loadAndShowPicker();
            }
        },

        _showPicker: function() {
            var self = this;
            var api = this._elements.picker.api;
            
            api.attach(this);
            api.pick(this, [ this.value ], this.value).then(function(values) {
                self._elements.picker.api.detach();
                self._elements.picker.open = false;
                self._elements.choose.focus();
                self.value = values.length > 0 ? values[0].value : null;
            }, function() {
                self._onCancelPicker();
            });

            this._elements.picker.el.focus && this._elements.picker.el.focus();
            this._elements.picker.open = true;
        },

        _onCancelPicker: function() {
            this._elements.picker.api.detach();
            this._elements.picker.open = false;
            this._elements.choose.focus();
        }
    });
})(window, document, Granite.$, Coral, Granite, Granite.URITemplate);