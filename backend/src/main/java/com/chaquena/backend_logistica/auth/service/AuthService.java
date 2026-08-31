package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.dto.AuthResponseDto;
import com.chaquena.backend_logistica.auth.dto.LoginRequestDto;

public interface AuthService {

    AuthResponseDto autenticarConGoogle(String googleAccessToken);

    AuthResponseDto login(LoginRequestDto request);
}
