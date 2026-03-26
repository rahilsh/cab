package in.rsh.cab.mapper;

import in.rsh.cab.entity.CityEntity;
import in.rsh.cab.model.City;
import in.rsh.cab.model.response.CityResponse;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface CityMapper {

  City toModel(CityEntity entity);

  CityEntity toEntity(City city);

  CityResponse toResponse(City city);
}
