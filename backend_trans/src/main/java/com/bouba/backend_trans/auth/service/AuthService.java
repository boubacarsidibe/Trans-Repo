package com.bouba.backend_trans.auth.service;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.dto.ForgotPasswordRequest;
import com.bouba.backend_trans.auth.dto.LoginRequest;
import com.bouba.backend_trans.auth.dto.RefreshRequest;
import com.bouba.backend_trans.auth.dto.RegisterRequest;
import com.bouba.backend_trans.auth.dto.ResetPasswordRequest;

public interface AuthService {

	AuthResponse register(RegisterRequest request);

	AuthResponse login(LoginRequest request);

	AuthResponse refresh(RefreshRequest request);

	void forgotPassword(ForgotPasswordRequest request);

	void resetPassword(ResetPasswordRequest request);
}
