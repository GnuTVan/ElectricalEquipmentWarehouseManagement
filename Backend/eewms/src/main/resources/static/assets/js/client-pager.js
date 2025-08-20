(function () {
    // 'keep' | 'toTop' | 'toTable' | 'toPager'
    const SCROLL_MODE = 'toTable'; // đổi thành 'toPager' hoặc 'toTable' nếu muốn cuộn mượt

    function initPager(wrapper) {
        function smoothScroll() {
            if (SCROLL_MODE === 'keep') return;

            const behavior = { behavior: 'smooth', block: 'start' };

            if (SCROLL_MODE === 'toTop') {
                window.scrollTo({ top: 0, behavior: 'smooth' });
                return;
            }
            if (SCROLL_MODE === 'toTable') {
                table?.scrollIntoView(behavior);
                return;
            }
            if (SCROLL_MODE === 'toPager') {
                wrapper?.scrollIntoView({ behavior: 'smooth', block: 'end' });
                return;
            }
        }

        const targetId = wrapper.getAttribute('data-target');
        const table = document.getElementById(targetId);
        if (!table) return;

        const tbody = table.querySelector('tbody');
        if (!tbody) return;

        const rows = Array.from(tbody.querySelectorAll('tr'));
        if (rows.length === 0) return;

        const ulPages = wrapper.querySelector('.pages');
        const info = wrapper.querySelector('.info');
        const sizeSelect = wrapper.querySelector('.page-size');

        const initSize = parseInt(wrapper.getAttribute('data-pagesize') || sizeSelect.value, 10) || 10;
        sizeSelect.value = String(initSize);

        let pageSize = initSize;
        let page = 0;

        const totalPages = () => Math.max(1, Math.ceil(rows.length / pageSize));

        function renderPageButtons() {
            const tp = totalPages();
            ulPages.innerHTML = '';

            // class cơ bản cho nút với hover xanh
            const BASE_BTN =
                'px-3 py-1 border rounded transition-colors ' +
                'hover:bg-blue-600 hover:text-white hover:border-blue-600';

            const mkBtn = (label, onClick, extra = '') => {
                const b = document.createElement('button');
                b.type = 'button';
                b.textContent = label;
                b.className = BASE_BTN + (extra ? (' ' + extra) : '');
                if (onClick) b.addEventListener('click', onClick);
                const li = document.createElement('li');
                li.appendChild(b);
                ulPages.appendChild(li);
            };
            const dots = () => {
                const s = document.createElement('span');
                s.textContent = '…';
                s.className = 'px-2';
                ulPages.appendChild(s);
            };

            const MAX_WIN = 10;
            if (tp <= 0) return;

            // Tính cửa sổ: ghim trang hiện tại ở giữa khi có thể
            let start, end;
            const half = MAX_WIN / 2; // 5
            start = Math.floor(page - (half - 1));
            end   = Math.floor(page + half);
            if (start < 0) { end += -start; start = 0; }
            if (end > tp - 1) { start -= (end - (tp - 1)); end = tp - 1; }
            if (start < 0) start = 0;
            if (tp < MAX_WIN) { start = 0; end = tp - 1; }
            if (end - start + 1 > MAX_WIN) start = end - (MAX_WIN - 1);

            // Không ở đầu -> hiện Đầu/Trước
            if (page > 0) {
                mkBtn('Đầu',   () => { page = 0; render();smoothScroll(); });
                mkBtn('Trước', () => { page = Math.max(0, page - 1); render();smoothScroll(); });
            }

            // Trước cửa sổ
            if (start > 0) {
                mkBtn('1', () => { page = 0; render();smoothScroll(); });
                if (start > 1) dots();
            }

            // Các số trong cửa sổ
            for (let i = start; i <= end; i++) {
                const isCurrent = i === page;
                mkBtn(String(i + 1),
                    isCurrent ? null : () => { page = i; render();smoothScroll(); },
                    isCurrent ? 'bg-blue-600 text-white border-blue-600' : ''
                );
            }

            // Sau cửa sổ
            if (end < tp - 1) {
                if (end < tp - 2) dots();
                mkBtn(String(tp), () => { page = tp - 1; render(); smoothScroll();});
            }

            // Không ở cuối -> hiện Tiếp/Cuối
            if (page < tp - 1) {
                mkBtn('Tiếp', () => { page = Math.min(tp - 1, page + 1); render();smoothScroll(); });
                mkBtn('Cuối', () => { page = tp - 1; render();smoothScroll(); });
            }
        }

        function renderRows() {
            rows.forEach(r => (r.style.display = 'none'));
            const start = page * pageSize;
            rows.slice(start, start + pageSize).forEach(r => (r.style.display = ''));
        }

        function renderInfo() {
            info.textContent = `Trang ${page + 1}/${totalPages()} • ${rows.length} bản ghi`;
        }

        function render() {
            renderRows();
            renderPageButtons();
            renderInfo();
        }

        sizeSelect?.addEventListener('change', () => {
            const v = parseInt(sizeSelect.value, 10);
            if (!isNaN(v) && v > 0) { pageSize = v; page = 0; render();smoothScroll(); }
        });

        render();smoothScroll();
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.client-pager').forEach(initPager);
    });
})();
