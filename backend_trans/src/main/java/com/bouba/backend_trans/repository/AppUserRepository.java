package com.bouba.backend_trans.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);
}
