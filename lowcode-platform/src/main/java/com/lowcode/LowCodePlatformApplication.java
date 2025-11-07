package com.lowcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Main application class for Low-Code Platform
 */
@SpringBootApplication
public class LowCodePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LowCodePlatformApplication.class, args);
        System.out.println("\n" +
            "╔═══════════════════════════════════════════════════════════╗\n" +
            "║     Low-Code Platform with Checkpoint System Started     ║\n" +
            "║                                                           ║\n" +
            "║  API Server:    http://localhost:8080                    ║\n" +
            "║  Swagger UI:    http://localhost:8080/swagger-ui.html    ║\n" +
            "║  Health Check:  http://localhost:8080/api/health         ║\n" +
            "╚═══════════════════════════════════════════════════════════╝\n");
    }

    /**
     * Configure CORS for React frontend
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
            }
        };
    }
}
