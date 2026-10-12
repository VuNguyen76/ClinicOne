package com.clinicone.invoice;

public record PublicInvoiceItemResponse(String itemName, String unit, int quantity,
                                        long unitPrice, long totalPrice) {
    public static PublicInvoiceItemResponse from(InvoiceItem item) {
        return new PublicInvoiceItemResponse(item.getItemName(), item.getUnit(), item.getQuantity(),
                item.getUnitPrice(), item.getTotalPrice());
    }
}
