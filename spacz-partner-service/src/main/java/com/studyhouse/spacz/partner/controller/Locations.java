package com.studyhouse.spacz.partner.controller;

import java.net.URI;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

final class Locations {

    private Locations() {
    }

    /** URI of a newly created resource: the current request path + "/{id}". */
    static URI of(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
