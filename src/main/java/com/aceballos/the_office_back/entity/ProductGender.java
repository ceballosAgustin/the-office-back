package com.aceballos.the_office_back.entity;

public enum ProductGender {
    MEN,
    WOMEN,
    UNISEX;

    public static ProductGender fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return ProductGender.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Género no válido: " + value + ". Valores admitidos: MEN, WOMEN, UNISEX");
        }
    }
}

