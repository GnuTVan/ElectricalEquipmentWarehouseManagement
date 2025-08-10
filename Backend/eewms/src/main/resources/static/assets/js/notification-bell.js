(function () {
    const btn = document.getElementById('btnNotif');
    const dd = document.getElementById('notifDropdown');
    const list = document.getElementById('notifList');
    const badge = document.getElementById('notifBadge');
    const clearAll = document.getElementById('btnClearAll');

    if (!btn || !dd || !list) return;

    const csrfH = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfT = document.querySelector('meta[name="_csrf"]')?.content;

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

// Refresh badge khi init (không cần mở dropdown)
    async function refreshBadge(){
        try{
            const r = await fetch('/admin/notifications?limit=20');
            const data = await r.json();
            setBadge(Array.isArray(data) ? data.length : 0);
        }catch(e){}
    }
    refreshBadge();

    function headers(json = true) {
        const h = json ? {'Content-Type': 'application/json'} : {};
        if (csrfH && csrfT) h[csrfH] = csrfT;
        return h;
    }

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
        return (s || '').replace(/[&<>"']/g, m => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[m]));
    }

    function fmtTime(s) {
        try {
            return new Date(s).toLocaleString();
        } catch (e) {
            return '';
        }
    }

    async function load() {
        const r = await fetch('/admin/notifications?limit=20');
        const data = await r.json();

        if (Array.isArray(data) && data.length > 0) {
            setBadge(data.length);
        } else {
            setBadge(0);
        }

        list.innerHTML = (data && data.length)
            ? data.map(n => `
        <div class="flex items-start gap-2 px-3 py-2">
          <i class="ri-${icon(n.type)} text-base mt-0.5"></i>
          <div class="flex-1">
            <div class="text-sm">${esc(n.message)}</div>
            <div class="text-xs text-gray-500">${fmtTime(n.createdAt)}</div>
          </div>
          <button type="button" data-id="${n.id}" title="Xoá"
                  class="ri-close-line text-gray-400 hover:text-red-500"></button>
        </div>`).join('')
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

    //handler xóa từng thông báo
    list.addEventListener('click', async (e) => {
        const id = e.target.getAttribute('data-id');
        if (id) {
            const cur = parseInt(badge?.textContent || '0', 10) || 0;
            if(cur > 0) setBadge(cur - 1);

            await fetch(`/admin/notifications/${id}`, {method: 'DELETE', headers: headers(false)});
            await load();
        }
    });
    //handler xóa tất cả
    clearAll?.addEventListener('click', async () => {
        setBadge(0);
        await fetch('/admin/notifications', {method: 'DELETE', headers: headers(false)});
        await load();
    });
})();
