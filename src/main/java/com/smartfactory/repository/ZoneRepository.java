package com.smartfactory.repository;

import com.smartfactory.entity.Zone;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneRepository extends MongoRepository<Zone, String> {

    Optional<Zone> findByName(String name);

    boolean existsByName(String name);
}
