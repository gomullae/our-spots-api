package com.ourspots.common.exception

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun handleNoResourceFoundException_whenPathUnmapped_shouldReturn404() {
        mockMvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }
}
