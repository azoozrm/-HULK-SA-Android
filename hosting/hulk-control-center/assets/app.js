'use strict';

(() => {
    const sidebar = document.querySelector('[data-sidebar]');
    const openButton = document.querySelector('[data-nav-open]');
    const closeButton = document.querySelector('[data-nav-close]');
    const backdrop = document.querySelector('[data-nav-backdrop]');
    const workspace = document.querySelector('[data-workspace]');
    const mobileQuery = window.matchMedia('(max-width: 860px)');

    if (!sidebar || !openButton || !closeButton || !backdrop || !workspace) {
        return;
    }

    let lastFocused = null;

    const focusableSelector = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])',
    ].join(',');

    const setOpen = (open, restoreFocus = true) => {
        const mobile = mobileQuery.matches;
        open = mobile && open;
        if (open) {
            lastFocused = document.activeElement;
        }

        sidebar.classList.toggle('is-open', open);
        backdrop.hidden = !open;
        document.body.classList.toggle('nav-is-open', open);
        openButton.setAttribute('aria-expanded', open ? 'true' : 'false');

        if (mobile) {
            if (open) {
                sidebar.removeAttribute('inert');
                sidebar.setAttribute('aria-hidden', 'false');
                sidebar.setAttribute('role', 'dialog');
                sidebar.setAttribute('aria-modal', 'true');
                closeButton.focus();
                workspace.setAttribute('inert', '');
                workspace.setAttribute('aria-hidden', 'true');
            } else {
                workspace.removeAttribute('aria-hidden');
                workspace.removeAttribute('inert');
                sidebar.setAttribute('inert', '');
                sidebar.setAttribute('aria-hidden', 'true');
                sidebar.removeAttribute('role');
                sidebar.removeAttribute('aria-modal');
            }
        } else {
            sidebar.removeAttribute('inert');
            sidebar.removeAttribute('aria-hidden');
            sidebar.removeAttribute('role');
            sidebar.removeAttribute('aria-modal');
            workspace.removeAttribute('inert');
            workspace.removeAttribute('aria-hidden');
        }

        if (!open && restoreFocus && lastFocused instanceof HTMLElement) {
            lastFocused.focus();
        }
    };

    openButton.addEventListener('click', () => setOpen(true));
    closeButton.addEventListener('click', () => setOpen(false));
    backdrop.addEventListener('click', () => setOpen(false));

    sidebar.addEventListener('click', (event) => {
        if (window.matchMedia('(max-width: 860px)').matches && event.target.closest('a')) {
            setOpen(false);
        }
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && sidebar.classList.contains('is-open')) {
            setOpen(false);
            return;
        }

        if (event.key !== 'Tab' || !mobileQuery.matches || !sidebar.classList.contains('is-open')) {
            return;
        }

        const focusable = Array.from(sidebar.querySelectorAll(focusableSelector));
        if (focusable.length === 0) {
            event.preventDefault();
            closeButton.focus();
            return;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    mobileQuery.addEventListener('change', () => setOpen(false, false));
    setOpen(false, false);
})();
