/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {css, html} from 'qwc-hot-reload-element';
import {QwcCamelCore} from "./qwc-camel-core.js";
import '@vaadin/select';
import '@vaadin/text-field';
import '@vaadin/horizontal-layout';

export class QwcCamelCoreDiagram extends QwcCamelCore {

    static styles = [
        QwcCamelCore.styles,
        css`
            .diagram-container {
                display: flex;
                flex-direction: column;
                height: 100%;
                overflow: auto;
            }

            .diagram-image {
                max-width: 100%;
                height: auto;
            }

            .diagram-text {
                white-space: pre;
                font-family: monospace;
                font-size: 14px;
                padding: 10px;
                overflow: auto;
            }
        `
    ];

    static properties = {
        _mode: {state: true},
        _theme: {state: true},
    };

    constructor() {
        super('route-diagram', {});
        this._mode = 'route';
        this._theme = 'transparent';
    }

    render() {
        const data = super.consoleData();

        return html`
            <vaadin-horizontal-layout theme="spacing padding">
                <vaadin-select
                        label="Mode"
                        .items="${[
                            {label: 'Route', value: 'route'},
                            {label: 'Topology', value: 'topology'}
                        ]}"
                        .value="${this._mode}"
                        @value-changed="${(e) => {
                            this._mode = e.detail.value;
                            super.putOption('mode', e.detail.value);
                        }}">
                </vaadin-select>
                <vaadin-select
                        label="Theme"
                        .items="${[
                            {label: 'Transparent', value: 'transparent'},
                            {label: 'Dark', value: 'dark'},
                            {label: 'Light', value: 'light'}
                        ]}"
                        .value="${this._theme}"
                        @value-changed="${(e) => {
                            this._theme = e.detail.value;
                            super.putOption('theme', e.detail.value);
                        }}">
                </vaadin-select>
                <vaadin-text-field
                        label="Filter"
                        placeholder="Route ID filter"
                        clear-button-visible
                        @input="${(e) => {
                            super.putOption('filter', e.target.value || '*');
                        }}">
                    <vaadin-icon slot="prefix" icon="font-awesome-solid:magnifying-glass"></vaadin-icon>
                </vaadin-text-field>
            </vaadin-horizontal-layout>
            <div class="diagram-container">
                ${this._renderDiagram(data)}
            </div>
        `;
    }

    _renderDiagram(data) {
        if (!data || Object.keys(data).length === 0) {
            return super.redenderNoDataAvailableMessage();
        }

        if (data.image) {
            return html`<img class="diagram-image" src="data:image/png;base64,${data.image}" alt="Route Diagram">`;
        }

        if (data.text) {
            return html`<div class="diagram-text">${data.text}</div>`;
        }

        return super.redenderNoDataAvailableMessage();
    }
}

customElements.define('qwc-camel-core-diagram', QwcCamelCoreDiagram);
