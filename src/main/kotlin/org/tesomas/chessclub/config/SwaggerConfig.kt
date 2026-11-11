package org.tesomas.chessclub.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("ChessClub API")
                .description(
                    "REST API for managing chess club members and their recorded games. " +
                    "Supports full CRUD for players (with per-time-control ELO ratings) " +
                    "and games (with PGN storage, rating snapshots, and player validation)."
                )
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("ChessClub")
                        .email("admin@chessclub.com")
                )
                .license(
                    License()
                        .name("MIT")
                )
        )
}
