package com.fuelfinder.modules.anp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import com.fuelfinder.modules.anp.service.AnpRetailerApiClient;
import com.fuelfinder.modules.anp.service.AnpRetailerPage;
import com.fuelfinder.modules.anp.service.AnpRetailerRecord;
import com.fuelfinder.modules.anp.service.AnpCsvDownloader;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private AnpRetailerApiClient anpRetailerApiClient;

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
        reset(csvDownloader, anpRetailerApiClient);
        when(csvDownloader.validateSourceUrl(SOURCE_URL)).thenReturn(SOURCE_URI);
        when(csvDownloader.sanitizeSourceUrl(SOURCE_URI))
                .thenReturn("https://www.gov.br/anp/historico.csv");
        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "GASOLINA ADITIVADA", "5,899")));
        when(anpRetailerApiClient.getSaoPauloPage(1))
                .thenReturn(new AnpRetailerPage(java.util.List.of()));
    }

    @Test
    void adminCanImportAndReimportIdempotentlyWithCompleteAudit() throws Exception {
        String adminToken = createAdminAndLogin();

        MvcResult started = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://www.gov.br/anp/historico.csv"))
                .andReturn();
        UUID importId = importId(started);
        MvcResult imported = awaitImport(adminToken, importId);
        assertEquals("PARTIAL", objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("status").asText());
        assertEquals(2, objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("totalRecordsRead").asInt());
        assertEquals(2, objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("totalRecordsImported").asInt());
        assertEquals(2, objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("apiCnpjsUnmatched").asInt());
        assertEquals(1, objectMapper.readTree(
                imported.getResponse().getContentAsString()).get("apiPagesProcessed").asInt());
        assertEquals(2, stationRepository.count());
        assertEquals(2, fuelPriceRepository.count());
        assertEquals(2, importLogRepository.findById(importId).orElseThrow()
                .getErrorDetails().split("\\R").length);
        Station ungeocoded = stationRepository.findByCnpj("12345678000195").orElseThrow();
        assertNull(ungeocoded.getLatitude());

        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "GASOLINA ADITIVADA", "5,899")));
        MvcResult secondStarted = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult secondResult = awaitImport(adminToken, importId(secondStarted));
        assertEquals("PARTIAL", objectMapper.readTree(
                secondResult.getResponse().getContentAsString()).get("status").asText());
        assertEquals(0, objectMapper.readTree(
                secondResult.getResponse().getContentAsString()).get("totalRecordsImported").asInt());
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
    void storesAnpApiCoordinatesAndMarksUnknownProductsPartial()
            throws Exception {
        String adminToken = createAdminAndLogin();
        when(anpRetailerApiClient.getSaoPauloPage(1)).thenReturn(new AnpRetailerPage(
                java.util.List.of(new AnpRetailerRecord(
                        "12345678000195", "SP", "-23.5", "-46.6"))));
        when(anpRetailerApiClient.getSaoPauloPage(2))
                .thenReturn(new AnpRetailerPage(java.util.List.of()));
        when(csvDownloader.download(SOURCE_URL)).thenReturn(csv(
                row("12345678000195", "ETANOL", "4,199"),
                row("12345678000196", "COMBUSTIVEL DESCONHECIDO", "5,899")));

        MvcResult started = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult result = awaitImport(adminToken, importId(started));
        assertEquals("PARTIAL", objectMapper.readTree(
                result.getResponse().getContentAsString()).get("status").asText());
        assertEquals(1, objectMapper.readTree(
                result.getResponse().getContentAsString()).get("totalRecordsImported").asInt());
        assertEquals(1, objectMapper.readTree(
                result.getResponse().getContentAsString()).get("coordinatesUpdated").asInt());
        assertEquals(2, objectMapper.readTree(
                result.getResponse().getContentAsString()).get("apiPagesProcessed").asInt());

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
        MvcResult started = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andReturn();
        awaitImport(adminToken, importId(started));
        when(csvDownloader.download(SOURCE_URL))
                .thenReturn("invalid;layout".getBytes(StandardCharsets.UTF_8));

        MvcResult failedStarted = triggerImport(adminToken, SOURCE_URL)
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult failed = awaitImport(adminToken, importId(failedStarted));
        assertEquals("FAILED", objectMapper.readTree(
                failed.getResponse().getContentAsString()).get("status").asText());
        assertNotNull(objectMapper.readTree(
                failed.getResponse().getContentAsString()).get("errorDetails"));

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

    private UUID importId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(
                        result.getResponse().getContentAsString()).get("id").asText());
    }

    private MvcResult awaitImport(String token, UUID importId) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            MvcResult result = mockMvc.perform(get("/admin/anp/imports/" + importId)
                                    .header("Authorization", bearer(token)))
                            .andExpect(status().isOk())
                            .andReturn();
            if (!"RUNNING".equals(objectMapper.readTree(
                            result.getResponse().getContentAsString()).get("status").asText())) {
                        return result;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Importação ANP não terminou dentro do tempo esperado.");
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
