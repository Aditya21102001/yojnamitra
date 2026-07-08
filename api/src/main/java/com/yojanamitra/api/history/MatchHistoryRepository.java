package com.yojanamitra.api.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

    List<MatchHistory> findTop20ByOwnerOrderByCreatedAtDesc(String owner);
}
