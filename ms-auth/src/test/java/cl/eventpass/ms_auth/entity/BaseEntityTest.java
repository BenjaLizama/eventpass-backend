package cl.eventpass.ms_auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    @Test
    void shouldReturnTrueWhenEntityIsDeleted() {
        TestEntity entity = new TestEntity();
        entity.setDeletedAt(Instant.now());

        assertTrue(entity.isDeleted());
    }

    @Test
    void shouldReturnFalseWhenEntityIsNotDeleted() {
        TestEntity entity = new TestEntity();
        entity.setDeletedAt(null);

        assertFalse(entity.isDeleted());
    }

    @Test
    void shouldSetAndGetEntityFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();
        Instant deletedAt = Instant.now();
        Long version = 1L;

        TestEntity entity = new TestEntity();

        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setDeletedAt(deletedAt);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("admin");
        entity.setVersion(version);

        assertEquals(id, entity.getId());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(updatedAt, entity.getUpdatedAt());
        assertEquals(deletedAt, entity.getDeletedAt());
        assertEquals("system", entity.getCreatedBy());
        assertEquals("admin", entity.getUpdatedBy());
        assertEquals(version, entity.getVersion());
    }

    private static class TestEntity extends BaseEntity {
    }
}