package com.code.repository;

import com.code.entity.MrpRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MrpRequirementRepository extends JpaRepository<MrpRequirement, Long> {
}

