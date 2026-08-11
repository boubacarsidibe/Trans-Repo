package com.bouba.backend_trans.equipement.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-API-Key";
	private static final String METRICS_PATH_PREFIX = "/api/v1/metrics/";

	private final EquipementRepository equipementRepository;

	public ApiKeyAuthenticationFilter(EquipementRepository equipementRepository) {
		this.equipementRepository = equipementRepository;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(METRICS_PATH_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String apiKey = request.getHeader(API_KEY_HEADER);
		if (apiKey != null && !apiKey.isBlank()) {
			equipementRepository.findByCleApi(apiKey)
					.filter(equipement -> equipement.getEtat() != EtatEquipement.INACTIF)
					.ifPresent(equipement -> {
						UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
								equipement.getId(),
								null,
								List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
						authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authToken);
					});
		}

		filterChain.doFilter(request, response);
	}
}
