package in.rsh.cab.ratelimit;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {}
