package com.finops.domain.user.repository;

import com.finops.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTenantIdAndEmail(Long tenantId, String email);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);
}
