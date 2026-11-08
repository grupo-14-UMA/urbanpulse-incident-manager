package es.uma.urbanpulse.user;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.uma.urbanpulse.auth.InvalidSessionException;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** The user a valid token belongs to; a token of a deleted user is no longer a valid session. */
	@Transactional(readOnly = true)
	public UserResponse findCurrent(Integer userId) {
		return userRepository.findById(userId).map(UserResponse::from).orElseThrow(InvalidSessionException::new);
	}

	@Transactional(readOnly = true)
	public List<TechnicianResponse> findTechnicians(int limit) {
		return userRepository.findByRoleOrderByNameAscIdAsc(Role.TECHNICIAN, Limit.of(limit)).stream()
			.map(TechnicianResponse::from)
			.toList();
	}

}
