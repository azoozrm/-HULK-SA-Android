'use strict';

(() => {
    const MAX_SAVED_FILTERS = 10;

    const initializeNavigation = () => {
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
            if (mobileQuery.matches && event.target.closest('a')) {
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
    };

    const initializeConfirmations = () => {
        document.querySelectorAll('form[data-confirm]').forEach((form) => {
            form.addEventListener('submit', (event) => {
                const message = form.getAttribute('data-confirm') || 'هل تريد تنفيذ هذا الإجراء؟';
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });
    };

    const initializeResponsiveTables = () => {
        document.querySelectorAll('table[data-mobile-cards]').forEach((table) => {
            const headings = Array.from(table.querySelectorAll('thead th')).map((heading) =>
                (heading.textContent || '').trim() || 'التفاصيل'
            );
            table.querySelectorAll('tbody tr').forEach((row) => {
                Array.from(row.children).forEach((cell, index) => {
                    cell.setAttribute('data-label', headings[index] || 'التفاصيل');
                });
            });
            table.classList.add('is-mobile-card-ready');
            const scroll = table.closest('.table-scroll');
            if (scroll) {
                scroll.classList.add('is-mobile-card-ready');
            }
        });
    };

    const initializeSavedFilters = () => {
        document.querySelectorAll('form[data-saved-filters]').forEach((form) => {
            const module = form.getAttribute('data-saved-filters') || '';
            const allowed = (form.getAttribute('data-saved-filter-fields') || '')
                .split(',')
                .map((value) => value.trim())
                .filter((value) => /^[a-z_]{1,32}$/.test(value));
            const controls = form.querySelector('[data-saved-filter-controls]');
            const nameInput = form.querySelector('[data-saved-filter-name]');
            const select = form.querySelector('[data-saved-filter-select]');
            const saveButton = form.querySelector('[data-saved-filter-save]');
            const applyButton = form.querySelector('[data-saved-filter-apply]');
            const deleteButton = form.querySelector('[data-saved-filter-delete]');
            const status = form.querySelector('[data-saved-filter-status]');
            if (
                !/^[a-z0-9-]{1,40}$/.test(module)
                || allowed.length === 0
                || !controls
                || !nameInput
                || !select
                || !saveButton
                || !applyButton
                || !deleteButton
                || !status
            ) {
                return;
            }

            const storageKey = `hulk-cc:saved-filters:${module}`;
            const safeSavedValue = (value) =>
                typeof value === 'string' && /^[A-Za-z0-9_.:-]{1,40}$/.test(value) ? value : '';
            const setStatus = (message) => {
                status.textContent = message;
            };
            const load = () => {
                try {
                    const parsed = JSON.parse(window.localStorage.getItem(storageKey) || '[]');
                    return Array.isArray(parsed)
                        ? parsed.filter((item) => item && typeof item.name === 'string' && item.values && typeof item.values === 'object').slice(0, MAX_SAVED_FILTERS)
                        : [];
                } catch (_error) {
                    setStatus('تعذر قراءة العروض المحفوظة في هذا المتصفح.');
                    return [];
                }
            };
            const persist = (items) => {
                try {
                    window.localStorage.setItem(storageKey, JSON.stringify(items.slice(0, MAX_SAVED_FILTERS)));
                    return true;
                } catch (_error) {
                    setStatus('التخزين المحلي غير متاح في هذا المتصفح.');
                    return false;
                }
            };
            const render = (items, selected = '') => {
                select.replaceChildren();
                const placeholder = document.createElement('option');
                placeholder.value = '';
                placeholder.textContent = 'اختر عرضًا محفوظًا';
                select.appendChild(placeholder);
                items.forEach((item, index) => {
                    const option = document.createElement('option');
                    option.value = String(index);
                    option.textContent = item.name;
                    option.selected = String(index) === selected;
                    select.appendChild(option);
                });
            };
            const collect = () => {
                const values = {};
                allowed.forEach((fieldName) => {
                    const field = form.elements.namedItem(fieldName);
                    const value = field ? safeSavedValue(field.value) : '';
                    if (value !== '') {
                        values[fieldName] = value;
                    }
                });
                return values;
            };

            saveButton.addEventListener('click', () => {
                const name = nameInput.value.trim();
                if (!/^[\p{L}\p{N} _.-]{1,40}$/u.test(name)) {
                    setStatus('اكتب اسمًا قصيرًا من الحروف أو الأرقام لحفظ العرض.');
                    nameInput.focus();
                    return;
                }
                const items = load().filter((item) => item.name !== name);
                items.unshift({name, values: collect()});
                if (persist(items)) {
                    render(items, '0');
                    setStatus('تم حفظ الفلاتر الآمنة فقط في هذا المتصفح.');
                }
            });
            applyButton.addEventListener('click', () => {
                const index = Number.parseInt(select.value, 10);
                const item = load()[index];
                if (!item) {
                    setStatus('اختر عرضًا محفوظًا أولًا.');
                    return;
                }
                allowed.forEach((fieldName) => {
                    const field = form.elements.namedItem(fieldName);
                    if (field && typeof field.value === 'string') {
                        field.value = safeSavedValue(item.values[fieldName]);
                    }
                });
                form.requestSubmit();
            });
            deleteButton.addEventListener('click', () => {
                const index = Number.parseInt(select.value, 10);
                const items = load();
                if (!items[index]) {
                    setStatus('اختر عرضًا محفوظًا لحذفه.');
                    return;
                }
                items.splice(index, 1);
                if (persist(items)) {
                    render(items);
                    setStatus('تم حذف العرض المحفوظ.');
                }
            });
            render(load());
        });
    };

    initializeNavigation();
    initializeConfirmations();
    initializeResponsiveTables();
    initializeSavedFilters();
})();
