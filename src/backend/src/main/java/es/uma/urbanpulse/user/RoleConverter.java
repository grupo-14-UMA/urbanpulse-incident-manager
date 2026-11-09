package es.uma.urbanpulse.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleConverter implements AttributeConverter<Role, Integer> {

	@Override
	public Integer convertToDatabaseColumn(Role role) {
		return role == null ? null : role.getId();
	}

	@Override
	public Role convertToEntityAttribute(Integer id) {
		return id == null ? null : Role.fromId(id);
	}

}
