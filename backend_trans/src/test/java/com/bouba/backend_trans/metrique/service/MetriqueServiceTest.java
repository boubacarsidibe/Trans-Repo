package com.bouba.backend_trans.metrique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.metrique.dto.MetriquesEquipement;
import com.bouba.backend_trans.metrique.dto.NetworkMetricsRequest;
import com.bouba.backend_trans.metrique.dto.SystemMetricsRequest;
import com.bouba.backend_trans.metrique.entity.Metrique;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

@ExtendWith(MockitoExtension.class)
class MetriqueServiceTest {

	@Mock
	private MetriqueRepository metriqueRepository;

	@Mock
	private EquipementRepository equipementRepository;

	@Mock
	private MetriqueSeuilEvaluator seuilEvaluator;

	@Mock
	private DiffusionSupervision diffusionSupervision;

	private MetriqueService service;

	private UUID equipementId;
	private Equipement equipement;

	@BeforeEach
	void initService() {
		service = new MetriqueService(metriqueRepository, equipementRepository, seuilEvaluator, diffusionSupervision);
		equipementId = UUID.randomUUID();
		equipement = new Equipement();
		equipement.setId(equipementId);
		equipement.setNom("Serveur applicatif");
	}

	// --- ingestion système ---

	@Test
	void ingestion_systeme_rejette_un_equipement_introuvable() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.empty());
		SystemMetricsRequest request = requeteSysteme(equipementId);

		assertThatThrownBy(() -> service.ingestSystemMetrics(request))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(metriqueRepository, diffusionSupervision);
	}

	@Test
	void ingestion_systeme_enregistre_uniquement_les_valeurs_fournies() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.of(equipement));
		SystemMetricsRequest request = requeteSysteme(equipementId);
		request.setCpuPercent(new BigDecimal("42.5"));
		request.setMemoryPercent(new BigDecimal("60"));
		// tous les autres champs restent null : ils ne doivent générer aucune ligne

		service.ingestSystemMetrics(request);

		ArgumentCaptor<Metrique> captor = ArgumentCaptor.forClass(Metrique.class);
		verify(metriqueRepository, times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(Metrique::getTypeMetrique)
				.containsExactlyInAnyOrder(TypeMetrique.CPU, TypeMetrique.RAM);
		assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.getEquipement()).isEqualTo(equipement));
	}

	@Test
	void ingestion_systeme_evalue_le_seuil_pour_chaque_valeur_enregistree() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.of(equipement));
		SystemMetricsRequest request = requeteSysteme(equipementId);
		request.setCpuPercent(new BigDecimal("42.5"));

		service.ingestSystemMetrics(request);

		verify(seuilEvaluator).evaluer(equipement, TypeMetrique.CPU, new BigDecimal("42.5"));
	}

	@Test
	void ingestion_systeme_met_a_jour_la_derniere_mesure_et_diffuse_un_evenement() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.of(equipement));
		SystemMetricsRequest request = requeteSysteme(equipementId);
		request.setCpuPercent(new BigDecimal("42.5"));

		service.ingestSystemMetrics(request);

		assertThat(equipement.getDerniereMesure()).isNotNull();
		verify(equipementRepository).save(equipement);

		ArgumentCaptor<MetriquesEquipement> captor = ArgumentCaptor.forClass(MetriquesEquipement.class);
		verify(diffusionSupervision).publier(eq(TypeEvenement.METRIC_UPDATE), captor.capture());
		assertThat(captor.getValue().equipementId()).isEqualTo(equipementId);
		assertThat(captor.getValue().metriques()).hasSize(1);
	}

	@Test
	void ingestion_systeme_ne_diffuse_rien_quand_toutes_les_valeurs_sont_absentes() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.of(equipement));
		SystemMetricsRequest request = requeteSysteme(equipementId);

		service.ingestSystemMetrics(request);

		verifyNoInteractions(metriqueRepository, diffusionSupervision);
		verify(equipementRepository, never()).save(any());
		assertThat(equipement.getDerniereMesure()).isNull();
	}

	// --- ingestion réseau ---

	@Test
	void ingestion_reseau_enregistre_les_trois_metriques_fournies() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.of(equipement));
		NetworkMetricsRequest request = new NetworkMetricsRequest();
		request.setEquipmentId(equipementId);
		request.setBandwidthMbps(new BigDecimal("120"));
		request.setLatencyMs(new BigDecimal("8"));
		request.setErrorRatePercent(new BigDecimal("0.5"));

		service.ingestNetworkMetrics(request);

		ArgumentCaptor<Metrique> captor = ArgumentCaptor.forClass(Metrique.class);
		verify(metriqueRepository, times(3)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(Metrique::getTypeMetrique)
				.containsExactlyInAnyOrder(TypeMetrique.BANDE_PASSANTE, TypeMetrique.LATENCE, TypeMetrique.TAUX_ERREUR);
	}

	@Test
	void ingestion_reseau_rejette_un_equipement_introuvable() {
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.empty());
		NetworkMetricsRequest request = new NetworkMetricsRequest();
		request.setEquipmentId(equipementId);

		assertThatThrownBy(() -> service.ingestNetworkMetrics(request))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// --- lecture de l'historique ---

	@Test
	void historique_sans_type_interroge_la_variante_non_filtree() {
		LocalDateTime depuis = LocalDateTime.now().minusHours(24);
		Pageable pageable = PageRequest.of(0, 1000);
		when(metriqueRepository.findByEquipementIdAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
				equipementId, depuis, pageable)).thenReturn(java.util.List.of());

		service.historiqueParEquipement(equipementId, null, depuis, pageable);

		verify(metriqueRepository).findByEquipementIdAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
				equipementId, depuis, pageable);
		verify(metriqueRepository, never())
				.findByEquipementIdAndTypeMetriqueAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
						any(), any(), any(), any());
	}

	@Test
	void historique_avec_type_interroge_la_variante_filtree() {
		LocalDateTime depuis = LocalDateTime.now().minusHours(24);
		Pageable pageable = PageRequest.of(0, 1000);
		when(metriqueRepository.findByEquipementIdAndTypeMetriqueAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
				equipementId, TypeMetrique.CPU, depuis, pageable)).thenReturn(java.util.List.of());

		service.historiqueParEquipement(equipementId, TypeMetrique.CPU, depuis, pageable);

		verify(metriqueRepository).findByEquipementIdAndTypeMetriqueAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
				equipementId, TypeMetrique.CPU, depuis, pageable);
		verify(metriqueRepository, never())
				.findByEquipementIdAndHorodatageGreaterThanEqualOrderByHorodatageDesc(any(), any(), any());
	}

	// --- fixtures ---

	private SystemMetricsRequest requeteSysteme(UUID equipementId) {
		SystemMetricsRequest request = new SystemMetricsRequest();
		request.setEquipmentId(equipementId);
		return request;
	}
}
