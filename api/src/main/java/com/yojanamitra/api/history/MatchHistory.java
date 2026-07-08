package com.yojanamitra.api.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One saved record of a match request, so we can show recent activity. */
@Entity
@Table(name = "match_history")
public class MatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username this match belongs to, or null for an anonymous match. */
    private String owner;

    @Column(length = 500)
    private String querySummary;

    private int schemeCount;

    private String topSchemeId;

    private Instant createdAt = Instant.now();

    protected MatchHistory() {
    }

    public MatchHistory(String owner, String querySummary, int schemeCount, String topSchemeId) {
        this.owner = owner;
        this.querySummary = querySummary;
        this.schemeCount = schemeCount;
        this.topSchemeId = topSchemeId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getQuerySummary() {
        return querySummary;
    }

    public int getSchemeCount() {
        return schemeCount;
    }

    public String getTopSchemeId() {
        return topSchemeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
