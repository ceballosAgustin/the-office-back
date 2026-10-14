package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Role;
import com.aceballos.the_office_back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(Role role);
}
