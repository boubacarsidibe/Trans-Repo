package com.bouba.backend_trans.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	/** Destinataires possibles d'une notification d'alerte (F7). */
	List<AppUser> findByActiveTrueAndRoleIn(Collection<Role> roles);
}
