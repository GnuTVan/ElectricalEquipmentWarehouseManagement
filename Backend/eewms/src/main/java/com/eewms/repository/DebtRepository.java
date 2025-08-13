package com.eewms.repository;

import com.eewms.entities.Debt;
import com.eewms.entities.Debt.DocumentType;
import com.eewms.entities.Debt.PartyType;
import com.eewms.entities.Debt.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    Optional<Debt> findByDocumentTypeAndDocumentId(DocumentType documentType, Long documentId);
    boolean existsByDocumentTypeAndDocumentId(DocumentType documentType, Long documentId);

    // Back-compat: code cũ gọi theo receiptId vẫn dùng được
    default Optional<Debt> findByWarehouseReceiptId(Long wrId) {
        return findByDocumentTypeAndDocumentId(DocumentType.WAREHOUSE_RECEIPT, wrId);
    }
    default boolean existsByWarehouseReceiptId(Long wrId) {
        return existsByDocumentTypeAndDocumentId(DocumentType.WAREHOUSE_RECEIPT, wrId);
    }

    // Cho trang danh sách công nợ (filter linh hoạt)
    Page<Debt> findAll(Specification<Debt> spec, Pageable pageable);
}
