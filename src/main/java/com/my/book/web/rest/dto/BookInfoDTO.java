package com.my.book.web.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
public class BookInfoDTO implements Serializable {
    private Long id;
    private String title;

}
