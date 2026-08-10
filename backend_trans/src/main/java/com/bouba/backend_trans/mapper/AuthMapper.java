package com.bouba.backend_trans.mapper;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.dto.auth.AuthResponse;
import com.bouba.backend_trans.dto.auth.RegisterRequest;
import com.bouba.backend_trans.entity.AppUser;
import com.bouba.backend_trans.entity.enums.Role;
import com.bouba.backend_trans.entity.enums.UserType;

@Component
public class AuthMapper {

	public AppUser toEntity(RegisterRequest request, String encodedPassword) {
		AppUser user = new AppUser();
		user.setUsername(request.getUsername().trim());
		user.setEmail(request.getEmail().trim().toLowerCase());
		user.setPasswordHash(encodedPassword);
		user.setRole(request.getRole() == null ? Role.CLIENT : request.getRole());
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
