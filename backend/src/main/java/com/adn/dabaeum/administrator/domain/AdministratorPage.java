package com.adn.dabaeum.administrator.domain;

import java.util.List;

public record AdministratorPage(List<Administrator> administrators, long totalElements) {

    public AdministratorPage {
        administrators = List.copyOf(administrators);
    }
}
