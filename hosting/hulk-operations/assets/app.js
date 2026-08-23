const apkUploadInput = document.querySelector('input[type="file"][name="apk"]');
if (apkUploadInput) {
    // iOS Safari greys out APK files when an Android-specific accept filter is present.
    // Keep selection unrestricted in the browser; the backend still validates extension,
    // MIME, ZIP signature, AndroidManifest.xml, classes.dex, size and SHA-256.
    apkUploadInput.removeAttribute('accept');
}

const releaseRows = document.querySelectorAll('table tbody tr');
releaseRows.forEach(function (row) {
    const releaseIdInput = row.querySelector('input[name="release_id"]');
    const csrfInput = row.querySelector('input[name="csrf_token"]');
    const actionsContainer = row.querySelector('td:last-child .actions');

    if (!releaseIdInput || !csrfInput || !actionsContainer || actionsContainer.querySelector('[data-delete-release]')) {
        return;
    }

    const form = document.createElement('form');
    form.method = 'post';
    form.action = 'delete_release.php';
    form.setAttribute('data-confirm', 'هل تريد حذف هذا الإصدار نهائيًا؟ سيتم حذف سجل الإصدار وملف APK من الاستضافة، وإذا كان منشورًا ستعود سياسة التحديث إلى الوضع الآمن 0.9.3.20 / 64.');
    form.setAttribute('data-delete-release', '1');

    const csrf = document.createElement('input');
    csrf.type = 'hidden';
    csrf.name = 'csrf_token';
    csrf.value = csrfInput.value;

    const releaseId = document.createElement('input');
    releaseId.type = 'hidden';
    releaseId.name = 'release_id';
    releaseId.value = releaseIdInput.value;

    const button = document.createElement('button');
    button.type = 'submit';
    button.className = 'button danger';
    button.textContent = 'حذف';

    form.appendChild(csrf);
    form.appendChild(releaseId);
    form.appendChild(button);
    actionsContainer.appendChild(form);
});

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
    const growthCommand = event.submitter?.getAttribute('name') === 'growth_command'
        ? event.submitter.value
        : '';
    let message = form.getAttribute('data-confirm');
    if (action === 'update_service_status' && form.querySelector('[name="status"]')?.value === 'MAINTENANCE') {
        message = 'هل تريد تشغيل وضع الصيانة؟';
    }
    if (action === 'update_release_policy' && form.querySelector('[name="required"]')?.checked) {
        message = 'هل تريد تفعيل التحديث الإجباري؟';
    }
    if (growthCommand === 'delete_renewal_qr' || growthCommand === 'delete_support_qr') {
        message = 'هل تريد حذف QR المخصص والعودة إلى الوضع التلقائي؟';
    }
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
