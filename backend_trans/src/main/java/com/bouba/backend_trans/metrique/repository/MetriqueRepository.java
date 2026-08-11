package com.bouba.backend_trans.metrique.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.metrique.entity.Metrique;

public interface MetriqueRepository extends JpaRepository<Metrique, Long> {

	List<Metrique> findByEquipementIdOrderByHorodatageDesc(UUID equipementId);
}
