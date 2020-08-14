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

    function triggerClose() {
        // coral-tooltip is kept open if a child-element
        // has the focus, so we remove the focus
        if (this.contains(document.activeElement)) {
            document.activeElement.blur();
        }

        var hideFn = this._startHide || this.hide;
        if (hideFn instanceof Function) {
            hideFn.bind(this).call();
        }
    }

    function keepOpenOnHover(e) {
        if ($(this).is(':hover')) {
            e.preventDefault();
        }
    }

    Coral.commons.ready(function() {
        $(document)
            .on('coral-overlay:beforeclose', 'coral-tooltip', keepOpenOnHover)
            .on('mouseleave', 'coral-tooltip', triggerClose);

        // move tooltips to a container in the body element, otherwise they may
        // be cut off in some scenarios due to the CSS of container elements
        // note: only _prev and _next are currently supported
        var container = document.createElement('div');
        container.classList.add('distilledcode-coral-tooltip-container');
        $('coral-tooltip[target=_prev],coral-tooltip[target=_next]').each(function() {
            if (this.target === '_prev' && this.previousElementSibling) {
                this.target = this.previousElementSibling;
            } else if (this.target === '_next' && this.nextElementSibling) {
                this.target = this.nextElementSibling;
            }
            container.appendChild(this);
        });
        if (container.childElementCount > 0) {
            document.body.appendChild(container);
        }
    });
})(document, Granite.$);
