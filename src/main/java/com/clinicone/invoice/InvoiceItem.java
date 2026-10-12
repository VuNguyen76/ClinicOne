package com.clinicone.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(length = 30)
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    protected InvoiceItem() {
    }

    private InvoiceItem(String itemType, String itemName, String unit, int quantity, long unitPrice, int lineNumber) {
        this.itemType = itemType;
        this.itemName = itemName;
        this.unit = unit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = Math.multiplyExact(unitPrice, quantity);
        this.lineNumber = lineNumber;
    }

    public static InvoiceItem consultation(String itemName, long fee) {
        if (itemName == null || itemName.isBlank() || fee <= 0) {
            throw new IllegalArgumentException("Chi tiết phí khám không hợp lệ.");
        }
        return new InvoiceItem("CONSULTATION", itemName.trim(), "Lượt", 1, fee, 1);
    }

    void attachTo(Invoice invoice) {
        this.invoice = invoice;
    }
}
