package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.VenueEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {

    // Busqueda por ID excluyendo registros con borrado logico.
    @Query("SELECT v FROM VenueEntity v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<VenueEntity> findActiveById(@Param("id") UUID id);

    // Listado paginado de recintos activos por ciudad.
    Page<VenueEntity> findByCityIgnoreCaseAndDeletedAtIsNull(String city, Pageable pageable);

    // Listado de todos los recintos activos paginados.
    Page<VenueEntity> findByDeletedAtIsNull(Pageable pageable);
}
