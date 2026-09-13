package com.studyhouse.spacz.service;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.AspirantUser;
import com.studyhouse.spacz.repository.AspirantUserRepository;

@Service
public class AspirantUserServiceImpl implements AspirantUserService {

    private final AspirantUserRepository aspirantUserRepository;

    @Autowired
    public AspirantUserServiceImpl(AspirantUserRepository aspirantUserRepository) {
        this.aspirantUserRepository = aspirantUserRepository;
    }

    @Override
    public List<AspirantUser> getAllAspirantUsers() {
        return aspirantUserRepository.findAll();
    }

    @Override
    public Optional<AspirantUser> getAspirantUserById(Long id) {
        return aspirantUserRepository.findById(id);
    }

    @Override
    public AspirantUser createAspirantUser(AspirantUser aspirantUser) {
        return aspirantUserRepository.save(aspirantUser);
    }

    @Override
    public AspirantUser updateAspirantUser(Long id, AspirantUser aspirantUser) {
        if (aspirantUserRepository.existsById(id)) {
            aspirantUser.setAspirantUserId(id);
            return aspirantUserRepository.save(aspirantUser);
        }
        return null; // or throw a custom exception
    }

    @Override
    public boolean deleteAspirantUser(Long id) {
        if (aspirantUserRepository.existsById(id)) {
            aspirantUserRepository.deleteById(id);
            return true;
        }
        return false;
    }
}