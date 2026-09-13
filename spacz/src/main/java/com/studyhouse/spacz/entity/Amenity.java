package com.studyhouse.spacz.entity;

import jakarta.persistence.*;

@Entity
public class Amenity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long amenityId; // Unique identifier for the amenity

    private boolean ac;        // AC available in the block
    private boolean wifi;      // Wi-Fi available in the block
    private boolean water;     // Water available in the block
    private boolean lockers;   // Lockers available in the block
    private boolean newspapers; // Newspapers available in the block

    // One-to-One relationship with Block (one block can have only one amenity)
    @OneToOne
    @JoinColumn(name = "block_id", referencedColumnName = "blockId")
    private Block block;

    // Getters and setters
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
