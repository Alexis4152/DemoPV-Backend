package com.boutique.pos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CashCutScheduleRequest {
    @NotNull @Min(0) @Max(23)
    private Integer closeHour;
    @NotNull @Min(0) @Max(59)
    private Integer closeMinute;
    @NotNull
    private Boolean enabled;
}
