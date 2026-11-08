package es.uma.urbanpulse.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.uma.urbanpulse.user.Role;
import es.uma.urbanpulse.user.User;
import es.uma.urbanpulse.user.UserRepository;
import es.uma.urbanpulse.user.UserResponse;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthTokens authTokens;
	/** Compared against when the email is unknown, so both failures take the same time. */
	private final String unknownUserHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthTokens authTokens) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authTokens = authTokens;
		this.unknownUserHash = passwordEncoder.encode("unknown-user-placeholder");
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = User.normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			log.info("Registration rejected, email already registered: email={}", EmailMask.mask(email));
			throw new EmailAlreadyRegisteredException();
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.CITIZEN);
		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			log.info("Registration rejected, email registered concurrently: email={}", EmailMask.mask(email));
			throw new EmailAlreadyRegisteredException();
		}
		log.info("Citizen registered: userId={}, email={}", user.getId(), EmailMask.mask(email));
		return authResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = User.normalizeEmail(request.email());
		User user = userRepository.findByEmail(email).orElse(null);
		String hash = user == null ? unknownUserHash : user.getPasswordHash();
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (user == null || !matches) {
			log.warn("Login failed: email={}, reason={}", EmailMask.mask(email),
					user == null ? "unknown email" : "wrong password");
			throw new InvalidCredentialsException();
		}
		return authResponse(user);
	}

	/** Exchanges a still-valid token for a fresh one, read from the current state of the user. */
	@Transactional(readOnly = true)
	public AuthResponse refresh(TokenSubject subject) {
		User user = userRepository.findById(subject.userId()).orElseThrow(InvalidSessionException::new);
		return authResponse(user);
	}

	private AuthResponse authResponse(User user) {
		IssuedToken issued = authTokens.issue(user.getId(), user.getRole());
		return new AuthResponse(issued.token(), issued.expiresAt(), UserResponse.from(user));
	}

}
