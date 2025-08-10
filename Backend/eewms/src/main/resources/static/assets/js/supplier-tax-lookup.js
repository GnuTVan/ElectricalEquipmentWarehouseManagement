(function () {
    const $ = (id) => document.getElementById(id);
    const setVis = (el, show) => { if (el) el.classList.toggle('hidden', !show); };

    async function fetchAndFill(mst, mode) {
        const onlyDigits = (mst || '').replace(/[^0-9]/g,'');
        if (!onlyDigits || onlyDigits.length < 8) return;

        const isEdit = mode === 'edit';
        const spin = isEdit ? $('editTaxLoading') : $('taxLoading');
        setVis(spin, true);
        try {
            const res = await fetch(`/api/tax-lookup/${encodeURIComponent(onlyDigits)}`, {
                headers: { 'Accept': 'application/json' }
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
                window.toastr && toastr.success(data.message || 'Đã tự điền từ MST');
            } else {
                window.toastr && toastr.warning(data?.message || 'Không tìm thấy theo MST');
            }
        } catch (e) {
            window.toastr && toastr.error('Không truy cập được dịch vụ tra cứu');
        } finally {
            setVis(spin, false);
        }
    }

    // --- Add modal ---
    const taxAdd = $('taxCode');
    if (taxAdd) {
        let t1=null;
        taxAdd.addEventListener('input', () => { clearTimeout(t1); t1=setTimeout(()=>fetchAndFill(taxAdd.value,'add'), 500); });
        taxAdd.addEventListener('blur', () => fetchAndFill(taxAdd.value,'add'));
    }
    $('btnFetchTax')?.addEventListener('click', () => fetchAndFill($('taxCode')?.value,'add'));

    // --- Edit modal (optional) ---
    const taxEdit = $('editTaxCode');
    if (taxEdit) {
        let t2=null;
        taxEdit.addEventListener('input', () => { clearTimeout(t2); t2=setTimeout(()=>fetchAndFill(taxEdit.value,'edit'), 500); });
        taxEdit.addEventListener('blur', () => fetchAndFill(taxEdit.value,'edit'));
    }
    $('btnFetchTaxEdit')?.addEventListener('click', () => fetchAndFill($('editTaxCode')?.value,'edit'));
})();
