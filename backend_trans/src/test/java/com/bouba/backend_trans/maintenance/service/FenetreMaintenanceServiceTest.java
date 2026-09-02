package com.bouba.backend_trans.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.maintenance.dto.FenetreMaintenanceRequest;
import com.bouba.backend_trans.maintenance.entity.FenetreMaintenance;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;

@ExtendWith(MockitoExtension.class)
class FenetreMaintenanceServiceTest {

	@Mock
	private FenetreMaintenanceRepository fenetreMaintenanceRepository;

	@Mock
	private EquipementRepository equipementRepository;

	private FenetreMaintenanceService fenetreMaintenanceService;

	@BeforeEach
	void initService() {
		fenetreMaintenanceService = new FenetreMaintenanceService(fenetreMaintenanceRepository, equipementRepository);
	}

	// --- estActive : c'est ce que consulte le moteur d'alertes (issue #160) ---

	@Test
	void est_active_quand_une_fenetre_non_annulee_couvre_l_instant_present() {
		UUID equipementId = UUID.randomUUID();
		when(fenetreMaintenanceRepository
				.existsByEquipementIdAndAnnuleeFalseAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
						any(), any(), any()))
				.thenReturn(true);

		assertThat(fenetreMaintenanceService.estActive(equipementId)).isTrue();
	}

	@Test
	void n_est_pas_active_quand_aucune_fenetre_ne_couvre_l_instant_present() {
		UUID equipementId = UUID.randomUUID();
		when(fenetreMaintenanceRepository
				.existsByEquipementIdAndAnnuleeFalseAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
						any(), any(), any()))
				.thenReturn(false);

		assertThat(fenetreMaintenanceService.estActive(equipementId)).isFalse();
	}

	// --- création ---

	@Test
	void cree_une_fenetre_de_maintenance_pour_un_equipement_existant() {
		Equipement equipement = equipement();
		AppUser createur = utilisateur();
		LocalDateTime debut = LocalDateTime.now().plusHours(1);
		LocalDateTime fin = debut.plusHours(2);
		FenetreMaintenanceRequest request = requete(debut, fin, "Remplacement de l'alimentation");
		when(equipementRepository.findById(equipement.getId())).thenReturn(Optional.of(equipement));
		when(fenetreMaintenanceRepository.save(any(FenetreMaintenance.class))).thenAnswer(inv -> inv.getArgument(0));

		FenetreMaintenance enregistree = fenetreMaintenanceService.create(equipement.getId(), request, createur);

		assertThat(enregistree.getEquipement()).isEqualTo(equipement);
		assertThat(enregistree.getDateDebut()).isEqualTo(debut);
		assertThat(enregistree.getDateFin()).isEqualTo(fin);
		assertThat(enregistree.getCreePar()).isEqualTo(createur);
		assertThat(enregistree.getCommentaire()).isEqualTo("Remplacement de l'alimentation");
		assertThat(enregistree.isAnnulee()).isFalse();
	}

	@Test
	void refuse_de_creer_une_fenetre_pour_un_equipement_introuvable() {
		UUID equipementId = UUID.randomUUID();
		LocalDateTime debut = LocalDateTime.now().plusHours(1);
		FenetreMaintenanceRequest request = requete(debut, debut.plusHours(1), null);
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> fenetreMaintenanceService.create(equipementId, request, utilisateur()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
		verify(fenetreMaintenanceRepository, never()).save(any());
	}

	@Test
	void refuse_une_date_de_fin_anterieure_ou_egale_a_la_date_de_debut() {
		LocalDateTime debut = LocalDateTime.now().plusHours(1);
		FenetreMaintenanceRequest request = requete(debut, debut, null);

		assertThatThrownBy(() -> fenetreMaintenanceService.create(UUID.randomUUID(), request, utilisateur()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La date de fin doit être postérieure à la date de début.");
		verifyNoInteractions(equipementRepository, fenetreMaintenanceRepository);
	}

	@Test
	void refuse_une_fenetre_sans_date_de_debut_ni_de_fin() {
		FenetreMaintenanceRequest request = requete(null, null, null);

		assertThatThrownBy(() -> fenetreMaintenanceService.create(UUID.randomUUID(), request, utilisateur()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La date de début et la date de fin sont obligatoires.");
		verifyNoInteractions(equipementRepository, fenetreMaintenanceRepository);
	}

	// --- liste par équipement ---

	@Test
	void liste_les_fenetres_d_un_equipement_existant() {
		Equipement equipement = equipement();
		List<FenetreMaintenance> fenetres = List.of(fenetre(equipement, false));
		when(equipementRepository.findById(equipement.getId())).thenReturn(Optional.of(equipement));
		when(fenetreMaintenanceRepository.findByEquipementIdOrderByDateDebutDesc(equipement.getId()))
				.thenReturn(fenetres);

		assertThat(fenetreMaintenanceService.findByEquipement(equipement.getId())).isEqualTo(fenetres);
	}

	@Test
	void refuse_de_lister_les_fenetres_d_un_equipement_introuvable() {
		UUID equipementId = UUID.randomUUID();
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> fenetreMaintenanceService.findByEquipement(equipementId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
		verify(fenetreMaintenanceRepository, never()).findByEquipementIdOrderByDateDebutDesc(any());
	}

	// --- annulation ---

	@Test
	void annule_une_fenetre_de_maintenance_active() {
		Equipement equipement = equipement();
		FenetreMaintenance fenetre = fenetre(equipement, false);
		when(fenetreMaintenanceRepository.findById(fenetre.getId())).thenReturn(Optional.of(fenetre));
		when(fenetreMaintenanceRepository.save(fenetre)).thenReturn(fenetre);

		FenetreMaintenance annulee = fenetreMaintenanceService.annuler(equipement.getId(), fenetre.getId());

		assertThat(annulee.isAnnulee()).isTrue();
		verify(fenetreMaintenanceRepository).save(fenetre);
	}

	@Test
	void refuse_d_annuler_une_fenetre_deja_annulee() {
		Equipement equipement = equipement();
		FenetreMaintenance fenetre = fenetre(equipement, true);
		when(fenetreMaintenanceRepository.findById(fenetre.getId())).thenReturn(Optional.of(fenetre));

		assertThatThrownBy(() -> fenetreMaintenanceService.annuler(equipement.getId(), fenetre.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Cette fenêtre de maintenance est déjà annulée.");
		verify(fenetreMaintenanceRepository, never()).save(any());
	}

	@Test
	void refuse_d_annuler_une_fenetre_introuvable() {
		UUID id = UUID.randomUUID();
		when(fenetreMaintenanceRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> fenetreMaintenanceService.annuler(UUID.randomUUID(), id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Fenêtre de maintenance introuvable.");
	}

	@Test
	void refuse_d_annuler_une_fenetre_qui_n_appartient_pas_a_cet_equipement() {
		Equipement equipement = equipement();
		FenetreMaintenance fenetre = fenetre(equipement, false);
		when(fenetreMaintenanceRepository.findById(fenetre.getId())).thenReturn(Optional.of(fenetre));

		assertThatThrownBy(() -> fenetreMaintenanceService.annuler(UUID.randomUUID(), fenetre.getId()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Fenêtre de maintenance introuvable.");
		verify(fenetreMaintenanceRepository, never()).save(any());
	}

	// --- fixtures ---

	private FenetreMaintenanceRequest requete(LocalDateTime debut, LocalDateTime fin, String commentaire) {
		FenetreMaintenanceRequest request = new FenetreMaintenanceRequest();
		request.setDateDebut(debut);
		request.setDateFin(fin);
		request.setCommentaire(commentaire);
		return request;
	}

	private Equipement equipement() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur coeur");
		return equipement;
	}

	private AppUser utilisateur() {
		AppUser appUser = new AppUser();
		appUser.setId(1L);
		appUser.setUsername("technicien");
		appUser.setEmail("technicien@ept.sn");
		return appUser;
	}

	private FenetreMaintenance fenetre(Equipement equipement, boolean annulee) {
		FenetreMaintenance fenetre = new FenetreMaintenance();
		fenetre.setId(UUID.randomUUID());
		fenetre.setEquipement(equipement);
		fenetre.setDateDebut(LocalDateTime.now().minusHours(1));
		fenetre.setDateFin(LocalDateTime.now().plusHours(1));
		fenetre.setCreePar(utilisateur());
		fenetre.setAnnulee(annulee);
		return fenetre;
	}
}
