    package com.eewms.services;

    import com.eewms.dto.GoodIssueNoteDTO;
    import com.eewms.entities.SaleOrder;
    import com.eewms.entities.GoodIssueNote;

    import java.time.LocalDate;
    import java.util.List;

    public interface IGoodIssueService {
        GoodIssueNote createFromOrder(SaleOrder order, String username);
        GoodIssueNoteDTO getById(Long id);
        List<GoodIssueNoteDTO> getAllNotes();
        List<GoodIssueNoteDTO> filterReport(LocalDate fromDate, LocalDate toDate, Long customerId, Long userId);
    }