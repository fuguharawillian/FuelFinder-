package com.fuelfinder.modules.station;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_station;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderStationIntegrationSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class StationControllerIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void servesFrontendPagesAndAssetsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FuelFinder")));
        mockMvc.perform(get("/admin/reviews.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/css/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mockMvc.perform(get("/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refreshToken")));
    }

    @Test
    void adminCanCreateUpdateAndDeactivateStationWhilePublicSearchReturnsActiveStations()
            throws Exception {
        String adminToken = createAdminAndLogin();
        String driverToken = registerAndLogin("station-driver@example.com");

        mockMvc.perform(post("/stations")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationBody("12345678000195", "Near", 0, 0)))
                .andExpect(status().isForbidden());

        MvcResult created = createStation(
                adminToken, "12.345.678/0001-95", "Near", 0, 0)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        UUID stationId = stationId(created);
        mockMvc.perform(get("/stations/" + stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateName").value("Near"));

        createStation(adminToken, "12345678000195", "Duplicate", 0, 0)
                .andExpect(status().isConflict());

        MvcResult second = createStation(
                adminToken, "12345678000196", "Far", 0, 0.2)
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/stations")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].corporateName").value("Near"))
                .andExpect(jsonPath("$[1].corporateName").value("Far"));

        mockMvc.perform(get("/stations").param("query", "Far"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].corporateName").value("Far"));

        mockMvc.perform(patch("/stations/" + stationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeName":"Updated trade name","city":"New City"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Updated trade name"))
                .andExpect(jsonPath("$.city").value("New City"));

        mockMvc.perform(delete("/stations/" + stationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/stations/" + stationId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/stations").param("query", "Near"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertEquals("INACTIVE",
                stationRepository.findById(stationId).orElseThrow().getStatus().name());
        assertEquals("Far",
                stationRepository.findById(stationId(second)).orElseThrow().getCorporateName());
    }

    @Test
    void validatesPublicSearchAndStationCreationFields() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/stations"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/stations")
                        .param("latitude", "91")
                        .param("longitude", "0"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/stations")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "-1"))
                .andExpect(status().isUnprocessableEntity());

        createStation(adminToken, "12345678000197", "Invalid coordinates", 91, 0)
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/stations")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationBody("invalid-cnpj", "Invalid CNPJ", 0, 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/stations")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"12345678000195\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions createStation(
            String token, String cnpj, String name, double latitude, double longitude)
            throws Exception {
        return mockMvc.perform(post("/stations")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(stationBody(cnpj, name, latitude, longitude)));
    }

    private String stationBody(String cnpj, String name, double latitude, double longitude) {
        return """
                {
                  "cnpj":"%s",
                  "corporateName":"%s",
                  "tradeName":"%s trade",
                  "brand":"Brand",
                  "street":"Main Street",
                  "number":"10",
                  "neighborhood":"Center",
                  "city":"São Paulo",
                  "state":"SP",
                  "postalCode":"01000-000",
                  "latitude":%s,
                  "longitude":%s
                }
                """.formatted(cnpj, name, name, latitude, longitude);
    }

    private String createAdminAndLogin() throws Exception {
        User admin = new User(
                "Station Admin", "station-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        return login("station-admin@example.com");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Station Driver","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private UUID stationId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
