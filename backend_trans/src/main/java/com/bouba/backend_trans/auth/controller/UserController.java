package com.bouba.backend_trans.auth.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.auth.dto.UserCreateRequest;
import com.bouba.backend_trans.auth.dto.UserResponse;
import com.bouba.backend_trans.auth.dto.UserUpdateRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATEUR')")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.findAll().stream()
				.map(UserResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
		AppUser created = userService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(created));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
		return UserResponse.fromEntity(userService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deactivate(@PathVariable Long id) {
		userService.deactivate(id);
		return ResponseEntity.noContent().build();
	}
}
