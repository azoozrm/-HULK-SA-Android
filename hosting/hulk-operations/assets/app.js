document.addEventListener('click', function (event) {
    const copyButton = event.target.closest('[data-copy]');
    if (copyButton) {
        const value = copyButton.getAttribute('data-copy') || '';
        navigator.clipboard.writeText(value).then(function () {
            const original = copyButton.textContent;
            copyButton.textContent = 'تم النسخ';
            window.setTimeout(function () { copyButton.textContent = original; }, 1400);
        });
    }
});

document.addEventListener('submit', function (event) {
    const form = event.target;
    const action = form.querySelector('[name="action"]')?.value || '';
    let message = form.getAttribute('data-confirm');
    if (action === 'update_service_status' && form.querySelector('[name="status"]')?.value === 'MAINTENANCE') {
        message = 'هل تريد تشغيل وضع الصيانة؟';
    }
    if (action === 'update_release_policy' && form.querySelector('[name="required"]')?.checked) {
        message = 'هل تريد تفعيل التحديث الإجباري؟';
    }
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
