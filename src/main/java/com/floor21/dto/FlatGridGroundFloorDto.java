package com.floor21.dto;



import com.floor21.util.GroundFloorShopConfigUtil;
import com.floor21.util.ParkingFloorConfigUtil;
import java.math.BigDecimal;

import java.util.List;



public record FlatGridGroundFloorDto(

        boolean configured,

        int shopCount,

        String rangeLabel,

        List<FlatGridFlatDto> shops,

        BigDecimal shopAreaSqft,

        int shopGridRows,

        int shopMinGridRows,

        boolean hasLayoutImage,

        int parkingSlotCount,

        BigDecimal parkingSlotAreaSqft,

        int shopSizePercent,

        int parkingCarSizePercent,

        int carLiftCount,

        int passengerLiftCount,

        int gateCount) {



    public static FlatGridGroundFloorDto empty() {

        return new FlatGridGroundFloorDto(

                false,

                0,

                null,

                List.of(),

                null,

                1,

                1,

                false,

                0,

                null,

                GroundFloorShopConfigUtil.DEFAULT_SHOP_SIZE_PERCENT,

                ParkingFloorConfigUtil.DEFAULT_CAR_SIZE_PERCENT,

                0,

                0,

                0);

    }

}


