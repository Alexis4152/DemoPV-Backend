package com.boutique.pos.repository;

import com.boutique.pos.model.MailConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailConfigRepository extends JpaRepository<MailConfig, Long> {
}
