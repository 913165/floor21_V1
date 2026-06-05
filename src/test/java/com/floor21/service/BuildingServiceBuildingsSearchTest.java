package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildingServiceBuildingsSearchTest {

    @Test
    void matchesBuildingSearch_findsByProjectBuildingCityAndLayoutPrefix() {
        Builder builder = new Builder();
        builder.setCompanyName("Skyline Homes");

        Building building = new Building();
        building.setId(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        building.setBuilder(builder);
        building.setBuildingName("Tower A");
        building.setCity("Mumbai");

        assertThat(BuildingService.matchesBuildingSearch(building, "tower")).isTrue();
        assertThat(BuildingService.matchesBuildingSearch(building, "mumbai")).isTrue();
        assertThat(BuildingService.matchesBuildingSearch(building, "skyline")).isTrue();
        assertThat(BuildingService.matchesBuildingSearch(building, "a1b2c3d4")).isTrue();
        assertThat(BuildingService.matchesBuildingSearch(building, "pune")).isFalse();
    }
}
