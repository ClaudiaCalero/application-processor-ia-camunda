package com.claudia.tramitador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequest(@NotBlank String firstName, @Size(max=5000) String text ) {
}
