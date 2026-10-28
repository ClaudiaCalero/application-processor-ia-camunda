package com.claudia.tramitador.repository;

import com.claudia.tramitador.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {
}
