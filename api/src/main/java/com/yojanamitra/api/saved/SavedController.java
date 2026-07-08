package com.yojanamitra.api.saved;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bookmarked schemes for the logged-in user. All endpoints require authentication. */
@RestController
@RequestMapping("/api/saved")
public class SavedController {

    private final SavedSchemeRepository repo;

    public SavedController(SavedSchemeRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<SavedScheme> list(Authentication auth) {
        return repo.findByOwnerOrderByCreatedAtDesc(auth.getName());
    }

    @PostMapping
    public SavedScheme save(@Valid @RequestBody SaveSchemeRequest req, Authentication auth) {
        String owner = auth.getName();
        // Idempotent: saving an already-saved scheme just returns the existing row.
        return repo.findByOwnerAndSchemeId(owner, req.schemeId())
                .orElseGet(() -> repo.save(new SavedScheme(
                        owner, req.schemeId(), req.name(), req.category(),
                        req.verdict(), req.reason(), req.applyUrl())));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication auth) {
        repo.findByIdAndOwner(id, auth.getName()).ifPresent(repo::delete);
    }
}
