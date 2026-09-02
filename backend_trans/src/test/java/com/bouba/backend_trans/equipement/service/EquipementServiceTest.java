package com.bouba.backend_trans.equipement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.equipement.dto.EquipementRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.seuil.repository.SeuilAlerteRepository;

@ExtendWith(MockitoExtension.class)
class EquipementServiceTest {

	@Mock
	private EquipementRepository equipementRepository;

	@Mock
	private MetriqueRepository metriqueRepository;

	@Mock
	private AlerteRepository alerteRepository;

	@Mock
	private SeuilAlerteRepository seuilAlerteRepository;

	@Mock
	private FenetreMaintenanceRepository fenetreMaintenanceRepository;

	private EquipementService equipementService;

	@BeforeEach
	void initService() {
		equipementService = new EquipementService(equipementRepository, metriqueRepository, alerteRepository,
				seuilAlerteRepository, fenetreMaintenanceRepository);
	}

	// --- findAll / findById ---

	@Test
	void retourne_tous_les_equipements() {
		List<Equipement> equipements = List.of(equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF));
		when(equipementRepository.findAll()).thenReturn(equipements);

		assertThat(equipementService.findAll()).isEqualTo(equipements);
	}

	@Test
	void retourne_l_equipement_correspondant_a_l_identifiant() {
		Equipement equipement = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(equipement.getId())).thenReturn(Optional.of(equipement));

		assertThat(equipementService.findById(equipement.getId())).isEqualTo(equipement);
	}

	@Test
	void leve_une_exception_quand_l_equipement_est_introuvable() {
		UUID id = UUID.randomUUID();
		when(equipementRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> equipementService.findById(id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
	}

	// --- create ---

	@Test
	void cree_un_equipement_et_lui_genere_une_cle_api_quand_aucune_n_est_fournie() {
		EquipementRequest request = requete("Routeur coeur", "10.0.0.1", TypeEquipement.ROUTEUR);
		when(equipementRepository.existsByAdresseIp("10.0.0.1")).thenReturn(false);
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Equipement cree = equipementService.create(request);

		assertThat(cree.getNom()).isEqualTo("Routeur coeur");
		assertThat(cree.getEtat()).isEqualTo(EtatEquipement.ACTIF);
		assertThat(cree.getCleApi()).isNotBlank();
	}

	@Test
	void conserve_la_cle_api_fournie_a_la_creation() {
		EquipementRequest request = requete("Routeur coeur", "10.0.0.1", TypeEquipement.ROUTEUR);
		request.setCleApi("cle-imposee");
		when(equipementRepository.existsByAdresseIp("10.0.0.1")).thenReturn(false);
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Equipement cree = equipementService.create(request);

		assertThat(cree.getCleApi()).isEqualTo("cle-imposee");
	}

	@Test
	void applique_l_etat_actif_par_defaut_quand_aucun_etat_n_est_precise() {
		EquipementRequest request = requete("Switch", "10.0.0.2", TypeEquipement.SWITCH);
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Equipement cree = equipementService.create(request);

		assertThat(cree.getEtat()).isEqualTo(EtatEquipement.ACTIF);
	}

	@Test
	void rejette_la_creation_d_un_equipement_dont_l_adresse_ip_est_deja_utilisee() {
		EquipementRequest request = requete("Routeur coeur", "10.0.0.1", TypeEquipement.ROUTEUR);
		when(equipementRepository.existsByAdresseIp("10.0.0.1")).thenReturn(true);

		assertThatThrownBy(() -> equipementService.create(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Un équipement avec cette adresse IP existe déjà.");

		verify(equipementRepository, never()).save(any());
	}

	@Test
	void rattache_l_equipement_cree_a_l_equipement_dont_il_depend() {
		Equipement parent = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		EquipementRequest request = requete("Poste", "10.0.0.5", TypeEquipement.POINT_ACCES);
		request.setDependDeId(parent.getId());

		Equipement cree = equipementService.create(request);

		assertThat(cree.getDependDe()).isEqualTo(parent);
	}

	@Test
	void rejette_une_dependance_vers_un_equipement_introuvable() {
		UUID parentId = UUID.randomUUID();
		when(equipementRepository.findById(parentId)).thenReturn(Optional.empty());

		EquipementRequest request = requete("Poste", "10.0.0.5", TypeEquipement.POINT_ACCES);
		request.setDependDeId(parentId);

		assertThatThrownBy(() -> equipementService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
	}

	// --- update ---

	@Test
	void modifie_les_champs_d_un_equipement_existant() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		EquipementRequest request = requete("Nouveau nom", "10.0.0.1", TypeEquipement.SERVEUR);
		request.setEtat(EtatEquipement.EN_MAINTENANCE);

		Equipement modifie = equipementService.update(existant.getId(), request);

		assertThat(modifie.getNom()).isEqualTo("Nouveau nom");
		assertThat(modifie.getType()).isEqualTo(TypeEquipement.SERVEUR);
		assertThat(modifie.getEtat()).isEqualTo(EtatEquipement.EN_MAINTENANCE);
		verify(equipementRepository, never()).existsByAdresseIp(any());
	}

	@Test
	void leve_une_exception_lors_de_la_modification_d_un_equipement_introuvable() {
		UUID id = UUID.randomUUID();
		when(equipementRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> equipementService.update(id, requete("Nom", "10.0.0.1", TypeEquipement.SERVEUR)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
	}

	@Test
	void rejette_la_modification_vers_une_adresse_ip_deja_utilisee_par_un_autre_equipement() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(equipementRepository.existsByAdresseIp("10.0.0.9")).thenReturn(true);

		EquipementRequest request = requete("Nom", "10.0.0.9", TypeEquipement.SERVEUR);

		assertThatThrownBy(() -> equipementService.update(existant.getId(), request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Un équipement avec cette adresse IP existe déjà.");

		verify(equipementRepository, never()).save(any());
	}

	@Test
	void autorise_la_modification_vers_une_nouvelle_adresse_ip_libre() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(equipementRepository.existsByAdresseIp("10.0.0.9")).thenReturn(false);
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		EquipementRequest request = requete("Nom", "10.0.0.9", TypeEquipement.SERVEUR);

		Equipement modifie = equipementService.update(existant.getId(), request);

		assertThat(modifie.getAdresseIp()).isEqualTo("10.0.0.9");
	}

	@Test
	void rejette_une_dependance_d_un_equipement_envers_lui_meme() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));

		EquipementRequest request = requete("Nom", "10.0.0.1", TypeEquipement.SERVEUR);
		request.setDependDeId(existant.getId());

		assertThatThrownBy(() -> equipementService.update(existant.getId(), request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Un équipement ne peut pas dépendre de lui-même.");
	}

	@Test
	void rejette_une_dependance_qui_formerait_une_boucle() {
		Equipement a = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		a.setNom("Switch A");
		Equipement b = equipement(UUID.randomUUID(), "10.0.0.2", EtatEquipement.ACTIF);
		b.setNom("Switch B");
		b.setDependDe(a);

		when(equipementRepository.findById(a.getId())).thenReturn(Optional.of(a));
		when(equipementRepository.findById(b.getId())).thenReturn(Optional.of(b));

		EquipementRequest request = requete("Switch A", "10.0.0.1", TypeEquipement.SWITCH);
		request.setDependDeId(b.getId());

		assertThatThrownBy(() -> equipementService.update(a.getId(), request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Cette dépendance formerait une boucle : Switch B dépend déjà, directement ou non, de Switch A.");
	}

	// --- archive ---

	@Test
	void archive_un_equipement_en_le_passant_a_l_etat_inactif() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(equipementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		equipementService.archive(existant.getId());

		assertThat(existant.getEtat()).isEqualTo(EtatEquipement.INACTIF);
		verify(equipementRepository).save(existant);
	}

	@Test
	void leve_une_exception_lors_de_l_archivage_d_un_equipement_introuvable() {
		UUID id = UUID.randomUUID();
		when(equipementRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> equipementService.archive(id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
	}

	// --- supprimerDefinitivement ---

	@Test
	void supprime_definitivement_un_equipement_sans_aucun_historique() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(alerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(seuilAlerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(equipementRepository.existsByDependDeId(existant.getId())).thenReturn(false);

		equipementService.supprimerDefinitivement(existant.getId());

		verify(equipementRepository).delete(existant);
	}

	@Test
	void refuse_la_suppression_definitive_d_un_equipement_avec_des_metriques() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(true);

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(existant.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des métriques")
				.hasMessageContaining("Archivez-le à la place");

		verify(equipementRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_equipement_avec_des_alertes() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(alerteRepository.existsByEquipementId(existant.getId())).thenReturn(true);

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(existant.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des alertes");

		verify(equipementRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_equipement_avec_des_seuils() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(alerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(seuilAlerteRepository.existsByEquipementId(existant.getId())).thenReturn(true);

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(existant.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des seuils d'alerte");

		verify(equipementRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_equipement_avec_des_fenetres_de_maintenance() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(alerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(seuilAlerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByEquipementId(existant.getId())).thenReturn(true);

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(existant.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des fenêtres de maintenance");

		verify(equipementRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_equipement_dont_dependent_d_autres_equipements() {
		Equipement existant = equipement(UUID.randomUUID(), "10.0.0.1", EtatEquipement.ACTIF);
		when(equipementRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(metriqueRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(alerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(seuilAlerteRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByEquipementId(existant.getId())).thenReturn(false);
		when(equipementRepository.existsByDependDeId(existant.getId())).thenReturn(true);

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(existant.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des équipements qui en dépendent");

		verify(equipementRepository, never()).delete(any());
	}

	@Test
	void leve_une_exception_lors_de_la_suppression_definitive_d_un_equipement_introuvable() {
		UUID id = UUID.randomUUID();
		when(equipementRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> equipementService.supprimerDefinitivement(id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");

		verify(equipementRepository, never()).delete(any());
	}

	// --- fixtures ---

	private EquipementRequest requete(String nom, String adresseIp, TypeEquipement type) {
		EquipementRequest request = new EquipementRequest();
		request.setNom(nom);
		request.setAdresseIp(adresseIp);
		request.setType(type);
		return request;
	}

	private Equipement equipement(UUID id, String adresseIp, EtatEquipement etat) {
		Equipement equipement = new Equipement();
		equipement.setId(id);
		equipement.setNom("Équipement " + adresseIp);
		equipement.setAdresseIp(adresseIp);
		equipement.setType(TypeEquipement.SERVEUR);
		equipement.setEtat(etat);
		return equipement;
	}
}
