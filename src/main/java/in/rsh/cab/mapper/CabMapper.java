package in.rsh.cab.mapper;

import in.rsh.cab.entity.CabEntity;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.response.CabResponse;
import in.rsh.cab.model.response.LocationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface CabMapper {

  @Mapping(target = "cabId", source = "id")
  @Mapping(target = "location", source = "entity", qualifiedByName = "toLocation")
  Cab toModel(CabEntity entity);

  @Mapping(target = "id", source = "cabId")
  @Mapping(target = "locationX", source = "model.location", qualifiedByName = "locationToX")
  @Mapping(target = "locationY", source = "model.location", qualifiedByName = "locationToY")
  CabEntity toEntity(Cab model);

  @Mapping(target = "cabId", source = "cab.cabId")
  @Mapping(target = "driverId", source = "cab.driverId")
  @Mapping(target = "cabNumber", source = "cab.cabNumber")
  @Mapping(target = "status", source = "cab.status", qualifiedByName = "cabStatusToString")
  @Mapping(target = "type", source = "cab.type", qualifiedByName = "cabTypeToString")
  @Mapping(target = "location", source = "cab.location", qualifiedByName = "toLocationResponse")
  @Mapping(target = "idleFrom", source = "cab.idleFrom")
  @Mapping(target = "cityId", source = "cab.cityId")
  @Mapping(target = "model", source = "cab.model")
  CabResponse toResponse(Cab cab);

  @Named("toLocation")
  default Location toLocation(CabEntity entity) {
    if (entity.getLocationX() == null || entity.getLocationY() == null) {
      return null;
    }
    return new Location(entity.getLocationX(), entity.getLocationY());
  }

  @Named("locationToX")
  default Integer locationToX(Location location) {
    return location != null ? location.latitude() : null;
  }

  @Named("locationToY")
  default Integer locationToY(Location location) {
    return location != null ? location.longitude() : null;
  }

  @Named("toLocationResponse")
  default LocationResponse toLocationResponse(Location location) {
    if (location == null) {
      return null;
    }
    return new LocationResponse(location.latitude(), location.longitude());
  }

  @Named("cabStatusToString")
  default String cabStatusToString(Cab.CabStatus status) {
    return status != null ? status.name() : null;
  }

  @Named("cabTypeToString")
  default String cabTypeToString(Cab.CabType type) {
    return type != null ? type.name() : null;
  }
}
