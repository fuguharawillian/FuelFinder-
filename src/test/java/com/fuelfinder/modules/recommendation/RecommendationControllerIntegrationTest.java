package com.fuelfinder.modules.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.entity.DataSource;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import com.fuelfinder.modules.vehicle.entity.FuelConsumption;
import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import com.fuelfinder.modules.vehicle.entity.TankCapacity;
import com.fuelfinder.modules.vehicle.entity.Vehicle;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
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
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_recommendations;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderRecommendationIntegrationSecretKey32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class RecommendationControllerIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private FuelTypeRepository fuelTypeRepository;
    @Autowired
    private FuelPriceRepository fuelPriceRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabaseAndSeedFuelCatalog() {
        fuelPriceRepository.deleteAll();
        vehicleRepository.deleteAll();
        stationRepository.deleteAll();
        fuelTypeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
        seedFuelType("ETHANOL", "Etanol", "R$/litro");
        seedFuelType("GASOLINE_REGULAR", "Gasolina Comum", "R$/litro");
        seedFuelType("GASOLINE_PREMIUM", "Gasolina Premium", "R$/litro");
        seedFuelType("DIESEL_S10", "Diesel S10", "R$/litro");
        seedFuelType("DIESEL_S500", "Diesel S500", "R$/litro");
        seedFuelType("CNG", "GNV", "R$/m³");
    }

    @Test
    void returnsPersonalizedRecommendationFromNearbyCurrentPrices() throws Exception {
        String accessToken = registerAndGetAccessToken("recommendation-driver@example.com");
        User user = userRepository.findByEmail("recommendation-driver@example.com").orElseThrow();
        Vehicle vehicle = saveFlexVehicle(user.getId());
        Station station = saveStation();
        savePrice(station, "ETHANOL", "3.89");
        savePrice(station, "GASOLINE_REGULAR", "5.79");

        mockMvc.perform(get("/recommendations/fuel")
                        .header("Authorization", bearer(accessToken))
                        .param("vehicleId", vehicle.getId().toString())
                        .param("latitude", "0")
                        .param("longitude", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.nickname").value("Meu veículo"))
                .andExpect(jsonPath("$.recommendedFuel").value("ETHANOL"))
                .andExpect(jsonPath("$.parityPercentage").value(67.18))
                .andExpect(jsonPath("$.topOptions.length()").value(1))
                .andExpect(jsonPath("$.topOptions[0].stationName").value("Posto Central"))
                .andExpect(jsonPath("$.topOptions[0].costPerKm").value(0.4228))
                .andExpect(jsonPath("$.topOptions[0].unitOfMeasure").value("R$/litro"));
    }

    @Test
    void requiresDriverAuthenticationAndHidesVehiclesOwnedByOtherUsers() throws Exception {
        mockMvc.perform(get("/recommendations/fuel")
                        .param("vehicleId", "00000000-0000-0000-0000-000000000001")
                        .param("latitude", "0")
                        .param("longitude", "0"))
                .andExpect(status().isUnauthorized());

        String ownerToken = registerAndGetAccessToken("recommendation-owner@example.com");
        String otherToken = registerAndGetAccessToken("recommendation-other@example.com");
        User owner = userRepository.findByEmail("recommendation-owner@example.com").orElseThrow();
        Vehicle vehicle = saveFlexVehicle(owner.getId());
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/recommendations/fuel")
                        .header("Authorization", bearer(otherToken))
                        .param("vehicleId", vehicle.getId().toString())
                        .param("latitude", "0")
                        .param("longitude", "0"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/recommendations/fuel")
                        .header("Authorization", bearer(adminToken))
                        .param("vehicleId", vehicle.getId().toString())
                        .param("latitude", "0")
                        .param("longitude", "0"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/recommendations/fuel")
                        .header("Authorization", bearer(ownerToken))
                        .param("vehicleId", vehicle.getId().toString())
                        .param("latitude", "not-a-number")
                        .param("longitude", "0"))
                .andExpect(status().isBadRequest());
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Recommendation Driver","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return accessToken(result);
    }

    private String createAdminAndLogin() throws Exception {
        User admin = new User(
                "Recommendation Admin",
                "recommendation-admin@example.com",
                passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"recommendation-admin@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return accessToken(result);
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private Vehicle saveFlexVehicle(java.util.UUID userId) {
        return vehicleRepository.save(new Vehicle(
                userId,
                "Meu veículo",
                "Marca",
                "Modelo",
                2020,
                FuelTypeAccepted.FLEX,
                new TankCapacity(new BigDecimal("50"), VolumeUnit.LITER),
                new FuelConsumption(new BigDecimal("13.5"), ConsumptionUnit.KM_PER_LITER),
                new FuelConsumption(new BigDecimal("9.2"), ConsumptionUnit.KM_PER_LITER),
                null,
                null));
    }

    private Station saveStation() {
        return stationRepository.save(new Station(
                "12345678901234",
                "Posto Corporativo",
                "Posto Central",
                "Bandeira",
                "Rua Central",
                "1",
                "Centro",
                "São Paulo",
                "SP",
                "01000-000",
                BigDecimal.ZERO,
                new BigDecimal("0.001")));
    }

    private void savePrice(Station station, String code, String saleValue) {
        FuelType fuelType = fuelTypeRepository.findByCode(code).orElseThrow();
        fuelPriceRepository.save(new FuelPrice(
                station,
                fuelType,
                new BigDecimal(saleValue),
                LocalDate.now(),
                DataSource.MANUAL_ADMIN));
    }

    private void seedFuelType(String code, String name, String unitOfMeasure) {
        FuelType fuelType = new FuelType();
        fuelType.setCode(code);
        fuelType.setName(name);
        fuelType.setUnitOfMeasure(unitOfMeasure);
        fuelType.setActive(true);
        fuelTypeRepository.save(fuelType);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
