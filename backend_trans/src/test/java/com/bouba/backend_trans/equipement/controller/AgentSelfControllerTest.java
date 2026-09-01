package com.bouba.backend_trans.equipement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.bouba.backend_trans.equipement.dto.EquipementResponse;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.service.EquipementService;

/**
 * L'auto-configuration de l'agent (§ ajout d'équipement réseau sans copier-coller
 * des paramètres SNMP) : l'identité de l'appelant vient de sa clé API (principal
 * posé par ApiKeyAuthenticationFilter), jamais d'un paramètre de la requête.
 */
@ExtendWith(MockitoExtension.class)
class AgentSelfControllerTest {

	@Mock
	private EquipementService equipementService;

	private AgentSelfController agentSelfController;

	@BeforeEach
	void initController() {
		agentSelfController = new AgentSelfController(equipementService);
	}

	@Test
	void renvoie_la_fiche_de_l_equipement_authentifie_par_sa_cle_api() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Switch-Coeur-CRI");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.SWITCH);
		equipement.setCleApi("cle-secrete");
		equipement.setSnmpCommunity("public");
		equipement.setSnmpPort(161);
		equipement.setInterfaceIndex(1);
		when(equipementService.findById(equipement.getId())).thenReturn(equipement);

		Authentication authentication = new UsernamePasswordAuthenticationToken(
				equipement.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));

		EquipementResponse response = agentSelfController.self(authentication);

		assertThat(response.getId()).isEqualTo(equipement.getId());
		assertThat(response.getNom()).isEqualTo("Switch-Coeur-CRI");
		assertThat(response.getAdresseIp()).isEqualTo("10.0.0.1");
		assertThat(response.getSnmpCommunity()).isEqualTo("public");
		assertThat(response.getSnmpPort()).isEqualTo(161);
		assertThat(response.getInterfaceIndex()).isEqualTo(1);
	}

	@Test
	void ne_renvoie_jamais_la_cle_api_dans_sa_propre_reponse() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Switch-Coeur-CRI");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.SWITCH);
		equipement.setCleApi("cle-secrete");
		when(equipementService.findById(equipement.getId())).thenReturn(equipement);

		Authentication authentication = new UsernamePasswordAuthenticationToken(
				equipement.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));

		EquipementResponse response = agentSelfController.self(authentication);

		assertThat(response.getCleApi()).isNull();
	}
}
