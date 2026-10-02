package com.fuelfinder.modules.anp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import com.fuelfinder.modules.anp.service.AnpCsvDownloader;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.station.service.GeoapifyGeocodingService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_anp;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderAnpIntegrationSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500",
                "geoapify.api-key="
        })
@AutoConfigureMockMvc
class AnpImportControllerIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";
    private static final String SOURCE_URL =
            "https://www.gov.br/anp/historico.csv?token=must-not-be-logged";
    private static final URI SOURCE_URI = URI.create(SOURCE_URL);

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
    private AnpImportLogRepository importLogRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AnpCsvDownloader csvDownloader;
    @MockitoBean
    private GeoapifyGeocodingService geocodingService;

    @BeforeEach
    void resetDatabaseAndMocks() {
        fuelPriceRepository.deleteAll();
        importLogRepository.deleteAll();
        stationRepository.deleteAll();
        fuelTypeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
        seedFuelType("ETHANOL", "Etanol", "R$/litro");
        seedFuelType("GASOLINE_PREMIUM", "Gasolina Premium", "R$/litro");
        reset(csvDownloader, geocodingService);
        when(csvDownloader.validateSourceUrl(SOURCE_URL)).thenReturn(SOURCE_URI);
        when(csvDownloader.sanitizeSourceUrl(SOURCE_URI))
                .thenReturn("https://www.gov.br/anp/historico.csv");
        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "GASOLINA ADITIVADA", "5,899")));
        when(geocodingService.isConfigured()).thenReturn(false);
    }

    @Test
    void adminCanImportAndReimportIdempotentlyWithCompleteAudit() throws Exception {
        String adminToken = createAdminAndLogin();

        MvcResult imported = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalRecordsRead").value(2))
                .andExpect(jsonPath("$.totalRecordsImported").value(2))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://www.gov.br/anp/historico.csv"))
                .andReturn();
        UUID importId = UUID.fromString(objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("id").asText());
        assertEquals(2, stationRepository.count());
        assertEquals(2, fuelPriceRepository.count());
        assertEquals(2, importLogRepository.findById(importId).orElseThrow()
                .getErrorDetails().split("\\R").length);
        Station ungeocoded = stationRepository.findByCnpj("12345678000195").orElseThrow();
        assertNull(ungeocoded.getLatitude());

        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "GASOLINA ADITIVADA", "5,899")));
        triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalRecordsImported").value(0));
        assertEquals(2, stationRepository.count());
        assertEquals(2, fuelPriceRepository.count());

        mockMvc.perform(get("/admin/anp/imports")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/admin/anp/imports/" + importId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(importId.toString()))
                .andExpect(jsonPath("$.triggeredBy").value(
                        userRepository.findByEmail("anp-admin@example.com")
                                .orElseThrow().getId().toString()));
    }

    @Test
    void storesGeoapifyCoordinatesWhenConfiguredAndMarksUnknownProductsPartial()
            throws Exception {
        String adminToken = createAdminAndLogin();
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode(
                "Rua Um 10, Centro, 01000-000, Sao Paulo, SP, Brasil"))
                .thenReturn(java.util.Optional.of(
                        new GeoapifyGeocodingService.GeoPoint(-23.5, -46.6)));
        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "COMBUSTIVEL DESCONHECIDO", "5,899")));

        triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalRecordsRead").value(2))
                .andExpect(jsonPath("$.totalRecordsImported").value(1));

        Station geocoded = stationRepository.findByCnpj("12345678000195").orElseThrow();
        assertEquals(0, new BigDecimal("-23.5").compareTo(geocoded.getLatitude()));
        assertEquals(0, new BigDecimal("-46.6").compareTo(geocoded.getLongitude()));
        assertEquals(1, stationRepository.count());
        assertEquals(1, fuelPriceRepository.count());
    }

    @Test
    void failedLayoutCreatesFailureAuditAndPreservesExistingImportedPrices()
            throws Exception {
        String adminToken = createAdminAndLogin();
        triggerImport(adminToken, SOURCE_URL).andExpect(status().isAccepted());
        when(csvDownloader.download(SOURCE_URL))
                .thenReturn("invalid;layout".getBytes(StandardCharsets.UTF_8));

        triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorDetails").exists());

        assertEquals(2, fuelPriceRepository.count());
        assertEquals(2, importLogRepository.count());
    }

    @Test
    void restrictsImportAndAuditRoutesToAdminsAndValidatesRequest() throws Exception {
        mockMvc.perform(get("/admin/anp/imports"))
                .andExpect(status().isUnauthorized());
        String driverToken = registerAndLogin("anp-driver@example.com");
        mockMvc.perform(get("/admin/anp/imports")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden());
        triggerImport(driverToken, SOURCE_URL)
                .andExpect(status().isForbidden());

        String adminToken = createAdminAndLogin();
        when(csvDownloader.validateSourceUrl("https://example.com/anp.csv"))
                .thenThrow(new BusinessException("invalid source URL"));
        triggerImport(adminToken, "https://example.com/anp.csv")
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/admin/anp/import")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceUrl":"","referencePeriod":""}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/anp/imports/" + UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        assertEquals(0, importLogRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions triggerImport(
            String token,
            String sourceUrl) throws Exception {
        return mockMvc.perform(post("/admin/anp/import")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceUrl":"%s","referencePeriod":"2026-S1"}
                        """.formatted(sourceUrl)));
    }

    private String createAdminAndLogin() throws Exception {
        User admin = new User(
                "ANP Admin", "anp-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        return login("anp-admin@example.com");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"ANP Driver","email":"%s","password":"%s"}
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
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void seedFuelType(String code, String name, String unit) {
        FuelType fuelType = new FuelType();
        fuelType.setCode(code);
        fuelType.setName(name);
        fuelType.setUnitOfMeasure(unit);
        fuelType.setActive(true);
        fuelTypeRepository.save(fuelType);
    }

    private String row(String cnpj, String product, String price) {
        return "SE;SP;Sao Paulo;Posto ANP;" + cnpj + ";Rua Um;10;Centro;"
                + "01000-000;" + product + ";29/09/2026;" + price + ";R$/L;Marca";
    }

    private byte[] csv(String... records) {
        String header = "Regiao - Sigla;Estado - Sigla;Municipio;Revenda;CNPJ da Revenda;"
                + "Nome da Rua;Numero Rua;Bairro;Cep;Produto;Data da Coleta;"
                + "Valor de Venda;Unidade de Medida;Bandeira";
        return (header + "\n" + String.join("\n", records))
                .getBytes(StandardCharsets.UTF_8);
    }
}
