(function () {
    function $(id){ return document.getElementById(id); }
    function setVis(el, show){ if(el) el.classList.toggle('hidden', !show); }
    function notifyWarn(msg){ if (window.toastr) toastr.warning(msg); else alert(msg); }
    function notifySuccess(msg){ if (window.toastr) toastr.success(msg); else alert(msg); }
    function notifyError(msg){ if (window.toastr) toastr.error(msg); else alert(msg); }

    // GỌI API
    let busy = false;
    async function fetchAndFill(mst, mode) {
        if (busy) return;

        const raw = (mst || '').trim();
        const only = raw.replace(/[^0-9]/g, '');

        // ⚠️ Hiển thị toastr ngay khi không hợp lệ
        if (!(only.length === 10 || only.length === 13)) {
            notifyWarn(`Mã số thuế "${raw}" không hợp lệ, vui lòng nhập mã số thuế 10 hoặc 13 chữ số.`);
            return;
        }

        const isEdit = mode === 'edit';
        const spin = isEdit ? $('editTaxLoading') : $('taxLoading');
        const btn  = isEdit ? $('btnFetchTaxEdit') : $('btnFetchTax');

        busy = true;
        setVis(spin, true);
        if (btn) btn.disabled = true;

        try {
            const res = await fetch(`/api/tax-lookup/${encodeURIComponent(only)}`, { headers: { 'Accept': 'application/json' }});
            const data = await res.json();

            if (data && data.found && data.data) {
                if (isEdit) {
                    $('editName')    && ($('editName').value    = data.data.name    || $('editName').value    || '');
                    $('editAddress') && ($('editAddress').value = data.data.address || $('editAddress').value || '');
                } else {
                    $('name')    && ($('name').value    = data.data.name    || $('name').value    || '');
                    $('address') && ($('address').value = data.data.address || $('address').value || '');
                }
                notifySuccess(`Tìm kiếm thông tin thành công NCC với mã số thuế ${only}`);
            } else {
                notifyWarn(`Không tìm thấy thông tin NCC với mã số thuế ${only}`);
            }
        } catch (e) {
            notifyError('Không truy cập được dịch vụ tra cứu');
        } finally {
            setVis(spin, false);
            if (btn) btn.disabled = false;
            busy = false;
        }
    }

    // CHỈ TRA CỨU KHI BẤM NÚT (có chặn submit)
    const btnAdd = $('btnFetchTax');
    if (btnAdd) {
        btnAdd.addEventListener('click', (e) => {
            e.preventDefault(); e.stopPropagation();
            fetchAndFill($('taxCode')?.value, 'add');
        });
    }

    const btnEdit = $('btnFetchTaxEdit');
    if (btnEdit) {
        btnEdit.addEventListener('click', (e) => {
            e.preventDefault(); e.stopPropagation();
            fetchAndFill($('editTaxCode')?.value, 'edit');
        });
    }
})();
