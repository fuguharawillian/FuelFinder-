package com.fuelfinder.modules.user.dto;

import com.fuelfinder.modules.user.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequestDTO(
        @NotNull(message = "O status da conta é obrigatório")
        AccountStatus status) {
}
