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
    // x enable/disable imageeditor automatically, depending on mimeType (i.e. image/*),
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

    var HIDDEN_INPUT_IDS_TO_NAMES = {
        fileReference: function() { return this.name; },
        fileName: function() { return this.fileNameParameter; },
        crop: 'imageCrop',
        map: 'imageMap',
        rotate: 'imageRotate'
        // TODO: flip maps to properties imageFlipHorizontal = "true" and imageFlipVertical = "true"
    };

    var TRANSFORMATION_HANDLERS = {
        crop: {

            _scaleCrop:function(field, transformations, naturalWidth, naturalHeight, invertRatio) {
                var crop = transformations['crop'];
                if (!crop) {
                    return null;
                }
                var ref = field.info.classicUiCropReference;
                if (ref && naturalWidth !== ref.width || naturalHeight !== ref.height) {
                    var ratio = Math.max(naturalWidth, naturalHeight) / Math.max(ref.width, ref.height);
                    if (invertRatio) {
                        ratio = 1 / ratio;
                    }
                    var scaled = scale(ratio, crop, 'left top width height'.split(' '));
                    scaled.transformation = 'crop';
                    return scaled;
                }
            },

            beforeSerialization: function(field, transformations, naturalWidth, naturalHeight) {
                return this._scaleCrop(field, transformations, naturalWidth, naturalHeight, true);
            },

            afterDeserialization: function(field, transformations, naturalWidth, naturalHeight) {
                return this._scaleCrop(field, transformations, naturalWidth, naturalHeight, false);
            },

            serialize: function serializeCrop(data, naturalWidth, naturalHeight) {
                return [data.left, data.top, data.left + data.width, data.top + data.height].join(',');
            },

            deserialize: function deserializeCrop(serializedData, naturalWidth, naturalHeight) {
                var slashPos = serializedData.indexOf('/')
                var rawCrop = slashPos === -1 ? serializedData : serializedData.substring(0, slashPos);
                var coords = rawCrop.split(',').map(n => Math.round(parseInt(n, 10)));
                if (coords.length != 4) {
                    throw `'${serializedData}' cannot be split into 4 crop coordinates`;
                }

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
            _computeImageMapReferenceDimensions: function(field, naturalWidth, naturalHeight) {
                var formData = Array.from(new FormData(field.closest('form')).entries())
                            .reduce((acc, curr) => { acc[curr[0]] = curr[1]; return acc; }, Object.create(null));
                var dimensionProviders = $(window).adaptTo('foundation-registry')
                        .get('distilledcode.assetreference.imageeditor.map.dimension.provider')
                var sortedProviders = [...dimensionProviders].sort((a, b) => (a.ranking || 0) - (b.ranking || 0));
                var dimensions = sortedProviders.reduce((acc, curr) => {
                    if (acc === undefined) {
                        var result = curr.handler(field, formData, naturalWidth, naturalHeight);
                        return !result || (result.width === -1 && result.height === -1) ? acc : result;
                    } else {
                        // use first handler that returned a valid
                        return acc;
                    }
                }, undefined);
                return dimensions;
            },

            _scaleMaps: function(field, transformations, naturalWidth, naturalHeight, invertRatio) {
                var map = transformations['map'];
                if (!map || !map.areas) {
                    return null;
                }

                var naturalDimensions = { width: naturalWidth, height: naturalHeight };
                var originalDimensions = { width: field.info.width, height: field.info.height };
                var refDimensions = this._computeImageMapReferenceDimensions(field, naturalWidth, naturalHeight) || naturalDimensions;
                
                // don't exceed physical dimensions of the original rendition
                if (originalDimensions.width < refDimensions.width || originalDimensions.height < refDimensions.height) {
                    refDimensions = originalDimensions;
                }

                // we need the crop coordinates adjusted for the web rendition when loading data (afterDeserialization)
                // but the crop coordinates already correspond to the web rendition when saving data (beforeSerialization)
                var crop = transformations['crop'];
                var scaledCrop;
                if (!crop) {
                    scaledCrop = naturalDimensions;
                } else {
                    scaledCrop = invertRatio
                        ? crop
                        : TRANSFORMATION_HANDLERS['crop']._scaleCrop(field, transformations, naturalWidth, naturalHeight, invertRatio);
                    scaledCrop = scaledCrop || crop; // _scaleCrop may return undefined
                }

                // calculate max available width and height based on crop % and original rendition, then adjust refDimensions accordingly
                var maxCroppedDimensions = {
                    w: Math.round(originalDimensions.width * scaledCrop.width / naturalWidth),
                    h: Math.round(originalDimensions.height * scaledCrop.height / naturalHeight)
                }

                var croppedDimensions = { w: scaledCrop.width, h: scaledCrop.height };

                var refDimForRatio = {
                    w: Math.min(refDimensions.width, maxCroppedDimensions.w),
                    h: Math.min(refDimensions.height, maxCroppedDimensions.h)
                };
                var ratio = resizeRatio(croppedDimensions, refDimForRatio);
                if (invertRatio) {
                    ratio = 1 / ratio;
                }

                if (map.areas) {
                    for (var i = 0; i < map.areas.length; i++) {
                        var area = map.areas[i];
                        if (area.shape === 'polygon') {
                            area.points = area.points.map(p => scale(ratio, p, 'w h'.split(' ')));
                        } else {
                            area.selection = scale(ratio, area.selection, 'left top width height'.split(' '));
                        }
                    }
                }
                return map;
            },

            beforeSerialization: function(field, transformations, naturalWidth, naturalHeight) {
                return this._scaleMaps(field, transformations, naturalWidth, naturalHeight, true);
            },

            afterDeserialization: function(field, transformations, naturalWidth, naturalHeight) {
                return this._scaleMaps(field, transformations, naturalWidth, naturalHeight, false);
            },

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

                    // TODO: verify relative coordinates are correct with cropped images
                    // var relCoords = coords.map((coord, idx) =>
                    //     Math.round(coord * 10000 / (idx % 2 === 0 ? naturalWidth : naturalHeight)) / 10000);
                    // areas.push(`[${shape}(${coords.join(',')})` +
                    //    `"${area.href}"|"${area.target}"|"${area.alt}"|(${relCoords.join(',')})]`);

                    areas.push(`[${shape}(${coords.join(',')})` +
                        `"${area.href}"|"${area.target}"|"${area.alt}"]`);
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

    function sortAlwaysFirst(testFn) {
        return (a, b) => testFn(a) ? -1 : (testFn(b) ? 1 : 0);
    }

    function sortBy(valueMapperFn) {
        return (a, b) => {
            var valA = valueMapperFn(a);
            var valB = valueMapperFn(b);
            return valA === valB ? 0 : (valA < valB ? -1 : 1);
        }
    }

    function sortByName() {
        return sortBy(e => e.name);
    }

    function chainedSort(...sortFns) {
        return (a, b) => sortFns.reduce((acc, fn) => acc === 0 ? fn(a, b) : acc, 0);
    }

    function resizeRatio(container, content) {
        return 1 / Math.max(
            content.w / container.w,
            content.h / container.h
        );
    }

    function scale(ratio, values, keys) {
        if (ratio === 1) {
            return values;
        }
        var result = Object.create(null);
        keys.forEach(key => {
            result[key] = Math.round(values[key] * ratio);
        });
        return result;
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
                },
                alsoSync: ['fileNameParameter']
            },
            'fileNameParameter': {
                'default': null,
                attribute: 'filenameparameter',
                reflectAttribute: true,
                sync: function() {
                    var paramName = this.fileNameParameter;
                    if (!paramName) {
                        this._removeHiddenInput('fileName');
                    } else {
                        if (!paramName.startsWith('./')) {
                            var prefix = this._getPathPrefix();
                            paramName = prefix + paramName;
                        }
                        this._createOrUpdateHiddenInput('fileName', paramName);
                    }
                }
            },
            'fileName': {
                'default': '',
                reflectAttribute: false,
                sync: function () {
                    var input = this._elements.inputs['fileName'];
                    if (input) {
                        var ref = this.value;
                        if (ref) {
                            var fileName = ref.substring(ref.lastIndexOf('/') + 1);
                            input.value = fileName;
                        } else {
                            input.value = null;
                        }
                    }
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
                        this._resetTransformations();
                    }
                    this._elements.inputs['fileReference'].value = value;
                },
                alsoSync: [
                    'name',    // needed for adding @Delete when value is empty
                    'fileName' // fileName is derived from fileReference
                ],
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

        _createOrUpdateHiddenInput: function(fieldId, fieldName) {
            this._elements.inputs = this._elements.inputs || {};
            var input = this._elements.inputs[fieldId] = this._elements.inputs[fieldId] || document.createElement('input');
            input.type = 'hidden';
            input.name = fieldName;
            this.appendChild(input);

            if (!fieldId.endsWith('@Delete')) {
                this._createOrUpdateHiddenInput(fieldId + "@Delete", fieldName + "@Delete");
            }

            return input;
        },

        _removeHiddenInput: function(fieldId) {
            this._elements.inputs = this._elements.inputs || {};
            var input = this._elements.inputs[fieldId];
            delete this._elements.inputs[fieldId];
            if (input) {
                input.remove();
            }
            if (!fieldId.endsWith('@Delete')) {
                this._removeHiddenInput(fieldId + '@Delete');
            }
        },

        _getHiddenFieldName(fieldId) {
            var fieldName = HIDDEN_INPUT_IDS_TO_NAMES[fieldId];
            if (typeof fieldName === 'function') {
                return fieldName.apply(this);
            }
            return fieldName;
        },

        _createHiddenInputs: function() {
            var fieldIds = Object.keys(HIDDEN_INPUT_IDS_TO_NAMES)
            for (var i = 0; i < fieldIds.length; i++) {
                var fieldId = fieldIds[i];
                var fieldName = this._getHiddenFieldName(fieldId);
                if (fieldName) {
                    var input = this._createOrUpdateHiddenInput(fieldId, fieldName)
                    var value = $(this).attr('data-transformation-' + fieldId);
                    if (value) {
                        input.value = value;
                    }
                }
            }

            var self = this;
            ['jcr:lastModified', 'jcr:lastModifiedBy'].forEach(name => {
                var input = self._createOrUpdateHiddenInput(name, name);
                input.value = '';
                input.disabled = true;
                self.appendChild(input);
            });
        },

        _setLastModified: function() {
            this._elements.inputs['jcr:lastModified'].disabled = false;
            this._elements.inputs['jcr:lastModifiedBy'].disabled = false;
        },

        _resetLastModified: function() {
            this._elements.inputs['jcr:lastModified'].disabled = true;
            this._elements.inputs['jcr:lastModifiedBy'].disabled = true;
        },

        _getTransformationHandlerNames: function() {
            return Object.keys(TRANSFORMATION_HANDLERS);
        },

        _loadTransformations: function (naturalWidth, naturalHeight) {
            var handlers = this._getTransformationHandlerNames();
            var result = Object.create(null);
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                var value = this._elements.inputs[type].value;
                if (value) {
                    var deserializedValue = this._deserializeTransformation(type, value, naturalWidth, naturalHeight);
                    if (deserializedValue) {
                        result[type] = deserializedValue;
                    }
                }
            }
            return this._processTransformations('afterDeserialization', result, naturalWidth, naturalHeight);
        },

        _deserializeTransformation: function(type, data, naturalWidth, naturalHeight) {
            var value = TRANSFORMATION_HANDLERS[type].deserialize(data, naturalWidth, naturalHeight);
            value.transformation = type;
            return value;
        },

        _storeTransformations: function(result, naturalWidth, naturalHeight) {
            var data = result.reduce((a, c) => { a[c.transformation] = c; return a }, Object.create(null));
            var processedTransformations = this._processTransformations('beforeSerialization', data, naturalWidth, naturalHeight);

            var handlers = this._getTransformationHandlerNames();
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                var input = this._elements.inputs[type];
                var transformation = processedTransformations[type];
                if (transformation) {
                    var serializedData = TRANSFORMATION_HANDLERS[type].serialize(transformation, naturalWidth, naturalHeight);
                    input.value = serializedData;
                    this._setLastModified();
                } else {
                    input.value = '';
                }
            }
            this._updatePreview();
        },

        _resetTransformations: function() {
            var handlers = this._getTransformationHandlerNames();
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                this._elements.inputs[type].value = '';
            }
            this._resetLastModified();
        },

        _processTransformations(methodName, transformations, naturalWidth, naturalHeight) {
            var handlers = this._getTransformationHandlerNames();
            var processedTransformations = $.extend(true, Object.create(null), transformations);
            for (var i = 0; i < handlers.length; i++) {
                var type = handlers[i];
                var fn = TRANSFORMATION_HANDLERS[type][methodName];
                if (fn && typeof fn === 'function') {
                    var clonedTransformations = $.extend(true, Object.create(null), transformations);
                    var newData = fn.bind(TRANSFORMATION_HANDLERS[type])(this, clonedTransformations, naturalWidth, naturalHeight);
                    if (newData && newData.transformation === type) {
                        processedTransformations[type] = newData;
                    }
                }
            }
            return processedTransformations;
        },

        _getPathPrefix: function() {
            var name = this.name;
            var pos = name.lastIndexOf('/');
            return pos == -1 ? null : name.substring(0, pos + 1);
        },

        _syncName: function() {
            var prefix = this._getPathPrefix();
            var name = prefix ? this.name.substring(prefix.length) : this.name;
            var self = this;
            var fieldIds = Object.keys(this._elements.inputs);
            fieldIds.filter(fieldId => !fieldId.endsWith('@Delete')).forEach(fieldId => {
                var fieldName = this._getHiddenFieldName(fieldId) || fieldId;
                self._createOrUpdateHiddenInput(fieldId, fieldName.startsWith('./') ? fieldName : (prefix + fieldName));
            });
        },

        _updatePreview: function() {
            var img = this._elements.img;
            var plh = this._elements.placeholder;
            var edit = this._elements.edit;
            var clear = this._elements.clear;
            var assetPath = this.value;
            if (assetPath) {

                var self = this;
                $(img).one('load', e => {
                    var image = e.target;
                    Coral.commons.nextFrame(_ => self._updateImageCss(self._loadTransformations(image.naturalWidth, image.naturalHeight)));
                });

                $.getJSON(`${assetPath}.assetreference.info.json`, function(info) {
                    self.info = info;
                    var isImageAsset = info.mimeType.startsWith('image/');
                    if (isImageAsset) {
                        var webRenditions = [...info.renditions].filter(rendition => rendition.name.startsWith('cq5dam.web.'));
                        info.classicUiCropReference = webRenditions.find(r => r.isClassicUiCropReference);
                        var webRendition = webRenditions
                            // prefer 'cq5dam.web.1280.1280.' web rendition, but take any other as well
                            // sorted by dimensions, largest dimensions first
                            .sort(chainedSort(
                                sortAlwaysFirst(r => r.name.startsWith('cq5dam.web.1280.1280.')),
                                sortBy(r => r.width * -1 + r.height * -1)
                            ))
                            .shift();
                        // use web rendition and fall back to original
                        img.src = webRendition ? webRendition.url : info.url;
                    } else {
                        img.src = info.thumbnailUrl;
                    }
                    img.alt = assetPath;
                    img.title = assetPath;
                    img.hidden = false;
                    plh.hidden = true;
                    edit.hidden = !isImageAsset;
                    clear.hidden = false;
                });
            } else {
                img.removeAttribute('src');
                img.removeAttribute('alt');
                img.removeAttribute('title');
                img.removeAttribute('style');
                img.hidden = true;
                plh.hidden = false;
                edit.hidden = true;
                clear.hidden = true;
            }
        },

        _updateImageCss: function(transformations) {
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
                ratio = Math.min(ratio, 1.0); // only scale down

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
                canvas = $('<div>', { class: 'distilledcode-imageeditor-canvas' });
                $('body').append(canvas);
            }
            canvas.empty();

            var val = this.value;
            var editorConfig = this._postProcessPluginConfig($(this).data('image-editor-config') || {})

            $('<img>', {
                src: this._elements.img.src,
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