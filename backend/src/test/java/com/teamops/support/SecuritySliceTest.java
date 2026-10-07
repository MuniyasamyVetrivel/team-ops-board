package com.teamops.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.teamops.auth.controller.RefreshCookieManager;
import com.teamops.common.config.ClockConfig;
import com.teamops.common.exception.ErrorResponseWriter;
import com.teamops.common.security.JwtConfig;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.RestAccessDeniedHandler;
import com.teamops.common.security.RestAuthenticationEntryPoint;
import com.teamops.common.security.SecurityConfig;

/**
 * Use together with {@code @WebMvcTest}: loads the real security filter chain, JWT encoder/decoder and error
 * handling, with test JWT settings. The test must provide {@code @MockitoBean UserPrincipalService}; use
 * {@link SliceAuth} to mint tokens for test principals.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({ SecurityConfig.class, JwtConfig.class, JwtTokenService.class, RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class, ErrorResponseWriter.class, RefreshCookieManager.class, ClockConfig.class })
@TestPropertySource(properties = { "app.security.jwt.secret=" + TestFixtures.JWT_SECRET,
		"app.security.jwt.access-token-ttl=60", "app.security.jwt.refresh-token-ttl=7",
		"app.security.refresh-cookie.secure=false", "app.security.cors.allowed-origins=http://localhost:5173" })
public @interface SecuritySliceTest {

}
