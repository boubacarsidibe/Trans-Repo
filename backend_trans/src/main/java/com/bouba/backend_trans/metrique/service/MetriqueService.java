package com.bouba.backend_trans.metrique.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.metrique.dto.NetworkMetricsRequest;
import com.bouba.backend_trans.metrique.dto.SystemMetricsRequest;
import com.bouba.backend_trans.metrique.entity.Metrique;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;

@Service
public class MetriqueService {

	private final MetriqueRepository metriqueRepository;
	private final EquipementRepository equipementRepository;
	private final MetriqueSeuilEvaluator seuilEvaluator;

	public MetriqueService(
			MetriqueRepository metriqueRepository,
			EquipementRepository equipementRepository,
			MetriqueSeuilEvaluator seuilEvaluator
	) {
		this.metriqueRepository = metriqueRepository;
		this.equipementRepository = equipementRepository;
		this.seuilEvaluator = seuilEvaluator;
	}

	@Transactional
	public void ingestSystemMetrics(SystemMetricsRequest request) {
		Equipement equipement = getEquipement(request.getEquipmentId());

		enregistrer(equipement, TypeMetrique.CPU, request.getCpuPercent(), "%");
		enregistrer(equipement, TypeMetrique.RAM, request.getMemoryPercent(), "%");
		enregistrer(equipement, TypeMetrique.DISQUE, request.getDiskPercent(), "%");
	}

	@Transactional
	public void ingestNetworkMetrics(NetworkMetricsRequest request) {
		Equipement equipement = getEquipement(request.getEquipmentId());

		enregistrer(equipement, TypeMetrique.BANDE_PASSANTE, request.getBandwidthMbps(), "Mb/s");
		enregistrer(equipement, TypeMetrique.LATENCE, request.getLatencyMs(), "ms");
		enregistrer(equipement, TypeMetrique.TAUX_ERREUR, request.getErrorRatePercent(), "%");
	}

	@Transactional(readOnly = true)
	public List<Metrique> historiqueParEquipement(UUID equipementId) {
		return metriqueRepository.findByEquipementIdOrderByHorodatageDesc(equipementId);
	}

	private void enregistrer(Equipement equipement, TypeMetrique type, BigDecimal valeur, String unite) {
		if (valeur == null) {
			return;
		}

		Metrique metrique = new Metrique();
		metrique.setEquipement(equipement);
		metrique.setTypeMetrique(type);
		metrique.setValeur(valeur);
		metrique.setUnite(unite);
		metriqueRepository.save(metrique);

		seuilEvaluator.evaluer(equipement, type, valeur);
	}

	private Equipement getEquipement(UUID id) {
		return equipementRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Équipement introuvable."));
	}
}
