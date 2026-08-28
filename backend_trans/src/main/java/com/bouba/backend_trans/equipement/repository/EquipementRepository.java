package com.bouba.backend_trans.equipement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;

public interface EquipementRepository extends JpaRepository<Equipement, UUID> {

	boolean existsByAdresseIp(String adresseIp);

	Optional<Equipement> findByCleApi(String cleApi);

	/** Parc réellement supervisé : tout sauf les équipements archivés. */
	List<Equipement> findByEtatNot(EtatEquipement etat);

	long countByEtatNot(EtatEquipement etat);
}
