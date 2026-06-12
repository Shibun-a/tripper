package io.github.shibuna.tripsmith.verification;

public enum PlanIssueCategory {
    DATE_GAP,
    DATE_OUT_OF_RANGE,
    DUPLICATE_DATE,
    MISSING_LOCATION,
    ROUTE_TOO_LONG,
    ROUTE_ESTIMATE_UNAVAILABLE,
    BUDGET_EXCEEDED,
    INVALID_LINK,
    MISSING_STAY,
    /** The verification request itself is unusable (e.g. non-positive budget) — not a plan defect. */
    INVALID_INPUT
}
