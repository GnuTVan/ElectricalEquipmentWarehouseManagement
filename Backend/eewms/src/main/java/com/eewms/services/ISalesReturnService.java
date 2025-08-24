package com.eewms.services;
import com.eewms.dto.returning.SalesReturnDTO;

public interface ISalesReturnService {
    SalesReturnDTO createDraft(SalesReturnDTO dto, String username);
    void submit(Long id);
    void approve(Long id, String managerNote);
    void reject(Long id, String reason);
    // Phase 4 sẽ nhập kho
    void complete(Long id);
    SalesReturnDTO getById(Long id);
    void createReplacementRequest(Long id, String username);
    default void receive(Long id, String username) {
        receive(id, username, null);
    }
    void receive(Long id, String username, com.eewms.constant.ReturnSettlementOption opt);
}