"""Pydantic request/response models for the YojanaMitra GenAI service."""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field


class Profile(BaseModel):
    """A citizen's self-described situation used for scheme matching."""

    age: Optional[int] = Field(None, ge=0, le=120)
    gender: Optional[Literal["male", "female", "other"]] = None
    state: Optional[str] = None
    occupation: Optional[str] = None
    annual_income: Optional[int] = Field(None, ge=0, description="Annual household income in INR")
    category: Optional[str] = Field(None, description="e.g. General, SC, ST, OBC, Minority")
    description: Optional[str] = Field(None, description="Free-text: anything else about the situation")

    def to_query(self) -> str:
        """Flatten the profile into a single natural-language string for retrieval."""
        parts: list[str] = []
        if self.age is not None:
            parts.append(f"{self.age} years old")
        if self.gender:
            parts.append(self.gender)
        if self.category:
            parts.append(f"{self.category} category")
        if self.occupation:
            parts.append(self.occupation)
        if self.state:
            parts.append(f"from {self.state}")
        if self.annual_income is not None:
            parts.append(f"annual household income Rs {self.annual_income}")
        if self.description:
            parts.append(self.description)
        return ", ".join(parts) if parts else "Indian citizen looking for welfare schemes"


class MatchRequest(BaseModel):
    profile: Profile
    top_k: int = Field(5, ge=1, le=12)
    lang: Literal["en", "hi"] = "en"


class MatchedScheme(BaseModel):
    id: str
    name: str
    ministry: str
    category: str
    description: str
    benefits: str
    apply_url: str
    verdict: Literal["eligible", "maybe", "not_eligible"]
    reason: str
    how_to_apply: str
    score: float = Field(..., description="Retrieval similarity 0-1 (higher = closer)")


class MatchResponse(BaseModel):
    query: str
    count: int
    schemes: list[MatchedScheme]


class ChatRequest(BaseModel):
    scheme_id: str
    question: str = Field(..., min_length=1)
    lang: Literal["en", "hi"] = "en"


class ChatResponse(BaseModel):
    scheme_id: str
    answer: str
