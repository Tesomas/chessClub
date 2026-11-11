package org.tesomas.chessclub

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ChessClubApplicationTests {

    @Test
    fun `application context loads`() {
    }
}
