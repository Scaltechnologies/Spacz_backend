package com.spacz.user.repository;

import com.spacz.user.entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserId(Long userId);

    Optional<UserDocument> findByUserIdAndDocumentType(Long userId, UserDocument.Type documentType);
}
