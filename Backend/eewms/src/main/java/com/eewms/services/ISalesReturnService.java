package com.eewms.services;
import com.eewms.constant.ReturnSettlementOption;
import com.eewms.dto.returning.SalesReturnDTO;
import org.springframework.transaction.annotation.Transactional;

public interface ISalesReturnService {
    SalesReturnDTO createDraft(SalesReturnDTO dto, String username);
    void submit(Long id);
    void approve(Long id, String managerNote);
    void reject(Long id, String reason);
    // Phase 4 sẽ nhập kho
    void complete(Long id);

    @Transactional
    void receive(Long id,
                 String username,
                 ReturnSettlementOption opt,
                 Long warehouseId);

    SalesReturnDTO getById(Long id);
    void createReplacementRequest(Long id, String username);

}