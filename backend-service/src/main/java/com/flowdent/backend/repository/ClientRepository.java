package com.flowdent.backend.repository;

import com.flowdent.backend.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUserId(Long userId);
    Optional<Client> findFirstByEmailIgnoreCase(String email);
    Optional<Client> findFirstByPhone(String phone);
    List<Client> findAllByOrderByNameAsc();
}
