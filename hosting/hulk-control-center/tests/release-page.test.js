'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');

function assert(condition, message) {
    if (!condition) {
        throw new Error(`FAIL: ${message}`);
    }
}

function makeElement() {
    return {
        handlers: {},
        attributes: {},
        hidden: true,
        value: '',
        textContent: '',
        focused: false,
        selected: false,
        addEventListener(name, handler) {
            this.handlers[name] = handler;
        },
        getAttribute(name) {
            return this.attributes[name] || '';
        },
        setAttribute(name, value) {
            this.attributes[name] = String(value);
        },
        removeAttribute(name) {
            delete this.attributes[name];
        },
        focus() {
            this.focused = true;
        },
        select() {
            this.selected = true;
        },
    };
}

const fallback = makeElement();
const status = makeElement();
const copyButton = makeElement();
copyButton.attributes['data-copy-value'] = 'https://hulksa.com/app/';
copyButton.attributes['data-copy-fallback'] = 'short-fallback';
copyButton.attributes['data-copy-status'] = 'short-status';

const clipboard = {
    writeText: async () => {},
};

const document = {
    querySelector: () => null,
    querySelectorAll: (selector) => selector === '[data-copy-value]' ? [copyButton] : [],
    getElementById: (id) => {
        if (id === 'short-fallback') {
            return fallback;
        }
        if (id === 'short-status') {
            return status;
        }
        return null;
    },
};

const source = fs.readFileSync(path.join(__dirname, '..', 'assets', 'app.js'), 'utf8');
vm.runInNewContext(source, {
    Array,
    document,
    navigator: {clipboard},
    window: {},
    Promise,
});

async function flush() {
    for (let index = 0; index < 5; index += 1) {
        await Promise.resolve();
    }
}

async function main() {
    clipboard.writeText = async () => {
        throw new Error('clipboard unavailable');
    };
    copyButton.handlers.click();
    await flush();
    assert(fallback.hidden === false, 'clipboard failure reveals the selectable fallback');
    assert(fallback.value === 'https://hulksa.com/app/', 'the fallback contains the exact official URL');
    assert(fallback.focused && fallback.selected, 'clipboard failure focuses and selects the fallback');
    assert(status.attributes['data-copy-state'] === 'fallback', 'clipboard failure is labeled as a fallback');
    assert(status.textContent.includes('تعذر النسخ التلقائي'), 'clipboard failure is honest');
    assert(!status.textContent.includes('تم نسخ'), 'clipboard failure never claims success');

    fallback.hidden = true;
    status.textContent = '';
    delete status.attributes['data-copy-state'];
    clipboard.writeText = async () => {};
    copyButton.handlers.click();
    await flush();
    assert(fallback.hidden === true, 'clipboard success keeps the fallback hidden');
    assert(status.textContent === 'تم نسخ الرابط.', 'clipboard success reports the copied URL');
    assert(status.attributes['data-copy-state'] === 'success', 'clipboard success is labeled as success');

    process.stdout.write('PASS: Release R06 clipboard success and selectable-fallback contract.\n');
}

main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exit(1);
});
