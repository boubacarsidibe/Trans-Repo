package com.bouba.backend_trans.auth.dto;

import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserCreateRequest {

	@NotBlank
	@Size(min = 2, max = 100)
	private String username;

	@NotBlank
	@Email
	private String email;

	@NotBlank
	@Size(min = 8, max = 128)
	private String password;

	@NotNull
	private Role role;

	private UserType userType;

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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
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
}
