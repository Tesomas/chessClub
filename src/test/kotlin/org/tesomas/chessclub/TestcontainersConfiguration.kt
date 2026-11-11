package org.tesomas.chessclub

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts a throwaway MongoDB container and wires it into Spring via @ServiceConnection.
 * Import it into any test that needs a real database; requires Docker to be running.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mongoDbContainer(): MongoDBContainer = MongoDBContainer(DockerImageName.parse(MONGO_IMAGE))

    private companion object {
        const val MONGO_IMAGE = "mongo:7"
    }
}
