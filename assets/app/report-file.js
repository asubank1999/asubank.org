// "Report this file" on pages that list uploads. Any button with data-report-url opens the form. No account needed.
(function () {
    var API = 'https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec';
    var REASONS = [['spam', 'Spam or advertising'], ['sexual', 'Sexual content'], ['violence', 'Violence or threats'], ['privacy', "Shares someone's private information"],
        ['copyright', 'Copyright: I own this or represent the owner'], ['wrong', 'Wrong, broken or misleading file'], ['other', 'Something else']];

    function el(tag, props, kids) {
        var e = document.createElement(tag);
        Object.keys(props || {}).forEach(function (k) { e[k] = props[k]; });
        (kids || []).forEach(function (c) { e.appendChild(c); });
        return e;
    }

    function open(url, name) {
        var pick = el('select', { required: true, ariaLabel: "What's wrong?", style: 'width:100%;padding:10px;margin:8px 0;border-radius:10px' }, [el('option', { value: '', textContent: "What's wrong?" })]
            .concat(REASONS.map(function (r) { return el('option', { value: r[0], textContent: r[1] }); })));
        var details = el('textarea', { rows: 3, maxLength: 1000, placeholder: 'Details (optional)', style: 'width:100%;padding:10px;border-radius:10px;box-sizing:border-box' });
        var msg = el('p', { role: 'alert', style: 'margin:8px 0;color:#b00020' });
        var send = el('button', { type: 'submit', textContent: 'Send report', style: 'padding:10px 18px;border-radius:10px;border:0;background:#1d4ed8;color:#fff;font-weight:600;cursor:pointer' });
        var cancel = el('button', { type: 'button', textContent: 'Cancel', style: 'padding:10px 18px;border-radius:10px;border:0;margin-left:8px;cursor:pointer' });
        var form = el('form', { method: 'dialog' }, [
            el('h3', { textContent: 'Report this file', style: 'margin:0 0 6px' }),
            el('p', { textContent: name, style: 'margin:0 0 8px;font-size:.9rem;opacity:.75;overflow-wrap:anywhere' }),
            el('div', { style: 'margin:0 0 10px;padding:10px 12px;border-radius:12px;background:rgba(29,78,216,.08);font-size:.88rem;line-height:1.45' }, [
                el('b', { textContent: 'Before you report a study file' }),
                el('p', { textContent: "Most study files here come from the university itself or were shared by students, through links that were already public. The collection was reviewed and approved by one of the university's deanships.", style: 'margin:6px 0' }),
                el('p', { style: 'margin:0' }, [
                    document.createTextNode('If you want a file removed, '),
                    el('a', { href: 'mailto:necro144@yahoo.com?subject=' + encodeURIComponent('UniHub file removal request'), textContent: 'email the owner' }),
                    document.createTextNode(" and you will get an answer. The WhatsApp community moderators don't manage uploads, so please don't ask them.")
                ])
            ]),
            pick, details, msg, send, cancel
        ]);
        var box = el('dialog', { style: 'border:0;border-radius:16px;padding:20px;width:min(420px,90vw)' }, [form]);
        cancel.onclick = function () { box.close(); box.remove(); };
        form.onsubmit = function (ev) {
            ev.preventDefault();
            send.disabled = true;
            msg.textContent = '';
            fetch(API, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' },
                body: JSON.stringify({ action: 'report', kind: 'file', targetId: url, targetName: name, reason: pick.value, details: details.value }) })
                .then(function (r) { return r.json(); })
                .then(function (r) {
                    if (!r.ok) throw new Error(r.error || 'Something went wrong.');
                    form.textContent = 'Thank you. Our team will review your report.';
                    form.appendChild(el('br'));
                    cancel.textContent = 'Close';
                    form.appendChild(cancel);
                })
                .catch(function (e) { msg.textContent = e.message; send.disabled = false; });
        };
        document.body.appendChild(box);
        box.showModal();
    }

    // runs before the card's own click, so reporting doesn't open the file
    document.addEventListener('click', function (ev) {
        var b = ev.target.closest && ev.target.closest('[data-report-url]');
        if (!b) return;
        ev.preventDefault();
        ev.stopPropagation();
        open(b.getAttribute('data-report-url'), b.getAttribute('data-report-name') || '');
    }, true);
})();
