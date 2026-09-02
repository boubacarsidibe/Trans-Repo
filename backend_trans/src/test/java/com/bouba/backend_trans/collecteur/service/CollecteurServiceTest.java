package com.bouba.backend_trans.collecteur.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bouba.backend_trans.collecteur.entity.Collecteur;
import com.bouba.backend_trans.collecteur.repository.CollecteurRepository;

class CollecteurServiceTest {

	private final CollecteurRepository collecteurRepository = mock(CollecteurRepository.class);
	private final CollecteurService collecteurService = new CollecteurService(collecteurRepository);

	@Test
	void cree_une_nouvelle_ligne_au_premier_heartbeat() {
		when(collecteurRepository.findById("primaire")).thenReturn(Optional.empty());
		when(collecteurRepository.findByActifTrueAndCollecteurIdNot("primaire")).thenReturn(List.of());

		collecteurService.enregistrerHeartbeat("primaire", true);

		var captureur = org.mockito.ArgumentCaptor.forClass(Collecteur.class);
		verify(collecteurRepository).save(captureur.capture());
		Collecteur enregistre = captureur.getValue();
		assertThat(enregistre.getCollecteurId()).isEqualTo("primaire");
		assertThat(enregistre.isActif()).isTrue();
		assertThat(enregistre.getDernierHeartbeat()).isNotNull();
	}

	@Test
	void met_a_jour_le_dernier_heartbeat_d_une_instance_existante() {
		Collecteur existant = collecteur("primaire", true, LocalDateTime.now().minusMinutes(5));
		when(collecteurRepository.findById("primaire")).thenReturn(Optional.of(existant));
		when(collecteurRepository.findByActifTrueAndCollecteurIdNot("primaire")).thenReturn(List.of());

		collecteurService.enregistrerHeartbeat("primaire", true);

		assertThat(existant.getDernierHeartbeat()).isAfter(LocalDateTime.now().minusSeconds(5));
	}

	@Test
	void desactive_les_autres_instances_actives_quand_une_nouvelle_prend_le_relais() {
		Collecteur primairePerimee = collecteur("primaire", true, LocalDateTime.now().minusMinutes(10));
		when(collecteurRepository.findById("secondaire")).thenReturn(Optional.empty());
		when(collecteurRepository.findByActifTrueAndCollecteurIdNot("secondaire"))
				.thenReturn(List.of(primairePerimee));

		collecteurService.enregistrerHeartbeat("secondaire", true);

		assertThat(primairePerimee.isActif()).isFalse();
	}

	@Test
	void ne_touche_pas_aux_autres_instances_quand_le_heartbeat_n_est_pas_actif() {
		when(collecteurRepository.findById("secondaire")).thenReturn(Optional.empty());

		collecteurService.enregistrerHeartbeat("secondaire", false);

		verify(collecteurRepository, never()).findByActifTrueAndCollecteurIdNot(any());
		var captureur = org.mockito.ArgumentCaptor.forClass(Collecteur.class);
		verify(collecteurRepository).save(captureur.capture());
		assertThat(captureur.getValue().isActif()).isFalse();
	}

	private Collecteur collecteur(String id, boolean actif, LocalDateTime dernierHeartbeat) {
		Collecteur collecteur = new Collecteur();
		collecteur.setCollecteurId(id);
		collecteur.setActif(actif);
		collecteur.setDernierHeartbeat(dernierHeartbeat);
		return collecteur;
	}
}
