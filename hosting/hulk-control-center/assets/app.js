'use strict';

(() => {
    const sidebar = document.querySelector('[data-sidebar]');
    const openButton = document.querySelector('[data-nav-open]');
    const closeButton = document.querySelector('[data-nav-close]');
    const backdrop = document.querySelector('[data-nav-backdrop]');

    if (!sidebar || !openButton || !closeButton || !backdrop) {
        return;
    }

    let lastFocused = null;

    const setOpen = (open) => {
        sidebar.classList.toggle('is-open', open);
        backdrop.hidden = !open;
        document.body.classList.toggle('nav-is-open', open);
        openButton.setAttribute('aria-expanded', open ? 'true' : 'false');

        if (open) {
            lastFocused = document.activeElement;
            closeButton.focus();
        } else if (lastFocused instanceof HTMLElement) {
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
        }
    });

    window.matchMedia('(min-width: 861px)').addEventListener('change', (event) => {
        if (event.matches) {
            setOpen(false);
        }
    });
})();
