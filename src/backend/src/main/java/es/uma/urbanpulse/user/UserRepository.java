package es.uma.urbanpulse.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

	/** Expects an email already normalized with {@link User#normalizeEmail(String)}. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	List<User> findByRoleOrderByNameAscIdAsc(Role role, Limit limit);

}
