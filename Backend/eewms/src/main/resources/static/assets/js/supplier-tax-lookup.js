(function () {
    const $ = (id) => document.getElementById(id);
    const setVis = (el, show) => {
        if (el) el.classList.toggle('hidden', !show);
    }

    let busy = false;

    async function fetchAndFill(mst, mode) {
        if (busy) return;
        const onlyDigits = (mst || '').replace(/[^0-9]/g, '');
        if (!onlyDigits || onlyDigits.length < 8) {
            window.toastr && toastr.warning('Mã số thuế không hợp lệ, vui lòng nhập lại.');
            return;
        }

        const isEdit = mode === 'edit';
        const spin = isEdit ? $('editTaxLoading') : $('taxLoading');
        const btn = isEdit ? $('btnFetchTaxEdit') : $('btnFetchTax');

        busy = true;
        setVis(spin, true);
        btn && (btn.disabled = true);

        try {
            const res = await fetch(`/api/tax-lookup/${encodeURIComponent(onlyDigits)}`, {
                headers: {'Accept': 'application/json'}
            });
            const data = await res.json();

            if (data && data.found && data.data) {
                if (isEdit) {
                    $('editName') && ($('editName').value = data.data.name || $('editName').value || '');
                    $('editAddress') && ($('editAddress').value = data.data.address || $('editAddress').value || '');
                } else {
                    $('name') && ($('name').value = data.data.name || $('name').value || '');
                    $('address') && ($('address').value = data.data.address || $('address').value || '');
                }
                window.toastr && toastr.success(`Tìm kiếm thông tin thành công NCC với mã số thuế ${onlyDigits}`);
            } else {
                window.toastr && toastr.warning(`Không tìm thấy thông tin NCC với mã số thuế ${onlyDigits}`);
            }
        } catch (e) {
            window.toastr && toastr.error('Không truy cập được dịch vụ tra cứu');
        } finally {
            setVis(spin, false);
            btn && (btn.disabled = false);
            busy = false;
        }
    }

    // Chỉ tra cứu khi bấm nút
    $('btnFetchTax')?.addEventListener('click', () => fetchAndFill($('taxCode')?.value, 'add'));
    $('btnFetchTaxEdit')?.addEventListener('click', () => fetchAndFill($('editTaxCode')?.value, 'edit'));
})();
