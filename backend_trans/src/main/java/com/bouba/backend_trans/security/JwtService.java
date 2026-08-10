package com.bouba.backend_trans.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.bouba.backend_trans.entity.AppUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final String secret;
	private final long jwtExpirationMs;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-ms}") long jwtExpirationMs
	) {
		this.secret = secret;
		this.jwtExpirationMs = jwtExpirationMs;
	}

	public String generateToken(AppUser user) {
		Map<String, Object> claims = Map.of(
				"role", user.getRole().name(),
				"userType", user.getUserType().name()
		);
		return generateToken(claims, user.getEmail());
	}

	public String extractUsername(String token) {
		return extractAllClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		String username = extractUsername(token);
		return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
	}

	private String generateToken(Map<String, Object> extraClaims, String subject) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + jwtExpirationMs);

		return Jwts.builder()
				.claims(extraClaims)
				.subject(subject)
				.issuedAt(now)
				.expiration(expiration)
				.signWith(getSignInKey())
				.compact();
	}

	private boolean isTokenExpired(String token) {
		return extractAllClaims(token).getExpiration().before(new Date());
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSignInKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey getSignInKey() {
		byte[] keyBytes;
		try {
			keyBytes = Decoders.BASE64.decode(secret);
		} catch (IllegalArgumentException ex) {
			keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		}
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
