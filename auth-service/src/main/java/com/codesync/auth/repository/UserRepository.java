package com.codesync.auth.repository;

import com.codesync.auth.entity.User;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findAllByRole(Role role);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    // Search users by username containing keyword (case-insensitive)
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchByUsername(@Param("keyword") String keyword);

    void deleteByUserId(Long userId);
}