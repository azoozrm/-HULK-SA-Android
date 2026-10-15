/* Public TV-activation page: fragment code prefill and password visibility only. */
(function () {
    'use strict';

    function prefillFromFragment() {
        var codeInput = document.getElementById('pairing-code');
        if (!codeInput || codeInput.value !== '') {
            return;
        }
        var hash = window.location.hash || '';
        var match = /^#C=([A-Za-z0-9-]{1,16})$/.exec(hash);
        if (match) {
            codeInput.value = match[1].toUpperCase();
        }
    }

    function bindPasswordToggle() {
        var toggle = document.getElementById('pairing-show-password');
        var password = document.getElementById('pairing-password');
        if (!toggle || !password) {
            return;
        }
        toggle.addEventListener('change', function () {
            password.type = toggle.checked ? 'text' : 'password';
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            prefillFromFragment();
            bindPasswordToggle();
        });
    } else {
        prefillFromFragment();
        bindPasswordToggle();
    }
})();
