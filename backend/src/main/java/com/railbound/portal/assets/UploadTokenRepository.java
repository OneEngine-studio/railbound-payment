package com.railbound.portal.assets;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadTokenRepository extends MongoRepository<UploadToken, String> {

    Optional<UploadToken> findByToken(String token);
}
