package org.tesomas.chessclub.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.tesomas.chessclub.TestcontainersConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration::class)
class SwaggerConfigTest {

    @Autowired lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET api-docs returns OpenAPI spec with API info and endpoints`() {
        val response = restTemplate.getForEntity("/v3/api-docs", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.contains("\"title\":\"ChessClub API\""))
        assertTrue(body.contains("\"/api/players/{id}\""))
        assertTrue(body.contains("\"/api/games/{id}\""))
    }

    @Test
    fun `GET swagger-ui returns 200`() {
        val response = restTemplate.getForEntity("/swagger-ui/index.html", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
