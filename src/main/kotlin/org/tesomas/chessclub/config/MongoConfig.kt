package org.tesomas.chessclub.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

/**
 * Stores java.time values with the MongoDB driver's codecs, so a `LocalDate` is a BSON date at
 * UTC midnight regardless of the server's time zone. Spring Data's default converts via the
 * system zone, which made dates written by `docker/mongo-seed/seed.js` (UTC midnight) read back a
 * day early west of UTC and shifted date-range queries.
 *
 * `@DataMongoTest` slices do not pick this up automatically; import it there.
 */
@Configuration(proxyBeanMethods = false)
class MongoConfig {

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions =
        MongoCustomConversions.create { it.useNativeDriverJavaTimeCodecs() }
}
