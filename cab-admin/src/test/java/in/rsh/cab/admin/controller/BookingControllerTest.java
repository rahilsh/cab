package in.rsh.cab.admin.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.rsh.cab.admin.model.request.AddCabRequest;
import in.rsh.cab.admin.model.request.AddCityRequest;
import in.rsh.cab.admin.model.request.BookCabRequest;
import in.rsh.cab.commons.adapter.LocalDateTimeDeserializer;
import in.rsh.cab.commons.adapter.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:testdb"
    })
class BookingControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mvc;
  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
          .create();

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void testBookings() throws Exception {
    onboardCities();
    final BookCabRequest bookCabRequest = onboardCab();
    bookCab(bookCabRequest);
    mvc.perform(get("/bookings").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[0].cabId", is(String.valueOf(1))))
        .andExpect(jsonPath("$[0].bookedBy", is(String.valueOf(bookCabRequest.employeeId()))));
  }

  private void bookCab(BookCabRequest bookCabRequest) throws Exception {
    mvc.perform(
            post("/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(bookCabRequest)))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$.bookedBy", is(String.valueOf(bookCabRequest.employeeId()))))
        .andExpect(jsonPath("$.cabId", is(String.valueOf(1))));
  }

  private BookCabRequest onboardCab() throws Exception {
    mvc.perform(
            post("/cabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCabRequest(1, 1, "HECTOR"))))
        .andExpect(status().isOk())
        .andDo(print());

    return new BookCabRequest(1, 1, 2);
  }

  private void onboardCities() throws Exception {
    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCityRequest("BLR", "KA"))))
        .andExpect(status().isOk())
        .andDo(print());

    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCityRequest("MUM", "MH"))))
        .andExpect(status().isOk())
        .andDo(print());
  }
}
