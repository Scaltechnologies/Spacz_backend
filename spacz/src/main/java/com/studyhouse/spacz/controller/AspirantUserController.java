package com.studyhouse.spacz.controller;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.entity.AspirantUser;
import com.studyhouse.spacz.service.AspirantUserService;

@RestController
@RequestMapping("/aspirant-users")
public class AspirantUserController {

    private final AspirantUserService aspirantUserService;

    @Autowired
    public AspirantUserController(AspirantUserService aspirantUserService) {
        this.aspirantUserService = aspirantUserService;
    }

    // Get all aspirant users
    @GetMapping
    public ResponseEntity<List<AspirantUser>> getAllAspirantUsers() {
        List<AspirantUser> aspirantUsers = aspirantUserService.getAllAspirantUsers();
        return new ResponseEntity<>(aspirantUsers, HttpStatus.OK);
    }

    // Get a single aspirant user by ID
    @GetMapping("/{id}")
    public ResponseEntity<AspirantUser> getAspirantUserById(@PathVariable("id") Long id) {
        Optional<AspirantUser> aspirantUser = aspirantUserService.getAspirantUserById(id);
        return aspirantUser.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Create a new aspirant user
    @PostMapping
    public ResponseEntity<AspirantUser> createAspirantUser(@RequestBody AspirantUser aspirantUser) {
        AspirantUser savedAspirantUser = aspirantUserService.createAspirantUser(aspirantUser);
        return new ResponseEntity<>(savedAspirantUser, HttpStatus.CREATED);
    }

    // Update an existing aspirant user
    @PutMapping("/{id}")
    public ResponseEntity<AspirantUser> updateAspirantUser(@PathVariable("id") Long id, @RequestBody AspirantUser aspirantUser) {
        AspirantUser updatedAspirantUser = aspirantUserService.updateAspirantUser(id, aspirantUser);
        return updatedAspirantUser != null ? new ResponseEntity<>(updatedAspirantUser, HttpStatus.OK) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // Delete an aspirant user by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAspirantUser(@PathVariable("id") Long id) {
        if (aspirantUserService.deleteAspirantUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}