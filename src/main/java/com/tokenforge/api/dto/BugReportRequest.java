package com.tokenforge.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BugReportRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 200, message = "O título excede o tamanho máximo de 200 caracteres")
        String title,

        @Size(max = 5000, message = "O módulo excede o tamanho máximo de 5000 caracteres")
        String module,

        @Size(max = 5000, message = "A severidade excede o tamanho máximo de 5000 caracteres")
        String severity,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 5000, message = "A descrição excede o tamanho máximo de 5000 caracteres")
        String description,

        @Size(max = 5000, message = "Os passos para reproduzir excedem o tamanho máximo de 5000 caracteres")
        String steps,

        @Size(max = 100, message = "A data do ocorrido excede o tamanho máximo permitido")
        String occurredAt,

        @Email(message = "O e-mail informado é inválido")
        String reporterEmail
) {}
