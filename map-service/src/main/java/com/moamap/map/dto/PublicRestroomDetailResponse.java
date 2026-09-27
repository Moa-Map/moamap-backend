package com.moamap.map.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.moamap.map.entity.PublicRestroom;

public record PublicRestroomDetailResponse(
    Long id,
    String mngNo,
    String name,
    String category,
    String ownerType,
    String roadAddress,
    String lotAddress,
    BigDecimal lat,
    BigDecimal lng,
    Short maleToilet,
    Short maleUrinal,
    Short maleDisabledToilet,
    Short maleDisabledUrinal,
    Short maleChildToilet,
    Short maleChildUrinal,
    Short femaleToilet,
    Short femaleDisabledToilet,
    Short femaleChildToilet,
    String openHours,
    String openHoursDetail,
    boolean diaperTable,
    String diaperTableLocation,
    boolean emergencyBell,
    String emergencyBellLocation,
    boolean entranceCctv,
    String wasteDisposal,
    String managerOrg,
    String phone,
    String installedYm,
    String remodeledYm,
    LocalDate dataRefDate
) {

    public static PublicRestroomDetailResponse from(PublicRestroom r) {
        return new PublicRestroomDetailResponse(
            r.getId(),
            r.getMngNo(),
            r.getName(),
            r.getCategory(),
            r.getOwnerType(),
            r.getRoadAddress(),
            r.getLotAddress(),
            r.getLat(),
            r.getLng(),
            r.getMaleToilet(),
            r.getMaleUrinal(),
            r.getMaleDisabledToilet(),
            r.getMaleDisabledUrinal(),
            r.getMaleChildToilet(),
            r.getMaleChildUrinal(),
            r.getFemaleToilet(),
            r.getFemaleDisabledToilet(),
            r.getFemaleChildToilet(),
            r.getOpenHours(),
            r.getOpenHoursDetail(),
            r.isDiaperTable(),
            r.getDiaperTableLocation(),
            r.isEmergencyBell(),
            r.getEmergencyBellLocation(),
            r.isEntranceCctv(),
            r.getWasteDisposal(),
            r.getManagerOrg(),
            r.getPhone(),
            r.getInstalledYm(),
            r.getRemodeledYm(),
            r.getDataRefDate()
        );
    }
}
