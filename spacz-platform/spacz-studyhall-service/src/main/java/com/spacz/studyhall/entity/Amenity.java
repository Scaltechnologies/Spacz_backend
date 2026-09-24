package com.spacz.studyhall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Global amenity catalog (Wi-Fi, AC, lockers ...), managed by admins.
 */
@Entity
@Table(name = "amenities")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Amenity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 60)
    private String icon;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active;

    public static Amenity create(String code, String name, String icon, String description) {
        Amenity amenity = new Amenity();
        amenity.code = code;
        amenity.name = name;
        amenity.icon = icon;
        amenity.description = description;
        amenity.active = true;
        return amenity;
    }
}
