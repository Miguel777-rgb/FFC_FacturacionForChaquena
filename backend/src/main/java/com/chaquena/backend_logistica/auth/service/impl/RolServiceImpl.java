package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.dto.RolResponseDto;
import com.chaquena.backend_logistica.auth.repository.RolRepository;
import com.chaquena.backend_logistica.auth.service.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RolResponseDto> listarTodos() {
        return rolRepository.findAll(Sort.by("nombre")).stream()
                .map(RolResponseDto::fromEntity)
                .toList();
    }
}
