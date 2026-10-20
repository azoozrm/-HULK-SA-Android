'use strict';

(() => {
    const MAX_SAVED_FILTERS = 10;

    const initializePrimaryTabs = () => {
        const track = document.querySelector('[data-primary-tabs]');
        if (!track) {
            return;
        }
        const active = track.querySelector('.primary-tab.is-active');
        if (
            active
            && track.scrollWidth > track.clientWidth
            && typeof active.scrollIntoView === 'function'
        ) {
            active.scrollIntoView({block: 'nearest', inline: 'center'});
        }
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

    const initializeSubmitOnce = () => {
        document.querySelectorAll('form[data-submit-once]').forEach((form) => {
            form.addEventListener('submit', (event) => {
                if (event.defaultPrevented) {
                    return;
                }
                form.querySelectorAll('button[type="submit"]').forEach((button) => {
                    button.disabled = true;
                    button.setAttribute('aria-disabled', 'true');
                });
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

    const initializeReleaseUpload = () => {
        const panel = document.getElementById('release-upload-panel');
        if (!panel) {
            return;
        }
        const openers = Array.from(document.querySelectorAll('[data-r6-open-upload]'));
        const closers = Array.from(document.querySelectorAll('[data-r6-close-upload]'));
        const fileInput = document.getElementById('r6-apk-file');
        const fileHint = document.getElementById('r6-file-hint');
        const required = document.querySelector('[data-r6-required]');
        const versionCode = document.getElementById('r6-version-code');
        const minimumCode = document.getElementById('r6-min-code');
        const policyHelp = document.getElementById('r6-policy-help');

        const open = (event) => {
            if (event) {
                event.preventDefault();
            }
            panel.hidden = false;
            if (fileInput && typeof fileInput.focus === 'function') {
                fileInput.focus({preventScroll: true});
            }
            if (typeof panel.scrollIntoView === 'function') {
                panel.scrollIntoView({block: 'start'});
            }
        };
        const close = (event) => {
            if (event) {
                event.preventDefault();
            }
            panel.hidden = true;
            const opener = openers[0];
            if (opener && typeof opener.focus === 'function') {
                opener.focus({preventScroll: true});
            }
        };
        openers.forEach((opener) => opener.addEventListener('click', open));
        closers.forEach((closer) => closer.addEventListener('click', close));

        if (fileInput && fileHint) {
            fileInput.addEventListener('change', () => {
                const file = fileInput.files && fileInput.files[0];
                fileHint.textContent = file
                    ? `${file.name} · ${(file.size / 1000000).toFixed(1)} MB`
                    : 'اختر ملفًا بامتداد .apk — لا يُنشر بمجرد اختياره.';
            });
        }

        if (required && versionCode && minimumCode) {
            const syncPolicy = () => {
                minimumCode.readOnly = required.checked;
                if (required.checked && versionCode.value !== '') {
                    minimumCode.value = versionCode.value;
                }
                if (policyHelp) {
                    policyHelp.textContent = required.checked
                        ? 'يجب على المستخدم تحديث التطبيق للمتابعة.'
                        : 'يمكن للمستخدم تأجيل هذا التحديث، ما لم يكن أقل من الحد الأدنى المدعوم.';
                }
            };
            required.addEventListener('change', syncPolicy);
            versionCode.addEventListener('input', syncPolicy);
            syncPolicy();
        }
    };

    const initializeReleaseCopy = () => {
        const copyText = async (button) => {
            const value = button.getAttribute('data-copy-value') || '';
            if (value === '') {
                return;
            }
            const fallbackId = button.getAttribute('data-copy-fallback') || '';
            const statusId = button.getAttribute('data-copy-status') || '';
            const fallback = fallbackId ? document.getElementById(fallbackId) : null;
            const status = statusId ? document.getElementById(statusId) : null;
            const setStatus = (message, state) => {
                if (!status) {
                    return;
                }
                status.textContent = message;
                if (state) {
                    status.setAttribute('data-copy-state', state);
                } else {
                    status.removeAttribute('data-copy-state');
                }
            };

            try {
                if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') {
                    throw new Error('clipboard unavailable');
                }
                await navigator.clipboard.writeText(value);
                setStatus('تم نسخ الرابط.', 'success');
            } catch (_error) {
                if (fallback) {
                    fallback.hidden = false;
                    fallback.value = value;
                    fallback.focus();
                    if (typeof fallback.select === 'function') {
                        fallback.select();
                    }
                }
                setStatus('تعذر النسخ التلقائي. الرابط محدد وجاهز للنسخ اليدوي.', 'fallback');
            }
        };

        document.querySelectorAll('[data-copy-value]').forEach((button) => {
            button.addEventListener('click', () => {
                void copyText(button);
            });
        });
    };

    const initializeReleaseRiskConfirmation = () => {
        const confirmation = document.querySelector('[data-r6-min-confirm]');
        const publish = document.querySelector('[data-r6-publish]');
        if (!confirmation || !publish) {
            return;
        }
        const sync = () => {
            publish.disabled = !confirmation.checked;
        };
        confirmation.addEventListener('change', sync);
        sync();
    };

    initializePrimaryTabs();
    initializeConfirmations();
    initializeSubmitOnce();
    initializeResponsiveTables();
    initializeSavedFilters();
    initializeReleaseUpload();
    initializeReleaseCopy();
    initializeReleaseRiskConfirmation();
})();
