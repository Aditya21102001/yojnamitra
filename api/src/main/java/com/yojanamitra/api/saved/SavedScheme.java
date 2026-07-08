package com.yojanamitra.api.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** A scheme a user has bookmarked from their match results. */
@Entity
@Table(name = "saved_scheme",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "schemeId"}))
public class SavedScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String schemeId;

    private String name;
    private String category;
    private String verdict;

    @Column(length = 1000)
    private String reason;

    private String applyUrl;

    private Instant createdAt = Instant.now();

    protected SavedScheme() {
    }

    public SavedScheme(String owner, String schemeId, String name, String category,
                       String verdict, String reason, String applyUrl) {
        this.owner = owner;
        this.schemeId = schemeId;
        this.name = name;
        this.category = category;
        this.verdict = verdict;
        this.reason = reason;
        this.applyUrl = applyUrl;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getSchemeId() {
        return schemeId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getReason() {
        return reason;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
