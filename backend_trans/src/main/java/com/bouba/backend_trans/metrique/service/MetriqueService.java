package com.bouba.backend_trans.metrique.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.metrique.dto.MetriqueResponse;
import com.bouba.backend_trans.metrique.dto.MetriquesEquipement;
import com.bouba.backend_trans.metrique.dto.NetworkMetricsRequest;
import com.bouba.backend_trans.metrique.dto.SystemMetricsRequest;
import com.bouba.backend_trans.metrique.entity.Metrique;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

@Service
public class MetriqueService {

	private final MetriqueRepository metriqueRepository;
	private final EquipementRepository equipementRepository;
	private final MetriqueSeuilEvaluator seuilEvaluator;
	private final DiffusionSupervision diffusionSupervision;

	public MetriqueService(
			MetriqueRepository metriqueRepository,
			EquipementRepository equipementRepository,
			MetriqueSeuilEvaluator seuilEvaluator,
			DiffusionSupervision diffusionSupervision
	) {
		this.metriqueRepository = metriqueRepository;
		this.equipementRepository = equipementRepository;
		this.seuilEvaluator = seuilEvaluator;
		this.diffusionSupervision = diffusionSupervision;
	}

	@Transactional
	public void ingestSystemMetrics(SystemMetricsRequest request) {
		LotMetriques lot = new LotMetriques(getEquipement(request.getEquipmentId()));

		lot.ajouter(TypeMetrique.CPU, request.getCpuPercent(), "%");
		lot.ajouter(TypeMetrique.RAM, request.getMemoryPercent(), "%");
		lot.ajouter(TypeMetrique.DISQUE, request.getDiskPercent(), "%");
		lot.ajouter(TypeMetrique.SWAP, request.getSwapPercent(), "%");
		lot.ajouter(TypeMetrique.NOMBRE_PROCESSUS, request.getProcessCount(), "processus");
		lot.ajouter(TypeMetrique.PORTS_ECOUTE, request.getListeningPortsCount(), "ports");
		lot.ajouter(TypeMetrique.DISQUE_IO_LECTURE, request.getDiskReadKbps(), "Ko/s");
		lot.ajouter(TypeMetrique.DISQUE_IO_ECRITURE, request.getDiskWriteKbps(), "Ko/s");
		lot.ajouter(TypeMetrique.RESEAU_IO_ENTRANT, request.getNetworkInKbps(), "Ko/s");
		lot.ajouter(TypeMetrique.RESEAU_IO_SORTANT, request.getNetworkOutKbps(), "Ko/s");
		lot.ajouter(TypeMetrique.UPTIME, request.getUptimeSeconds(), "s");
		lot.ajouter(TypeMetrique.CHARGE_1MIN, request.getLoad1min(), "ratio");
		lot.ajouter(TypeMetrique.RAM_TOTALE_MO, request.getMemoryTotalMb(), "Mo");
		lot.ajouter(TypeMetrique.RAM_UTILISEE_MO, request.getMemoryUsedMb(), "Mo");
		lot.ajouter(TypeMetrique.DISQUE_TOTAL_GO, request.getDiskTotalGb(), "Go");
		lot.ajouter(TypeMetrique.DISQUE_UTILISE_GO, request.getDiskUsedGb(), "Go");
		lot.ajouter(TypeMetrique.LIMITE_FICHIERS_OUVERTS, request.getOpenFilesLimit(), "fichiers");
		lot.ajouter(TypeMetrique.LIMITE_PROCESSUS, request.getProcessLimit(), "processus");
		lot.ajouter(TypeMetrique.SERVICES_TCP_INDISPONIBLES, request.getTcpServicesDown(), "services");
		lot.ajouter(TypeMetrique.DNS_LATENCE, request.getDnsLatencyMs(), "ms");
		lot.ajouter(TypeMetrique.LOG_LIGNES, request.getLogLinesCount(), "lignes");
		lot.ajouter(TypeMetrique.LOG_LIGNES_MATCH, request.getLogLinesMatchCount(), "lignes");
		lot.ajouter(TypeMetrique.FICHIER_EXISTE, request.getWatchedFileExists(), "bool");
		lot.ajouter(TypeMetrique.FICHIER_TAILLE, request.getWatchedFileSizeBytes(), "octets");
		lot.ajouter(TypeMetrique.TEMPERATURE_MAX, request.getTemperatureMaxCelsius(), "°C");
		lot.ajouter(TypeMetrique.VENTILATEUR_RPM, request.getFanSpeedRpm(), "rpm");
		lot.ajouter(TypeMetrique.MODBUS_VALEUR, request.getModbusValue(), "brut");

		lot.diffuser();
	}

	@Transactional
	public void ingestNetworkMetrics(NetworkMetricsRequest request) {
		LotMetriques lot = new LotMetriques(getEquipement(request.getEquipmentId()));

		lot.ajouter(TypeMetrique.BANDE_PASSANTE, request.getBandwidthMbps(), "Mb/s");
		lot.ajouter(TypeMetrique.LATENCE, request.getLatencyMs(), "ms");
		lot.ajouter(TypeMetrique.TAUX_ERREUR, request.getErrorRatePercent(), "%");

		lot.diffuser();
	}

	/**
	 * Historique d'un équipement, borné dans le temps et paginé (§7.9). Le type
	 * de métrique est facultatif : sans lui, toutes les mesures de la fenêtre
	 * sont rendues.
	 */
	@Transactional(readOnly = true)
	public List<Metrique> historiqueParEquipement(
			UUID equipementId,
			TypeMetrique typeMetrique,
			LocalDateTime depuis,
			Pageable pageable
	) {
		return typeMetrique == null
				? metriqueRepository.findByEquipementIdAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
						equipementId, depuis, pageable)
				: metriqueRepository.findByEquipementIdAndTypeMetriqueAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
						equipementId, typeMetrique, depuis, pageable);
	}

	private Equipement getEquipement(UUID id) {
		return equipementRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Équipement introuvable."));
	}

	/**
	 * Accumule les métriques d'un même cycle de collecte pour n'émettre qu'un
	 * événement temps réel à la fin, une fois la transaction validée.
	 */
	private final class LotMetriques {

		private final Equipement equipement;
		private final List<MetriqueResponse> enregistrees = new ArrayList<>();

		private LotMetriques(Equipement equipement) {
			this.equipement = equipement;
		}

		private void ajouter(TypeMetrique type, BigDecimal valeur, String unite) {
			if (valeur == null) {
				return;
			}

			Metrique metrique = new Metrique();
			metrique.setEquipement(equipement);
			metrique.setTypeMetrique(type);
			metrique.setValeur(valeur);
			metrique.setUnite(unite);
			metriqueRepository.save(metrique);

			enregistrees.add(MetriqueResponse.fromEntity(metrique));
			seuilEvaluator.evaluer(equipement, type, valeur);
		}

		private void diffuser() {
			if (enregistrees.isEmpty()) {
				return;
			}

			// Marque la remontée : c'est ce que lit le watchdog de disponibilité.
			equipement.setDerniereMesure(LocalDateTime.now());
			equipementRepository.save(equipement);

			diffusionSupervision.publier(
					TypeEvenement.METRIC_UPDATE,
					new MetriquesEquipement(equipement.getId(), equipement.getNom(), enregistrees));
		}
	}
}
