package com.bouba.backend_trans.seuil.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.seuil.entity.SeuilAlerte;

public interface SeuilAlerteRepository extends JpaRepository<SeuilAlerte, UUID> {

	@EntityGraph(attributePaths = "equipement")
	List<SeuilAlerte> findAllByOrderByTypeMetriqueAsc();

	Optional<SeuilAlerte> findByTypeMetriqueAndEquipementId(TypeMetrique typeMetrique, UUID equipementId);

	Optional<SeuilAlerte> findByTypeMetriqueAndEquipementIsNull(TypeMetrique typeMetrique);

	boolean existsByTypeMetriqueAndEquipementIsNull(TypeMetrique typeMetrique);
}
