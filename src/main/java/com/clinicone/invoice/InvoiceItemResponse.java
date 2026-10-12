package com.clinicone.invoice;

import java.util.UUID;

public record InvoiceItemResponse(UUID id, String itemType, String itemName, String unit,
                                  int quantity, long unitPrice, long totalPrice, int lineNumber) {
    public static InvoiceItemResponse from(InvoiceItem item) {
        return new InvoiceItemResponse(item.getId(), item.getItemType(), item.getItemName(), item.getUnit(),
                item.getQuantity(), item.getUnitPrice(), item.getTotalPrice(), item.getLineNumber());
    }
}
