package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialtyCatalogServiceTest {

    @Mock
    private SpecialtyCatalogRepository repository;

    @InjectMocks
    private SpecialtyCatalogService service;

    private SpecialtyCatalogEntry entryNoi;
    private SpecialtyCatalogEntry entryTim;

    @BeforeEach
    void setUp() {
        entryNoi = SpecialtyCatalogEntry.create("NOI", "Khám Tổng Quát", "Khám sức khỏe tổng quát");
        entryTim = SpecialtyCatalogEntry.create("TIM", "Khám Tim Mạch", "Chẩn đoán bệnh tim");
    }

    @Test
    void listReturnsAllActiveSpecialtiesFromDatabase() {
        when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entryNoi, entryTim));

        List<SpecialtyResponse> result = service.list(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("TIM");
        assertThat(result.get(0).name()).isEqualTo("Khám Tim Mạch");
        assertThat(result.get(1).code()).isEqualTo("NOI");
        assertThat(result.get(1).name()).isEqualTo("Khám Tổng Quát");
        verify(repository).findByActiveTrueOrderByNameAsc();
    }

    @Test
    void listFiltersByQuery() {
        when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entryNoi, entryTim));

        List<SpecialtyResponse> result = service.list("tim");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("TIM");
    }

    @Test
    void requireReturnsSpecialtyWhenFound() {
        when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entryNoi, entryTim));

        SpecialtyResponse response = service.require("Khám Tổng Quát");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("NOI");
    }

    @Test
    void requireThrowsNotFoundWhenNotFound() {
        when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entryNoi, entryTim));

        assertThatThrownBy(() -> service.require("Khám Vũ Trụ"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createSavesNewSpecialtyEntry() {
        CreateSpecialtyRequest request = new CreateSpecialtyRequest("RHM", "Khám Răng Hàm Mặt", "Nha khoa tổng quát");
        when(repository.existsByCodeIgnoreCase("RHM")).thenReturn(false);
        when(repository.existsByNameIgnoreCase("Khám Răng Hàm Mặt")).thenReturn(false);
        when(repository.save(any(SpecialtyCatalogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpecialtyResponse created = service.create(request);

        assertThat(created.code()).isEqualTo("RHM");
        assertThat(created.name()).isEqualTo("Khám Răng Hàm Mặt");
        verify(repository).save(any(SpecialtyCatalogEntry.class));
    }

    @Test
    void updateModifiesExistingEntry() {
        when(repository.findByCodeIgnoreCase("NOI")).thenReturn(Optional.of(entryNoi));
        when(repository.save(any(SpecialtyCatalogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSpecialtyRequest request = new CreateSpecialtyRequest("NOI", "Khám Nội Khoa Cập Nhật", "Mô tả mới");
        SpecialtyResponse updated = service.update("NOI", request);

        assertThat(updated.name()).isEqualTo("Khám Nội Khoa Cập Nhật");
        assertThat(updated.description()).isEqualTo("Mô tả mới");
        verify(repository).save(entryNoi);
    }

    @Test
    void deactivateMarksSpecialtyInactive() {
        when(repository.findByCodeIgnoreCase("NOI")).thenReturn(Optional.of(entryNoi));

        service.deactivate("NOI");

        assertThat(entryNoi.isActive()).isFalse();
        verify(repository).save(entryNoi);
    }
}
