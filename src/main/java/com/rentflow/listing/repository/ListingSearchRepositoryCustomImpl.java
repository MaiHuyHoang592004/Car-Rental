package com.rentflow.listing.repository;

import com.rentflow.listing.dto.ListingSearchCriteria;
import com.rentflow.listing.dto.ListingSearchResponse;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
@RequiredArgsConstructor
public class ListingSearchRepositoryCustomImpl implements ListingSearchRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Page<ListingSearchResponse> search(ListingSearchCriteria criteria, Pageable pageable) {
        if (criteria.pickupDate() != null && criteria.returnDate() != null) {
            return searchWithDates(criteria, pageable);
        }
        return searchWithoutDates(criteria, pageable);
    }

    private Page<ListingSearchResponse> searchWithoutDates(ListingSearchCriteria criteria, Pageable pageable) {
        String baseSelect = """
            SELECT l.id, l.title, l.city, v.category, l.base_price_per_day,
                l.currency, v.seats, v.transmission, v.fuel_type, l.average_rating
            FROM listings l
            JOIN vehicles v ON l.vehicle_id = v.id
            WHERE l.status = 'ACTIVE'
            """;

        StringBuilder dynamic = buildDynamicFilters(criteria);
        String orderBy = orderBy(criteria);
        String dataSql = baseSelect + dynamic + orderBy;
        String countSql = "SELECT COUNT(*) FROM (" + baseSelect + dynamic + ") AS t";

        Query dataQuery = em.createNativeQuery(dataSql);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        bindCommonParams(dataQuery, criteria, false);

        Query countQuery = em.createNativeQuery(countSql);
        bindCommonParams(countQuery, criteria, false);

        List<?> data = dataQuery.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        return new PageImpl<>(toSearchResponses(data), pageable, total.longValue());
    }

    private Page<ListingSearchResponse> searchWithDates(ListingSearchCriteria criteria, Pageable pageable) {
        String baseSelect = """
            SELECT l.id, l.title, l.city, v.category, l.base_price_per_day,
                l.currency, v.seats, v.transmission, v.fuel_type, l.average_rating
            FROM listings l
            JOIN vehicles v ON l.vehicle_id = v.id
            WHERE l.status = 'ACTIVE'
            """;

        String availabilityFilter = """
            AND NOT EXISTS (
                SELECT 1 FROM generate_series(
                    CAST(:pickupDate AS date),
                    (CAST(:returnDate AS date) - INTERVAL '1 day'),
                    '1 day'::interval
                ) AS ds(d)
                WHERE NOT EXISTS (
                    SELECT 1 FROM availability_calendar ac
                    WHERE ac.listing_id = l.id AND ac.available_date = ds.d::date
                )
            )
            AND NOT EXISTS (
                SELECT 1 FROM availability_calendar ac
                WHERE ac.listing_id = l.id
                  AND ac.available_date >= :pickupDate
                  AND ac.available_date < :returnDate
                  AND ac.status IN ('HOLD', 'BOOKED', 'BLOCKED')
            )
            """;

        StringBuilder dynamic = buildDynamicFilters(criteria);
        String orderBy = orderBy(criteria);
        String dataSql = baseSelect + availabilityFilter + dynamic + orderBy;
        String countSql = "SELECT COUNT(*) FROM (" + baseSelect + availabilityFilter + dynamic + ") AS t";

        Query dataQuery = em.createNativeQuery(dataSql);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        bindCommonParams(dataQuery, criteria, true);

        Query countQuery = em.createNativeQuery(countSql);
        bindCommonParams(countQuery, criteria, true);

        List<?> data = dataQuery.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        return new PageImpl<>(toSearchResponses(data), pageable, total.longValue());
    }

    private List<ListingSearchResponse> toSearchResponses(List<?> rows) {
        return rows.stream()
            .map(row -> {
                Object[] columns = (Object[]) row;
                return new ListingSearchResponse(
                    (UUID) columns[0],
                    (String) columns[1],
                    (String) columns[2],
                    enumValue(VehicleCategory.class, columns[3]),
                    (BigDecimal) columns[4],
                    (String) columns[5],
                    ((Number) columns[6]).intValue(),
                    enumValue(TransmissionType.class, columns[7]),
                    enumValue(FuelType.class, columns[8]),
                    null,
                    (BigDecimal) columns[9]
                );
            })
            .toList();
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, Object value) {
        return value == null ? null : Enum.valueOf(enumType, value.toString());
    }

    private StringBuilder buildDynamicFilters(ListingSearchCriteria criteria) {
        StringBuilder dynamic = new StringBuilder();
        if (criteria.query() != null) {
            dynamic.append("""
                 AND (
                    l.title ILIKE '%' || :query || '%'
                    OR v.make ILIKE '%' || :query || '%'
                    OR v.model ILIKE '%' || :query || '%'
                 )
                """);
        }
        if (criteria.city() != null) {
            dynamic.append(" AND l.city ILIKE :city || '%'");
        }
        if (criteria.categories() != null && !criteria.categories().isEmpty()) {
            String inClause = IntStream.range(0, criteria.categories().size())
                .mapToObj(i -> ":cat" + i)
                .collect(Collectors.joining(", ", " AND v.category IN (", ")"));
            dynamic.append(inClause);
        }
        if (criteria.minPrice() != null) {
            dynamic.append(" AND l.base_price_per_day >= :minPrice");
        }
        if (criteria.maxPrice() != null) {
            dynamic.append(" AND l.base_price_per_day <= :maxPrice");
        }
        if (criteria.seats() != null) {
            dynamic.append(" AND v.seats >= :seats");
        }
        if (criteria.transmission() != null) {
            dynamic.append(" AND v.transmission = :transmission");
        }
        if (criteria.fuelType() != null) {
            dynamic.append(" AND v.fuel_type = :fuelType");
        }
        if (criteria.instantBook() != null) {
            dynamic.append(" AND l.instant_book = :instantBook");
        }
        if (criteria.minRating() != null) {
            dynamic.append(" AND l.average_rating >= :minRating");
        }
        return dynamic;
    }

    private void bindCommonParams(Query query, ListingSearchCriteria criteria, boolean includeDates) {
        if (includeDates) {
            query.setParameter("pickupDate", criteria.pickupDate());
            query.setParameter("returnDate", criteria.returnDate());
        }
        if (criteria.city() != null) {
            query.setParameter("city", criteria.city().trim());
        }
        if (criteria.query() != null) {
            query.setParameter("query", criteria.query().trim());
        }
        if (criteria.categories() != null && !criteria.categories().isEmpty()) {
            for (int i = 0; i < criteria.categories().size(); i++) {
                query.setParameter("cat" + i, criteria.categories().get(i).name());
            }
        }
        if (criteria.minPrice() != null) {
            query.setParameter("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            query.setParameter("maxPrice", criteria.maxPrice());
        }
        if (criteria.seats() != null) {
            query.setParameter("seats", criteria.seats());
        }
        if (criteria.transmission() != null) {
            query.setParameter("transmission", criteria.transmission().name());
        }
        if (criteria.fuelType() != null) {
            query.setParameter("fuelType", criteria.fuelType().name());
        }
        if (criteria.instantBook() != null) {
            query.setParameter("instantBook", criteria.instantBook());
        }
        if (criteria.minRating() != null) {
            query.setParameter("minRating", criteria.minRating());
        }
    }

    private String orderBy(ListingSearchCriteria criteria) {
        return switch (criteria.sort()) {
            case PRICE_ASC -> " ORDER BY l.base_price_per_day ASC, l.created_at DESC, l.id ASC";
            case PRICE_DESC -> " ORDER BY l.base_price_per_day DESC, l.created_at DESC, l.id ASC";
            case NEWEST -> " ORDER BY l.created_at DESC, l.id ASC";
        };
    }
}
