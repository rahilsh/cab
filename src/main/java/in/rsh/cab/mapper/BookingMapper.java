package in.rsh.cab.mapper;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.response.BookingResponse;
import in.rsh.cab.model.response.LocationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface BookingMapper {

  @Mapping(target = "startLocation", source = "entity", qualifiedByName = "toStartLocation")
  @Mapping(target = "endLocation", source = "entity", qualifiedByName = "toEndLocation")
  Booking toModel(BookingEntity entity);

  @Mapping(target = "startLocationX", source = "booking.startLocation", qualifiedByName = "locationToX")
  @Mapping(target = "startLocationY", source = "booking.startLocation", qualifiedByName = "locationToY")
  @Mapping(target = "endLocationX", source = "booking.endLocation", qualifiedByName = "locationToX")
  @Mapping(target = "endLocationY", source = "booking.endLocation", qualifiedByName = "locationToY")
  BookingEntity toEntity(Booking booking);

  @Mapping(target = "bookingId", source = "booking.bookingId")
  @Mapping(target = "startTime", source = "booking.startTime")
  @Mapping(target = "endTime", source = "booking.endTime")
  @Mapping(target = "riderId", source = "booking.riderId")
  @Mapping(target = "cabId", source = "booking.cabId")
  @Mapping(target = "status", source = "booking.status", qualifiedByName = "bookingStatusToString")
  @Mapping(target = "startLocation", source = "booking.startLocation", qualifiedByName = "toLocationResponse")
  @Mapping(target = "endLocation", source = "booking.endLocation", qualifiedByName = "toLocationResponse")
  @Mapping(target = "bookedBy", source = "booking.bookedBy")
  BookingResponse toResponse(Booking booking);

  @Named("toStartLocation")
  default Location toStartLocation(BookingEntity entity) {
    if (entity.getStartLocationX() == null || entity.getStartLocationY() == null) {
      return null;
    }
    return new Location(entity.getStartLocationX(), entity.getStartLocationY());
  }

  @Named("toEndLocation")
  default Location toEndLocation(BookingEntity entity) {
    if (entity.getEndLocationX() == null || entity.getEndLocationY() == null) {
      return null;
    }
    return new Location(entity.getEndLocationX(), entity.getEndLocationY());
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

  @Named("bookingStatusToString")
  default String bookingStatusToString(Booking.BookingStatus status) {
    return status != null ? status.name() : null;
  }
}
