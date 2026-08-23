package com.clinicone.schedule;

import lombok.Getter;

import com.clinicone.doctor.DoctorProfile;
import com.clinicone.queue.ClinicRoom;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Entity
@Table(name = "work_schedule_templates")
public class WorkScheduleTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_service_id", nullable = false)
    private ClinicService clinicService;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ClinicRoom room;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "visit_type", nullable = false, length = 60)
    private String visitType;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "day_start", nullable = false)
    private LocalTime dayStart;

    @Column(name = "day_end", nullable = false)
    private LocalTime dayEnd;

    @ElementCollection
    @CollectionTable(name = "work_schedule_template_weekdays",
            joinColumns = @JoinColumn(name = "template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private Set<DayOfWeek> weekdays = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "work_schedule_template_breaks",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "break_order")
    private List<ScheduleBreak> breaks = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "work_schedule_template_exceptions",
            joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "exception_date", nullable = false)
    private Set<LocalDate> exceptionDates = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkScheduleTemplate() {
    }

    private WorkScheduleTemplate(ClinicService clinicService, DoctorProfile doctorProfile, ClinicRoom room,
                                 LocalDate startDate, LocalDate endDate, LocalTime dayStart, LocalTime dayEnd,
                                 int durationMinutes, Collection<DayOfWeek> weekdays,
                                 Collection<ScheduleBreak> breaks, Collection<LocalDate> exceptionDates) {
        this.clinicService = clinicService;
        this.doctorProfile = doctorProfile;
        this.room = room;
        this.specialty = clinicService.getSpecialty();
        this.visitType = clinicService.getVisitType();
        this.durationMinutes = durationMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.weekdays.addAll(weekdays);
        this.breaks.addAll(breaks);
        this.exceptionDates.addAll(exceptionDates);
        this.active = true;
    }

    public static WorkScheduleTemplate create(ClinicService clinicService, DoctorProfile doctorProfile,
                                               ClinicRoom room, LocalDate startDate, LocalDate endDate,
                                               LocalTime dayStart, LocalTime dayEnd, int durationMinutes,
                                               Collection<DayOfWeek> weekdays, Collection<ScheduleBreak> breaks,
                                               Collection<LocalDate> exceptionDates) {
        return new WorkScheduleTemplate(clinicService, doctorProfile, room, startDate, endDate, dayStart, dayEnd,
                durationMinutes, weekdays, breaks, exceptionDates);
    }

    public void setActive(boolean active) { this.active = active; }

    public void setWeekdays(Collection<DayOfWeek> weekdays) {
        this.weekdays.clear();
        if (weekdays != null) {
            this.weekdays.addAll(weekdays);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

}
