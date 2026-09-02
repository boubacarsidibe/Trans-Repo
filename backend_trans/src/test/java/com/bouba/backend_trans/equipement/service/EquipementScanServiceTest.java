package com.bouba.backend_trans.equipement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.equipement.dto.CandidatEquipement;
import com.bouba.backend_trans.equipement.dto.ScanRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.equipement.scan.HoteAccessible;
import com.bouba.backend_trans.equipement.scan.SnmpClient;
import com.bouba.backend_trans.equipement.scan.SnmpResultat;

/**
 * Le service de scan (issue #152) est testé avec {@link HoteAccessible} et
 * {@link SnmpClient} entièrement simulés (Mockito) : aucun test ici n'ouvre
 * de socket ni ne fait un vrai appel ICMP/SNMP, cohérent avec le reste de la
 * suite qui évite les dépendances réseau réelles en local.
 */
@ExtendWith(MockitoExtension.class)
class EquipementScanServiceTest {

	@Mock
	private EquipementRepository equipementRepository;

	@Mock
	private HoteAccessible hoteAccessible;

	@Mock
	private SnmpClient snmpClient;

	private EquipementScanService scanService;

	@BeforeEach
	void initService() {
		scanService = new EquipementScanService(equipementRepository, hoteAccessible, snmpClient);
	}

	@Test
	void detecte_un_hote_accessible_et_snmp_responsif_comme_candidat() {
		when(hoteAccessible.estAccessible(eq("10.0.0.1"), anyInt())).thenReturn(true);
		when(snmpClient.interroger(eq("10.0.0.1"), anyInt(), anyString(), anyInt()))
				.thenReturn(new SnmpResultat(true, "Cisco IOS", "1.3.6.1.4.1.9.1.1"));
		when(equipementRepository.findByAdresseIpIn(any())).thenReturn(List.of());

		List<CandidatEquipement> candidats = scanService.scanner(requete("10.0.0.1", "10.0.0.1"));

		assertThat(candidats).hasSize(1);
		CandidatEquipement candidat = candidats.get(0);
		assertThat(candidat.ipAddress()).isEqualTo("10.0.0.1");
		assertThat(candidat.reachable()).isTrue();
		assertThat(candidat.snmpResponsive()).isTrue();
		assertThat(candidat.sysDescr()).isEqualTo("Cisco IOS");
		assertThat(candidat.sysObjectID()).isEqualTo("1.3.6.1.4.1.9.1.1");
		assertThat(candidat.dejaDeclare()).isFalse();
	}

	@Test
	void marque_inaccessible_sans_interroger_le_snmp() {
		when(hoteAccessible.estAccessible(eq("10.0.0.2"), anyInt())).thenReturn(false);
		when(equipementRepository.findByAdresseIpIn(any())).thenReturn(List.of());

		List<CandidatEquipement> candidats = scanService.scanner(requete("10.0.0.2", "10.0.0.2"));

		assertThat(candidats).hasSize(1);
		CandidatEquipement candidat = candidats.get(0);
		assertThat(candidat.reachable()).isFalse();
		assertThat(candidat.snmpResponsive()).isFalse();
		assertThat(candidat.sysDescr()).isNull();
		verify(snmpClient, never()).interroger(anyString(), anyInt(), anyString(), anyInt());
	}

	@Test
	void marque_accessible_mais_non_snmp_responsif_quand_le_snmp_ne_repond_pas() {
		when(hoteAccessible.estAccessible(eq("10.0.0.3"), anyInt())).thenReturn(true);
		when(snmpClient.interroger(eq("10.0.0.3"), anyInt(), anyString(), anyInt()))
				.thenReturn(SnmpResultat.AUCUNE_REPONSE);
		when(equipementRepository.findByAdresseIpIn(any())).thenReturn(List.of());

		List<CandidatEquipement> candidats = scanService.scanner(requete("10.0.0.3", "10.0.0.3"));

		assertThat(candidats).hasSize(1);
		CandidatEquipement candidat = candidats.get(0);
		assertThat(candidat.reachable()).isTrue();
		assertThat(candidat.snmpResponsive()).isFalse();
	}

	@Test
	void marque_dejaDeclare_quand_un_equipement_existant_porte_deja_cette_ip() {
		when(hoteAccessible.estAccessible(eq("10.0.0.4"), anyInt())).thenReturn(false);
		Equipement existant = new Equipement();
		existant.setId(UUID.randomUUID());
		existant.setNom("Switch existant");
		existant.setAdresseIp("10.0.0.4");
		existant.setType(TypeEquipement.SWITCH);
		existant.setEtat(EtatEquipement.ACTIF);
		when(equipementRepository.findByAdresseIpIn(any())).thenReturn(List.of(existant));

		List<CandidatEquipement> candidats = scanService.scanner(requete("10.0.0.4", "10.0.0.4"));

		assertThat(candidats).hasSize(1);
		assertThat(candidats.get(0).dejaDeclare()).isTrue();
	}

	@Test
	void analyse_chaque_adresse_de_la_plage() {
		when(hoteAccessible.estAccessible(anyString(), anyInt())).thenReturn(false);
		when(equipementRepository.findByAdresseIpIn(any())).thenReturn(List.of());

		List<CandidatEquipement> candidats = scanService.scanner(requete("10.0.0.1", "10.0.0.5"));

		assertThat(candidats).extracting(CandidatEquipement::ipAddress).containsExactlyInAnyOrder(
				"10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5");
		verify(snmpClient, never()).interroger(anyString(), anyInt(), anyString(), anyInt());
	}

	private ScanRequest requete(String ipDebut, String ipFin) {
		ScanRequest request = new ScanRequest();
		request.setIpDebut(ipDebut);
		request.setIpFin(ipFin);
		request.setCommunaute("public");
		request.setPort(161);
		return request;
	}
}
