UPDATE public.generated_clinic_slots AS slot
SET status = 'CANCELLED'
FROM public.work_schedule_templates AS template
WHERE template.id = slot.template_id
  AND template.active = false
  AND slot.status = 'OPEN'
  AND NOT EXISTS (
      SELECT 1
      FROM public.appointments AS appointment
      WHERE appointment.clinic_service_id = slot.clinic_service_id
        AND appointment.doctor_staff_id = slot.doctor_staff_id
        AND appointment.appointment_date = slot.appointment_date
        AND appointment.start_time = slot.start_time
        AND appointment.status IN ('BOOKED', 'CHECKED_IN')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.appointment_holds AS hold
      WHERE hold.clinic_service_id = slot.clinic_service_id
        AND hold.doctor_staff_id = slot.doctor_staff_id
        AND hold.appointment_date = slot.appointment_date
        AND hold.start_time = slot.start_time
        AND hold.expires_at > CURRENT_TIMESTAMP
  );
