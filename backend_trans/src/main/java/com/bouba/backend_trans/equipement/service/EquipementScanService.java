package com.bouba.backend_trans.equipement.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bouba.backend_trans.equipement.dto.CandidatEquipement;
import com.bouba.backend_trans.equipement.dto.ScanRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.equipement.scan.HoteAccessible;
import com.bouba.backend_trans.equipement.scan.PlageIpUtils;
import com.bouba.backend_trans.equipement.scan.SnmpClient;
import com.bouba.backend_trans.equipement.scan.SnmpResultat;

/**
 * Scan de découverte d'une plage d'IP (issue #152) : test ICMP puis, si
 * l'hôte répond, GET SNMP sur {@code sysDescr}/{@code sysObjectID}.
 *
 * <p>Ne crée jamais d'{@link Equipement} : le scan ne fait que proposer des
 * candidats à la validation d'un administrateur, qui les déclare ensuite
 * manuellement via le CRUD existant ({@code POST /api/v1/equipments}) —
 * jamais d'ajout automatique silencieux, conformément à la demande de
 * l'issue.
 */
@Service
public class EquipementScanService {

	/** Nombre de threads maximum consacrés à un scan, quelle que soit la taille de la plage. */
	private static final int TAILLE_MAX_POOL = 32;

	/** Délai maximum accordé au test ICMP puis, séparément, à l'interrogation SNMP, par IP. */
	private static final int TIMEOUT_PAR_IP_MS = 300;

	/** Délai maximum accordé à l'analyse d'une IP avant de l'abandonner (marge sur {@link #TIMEOUT_PAR_IP_MS}). */
	private static final long TIMEOUT_TACHE_SECONDES = 5;

	private final EquipementRepository equipementRepository;
	private final HoteAccessible hoteAccessible;
	private final SnmpClient snmpClient;

	public EquipementScanService(EquipementRepository equipementRepository, HoteAccessible hoteAccessible,
			SnmpClient snmpClient) {
		this.equipementRepository = equipementRepository;
		this.hoteAccessible = hoteAccessible;
		this.snmpClient = snmpClient;
	}

	public List<CandidatEquipement> scanner(ScanRequest request) {
		List<String> adresses = PlageIpUtils.enumererPlage(request.getIpDebut(), request.getIpFin());

		// Une seule requête avant de paralléliser : les tâches réseau ci-dessous ne
		// doivent pas partager l'accès au repository entre threads concurrents.
		Set<String> dejaDeclarees = equipementRepository.findByAdresseIpIn(adresses).stream()
				.map(Equipement::getAdresseIp)
				.collect(Collectors.toSet());

		ExecutorService executeur = Executors.newFixedThreadPool(Math.min(TAILLE_MAX_POOL, adresses.size()));
		try {
			List<Future<CandidatEquipement>> taches = adresses.stream()
					.map(ip -> executeur.submit(() -> analyser(ip, request, dejaDeclarees.contains(ip))))
					.collect(Collectors.toList());

			return taches.stream().map(this::resultat).collect(Collectors.toList());
		} finally {
			executeur.shutdown();
			attendreArret(executeur);
		}
	}

	private CandidatEquipement analyser(String ip, ScanRequest request, boolean dejaDeclare) {
		boolean accessible = hoteAccessible.estAccessible(ip, TIMEOUT_PAR_IP_MS);
		SnmpResultat snmp = accessible
				? snmpClient.interroger(ip, request.getPort(), request.getCommunaute(), TIMEOUT_PAR_IP_MS)
				: SnmpResultat.AUCUNE_REPONSE;

		return new CandidatEquipement(ip, accessible, snmp.responsive(), snmp.sysDescr(), snmp.sysObjectID(),
				dejaDeclare);
	}

	private CandidatEquipement resultat(Future<CandidatEquipement> tache) {
		try {
			return tache.get(TIMEOUT_TACHE_SECONDES, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Scan interrompu.", e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("Échec de l'analyse d'une adresse durant le scan.", e.getCause());
		} catch (TimeoutException e) {
			tache.cancel(true);
			throw new IllegalStateException("Le scan a dépassé le délai autorisé pour une adresse.", e);
		}
	}

	private void attendreArret(ExecutorService executeur) {
		try {
			if (!executeur.awaitTermination(TIMEOUT_TACHE_SECONDES, TimeUnit.SECONDS)) {
				executeur.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			executeur.shutdownNow();
		}
	}
}
