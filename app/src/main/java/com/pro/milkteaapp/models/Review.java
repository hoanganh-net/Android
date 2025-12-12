package com.pro.milkteaapp.models;

public class Review {
    private String name;
    private final String comment;
    private final String avatarUrl; // có thể là URL hoặc tên drawable
    private final double rating;

    public Review(String name, String comment, String avatarUrl, double rating) {
        this.name = name;
        this.comment = comment;
        this.avatarUrl = avatarUrl;
        this.rating = rating;
    }

    public String getName() { return name; }
    public String getComment() { return comment; }
    public String getAvatarUrl() { return avatarUrl; }
    public double getRating() { return rating; }

    // Thêm setter để enrich từ hồ sơ users/{uid}
    public void setName(String name) { this.name = name; }
}
