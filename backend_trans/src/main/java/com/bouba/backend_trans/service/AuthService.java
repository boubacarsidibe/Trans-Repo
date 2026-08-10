package com.bouba.backend_trans.service;

import com.bouba.backend_trans.dto.auth.AuthResponse;
import com.bouba.backend_trans.dto.auth.LoginRequest;
import com.bouba.backend_trans.dto.auth.RegisterRequest;

public interface AuthService {

	AuthResponse register(RegisterRequest request);

	AuthResponse login(LoginRequest request);
}
