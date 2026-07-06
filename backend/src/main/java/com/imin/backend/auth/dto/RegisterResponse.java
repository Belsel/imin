package com.imin.backend.auth.dto;

/**
 * Returned from {@code POST /api/auth/register}. LOCAL registration creates
 * the account and sends a verification email but does not log the user in —
 * no JWT is issued until the account is verified, so this intentionally has
 * a different shape than {@link AuthResponse}.
 */
public record RegisterResponse(String email, boolean emailVerified, String message) {

    public static RegisterResponse pendingVerification(String email) {
        return new RegisterResponse(email, false, "Registered. Check your email to verify your account before logging in.");
    }
}
