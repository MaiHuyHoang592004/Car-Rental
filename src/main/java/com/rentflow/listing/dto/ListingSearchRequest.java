package com.rentflow.listing.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ListingSearchRequest(
    String query,
    String city,
    List<VehicleCategory> categories,
    LocalDate pickupDate,
    LocalDate returnDate,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer seats,
    TransmissionType transmission,
    FuelType fuelType,
    Boolean instantBook,
    BigDecimal minRating,
    ListingSearchSort sort,
    Integer page,
    Integer size
) {
    public ListingSearchRequest {
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 20;
        if (size > 100) size = 100;
        if (sort == null) sort = ListingSearchSort.NEWEST;
    }
}
