/*
 *  Copyright 2020 Code Distillery GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the 'License');
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
;(function (document, $, Granite) {
    'use strict';

    // 'homepage|generic/gridparsys/teasergrid/abc|def/123|456/griditems|parsys'
    // - multiply all by iterating all items from right to left
    // - then drop index 1, and repeat                         'homepage|generic/teasergrid/abc|def/123|456/griditems|parsys'
    // - then drop index 2, and repeat                         'homepage|generic/gridparsys/abc|def/123|456/griditems|parsys'
    // - then drop index 2 and 1, and repeat                   'homepage|generic/abc|def/123|456/griditems|parsys'
    // - then drop index 3, and repeat                         'homepage|generic/gridparsys/teasergrid/123|456/griditems|parsys'
    // - then drop index 3 and 1, and repeat                   'homepage|generic/teasergrid/123|456/griditems|parsys'
    // - then drop index 3 and 2, and repeat                   'homepage|generic/gridparsys/123|456/griditems|parsys'
    // - then drop index 3 and 2 and 1, and repeat             'homepage|generic/123|456/griditems|parsys'
    // - then drop index 4, and repeat                         'homepage|generic/gridparsys/teasergrid/abc|def/griditems|parsys'
    // - then drop index 4 and 1, and repeat                   'homepage|generic/teasergrid/abc|def/griditems|parsys'
    // - then drop index 4 and 2, and repeat                   'homepage|generic/gridparsys/abc|def/griditems|parsys'
    // - then drop index 4 and 2 and 1, and repeat             'homepage|generic/abc|def/griditems|parsys'
    // - then drop index 4 and 3, and repeat                   'homepage|generic/gridparsys/teasergrid/griditems|parsys'
    // - then drop index 4 and 3 and 1, and repeat             'homepage|generic/teasergrid/griditems|parsys'
    // - then drop index 4 and 3 and 2, and repeat             'homepage|generic/gridparsys/griditems|parsys'
    // - then drop index 4 and 3 and 2 and 1, and repeat       'homepage|generic/griditems|parsys'
    // - then drop index 5 (names), and repeat                 'homepage|generic/gridparsys/teasergrid/abc|def/123|456'
    // - then drop index 5 and 1, and repeat                   'homepage|generic/teasergrid/abc|def/123|456'
    // - then drop index 5 and 2, and repeat                   'homepage|generic/gridparsys/abc|def/123|456'
    // - then drop index 5 and 2 and 1, and repeat             'homepage|generic/abc|def/123|456'
    // - then drop index 5 and 3, and repeat                   'homepage|generic/gridparsys/teasergrid/123|456'
    // - then drop index 5 and 3 and 1, and repeat             'homepage|generic/teasergrid/123|456'
    // - then drop index 5 and 3 and 2, and repeat             'homepage|generic/gridparsys/123|456'
    // - then drop index 5 and 3 and 2 and 1, and repeat       'homepage|generic/123|456'
    // - then drop index 5 and 4, and repeat                   'homepage|generic/gridparsys/teasergrid/abc|def'
    // - then drop index 5 and 4 and 1, and repeat             'homepage|generic/teasergrid/abc|def'
    // - then drop index 5 and 4 and 2, and repeat             'homepage|generic/gridparsys/abc|def'
    // - then drop index 5 and 4 and 2 and 1, and repeat       'homepage|generic/abc|def'
    // - then drop index 5 and 4 and 3, and repeat             'homepage|generic/gridparsys/teasergrid'
    // - then drop index 5 and 4 and 3 and 1, and repeat       'homepage|generic/teasergrid'
    // - then drop index 5 and 4 and 3 and 2, and repeat       'homepage|generic/gridparsys'
    // - then drop index 5 and 4 and 3 and 2 and 1, and repeat 'homepage|generic'
    // - append index 5 (names)                                'griditems|parsys'

    function* multiply(segments, startIndex = 0) {
        if (segments.length - 1 === startIndex) {
            // last segment doesn't require concatenation
            yield* segments[startIndex];
        } else if (segments.length > 1) {
            for (var name of segments[startIndex]) {
                for (var suffix of multiply(segments, startIndex + 1)) {
                    yield name + '/' + suffix;
                }
            }
        }
    }

    function* getSearchPaths(segments, ignoredIndexes = []) {
        var segmentsWithoutIgnored = [];
        for (var i = 0; i < segments.length; i++) {
            if (ignoredIndexes.indexOf(i) === -1) {
                segmentsWithoutIgnored.push(segments[i]);
            }
        }

        var addedSearchPaths = multiply(segmentsWithoutIgnored);
        //console.debug('processing', ignoredIndexes, segmentsWithoutIgnored.map(el => el.join('|')).join('/') /*, [...addedSearchPaths] */);
        yield* addedSearchPaths;

        var max = ignoredIndexes.length === 0 ? segments.length : Math.min(...ignoredIndexes);
        for (var i = 1; i < max; i++) {
            var ignore = [].concat(ignoredIndexes);
            ignore.push(i);
            yield* getSearchPaths(segments, ignore);
        }

        if (ignoredIndexes.length === 0) {
            // at the very end append the names (i.e. last segment)
            // console.debug('processing', ignoredIndexes, segmentsWithoutIgnored.map(el => el.join('|')).join('/'), segments[segments.length - 1]);
            yield* segments[segments.length - 1];
        }
    }

    function toCellSearchPath(segments) {
        return segments.map(el => el.join('|')).join('/');
    }

    $(document).on('cq-components-loaded', function() {

        var designCache = Object.create(null);

        Granite.author.designResolver.getProperty = (function getProperty(editableConfig, designs, propertyName) {
            if (!editableConfig || !designs || !propertyName) {
                return null;
            }

            var segments = Granite.author.getSegments(editableConfig);
            var cellSearchPath = toCellSearchPath(segments);
            var propertyCache;
            if (designCache[propertyName]) {
                propertyCache = designCache[propertyName];
            } else {
                propertyCache = designCache[propertyName] = Object.create(null);
            }
            if (propertyCache[cellSearchPath]) {
                return propertyCache[cellSearchPath];
            }

            var searchPaths = getSearchPaths(segments);

            for (var searchPath of searchPaths) {
                var names = searchPath.split('/');
                var currentDesign = designs;
                for (var name of names) {
                    if (!currentDesign.hasOwnProperty(name)) {
                        currentDesign = null;
                        break;
                    }
                    currentDesign = currentDesign[name];
                }
                if (currentDesign && currentDesign.hasOwnProperty(propertyName)) {
                    return propertyCache[cellSearchPath] = currentDesign[propertyName];
                }
            }

            return null;
        }).bind(Granite.author.designResolver);
    });

})(document, Granite.$, Granite);


