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
@Table(name = "block")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long blockId;

    @Column(name = "block_name")
    private String blockName; // Name of the block (e.g., "Block A", "Block 1")

    // Many-to-One relationship with Property (multiple blocks can belong to one property)
    @ManyToOne
    @JoinColumn(name = "property_id", referencedColumnName = "property_id")
    private Property property;

    // One-to-Many relationship with Seat (a block can have many seats)
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("seatId ASC")
    private List<Seat> seats = new ArrayList<>();

    @Column(name = "block_daily_price", nullable = false)
    private double blockDailyPrice;

    @Column(name = "block_monthly_price", nullable = false)
    private double blockMonthlyPrice;

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    public double getBlockDailyPrice() {
        return blockDailyPrice;
    }

    public void setBlockDailyPrice(double blockDailyPrice) {
        this.blockDailyPrice = blockDailyPrice;
    }

    public double getBlockMonthlyPrice() {
        return blockMonthlyPrice;
    }

    public void setBlockMonthlyPrice(double blockMonthlyPrice) {
        this.blockMonthlyPrice = blockMonthlyPrice;
    }
}
