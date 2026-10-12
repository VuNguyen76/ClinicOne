package com.clinicone.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.clinicone.appointment.Appointment;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.auth.PatientAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

class InvoiceTest {
    @Test
    void recordsPaymentOnlyForExactOutstandingAmount() {
        Invoice invoice = Invoice.create(session(), "INV-TEST", 250_000L, "receptionist");

        assertThatIllegalStateException()
                .isThrownBy(() -> invoice.markPaid(249_999L, PaymentMethod.CASH, "receptionist"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(invoice.getPaidAmount()).isZero();

        invoice.markPaid(250_000L, PaymentMethod.CASH, "receptionist");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAmount()).isEqualTo(250_000L);
        assertThat(invoice.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void doesNotAllowPaymentTwice() {
        Invoice invoice = Invoice.create(session(), "INV-TEST", 250_000L, "receptionist");
        invoice.markPaid(250_000L, PaymentMethod.BANK_TRANSFER, "receptionist");

        assertThatIllegalStateException()
                .isThrownBy(() -> invoice.markPaid(250_000L, PaymentMethod.BANK_TRANSFER, "receptionist"));
    }

    private ExaminationSession session() {
        PatientAccount patient = Mockito.mock(PatientAccount.class);
        when(patient.getFullName()).thenReturn("Nguyen Van A");
        when(patient.getPhone()).thenReturn("0912345678");
        Appointment appointment = Mockito.mock(Appointment.class);
        when(appointment.getPatient()).thenReturn(patient);
        when(appointment.getAppointmentCode()).thenReturn("APT-1");
        when(appointment.getDoctorName()).thenReturn("Doctor A");
        when(appointment.getSpecialty()).thenReturn("General");
        return ExaminationSession.create(appointment);
    }
}
