package com.studyhouse.spacz.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "amenity")
public class Amenity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "amenity_id")
    private Long amenityId;

    @Column(name = "ac", nullable = false)
    private boolean ac;

    @Column(name = "wifi", nullable = false)
    private boolean wifi;

    @Column(name = "water", nullable = false)
    private boolean water;

    @Column(name = "lockers", nullable = false)
    private boolean lockers;

    @Column(name = "newspapers", nullable = false)
    private boolean newspapers;

    // One-to-One relationship with Block (one block can have only one amenity record)
    @OneToOne
    @JoinColumn(name = "block_id", referencedColumnName = "block_id")
    private Block block;

    public Long getAmenityId() {
        return amenityId;
    }

    public void setAmenityId(Long amenityId) {
        this.amenityId = amenityId;
    }

    public boolean isAc() {
        return ac;
    }

    public void setAc(boolean ac) {
        this.ac = ac;
    }

    public boolean isWifi() {
        return wifi;
    }

    public void setWifi(boolean wifi) {
        this.wifi = wifi;
    }

    public boolean isWater() {
        return water;
    }

    public void setWater(boolean water) {
        this.water = water;
    }

    public boolean isLockers() {
        return lockers;
    }

    public void setLockers(boolean lockers) {
        this.lockers = lockers;
    }

    public boolean isNewspapers() {
        return newspapers;
    }

    public void setNewspapers(boolean newspapers) {
        this.newspapers = newspapers;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }
}
