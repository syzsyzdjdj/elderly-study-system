package com.elderly.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record HandleExchangeDTO(@NotNull @Positive Long id,
                                @NotNull @Pattern(regexp = "DONE|CANCEL") String action) {}
