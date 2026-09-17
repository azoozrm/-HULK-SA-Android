'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');

class TestElement {
    constructor(name) {
        this.name = name;
        this.attributes = {};
        this.handlers = {};
        this.hidden = false;
        this.focusableChildren = [];
        this.classList = {
            values: new Set(),
            toggle: (value, enabled) => enabled
                ? this.classList.values.add(value)
                : this.classList.values.delete(value),
            contains: (value) => this.classList.values.has(value),
        };
    }

    addEventListener(name, handler) {
        this.handlers[name] = handler;
    }

    setAttribute(name, value) {
        this.attributes[name] = String(value);
    }

    removeAttribute(name) {
        delete this.attributes[name];
    }

    focus() {
        testDocument.activeElement = this;
    }

    querySelectorAll() {
        return this.focusableChildren;
    }
}

function assert(condition, message) {
    if (!condition) {
        throw new Error(`FAIL: ${message}`);
    }
}

const sidebar = new TestElement('sidebar');
const openButton = new TestElement('open');
const closeButton = new TestElement('close');
const backdrop = new TestElement('backdrop');
const workspace = new TestElement('workspace');
const body = new TestElement('body');
const lastLink = new TestElement('last-link');
const confirmForm = new TestElement('confirm-form');
confirmForm.getAttribute = () => 'تأكيد الاختبار';
sidebar.focusableChildren = [closeButton, lastLink];

const elements = {
    '[data-sidebar]': sidebar,
    '[data-nav-open]': openButton,
    '[data-nav-close]': closeButton,
    '[data-nav-backdrop]': backdrop,
    '[data-workspace]': workspace,
};
const documentHandlers = {};
const testDocument = {
    activeElement: openButton,
    body,
    querySelector: (selector) => elements[selector] ?? null,
    querySelectorAll: (selector) => selector === 'form[data-confirm]' ? [confirmForm] : [],
    addEventListener: (name, handler) => {
        documentHandlers[name] = handler;
    },
};
const mediaQuery = {
    matches: true,
    handler: null,
    addEventListener: (_name, handler) => {
        mediaQuery.handler = handler;
    },
};

const source = fs.readFileSync(path.join(__dirname, '..', 'assets', 'app.js'), 'utf8');
let confirmResult = false;
vm.runInNewContext(source, {
    Array,
    document: testDocument,
    HTMLElement: TestElement,
    window: {matchMedia: () => mediaQuery, confirm: () => confirmResult},
});

assert('inert' in sidebar.attributes, 'closed mobile navigation is inert');
assert(sidebar.attributes['aria-hidden'] === 'true', 'closed mobile navigation is hidden from assistive technology');

openButton.handlers.click();
assert(!('inert' in sidebar.attributes), 'open mobile navigation is interactive');
assert(sidebar.attributes['aria-hidden'] === 'false', 'open mobile navigation is exposed to assistive technology');
assert('inert' in workspace.attributes, 'background workspace is inert while navigation is open');
assert(workspace.attributes['aria-hidden'] === 'true', 'background workspace is hidden from assistive technology');
assert(testDocument.activeElement === closeButton, 'opening navigation moves focus to its close control');

testDocument.activeElement = lastLink;
documentHandlers.keydown({key: 'Tab', shiftKey: false, preventDefault() {}});
assert(testDocument.activeElement === closeButton, 'forward Tab wraps inside the open navigation');

testDocument.activeElement = closeButton;
documentHandlers.keydown({key: 'Tab', shiftKey: true, preventDefault() {}});
assert(testDocument.activeElement === lastLink, 'reverse Tab wraps inside the open navigation');

documentHandlers.keydown({key: 'Escape', shiftKey: false, preventDefault() {}});
assert('inert' in sidebar.attributes, 'Escape closes and disables mobile navigation');
assert(!('inert' in workspace.attributes), 'Escape restores the workspace');
assert(testDocument.activeElement === openButton, 'Escape restores focus to the navigation trigger');

mediaQuery.matches = false;
mediaQuery.handler();
assert(!('inert' in sidebar.attributes), 'desktop navigation remains interactive after a breakpoint change');
assert(!('aria-hidden' in sidebar.attributes), 'desktop navigation remains exposed after a breakpoint change');

let prevented = false;
confirmForm.handlers.submit({preventDefault: () => { prevented = true; }});
assert(prevented, 'a rejected destructive-action confirmation prevents submission');
confirmResult = true;
prevented = false;
confirmForm.handlers.submit({preventDefault: () => { prevented = true; }});
assert(!prevented, 'an accepted destructive-action confirmation permits submission');

process.stdout.write('PASS: Control Center mobile navigation contract.\n');
