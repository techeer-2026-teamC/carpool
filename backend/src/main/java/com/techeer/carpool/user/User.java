package com.techeer.carpool.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
public class SiteUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long member_id;
    //email password name phone profile_image is_driver rating

    @Column(unique = true)
    private String username;

    private String password;

    @Column(unique = true)
    private String email;
}
