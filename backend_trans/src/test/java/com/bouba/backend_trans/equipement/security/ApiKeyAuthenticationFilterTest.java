package com.bouba.backend_trans.equipement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;

import jakarta.servlet.FilterChain;

class ApiKeyAuthenticationFilterTest {

	private static final String CLE_API = "cle-api-de-test";

	private final EquipementRepository equipementRepository = mock(EquipementRepository.class);
	private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(equipementRepository);

	@BeforeEach
	@AfterEach
	void nettoyerLeContexteDeSecurite() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authentifie_la_requete_pour_une_cle_api_valide_d_un_equipement_actif() throws Exception {
		Equipement equipement = equipement(EtatEquipement.ACTIF);
		when(equipementRepository.findByCleApi(CLE_API)).thenReturn(Optional.of(equipement));

		MockHttpServletRequest request = requeteIngestion();
		request.addHeader("X-API-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(equipement.getId());
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_AGENT");
	}

	@Test
	void authentifie_egalement_un_equipement_en_maintenance() throws Exception {
		Equipement equipement = equipement(EtatEquipement.EN_MAINTENANCE);
		when(equipementRepository.findByCleApi(CLE_API)).thenReturn(Optional.of(equipement));

		MockHttpServletRequest request = requeteIngestion();
		request.addHeader("X-API-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	@Test
	void rejette_une_cle_api_inconnue() throws Exception {
		when(equipementRepository.findByCleApi("cle-inconnue")).thenReturn(Optional.empty());

		MockHttpServletRequest request = requeteIngestion();
		request.addHeader("X-API-Key", "cle-inconnue");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void rejette_une_cle_api_valide_dont_l_equipement_est_inactif() throws Exception {
		Equipement equipement = equipement(EtatEquipement.INACTIF);
		when(equipementRepository.findByCleApi(CLE_API)).thenReturn(Optional.of(equipement));

		MockHttpServletRequest request = requeteIngestion();
		request.addHeader("X-API-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void laisse_passer_une_requete_d_ingestion_sans_en_tete_de_cle_api() throws Exception {
		MockHttpServletRequest request = requeteIngestion();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		verify(equipementRepository, never()).findByCleApi(any());
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void authentifie_egalement_les_requetes_vers_l_auto_configuration_de_l_agent() throws Exception {
		Equipement equipement = equipement(EtatEquipement.ACTIF);
		when(equipementRepository.findByCleApi(CLE_API)).thenReturn(Optional.of(equipement));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/self");
		request.addHeader("X-API-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(equipement.getId());
	}

	@Test
	void n_applique_aucun_controle_de_cle_api_en_dehors_des_routes_d_ingestion() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/equipments");
		request.addHeader("X-API-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		verify(equipementRepository, never()).findByCleApi(any());
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private MockHttpServletRequest requeteIngestion() {
		return new MockHttpServletRequest("POST", "/api/v1/metrics/ingest");
	}

	private Equipement equipement(EtatEquipement etat) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur coeur");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.ROUTEUR);
		equipement.setEtat(etat);
		equipement.setCleApi(CLE_API);
		return equipement;
	}
}
