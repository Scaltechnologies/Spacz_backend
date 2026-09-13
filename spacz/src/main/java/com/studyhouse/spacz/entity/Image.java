package com.studyhouse.spacz.entity;

import jakarta.persistence.*;

@Entity
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;   // Unique image ID

    private String imageUrl; // URL to the image

    // Many-to-One relationship with Property (multiple images can belong to one property)
    @ManyToOne
    @JoinColumn(name = "property_id", referencedColumnName = "propertyId")
    private Property property;

    // Getters and setters
    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }
}
