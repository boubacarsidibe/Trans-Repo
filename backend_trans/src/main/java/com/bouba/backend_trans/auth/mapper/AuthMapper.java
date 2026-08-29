package com.bouba.backend_trans.auth.mapper;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.entity.AppUser;

@Component
public class AuthMapper {

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
