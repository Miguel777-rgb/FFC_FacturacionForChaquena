package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.dto.RolResponseDto;

import java.util.List;

public interface RolService {

    List<RolResponseDto> listarTodos();
}
