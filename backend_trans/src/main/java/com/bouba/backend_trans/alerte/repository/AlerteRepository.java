package com.bouba.backend_trans.alerte.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;

public interface AlerteRepository extends JpaRepository<Alerte, UUID> {

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findAll();

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findByStatutOrderByDateDeclenchementDesc(StatutAlerte statut);

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	Optional<Alerte> findById(UUID id);

	Optional<Alerte> findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
			UUID equipementId, TypeAnomalie typeAnomalie, StatutAlerte statut);
}
