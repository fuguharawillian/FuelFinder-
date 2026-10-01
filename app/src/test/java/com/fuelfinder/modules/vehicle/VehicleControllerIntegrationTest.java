package com.fuelfinder.modules.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_vehicle;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderVehicleIntegrationSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class VehicleControllerIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
        userRepository.deleteAll();
    }

    @Test
    void createsListsAndRestrictsVehicleDetailsToItsOwner() throws Exception {
        AuthTokens firstDriver = register("vehicle-owner@example.com");
        AuthTokens secondDriver = register("other-owner@example.com");

        MvcResult created = createVehicle(firstDriver.accessToken(), flexVehicleBody())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fuelTypeAccepted").value("FLEX"))
                .andReturn();
        UUID firstVehicleId = responseVehicleId(created);

        MvcResult otherCreated = createVehicle(secondDriver.accessToken(), flexVehicleBody())
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondVehicleId = responseVehicleId(otherCreated);

        mockMvc.perform(get("/vehicles")
                        .header("Authorization", bearer(firstDriver.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstVehicleId.toString()));

        mockMvc.perform(get("/vehicles/" + firstVehicleId)
                        .header("Authorization", bearer(firstDriver.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Family car"));

        mockMvc.perform(get("/vehicles/" + secondVehicleId)
                        .header("Authorization", bearer(firstDriver.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void partiallyUpdatesVehicleAndDeletesIt() throws Exception {
        AuthTokens driver = register("vehicle-update@example.com");
        UUID vehicleId = responseVehicleId(createVehicle(
                driver.accessToken(), flexVehicleBody()).andReturn());

        mockMvc.perform(patch("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname":"Updated car",
                                  "brand":"New Brand",
                                  "model":"New Model",
                                  "yearManufacture":2022,
                                  "fuelTypeAccepted":"GASOLINE",
                                  "tankCapacity":{"value":55.25,"unit":"LITER"},
                                  "averageConsumptionGasoline":{
                                    "value":14.5,"unit":"KM_PER_LITER"
                                  },
                                  "averageConsumptionEthanol":null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Updated car"))
                .andExpect(jsonPath("$.brand").value("New Brand"))
                .andExpect(jsonPath("$.model").value("New Model"))
                .andExpect(jsonPath("$.yearManufacture").value(2022))
                .andExpect(jsonPath("$.fuelTypeAccepted").value("GASOLINE"))
                .andExpect(jsonPath("$.averageConsumptionEthanol").doesNotExist())
                .andExpect(jsonPath("$.averageConsumptionGasoline.unit")
                        .value("KM_PER_LITER"));

        mockMvc.perform(delete("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void enforcesAuthenticationRoleAndRequestValidation() throws Exception {
        mockMvc.perform(get("/vehicles"))
                .andExpect(status().isUnauthorized());

        User admin = new User(
                "Admin", "vehicle-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        AuthTokens adminTokens = login("vehicle-admin@example.com");

        mockMvc.perform(get("/vehicles")
                        .header("Authorization", bearer(adminTokens.accessToken())))
                .andExpect(status().isForbidden());

        AuthTokens driver = register("vehicle-validation@example.com");
        createVehicle(driver.accessToken(), """
                {
                  "nickname":"Flex car",
                  "brand":"Brand",
                  "model":"Model",
                  "yearManufacture":2020,
                  "fuelTypeAccepted":"FLEX",
                  "tankCapacity":{"value":50,"unit":"LITER"}
                }
                """)
                .andExpect(status().isUnprocessableEntity());

        createVehicle(driver.accessToken(), """
                {
                  "nickname":" ",
                  "brand":"Brand",
                  "model":"Model",
                  "yearManufacture":2020,
                  "fuelTypeAccepted":"GASOLINE",
                  "tankCapacity":{"value":50,"unit":"LITER"},
                  "averageConsumptionGasoline":{"value":12,"unit":"KM_PER_LITER"}
                }
                """)
                .andExpect(status().isBadRequest());

        createVehicle(driver.accessToken(), """
                {
                  "nickname":"Bad fuel",
                  "brand":"Brand",
                  "model":"Model",
                  "yearManufacture":2020,
                  "fuelTypeAccepted":"UNKNOWN",
                  "tankCapacity":{"value":50,"unit":"LITER"},
                  "averageConsumptionGasoline":{"value":12,"unit":"KM_PER_LITER"}
                }
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPatchesThatBreakFuelOrVehicleConstraints() throws Exception {
        AuthTokens driver = register("vehicle-patch-validation@example.com");
        UUID vehicleId = responseVehicleId(createVehicle(
                driver.accessToken(), flexVehicleBody()).andReturn());

        mockMvc.perform(patch("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"averageConsumptionGasoline":{
                                  "value":41,"unit":"KM_PER_LITER"
                                }}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fuelTypeAccepted":"GASOLINE"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tankCapacity":{"value":0,"unit":"LITER"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsNotFoundWhenDeletingVehicleOwnedByAnotherDriver() throws Exception {
        AuthTokens owner = register("vehicle-delete-owner@example.com");
        AuthTokens other = register("vehicle-delete-other@example.com");
        UUID vehicleId = responseVehicleId(createVehicle(
                owner.accessToken(), flexVehicleBody()).andReturn());

        mockMvc.perform(delete("/vehicles/" + vehicleId)
                        .header("Authorization", bearer(other.accessToken())))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions createVehicle(
            String accessToken,
            String body) throws Exception {
        return mockMvc.perform(post("/vehicles")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private AuthTokens register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Vehicle Driver","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return tokens(result);
    }

    private AuthTokens login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return tokens(result);
    }

    private AuthTokens tokens(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.get("accessToken").asText();
        assertNotNull(accessToken);
        return new AuthTokens(accessToken);
    }

    private UUID responseVehicleId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String flexVehicleBody() {
        return """
                {
                  "nickname":"Family car",
                  "brand":"Brand",
                  "model":"Model",
                  "yearManufacture":2020,
                  "fuelTypeAccepted":"FLEX",
                  "tankCapacity":{"value":50,"unit":"LITER"},
                  "averageConsumptionGasoline":{
                    "value":12.5,"unit":"KM_PER_LITER"
                  },
                  "averageConsumptionEthanol":{
                    "value":8,"unit":"KM_PER_LITER"
                  }
                }
                """;
    }

    private record AuthTokens(String accessToken) {
    }
}
