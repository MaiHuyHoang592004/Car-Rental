package com.rentflow.support.dto;

import jakarta.validation.constraints.Size;

public record CloseSupportCaseRequest(@Size(max = 5000) String note) {
}
