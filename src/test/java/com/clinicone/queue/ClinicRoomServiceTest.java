package com.clinicone.queue;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClinicRoomServiceTest {
    private ClinicRoomRepository repository;
    private ClinicRoomService service;
    private ClinicRoom room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        repository = mock(ClinicRoomRepository.class);
        service = new ClinicRoomService(repository);
        room = ClinicRoom.create("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát");
        roomId = UUID.randomUUID();
        setId(room, roomId);
        when(repository.save(any(ClinicRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void adminCreatesActiveRoom() {
        when(repository.existsByCodeIgnoreCase("NOI-01")).thenReturn(false);

        ClinicRoomResponse response = service.create(new CreateClinicRoomRequest("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát"));

        assertEquals("NOI-01", response.code());
        assertEquals("Nội tổng quát", response.specialty());
        assertEquals(true, response.active());
        verify(repository).save(any(ClinicRoom.class));
    }

    @Test
    void rejectsDuplicateRoomCodeIgnoringCase() {
        when(repository.existsByCodeIgnoreCase("NOI-01")).thenReturn(true);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.create(new CreateClinicRoomRequest("noi-01", "Phòng khác", "Nội tổng quát")));

        assertEquals(409, exception.getStatus().value());
        assertEquals("ROOM_CODE_EXISTS", exception.getCode());
        verify(repository, never()).save(any(ClinicRoom.class));
    }

    @Test
    void updatesRoomDetailsAndKeepsItsCodeUnique() {
        when(repository.findById(roomId)).thenReturn(Optional.of(room));
        when(repository.existsByCodeIgnoreCaseAndIdNot("NOI-02", roomId)).thenReturn(false);

        ClinicRoomResponse response = service.update(roomId, new UpdateClinicRoomRequest("NOI-02", "Phòng Nội 02", "Nội tổng quát"));

        assertEquals("NOI-02", response.code());
        assertEquals("Phòng Nội 02", response.name());
        verify(repository).save(room);
    }

    @Test
    void canDeactivateRoomWithoutDeletingItsHistory() {
        when(repository.findById(roomId)).thenReturn(Optional.of(room));

        ClinicRoomResponse response = service.setActive(roomId, false);

        assertEquals(false, response.active());
        verify(repository).save(room);
    }

    @Test
    void listsRoomsForCoordinator() {
        when(repository.findAllByOrderByCodeAsc()).thenReturn(List.of(room));

        assertEquals(1, service.list().size());
        assertEquals("NOI-01", service.list().get(0).code());
    }

    private static void setId(ClinicRoom target, UUID id) {
        try {
            var field = ClinicRoom.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
