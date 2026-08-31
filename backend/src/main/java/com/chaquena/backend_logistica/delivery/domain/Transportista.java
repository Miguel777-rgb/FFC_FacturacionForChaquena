package com.chaquena.backend_logistica.delivery.domain;

import com.chaquena.backend_logistica.personas.domain.Persona;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transportistas")
@PrimaryKeyJoinColumn(name = "persona_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Transportista extends Persona {

    @Column(name = "empresa_transporte", nullable = false, length = 100)
    private String empresaTransporte;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** Un transportista dado de alta esta activo mientras no se diga lo contrario. */
    @PrePersist
    public void prePersistTransportista() {
        if (this.activo == null) this.activo = true;
    }

    @Builder.Default
    @OneToMany(mappedBy = "transportista", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Vehiculo> vehiculos = new ArrayList<>();
}