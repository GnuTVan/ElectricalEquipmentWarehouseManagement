package com.eewms.services;

import com.eewms.entities.InventoryTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IInventoryTransferService {

    Page<InventoryTransfer> search(String keyword,
                                   InventoryTransfer.Status status,
                                   java.time.LocalDateTime fromDate,
                                   java.time.LocalDateTime toDate,
                                   Integer fromWarehouseId,
                                   Integer toWarehouseId,
                                   Pageable pageable);

    InventoryTransfer get(Long id);

    InventoryTransfer createDraft(InventoryTransfer draft); // nhận entity đã bind (items, from/to, note)

    InventoryTransfer approveFrom(Long id, Integer managerUserId);

    InventoryTransfer approveTo(Long id, Integer managerUserId);

    InventoryTransfer export(Long id, Integer staffUserId);

    InventoryTransfer importTo(Long id, Integer staffUserId);

    void cancel(Long id, Integer byUserId);
}
