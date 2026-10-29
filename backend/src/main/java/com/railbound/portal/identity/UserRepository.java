package com.railbound.portal.identity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByNakamaUserId(String nakamaUserId);
    boolean existsByNakamaUserId(String nakamaUserId);
}
