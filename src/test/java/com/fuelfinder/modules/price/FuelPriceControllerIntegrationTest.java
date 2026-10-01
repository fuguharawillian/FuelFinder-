package com.fuelfinder.modules.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import com.fuelfinder.modules.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_prices;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderPriceIntegrationSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class FuelPriceControllerIntegrationTest {

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
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private FuelPriceRepository fuelPriceRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabaseAndSeedCatalog() {
        fuelPriceRepository.deleteAll();
        vehicleRepository.deleteAll();
        stationRepository.deleteAll();
        fuelTypeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
        seedFuelType("GASOLINE_REGULAR", "Gasolina Comum", "R$/litro");
        seedFuelType("CNG", "GNV", "R$/m³");
    }

    @Test
    void adminCanManagePricesWhileDriverCannotAndPublicCanReadLatestPrices() throws Exception {
        String adminToken = createAdminAndLogin();
        String driverToken = registerAndLogin("price-driver@example.com");
        Station station = createStation("Price Station", 0, 0);

        mockMvc.perform(post("/stations/" + station.getId() + "/fuel-prices")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceBody("GASOLINE_REGULAR", "5.89", "2026-09-27")))
                .andExpect(status().isForbidden());

        MvcResult created = createPrice(
                adminToken, station.getId(), "5.89", "2026-09-27")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.dataSource").value("MANUAL_ADMIN"))
                .andExpect(jsonPath("$.unitOfMeasure").value("R$/litro"))
                .andReturn();
        UUID priceId = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).get("id").asText());

        createPrice(adminToken, station.getId(), "5.95", "2026-09-27")
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/stations/" + station.getId()
                                + "/fuel-prices/" + priceId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saleValue":5.75,"collectionDate":"2026-09-28"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleValue").value(5.75))
                .andExpect(jsonPath("$.collectionDate").value("2026-09-28"));

        createPrice(adminToken, station.getId(), "6.10", "2026-09-29")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/stations/" + station.getId() + "/fuel-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].saleValue").value(6.10))
                .andExpect(jsonPath("$[0].collectionDate").value("2026-09-29"));
    }

    @Test
    void compareSortsByPriceAndCalculatesCngCostsUsingCubicMeters() throws Exception {
        String adminToken = createAdminAndLogin();
        String driverToken = registerAndLogin("price-compare-driver@example.com");
        Station nearby = createStation("Nearby", 0, 0.005);
        Station farther = createStation("Farther", 0, 0.01);

        createPrice(adminToken, nearby.getId(), "5.50", "2026-09-28")
                .andExpect(status().isCreated());
        createPrice(adminToken, farther.getId(), "5.20", "2026-09-28")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/fuel-prices/compare")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "5")
                        .param("fuelTypeCode", "GASOLINE_REGULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stationName").value("Farther"))
                .andExpect(jsonPath("$[0].price").value(5.2));

        UUID cngVehicleId = createCngVehicle(driverToken);
        createPrice(adminToken, nearby.getId(), "4.00", "2026-09-28", "CNG")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/fuel-prices/compare")
                        .header("Authorization", bearer(driverToken))
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "5")
                        .param("fuelTypeCode", "CNG")
                        .param("vehicleId", cngVehicleId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unitOfMeasure").value("R$/m³"))
                .andExpect(jsonPath("$[0].costPerKm").value(0.3077))
                .andExpect(jsonPath("$[0].estimatedFullTankCost").value(50.0))
                .andExpect(jsonPath("$[0].estimatedRange").value(162.5))
                .andExpect(jsonPath("$[0].estimatedRoundTripCost").isNumber());

        mockMvc.perform(get("/fuel-prices/compare")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "5")
                        .param("fuelTypeCode", "CNG")
                        .param("vehicleId", cngVehicleId.toString()))
                .andExpect(status().isUnauthorized());

        MvcResult distanceSorted = mockMvc.perform(get("/fuel-prices/compare")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "5")
                        .param("fuelTypeCode", "GASOLINE_REGULAR")
                        .param("sortBy", "DISTANCE"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sortedResults = objectMapper.readTree(
                distanceSorted.getResponse().getContentAsString());
        assertEquals("Nearby", sortedResults.get(0).get("stationName").asText());
        assertTrue(sortedResults.get(0).get("distanceKm").asDouble()
                < sortedResults.get(1).get("distanceKm").asDouble());
    }

    @Test
    void rejectsInvalidComparisonQueriesAndEmptyPriceUpdates() throws Exception {
        String adminToken = createAdminAndLogin();
        Station station = createStation("Validation Station", 0, 0);

        mockMvc.perform(get("/fuel-prices/compare")
                        .param("latitude", "91")
                        .param("longitude", "0"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/fuel-prices/compare")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("sortBy", "INVALID"))
                .andExpect(status().isUnprocessableEntity());

        MvcResult created = createPrice(
                adminToken, station.getId(), "5.50", "2026-09-28")
                .andExpect(status().isCreated())
                .andReturn();
        UUID priceId = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(patch("/stations/" + station.getId()
                                + "/fuel-prices/" + priceId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void seedFuelType(String code, String name, String unit) {
        FuelType fuelType = new FuelType();
        fuelType.setCode(code);
        fuelType.setName(name);
        fuelType.setUnitOfMeasure(unit);
        fuelType.setActive(true);
        fuelTypeRepository.save(fuelType);
    }

    private Station createStation(String name, double latitude, double longitude) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return stationRepository.save(new Station(
                "12" + suffix,
                name + " Corporate",
                name,
                "Brand",
                "Main Street",
                "1",
                "Center",
                "São Paulo",
                "SP",
                "01000-000",
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude)));
    }

    private org.springframework.test.web.servlet.ResultActions createPrice(
            String token, UUID stationId, String value, String date) throws Exception {
        return createPrice(token, stationId, value, date, "GASOLINE_REGULAR");
    }

    private org.springframework.test.web.servlet.ResultActions createPrice(
            String token, UUID stationId, String value, String date, String fuelType)
            throws Exception {
        return mockMvc.perform(post("/stations/" + stationId + "/fuel-prices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(priceBody(fuelType, value, date)));
    }

    private String priceBody(String fuelType, String value, String date) {
        return """
                {"fuelTypeCode":"%s","saleValue":%s,"collectionDate":"%s"}
                """.formatted(fuelType, value, date);
    }

    private UUID createCngVehicle(String driverToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/vehicles")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname":"CNG car",
                                  "brand":"Brand",
                                  "model":"Model",
                                  "yearManufacture":2022,
                                  "fuelTypeAccepted":"CNG",
                                  "tankCapacity":{"value":12.5,"unit":"CUBIC_METER"},
                                  "averageConsumptionCng":{
                                    "value":13,"unit":"KM_PER_CUBIC_METER"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText());
    }

    private String createAdminAndLogin() throws Exception {
        User admin = new User(
                "Price Admin", "price-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        return login("price-admin@example.com");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Price Driver","email":"%s","password":"%s"}
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
