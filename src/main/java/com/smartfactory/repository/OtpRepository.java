package com.smartfactory.repository;

import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.OtpVerification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtpRepository extends MongoRepository<OtpVerification, String> {

    Optional<OtpVerification> findTopByEmailAndTypeOrderByCreatedAtDesc(String email, OtpType type);

    Optional<OtpVerification> findByEmailAndResetToken(String email, String resetToken);

    List<OtpVerification> findByEmailAndType(String email, OtpType type);

    void deleteByEmail(String email);
}
