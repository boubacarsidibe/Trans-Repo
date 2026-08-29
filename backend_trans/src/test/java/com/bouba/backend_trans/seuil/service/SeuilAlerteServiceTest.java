package com.bouba.backend_trans.seuil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.seuil.dto.SeuilAlerteRequest;
import com.bouba.backend_trans.seuil.entity.SeuilAlerte;
import com.bouba.backend_trans.seuil.repository.SeuilAlerteRepository;

@ExtendWith(MockitoExtension.class)
class SeuilAlerteServiceTest {

	@Mock
	private SeuilAlerteRepository seuilRepository;

	@Mock
	private EquipementRepository equipementRepository;

	private SeuilAlerteService seuilAlerteService;

	@BeforeEach
	void initService() {
		seuilAlerteService = new SeuilAlerteService(seuilRepository, equipementRepository);
	}

	// --- seuil effectif : surcharge par équipement vs défaut global (F3) ---

	@Test
	void utilise_le_seuil_specifique_a_l_equipement_quand_il_existe() {
		UUID equipementId = UUID.randomUUID();
		SeuilAlerte specifique = seuilAlerte(TypeMetrique.CPU, new BigDecimal("70"), new BigDecimal("90"), 60);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipementId))
				.thenReturn(Optional.of(specifique));

		Seuil seuil = seuilAlerteService.seuilEffectif(equipementId, TypeMetrique.CPU);

		assertThat(seuil.avertissement()).isEqualByComparingTo("70");
		assertThat(seuil.critique()).isEqualByComparingTo("90");
		assertThat(seuil.dureeSecondes()).isEqualTo(60);
		verify(seuilRepository, never()).findByTypeMetriqueAndEquipementIsNull(any());
	}

	@Test
	void se_replie_sur_le_defaut_global_quand_l_equipement_n_a_pas_de_seuil_propre() {
		UUID equipementId = UUID.randomUUID();
		SeuilAlerte defaut = seuilAlerte(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipementId))
				.thenReturn(Optional.empty());
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU))
				.thenReturn(Optional.of(defaut));

		Seuil seuil = seuilAlerteService.seuilEffectif(equipementId, TypeMetrique.CPU);

		assertThat(seuil.avertissement()).isEqualByComparingTo("80");
		assertThat(seuil.critique()).isEqualByComparingTo("95");
		assertThat(seuil.dureeSecondes()).isEqualTo(300);
	}

	@Test
	void retourne_null_quand_ni_seuil_specifique_ni_defaut_global_n_existent() {
		UUID equipementId = UUID.randomUUID();
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipementId))
				.thenReturn(Optional.empty());
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU))
				.thenReturn(Optional.empty());

		assertThat(seuilAlerteService.seuilEffectif(equipementId, TypeMetrique.CPU)).isNull();
	}

	@Test
	void met_en_cache_le_seuil_resolu_pour_ne_pas_relire_la_base_a_chaque_appel() {
		UUID equipementId = UUID.randomUUID();
		SeuilAlerte defaut = seuilAlerte(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipementId))
				.thenReturn(Optional.empty());
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU))
				.thenReturn(Optional.of(defaut));

		seuilAlerteService.seuilEffectif(equipementId, TypeMetrique.CPU);
		seuilAlerteService.seuilEffectif(equipementId, TypeMetrique.CPU);

		verify(seuilRepository, times(1)).findByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU);
	}

	// --- findAll / findById ---

	@Test
	void retourne_tous_les_seuils_tries_par_type_de_metrique() {
		List<SeuilAlerte> seuils = List.of(seuilAlerte(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300));
		when(seuilRepository.findAllByOrderByTypeMetriqueAsc()).thenReturn(seuils);

		assertThat(seuilAlerteService.findAll()).isEqualTo(seuils);
	}

	@Test
	void leve_une_exception_quand_le_seuil_est_introuvable() {
		UUID id = UUID.randomUUID();
		when(seuilRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> seuilAlerteService.findById(id))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Seuil introuvable.");
	}

	// --- création (F3, validation) ---

	@Test
	void cree_un_seuil_par_defaut_global_quand_aucun_n_existe_encore() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, "80", "95", 0);
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.RAM)).thenReturn(Optional.empty());
		when(seuilRepository.save(any(SeuilAlerte.class))).thenAnswer(inv -> inv.getArgument(0));

		SeuilAlerte enregistre = seuilAlerteService.create(request);

		assertThat(enregistre.getEquipement()).isNull();
		assertThat(enregistre.getAvertissement()).isEqualByComparingTo("80");
		assertThat(enregistre.getCritique()).isEqualByComparingTo("95");
		verify(equipementRepository, never()).findById(any());
	}

	@Test
	void cree_un_seuil_propre_a_un_equipement_quand_l_equipement_existe() {
		Equipement equipement = equipement();
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, equipement.getId(), "70", "90", 120);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipement.getId()))
				.thenReturn(Optional.empty());
		when(equipementRepository.findById(equipement.getId())).thenReturn(Optional.of(equipement));
		when(seuilRepository.save(any(SeuilAlerte.class))).thenAnswer(inv -> inv.getArgument(0));

		SeuilAlerte enregistre = seuilAlerteService.create(request);

		assertThat(enregistre.getEquipement()).isEqualTo(equipement);
		assertThat(enregistre.getDureeSecondes()).isEqualTo(120);
	}

	@Test
	void refuse_de_creer_un_second_seuil_global_pour_la_meme_metrique() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, "80", "95", 0);
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.RAM))
				.thenReturn(Optional.of(seuilAlerte(TypeMetrique.RAM, new BigDecimal("80"), new BigDecimal("95"), 0)));

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Un seuil existe déjà pour cette métrique et ce périmètre. "
						+ "Modifiez-le plutôt que d'en créer un second.");
		verify(seuilRepository, never()).save(any());
	}

	@Test
	void refuse_de_creer_un_second_seuil_pour_le_meme_equipement() {
		Equipement equipement = equipement();
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, equipement.getId(), "70", "90", 0);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipement.getId()))
				.thenReturn(Optional.of(seuilAlerte(TypeMetrique.CPU, new BigDecimal("70"), new BigDecimal("90"), 0)));

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class);
		verify(seuilRepository, never()).save(any());
		verify(equipementRepository, never()).findById(any());
	}

	@Test
	void refuse_un_seuil_sans_valeur_d_avertissement_ni_de_critique() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, null, null, 0);

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Un seuil doit porter au moins une valeur d'avertissement ou de critique.");
		verifyNoInteractions(seuilRepository, equipementRepository);
	}

	@Test
	void refuse_un_seuil_d_avertissement_superieur_au_seuil_critique() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, "96", "95", 0);

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Le seuil d'avertissement ne peut pas dépasser le seuil critique.");
		verifyNoInteractions(seuilRepository, equipementRepository);
	}

	@Test
	void refuse_une_duree_de_maintien_negative() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, "80", "95", -1);

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La durée de maintien ne peut pas être négative.");
		verifyNoInteractions(seuilRepository, equipementRepository);
	}

	@Test
	void refuse_de_creer_un_seuil_pour_un_equipement_introuvable() {
		UUID equipementId = UUID.randomUUID();
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, equipementId, "70", "90", 0);
		when(seuilRepository.findByTypeMetriqueAndEquipementId(TypeMetrique.CPU, equipementId))
				.thenReturn(Optional.empty());
		when(equipementRepository.findById(equipementId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> seuilAlerteService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Équipement introuvable.");
		verify(seuilRepository, never()).save(any());
	}

	@Test
	void la_duree_de_maintien_vaut_zero_quand_elle_n_est_pas_precisee() {
		SeuilAlerteRequest request = requete(TypeMetrique.RAM, null, "80", "95", null);
		when(seuilRepository.findByTypeMetriqueAndEquipementIsNull(TypeMetrique.RAM)).thenReturn(Optional.empty());
		when(seuilRepository.save(any(SeuilAlerte.class))).thenAnswer(inv -> inv.getArgument(0));

		SeuilAlerte enregistre = seuilAlerteService.create(request);

		assertThat(enregistre.getDureeSecondes()).isZero();
	}

	// --- modification ---

	@Test
	void modifie_les_valeurs_d_un_seuil_existant() {
		SeuilAlerte existant = seuilAlerte(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);
		when(seuilRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		when(seuilRepository.save(existant)).thenReturn(existant);
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, null, "75", "90", 180);

		SeuilAlerte resultat = seuilAlerteService.update(existant.getId(), request);

		assertThat(resultat.getAvertissement()).isEqualByComparingTo("75");
		assertThat(resultat.getCritique()).isEqualByComparingTo("90");
		assertThat(resultat.getDureeSecondes()).isEqualTo(180);
	}

	@Test
	void leve_une_exception_quand_on_modifie_un_seuil_introuvable() {
		UUID id = UUID.randomUUID();
		when(seuilRepository.findById(id)).thenReturn(Optional.empty());
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, null, "80", "95", 0);

		assertThatThrownBy(() -> seuilAlerteService.update(id, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Seuil introuvable.");
		verify(seuilRepository, never()).save(any());
	}

	@Test
	void refuse_une_modification_ou_l_avertissement_depasse_le_critique() {
		UUID id = UUID.randomUUID();
		SeuilAlerteRequest request = requete(TypeMetrique.CPU, null, "96", "90", 300);

		assertThatThrownBy(() -> seuilAlerteService.update(id, request))
				.isInstanceOf(IllegalArgumentException.class);
		verify(seuilRepository, never()).findById(any());
		verify(seuilRepository, never()).save(any());
	}

	// --- suppression ---

	@Test
	void supprime_un_seuil_existant() {
		SeuilAlerte existant = seuilAlerte(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);
		when(seuilRepository.findById(existant.getId())).thenReturn(Optional.of(existant));

		seuilAlerteService.delete(existant.getId());

		verify(seuilRepository).delete(existant);
	}

	@Test
	void leve_une_exception_quand_on_supprime_un_seuil_introuvable() {
		UUID id = UUID.randomUUID();
		when(seuilRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> seuilAlerteService.delete(id))
				.isInstanceOf(IllegalArgumentException.class);
		verify(seuilRepository, never()).delete(any());
	}

	// --- amorçage des seuils par défaut (idempotence) ---

	@Test
	void cree_le_seuil_par_defaut_quand_il_n_existe_pas_encore() {
		when(seuilRepository.existsByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU)).thenReturn(false);

		seuilAlerteService.creerDefautSiAbsent(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);

		verify(seuilRepository).save(any(SeuilAlerte.class));
	}

	@Test
	void n_ecrase_jamais_un_seuil_par_defaut_deja_present() {
		when(seuilRepository.existsByTypeMetriqueAndEquipementIsNull(TypeMetrique.CPU)).thenReturn(true);

		seuilAlerteService.creerDefautSiAbsent(TypeMetrique.CPU, new BigDecimal("80"), new BigDecimal("95"), 300);

		verify(seuilRepository, never()).save(any());
	}

	// --- fixtures ---

	private SeuilAlerteRequest requete(
			TypeMetrique typeMetrique, UUID equipementId, String avertissement, String critique, Integer dureeSecondes) {
		SeuilAlerteRequest request = new SeuilAlerteRequest();
		request.setTypeMetrique(typeMetrique);
		request.setEquipementId(equipementId);
		request.setAvertissement(avertissement == null ? null : new BigDecimal(avertissement));
		request.setCritique(critique == null ? null : new BigDecimal(critique));
		request.setDureeSecondes(dureeSecondes);
		return request;
	}

	private SeuilAlerte seuilAlerte(TypeMetrique typeMetrique, BigDecimal avertissement, BigDecimal critique, int dureeSecondes) {
		SeuilAlerte seuil = new SeuilAlerte();
		seuil.setId(UUID.randomUUID());
		seuil.setTypeMetrique(typeMetrique);
		seuil.setAvertissement(avertissement);
		seuil.setCritique(critique);
		seuil.setDureeSecondes(dureeSecondes);
		return seuil;
	}

	private Equipement equipement() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur coeur");
		return equipement;
	}
}
