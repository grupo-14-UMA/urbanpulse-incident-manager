package es.uma.urbanpulse.auth;

import es.uma.urbanpulse.user.Role;

/** Who a valid access token was issued to. */
public record TokenSubject(Integer userId, Role role) {
}
