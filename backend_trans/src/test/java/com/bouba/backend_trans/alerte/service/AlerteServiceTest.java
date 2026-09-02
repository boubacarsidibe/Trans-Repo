package com.bouba.backend_trans.alerte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.maintenance.service.FenetreMaintenanceService;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

@ExtendWith(MockitoExtension.class)
class AlerteServiceTest {

	@Mock
	private AlerteRepository alerteRepository;

	@Mock
	private DiffusionSupervision diffusionSupervision;

	@Mock
	private FenetreMaintenanceService fenetreMaintenanceService;

	private AlerteService alerteService;

	@BeforeEach
	void initService() {
		alerteService = new AlerteService(alerteRepository, diffusionSupervision, fenetreMaintenanceService);
	}

	// --- déclenchement : règle F2 (maintenance) ---

	@Test
	void n_alerte_pas_l_indisponibilite_d_un_equipement_en_maintenance() {
		Equipement equipement = equipement("Switch etage 3", EtatEquipement.EN_MAINTENANCE);

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);

		verifyNoInteractions(alerteRepository, diffusionSupervision);
	}

	@Test
	void alerte_quand_meme_une_saturation_disque_pendant_une_maintenance() {
		Equipement equipement = equipement("Switch etage 3", EtatEquipement.EN_MAINTENANCE);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.DISQUE, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.DISQUE, Severite.AVERTISSEMENT);

		verify(alerteRepository).save(any(Alerte.class));
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_CREATED), any());
	}

	// --- déclenchement : dépendance à un parent déjà en panne ---

	@Test
	void n_alerte_pas_un_equipement_injoignable_a_cause_d_un_parent_deja_en_panne() {
		Equipement parent = equipement("Switch etage 3", EtatEquipement.ACTIF);
		Equipement equipement = equipement("Poste 12", EtatEquipement.ACTIF);
		equipement.setDependDe(parent);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				parent.getId(), TypeAnomalie.INDISPONIBILITE, StatutAlerte.RESOLUE))
				.thenReturn(Optional.of(alerte(parent, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE, StatutAlerte.DECLENCHEE)));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);

		verify(alerteRepository, never()).save(any());
		verifyNoInteractions(diffusionSupervision);
	}

	@Test
	void alerte_normalement_un_equipement_dont_le_parent_est_toujours_joignable() {
		Equipement parent = equipement("Switch etage 3", EtatEquipement.ACTIF);
		Equipement equipement = equipement("Poste 12", EtatEquipement.ACTIF);
		equipement.setDependDe(parent);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				parent.getId(), TypeAnomalie.INDISPONIBILITE, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.INDISPONIBILITE, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);

		verify(alerteRepository).save(any(Alerte.class));
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_CREATED), any());
	}

	// --- déclenchement : création vs anti-répétition (§11.4) ---

	@Test
	void cree_une_alerte_d_indisponibilite_pour_un_equipement_actif_qui_tombe() {
		Equipement equipement = equipement("Routeur coeur", EtatEquipement.ACTIF);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.INDISPONIBILITE, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);

		ArgumentCaptor<Alerte> captor = ArgumentCaptor.forClass(Alerte.class);
		verify(alerteRepository).save(captor.capture());
		Alerte creee = captor.getValue();
		assertThat(creee.getEquipement()).isEqualTo(equipement);
		assertThat(creee.getTypeAnomalie()).isEqualTo(TypeAnomalie.INDISPONIBILITE);
		assertThat(creee.getSeverite()).isEqualTo(Severite.CRITIQUE);
		assertThat(creee.getStatut()).isEqualTo(StatutAlerte.DECLENCHEE);
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_CREATED), any());
	}

	@Test
	void eleve_la_severite_d_une_alerte_active_moins_grave_au_lieu_d_en_creer_une_autre() {
		Equipement equipement = equipement("Serveur web", EtatEquipement.ACTIF);
		Alerte active = alerte(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT, StatutAlerte.DECLENCHEE);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.of(active));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);

		assertThat(active.getSeverite()).isEqualTo(Severite.CRITIQUE);
		verify(alerteRepository).save(active);
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_UPDATED), any());
	}

	@Test
	void ne_touche_pas_une_alerte_active_deja_au_moins_aussi_severe() {
		Equipement equipement = equipement("Serveur web", EtatEquipement.ACTIF);
		Alerte active = alerte(equipement, TypeAnomalie.CPU, Severite.CRITIQUE, StatutAlerte.DECLENCHEE);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.of(active));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT);

		assertThat(active.getSeverite()).isEqualTo(Severite.CRITIQUE);
		verify(alerteRepository, never()).save(any());
		verifyNoInteractions(diffusionSupervision);
	}

	// --- fenêtre de maintenance active (issue #160) ---

	@Test
	void ne_cree_pas_de_nouvelle_alerte_pendant_une_fenetre_de_maintenance_active() {
		Equipement equipement = equipement("Serveur applicatif", EtatEquipement.ACTIF);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(fenetreMaintenanceService.estActive(equipement.getId())).thenReturn(true);

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);

		verify(alerteRepository, never()).save(any());
		verifyNoInteractions(diffusionSupervision);
	}

	@Test
	void cree_normalement_une_alerte_quand_aucune_fenetre_de_maintenance_n_est_active() {
		Equipement equipement = equipement("Serveur applicatif", EtatEquipement.ACTIF);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());
		when(fenetreMaintenanceService.estActive(equipement.getId())).thenReturn(false);
		when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);

		verify(alerteRepository).save(any(Alerte.class));
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_CREATED), any());
	}

	@Test
	void eleve_quand_meme_la_severite_d_une_alerte_deja_ouverte_pendant_une_maintenance() {
		Equipement equipement = equipement("Serveur applicatif", EtatEquipement.ACTIF);
		Alerte active = alerte(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT, StatutAlerte.DECLENCHEE);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.of(active));

		alerteService.declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);

		assertThat(active.getSeverite()).isEqualTo(Severite.CRITIQUE);
		verify(alerteRepository).save(active);
		// La fenêtre de maintenance ne bloque que la création d'une alerte nouvelle :
		// une alerte déjà ouverte n'a même pas à être consultée ici.
		verifyNoInteractions(fenetreMaintenanceService);
	}

	// --- résolution automatique ---

	@Test
	void resoud_l_alerte_active_quand_la_metrique_repasse_sous_le_seuil() {
		Equipement equipement = equipement("Serveur web", EtatEquipement.ACTIF);
		Alerte active = alerte(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT, StatutAlerte.DECLENCHEE);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.of(active));

		alerteService.resoudreSiActive(equipement, TypeAnomalie.CPU);

		assertThat(active.getStatut()).isEqualTo(StatutAlerte.RESOLUE);
		assertThat(active.getDateResolution()).isNotNull();
		verify(alerteRepository).save(active);
		verify(diffusionSupervision).publier(eq(TypeEvenement.ALERT_RESOLVED), any());
	}

	@Test
	void ne_fait_rien_quand_aucune_alerte_n_est_active_pour_cet_equipement() {
		Equipement equipement = equipement("Serveur web", EtatEquipement.ACTIF);
		when(alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), TypeAnomalie.CPU, StatutAlerte.RESOLUE))
				.thenReturn(Optional.empty());

		alerteService.resoudreSiActive(equipement, TypeAnomalie.CPU);

		verify(alerteRepository, never()).save(any());
		verifyNoInteractions(diffusionSupervision);
	}

	// --- recherche paginée et filtrable (§7.9) ---

	@Test
	void recherche_avec_statut_et_severite_delegue_a_la_requete_combinee() {
		Pageable pageable = PageRequest.of(0, 20);
		List<Alerte> resultat = List.of(alerte(
				equipement("Routeur coeur", EtatEquipement.ACTIF), TypeAnomalie.CPU, Severite.CRITIQUE, StatutAlerte.DECLENCHEE));
		when(alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE, pageable))
				.thenReturn(resultat);

		List<Alerte> obtenu = alerteService.rechercher(StatutAlerte.DECLENCHEE, Severite.CRITIQUE, pageable);

		assertThat(obtenu).isEqualTo(resultat);
		verify(alerteRepository).findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE, pageable);
		verify(alerteRepository, never()).findAllBy(any());
	}

	@Test
	void recherche_avec_seulement_le_statut_delegue_a_la_requete_correspondante() {
		Pageable pageable = PageRequest.of(1, 50);
		List<Alerte> resultat = List.of(alerte(
				equipement("Routeur coeur", EtatEquipement.ACTIF), TypeAnomalie.CPU, Severite.INFO, StatutAlerte.PRISE_EN_COMPTE));
		when(alerteRepository.findByStatut(StatutAlerte.PRISE_EN_COMPTE, pageable)).thenReturn(resultat);

		List<Alerte> obtenu = alerteService.rechercher(StatutAlerte.PRISE_EN_COMPTE, null, pageable);

		assertThat(obtenu).isEqualTo(resultat);
		verify(alerteRepository).findByStatut(StatutAlerte.PRISE_EN_COMPTE, pageable);
		verify(alerteRepository, never()).findAllBy(any());
	}

	@Test
	void recherche_avec_seulement_la_severite_delegue_a_la_requete_correspondante() {
		Pageable pageable = PageRequest.of(0, 100);
		List<Alerte> resultat = List.of(alerte(
				equipement("Routeur coeur", EtatEquipement.ACTIF), TypeAnomalie.RESEAU, Severite.AVERTISSEMENT, StatutAlerte.DECLENCHEE));
		when(alerteRepository.findBySeverite(Severite.AVERTISSEMENT, pageable)).thenReturn(resultat);

		List<Alerte> obtenu = alerteService.rechercher(null, Severite.AVERTISSEMENT, pageable);

		assertThat(obtenu).isEqualTo(resultat);
		verify(alerteRepository).findBySeverite(Severite.AVERTISSEMENT, pageable);
		verify(alerteRepository, never()).findAllBy(any());
	}

	@Test
	void recherche_sans_filtre_delegue_a_la_requete_non_filtree() {
		Pageable pageable = PageRequest.of(0, 200);
		List<Alerte> resultat = List.of(alerte(
				equipement("Routeur coeur", EtatEquipement.ACTIF), TypeAnomalie.MATERIEL, Severite.CRITIQUE, StatutAlerte.DECLENCHEE));
		when(alerteRepository.findAllBy(pageable)).thenReturn(resultat);

		List<Alerte> obtenu = alerteService.rechercher(null, null, pageable);

		assertThat(obtenu).isEqualTo(resultat);
		verify(alerteRepository).findAllBy(pageable);
	}

	// --- findById ---

	@Test
	void leve_une_exception_quand_l_alerte_est_introuvable() {
		UUID id = UUID.randomUUID();
		when(alerteRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> alerteService.findById(id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Alerte introuvable.");
	}

	// --- fixtures ---

	private Equipement equipement(String nom, EtatEquipement etat) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom(nom);
		equipement.setEtat(etat);
		return equipement;
	}

	private Alerte alerte(Equipement equipement, TypeAnomalie typeAnomalie, Severite severite, StatutAlerte statut) {
		Alerte alerte = new Alerte();
		alerte.setId(UUID.randomUUID());
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(typeAnomalie);
		alerte.setSeverite(severite);
		alerte.setStatut(statut);
		return alerte;
	}
}
