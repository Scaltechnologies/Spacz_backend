package com.studyhouse.spacz.service;

import com.studyhouse.spacz.entity.Image;

import java.util.List;
import java.util.Optional;

public interface ImageService {

    List<Image> getAllImages();

    Optional<Image> getImageById(Long id);

    Image createImage(Image image);

    Image updateImage(Long id, Image image);

    boolean deleteImage(Long id);
}