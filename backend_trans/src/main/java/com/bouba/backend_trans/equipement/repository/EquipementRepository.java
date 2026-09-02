package com.bouba.backend_trans.equipement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;

public interface EquipementRepository extends JpaRepository<Equipement, UUID> {

	boolean existsByAdresseIp(String adresseIp);

	/** Les équipements déjà déclarés parmi une liste d'IP — utilisé par le scan de découverte pour marquer les doublons. */
	List<Equipement> findByAdresseIpIn(List<String> adresseIps);

	Optional<Equipement> findByCleApi(String cleApi);

	/** Parc réellement supervisé : tout sauf les équipements archivés. */
	List<Equipement> findByEtatNot(EtatEquipement etat);

	long countByEtatNot(EtatEquipement etat);

	/** Vrai si un autre équipement dépend de celui-ci — bloque sa suppression définitive. */
	boolean existsByDependDeId(UUID dependDeId);
}
