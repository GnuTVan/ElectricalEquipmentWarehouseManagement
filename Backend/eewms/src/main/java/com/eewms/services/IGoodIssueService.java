package com.eewms.services;

import com.eewms.entities.Order;
import com.eewms.entities.GoodIssueNote;

public interface IGoodIssueService {
    GoodIssueNote createFromOrder(Order order);
}
