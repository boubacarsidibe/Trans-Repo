package com.bouba.backend_trans.rapport.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.rapport.entity.Rapport;

public interface RapportRepository extends JpaRepository<Rapport, UUID> {
}
