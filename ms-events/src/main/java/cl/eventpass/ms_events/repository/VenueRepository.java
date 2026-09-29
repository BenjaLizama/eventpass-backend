package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.VenueEntity;
import org.hibernate.query.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {

    // Busqueda por ID excluyendo registros con borrado logico.
    @Query("SELECT v FROM VenueEntity v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<VenueEntity> findActiveById(@Param("id") UUID id);

    // Listado paginado de recintos activos por ciudad.
    Page findByCityIgnoreCaseAndDeletedAtIsNull(String city, Pageable pageable);

    // Listado de todos los recintos activos paginados.
    Page findByDeletedAtIsNull(Pageable pageable);
}
