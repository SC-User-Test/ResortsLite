package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Azure Active Directory integration.
 * Enables cloud-native authentication and authorization.
 * 
 * Fixes applied:
 * - cr-java-0090: Integrates Azure Active Directory for authentication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${azure.activedirectory.enabled:false}")
    private boolean azureAdEnabled;

    /**
     * Configures security filter chain.
     * When Azure AD is enabled, integrates with Azure Active Directory for authentication.
     * Otherwise, uses basic security for development/testing.
     * 
     * @param http HttpSecurity configuration
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (azureAdEnabled) {
            // Azure AD authentication enabled
            http
                .authorizeRequests()
                    .antMatchers("/api/bookings/**").authenticated()
                    .antMatchers("/h2-console/**").permitAll()
                    .anyRequest().authenticated()
                .and()
                .oauth2Login()
                .and()
                .oauth2ResourceServer()
                    .jwt();
        } else {
            // Development mode - permit all for testing
            http
                .authorizeRequests()
                    .anyRequest().permitAll()
                .and()
                .csrf().disable()
                .headers().frameOptions().disable(); // For H2 console
        }
        
        return http.build();
    }
}
