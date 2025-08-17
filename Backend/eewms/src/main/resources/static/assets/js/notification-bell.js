(function () {
    const btn = document.getElementById('btnNotif');
    const dd = document.getElementById('notifDropdown');
    const list = document.getElementById('notifList');
    const badge = document.getElementById('notifBadge');
    const clearAll = document.getElementById('btnClearAll');

    if (!btn || !dd || !list) return;

    const csrfH = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfT = document.querySelector('meta[name="_csrf"]')?.content;

    function headers(json = true) {
        const h = json ? {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        } : {'Accept': 'application/json'};
        if (csrfH && csrfT) h[csrfH] = csrfT;
        return h;
    }

    // === badge helpers ===
    function setBadge(n) {
        if (!badge) return;
        n = parseInt(n || 0, 10) || 0;
        if (n > 0) {
            badge.textContent = n;
            badge.classList.remove('hidden');
        } else {
            badge.textContent = '0';
            badge.classList.add('hidden');
        }
    }

    // helper: toast lỗi (dùng toastr nếu có)
    function toastError(msg) {
        try {
            window.toastr?.error?.(msg);
        } catch {
        }
    }

    // Expose cho toast-script gọi sau khi POST log
    window.__notif_inc = function () {
        const cur = parseInt(badge?.textContent || '0', 10) || 0;
        setBadge(cur + 1);
    };

    // Nếu toast đã bắn trước khi file này load → tăng bù một lần
    if (sessionStorage.getItem('notif_inc_pending') === '1') {
        sessionStorage.removeItem('notif_inc_pending');
        const cur = parseInt(badge?.textContent || '0', 10) || 0;
        setBadge(cur + 1);
    }

    // === icon / format ===
    function icon(t) {
        switch ((t || 'info').toLowerCase()) {
            case 'success':
                return 'checkbox-circle-line';
            case 'error':
                return 'error-warning-line';
            case 'warning':
                return 'alert-line';
            default:
                return 'information-line';
        }
    }

    function esc(s) {
        return (s || '').replace(/[&<>"']/g, m => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[m]));
    }

    function fmtTime(s) {
        try {
            return new Date(s).toLocaleString();
        } catch {
            return '';
        }
    }

    // === API wrappers ===
    async function getUnreadCount() {
        const r = await fetch('/admin/notifications/unread-count', {headers: headers(false)});
        if (!r.ok) return 0;
        const j = await r.json();
        return j?.count || 0;
    }

    async function getMy(page = 0, size = 20) {
        const qs = new URLSearchParams({page, size});
        const r = await fetch('/admin/notifications/my?' + qs.toString(), {headers: headers(false)});
        if (!r.ok) return {content: []};
        return r.json();
    }

    async function markRead(userNotificationId) {
        return fetch(`/admin/notifications/${userNotificationId}/read`, {method: 'POST', headers: headers(false)});
    }

    async function markAllRead() {
        return fetch('/admin/notifications/read-all', {method: 'POST', headers: headers(false)});
    }

    // Refresh badge khi init (không cần mở dropdown)
    async function refreshBadge() {
        try {
            const c = await getUnreadCount();
            setBadge(c);
        } catch (e) { /* noop */
        }
    }

    refreshBadge();

    // === render list ===
    async function load() {
        const data = await getMy(0, 20);
        const items = Array.isArray(data?.content) ? data.content : [];

        // badge = số chưa đọc thực tế
        refreshBadge().catch(() => {
        });

        list.innerHTML = items.length
            ? items.map(n => {
                const readClass = n.read ? 'opacity-60' : 'font-medium';
                return `
        <div class="flex items-start gap-2 px-3 py-2" data-rowid="${n.userNotificationId}">
          <i class="ri-${icon(n.type)} text-base mt-0.5"></i>
          <div class="flex-1 ${readClass}">
            <div class="text-sm">${esc(n.message)}</div>
            <div class="text-xs text-gray-500">${fmtTime(n.createdAt)}</div>
          </div>
          <button type="button" data-delid="${n.userNotificationId}" title="Xoá thông báo"
                  class="text-xs px-2 py-1 rounded bg-gray-100 hover:bg-gray-200">Xóa</button>
        </div>`;
            }).join('')
            : `<div class="px-3 py-2 text-sm text-gray-500">Không có thông báo</div>`;
    }

    btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        dd.classList.toggle('hidden');
        if (!dd.classList.contains('hidden')) await load();
    });

    document.addEventListener('click', (e) => {
        if (!dd.contains(e.target) && !btn.contains(e.target)) {
            dd.classList.add('hidden');
        }
    });

    // handler: xoá từng thông báo (optimistic UI + rollback khi lỗi)
    list.addEventListener('click', async (e) => {
        const delId = e.target.getAttribute('data-delid');
        if (!delId) return;

        const row = e.target.closest('[data-rowid]');
        const snapshotHTML = row?.outerHTML; // để rollback nếu cần
        try {
            // Optimistic: ẩn item ngay
            if (row) row.remove();

            const r = await fetch(`/admin/notifications/my/${delId}`, {method: 'DELETE', headers: headers(false)});

            if (!r.ok) {
                // rollback UI
                if (snapshotHTML) {
                    const tmp = document.createElement('div');
                    tmp.innerHTML = snapshotHTML;
                    const restored = tmp.firstElementChild;
                    if (restored) list.prepend(restored);
                }
                toastError('Xoá thông báo thất bại.');
                return;
            }
            await refreshBadge();
            // Nếu danh sách trống → nạp lại để hiện “Không có thông báo”
            if (!list.children.length) await load();
        } catch {
            // rollback UI
            if (snapshotHTML) {
                const tmp = document.createElement('div');
                tmp.innerHTML = snapshotHTML;
                const restored = tmp.firstElementChild;
                if (restored) list.prepend(restored);
            }
            toastError('Có lỗi mạng. Vui lòng thử lại.');
        }
    });

    // handler: đánh dấu tất cả đã đọc
    clearAll?.addEventListener('click', async () => {
        await markAllRead();
        await Promise.all([refreshBadge(), load()]);
    });

    // Auto refresh badge mỗi 60s
    setInterval(refreshBadge, 60000);
})();
