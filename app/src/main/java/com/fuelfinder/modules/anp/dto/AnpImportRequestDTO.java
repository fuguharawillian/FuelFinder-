package com.fuelfinder.modules.anp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnpImportRequestDTO(
        @NotBlank(message = "A URL de origem é obrigatória")
        @Size(max = 500, message = "A URL de origem aceita no máximo 500 caracteres")
        String sourceUrl,

        @NotBlank(message = "O período de referência é obrigatório")
        @Size(max = 20, message = "O período de referência aceita no máximo 20 caracteres")
        String referencePeriod) {
}
