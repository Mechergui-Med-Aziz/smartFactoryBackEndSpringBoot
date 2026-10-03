package com.smartfactory.repository;

import com.smartfactory.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends MongoRepository<Group, String> {

    Optional<Group> findByName(String name);

    boolean existsByName(String name);

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Page<Group> searchGroups(String query, Pageable pageable);

    List<Group> findByOperatorsContaining(String userId);

    List<Group> findBySupervisorId(String supervisorId);
}
