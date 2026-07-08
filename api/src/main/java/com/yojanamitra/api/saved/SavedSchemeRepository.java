package com.yojanamitra.api.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSchemeRepository extends JpaRepository<SavedScheme, Long> {

    List<SavedScheme> findByOwnerOrderByCreatedAtDesc(String owner);

    Optional<SavedScheme> findByOwnerAndSchemeId(String owner, String schemeId);

    Optional<SavedScheme> findByIdAndOwner(Long id, String owner);
}
