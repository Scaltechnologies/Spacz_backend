package com.studyhouse.spacz.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long propertyId;

    private String propertyName;
    private String address;
    private String googleCoordinates;

    // Many-to-One relationship with owner (Owner)
    @ManyToOne
    @JoinColumn(name = "owner_id", referencedColumnName = "ownerId")
    private Owner owner;

    // One-to-Many relationship with Image (A property can have many images)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Image> images;
    
 // One-to-Many relationship with Block (A property can have many blocks)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Block> blocks;

    // Getters and setters
    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGoogleCoordinates() {
        return googleCoordinates;
    }

    public void setGoogleCoordinates(String googleCoordinates) {
        this.googleCoordinates = googleCoordinates;
    }

    public Owner getowner() {
        return owner;
    }

    public void setowner(Owner owner) {
        this.owner = owner;
    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }
    
    public List<Block> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<Block> blocks) {
        this.blocks = blocks;
    }
}
