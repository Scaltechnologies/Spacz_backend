package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.AspirantUser;

public interface AspirantUserService {

    List<AspirantUser> getAllAspirantUsers();

    Optional<AspirantUser> getAspirantUserById(Long id);

    AspirantUser createAspirantUser(AspirantUser aspirantUser);

    AspirantUser updateAspirantUser(Long id, AspirantUser aspirantUser);

    boolean deleteAspirantUser(Long id);
}