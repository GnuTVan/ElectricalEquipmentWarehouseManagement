(function(){
    // Lọc sau khi người dùng ngừng gõ 0.5s
    const DEBOUNCE = 500;

    // Chuẩn hóa không dấu + lowercase
    const norm = (s) => (s || "")
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '') // bỏ dấu
        .toLowerCase().trim();

    function initAutoSuggest(inputId, listId){
        const input = document.getElementById(inputId);
        const list  = document.getElementById(listId);
        if(!input || !list) return;

        let timer = null, lastQ = '', current = [];
        let reqId = 0; // chống race condition

        const format = (b) => `${(b.shortName || b.code || '').trim()} - ${b.name}`;

        const render = (items)=>{
            list.innerHTML = '';
            if(!items.length){
                list.innerHTML = '<div class="px-3 py-2 text-sm text-gray-500">Không có kết quả</div>';
            } else {
                items.forEach(b=>{
                    const btn = document.createElement('button');
                    btn.type='button';
                    btn.className='w-full text-left px-3 py-2 hover:bg-gray-100 text-sm flex items-center gap-2';
                    btn.innerHTML = (b.logoUrl? `<img src="${b.logoUrl}" class="w-5 h-5 rounded-sm" alt="">`:'')
                        + `<span class="font-medium">${b.code || b.shortName || ''}</span>`
                        + `<span class="text-gray-500"> - ${b.name}</span>`;
                    btn.addEventListener('click', ()=>{
                        input.value = format(b);
                        list.classList.add('hidden');
                        if (window.toastr?.success) toastr.success(`Đã chọn ngân hàng: ${b.name}`);
                    });
                    list.appendChild(btn);
                });
            }
            list.classList.remove('hidden');
        };

        const fetchBanks = async (qRaw) => {
            const q = qRaw.trim();                    // gửi q dạng thô; BE đã norm
            // gọi theo q trước
            let res = await fetch(`/api/banks?q=${encodeURIComponent(q)}&limit=100`);
            let data = res.ok ? await res.json() : [];

            // Fallback: nếu rỗng và có q -> lấy full list rồi lọc client theo norm
            if (!data.length && q) {
                const resAll = await fetch(`/api/banks?q=&limit=200`);
                const all = resAll.ok ? await resAll.json() : [];
                const k = norm(q);
                data = all.filter(b =>
                    norm(b.name).includes(k) ||
                    norm(b.shortName).includes(k) ||
                    norm(b.code).includes(k) ||
                    norm(b.bin).includes(k)
                );
            }
            return data;
        };


        const search = ()=>{
            const qRaw = input.value;
            // Nếu giống lần trước → chỉ mở lại dropdown
            if(qRaw === lastQ){ list.classList.remove('hidden'); return; }
            lastQ = qRaw;

            clearTimeout(timer);
            timer = setTimeout(async ()=>{
                const q = qRaw.trim();
                if(!q){ current = []; list.classList.add('hidden'); return; }

                const myReq = ++reqId;
                try{
                    const data = await fetchBanks(q);
                    // Nếu trong lúc chờ user đã gõ tiếp → bỏ kết quả cũ
                    if (myReq !== reqId) return;

                    current = data;
                    render(current);
                }catch(e){
                    list.classList.add('hidden');
                }
            }, DEBOUNCE);
        };

        input.addEventListener('input', search);
        input.addEventListener('focus', ()=>{ if(current.length) list.classList.remove('hidden'); });
        document.addEventListener('click', (e)=>{ if(!list.contains(e.target) && e.target!==input) list.classList.add('hidden'); });

        // Enter: chọn dòng đầu
        input.addEventListener('keydown', (e)=>{
            if(e.key==='Enter' && current.length){
                e.preventDefault();
                input.value = format(current[0]);
                list.classList.add('hidden');
            }
        });
    }

    // Khởi tạo cho Add & Edit
    initAutoSuggest('bankNameInput', 'bankSuggest');
    initAutoSuggest('editBankNameInput', 'editBankSuggest');
})();
