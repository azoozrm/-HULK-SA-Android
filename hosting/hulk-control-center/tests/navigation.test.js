'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');

function assert(condition, message) {
    if (!condition) {
        throw new Error(`FAIL: ${message}`);
    }
}

class ConfirmForm {
    constructor() {
        this.handlers = {};
    }

    addEventListener(name, handler) {
        this.handlers[name] = handler;
    }

    getAttribute() {
        return 'تأكيد الاختبار';
    }
}

function runApp({scrollWidth, clientWidth}) {
    const confirmForm = new ConfirmForm();
    const scrollCalls = [];
    const activeTab = {
        scrollIntoView: (options) => {
            scrollCalls.push(options);
        },
    };
    const track = {
        scrollWidth,
        clientWidth,
        querySelector: (selector) => selector === '.primary-tab.is-active' ? activeTab : null,
    };
    const submitButton = {
        disabled: false,
        attributes: {},
        setAttribute(name, value) {
            this.attributes[name] = String(value);
        },
    };
    const submitForm = {
        handlers: {},
        addEventListener(name, handler) {
            this.handlers[name] = handler;
        },
        querySelectorAll: (selector) => selector === 'button[type="submit"]' ? [submitButton] : [],
    };
    let confirmResult = false;
    const document = {
        querySelector: (selector) => selector === '[data-primary-tabs]' ? track : null,
        getElementById: () => null,
        querySelectorAll: (selector) => {
            if (selector === 'form[data-confirm]') {
                return [confirmForm];
            }
            if (selector === 'form[data-submit-once]') {
                return [submitForm];
            }
            return [];
        },
    };
    const source = fs.readFileSync(path.join(__dirname, '..', 'assets', 'app.js'), 'utf8');
    vm.runInNewContext(source, {
        Array,
        document,
        window: {confirm: () => confirmResult},
    });
    return {
        confirmForm,
        scrollCalls,
        submitForm,
        submitButton,
        setConfirmResult: (value) => {
            confirmResult = value;
        },
    };
}

const overflowing = runApp({scrollWidth: 640, clientWidth: 390});
assert(overflowing.scrollCalls.length === 1, 'an overflowing primary tab strip centers the active tab');
assert(
    overflowing.scrollCalls[0].block === 'nearest' && overflowing.scrollCalls[0].inline === 'center',
    'active tab scrolling is bounded to the nearest block and centered inline',
);

const fitting = runApp({scrollWidth: 390, clientWidth: 390});
assert(fitting.scrollCalls.length === 0, 'a fitting primary tab strip is not scrolled');

let prevented = false;
overflowing.confirmForm.handlers.submit({preventDefault: () => { prevented = true; }});
assert(prevented, 'a rejected destructive-action confirmation prevents submission');
overflowing.setConfirmResult(true);
prevented = false;
overflowing.confirmForm.handlers.submit({preventDefault: () => { prevented = true; }});
assert(!prevented, 'an accepted destructive-action confirmation permits submission');

const onceSubmit = runApp({scrollWidth: 390, clientWidth: 390});
onceSubmit.submitForm.handlers.submit({defaultPrevented: false});
assert(onceSubmit.submitButton.disabled, 'submit-once forms disable their submit button');
assert(onceSubmit.submitButton.attributes['aria-disabled'] === 'true', 'submit-once forms expose aria-disabled');

const preventedSubmit = runApp({scrollWidth: 390, clientWidth: 390});
preventedSubmit.submitForm.handlers.submit({defaultPrevented: true});
assert(!preventedSubmit.submitButton.disabled, 'a cancelled confirmation does not disable the submit button');

process.stdout.write('PASS: Control Center primary tabs, confirmations and submit-once contract.\n');
