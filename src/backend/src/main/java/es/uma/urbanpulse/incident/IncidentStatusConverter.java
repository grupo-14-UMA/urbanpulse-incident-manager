package es.uma.urbanpulse.incident;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class IncidentStatusConverter implements AttributeConverter<IncidentStatus, Integer> {

	@Override
	public Integer convertToDatabaseColumn(IncidentStatus status) {
		return status == null ? null : status.getId();
	}

	@Override
	public IncidentStatus convertToEntityAttribute(Integer id) {
		return id == null ? null : IncidentStatus.fromId(id);
	}

}
