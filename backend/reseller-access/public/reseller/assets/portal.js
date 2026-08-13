(() => {
    "use strict";

    const feedback = document.querySelector("[data-ui-feedback]");
    let feedbackTimer = 0;

    const announce = (message) => {
        if (!feedback) {
            return;
        }
        window.clearTimeout(feedbackTimer);
        feedback.textContent = message;
        feedback.classList.add("is-visible");
        feedbackTimer = window.setTimeout(() => {
            feedback.classList.remove("is-visible");
        }, 2400);
    };

    const valueFromTarget = (targetId) => {
        const target = document.getElementById(targetId);
        if (!target) {
            return "";
        }
        return "value" in target ? String(target.value) : String(target.textContent || "").trim();
    };

    const copyText = async (value) => {
        if (!value) {
            return false;
        }
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(value);
            return true;
        }

        const helper = document.createElement("textarea");
        helper.value = value;
        helper.setAttribute("readonly", "");
        helper.style.position = "fixed";
        helper.style.opacity = "0";
        document.body.appendChild(helper);
        helper.select();
        const copied = document.execCommand("copy");
        helper.remove();
        return copied;
    };

    document.addEventListener("click", async (event) => {
        const clicked = event.target instanceof Element ? event.target : null;
        if (!clicked) {
            return;
        }

        const passwordButton = clicked.closest("[data-password-toggle]");
        if (passwordButton) {
            const input = document.getElementById(passwordButton.getAttribute("aria-controls") || "");
            if (input instanceof HTMLInputElement) {
                const showing = input.type === "text";
                input.type = showing ? "password" : "text";
                passwordButton.textContent = showing ? "إظهار" : "إخفاء";
                passwordButton.setAttribute("aria-pressed", showing ? "false" : "true");
            }
            return;
        }

        const copyButton = clicked.closest("[data-copy-target]");
        if (copyButton) {
            const value = valueFromTarget(copyButton.dataset.copyTarget || "");
            try {
                const copied = await copyText(value);
                announce(copied ? "تم نسخ كود الدخول" : "تعذر نسخ الكود");
            } catch {
                announce("تعذر نسخ الكود");
            }
            return;
        }

        const shareButton = clicked.closest("[data-share-target]");
        if (shareButton) {
            const value = valueFromTarget(shareButton.dataset.shareTarget || "");
            if (!value) {
                return;
            }
            try {
                if (navigator.share) {
                    await navigator.share({
                        title: "كود دخول HULK SA",
                        text: `كود الدخول: ${value}`,
                    });
                } else {
                    await copyText(value);
                    announce("تم نسخ الكود للمشاركة");
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === "AbortError") {
                    return;
                }
                announce("تعذرت مشاركة الكود");
            }
        }
    });

    document.querySelectorAll("[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            const message = form.dataset.confirm || "هل تريد المتابعة؟";
            if (!window.confirm(message)) {
                event.preventDefault();
            }
        });
    });

    document.querySelectorAll("[data-access-code]").forEach((input) => {
        input.addEventListener("input", () => {
            const previousStart = input.selectionStart;
            input.value = input.value.toUpperCase().replace(/[^A-Z0-9-]/g, "");
            if (previousStart !== null) {
                input.setSelectionRange(previousStart, previousStart);
            }
        });
    });

    const searchInput = document.querySelector("[data-reseller-search]");
    const statusFilter = document.querySelector("[data-reseller-status]");
    const cards = Array.from(document.querySelectorAll("[data-reseller-card]"));
    const emptyFilterState = document.querySelector("[data-filter-empty]");

    const filterResellers = () => {
        if (!searchInput || !statusFilter || cards.length === 0) {
            return;
        }
        const query = searchInput.value.trim().toLocaleLowerCase("ar");
        const wantedStatus = statusFilter.value;
        let visibleCount = 0;

        cards.forEach((card) => {
            const text = (card.textContent || "").toLocaleLowerCase("ar");
            const matchesQuery = query === "" || text.includes(query);
            const matchesStatus = wantedStatus === "all" || card.dataset.status === wantedStatus;
            const visible = matchesQuery && matchesStatus;
            card.hidden = !visible;
            if (visible) {
                visibleCount += 1;
            }
        });

        if (emptyFilterState) {
            emptyFilterState.hidden = visibleCount !== 0;
        }
    };

    if (searchInput && statusFilter) {
        searchInput.addEventListener("input", filterResellers);
        statusFilter.addEventListener("change", filterResellers);
    }
})();
