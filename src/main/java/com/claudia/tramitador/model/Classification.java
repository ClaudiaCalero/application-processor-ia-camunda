package com.claudia.tramitador.model;

import com.claudia.tramitador.enums.RequestCategory;

public record Classification(RequestCategory category,
                             Double amount,
                             String summary,
                             double score) {
}
