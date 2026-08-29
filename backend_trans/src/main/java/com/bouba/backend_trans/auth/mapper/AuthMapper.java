package com.bouba.backend_trans.auth.mapper;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.dto.RegisterRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.UserType;

@Component
public class AuthMapper {

	public AppUser toEntity(RegisterRequest request, String encodedPassword) {
		AppUser user = new AppUser();
		user.setUsername(request.getUsername().trim());
		user.setEmail(request.getEmail().trim().toLowerCase());
		user.setPasswordHash(encodedPassword);
		// Rôle laissé à null : AppUser.onCreate() applique le moindre privilège
		// de la matrice RBAC (OBSERVATEUR), cf. issue #8.
		user.setUserType(request.getUserType() == null ? UserType.INDIVIDUAL : request.getUserType());
		return user;
	}

	public AuthResponse toResponse(AppUser user, String message) {
		AuthResponse response = new AuthResponse();
		response.setId(user.getId());
		response.setUsername(user.getUsername());
		response.setEmail(user.getEmail());
		response.setRole(user.getRole());
		response.setUserType(user.getUserType());
		response.setTokenType("Bearer");
		response.setMessage(message);
		return response;
	}
}
