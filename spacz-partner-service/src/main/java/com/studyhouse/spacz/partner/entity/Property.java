package com.studyhouse.spacz.partner.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "property")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "property_name")
    private String propertyName;

    @Column(name = "address")
    private String address;

    @Column(name = "google_coordinates")
    private String googleCoordinates;

    // Many-to-One relationship with Owner (multiple properties can belong to one owner)
    @ManyToOne
    @JoinColumn(name = "owner_id", referencedColumnName = "owner_id")
    private Owner owner;

    // One-to-Many relationship with Image (a property can have many images)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("imageId ASC")
    private List<Image> images = new ArrayList<>();

    // One-to-Many relationship with Block (a property can have many blocks)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("blockId ASC")
    private List<Block> blocks = new ArrayList<>();

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

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
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
