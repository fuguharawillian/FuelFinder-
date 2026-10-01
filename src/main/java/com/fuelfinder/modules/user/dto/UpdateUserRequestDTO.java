package com.fuelfinder.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequestDTO(
        @Size(min = 2, max = 255, message = "O nome deve conter entre 2 e 255 caracteres")
        String fullName,

        @Email(message = "E-mail inválido")
        @Size(max = 255)
        String email) {
}
