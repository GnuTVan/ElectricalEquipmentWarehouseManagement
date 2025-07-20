package com.eewms.services;

import com.eewms.entities.SaleOrder;
import com.eewms.entities.GoodIssueNote;

public interface IGoodIssueService {
    GoodIssueNote createFromOrder(SaleOrder order);
}
