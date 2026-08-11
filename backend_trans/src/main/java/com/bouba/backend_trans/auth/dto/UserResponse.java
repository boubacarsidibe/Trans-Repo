package com.bouba.backend_trans.auth.dto;

import java.time.LocalDateTime;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;

public class UserResponse {

	private Long id;
	private String username;
	private String email;
	private Role role;
	private UserType userType;
	private boolean active;
	private LocalDateTime createdAt;

	public static UserResponse fromEntity(AppUser user) {
		UserResponse response = new UserResponse();
		response.id = user.getId();
		response.username = user.getUsername();
		response.email = user.getEmail();
		response.role = user.getRole();
		response.userType = user.getUserType();
		response.active = user.isActive();
		response.createdAt = user.getCreatedAt();
		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public UserType getUserType() {
		return userType;
	}

	public void setUserType(UserType userType) {
		this.userType = userType;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
