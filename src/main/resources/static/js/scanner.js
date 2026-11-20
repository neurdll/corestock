(() => {
    const SCAN_GAP_MS = 60;
    let buffer = "";
    let lastKeyAt = 0;

    // Prevent hardware HID scanner Enter suffix from prematurely submitting SKU scan inputs;
    // instead advance focus to quantity or append scanned serials cleanly.
    document.addEventListener("keydown", (event) => {
        const target = event.target;
        if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) {
            return;
        }
        const now = Date.now();
        const rapid = now - lastKeyAt <= SCAN_GAP_MS;
        lastKeyAt = now;

        if (event.key === "Enter" && target.matches("[data-scan-target]")) {
            event.preventDefault();
            const form = target.closest("form");
            if (!form) return;
            const serialBox = form.querySelector("[data-scan-append]");
            const qtyInput = form.querySelector('input[name="quantity"]');
            if (qtyInput && target.value.trim().length > 0) {
                qtyInput.focus();
                qtyInput.select();
            } else if (serialBox) {
                serialBox.focus();
            }
            buffer = "";
            return;
        }

        if (event.key.length === 1) {
            buffer = rapid ? buffer + event.key : event.key;
        }
    });
})();
